// app/src/main/java/com/example/myapplication/data/remote/BedrockClient.kt

package com.example.myapplication.data.remote

import android.util.Log
import com.example.myapplication.data.local.ContentSourceEntity
import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelRequest
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelWithResponseStreamRequest
import aws.sdk.kotlin.services.bedrockruntime.model.ResponseStream
import aws.smithy.kotlin.runtime.http.engine.okhttp.OkHttpEngine
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.local.CompletedWorkoutWithExercise
import org.json.JSONObject
import okhttp3.internal.http2.StreamResetException
import kotlinx.coroutines.CancellationException
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.local.UserMemoryEntity
import com.example.myapplication.data.local.UserPreferencesRepository
import com.example.myapplication.data.repository.AuthRepository
import com.example.myapplication.data.repository.PromptRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.EncodeDefault
import java.util.Date
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.net.ssl.SSLException

// --- AI RESPONSE DATA MODELS ---
@Serializable
data class FoodLogResponse(
    val foodItems: List<FoodItem>,
    val totalMacros: MacroSummary,
    val analysis: String,
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
    val nutrition: RemoteNutritionPlan? = null,
    val mesocycleLengthWeeks: Int = 4
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
    val isAMRAP: Boolean = false,
    val isEMOM: Boolean = false,
    val estimatedTimeMinutes: Float = 0f,
    val loadability: String? = null
)

// --- CLAUDE API REQUEST MODELS ---
@Serializable
data class ClaudeRequest(
    val anthropic_version: String = "bedrock-2023-05-31",
    val max_tokens: Int,
    val system: String,
    val messages: List<Message>,
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val thinking: ThinkingConfig? = null
)

@Serializable
data class ThinkingConfig(
    val type: String = "enabled",
    val budget_tokens: Int
)

@Serializable
data class Message(val role: String, val content: String)

@Serializable
data class ClaudeResponse(
    val content: List<ContentBlock>,
    @SerialName("stop_reason") val stopReason: String? = null
)

@Serializable
data class ContentBlock(
    val type: String = "text",
    val text: String? = null,
    val thinking: String? = null
)

@Serializable
data class StreamEvent(
    val type: String,
    val message: StreamMessage? = null,
    @SerialName("content_block") val contentBlock: ContentBlock? = null,
    val delta: StreamDelta? = null,
    @SerialName("usage") val usage: StreamUsage? = null
)

@Serializable
data class StreamMessage(
    val role: String? = null,
    val content: List<ContentBlock>? = null,
    @SerialName("stop_reason") val stopReason: String? = null
)

@Serializable
data class StreamDelta(
    val type: String? = null,
    val text: String? = null,
    val thinking: String? = null
)

@Serializable
data class StreamUsage(
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0
)

// --- CONFIGURATION ---
private val jsonConfig = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
    coerceInputValues = true
    allowTrailingComma = true
}

private const val tierDefinitions = """
    - Compound: Primary Compound Lifts (High fatigue, high priority, e.g., Squat, Bench, Deadlift).
    - Secondary: Secondary/Accessory Lifts (Moderate fatigue, e.g., Dumbbell Press, Rows).
    - Isolation: Isolation/Correctives (Low fatigue, e.g., Curls, Lateral Raises, Face Pulls).
"""

// Model IDs - Using Inference Profiles for Claude 3.x models to support on-demand throughput
private const val CLAUDE_SONNET = "arn:aws:bedrock:us-east-1:609113669048:inference-profile/us.anthropic.claude-3-7-sonnet-20250219-v1:0"
private const val CLAUDE_HAIKU = "arn:aws:bedrock:us-east-1:609113669048:inference-profile/us.anthropic.claude-3-haiku-20240307-v1:0"

