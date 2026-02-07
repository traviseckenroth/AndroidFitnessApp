package com.example.myapplication.data.repository

import com.example.myapplication.data.NutritionPlan
import com.example.myapplication.data.local.FoodLogEntity
import com.example.myapplication.data.local.UserPreferencesRepository
import com.example.myapplication.data.local.WorkoutDao
import com.example.myapplication.data.remote.BedrockClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NutritionRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val userPrefs: UserPreferencesRepository,
    private val bedrockClient: BedrockClient
) {
    // --- FOOD LOGGING ---
    suspend fun logFood(userQuery: String): FoodLogEntity {
        val aiResponse = bedrockClient.parseFoodLog(userQuery)
        val entity = FoodLogEntity(
            date = System.currentTimeMillis(),
            inputQuery = userQuery,
            totalCalories = aiResponse.totalMacros.calories,
            totalProtein = aiResponse.totalMacros.protein,
            totalCarbs = aiResponse.totalMacros.carbs,
            totalFats = aiResponse.totalMacros.fats,
            aiAnalysis = aiResponse.analysis,
            mealType = aiResponse.mealType
        )
        workoutDao.insertFoodLog(entity)
        return entity
    }

    suspend fun logManualFood(name: String, cals: Int, pro: Int, carb: Int, fat: Int, meal: String) {
        val entity = FoodLogEntity(
            date = System.currentTimeMillis(),
            inputQuery = name,
            totalCalories = cals,
            totalProtein = pro,
            totalCarbs = carb,
            totalFats = fat,
            aiAnalysis = "Manual Entry",
            mealType = meal
        )
        workoutDao.insertFoodLog(entity)
    }

    fun getRecentFoodHistory(): Flow<List<String>> = workoutDao.getRecentFoodQueries()
    fun getRecentFoodLogs(): Flow<List<FoodLogEntity>> = workoutDao.getRecentFoodLogs()

    fun getTodayFoodLogs(): Flow<List<FoodLogEntity>> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = cal.timeInMillis
        return workoutDao.getFoodLogsForDate(startOfDay, endOfDay)
    }

    // --- NUTRITION GENERATION ---
    suspend fun generateAndSaveNutrition(): NutritionPlan {
        val height = userPrefs.userHeight.first()
        val weight = userPrefs.userWeight.first()
        val age = userPrefs.userAge.first()
        val gender = userPrefs.userGender.first()
        val diet = userPrefs.userDiet.first()
        val pace = userPrefs.userGoalPace.first()

        val currentPlan = workoutDao.getLatestPlan() ?: throw Exception("No active workout plan found")
        val workouts = workoutDao.getWorkoutsForPlan(currentPlan.planId)

        val weeklyWorkoutDays = if (workouts.isNotEmpty()) {
            val startDate = workouts.first().scheduledDate
            workouts.count { it.scheduledDate < startDate + (7 * 24 * 60 * 60 * 1000) }
        } else { 3 }

        val remoteNutrition = bedrockClient.generateNutritionPlan(
            userAge = age,
            userHeight = height,
            userWeight = weight,
            gender = gender,
            dietType = diet,
            goalPace = pace,
            weeklyWorkoutDays = weeklyWorkoutDays,
            avgWorkoutDurationMins = 60
        )

        val nutritionJson = Json.encodeToString(remoteNutrition)
        workoutDao.updateNutrition(currentPlan.planId, nutritionJson)

        return NutritionPlan(
            calories = remoteNutrition.calories,
            protein = remoteNutrition.protein,
            carbs = remoteNutrition.carbs,
            fats = remoteNutrition.fats,
            timing = remoteNutrition.timing,
            explanation = remoteNutrition.explanation
        )
    }
}