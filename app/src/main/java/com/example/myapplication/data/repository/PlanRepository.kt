// app/src/main/java/com/example/myapplication/data/repository/PlanRepository.kt
package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.data.Exercise
import com.example.myapplication.data.WeeklyPlan
import com.example.myapplication.data.WorkoutPlan
import com.example.myapplication.data.local.CompletedWorkoutWithExercise
import com.example.myapplication.data.local.DailyWorkoutEntity
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.local.UserPreferencesRepository
import com.example.myapplication.data.local.WorkoutDao
import com.example.myapplication.data.local.WorkoutPlanEntity
import com.example.myapplication.data.local.WorkoutSetEntity
import com.example.myapplication.data.remote.BedrockClient
import com.example.myapplication.data.remote.GeneratedDay
import com.example.myapplication.data.remote.GeneratedExercise
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class PlanRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val userPrefs: UserPreferencesRepository,
    private val bedrockClient: BedrockClient,
    private val healthConnectManager: HealthConnectManager
) {
    suspend fun generateAndSavePlan(
        goal: String,
        duration: Int,
        days: List<String>,
        programType: String = "Hypertrophy",
        block: Int = 1,
        onThoughtReceived: (String) -> Unit = {}
    ): Long {
        Log.d("PlanRepo", "Generating Plan for: $goal, Block: $block")
        val previousPlan = if (block > 1) workoutDao.getLatestPlan() else null
        val existingNutrition = previousPlan?.nutrition

        if (block == 1) {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            workoutDao.deleteFutureUncompletedWorkouts(cal.timeInMillis)
        }

        val workoutHistory = workoutDao.getCompletedWorkoutsWithExercise().first()
        val allExercises = workoutDao.getAllExercises().first()
        val excludedEquipment = userPrefs.excludedEquipment.first()

        val availableExercises = allExercises.filter { exercise ->
            val eq = exercise.equipment
            eq.isNullOrBlank() || !excludedEquipment.contains(eq)
        }

        val hcHeight = healthConnectManager.getLatestHeight()
        val hcWeight = healthConnectManager.getLatestWeight()
        val lastNightSleep = healthConnectManager.getLastNightSleepDuration()
        lastNightSleep.toMinutes() / 60.0

        val prefHeight = userPrefs.userHeight.first()
        val prefWeight = userPrefs.userWeight.first()
        val age = userPrefs.userAge.first()

        val finalHeight = hcHeight ?: prefHeight
        val finalWeight = hcWeight ?: prefWeight

        val aiResponse = bedrockClient.generateWorkoutPlan(
            goal = goal,
            programType = programType,
            days = days,
            duration = duration.toFloat(),
            workoutHistory = workoutHistory,
            allExercises = allExercises,             // <-- ADDED THIS
            excludedEquipment = excludedEquipment,
            userAge = age,
            userHeight = finalHeight,
            userWeight = finalWeight,
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
            nutrition = existingNutrition, // CHANGED HERE
            block = block,
            isActive = false
        )

        val adjustedSchedule = enforceTimeConstraints(aiResponse.schedule, duration.toFloat())

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
                    // FIX: Changed 'val' to 'var' so it can be reassigned when the AI generates a custom AMRAP circuit
                    var realEx = availableExercises.find { it.name.equals(aiEx.name, ignoreCase = true) }

                    if (realEx == null && (aiEx.isAMRAP || aiEx.isEMOM || aiEx.name.contains(
                            "AMRAP",
                            true
                        ) || aiEx.name.contains("EMOM", true))
                    ) {
                        val newExId = workoutDao.insertExercise(
                            ExerciseEntity(
                                name = aiEx.name,
                                muscleGroup = aiEx.muscleGroup ?: "Conditioning",
                                majorMuscle = aiEx.muscleGroup ?: "Conditioning",
                                minorMuscle = null,
                                equipment = aiEx.equipment ?: "Bodyweight",
                                tier = aiEx.tier,
                                loadability = aiEx.loadability ?: "Bodyweight",
                                fatigue = aiEx.fatigue ?: "High",
                                movementPattern = "Circuit",
                                localRecoveryHours = 24,
                                resistanceProfile = "Conditioning",
                                notes = aiEx.notes,
                                description = aiEx.notes,
                                estimatedTimePerSet = aiEx.estimatedTimeMinutes.toDouble()
                                    .let { if (it > 0) it else 15.0 }
                            )
                        )
                        realEx = ExerciseEntity(
                            exerciseId = newExId,
                            name = aiEx.name,
                            muscleGroup = aiEx.muscleGroup ?: "Conditioning",
                            majorMuscle = aiEx.muscleGroup ?: "Conditioning",
                            minorMuscle = null,
                            equipment = aiEx.equipment ?: "Bodyweight",
                            tier = aiEx.tier,
                            loadability = aiEx.loadability ?: "Bodyweight",
                            fatigue = aiEx.fatigue ?: "High",
                            movementPattern = "Circuit",
                            localRecoveryHours = 24,
                            resistanceProfile = "Conditioning",
                            notes = aiEx.notes,
                            description = aiEx.notes,
                            estimatedTimePerSet = aiEx.estimatedTimeMinutes.toDouble()
                                .let { if (it > 0) it else 15.0 }
                        )
                    }

                    realEx?.let { validExercise ->
                        // NEW LOGIC: Prevent AMRAPs from having their sets manipulated by Deload logic
                        val finalSets =
                            if (isDeload && !aiEx.isAMRAP && !aiEx.isEMOM) (aiEx.sets * 0.6f).roundToInt()
                                .coerceAtLeast(2) else aiEx.sets
                        val finalLbs =
                            if (isDeload) (aiEx.suggestedLbs * 0.7f).toInt() else aiEx.suggestedLbs.toInt()

                        for (setNum in 1..finalSets) {
                            setsForThisWorkout.add(
                                WorkoutSetEntity(
                                    workoutId = 0,
                                    exerciseId = validExercise.exerciseId,
                                    setNumber = setNum,
                                    suggestedReps = aiEx.suggestedReps,
                                    suggestedRpe = if (isDeload) 5 else aiEx.suggestedRpe,
                                    suggestedLbs = if (finalLbs == 0) 45 else finalLbs,
                                    isCompleted = false,
                                    isAMRAP = aiEx.isAMRAP,
                                    isEMOM = aiEx.isEMOM
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

    fun getNextBlockNumberFlow(): Flow<Int?> = workoutDao.getActivePlanFlow().flatMapLatest { activePlan ->
        if (activePlan == null) flowOf(null)
        else {
            combine(
                workoutDao.getUncompletedWorkoutsCountFlow(activePlan.planId),
                workoutDao.getTotalWorkoutsCountFlow(activePlan.planId)
            ) { remaining, total ->
                // Only suggest next block if we are actually near the end (3 or fewer workouts remaining)
                // AND we have actually made progress (remaining < total) to prevent immediate suggestions.
                if (total > 0 && remaining <= 3 && remaining < total) {
                    activePlan.block + 1
                } else null
            }
        }
    }

    suspend fun getNextBlockNumber(): Int? {
        val activePlan = workoutDao.getActivePlan() ?: return null
        val remaining = workoutDao.getUncompletedWorkoutsCount(activePlan.planId)
        val total = workoutDao.getTotalWorkoutsCount(activePlan.planId)
        return if (total > 0 && remaining <= 3 && remaining < total) activePlan.block + 1 else null
    }

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

        val domainNutrition = planEntity?.nutrition

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
        // OPTIMIZED: Reuse existing "Standalone Workouts" plan container if it exists
        val standalonePlanName = "Standalone Workouts"
        val existingPlan = workoutDao.getPlanByName(standalonePlanName)
        
        val targetPlanId = existingPlan?.planId ?: workoutDao.insertPlan(
            WorkoutPlanEntity(
                name = standalonePlanName,
                startDate = System.currentTimeMillis(),
                goal = "General Fitness",
                programType = "Custom"
            )
        )

        val workoutId = workoutDao.insertDailyWorkout(
            DailyWorkoutEntity(
                planId = targetPlanId,
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
                        movementPattern = "Mobility",
                        localRecoveryHours = 0,
                        resistanceProfile = "Bodyweight",
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
                    movementPattern = "Mobility",
                    localRecoveryHours = 0,
                    resistanceProfile = "Bodyweight",
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
                    isCompleted = false,
                    isAMRAP = aiEx.isAMRAP,
                    isEMOM = aiEx.isEMOM
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
    suspend fun updatePlan(plan: WorkoutPlanEntity) {
        workoutDao.updatePlan(plan)
    }
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
            var currentMinutes = calculateTotalMinutes(exercises).toInt()

            // 1. Cut sets if it's too long (Your existing logic)
            while (currentMinutes > targetMinutes + tolerance) {
                val cutCandidateIndex = exercises.indexOfFirst {
                    it == exercises.filter { e -> e.sets > 2 }
                        .sortedWith(compareByDescending<GeneratedExercise> { ex -> ex.tier }.thenByDescending { ex -> ex.sets })
                        .firstOrNull()
                }
                if (cutCandidateIndex != -1) {
                    val ex = exercises[cutCandidateIndex]
                    exercises[cutCandidateIndex] = ex.copy(sets = ex.sets - 1)
                    currentMinutes = calculateTotalMinutes(exercises).toInt()
                } else { break }
            }

            // 2. Pad sets if it's too short (NEW LOGIC)
            while (currentMinutes < targetMinutes - tolerance) {
                // Find a Tier 2 or 3 exercise with less than 4 sets to pad
                val padCandidateIndex = exercises.indexOfFirst { it.tier > 1 && it.sets < 4 }
                if (padCandidateIndex != -1) {
                    val ex = exercises[padCandidateIndex]
                    exercises[padCandidateIndex] = ex.copy(sets = ex.sets + 1)
                    currentMinutes = calculateTotalMinutes(exercises).toInt()
                } else { break } // Stop if we can't pad without exceeding safe volume
            }

            day.copy(exercises = exercises)
        }
    }

    private fun calculateTotalMinutes(exercises: List<GeneratedExercise>): Double {
        return exercises.sumOf { ex ->
            val timePerSet = when (ex.tier) { 1 -> 3.0; 2 -> 2.5; else -> 2.0 }
            ex.sets * timePerSet
        }
    }
}