// app/src/main/java/com/example/myapplication/data/repository/PlanRepository.kt
package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.data.Exercise
import com.example.myapplication.data.NutritionPlan
import com.example.myapplication.data.WeeklyPlan
import com.example.myapplication.data.WorkoutPlan
import com.example.myapplication.data.local.DailyWorkoutEntity
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.local.UserPreferencesRepository
import com.example.myapplication.data.local.WorkoutDao
import com.example.myapplication.data.local.WorkoutPlanEntity
import com.example.myapplication.data.local.WorkoutSetEntity
import com.example.myapplication.data.remote.BedrockClient
// FIX: Ensure these imports exactly match the package in BedrockClient.kt
import com.example.myapplication.data.remote.GeneratedDay
import com.example.myapplication.data.remote.GeneratedExercise
import com.example.myapplication.data.remote.RemoteNutritionPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class PlanRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val userPrefs: UserPreferencesRepository,
    private val bedrockClient: BedrockClient
) {
    suspend fun generateAndSavePlan(
        goal: String,
        duration: Int,
        days: List<String>,
        programType: String = "Hypertrophy"
    ): Long {
        Log.d("PlanRepo", "Generating Plan for: $goal")
        val previousPlan = workoutDao.getLatestPlan()
        val existingNutritionJson = previousPlan?.nutritionJson

        workoutDao.deleteFutureUncompletedWorkouts(System.currentTimeMillis())

        val workoutHistory = workoutDao.getCompletedWorkoutsWithExercise().first()
        val availableExercises = workoutDao.getAllExercises().first()
        val height = userPrefs.userHeight.first()
        val weight = userPrefs.userWeight.first()
        val age = userPrefs.userAge.first()

        val aiResponse = bedrockClient.generateWorkoutPlan(
            goal = goal,
            programType = programType,
            days = days,
            duration = duration.toFloat(),
            workoutHistory = workoutHistory,
            availableExercises = availableExercises,
            userAge = age,
            userHeight = height,
            userWeight = weight
        )

        val startDate = System.currentTimeMillis()
        val planEntity = WorkoutPlanEntity(
            name = "$programType for $goal",
            startDate = startDate,
            goal = goal,
            programType = programType,
            aiExplanation = aiResponse.explanation,
            nutritionJson = existingNutritionJson
        )

        val adjustedSchedule = enforceTimeConstraints(aiResponse.schedule, duration.toFloat())
        val totalWeeks = if (programType == "Endurance") 4 else 5
        val deloadWeekIndex = if (programType == "Endurance") 4 else 5
        val fullPlanData = mutableMapOf<DailyWorkoutEntity, List<WorkoutSetEntity>>()

        for (weekNum in 1..totalWeeks) {
            val isDeload = weekNum == deloadWeekIndex
            adjustedSchedule.forEach { templateDay ->
                val workoutDate = calculateSmartDate(startDate, weekNum, templateDay.day)
                val workoutEntity = DailyWorkoutEntity(
                    planId = 0,
                    scheduledDate = workoutDate,
                    title = if (isDeload) "DELOAD: ${templateDay.title}" else templateDay.title,
                    isCompleted = false
                )
                val setsForThisWorkout = mutableListOf<WorkoutSetEntity>()

                templateDay.exercises.forEach { aiEx ->
                    val realEx = availableExercises.find { it.name.equals(aiEx.name, ignoreCase = true) }
                    realEx?.let { validExercise ->
                        val finalSets = if (isDeload) (aiEx.sets * 0.6f).roundToInt().coerceAtLeast(2) else aiEx.sets
                        val finalLbs = if (isDeload) (aiEx.suggestedLbs * 0.7f).toInt() else aiEx.suggestedLbs.toInt()

                        for (setNum in 1..finalSets) {
                            setsForThisWorkout.add(
                                WorkoutSetEntity(
                                    workoutId = 0,
                                    exerciseId = validExercise.exerciseId,
                                    setNumber = setNum,
                                    suggestedReps = aiEx.suggestedReps,
                                    suggestedRpe = if (isDeload) 5 else aiEx.suggestedRpe,
                                    suggestedLbs = if (finalLbs == 0) 45 else finalLbs,
                                    isCompleted = false
                                )
                            )
                        }
                    }
                }
                fullPlanData[workoutEntity] = setsForThisWorkout
            }
        }
        return workoutDao.saveFullWorkoutPlan(planEntity, fullPlanData)
    }

    suspend fun getPlanDetails(planId: Long): WorkoutPlan {
        val targetPlanId = if (planId == 0L) workoutDao.getLatestPlan()?.planId ?: 0L else planId
        val planEntity = workoutDao.getPlanById(targetPlanId)

        val remoteNutrition = planEntity?.nutritionJson?.let {
            try { Json.decodeFromString<RemoteNutritionPlan>(it) } catch(e: Exception) { null }
        }

        val domainNutrition = remoteNutrition?.let {
            NutritionPlan(it.calories, it.protein, it.carbs, it.fats, it.timing, it.explanation)
        }

        val workouts = workoutDao.getWorkoutsForPlan(targetPlanId)
        val allExercises = workoutDao.getAllExercisesOneShot()
        val weeklyPlans = mutableListOf<WeeklyPlan>()

        if (workouts.isNotEmpty()) {
            val startDate = workouts.first().scheduledDate
            val workoutsByWeek = workouts.groupBy {
                val diff = it.scheduledDate - startDate
                (diff / (1000L * 60 * 60 * 24 * 7)).toInt() + 1
            }
            workoutsByWeek.forEach { (weekNum, dailyEntities) ->
                val dailyWorkouts = dailyEntities.map { entity ->
                    val sets = workoutDao.getSetsForWorkoutList(entity.workoutId)
                    val exercisesForDay = sets.groupBy { it.exerciseId }.map { (exId, setList) ->
                        val realEx = allExercises.find { it.exerciseId == exId }
                        val firstSet = setList.first()
                        Exercise(
                            exerciseId = exId,
                            name = realEx?.name ?: "Unknown",
                            sets = setList.size,
                            reps = firstSet.suggestedReps.toString(),
                            rest = "${(realEx?.estimatedTimePerSet ?: 2.0).toInt() * 60}s",
                            tier = realEx?.tier ?: 1,
                            explanation = realEx?.notes ?: "",
                            estimatedTimePerSet = realEx?.estimatedTimePerSet ?: 2.0,
                            rpe = firstSet.suggestedRpe,
                            suggestedLbs = firstSet.suggestedLbs.toFloat()
                        )
                    }
                    com.example.myapplication.data.DailyWorkout(
                        day = getDayNameFromDate(entity.scheduledDate),
                        title = entity.title,
                        exercises = exercisesForDay
                    )
                }
                weeklyPlans.add(WeeklyPlan(week = weekNum, days = dailyWorkouts))
            }
        }

        return WorkoutPlan(
            explanation = planEntity?.aiExplanation ?: planEntity?.name ?: "Your Plan",
            weeks = weeklyPlans,
            nutrition = domainNutrition
        )
    }

    suspend fun activatePlan(planId: Long) {
        workoutDao.deactivateAllPlans()
        workoutDao.activatePlan(planId)
    }
    fun getAllExercises(): Flow<List<ExerciseEntity>> = workoutDao.getAllExercises()
    fun getAllWorkoutDates(): Flow<List<Long>> = workoutDao.getAllWorkoutDates()

    fun getWorkoutForDate(date: Long): Flow<DailyWorkoutEntity?> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = cal.timeInMillis
        return workoutDao.getWorkoutByDate(startOfDay, endOfDay)
    }

    // --- UTILS ---
    private fun getDayNameFromDate(date: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = date
        return cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()) ?: "Day"
    }

    private fun calculateSmartDate(startDate: Long, weekNum: Int, dayName: String): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = startDate
        val lowerName = dayName.lowercase(Locale.getDefault())
        val targetDay = when {
            lowerName.startsWith("sun") -> Calendar.SUNDAY
            lowerName.startsWith("mon") -> Calendar.MONDAY
            lowerName.startsWith("tue") -> Calendar.TUESDAY
            lowerName.startsWith("wed") -> Calendar.WEDNESDAY
            lowerName.startsWith("thu") -> Calendar.THURSDAY
            lowerName.startsWith("fri") -> Calendar.FRIDAY
            lowerName.startsWith("sat") -> Calendar.SATURDAY
            else -> Calendar.MONDAY
        }
        val currentDay = cal.get(Calendar.DAY_OF_WEEK)
        var dayOffset = targetDay - currentDay
        if (dayOffset < 0) dayOffset += 7
        val totalDaysToAdd = dayOffset + ((weekNum - 1) * 7)
        cal.add(Calendar.DAY_OF_YEAR, totalDaysToAdd)
        cal.set(Calendar.HOUR_OF_DAY, 12)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        return cal.timeInMillis
    }

    private fun enforceTimeConstraints(schedule: List<GeneratedDay>, targetHours: Float): List<GeneratedDay> {
        val targetMinutes = (targetHours * 60).toInt()
        val tolerance = 5
        return schedule.map { day ->
            val exercises = day.exercises.toMutableList()
            var currentMinutes = calculateTotalMinutes(exercises)
            while (currentMinutes > targetMinutes + tolerance) {
                val cutCandidateIndex = exercises.indexOfFirst {
                    it == exercises.filter { e -> e.sets > 2 }
                        .sortedWith(compareByDescending<GeneratedExercise> { ex -> ex.tier }.thenByDescending { ex -> ex.sets })
                        .firstOrNull()
                }
                if (cutCandidateIndex != -1) {
                    val ex = exercises[cutCandidateIndex]
                    exercises[cutCandidateIndex] = ex.copy(sets = ex.sets - 1)
                    currentMinutes = calculateTotalMinutes(exercises)
                } else { break }
            }
            day.copy(exercises = exercises)
        }
    }

    private fun calculateTotalMinutes(exercises: List<GeneratedExercise>): Double {
        return exercises.sumOf { ex ->
            val timePerSet = when (ex.tier) { 1 -> 4.0; 2 -> 2.5; else -> 1.5 }
            ex.sets * timePerSet
        }
    }
}