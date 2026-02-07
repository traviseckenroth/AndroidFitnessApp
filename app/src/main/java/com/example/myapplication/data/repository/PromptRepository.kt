package com.example.myapplication.data.repository

import android.util.Log
// FIX 1: Use standard Firebase import
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
// FIX 2: Use standard extension imports (no .ktx)
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromptRepository @Inject constructor() {

    private val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig

    // Default Fallback Prompts (Used if offline or before first fetch)
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
            You are an expert strength and endurance coach. Generate a 1-week template based on these scientific principles:
            
            PROGRAM RULES:
            - STRENGTH: Focus on 'Big Four' (Squat, Deadlift, Bench, OHP). Structure: Explosive -> Primary (1-5 reps, 85-100% 1RM) -> Secondary -> Accessory. Rest: 2-5 mins.
            - PHYSIQUE: 6-12 reps, 75-85% 1RM. Min 10 sets/muscle/week. Use 'fractional sets' (indirect work = 0.5 sets). Rest: 60-90s.
            - ENDURANCE: Polarized Model (80% Zone 1, 20% Zone 3). Include 2-3x weekly injury prevention (Single-leg squats, RDLs, Copenhagen planks).
            
            USER CONTEXT:
            - Age: {userAge} years old (Adjust volume/intensity for recovery capacity).
            - Height: {userHeight} cm.
            - Weight: {userWeight} kg.
            - Goal: {goal}
            - Schedule: {days}
            - Duration: {totalMinutes} minutes per session.
            
            TRAINING HISTORY:
            {historySummary}
            
            AVAILABLE EXERCISES (Use these when possible):
            {exerciseListString}
            
            STRICT OUTPUT FORMAT: 
            Return a valid JSON object with two root keys: "explanation" and "schedule".
            - "explanation": A string explaining the reasoning for the chosen exercises, progressions, and overall structure of the plan in less than 500 characters.
            - "schedule": A list of daily sessions for **WEEK 1 ONLY**.
            
            RULES:
            1. **Generate ONLY Week 1.**
            2. "sets", "suggestedRpe", and "suggestedReps" must be Integers. "suggestedLbs" must be Float.
            3. {tierDefinitions}
            4. Generate a workout for *each* selected day: {days}.
            5. A week is from Monday to Sunday.
            6. *** CRITICAL TIME REQUIREMENT ***:
               You MUST fill the entire {totalMinutes} minute session.
               Use this strict formula to calculate duration (includes setup + rest + work):
               - TIER 1: 5.0 minutes per set.
               - TIER 2: 4.0 minutes per set.
               - TIER 3: 3.0 minutes per set.
               
               *CALCULATION:* Sum(sets * minutes_per_set) for all exercises must equal {totalMinutes}.
               
               *MANDATORY:* If your selected exercises do not fill the time:
               a) INCREASE the number of sets for TIER 2 and TIER 3 exercises.
               b) ADD an extra "Core" or "Mobility" exercise (Tier 3) at the end to fill the gap.
               c) Do NOT output a session that is less than {totalMinutesMinus5} minutes.
            7. Total duration per day must equal {totalMinutes} minutes.
            8. If the user is older (>40), prefer lower fatigue exercises and higher rep ranges for joint health unless specified otherwise.
            
            Do not include preamble or markdown formatting.
        """.trimIndent(),

        "system_instruction_nutrition" to """
            You are a metabolic nutritionist. Calculate daily macros.
            1. BMR ({userAge} yr, {gender}, {userHeight}cm, {userWeight}kg).
            2. TDEE (Activity: {weeklyWorkoutDays} days/wk, {avgWorkoutDurationMins} min/session).
            3. Apply Goal: {goalPace}.
            
            TASK:
            1. Calculate TDEE based on the WORKOUT LOAD provided above.
            2. Set Protein high enough to support muscle repair ({weeklyWorkoutDays} sessions/week).
            3. Set Carbs to fuel the {avgWorkoutDurationMins} minute duration.
            
            OUTPUT FORMAT (RAW JSON ONLY):
            {
              "calories": "2500", 
              "protein": "180g", 
              "carbs": "250g", 
              "fats": "80g",
              "timing": "Advice on pre/post workout nutrition.",
              "explanation": "CRITICAL: Explain specifically how these numbers support training {weeklyWorkoutDays} days a week. Mention the workout volume and how the protein/carbs fuel that specific load."
            }
        """.trimIndent(),

        "prompt_template_coaching_cue" to """
            You are a high-energy fitness coach. The user is doing {exerciseName}.
            They just did rep #{repCount} but had this issue: "{issue}".
            Give them a SHORT, spoken correction (max 5 words).
            Examples: "Chest up!", "Drive the knees!", "Squeeze at the top!".
            Output ONLY the text to speak. No quotes.
        """.trimIndent()
    )

    init {
        // Fetch config every hour (3600s)
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(defaults)

        // Fetch immediately on init
        fetchPrompts()
    }

    fun fetchPrompts() {
        remoteConfig.fetchAndActivate()
            .addOnSuccessListener { Log.d("PromptRepo", "Prompts updated from Firebase") }
            .addOnFailureListener { Log.e("PromptRepo", "Failed to fetch prompts") }
    }

    // Getters for specific prompts
    fun getWorkoutSystemPrompt(): String = remoteConfig.getString("system_instruction_workout")
    fun getNutritionSystemPrompt(): String = remoteConfig.getString("system_instruction_nutrition")
    fun getFoodLogSystemPrompt(): String = remoteConfig.getString("system_instruction_food_log")
    fun getCoachingCueTemplate(): String = remoteConfig.getString("prompt_template_coaching_cue")
}