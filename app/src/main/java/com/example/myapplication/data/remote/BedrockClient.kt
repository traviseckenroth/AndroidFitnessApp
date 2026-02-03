package com.example.myapplication.data.remote

import android.util.Log
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelRequest
import aws.smithy.kotlin.runtime.http.engine.okhttp.OkHttpEngine
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.local.CompletedWorkoutWithExercise
import com.example.myapplication.data.local.ExerciseEntity
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

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
class BedrockClient @Inject constructor() {

    // OPTIMIZATION: Initialize client once and reuse it across calls
    private val client by lazy {
        BedrockRuntimeClient {
            region = BuildConfig.AWS_REGION
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = BuildConfig.AWS_ACCESS_KEY
                secretAccessKey = BuildConfig.AWS_SECRET_KEY
            }
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
    ): GeneratedPlanResponse {

        try {
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
                "- ${item.completedWorkout.date}: ${item.completedWorkout.reps} reps at ${item.completedWorkout.weight} lbs (RPE ${item.completedWorkout.rpe})"
            }

            val exerciseListString = availableExercises.joinToString("\n") { ex ->
                "- ${ex.name} (Tier ${ex.tier}, ${ex.estimatedTimePerSet} mins/set)"
            }

            val totalMinutes = (duration * 60).toInt()
            
// OPTIMIZATION: Summarize history instead of dumping raw logs
// This saves tokens and gives the AI better "reasoning" data
            val historySummary = workoutHistory
                .groupBy { it.exercise.name }
                .map { (name, sessions) ->
                    val sorted = sessions.sortedBy { it.completedWorkout.date }
                    val first = sorted.first().completedWorkout
                    val last = sorted.last().completedWorkout
                    val progress = last.weight - first.weight
                    "- $name: Started at ${first.weight}lbs, currently at ${last.weight}lbs (Avg RPE: ${sessions.map { it.completedWorkout.rpe }.average().toInt()})"
                }.joinToString("\n")

            val systemPrompt = """
                You are an expert strength coach. Generate a **1-week workout template** that will be repeated for a 4-week block.
                USER CONTEXT:
                - Age: ${'$'}{userAge} years old (Adjust volume/intensity for recovery capacity).
                - Height: ${'$'}{userHeight} cm.
                - Weight: ${'$'}{userWeight} kg.
                - Goal: ${goal}
                - Schedule: ${days.joinToString()}
                - Duration: ${totalMinutes} minutes per session.
                - History: ${historyString}
      
                TRAINING HISTORY:
                ${'$'}{if (historySummary.isBlank()) "No previous history available." else historySummary}
                
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
                3. ${'$'}{tierDefinitions}
                4. Generate a workout for *each* selected day: ${'$'}{days.joinToString()}.
                5. A week is from Monday to Sunday.
                6. Time Calculation: Tier 1 (3m/set), Tier 2 (2.5m/set), Tier 3 (2m/set).
                7. Total duration per day must equal ${'$'}{totalMinutes} minutes.
                8. If the user is older (>40), prefer lower fatigue exercises and higher rep ranges for joint health unless specified otherwise.

                Do not include preamble or markdown formatting.
                
                USER HISTORY SUMMARY (Last Block):
                ${'$'}historySummary
                    
            """.trimIndent()

            val userPrompt = "Generate plan for ${userAge}yo male, ${userWeight}kg, Goal: ${goal}."

            val requestBody = ClaudeRequest(
                max_tokens = 12000,
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

            // Reuse the class-level client
            val response = client.invokeModel(request)
            val responseBody = response.body?.decodeToString() ?: ""

            val outerResponse = jsonConfig.decodeFromString<ClaudeResponse>(responseBody)
            val rawJson = outerResponse.content.firstOrNull()?.text ?: "{}"

            val cleanJson = rawJson.replace("```json", "").replace("```", "").trim()

            Log.d("BedrockService", "Clean JSON: ${cleanJson}")

            return jsonConfig.decodeFromString<GeneratedPlanResponse>(cleanJson)

        } catch (e: Exception) {
            Log.e("BedrockError", "Error invoking model", e)
            return GeneratedPlanResponse(explanation = "Error: ${e.localizedMessage}")
        }
    }
}
