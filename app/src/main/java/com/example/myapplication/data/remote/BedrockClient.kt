// app/src/main/java/com/example/myapplication/data/remote/BedrockClient.kt

package com.example.myapplication.data.remote

import android.util.Log
import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelRequest
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelWithResponseStreamRequest
import aws.sdk.kotlin.services.bedrockruntime.model.ResponseStream
import aws.smithy.kotlin.runtime.http.engine.okhttp.OkHttpEngine
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.local.CompletedWorkoutWithExercise
import com.example.myapplication.data.local.ContentSourceEntity
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.local.UserMemoryEntity
import com.example.myapplication.data.local.UserPreferencesRepository
import com.example.myapplication.data.repository.AuthRepository
import com.example.myapplication.data.repository.PromptRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.json.JSONObject
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

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
data class CacheControl(
    val type: String = "ephemeral"
)

@Serializable
data class SystemBlock(
    val type: String = "text",
    val text: String,
    val cache_control: CacheControl? = null
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
data class PropertySchema(
    val type: String,
    val description: String? = null,
    val items: PropertySchema? = null,                   // Used if type is "array"
    val properties: Map<String, PropertySchema>? = null, // Used if type is "object"
    val required: List<String>? = null                   // Used if type is "object"
)

@Serializable
data class ToolInputSchema(
    val type: String = "object",
    val properties: Map<String, PropertySchema>,
    val required: List<String>
)

@Serializable
data class Tool(
    val name: String,
    val description: String,
    val input_schema: ToolInputSchema
)

@Serializable
data class ToolChoice(
    val type: String,
    val name: String? = null
)

@Serializable
data class ClaudeRequest(
    val anthropic_version: String = "bedrock-2023-05-31",
    val max_tokens: Int,
    val system: List<SystemBlock>,
    val messages: List<Message>,
    val tools: List<Tool>? = null,           // <-- NEW
    val tool_choice: ToolChoice? = null
)

@Serializable
data class Message(val role: String, val content: String)

@Serializable
data class ClaudeResponse(
    val content: List<ContentBlock>,
    @SerialName("stop_reason") val stopReason: String? = null,
    val usage: UsageMetrics? = null
)

@Serializable
data class ContentBlock(
    val type: String = "text",
    val text: String? = null,
    val id: String? = null,                                     // <-- NEW (For tools)
    val name: String? = null,                                   // <-- NEW (For tools)
    val input: kotlinx.serialization.json.JsonObject? = null    // <-- NEW (The parsed tool data!)
)

@Serializable
data class UsageMetrics(
    val input_tokens: Int = 0,
    val output_tokens: Int = 0,
    val cache_creation_input_tokens: Int = 0,
    val cache_read_input_tokens: Int = 0
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
    val text: String? = null
)

@Serializable
data class StreamUsage(
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0
)

// --- CONFIGURATION ---
@OptIn(ExperimentalSerializationApi::class)
private val jsonConfig = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
    coerceInputValues = true
    allowTrailingComma = true
    explicitNulls = false
}

private const val tierDefinitions = """
    - Compound: Primary Compound Lifts (High fatigue, high priority, e.g., Squat, Bench, Deadlift).
    - Secondary: Secondary/Accessory Lifts (Moderate fatigue, e.g., Dumbbell Press, Rows).
    - Isolation: Isolation/Correctives (Low fatigue, e.g., Curls, Lateral Raises, Face Pulls).
"""

// Model IDs - Using Inference Profiles
private const val CLAUDE_SONNET = "arn:aws:bedrock:us-east-1:609113669048:inference-profile/us.anthropic.claude-3-7-sonnet-20250219-v1:0"
private const val CLAUDE_HAIKU = "arn:aws:bedrock:us-east-1:609113669048:inference-profile/us.anthropic.claude-3-haiku-20240307-v1:0"

