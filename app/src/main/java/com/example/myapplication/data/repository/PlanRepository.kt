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
import com.example.myapplication.data.local.CompletedWorkoutWithExercise
import com.example.myapplication.data.local.WorkoutPlanEntity
import com.example.myapplication.data.local.WorkoutSetEntity
import com.example.myapplication.data.remote.BedrockClient
import com.example.myapplication.data.remote.GeneratedDay
import com.example.myapplication.data.remote.GeneratedExercise
import com.example.myapplication.data.remote.RemoteNutritionPlan
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class PlanProgress(
    val completedWorkouts: Int,
    val totalWorkouts: Int,
    val percentage: Float
)

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
        programType: String = "Hypertrophy",
        block: Int = 1
    ): Long {
        Log.d("PlanRepo", "Generating Plan for: $goal, Block: $block")
        val previousPlan = if (block > 1) workoutDao.getLatestPlan() else null
        val existingNutritionJson = previousPlan?.nutritionJson

        // If Block 1, we start fresh. If Block 2+, we transition.
        if (block == 1) {
            workoutDao.deleteFutureUncompletedWorkouts(System.currentTimeMillis())
        }

        val workoutHistory = workoutDao.getCompletedWorkoutsWithExercise().first()
        val allExercises = workoutDao.getAllExercises().first()
        val excludedEquipment = userPrefs.excludedEquipment.first()

        val availableExercises = allExercises.filter { exercise ->
            val eq = exercise.equipment
            eq.isNullOrBlank() || !excludedEquipment.contains(eq)
        }

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
            userWeight = weight,
            block = block
        )

        val startDate = if (block > 1 && previousPlan != null) {
            val lastWorkout = workoutDao.getWorkoutsForPlan(previousPlan.planId).maxByOrNull { it.scheduledDate }
            (lastWorkout?.scheduledDate ?: System.currentTimeMillis()) + (24 * 60 * 60 * 1000)
        } else {
            System.currentTimeMillis()
        }

        val planEntity = WorkoutPlanEntity(
            name = if (block > 1) "$programType for $goal (Block $block)" else "$programType for $goal",
            startDate = startDate,
            goal = goal,
            programType = programType,
            aiExplanation = aiResponse.explanation,
            nutritionJson = existingNutritionJson,
            block = block,
            isActive = true
        )

        if (block > 1) {
            workoutDao.deactivateAllPlans()
        }

        val adjustedSchedule = enforceTimeConstraints(aiResponse.schedule, duration.toFloat())
        
        // Use AI determined mesocycle length (4-6 weeks)
        val totalWeeks = aiResponse.mesocycleLengthWeeks.coerceIn(4, 6)
        val deloadWeekIndex = totalWeeks 

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

    /**
     * Reactive eligibility for next block.
     * Returns the next block number if user has <= 3 uncompleted workouts in current active plan.
     */
    fun getNextBlockNumberFlow(): Flow<Int?> = workoutDao.getActivePlanFlow().flatMapLatest { activePlan ->
        if (activePlan == null) flowOf(null)
        else {
            workoutDao.getUncompletedWorkoutsCountFlow(activePlan.planId).map { remaining ->
                if (remaining <= 3) activePlan.block + 1 else null
            }
        }
    }

    suspend fun getNextBlockNumber(): Int? {
        val activePlan = workoutDao.getActivePlan() ?: return null
        val remaining = workoutDao.getUncompletedWorkoutsCount(activePlan.planId)
        return if (remaining <= 3) activePlan.block + 1 else null
    }

    /**
     * Reactive progress for the current active plan.
     */
    fun getActivePlanProgressFlow(): Flow<PlanProgress?> = workoutDao.getActivePlanFlow().flatMapLatest { activePlan ->
        if (activePlan == null) flowOf(null)
        else {
            combine(
                workoutDao.getTotalWorkoutsCountFlow(activePlan.planId),
                workoutDao.getCompletedWorkoutsCountFlow(activePlan.planId)
            ) { total, completed ->
                val percentage = if (total > 0) completed.toFloat() / total.toFloat() else 0f
                PlanProgress(completed, total, percentage)
            }
        }
    }

    suspend fun getActivePlanProgress(): PlanProgress? {
        val activePlan = workoutDao.getActivePlan() ?: return null
        val total = workoutDao.getTotalWorkoutsCount(activePlan.planId)
        val completed = workoutDao.getCompletedWorkoutsCount(activePlan.planId)
        val percentage = if (total > 0) completed.toFloat() / total.toFloat() else 0f
        return PlanProgress(completed, total, percentage)
    }

    fun getWorkoutHistory(): Flow<List<CompletedWorkoutWithExercise>> {
        return workoutDao.getCompletedWorkoutsWithExercise()
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
    private fun cleanMobilityDescription(text: String): String {
        val patterns = listOf(
            "(?i)hold\\s+for\\s+\\d+\\s*(seconds?|s|minutes?|m)",
            "(?i)(for\\s+)?\\d+\\s*(seconds?|s|minutes?|m)\\s*hold",
            "(?i)(for\\s+)?\\d+\\s*(seconds?|s|minutes?|m)\\s*stretch",
            "(?i)maintain\\s+(this\\s+)?position\\s+for\\s+\\d+\\s*(seconds?|s|minutes?|m)"
        )

        var cleaned = text
        patterns.forEach { pattern ->
            cleaned = cleaned.replace(Regex(pattern), "")
        }

        return cleaned.replace(Regex("[.,;:]\\s*$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .replaceFirstChar { it.uppercase() }
    }
    suspend fun saveSingleDayWorkout(
        planId: Long,
        date: Long,
        title: String,
        exercises: List<GeneratedExercise>
    ): Long {
        val workoutId = workoutDao.insertDailyWorkout(
            DailyWorkoutEntity(
                planId = planId,
                scheduledDate = date,
                title = title,
                isCompleted = false
            )
        )

        val isMobility = title.contains("Recovery", ignoreCase = true) || title.contains("Stretching", ignoreCase = true)

        val allExercises = workoutDao.getAllExercisesOneShot()
        val sets = exercises.flatMap { aiEx ->
            var realEx = allExercises.find {
                it.name.trim().equals(aiEx.name.trim(), ignoreCase = true)
            }

            val adjustedSuggestedReps = if (isMobility && aiEx.suggestedReps < 30) {
                if (aiEx.suggestedReps <= 2) 60 else 30
            } else {
                aiEx.suggestedReps
            }

            val cleanedNotes = cleanMobilityDescription(aiEx.notes)
            val holdTimeInMinutes = adjustedSuggestedReps.toDouble() / 60.0

            if (realEx == null) {
                val newExerciseId = workoutDao.insertExercise(
                    ExerciseEntity(
                        name = aiEx.name,
                        muscleGroup = aiEx.muscleGroup,
                        majorMuscle = aiEx.muscleGroup ?: "Mobility",
                        minorMuscle = null,
                        equipment = aiEx.equipment,
                        tier = 3,
                        loadability = aiEx.loadability,
                        fatigue = aiEx.fatigue,
                        notes = cleanedNotes,
                        description = cleanedNotes,
                        estimatedTimePerSet = holdTimeInMinutes
                    )
                )

                realEx = ExerciseEntity(
                    exerciseId = newExerciseId,
                    name = aiEx.name,
                    muscleGroup = aiEx.muscleGroup,
                    majorMuscle = "Mobility",
                    equipment = aiEx.equipment,
                    loadability = aiEx.loadability,
                    fatigue = aiEx.fatigue,
                    description = cleanedNotes,
                    tier = 3,
                    estimatedTimePerSet = holdTimeInMinutes,
                    notes = cleanedNotes
                )
            } else {
                val durationRegex = Regex("(?i)\\d+\\s*(second|s|minute)")
                if (isMobility || realEx.notes.contains(durationRegex) || realEx.description.contains(durationRegex)) {
                    val updatedEx = realEx.copy(
                        notes = cleanedNotes,
                        description = cleanedNotes,
                        estimatedTimePerSet = holdTimeInMinutes
                    )
                    workoutDao.updateExercise(updatedEx)
                    realEx = updatedEx
                }
            }

            List(aiEx.sets) { setNum ->
                WorkoutSetEntity(
                    workoutId = workoutId,
                    exerciseId = realEx!!.exerciseId,
                    setNumber = setNum + 1,
                    suggestedReps = adjustedSuggestedReps,
                    suggestedRpe = aiEx.suggestedRpe,
                    suggestedLbs = aiEx.suggestedLbs.toInt(),
                    isCompleted = false
                )
            }
        }

        if (sets.isNotEmpty()) {
            workoutDao.insertSets(sets)
        }
        return workoutId
    }

    suspend fun getActivePlan(): WorkoutPlanEntity? {
        return workoutDao.getActivePlan()
    }

    suspend fun getLatestPlan(): WorkoutPlanEntity? {
        return workoutDao.getLatestPlan()
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