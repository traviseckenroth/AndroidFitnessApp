package com.example.myapplication.util

import androidx.compose.ui.graphics.Color
import com.example.myapplication.data.local.DailyWorkoutEntity
import com.example.myapplication.data.local.WorkoutDao
import com.example.myapplication.data.remote.RemoteNutritionPlan
import com.example.myapplication.data.repository.HealthConnectManager
import com.example.myapplication.data.repository.NutritionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class FormaScore(
    val score: Int,
    val title: String,
    val description: String,
    val color: Color,
    val breakdown: String = ""
)

@Singleton
class ReadinessEngine @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val healthConnectManager: HealthConnectManager,
    private val nutritionRepository: NutritionRepository
) {

    suspend fun calculateReadiness(workoutTitle: String? = "today's session"): FormaScore {
        // --- 1. CNS Fatigue (ACWR) ---
        val now = Instant.now()
        val twentyEightDaysAgo = now.minus(28, ChronoUnit.DAYS)
        
        // Fetch last 28 days of history
        val history = workoutDao.getCompletedWorkoutsRecent(twentyEightDaysAgo.toEpochMilli()).first()
        
        // Calculate Daily Volumes
        val volumeByDate = history.groupBy { 
            // Group by day (simple truncation to epoch day)
            it.completedWorkout.date / (24 * 60 * 60 * 1000) 
        }.mapValues { (_, workouts) ->
            workouts.sumOf { it.completedWorkout.totalVolume }.toDouble()
        }

        // Chronic Load (Avg over 28 days)
        val chronicLoad = if (volumeByDate.isNotEmpty()) volumeByDate.values.average() else 0.0
        
        // Acute Load (Avg over last 4 days)
        val fourDaysAgoEpoch = now.minus(4, ChronoUnit.DAYS).toEpochMilli() / (24 * 60 * 60 * 1000)
        val acuteVolumes = volumeByDate.filterKeys { it >= fourDaysAgoEpoch }.values
        val acuteLoad = if (acuteVolumes.isNotEmpty()) acuteVolumes.average() else 0.0

        val acwr = if (chronicLoad > 0) acuteLoad / chronicLoad else 0.0
        
        // Determine CNS Score (0-100) based on ACWR
        // 0.8 - 1.3 is optimal. > 1.3 is fatigue. < 0.8 is fresh/detraining.
        val cnsScore = when {
            chronicLoad == 0.0 -> 100 // New user, assume fresh
            acwr > 1.5 -> 40  // High risk / Overreaching
            acwr > 1.3 -> 60  // Overreaching
            acwr > 1.1 -> 85  // High Training Stress
            acwr in 0.8..1.1 -> 100 // Prime Zone
            else -> 90 // < 0.8 (Fresh)
        }

        // --- 2. Biometric Recovery (Sleep) ---
        val sleepDuration = healthConnectManager.getLastNightSleepDuration()
        val sleepHours = sleepDuration.toMinutes() / 60.0
        val targetSleep = 8.0
        val sleepScore = if (sleepHours > 0) {
            ((sleepHours / targetSleep) * 100).toInt().coerceIn(0, 100)
        } else {
            -1 // Signal for no data
        }

        // --- 3. Bonus Modifiers ---
        var bonus = 0
        
        // Nutrition Bonus
        val foodLogs = nutritionRepository.getTodayFoodLogs().first()
        val activePlan = workoutDao.getActivePlan()
        val targetCals = activePlan?.nutritionJson?.let {
            try { Json.decodeFromString<RemoteNutritionPlan>(it).calories.toIntOrNull() } catch(e: Exception) { 2500 }
        } ?: 2500
        
        val loggedCals = foodLogs.sumOf { it.totalCalories }
        if (loggedCals > 0) {
            val ratio = loggedCals.toDouble() / targetCals
            if (ratio in 0.8..1.2) bonus += 5
        }

        // Mobility Bonus (Yesterday)
        val yesterdayStart = now.minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS).toEpochMilli()
        val yesterdayEnd = now.truncatedTo(ChronoUnit.DAYS).toEpochMilli()
        val yesterdayWorkouts = workoutDao.getCompletedWorkoutsRecent(yesterdayStart).first()
            .filter { it.completedWorkout.date < yesterdayEnd }
        
        // Check if any workout yesterday was "Stretching" or "Recovery"
        // Note: CompletedWorkoutEntity stores exerciseId, we need to check if we can link it to a session title.
        // Actually, CompletedWorkoutEntity doesn't store session title. 
        // We can check `DailyWorkoutEntity` for completion.
        // Or check if exercises include "Stretch" or "Yoga".
        // Let's check DailyWorkouts for yesterday.
        
        // Easier way: `workoutDao.getCompletedWorkouts()` returns `DailyWorkoutEntity` list.
        // We can filter for date.
        // But `ReadinessEngine` assumes `workoutDao` available.
        // Let's try to query completed daily workouts.
        // `getCompletedWorkouts()` is a Flow.
        // I'll stick to Volume/Exercises for simplicity or add a check.
        // Let's assume for now bonus is just nutrition + maybe if simple logic passes.
        // I will implement "Stretching" check via exercise names in history if possible?
        // `CompletedWorkoutWithExercise` has `exercise.name`.
        val didMobilityYesterday = yesterdayWorkouts.any { 
            it.exercise.name.contains("Stretch", ignoreCase = true) || 
            it.exercise.name.contains("Yoga", ignoreCase = true) ||
            it.exercise.majorMuscle.equals("Mobility", ignoreCase = true)
        }
        if (didMobilityYesterday) bonus += 5

        // --- 4. Final Calculation ---
        val totalScore: Int
        val calculationMethod: String
        
        if (sleepScore != -1) {
            // Full Model
            val baseScore = (cnsScore * 0.5) + (sleepScore * 0.4)
            totalScore = (baseScore + bonus).roundToInt().coerceIn(0, 100)
            calculationMethod = "ACWR + Sleep + Bonus"
        } else {
            // Fallback Model (No Sleep Data)
            val baseScore = cnsScore * 1.0
            totalScore = (baseScore + bonus).roundToInt().coerceIn(0, 100)
            calculationMethod = "ACWR (No Sleep Data)"
        }

        val (title, description) = getRecommendation(totalScore, workoutTitle)
        val color = when {
            totalScore >= 80 -> Color(0xFF4CAF50) // Green
            totalScore >= 50 -> Color(0xFFFFC107) // Yellow
            else -> Color(0xFFF44336) // Red
        }

        return FormaScore(totalScore, title, description, color, "Based on $calculationMethod")
    }

    private fun getRecommendation(score: Int, workoutTitle: String?): Pair<String, String> {
        val wTitle = workoutTitle ?: "training"
        return when {
            score >= 85 -> "Peak Performance" to "System is primed (ACWR Optimal). Push for PRs on $wTitle."
            score >= 70 -> "Ready to Train" to "Good balance of stress and recovery. Proceed with $wTitle."
            score >= 50 -> "Moderate Fatigue" to "Accumulated fatigue detected. Listen to your body during $wTitle."
            score >= 30 -> "High Strain" to "ACWR indicates overreaching. Consider reducing volume for $wTitle."
            else -> "Recovery Needed" to "High fatigue risk. Coach recommends swapping $wTitle for Mobility."
        }
    }
}