// --- CLIENT CLASS ---
@Singleton
class BedrockClient @Inject constructor(
    private val authRepository: AuthRepository,
    private val promptRepository: PromptRepository,
    private val userPrefs: UserPreferencesRepository
) {

    // We use a getter instead of lazy to ensure we always have fresh credentials if needed
    private fun getClient(): BedrockRuntimeClient {
        val cognitoProvider = CognitoCredentialsProvider(
            authRepository = authRepository,
            identityPoolId = BuildConfig.COGNITO_IDENTITY_POOL_ID,
            region = BuildConfig.AWS_REGION
        )

        return BedrockRuntimeClient {
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

            // 1. DEFINE NESTED SCHEMAS
            val foodItemSchema = PropertySchema(
                type = "object",
                properties = mapOf(
                    "name" to PropertySchema(type = "string", description = "Name of the food item"),
                    "quantity" to PropertySchema(type = "string", description = "Estimated or explicit quantity"),
                    "calories" to PropertySchema(type = "integer", description = "Calories (whole number)"),
                    "protein" to PropertySchema(type = "integer", description = "Protein in grams (whole number)"),
                    "carbs" to PropertySchema(type = "integer", description = "Carbs in grams (whole number)"),
                    "fats" to PropertySchema(type = "integer", description = "Fats in grams (whole number)")
                ),
                required = listOf("name", "quantity", "calories", "protein", "carbs", "fats")
            )

            val macroSummarySchema = PropertySchema(
                type = "object",
                properties = mapOf(
                    "calories" to PropertySchema(type = "integer"),
                    "protein" to PropertySchema(type = "integer"),
                    "carbs" to PropertySchema(type = "integer"),
                    "fats" to PropertySchema(type = "integer")
                ),
                required = listOf("calories", "protein", "carbs", "fats")
            )

            // 2. DEFINE THE MASTER TOOL
            val parseFoodTool = Tool(
                name = "parse_food_log",
                description = "Extracts nutritional information and macros from a user's natural language food log.",
                input_schema = ToolInputSchema(
                    properties = mapOf(
                        "foodItems" to PropertySchema(type = "array", items = foodItemSchema, description = "List of identified food items"),
                        "totalMacros" to macroSummarySchema,
                        "analysis" to PropertySchema(type = "string", description = "A short, encouraging 1-sentence analysis of the meal"),
                        "mealType" to PropertySchema(type = "string", description = "One of: Breakfast, Lunch, Dinner, Snack")
                    ),
                    required = listOf("foodItems", "totalMacros", "analysis", "mealType")
                )
            )

            // 3. INVOKE CLAUDE USING THE TOOL
            val systemBlocks = listOf(SystemBlock(text = systemPrompt))
            val jsonObjectOutput = doInvokeClaudeWithTool(
                systemBlocks = systemBlocks,
                userPrompt = userQuery,
                modelId = CLAUDE_HAIKU,
                tool = parseFoodTool
            )

            // 4. MAP DIRECTLY TO DATA CLASS
            jsonConfig.decodeFromJsonElement<FoodLogResponse>(jsonObjectOutput)

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
        allExercises: List<ExerciseEntity>, // <-- Pass ALL exercises, not filtered
        excludedEquipment: Set<String>,
        // Kept for signature compatibility
        userAge: Int,
        userHeight: Double,
        userWeight: Double,
        block: Int = 1
    ): GeneratedPlanResponse = withContext(Dispatchers.Default) {

        try {
            // 1. DYNAMIC DATA (Changes per user/session - NOT CACHED)
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

            val dynamicUserContext = """
                CRITICAL INSTRUCTION: Ignore any placeholder user stats or history in the previous system prompt. 
                USE THIS EXACT USER DATA FOR YOUR GENERATION:
                Age: $userAge | Ht: ${userHeight.toInt()} in | Wt: ${userWeight.toInt()} lbs
                Schedule: ${days.joinToString()}
                Excluded Equipment: ${if (excludedEquipment.isEmpty()) "None" else excludedEquipment.joinToString()}
                History: ${historySummary.ifBlank { "No previous history." }}
            """.trimIndent()

            // 2. STATIC DATA (Identical for EVERY user - CACHED!)
            val exerciseMasterList = allExercises.joinToString("\n") {
                "- ${it.name} (Muscle: ${it.muscleGroup ?: "General"}, Tier: ${it.tier}, Equip: ${it.equipment})"
            }

            val totalMinutes = (duration * 60).toInt()

            val staticRulesPrompt = promptRepository.getWorkoutSystemPrompt(
                goal = goal,
                programType = programType,
                days = emptyList(),
                totalMinutes = totalMinutes,
                userAge = 0,
                userHeight = 0.0,
                userWeight = 0.0,
                exerciseListString = exerciseMasterList,
                historySummary = ""
            )

            // 3. DEFINE THE NESTED WORKOUT TOOL SCHEMA
            val exerciseSchema = PropertySchema(
                type = "object",
                properties = mapOf(
                    "name" to PropertySchema(type = "string", description = "Name of the exercise (or Circuit title)"),
                    "sets" to PropertySchema(type = "integer", description = "Number of sets (Use 1 for AMRAP/EMOM circuits)"),
                    "suggestedReps" to PropertySchema(type = "integer", description = "Target reps per set"),
                    "suggestedLbs" to PropertySchema(type = "number", description = "Estimated weight in lbs (float)"),
                    "suggestedRpe" to PropertySchema(type = "integer", description = "Target RPE (1-10)"),
                    "tier" to PropertySchema(type = "integer", description = "1=Compound, 2=Secondary, 3=Isolation/Conditioning"),
                    "targetMuscle" to PropertySchema(type = "string"),
                    "isAMRAP" to PropertySchema(type = "boolean"),
                    "isEMOM" to PropertySchema(type = "boolean"),
                    "notes" to PropertySchema(type = "string", description = "Form cues, rest times, or EXACT circuit contents.")
                ),
                required = listOf("name", "sets", "suggestedReps", "suggestedLbs", "suggestedRpe", "tier", "isAMRAP", "isEMOM", "notes")
            )

            val daySchema = PropertySchema(
                type = "object",
                properties = mapOf(
                    "day" to PropertySchema(type = "string", description = "e.g., Monday"),
                    "workoutName" to PropertySchema(type = "string", description = "Title of the workout"),
                    "exercises" to PropertySchema(type = "array", items = exerciseSchema)
                ),
                required = listOf("day", "workoutName", "exercises")
            )

            val planTool = Tool(
                name = "save_workout_plan",
                description = "Saves the structured workout plan to the database.",
                input_schema = ToolInputSchema(
                    properties = mapOf(
                        "explanation" to PropertySchema(type = "string", description = "State the block goal and a brief look-ahead (< 200 chars)"),
                        "mesocycleLengthWeeks" to PropertySchema(type = "integer", description = "Length of the block in weeks (usually 4-6)"),
                        "schedule" to PropertySchema(type = "array", items = daySchema, description = "Exactly ONE WEEK of scheduled workouts")
                    ),
                    required = listOf("explanation", "mesocycleLengthWeeks", "schedule")
                )
            )

            // 4. ASSEMBLE BLOCKS AND INVOKE CLAUDE
            val systemBlocks = listOf(
                SystemBlock(text = staticRulesPrompt, cache_control = CacheControl(type = "ephemeral")),
                SystemBlock(text = dynamicUserContext)
            )

            val jsonObjectOutput = doInvokeClaudeWithTool(
                systemBlocks = systemBlocks,
                userPrompt = "Generate Block $block plan. Do not use my Excluded Equipment. Use the tool to save it.",
                modelId = CLAUDE_SONNET,
                tool = planTool
            )

            Log.d("BedrockClient", "Workout Plan Tool Response Received!")

            // 5. MAP DIRECTLY TO DATA CLASS (No string parsing!)
            jsonConfig.decodeFromJsonElement<GeneratedPlanResponse>(jsonObjectOutput)

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

        // 1. DEFINE THE TOOL SCHEMA
        val exerciseSchema = PropertySchema(
            type = "object",
            properties = mapOf(
                "name" to PropertySchema(type = "string"),
                "sets" to PropertySchema(type = "integer"),
                "suggestedReps" to PropertySchema(type = "integer"),
                "suggestedLbs" to PropertySchema(type = "number"),
                "suggestedRpe" to PropertySchema(type = "integer"),
                "tier" to PropertySchema(type = "integer"),
                "targetMuscle" to PropertySchema(type = "string"),
                "isAMRAP" to PropertySchema(type = "boolean"),
                "isEMOM" to PropertySchema(type = "boolean"),
                "notes" to PropertySchema(type = "string")
            ),
            required = listOf("name", "sets", "suggestedReps", "suggestedLbs", "suggestedRpe", "tier", "isAMRAP", "isEMOM", "notes")
        )

        val negotiationTool = Tool(
            name = "respond_to_user",
            description = "Provides conversational feedback and optionally modifies the live workout plan.",
            input_schema = ToolInputSchema(
                properties = mapOf(
                    "explanation" to PropertySchema(type = "string", description = "Your conversational response to the user (max 3 sentences)."),
                    "exercises" to PropertySchema(type = "array", items = exerciseSchema, description = "List of new or updated exercises. Empty if no changes are needed."),
                    "replacingExerciseName" to PropertySchema(type = "string", description = "The exact name of the current exercise to replace/update, or null if no change.")
                ),
                required = listOf("explanation", "exercises") // replacingExerciseName is intentionally optional
            )
        )

        // 2. INVOKE CLAUDE USING THE TOOL
        try {
            val systemBlocks = listOf(SystemBlock(text = systemPrompt))
            val jsonObjectOutput = doInvokeClaudeWithTool(
                systemBlocks = systemBlocks,
                userPrompt = userMessage,
                modelId = CLAUDE_HAIKU,
                tool = negotiationTool
            )

            // 3. MAP DIRECTLY TO DATA CLASS
            jsonConfig.decodeFromJsonElement<NegotiationResponse>(jsonObjectOutput)

        } catch (e: Exception) {
            Log.e("BedrockError", "Coach interaction failed", e)
            NegotiationResponse("I'm here, but I'm having trouble processing that. Keep going!", emptyList())
        }
    }

    suspend fun generateCoachingCue(
        exerciseName: String,
        issue: String
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

            // 1. DEFINE THE TOOL SCHEMA
            val nutritionTool = Tool(
                name = "save_nutrition_plan",
                description = "Saves the calculated macro plan to the user's database.",
                input_schema = ToolInputSchema(
                    properties = mapOf(
                        "calories" to PropertySchema(type = "string", description = "Total daily calories as a whole number string (e.g., '2500')"),
                        "protein" to PropertySchema(type = "string", description = "Grams of protein as a whole number string"),
                        "carbs" to PropertySchema(type = "string", description = "Grams of carbs as a whole number string"),
                        "fats" to PropertySchema(type = "string", description = "Grams of fats as a whole number string"),
                        "timing" to PropertySchema(type = "string", description = "Short, actionable timing advice"),
                        "explanation" to PropertySchema(type = "string", description = "Brief explanation of how this supports the strategy")
                    ),
                    required = listOf("calories", "protein", "carbs", "fats", "timing", "explanation")
                )
            )

            // 2. INVOKE CLAUDE USING THE TOOL
            val systemBlocks = listOf(SystemBlock(text = systemPrompt))
            val jsonObjectOutput = doInvokeClaudeWithTool(
                systemBlocks = systemBlocks,
                userPrompt = "Calculate my nutrition and save it using the tool.",
                modelId = CLAUDE_HAIKU,
                tool = nutritionTool
            )

            // 3. MAP THE JSON OBJECT DIRECTLY TO OUR DATA CLASS
            jsonConfig.decodeFromJsonElement<RemoteNutritionPlan>(jsonObjectOutput)

        } catch (e: Exception) {
            Log.e("BedrockError", "Nutrition Gen Failed", e)
            RemoteNutritionPlan(explanation = "Error generating plan: ${e.message}")
        }
    }

    suspend fun generateStretchingFlow(
        currentGoal: String,
        availableExercises: List<ExerciseEntity>
    ): GeneratedPlanResponse = withContext(Dispatchers.Default) {
        val exerciseList = availableExercises.joinToString("\n") { "- ${it.name}" }
        val rawPrompt = promptRepository.getStretchingSystemPrompt()
        val systemPrompt = rawPrompt
            .replace("{currentGoal}", currentGoal)
            .replace("{exerciseList}", exerciseList)

        // 1. DEFINE THE TOOL SCHEMA
        val exerciseSchema = PropertySchema(
            type = "object",
            properties = mapOf(
                "name" to PropertySchema(type = "string"),
                "sets" to PropertySchema(type = "integer"),
                "suggestedReps" to PropertySchema(type = "integer", description = "Hold time in SECONDS (30 or 60)"),
                "suggestedLbs" to PropertySchema(type = "number"),
                "suggestedRpe" to PropertySchema(type = "integer"),
                "tier" to PropertySchema(type = "integer"),
                "targetMuscle" to PropertySchema(type = "string"),
                "isAMRAP" to PropertySchema(type = "boolean"),
                "isEMOM" to PropertySchema(type = "boolean"),
                "notes" to PropertySchema(type = "string")
            ),
            required = listOf("name", "sets", "suggestedReps", "suggestedLbs", "suggestedRpe", "tier", "isAMRAP", "isEMOM", "notes")
        )

        val daySchema = PropertySchema(
            type = "object",
            properties = mapOf(
                "day" to PropertySchema(type = "string"),
                "workoutName" to PropertySchema(type = "string"),
                "exercises" to PropertySchema(type = "array", items = exerciseSchema)
            ),
            required = listOf("day", "workoutName", "exercises")
        )

        val planTool = Tool(
            name = "save_workout_plan",
            description = "Saves the structured mobility flow to the database.",
            input_schema = ToolInputSchema(
                properties = mapOf(
                    "explanation" to PropertySchema(type = "string", description = "Coach's reasoning for the mobility flow."),
                    "mesocycleLengthWeeks" to PropertySchema(type = "integer", description = "Default to 1"),
                    "schedule" to PropertySchema(type = "array", items = daySchema)
                ),
                required = listOf("explanation", "mesocycleLengthWeeks", "schedule")
            )
        )

        // 2. INVOKE CLAUDE USING THE TOOL
        try {
            val systemBlocks = listOf(SystemBlock(text = systemPrompt))
            val jsonObjectOutput = doInvokeClaudeWithTool(
                systemBlocks = systemBlocks,
                userPrompt = "Generate Stretching Flow",
                modelId = CLAUDE_HAIKU,
                tool = planTool
            )

            // 3. MAP DIRECTLY TO DATA CLASS
            jsonConfig.decodeFromJsonElement<GeneratedPlanResponse>(jsonObjectOutput)

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
        availableExercises: List<ExerciseEntity>
    ): GeneratedPlanResponse = withContext(Dispatchers.Default) {
        val exerciseList = availableExercises.joinToString("\n") { "- ${it.name} (Muscle: ${it.muscleGroup})" }
        val rawPrompt = promptRepository.getAccessorySystemPrompt()
        val systemPrompt = rawPrompt
            .replace("{currentGoal}", currentGoal)
            .replace("{exerciseList}", exerciseList)

        // 1. DEFINE THE TOOL SCHEMA
        val exerciseSchema = PropertySchema(
            type = "object",
            properties = mapOf(
                "name" to PropertySchema(type = "string"),
                "sets" to PropertySchema(type = "integer"),
                "suggestedReps" to PropertySchema(type = "integer"),
                "suggestedLbs" to PropertySchema(type = "number"),
                "suggestedRpe" to PropertySchema(type = "integer"),
                "tier" to PropertySchema(type = "integer"),
                "targetMuscle" to PropertySchema(type = "string"),
                "isAMRAP" to PropertySchema(type = "boolean"),
                "isEMOM" to PropertySchema(type = "boolean"),
                "notes" to PropertySchema(type = "string")
            ),
            required = listOf("name", "sets", "suggestedReps", "suggestedLbs", "suggestedRpe", "tier", "isAMRAP", "isEMOM", "notes")
        )

        val daySchema = PropertySchema(
            type = "object",
            properties = mapOf(
                "day" to PropertySchema(type = "string"),
                "workoutName" to PropertySchema(type = "string"),
                "exercises" to PropertySchema(type = "array", items = exerciseSchema)
            ),
            required = listOf("day", "workoutName", "exercises")
        )

        val planTool = Tool(
            name = "save_workout_plan",
            description = "Saves the structured accessory plan to the database.",
            input_schema = ToolInputSchema(
                properties = mapOf(
                    "explanation" to PropertySchema(type = "string", description = "Coach's reasoning for the selection."),
                    "mesocycleLengthWeeks" to PropertySchema(type = "integer", description = "Default to 1"),
                    "schedule" to PropertySchema(type = "array", items = daySchema)
                ),
                required = listOf("explanation", "mesocycleLengthWeeks", "schedule")
            )
        )

        // 2. INVOKE CLAUDE USING THE TOOL
        try {
            val systemBlocks = listOf(SystemBlock(text = systemPrompt))
            val jsonObjectOutput = doInvokeClaudeWithTool(
                systemBlocks = systemBlocks,
                userPrompt = "Generate Accessory Work",
                modelId = CLAUDE_HAIKU,
                tool = planTool
            )

            // 3. MAP DIRECTLY TO DATA CLASS
            jsonConfig.decodeFromJsonElement<GeneratedPlanResponse>(jsonObjectOutput)

        } catch (e: Exception) {
            Log.e("Bedrock", "Accessory parse failed", e)
            GeneratedPlanResponse(explanation = "Failed to generate accessory work.")
        }
    }

    suspend fun generateKnowledgeBriefing(
        content: List<ContentSourceEntity>,
        workoutTitle: String? = null
    ): String = withContext(Dispatchers.Default) {
        if (content.isEmpty() && workoutTitle == null) return@withContext "No No news yet."

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
            system = listOf(SystemBlock(text = systemPrompt)),
            messages = sanitizedMessages
        )

        val jsonString = jsonConfig.encodeToString(ClaudeRequest.serializer(), requestBody)

        // --- NEW LOGGING: Intercept the streaming conversation request ---
        try {
            val prettyJson = JSONObject(jsonString).toString(4)
            Log.d("BedrockClient", "=================== BEDROCK STREAMING REQUEST ===================")
            Log.d("BedrockClient", "MODEL: $CLAUDE_HAIKU")
            Log.d("BedrockClient", "PAYLOAD:\n$prettyJson")
            Log.d("BedrockClient", "===============================================================")
        } catch (e: Exception) {
            Log.d("BedrockClient", "Failed to pretty-print JSON payload. Raw: $jsonString")
        }

        val request = InvokeModelWithResponseStreamRequest {
            this.modelId = CLAUDE_HAIKU
            contentType = "application/json"
            accept = "application/json"
            body = jsonString.toByteArray()
        }

        val fullText = StringBuilder()

        try {
            getClient().invokeModelWithResponseStream(request) { response ->
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
        modelId: String = CLAUDE_HAIKU // <-- Added default value
    ): String {
        if (userPrompt.isBlank()) return "{}"

        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                return doInvokeClaude(systemPrompt, userPrompt, modelId)
            } catch (e: Exception) {
                lastError = e
                if (e is CancellationException) throw e

                val errName = e.javaClass.simpleName
                Log.w("BedrockClient", "InvokeClaude attempt ${attempt + 1} failed: $errName - ${e.message}")

                if (attempt < 2) {
                    kotlinx.coroutines.delay((attempt + 1) * 2000L)
                } else {
                    Log.e("BedrockClient", "InvokeClaude failed completely after 3 attempts.", e)
                    throw e
                }
            }
        }
        throw lastError ?: Exception("Unknown error")
    }

    private suspend fun doInvokeClaude(
        systemPrompt: String,
        userPrompt: String,
        modelId: String
    ): String {
        enforceLimit()
        val standardBlock = listOf(SystemBlock(text = systemPrompt))
        return doInvokeClaudeWithBlocks(standardBlock, userPrompt, modelId)
    }

    private suspend fun doInvokeClaudeWithBlocks(
        systemBlocks: List<SystemBlock>,
        userPrompt: String,
        modelId: String
    ): String {
        enforceLimit()

        val maxTokensToUse = if (modelId.contains("sonnet", ignoreCase = true)) 8192 else 4096

        val requestBody = ClaudeRequest(
            max_tokens = maxTokensToUse,
            system = systemBlocks,
            messages = listOf(Message(role = "user", content = userPrompt))
        )

        val jsonString = jsonConfig.encodeToString(ClaudeRequest.serializer(), requestBody)

        // --- NEW LOGGING: Intercept standard Bedrock request ---
        try {
            val prettyJson = JSONObject(jsonString).toString(4)
            Log.d("BedrockClient", "=================== BEDROCK REQUEST (STANDARD) ===================")
            Log.d("BedrockClient", "MODEL: $modelId")
            Log.d("BedrockClient", "PAYLOAD:\n$prettyJson")
            Log.d("BedrockClient", "==================================================================")
        } catch (e: Exception) {
            Log.d("BedrockClient", "Failed to pretty-print JSON payload. Raw: $jsonString")
        }

        val request = InvokeModelRequest {
            this.modelId = modelId
            contentType = "application/json"
            accept = "application/json"
            body = jsonString.toByteArray()
        }

        val response = getClient().invokeModel(request)
        val responseBody = response.body?.decodeToString() ?: ""

        Log.e("PROMPT_CACHE", "RAW AWS RESPONSE: $responseBody")

        val outerResponse = jsonConfig.decodeFromString<ClaudeResponse>(responseBody)

        // --- LOG CACHE METRICS TO ANDROID STUDIO ---
        outerResponse.usage?.let { u ->
            Log.e("PROMPT_CACHE", """
                🔥 CACHE METRICS 🔥
                Standard Input Tokens: ${u.input_tokens}
                Cache CREATED (Paid full price): ${u.cache_creation_input_tokens}
                Cache READ (90% Discount!): ${u.cache_read_input_tokens}
            """.trimIndent())
        }

        val rawText = outerResponse.content.firstOrNull { it.type == "text" }?.text ?: ""

        if (outerResponse.stopReason == "max_tokens") {
            Log.w("BedrockClient", "Model stopped due to max_tokens. Response may be incomplete.")
        }

        return extractJsonFromText(rawText)
    }

    private suspend fun doInvokeClaudeWithTool(
        systemBlocks: List<SystemBlock>,
        userPrompt: String,
        modelId: String,
        tool: Tool
    ): kotlinx.serialization.json.JsonObject {
        enforceLimit()

        val maxTokensToUse = if (modelId.contains("sonnet", ignoreCase = true)) 8192 else 4096

        val requestBody = ClaudeRequest(
            max_tokens = maxTokensToUse,
            system = systemBlocks,
            messages = listOf(Message(role = "user", content = userPrompt)),
            tools = listOf(tool),
            tool_choice = ToolChoice(type = "tool", name = tool.name)
        )

        val jsonString = jsonConfig.encodeToString(ClaudeRequest.serializer(), requestBody)

        // --- NEW LOGGING: Intercept Tool Use Bedrock request ---
        try {
            val prettyJson = JSONObject(jsonString).toString(4)
            Log.d("BedrockClient", "=================== BEDROCK REQUEST (TOOL USE) ===================")
            Log.d("BedrockClient", "MODEL: $modelId")
            Log.d("BedrockClient", "PAYLOAD:\n$prettyJson")
            Log.d("BedrockClient", "==================================================================")
        } catch (e: Exception) {
            Log.d("BedrockClient", "Failed to pretty-print JSON payload. Raw: $jsonString")
        }

        val request = InvokeModelRequest {
            this.modelId = modelId
            contentType = "application/json"
            accept = "application/json"
            body = jsonString.toByteArray()
        }

        val response = getClient().invokeModel(request)
        val responseBody = response.body?.decodeToString() ?: ""

        val outerResponse = jsonConfig.decodeFromString<ClaudeResponse>(responseBody)

        // --- KEEP LOGGING CACHE METRICS! ---
        outerResponse.usage?.let { u ->
            Log.e("PROMPT_CACHE", """
                🔥 CACHE METRICS (TOOL USE) 🔥
                Standard Input: ${u.input_tokens}
                Cache CREATED: ${u.cache_creation_input_tokens}
                Cache READ: ${u.cache_read_input_tokens}
            """.trimIndent())
        }

        val toolUseBlock = outerResponse.content.firstOrNull { it.type == "tool_use" }
        return toolUseBlock?.input ?: throw Exception("Claude failed to invoke the requested tool.")
    }
    suspend fun convertWorkoutForHome(
        originalExercises: List<String>,
        homeEquipment: Set<String>,
        allExercises: List<ExerciseEntity>
    ): List<ExerciseEntity> {

        val equipmentStr = homeEquipment.joinToString(", ")
        val originalStr = originalExercises.joinToString(", ")
        val catalog = allExercises.joinToString(", ") { it.name }

        // FIX 1: We explicitly ask for a JSON object instead of a comma-separated list
        val prompt = """
            You are an elite fitness AI. The user cannot go to the gym. 
            Their active workout contains: [$originalStr].
            Their home equipment is EXACTLY: [$equipmentStr]. 
            
            Task: Replace each exercise with the best equivalent from the provided catalog that targets the same muscles but STRICTLY uses ONLY the available equipment. If an exercise is already compliant, keep it. If they have 'None', use pure floor bodyweight.
            
            Catalog: [$catalog]
            
            Return ONLY a JSON object with a single key "exercises" containing an array of the new exercise names as strings. Do not include extra text.
            Example: {"exercises": ["Push-ups", "Jump Squats"]}
        """.trimIndent()

        return try {
            val responseText = invokeClaude(
                systemPrompt = prompt,
                userPrompt = "Convert this workout based on my home equipment.",
                modelId = CLAUDE_HAIKU
            )

            // FIX 2: We parse the JSON array returned by the AI
            val json = JSONObject(responseText)
            val jsonArray = json.getJSONArray("exercises")

            val returnedNames = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                returnedNames.add(jsonArray.getString(i).trim().lowercase())
            }

            returnedNames.mapNotNull { name ->
                allExercises.find { it.name.lowercase() == name }
            }
        } catch (e: Exception) {
            Log.e("BedrockClient", "Failed to convert or parse home workout", e)
            emptyList()
        }
    }
    private suspend fun doInvokeClaudeStreaming(
        systemPrompt: String,
        userPrompt: String,
        modelId: String
    ): String {
        enforceLimit()

        val requestBody = ClaudeRequest(
            max_tokens = 4096,
            system = listOf(SystemBlock(text = systemPrompt)),
            messages = listOf(Message(role = "user", content = userPrompt))
        )

        val jsonString = jsonConfig.encodeToString(ClaudeRequest.serializer(), requestBody)

        // --- NEW LOGGING: Intercept generic streaming request ---
        try {
            val prettyJson = JSONObject(jsonString).toString(4)
            Log.d("BedrockClient", "=================== BEDROCK REQUEST (STREAMING) ===================")
            Log.d("BedrockClient", "MODEL: $modelId")
            Log.d("BedrockClient", "PAYLOAD:\n$prettyJson")
            Log.d("BedrockClient", "===================================================================")
        } catch (e: Exception) {
            Log.d("BedrockClient", "Failed to pretty-print JSON payload. Raw: $jsonString")
        }

        val request = InvokeModelWithResponseStreamRequest {
            this.modelId = modelId
            contentType = "application/json"
            accept = "application/json"
            body = jsonString.toByteArray()
        }

        val fullText = StringBuilder()

        getClient().invokeModelWithResponseStream(request) { response ->
            response.body?.collect { event ->
                when (event) {
                    is ResponseStream.Chunk -> {
                        val chunkText = event.value.bytes?.decodeToString() ?: ""
                        try {
                            val streamEvent = jsonConfig.decodeFromString<StreamEvent>(chunkText)
                            when (streamEvent.type) {
                                "content_block_delta" -> {
                                    streamEvent.delta?.text?.let { fullText.append(it) }
                                }
                            }
                        } catch (e: Exception) {}
                    }
                    else -> {}
                }
            }
        }

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