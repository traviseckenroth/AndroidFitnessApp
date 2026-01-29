package com.example.myapplication.data.remote

import android.util.Log
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.local.CompletedWorkoutEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelRequest
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials

// --- AI RESPONSE DATA MODEL ---

@Serializable
data class GeneratedPlanResponse(
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
    val estimatedTimeMinutes: Int = 10
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
}

// --- MAIN FUNCTION ---

suspend fun invokeBedrock(
    goal: String,
    programType: String,
    days: List<String>,
    duration: Float,
    workoutHistory: List<CompletedWorkoutEntity>
): List<GeneratedDay> {

    try {
        val client = BedrockRuntimeClient {
            region = BuildConfig.AWS_REGION
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = BuildConfig.AWS_ACCESS_KEY
                secretAccessKey = BuildConfig.AWS_SECRET_KEY
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

        val totalMinutes = (duration * 60).toInt()

        val systemPrompt = """
            You are an expert strength coach. Create a 4-week training block.

            USER HISTORY:
            $historyString

            STRICT OUTPUT FORMAT:
            Return a valid JSON object with a single root key "schedule".
            Inside "schedule", include a list of daily sessions.

            JSON EXAMPLE:
            {
              "schedule": [
                {
                  "week": 1,
                  "day": "Monday",
                  "title": "Upper Body Power",
                  "exercises": [
                    {
                      "name": "Barbell Bench Press",
                      "muscleGroup": "Chest",
                      "equipment": "Barbell",
                      "tier": 1,
                      "loadability": "High",
                      "fatigue": "High",
                      "notes": "Retract scapula",
                      "suggestedReps": 5,
                      "suggestedRpe": 8,
                      "sets": 3,
                      "estimatedTimeMinutes": 15
                    }
                  ]
                }
              ]
            }

            RULES:
            1. "sets", "suggestedRpe", and "estimatedTimeMinutes" must be Integers.
            2. $tierDefinitions
            3. The user has selected the following days: ${days.joinToString()}. Generate a workout for *each* of these selected days within *each* week of the 4-week plan.
            4. A week is from Monday to Sunday. All selected days must be grouped within the same week number (e.g., all selected days appear for week 1, then all appear for week 2, etc.).
            5. The total `estimatedTimeMinutes` for a daily workout MUST add up to be as close as possible to the user's requested session duration of $totalMinutes minutes. Add or remove exercises to meet this duration.
            6. Prescribe a `suggestedRpe` for each exercise based on the program type and tier.
            7. CONSIDER USER HISTORY FOR PROGRESSIVE OVERLOAD.

            Do not include preamble or markdown formatting.
        """.trimIndent()

        val userPrompt = "Goal: $goal. Schedule: ${days.joinToString()}. Session Duration: $duration hours."

        val requestBody = ClaudeRequest(
            max_tokens = 4096,
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

        Log.d("BedrockService", "Clean JSON: $cleanJson")

        val planResponse = jsonConfig.decodeFromString<GeneratedPlanResponse>(cleanJson)

        client.close()

        return planResponse.schedule

    } catch (e: Exception) {
        Log.e("BedrockError", "Error invoking model", e)
        return emptyList()
    }
}
