package com.example.myapplication.data.remote

import android.util.Log
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelRequest
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
// FIX: Changed OkHttp to OkHttpEngine
import aws.smithy.kotlin.runtime.http.engine.okhttp.OkHttpEngine
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.local.CompletedWorkoutEntity
import com.example.myapplication.data.local.ExerciseEntity
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// --- AI RESPONSE DATA MODEL ---

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
    val muscleGroup: String = "General",
    val equipment: String = "Unspecified",
    val tier: Int = 1,
    val loadability: String = "Medium",
    val fatigue: String = "Medium",
    val notes: String = "",
    val suggestedReps: Int = 5,
    val suggestedRpe: Int = 7,
    val sets: Int = 3,
    val estimatedTimeMinutes: Int = 0,
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
    allowTrailingComma= true // <--- ADD THIS LINE
}

// --- MAIN FUNCTION ---

suspend fun invokeBedrock(
    goal: String,
    programType: String,
    days: List<String>,
    duration: Float,
    workoutHistory: List<CompletedWorkoutEntity>,
    availableExercises: List<ExerciseEntity>
): GeneratedPlanResponse {

    try {
        val client = BedrockRuntimeClient {
            region = BuildConfig.AWS_REGION
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = BuildConfig.AWS_ACCESS_KEY
                secretAccessKey = BuildConfig.AWS_SECRET_KEY
            }
            // FIX: Use OkHttpEngine and correct property names
            httpClient = OkHttpEngine {
                connectTimeout = 60.seconds
                // socketReadTimeout is the correct property name for read timeouts in this engine
                socketReadTimeout = 120.seconds
            }
        }

        val tierDefinitions = when (programType) {
            "Strength" -> """
                - Tier 1: 1-5 Reps (Squat/Deadlift).
                - Tier 2: 5-8 Reps.
                - Tier 3: 8-12 Reps.
            """.trimIndent()

            "Hypertrophy" -> """
                - Tier 1: 6-10 Reps.
                - Tier 2: 10-15 Reps.
                - Tier 3: 15-25+ Reps.
            """.trimIndent()

            "Powerlifting" -> "- Tier 1: SBD Competition Lifts ONLY."
            else -> "Use standard progressive overload."
        }

        val historyString = workoutHistory.joinToString(separator = "\n") { item ->
            "- ${item.date}: ${item.reps} reps at ${item.weight} lbs (RPE ${item.rpe})"
        }
        val exerciseListString = availableExercises.joinToString("/n") { ex ->
            "- ${ex.name} (${ex.tier}, ${ex.estimatedTimePerSet} mins/set): ${ex.notes}"
        }
        val totalMinutes = (duration * 60).toInt()

        val systemPrompt = """
            You are an expert strength coach. Generate a workout plan for 4 full weeks.
            USER CONTEXT:
            - Goal: ${goal}
            - Schedule: ${days.joinToString()}
            - Duration: ${totalMinutes} minutes per session.
            - History: ${historyString}
  
            AVAILABLE EXERCISES (Use these when possible):
            ${exerciseListString}
            
            STRICT OUTPUT FORMAT: 
            Return a valid JSON object with two root keys: "explanation" and "schedule".
            - "explanation": A string explaining the reasoning for the chosen exercises, progressions, and overall structure of the plan.
            - "schedule": A list of daily sessions for all 4 weeks.

            JSON EXAMPLE:
            {
              "explanation": "This plan is designed to increase your strength on the main lifts by using a block periodization model...",
              "schedule": [
                {
                  "week": 1,
                  "day": "Monday",
                  "title": "Upper Body Power",
                  "exercises": [ { "name": "Barbell Bench Press", "tier": 1, "sets": 5, "suggestedReps": 5, "estimatedTimeMinutes": 15 } ]
                },              
              ]
            }

            RULES:
            1. Generate a plan for 4 full weeks.
            2. "sets", "suggestedRpe", "suggestedReps", and "estimatedTimeMinutes" must be Integers.
            3. ${tierDefinitions}
            4. The user has selected the following days: ${days.joinToString()}. Generate a workout for *each* of these selected days for all 4 weeks.
            5. A week is from Monday to Sunday.
            
            6. *** CRITICAL TIME CALCULATION FORMULA ***:
               'estimatedTimeMinutes' is the total time for all sets of an exercise, including rest.
               Use this exact math:
               - TIER 1 (Heavy Compound): 3 minutes per set.
               - TIER 2 (Accessory): 2.5 minutes per set.
               - TIER 3 (Isolation): 2 minutes per set.
               
               EXAMPLE: 
               - Arnold Press is Tier 2 and has 4 sets.
               - Time = 4 * 2.5 = 10 minutes.

            7. TOTAL DURATION:
               The sum of all 'estimatedTimeMinutes' for a single day's workout must equal the user's requested duration of ${totalMinutes} minutes.
               - Add or remove exercises or sets to meet the duration.

            8. CONSIDER USER HISTORY FOR PROGRESSIVE OVERLOAD.

            Do not include preamble or markdown formatting.
        """.trimIndent()

        val userPrompt = "Goal: ${goal}. Schedule: ${days.joinToString()}. Session Duration: ${duration} hours."

        val requestBody = ClaudeRequest(
            max_tokens = 8192,
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
        val rawJson = outerResponse.content.firstOrNull()?.text ?: "{}"

        val cleanJson = rawJson.replace("```json", "").replace("```", "").trim()

        Log.d("BedrockService", "Clean JSON: ${cleanJson}")

        val planResponse = jsonConfig.decodeFromString<GeneratedPlanResponse>(cleanJson)

        client.close()

        return planResponse

    } catch (e: Exception) {
        Log.e("BedrockError", "Error invoking model", e)
        return GeneratedPlanResponse()
    }
}
