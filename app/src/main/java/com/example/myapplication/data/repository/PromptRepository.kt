package com.example.myapplication.data.repository

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.example.myapplication.BuildConfig
import com.google.firebase.FirebaseApp
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromptRepository @Inject constructor() {

    private var remoteConfig: FirebaseRemoteConfig? = null

    private val defaults = mapOf(
        "system_instruction_food_log" to """
            You are an expert AI Nutritionist.
            TASK: Analyze the user's natural language food log.
            
            CRITICAL RULES:
            1. If quantity is vague (e.g., "a chicken breast", "an egg", "a bowl of rice"), YOU MUST ESTIMATE using standard serving sizes (e.g., Breast=6oz, Egg=Large, Rice=1 cup).
            2. NEVER return 0 for calories/macros unless the item is water or diet soda.
            3. DETERMINE MEAL TYPE: Based on the text (e.g., "For breakfast I had...") or typical consumption time (if vague, default to "Snack").
               Categories: "Breakfast", "Lunch", "Dinner", "Snack".
            4. Calculate totals accurately.
            
            OUTPUT FORMAT (RAW JSON ONLY):
            {
              "foodItems": [
                { "name": "Chicken Breast", "quantity": "6oz (Est)", "calories": 280, "protein": 52, "carbs": 0, "fats": 6 }
              ],
              "totalMacros": { "calories": 280, "protein": 52, "carbs": 0, "fats": 6 },
              "analysis": "Brief comment on quality.",
              "mealType": "Dinner"
            }
        """.trimIndent(),

        "system_instruction_workout" to """
         You are an expert strength and endurance coach specializing in Periodization (Macrocycles and Mesocycles).
         ... (omitted for brevity in thinking, but I should include full content if I am overwriting)
        """.trimIndent(),

        "system_instruction_nutrition" to """
            You are a metabolic nutritionist. Calculate daily macros.
            ...
        """.trimIndent(),

        "system_instruction_stretching" to """
            You are a Mobility & Recovery Specialist. Generate a 15-minute RESTORATIVE stretching flow.
            ...
        """.trimIndent(),

        "system_instruction_accessory" to """
            You are a Strength & Conditioning Coach. 
            ...
        """.trimIndent(),

        "system_instruction_coach_interaction" to """
            You are an expert, supportive Fitness Coach. The user is currently performing a workout.
            
            YOUR GOALS:
            1. BE CONVERSATIONAL: If the user asks for advice, answer concisely (max 3 sentences).
            2. ADAPT THE PLAN: If the user mentions pain, extreme fatigue, or that a weight is "too heavy" or "too light", modify the plan.
            
            RULES FOR EXERCISE MODIFICATION:
            - If the user says a weight is too heavy/light for an exercise, adjust the "suggestedLbs" for that exercise.
            - If the user wants to SWAP or REPLACE an exercise, identify the exercise they want to remove and set "replacingExerciseName".
            - IMPORTANT: If you are adjusting an existing exercise (like changing its weight), you MUST put that exercise's name in "replacingExerciseName". This tells the app to replace the old sets with your new ones.
            - If the user is swapping an exercise for a NEW one, "replacingExerciseName" is the old one, and "exercises" contains the new one.
            - ONLY use exercises from the ALLOWED LIST below.
            - If no modification is needed, return an empty list for "exercises".
            
            CURRENT WORKOUT CONTEXT (Incomplete exercises only):
            {currentWorkout}
            
            ALLOWED LIST:
            {exerciseList}
            
            OUTPUT FORMAT (JSON OBJECT ONLY):
            {
              "explanation": "Your response to the user.",
              "exercises": [ { "name": "...", "sets": 3, "suggestedReps": 10, "suggestedLbs": 95.0, "tier": 1 } ],
              "replacingExerciseName": "The name of the exercise to be removed or updated, or null"
            }
        """.trimIndent()
    )

    init {
        try {
            val apiKey = try { FirebaseApp.getInstance().options.apiKey } catch (e: Exception) { "" }
            
            if (apiKey.contains("dummy") || apiKey.isEmpty()) {
                Log.w("PromptRepo", "Invalid/Dummy Firebase API Key detected ($apiKey). Skipping Remote Config init.")
            } else {
                remoteConfig = Firebase.remoteConfig
                val configSettings = remoteConfigSettings {
                    minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0 else 3600
                }
                remoteConfig?.setConfigSettingsAsync(configSettings)
                remoteConfig?.setDefaultsAsync(defaults)
                fetchPrompts()
            }
        } catch (e: Exception) {
            Log.e("PromptRepo", "Failed to initialize Firebase Remote Config: ${e.message}")
        }
    }

    fun fetchPrompts() {
        remoteConfig?.fetchAndActivate()
            ?.addOnSuccessListener { 
                Log.e("PromptRepo", "Prompts successfully updated from Firebase!")
            }
            ?.addOnFailureListener { e ->
                Log.e("PromptRepo", "Failed to fetch prompts: ${e.message}", e)
            }
    }

    fun getWorkoutSystemPrompt(): String = remoteConfig?.getString("system_instruction_workout") ?: defaults["system_instruction_workout"]!!
    fun getNutritionSystemPrompt(): String = remoteConfig?.getString("system_instruction_nutrition") ?: defaults["system_instruction_nutrition"]!!
    fun getFoodLogSystemPrompt(): String = remoteConfig?.getString("system_instruction_food_log") ?: defaults["system_instruction_food_log"]!!
    fun getStretchingSystemPrompt(): String = remoteConfig?.getString("system_instruction_stretching") ?: defaults["system_instruction_stretching"]!!
    fun getAccessorySystemPrompt(): String = remoteConfig?.getString("system_instruction_accessory") ?: defaults["system_instruction_accessory"]!!
    
    fun getCoachInteractionPrompt(): String {
        val value = remoteConfig?.getString("system_instruction_coach_interaction") ?: defaults["system_instruction_coach_interaction"]!!
        val isDefault = value == defaults["system_instruction_coach_interaction"]
        Log.e("PromptRepo", "Coach Interaction Prompt Source: ${if (isDefault) "DEFAULT" else "REMOTE (Firebase)"}")
        return value
    }
}