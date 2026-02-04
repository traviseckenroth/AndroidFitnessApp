package com.example.myapplication.data.remote

import android.util.Log
import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelRequest
import aws.smithy.kotlin.runtime.http.engine.okhttp.OkHttpEngine
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.local.CompletedWorkoutWithExercise
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.repository.AuthRepository // Ensure this is imported
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// --- AI RESPONSE DATA MODELS ---
@Serializable
data class GeneratedPlanResponse(
    val explanation: String = "",
    val schedule: List<GeneratedDay> = emptyList()
)

@Serializable
data class GeneratedDay(
    val week: Int = 1,
    val day: String,
    val title: String,
    val exercises: List<GeneratedExercise> = emptyList()
)

@Serializable
data class GeneratedExercise(
    val name: String,
    val muscleGroup: String? = null,
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
    private val authRepository: AuthRepository // Correctly injected in constructor
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
            val tierDefinitions = when (programType) {
                "Strength" -> """
                    - Tier 1: Reps: Low (1-5). Sets: High (5-8). Load: Very Heavy (85-95% 1RM). RPE: 8-9 (1-2 RIR). PROGRESSION: Linear Load.
                    - Tier 2: Reps: Low (3-6). Sets: Moderate (3-5). Load: Heavy (75-85% 1RM). RPE: 8-9 (1-2 RIR). PROGRESSION: Volume Accumulation.
                    - Tier 3: Reps: Moderate/High (8-15). Sets: Moderate (3-4). Load: Moderate (muscle failure). RPE: 9-10 (0-1 RIR). PROGRESSION: Density.
                """.trimIndent()

                "Hypertrophy" -> """
                    - Tier 1: Reps: Low (5–8). Sets: High (3–5). Load: Heavy (75-90% 1RM). RPE: 8 (2 RIR). PROGRESSION: Add Weight.
                    - Tier 2: Reps: Moderate (8–12). Sets: Moderate (3–4). Load: Moderate (60-75% 1RM). RPE: 9 (1 RIR). PROGRESSION: Add Reps/Form.
                    - Tier 3: Reps: High (12–20). Sets: Moderate (2–3). Load: Light (40-60% 1RM). RPE: 10 (Failure). PROGRESSION: Add Reps/Form.
                """.trimIndent()

                "Powerlifting" -> "- Tier 1: SBD Competition Lifts ONLY."
                else -> "Use standard progressive overload."
            }

            val historyString = workoutHistory.joinToString(separator = "\n") { item ->
                "- ${item.completedWorkout.date}: ${item.completedWorkout.reps} reps at ${item.completedWorkout.weight} lbs (RPE ${item.completedWorkout.rpe})"
            }

            val exerciseListString = availableExercises.joinToString("\n") { ex ->
                "- ${ex.name} (Tier ${ex.tier}, ${ex.estimatedTimePerSet} mins/set)"
            }

            val totalMinutes = (duration * 60).toInt()

            val historySummary = workoutHistory
                .groupBy { it.exercise.name }
                .map { (name, sessions) ->
                    val sorted = sessions.sortedBy { it.completedWorkout.date }
                    val first = sorted.first().completedWorkout
                    val last = sorted.last().completedWorkout
                    "- $name: Started at ${first.weight}lbs, currently at ${last.weight}lbs (Avg RPE: ${sessions.map { it.completedWorkout.rpe }.average().toInt()})"
                }.joinToString("\n")

            val systemPrompt = """
                You are an expert strength coach. Generate a **1-week workout template** that will be repeated for a 4-week block.
                USER CONTEXT:
                - Age: ${userAge} years old (Adjust volume/intensity for recovery capacity).
                - Height: ${userHeight} cm.
                - Weight: ${userWeight} kg.
                - Goal: ${goal}
                - Schedule: ${days.joinToString()}
                - Duration: ${totalMinutes} minutes per session.
      
                TRAINING HISTORY:
                ${if (historySummary.isBlank()) "No previous history." else historySummary}
                
                AVAILABLE EXERCISES (Use these when possible):
                ${exerciseListString}
                
                STRICT OUTPUT FORMAT: 
                Return a valid JSON object with two root keys: "explanation" and "schedule".
                - "explanation": A string explaining the reasoning for the chosen exercises, progressions, and overall structure of the plan in less than 500 characters.
                - "schedule": A list of daily sessions for **WEEK 1 ONLY**.

                JSON EXAMPLE:
                {
                    "explanation": "Given your age of 45 and goal of hypertrophy, we are prioritizing joint-friendly movements...",                  
                    "schedule": [
                    {
                      "week": 1,
                      "day": "Monday",
                      "title": "Upper Body Power",
                      "exercises": [ 
                        { 
                          "name": "Barbell Bench Press", 
                          "tier": 1, 
                          "sets": 5, 
                          "suggestedReps": 5, 
                          "suggestedLbs": 50.0, 
                          "suggestedRpe": 8, 
                          "estimatedTimeMinutes": 15.0 
                        } 
                      ]
                    }
                  ]
                }

                RULES:
                1. **Generate ONLY Week 1.**
                2. "sets", "suggestedRpe", and "suggestedReps" must be Integers. "suggestedLbs" must be Float.
                3. ${tierDefinitions}
                4. Generate a workout for *each* selected day: ${days.joinToString()}.
                5. A week is from Monday to Sunday.
                6. *** TIME CALCULATION FORMULA ***:
                   'estimatedTimeMinutes' is the total time for all sets of an exercise, including rest.
                   Use these estimates based on the user's rest requirements:
                   - TIER 1: 4.0 minutes per set (Heavy load, long rest).
                   - TIER 2: 3 minutes per set (Moderate load, medium rest).
                   - TIER 3: 2 minutes per set (Light load, short rest).
                   
                   *Adjust volume (sets) to ensure the total minutes sum up to exactly ${totalMinutes}.*
                7. Total duration per day must equal ${totalMinutes} minutes.
                8. If the user is older (>40), prefer lower fatigue exercises and higher rep ranges for joint health unless specified otherwise.

                Do not include preamble or markdown formatting.
            """.trimIndent()

            val userPrompt = "Generate plan for ${userAge}yo male, ${userWeight}kg, Goal: ${goal}."

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

            val response = client.invokeModel(request)
            val responseBody = response.body?.decodeToString() ?: ""

            val outerResponse = jsonConfig.decodeFromString<ClaudeResponse>(responseBody)

            val rawText = outerResponse.content.firstOrNull()?.text ?: ""
            val jsonRegex = Regex("\\{.*\\}", setOf(RegexOption.DOT_MATCHES_ALL))
            val match = jsonRegex.find(rawText)

            if (match != null) {
                val cleanJson = match.value
                Log.d("BedrockService", "Clean JSON: $cleanJson")
                jsonConfig.decodeFromString<GeneratedPlanResponse>(cleanJson)
            } else {
                Log.e("BedrockError", "AI returned invalid format: $rawText")
                GeneratedPlanResponse(explanation = "Error: AI response format invalid.")
            }

        } catch (e: Exception) {
            Log.e("BedrockError", "Error invoking model", e)
            GeneratedPlanResponse(explanation = "Error: ${e.localizedMessage}")
        }
    }
}