// --- CLIENT CLASS ---
@Singleton
class BedrockClient @Inject constructor(
    private val authRepository: AuthRepository,
    private val promptRepository: PromptRepository,
    private val userPrefs: UserPreferencesRepository
) {

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
                connectTimeout = 120.seconds
                socketReadTimeout = 300.seconds
            }
        }
    }

    private suspend fun enforceLimit() {
        val todayUsage = userPrefs.aiRequestsToday.first()
        val dailyLimit = userPrefs.aiDailyLimit.first()
        if (todayUsage >= dailyLimit) {
            throw Exception("Daily AI limit reached ($dailyLimit requests).")
        }
        userPrefs.incrementAiUsage()
    }

    suspend fun parseFoodLog(userQuery: String): FoodLogResponse = withContext(Dispatchers.Default) {
        try {
            val systemPrompt = promptRepository.getFoodLogSystemPrompt()
            val cleanJson = invokeClaude(systemPrompt, userQuery, CLAUDE_HAIKU)
            jsonConfig.decodeFromString<FoodLogResponse>(cleanJson)
        } catch (e: Exception) {
            Log.e("BedrockError", "Error parsing food log", e)
            FoodLogResponse(emptyList(), MacroSummary(0, 0, 0, 0), "Error: ${e.message}")
        }
    }

    suspend fun generateWorkoutPlan(
        goal: String,
        programType: String,
        days: List<String>,
        duration: Float,
        workoutHistory: List<CompletedWorkoutWithExercise>,
        availableExercises: List<ExerciseEntity>,
        userAge: Int,
        userHeight: Double,
        userWeight: Double,
        block: Int = 1,
        sleepHours: Double = 8.0,
        onThoughtReceived: (String) -> Unit = {}
    ): GeneratedPlanResponse = withContext(Dispatchers.Default) {

        try {
            val historySummary = workoutHistory
                .groupBy { it.completedWorkout.date }
                .entries.joinToString("\n") { (date, sessionSets) ->
                    val sessionDetails = sessionSets
                        .groupBy { it.exercise.name }
                        .entries.joinToString(", ") { (name, sets) ->
                            val maxWeight = sets.maxOfOrNull { it.completedWorkout.weight.toFloat() } ?: 0f
                            "$name (${sets.size} sets @ ~${maxWeight}lbs)"
                        }
                    "On ${Date(date)}, you did: $sessionDetails."
                }

            val exerciseListString = availableExercises.joinToString("\n") {
                "- ${it.name} (Muscle: ${it.muscleGroup ?: "General"}, Tier: ${it.tier})"
            }

            val totalMinutes = (duration * 60).toInt()

            val systemPrompt = promptRepository.getWorkoutSystemPrompt(
                goal = goal,
                programType = programType,
                days = days,
                totalMinutes = totalMinutes,
                userAge = userAge,
                userHeight = userHeight,
                userWeight = userWeight,
                exerciseListString = exerciseListString,
                historySummary = historySummary
            )

            val cleanJson = invokeClaude(
                systemPrompt = systemPrompt,
                userPrompt = "Generate Block $block plan",
                modelId = CLAUDE_SONNET
            )

            Log.d("BedrockClient", "Workout Plan Response: $cleanJson")

            jsonConfig.decodeFromString<GeneratedPlanResponse>(cleanJson)

        } catch (e: Exception) {
            Log.e("BedrockError", "Error generating workout plan: ${e.javaClass.simpleName} - ${e.message}", e)
            GeneratedPlanResponse(explanation = "Network or Generation Error: ${e.localizedMessage}. Please ensure your device is connected to the internet.")
        }
    }

    @Serializable
    data class NegotiationResponse(
        val explanation: String,
        val exercises: List<GeneratedExercise> = emptyList(),
        val replacingExerciseName: String? = null
    )

    suspend fun coachInteraction(
        currentWorkout: String,
        userMessage: String,
        availableExercises: List<ExerciseEntity>
    ): NegotiationResponse = withContext(Dispatchers.Default) {
        val exerciseList = availableExercises.joinToString("\n") { "- ${it.name}" }

        val rawPrompt = promptRepository.getCoachInteractionPrompt()

        val systemPrompt = rawPrompt
            .replace("{currentWorkout}", currentWorkout)
            .replace("{exerciseList}", exerciseList)

        try {
            val cleanJson = invokeClaude(systemPrompt, userMessage, CLAUDE_HAIKU)
            jsonConfig.decodeFromString<NegotiationResponse>(cleanJson)
        } catch (e: Exception) {
            Log.e("BedrockError", "Coach interaction failed", e)
            NegotiationResponse("I'm here, but I'm having trouble processing that. Keep going!", emptyList())
        }
    }

    suspend fun generateCoachingCue(
        exerciseName: String,
        issue: String,
        repCount: Int
    ): String {
        val response = coachInteraction(
            currentWorkout = "- $exerciseName",
            userMessage = "I am doing $exerciseName and having this issue: $issue. Give me a short (max 5 words) correction.",
            availableExercises = emptyList()
        )
        return response.explanation
    }

    suspend fun generateNutritionPlan(
        userAge: Int,
        userHeight: Double,
        userWeight: Double,
        gender: String,
        dietType: String,
        goalPace: String,
        weeklyWorkoutDays: Int,
        avgWorkoutDurationMins: Int
    ): RemoteNutritionPlan = withContext(Dispatchers.Default) {
        try {
            val rawPrompt = promptRepository.getNutritionSystemPrompt()

            val systemPrompt = rawPrompt
                .replace("{userAge}", userAge.toString())
                .replace("{gender}", gender)
                .replace("{userHeight}", userHeight.toString())
                .replace("{userWeight}", userWeight.toString())
                .replace("{weeklyWorkoutDays}", weeklyWorkoutDays.toString())
                .replace("{avgWorkoutDurationMins}", avgWorkoutDurationMins.toString())
                .replace("{goalPace}", goalPace)

            val cleanJson = invokeClaude(systemPrompt, "Generate Nutrition", CLAUDE_HAIKU)
            jsonConfig.decodeFromString<RemoteNutritionPlan>(cleanJson)
        } catch (e: Exception) {
            Log.e("BedrockError", "Nutrition Gen Failed", e)
            RemoteNutritionPlan(explanation = "Error generating plan.")
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
            val cleanJson = invokeClaude(prompt, "Generate Stretching", CLAUDE_HAIKU)
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

        val rawPrompt = promptRepository.getIntelSelectionPrompt()
        val systemPrompt = "$rawPrompt\nReturn the ID as a JSON object: {\"selectedId\": 123}"
        val userPrompt = "Today's Workout: $currentWorkout\n\nAvailable Content:\n$contentListString"

        try {
            val responseText = invokeClaude(systemPrompt, userPrompt, CLAUDE_HAIKU)
            val json = JSONObject(responseText)
            val selectedId = if (json.has("selectedId")) json.getLong("selectedId") else null
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

        val rawPrompt = promptRepository.getRecommendationsPrompt()
        val prompt = rawPrompt.replace("{currentInterests}", currentInterests.joinToString(", "))

        try {
            val response = invokeClaude(prompt, "Recommend interests", CLAUDE_HAIKU)
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
            val cleanJson = invokeClaude(prompt, "Generate Accessory", CLAUDE_HAIKU)
            jsonConfig.decodeFromString<GeneratedPlanResponse>(cleanJson)
        } catch (e: Exception) {
            Log.e("Bedrock", "Accessory parse failed", e)
            GeneratedPlanResponse(explanation = "Failed to generate accessory work.")
        }
    }

    suspend fun generateKnowledgeBriefing(
        content: List<ContentSourceEntity>,
        workoutTitle: String? = null
    ): String = withContext(Dispatchers.Default) {
        if (content.isEmpty() && workoutTitle == null) return@withContext "No news yet."

        val contentList = content.joinToString("\n") { "- ${it.title}: ${it.summary}" }
        val workoutContext = if (workoutTitle != null) "The user's workout for today is: $workoutTitle." else "No workout scheduled today."

        val rawPrompt = promptRepository.getKnowledgeBriefingPrompt()
        val systemPrompt = rawPrompt.replace("{workoutContext}", workoutContext)

        try {
            val cleanJson = invokeClaude(systemPrompt, "Summarize this content: \n$contentList", CLAUDE_HAIKU)
            val json = JSONObject(cleanJson)
            json.getString("briefing")
        } catch (e: Exception) {
            Log.e("Bedrock", "Briefing failed", e)
            "Stay focused on your goals!"
        }
    }

    suspend fun extractUserMemory(userSpeech: String): UserMemoryEntity? = withContext(Dispatchers.Default) {
        if (userSpeech.isBlank()) return@withContext null

        val systemPrompt = """
            You are a Fitness Memory Assistant. Analyze the user's speech for mentions of:
            1. Pain or injury (Category: 'Pain')
            2. Exercise preferences or dislikes (Category: 'Preference')
            3. Fitness goals (Category: 'Goal')
            
            If detected, output a JSON object:
            {
              "category": "Pain" | "Preference" | "Goal",
              "exerciseName": "Name of specific exercise if mentioned, else null",
              "note": "A concise summary of the memory"
            }
            If nothing significant is mentioned, return an empty JSON object {}.
            MANDATORY: Return ONLY the JSON object.
        """.trimIndent()

        try {
            val cleanJson = invokeClaude(systemPrompt, userSpeech, CLAUDE_HAIKU)
            if (cleanJson == "{}") return@withContext null

            val json = JSONObject(cleanJson)
            if (!json.has("category")) return@withContext null

            UserMemoryEntity(
                timestamp = System.currentTimeMillis(),
                category = json.getString("category"),
                exerciseName = if (json.isNull("exerciseName")) null else json.getString("exerciseName"),
                note = json.getString("note")
            )
        } catch (e: Exception) {
            Log.e("BedrockError", "Error extracting memory", e)
            null
        }
    }

    private fun sanitizeHistory(history: List<Message>): List<Message> {
        if (history.isEmpty()) return emptyList()

        val sanitized = mutableListOf<Message>()
        for (msg in history) {
            if (sanitized.isEmpty()) {
                if (msg.role == "user") {
                    sanitized.add(msg)
                } else {
                    continue
                }
            } else {
                val last = sanitized.last()
                if (last.role == msg.role) {
                    val merged = Message(last.role, last.content + "\n" + msg.content)
                    sanitized[sanitized.size - 1] = merged
                } else {
                    sanitized.add(msg)
                }
            }
        }

        if (sanitized.isNotEmpty() && sanitized.last().role != "user") {
            sanitized.add(Message("user", "..."))
        }

        return sanitized
    }

    suspend fun streamConversationalResponse(
        chatHistory: List<Message>,
        onTextChunkReceived: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        enforceLimit()

        // 1. Give Claude strict instructions on how to act live
        val systemPrompt = """
            You are an elite, highly conversational fitness coach currently on a live audio call with your client during their workout. 
            CRITICAL RULES:
            - Responses MUST be extremely brief (1 to 3 short sentences max). 
            - Speak like a human. Use filler words occasionally (e.g., 'Alright', 'Yeah', 'Hmm', 'Got it').
            - NEVER use formatting like asterisks, emojis, or bullet points. This text is being fed directly into a Text-To-Speech engine.
            - Provide hype, brief form cues, and ask how the weight feels.
        """.trimIndent()

        val sanitizedMessages = sanitizeHistory(chatHistory)
        if (sanitizedMessages.isEmpty()) return@withContext "Let's get back to work!"

        val requestBody = ClaudeRequest(
            max_tokens = 300,
            system = systemPrompt,
            messages = sanitizedMessages
        )

        val jsonString = jsonConfig.encodeToString(ClaudeRequest.serializer(), requestBody)

        val request = InvokeModelWithResponseStreamRequest {
            this.modelId = CLAUDE_HAIKU
            contentType = "application/json"
            accept = "application/json"
            body = jsonString.toByteArray()
        }

        val fullText = StringBuilder()

        try {
            client.invokeModelWithResponseStream(request) { response ->
                response.body?.collect { event ->
                    if (event is ResponseStream.Chunk) {
                        val chunkText = event.value.bytes?.decodeToString() ?: ""
                        try {
                            val streamEvent = jsonConfig.decodeFromString<StreamEvent>(chunkText)
                            if (streamEvent.type == "content_block_delta") {
                                streamEvent.delta?.text?.let { token ->
                                    fullText.append(token)
                                    onTextChunkReceived(token)
                                }
                            }
                        } catch (e: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BedrockClient", "Streaming conversational response failed", e)
            return@withContext "Keep pushing, you're doing great!"
        }
        return@withContext fullText.toString()
    }
    suspend fun generatePreWorkoutScript(
        recovery: Int,
        workoutTitle: String?,
        exercises: List<String>,
        userMemories: List<UserMemoryEntity>,
        recentFatigueState: String
    ): String = withContext(Dispatchers.Default) {
        val memoriesList = if (userMemories.isEmpty()) "None recorded." else userMemories.joinToString("\n") {
            "- ${it.category}: ${it.note} ${it.exerciseName?.let { name -> "($name)" } ?: ""}"
        }

        val workoutContext = if (workoutTitle != null) "The user's workout for today is: $workoutTitle." else "No workout scheduled today."
        val exerciseList = if (exercises.isNotEmpty()) "The planned exercises are: ${exercises.joinToString(", ")}." else ""

        val systemPrompt = """
            You are a professional Fitness Auto-Coach. Your goal is to provide a concise (max 3 sentences) pre-workout briefing based strictly on the user data provided.
            
            CONTEXT:
            ${workoutContext}
            ${exerciseList}
            RECOVERY SCORE: $recovery%
            
            USER MEMORIES (PAIN/PREFERENCES):
            $memoriesList
            
            RECENT FATIGUE STATE (VOLUME DATA):
            $recentFatigueState
            
            INSTRUCTIONS:
            1. Analyze the context above.
            2. Mention the primary focus of the workout.
            3. If there are USER MEMORIES regarding pain or injuries related to today's exercises, you MUST acknowledge them and advise caution.
            4. If RECENT FATIGUE STATE shows high volume for today's target muscles, advise focusing on form or slightly lower intensity.
            5. If RECOVERY SCORE is low, suggest a more conservative approach.
            6. STRICT RULE: DO NOT mention any injuries, pains, or history that are not explicitly listed in the USER MEMORIES or FATIGUE STATE above. Do not hallucinate trends.
            7. If everything looks good (high recovery, no pain, low fatigue), be purely encouraging about the session.
            8. Keep the tone professional, supportive, and data-driven.
            
            MANDATORY: Return ONLY a JSON object: {"briefing": "your briefing here"}
        """.trimIndent()

        try {
            val cleanJson = invokeClaude(systemPrompt, "Generate my pre-workout briefing.", CLAUDE_HAIKU)
            val json = JSONObject(cleanJson)
            json.getString("briefing")
        } catch (e: Exception) {
            Log.e("Bedrock", "Pre-workout script failed", e)
            "Let's have a great workout today!"
        }
    }

    suspend fun generateSetIntroScript(
        exerciseName: String,
        reps: Int,
        weight: Float,
        setNumber: Int,
        totalSets: Int
    ): String = withContext(Dispatchers.Default) {
        val systemPrompt = """
            You are a charismatic and professional Fitness Coach. 
            The user is about to start a set. 
            Generate a short, varied, and natural sentence encouraging the user and stating the prescribed work.
            Avoid being repetitive. Use coaching cues or motivational phrases.
            IMPORTANT: End the sentence with a countdown: "3... 2... 1... Go!"
            Return as JSON: {"script": "your sentence here"}
        """.trimIndent()

        val userPrompt = "Exercise: $exerciseName, Reps: $reps, Weight: $weight lbs, Set: $setNumber of $totalSets"

        try {
            val cleanJson = invokeClaude(systemPrompt, userPrompt, CLAUDE_HAIKU)
            val json = JSONObject(cleanJson)
            json.getString("script")
        } catch (e: Exception) {
            Log.e("Bedrock", "Set intro script failed", e)
            "Alright, set $setNumber of $totalSets. $reps reps at ${weight.toInt()} lbs. 3... 2... 1... Go!"
        }
    }

    suspend fun generatePostSetScript(reps: Int, weight: Float): String = withContext(Dispatchers.Default) {
        val systemPrompt = """
            You are a charismatic and professional Fitness Coach. 
            The user just finished a set.
            Generate a short, conversational phrase checking in on the set. 
            Vary your tone—sometimes be impressed, sometimes just clinical.
            Example: 'Solid. Did you nail all 8 reps?' or 'That looked heavy. Get all of them?'
            Return as JSON: {"script": "your sentence here"}
        """.trimIndent()

        val userPrompt = "Prescribed: $reps reps at $weight lbs"

        try {
            val cleanJson = invokeClaude(systemPrompt, userPrompt, CLAUDE_HAIKU)
            val json = JSONObject(cleanJson)
            json.getString("script")
        } catch (e: Exception) {
            Log.e("Bedrock", "Post-set script failed", e)
            "Nice work. Did you get all $reps reps?"
        }
    }

    private suspend fun invokeClaude(
        systemPrompt: String,
        userPrompt: String,
        modelId: String,
        thinkingBudget: Int = 0
    ): String {
        if (userPrompt.isBlank()) return "{}"

        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                return doInvokeClaude(systemPrompt, userPrompt, modelId, thinkingBudget)
            } catch (e: Exception) {
                lastError = e
                if (e is CancellationException) throw e

                // Identify fatal AWS errors that should NOT be retried (e.g. Validation, Auth errors)
                val errName = e.javaClass.simpleName
                val isFatalAwsError = errName.contains("Validation") || errName.contains("AccessDenied") || errName.contains("Unrecognized")

                if (!isFatalAwsError && attempt < 2) {
                    val delayMs = (attempt + 1) * 2000L
                    Log.w("BedrockClient", "InvokeClaude attempt ${attempt + 1} failed (${errName}: ${e.message}). Retrying in ${delayMs}ms...", e)
                    kotlinx.coroutines.delay(delayMs)
                } else {
                    Log.e("BedrockClient", "InvokeClaude failed completely after ${attempt + 1} attempts. Cause: ${errName} - ${e.message}", e)
                    throw e
                }
            }
        }
        throw lastError ?: Exception("Unknown error")
    }

    private suspend fun doInvokeClaude(
        systemPrompt: String,
        userPrompt: String,
        modelId: String,
        thinkingBudget: Int = 0
    ): String {
        Log.d("BedrockClient", "InvokeClaude System Instruction: $systemPrompt")
        enforceLimit()

        // Use 8192 for Sonnet to ensure the massive JSON schedules don't get truncated
        val maxTokensToUse = if (modelId.contains("sonnet", ignoreCase = true)) 8192 else 4096

        val requestBody = ClaudeRequest(
            max_tokens = maxTokensToUse,
            system = systemPrompt,
            messages = listOf(Message(role = "user", content = userPrompt)),
            thinking = if (thinkingBudget > 0) ThinkingConfig(budget_tokens = thinkingBudget) else null
        )

        val jsonString = jsonConfig.encodeToString(ClaudeRequest.serializer(), requestBody)

        val request = InvokeModelRequest {
            this.modelId = modelId
            contentType = "application/json"
            accept = "application/json"
            body = jsonString.toByteArray()
        }

        val response = client.invokeModel(request)
        val responseBody = response.body?.decodeToString() ?: ""
        val outerResponse = jsonConfig.decodeFromString<ClaudeResponse>(responseBody)

        // Log thinking/scratchpad if available in non-streaming response
        outerResponse.content.forEach { block ->
            if (block.thinking != null) {
                Log.d("BedrockClient", "Claude Thinking: ${block.thinking}")
            }
        }

        val rawText = outerResponse.content.firstOrNull { it.type == "text" }?.text ?: ""

        if (outerResponse.stopReason == "max_tokens") {
            Log.w("BedrockClient", "Model stopped due to max_tokens. Response may be incomplete.")
        }

        return extractJsonFromText(rawText)
    }

    private suspend fun invokeClaudeStreaming(
        systemPrompt: String,
        userPrompt: String,
        modelId: String,
        thinkingBudget: Int = 0,
        onThoughtReceived: (String) -> Unit = {}
    ): String {
        if (userPrompt.isBlank()) return "{}"

        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                return doInvokeClaudeStreaming(systemPrompt, userPrompt, modelId, thinkingBudget, onThoughtReceived)
            } catch (e: Exception) {
                lastError = e
                if (e is CancellationException) throw e

                val errName = e.javaClass.simpleName
                val isFatalAwsError = errName.contains("Validation") || errName.contains("AccessDenied") || errName.contains("Unrecognized")

                if (!isFatalAwsError && attempt < 2) {
                    val delayMs = (attempt + 1) * 2000L
                    Log.w("BedrockClient", "Streaming attempt ${attempt + 1} failed (${errName}: ${e.message}). Retrying in ${delayMs}ms...", e)
                    kotlinx.coroutines.delay(delayMs)
                } else {
                    Log.e("BedrockClient", "Streaming request failed completely after ${attempt + 1} attempts. Cause: ${errName} - ${e.message}", e)
                    throw e
                }
            }
        }
        throw lastError ?: Exception("Unknown streaming error")
    }

    private suspend fun doInvokeClaudeStreaming(
        systemPrompt: String,
        userPrompt: String,
        modelId: String,
        thinkingBudget: Int = 0,
        onThoughtReceived: (String) -> Unit = {}
    ): String {
        Log.d("BedrockClient", "InvokeClaudeStreaming System Instruction: $systemPrompt")
        enforceLimit()

        // Safely assign token limits for streaming as well
        val maxTokensToUse = if (modelId.contains("sonnet", ignoreCase = true)) 8192 else 4096

        val requestBody = ClaudeRequest(
            max_tokens = maxTokensToUse,
            system = systemPrompt,
            messages = listOf(Message(role = "user", content = userPrompt)),
            thinking = if (thinkingBudget > 0) ThinkingConfig(budget_tokens = thinkingBudget) else null
        )

        val jsonString = jsonConfig.encodeToString(ClaudeRequest.serializer(), requestBody)

        val request = InvokeModelWithResponseStreamRequest {
            this.modelId = modelId
            contentType = "application/json"
            accept = "application/json"
            body = jsonString.toByteArray()
        }

        val fullText = StringBuilder()
        var currentThought = StringBuilder()

        client.invokeModelWithResponseStream(request) { response ->
            response.body?.collect { event ->
                when (event) {
                    is ResponseStream.Chunk -> {
                        val chunkText = event.value.bytes?.decodeToString() ?: ""
                        try {
                            val streamEvent = jsonConfig.decodeFromString<StreamEvent>(chunkText)
                            when (streamEvent.type) {
                                "content_block_delta" -> {
                                    streamEvent.delta?.text?.let { fullText.append(it) }
                                    streamEvent.delta?.thinking?.let {
                                        currentThought.append(it)
                                        // Send summarized thought updates
                                        onThoughtReceived(summarizeThinking(currentThought.toString()))
                                    }
                                }
                            }
                        } catch (e: Exception) {}
                    }
                    else -> {}
                }
            }
        }

        Log.d("BedrockClient", "Claude Scratchpad/Thinking Output: ${currentThought.toString()}")
        val finalJson = extractJsonFromText(fullText.toString())
        return finalJson
    }

    private fun summarizeThinking(thought: String): String {
        val lowercaseThought = thought.lowercase()
        return when {
            lowercaseThought.contains("calculating progressive overload") || lowercaseThought.contains("intensity") -> "Optimizing Progressive Overload..."
            lowercaseThought.contains("volume") || lowercaseThought.contains("sets") || lowercaseThought.contains("reps") -> "Calculating training volume..."
            lowercaseThought.contains("muscle") || lowercaseThought.contains("split") || lowercaseThought.contains("days") -> "Structuring workout split..."
            lowercaseThought.contains("history") || lowercaseThought.contains("past") || lowercaseThought.contains("previous") -> "Analyzing performance history..."
            lowercaseThought.contains("nutrition") || lowercaseThought.contains("calories") -> "Syncing nutritional data..."
            lowercaseThought.contains("exercise") || lowercaseThought.contains("selection") || lowercaseThought.contains("variation") -> "Curating exercise selection..."
            lowercaseThought.contains("deload") || lowercaseThought.contains("fatigue") -> "Planning recovery strategy..."
            else -> {
                // Fallback to a cleaner version of the last sentence if no keyword match
                val sentences = thought.split(Regex("(?<=[.!?])\\s+"))
                val last = sentences.lastOrNull { it.length > 10 } ?: ""
                if (last.length > 60) last.take(57) + "..." else last
            }
        }
    }

    private fun extractJsonFromText(text: String): String {
        val start = text.indexOf('{')
        if (start == -1) throw Exception("AI returned invalid format (no opening brace found in response).")

        var balance = 0
        var inString = false
        var escaped = false

        for (i in start until text.length) {
            val c = text[i]
            if (escaped) {
                escaped = false
                continue
            }
            when (c) {
                '\\' -> escaped = true
                '\"' -> inString = !inString
                '{' -> if (!inString) balance++
                '}' -> if (!inString) {
                    balance--
                    if (balance == 0) return text.substring(start, i + 1)
                }
            }
        }
        throw Exception("AI response was truncated (unbalanced braces).")
    }
}