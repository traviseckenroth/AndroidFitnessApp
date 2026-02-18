// app/src/main/java/com/example/myapplication/data/remote/BedrockClient.kt

package com.example.myapplication.data.remote

import android.util.Log
import com.example.myapplication.data.local.ContentSourceEntity // ADD THIS IMPORT
import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelRequest
import aws.smithy.kotlin.runtime.http.engine.okhttp.OkHttpEngine
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.local.CompletedWorkoutWithExercise
import org.json.JSONObject
import okhttp3.internal.http2.StreamResetException
import kotlinx.coroutines.CancellationException
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.repository.AuthRepository
// 1. ADD IMPORT
import com.example.myapplication.data.repository.PromptRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import java.util.Date

// --- AI RESPONSE DATA MODELS ---
// --- 1. NEW DATA MODELS FOR FOOD LOGGING ---
@Serializable
data class FoodLogResponse(
    val foodItems: List<FoodItem>,
    val totalMacros: MacroSummary,
    val analysis: String, // Brief comment like "High protein meal!"
    val mealType: String = "Snack"
)

@Serializable
data class FoodItem(
    val name: String,
    val quantity: String,
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fats: Int
)

@Serializable
data class MacroSummary(
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fats: Int
)
@Serializable
data class RemoteNutritionPlan(
    val calories: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fats: String = "",
    val timing: String = "",
    val explanation: String = ""
)

@Serializable
data class GeneratedPlanResponse(
    val explanation: String = "",
    val schedule: List<GeneratedDay> = emptyList(),
    val nutrition: RemoteNutritionPlan? = null
)

@Serializable
data class GeneratedDay(
    val week: Int = 1,
    val day: String,
    @SerialName("workoutName") val title: String = "Workout",
    val exercises: List<GeneratedExercise> = emptyList()
)

@Serializable
data class GeneratedExercise(
    val name: String,
    @SerialName("targetMuscle") val muscleGroup: String? = null,
    val equipment: String? = null,
    val fatigue: String? = null,
    val tier: Int = 1,
    val notes: String = "",
    val suggestedReps: Int = 5,
    val suggestedRpe: Int = 7,
    val suggestedLbs: Float = 0f,
    val sets: Int = 3,
    val estimatedTimeMinutes: Float = 0f,
    val loadability: String? = null
)

// --- CLAUDE API REQUEST MODELS ---
@Serializable
data class ClaudeRequest(
    val anthropic_version: String = "bedrock-2023-05-31",
    val max_tokens: Int,
    val system: String,
    val messages: List<Message>
)

@Serializable
data class Message(val role: String, val content: String)

@Serializable
data class ClaudeResponse(val content: List<ContentBlock>)

@Serializable
data class ContentBlock(val text: String)

// --- CONFIGURATION ---
private val jsonConfig = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
    coerceInputValues = true
    allowTrailingComma = true
}

// --- CLIENT CLASS ---
@Singleton
class BedrockClient @Inject constructor(
    private val authRepository: AuthRepository,
    private val promptRepository: PromptRepository
) {

    // Initialize client using your Custom Cognito Provider
    private val client by lazy {
        val cognitoProvider = CognitoCredentialsProvider(
            authRepository = authRepository,
            identityPoolId = BuildConfig.COGNITO_IDENTITY_POOL_ID,
            region = BuildConfig.AWS_REGION
        )

        BedrockRuntimeClient {
            region = BuildConfig.AWS_REGION
            credentialsProvider = cognitoProvider
            httpClient = OkHttpEngine {
                connectTimeout = 60.seconds
                socketReadTimeout = 120.seconds
            }
        }
    }

    suspend fun generateCoachingCue(
        exerciseName: String,
        issue: String,
        repCount: Int
    ): String = withContext(Dispatchers.IO) {
        val rawTemplate = promptRepository.getCoachingCueTemplate()

        val prompt = rawTemplate
            .replace("{exerciseName}", exerciseName)
            .replace("{repCount}", repCount.toString())
            .replace("{issue}", issue)

        val jsonBody = JSONObject().apply {
            put("inputText", prompt)
            put("textGenerationConfig", JSONObject().apply {
                put("maxTokenCount", 20)
                put("temperature", 1.0)
                put("topP", 0.9)
            })
        }

        try {
            val request = InvokeModelRequest {
                modelId = "amazon.titan-text-express-v1"
                body = jsonBody.toString().toByteArray()
                contentType = "application/json"
                accept = "application/json"
            }

            val response = client.invokeModel(request)
            val bodyBytes = response.body ?: throw Exception("Empty body")
            val responseBody = JSONObject(String(bodyBytes))

            responseBody.getJSONArray("results").getJSONObject(0).getString("outputText").trim()

        } catch (e: StreamResetException) {
            // FIX: Handle stream reset safely
            Log.w("BedrockClient", "Cue generation cancelled: ${e.message}")
            throw CancellationException("Request cancelled")
        } catch (e: Exception) {
            Log.e("BedrockClient", "AI Generation failed", e)
            "Keep pushing!" // Fallback
        }
    }

    // --- NEW FUNCTION: PARSE NATURAL LANGUAGE FOOD ---
    suspend fun parseFoodLog(userQuery: String): FoodLogResponse = withContext(Dispatchers.Default) {
        try {
            // 3. FETCH PROMPT (No variables to replace in this one)
            val systemPrompt = promptRepository.getFoodLogSystemPrompt()

            val cleanJson = invokeClaude(systemPrompt, userQuery)
            jsonConfig.decodeFromString<FoodLogResponse>(cleanJson)
        } catch (e: Exception) {
            Log.e("BedrockError", "Error parsing food log", e)
            FoodLogResponse(emptyList(), MacroSummary(0, 0, 0, 0), "Error: ${e.message}")
        }
    }
    // --- 1. GENERATE WORKOUT PLAN (Exercise Only) ---
    suspend fun generateWorkoutPlan(
        goal: String,
        programType: String,
        days: List<String>,
        duration: Float,
        workoutHistory: List<CompletedWorkoutWithExercise>,
        availableExercises: List<ExerciseEntity>,
        userAge: Int,
        userHeight: Int,
        userWeight: Double
    ): GeneratedPlanResponse = withContext(Dispatchers.Default) {

        try {
            val tierDefinitions = """
                - Tier 1: Compound movements, fundamental for strength.
                - Tier 2: Accessory exercises, supplement Tier 1.
                - Tier 3: Isolation or machine-based exercises.
            """.trimIndent()

            // FIX 1: Group flat history list by Date -> then by Exercise Name
            val historySummary = workoutHistory
                .groupBy { it.completedWorkout.date }
                .entries.joinToString("\n") { (date, sessionSets) ->
                    val sessionDetails = sessionSets
                        .groupBy { it.exercise.name }
                        .entries.joinToString(", ") { (name, sets) ->
                            "$name (${sets.size} sets)"
                        }
                    "On ${Date(date)}, you did: $sessionDetails."
                }

            // FIX 2: Use correct property '.name' instead of '.exerciseName'
            val exerciseListString = availableExercises.joinToString("\n") {
                "- ${it.name} (Muscle: ${it.muscleGroup ?: "General"}, Equipment: ${it.equipment ?: "None"}, Tier: ${it.tier})"
            }

            val totalMinutes = (duration * 60).toInt()

            val rawPrompt = promptRepository.getWorkoutSystemPrompt()
            
            // VERIFICATION LOG: Confirming prompt source
            Log.d("BedrockPromptTest", "WORKOUT PROMPT TEMPLATE: $rawPrompt")

            val systemPrompt = rawPrompt
                .replace("{userAge}", userAge.toString())
                .replace("{userHeight}", userHeight.toString())
                .replace("{userWeight}", userWeight.toString())
                .replace("{goal}", goal)
                .replace("{days}", days.joinToString())
                .replace("{totalMinutes}", totalMinutes.toString())
                .replace("{historySummary}", if (historySummary.isBlank()) "No previous history." else historySummary)
                .replace("{exerciseListString}", exerciseListString)
                .replace("{tierDefinitions}", tierDefinitions)
                .replace("{totalMinutesMinus5}", (totalMinutes - 5).toString())
            
            Log.d("BedrockPromptTest", "RESOLVED SYSTEM PROMPT: $systemPrompt")

            val userPrompt = "Generate plan for ${userAge}year old, ${userWeight}kg, Goal: ${goal}."
            Log.d("BedrockPromptTest", "USER PROMPT: $userPrompt")

            val cleanJson = invokeClaude(systemPrompt, userPrompt)
            jsonConfig.decodeFromString<GeneratedPlanResponse>(cleanJson)

        } catch (e: Exception) {
            Log.e("BedrockError", "Error invoking model", e)
            GeneratedPlanResponse(explanation = "Error: ${e.localizedMessage}")
        }
    }
    @Serializable
    data class NegotiationResponse(
        val explanation: String,
        val exercises: List<GeneratedExercise>
    )

    suspend fun coachInteraction(
        currentWorkout: String,
        userMessage: String,
        availableExercises: List<ExerciseEntity>
    ): NegotiationResponse = withContext(Dispatchers.Default) {
        val exerciseList = availableExercises.joinToString("\n") { "- ${it.name}" }

        val systemPrompt = """
        You are an expert, supportive Fitness Coach. The user is currently performing a workout.
        
        YOUR GOALS:
        1. BE CONVERSATIONAL: If the user asks for advice, form tips, or motivation, answer them directly and concisely (max 3 sentences).
        2. ADAPT THE PLAN: If the user mentions pain (e.g., "my knee hurts"), extreme fatigue, or missing equipment, provide empathy AND modify the remaining exercises.
        
        RULES FOR EXERCISE MODIFICATION:
        - Only modify the plan if the user's message warrants it.
        - ONLY use exercises from the ALLOWED LIST below.
        - If no modification is needed, return an empty list for "exercises".
        
        CURRENT WORKOUT CONTEXT:
        $currentWorkout
        
        ALLOWED LIST:
        $exerciseList
        
        OUTPUT FORMAT (JSON OBJECT ONLY):
        {
          "explanation": "Your spoken response to the user (advice or explanation of changes).",
          "exercises": [ { "name": "...", "sets": 3, "suggestedReps": 10, "suggestedLbs": 0.0, "tier": 1 } ]
        }
    """.trimIndent()

        try {
            val cleanJson = invokeClaude(systemPrompt, userMessage)
            jsonConfig.decodeFromString<NegotiationResponse>(cleanJson)
        } catch (e: Exception) {
            Log.e("BedrockError", "Coach interaction failed", e)
            NegotiationResponse("I'm here, but I'm having trouble processing that. Keep going!", emptyList())
        }
    }
    // --- 2. GENERATE NUTRITION PLAN (Separate Call) ---
    suspend fun generateNutritionPlan(
        userAge: Int,
        userHeight: Int,
        userWeight: Double,
        gender: String,
        dietType: String,
        goalPace: String,
        weeklyWorkoutDays: Int,
        avgWorkoutDurationMins: Int
    ): RemoteNutritionPlan = withContext(Dispatchers.Default) {
        try {
            // 6. FETCH AND REPLACE VARIABLES FOR NUTRITION
            val rawPrompt = promptRepository.getNutritionSystemPrompt()

            val systemPrompt = rawPrompt
                .replace("{userAge}", userAge.toString())
                .replace("{gender}", gender)
                .replace("{userHeight}", userHeight.toString())
                .replace("{userWeight}", userWeight.toString())
                .replace("{weeklyWorkoutDays}", weeklyWorkoutDays.toString())
                .replace("{avgWorkoutDurationMins}", avgWorkoutDurationMins.toString())
                .replace("{goalPace}", goalPace)

            val cleanJson = invokeClaude(systemPrompt, "Generate Nutrition")
            jsonConfig.decodeFromString<RemoteNutritionPlan>(cleanJson)
        } catch (e: Exception) {
            Log.e("BedrockError", "Nutrition Gen Failed", e)
            RemoteNutritionPlan(explanation = "Error generating plan. Please try again.")
        }
    }
    suspend fun generateStretchingFlow(
        currentGoal: String,
        history: List<CompletedWorkoutWithExercise>,
        availableExercises: List<ExerciseEntity>
    ): GeneratedPlanResponse = withContext(Dispatchers.Default) {
        val exerciseList = availableExercises.joinToString("\n") { "- ${it.name}" }
        val rawPrompt = promptRepository.getStretchingSystemPrompt()
        val prompt = rawPrompt
            .replace("{currentGoal}", currentGoal)
            .replace("{exerciseList}", exerciseList)

        try {
            val cleanJson = invokeClaude(prompt, "Generate Stretching")
            jsonConfig.decodeFromString<GeneratedPlanResponse>(cleanJson)
        } catch (e: Exception) {
            Log.e("Bedrock", "Stretching parse failed", e)
            GeneratedPlanResponse(explanation = "Failed to generate mobility flow.")
        }
    }
    suspend fun selectDailyIntel(
        currentWorkout: String,
        availableContent: List<ContentSourceEntity>
    ): ContentSourceEntity? = withContext(Dispatchers.Default) {
        if (availableContent.isEmpty()) return@withContext null

        val contentListString = availableContent.joinToString("\n") { intel ->
            "${intel.sourceId}: ${intel.title} (${intel.sportTag})"
        }

        val systemPrompt = """
            You are a Fitness Content Curator. Given the user's scheduled workout and a list of articles/videos, 
            select the SINGLE most relevant item ID that will help them today.
            Return ONLY the ID number.
        """.trimIndent()

        val userPrompt = "Today's Workout: $currentWorkout\n\nAvailable Content:\n$contentListString"

        try {
            val responseText = invokeClaude(systemPrompt, userPrompt)
            val selectedId = responseText.filter { it.isDigit() }.toLongOrNull()
            availableContent.find { it.sourceId == selectedId }
        } catch (e: Exception) {
            Log.e("BedrockClient", "Intel selection failed", e)
            null
        }
    }
    suspend fun getInterestRecommendations(currentInterests: List<String>): List<String> = withContext(Dispatchers.Default) {
        if (currentInterests.isEmpty()) {
            return@withContext listOf("CrossFit", "Powerlifting", "Hyrox", "Olympic Weightlifting", "Yoga")
        }

        // Local fast-path for "CrossFit" as requested by user
        if (currentInterests.any { it.equals("CrossFit", ignoreCase = true) }) {
            return@withContext listOf("Mat Fraser", "Rich Froning", "Tia-Clair Toomey", "Justin Medeiros", "Mal O'Brien")
        }

        val prompt = """
            The user follows these sports/athletes: ${currentInterests.joinToString(", ")}.
            Recommend 5 related top athletes or niche sports they might like.
            If they follow a sport (e.g., "CrossFit"), recommend top athletes in that sport.
            If they follow an athlete, recommend their rivals or training partners.
            
            Return ONLY a JSON object with a "recommendations" key containing an array of strings. 
            Example: {"recommendations": ["Mat Fraser", "Hyrox", "Rich Froning"]}
        """.trimIndent()

        try {
            val response = invokeClaude(prompt, "Recommend interests")
            val json = JSONObject(response)
            val jsonArray = json.getJSONArray("recommendations")
            val results = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                results.add(jsonArray.getString(i))
            }
            return@withContext results
        } catch (e: Exception) {
            Log.e("Bedrock", "Recommendation failed", e)
            return@withContext emptyList()
        }
    }
    // --- WORKFLOW 2: ACCESSORY WORKOUT ---
    suspend fun generateAccessoryWorkout(
        currentGoal: String,
        history: List<CompletedWorkoutWithExercise>,
        availableExercises: List<ExerciseEntity>
    ): GeneratedPlanResponse = withContext(Dispatchers.Default) {
        val exerciseList = availableExercises.joinToString("\n") { "- ${it.name}" }
        val rawPrompt = promptRepository.getAccessorySystemPrompt()
        val prompt = rawPrompt
            .replace("{currentGoal}", currentGoal)
            .replace("{exerciseList}", exerciseList)

        try {
            val cleanJson = invokeClaude(prompt, "Generate Accessory")
            Log.d("BedrockClient", "Accessory JSON: $cleanJson") // Log the raw JSON
            jsonConfig.decodeFromString<GeneratedPlanResponse>(cleanJson)
        } catch (e: Exception) {
            Log.e("Bedrock", "Accessory parse failed: ${e.localizedMessage}", e)
            GeneratedPlanResponse(explanation = "Failed to generate accessory work. Error: ${e.localizedMessage}")
        }
    }

    suspend fun generateKnowledgeBriefing(
        content: List<ContentSourceEntity>,
        workoutTitle: String? = null
    ): String = withContext(Dispatchers.Default) {
        if (content.isEmpty() && workoutTitle == null) return@withContext "No news yet. Follow some interests to get started!"
        
        val contentList = content.joinToString("\n") { "- ${it.title} (${it.sportTag}): ${it.summary}" }
        val workoutContext = if (workoutTitle != null) "The user's workout for today is: $workoutTitle." else "No workout scheduled today."
        
        val systemPrompt = """
            You are a Fitness Intelligence Analyst. 
            Synthesize the following recent articles and videos into a 3-sentence "Daily Briefing" for the user.
            $workoutContext
            Focus on actionable tips or major trends that might be relevant to the user's interests or today's workout.
            Format as a single paragraph.
            Output JSON: {"briefing": "..."}
        """.trimIndent()

        try {
            val cleanJson = invokeClaude(systemPrompt, "Summarize this content: \n$contentList")
            val json = JSONObject(cleanJson)
            json.getString("briefing")
        } catch (e: Exception) {
            Log.e("Bedrock", "Briefing failed", e)
            "Stay focused on your goals! Check back later for your personalized briefing."
        }
    }

    // --- SHARED API HELPER ---
    private suspend fun invokeClaude(systemPrompt: String, userPrompt: String): String {
        val requestBody = ClaudeRequest(
            max_tokens = 6000,
            system = systemPrompt,
            messages = listOf(Message(role = "user", content = userPrompt))
        )

        val jsonString = jsonConfig.encodeToString(ClaudeRequest.serializer(), requestBody)

        val request = InvokeModelRequest {
            modelId = "anthropic.claude-3-haiku-20240307-v1:0"
            contentType = "application/json"
            accept = "application/json"
            body = jsonString.toByteArray()
        }
        try {
            val response = client.invokeModel(request)
            val responseBody = response.body?.decodeToString() ?: ""
            Log.d("BedrockClient", "Raw Response: $responseBody")

            val outerResponse = jsonConfig.decodeFromString<ClaudeResponse>(responseBody)
            val rawText = outerResponse.content.firstOrNull()?.text ?: ""

            // Extract JSON from potential Markdown wrapper
            val jsonRegex = Regex("\\{.*\\}", setOf(RegexOption.DOT_MATCHES_ALL))
            val match = jsonRegex.find(rawText)

            return match?.value ?: throw Exception("AI returned invalid format: $rawText")

        } catch (e: StreamResetException) {
            Log.w("BedrockClient", "Stream cancelled safely during request: ${e.message}")
            // Throwing CancellationException ensures the coroutine scope cancels gracefully without crashing
            throw CancellationException("Bedrock request cancelled")
        }
    }
}