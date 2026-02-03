package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.data.local.CompletedWorkoutEntity
import com.example.myapplication.data.local.CompletedWorkoutWithExercise
import com.example.myapplication.data.local.DailyWorkoutEntity
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.local.WorkoutDao
import com.example.myapplication.data.local.WorkoutPlanEntity
import com.example.myapplication.data.local.WorkoutSetEntity
import com.example.myapplication.data.local.UserPreferencesRepository
import com.example.myapplication.data.remote.BedrockClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val userPrefs: UserPreferencesRepository,
    private val bedrockClient: BedrockClient
) {
    // --- READS (No Changes) ---
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

    fun getAllWorkoutDates(): Flow<List<Long>> = workoutDao.getAllWorkoutDates()
    fun getSetsForSession(workoutId: Long): Flow<List<WorkoutSetEntity>> = workoutDao.getSetsForWorkout(workoutId)
    suspend fun updateSet(set: WorkoutSetEntity) = workoutDao.updateSet(set)
    fun getAllExercises(): Flow<List<ExerciseEntity>> = workoutDao.getAllExercises()
    fun getExercisesByIds(exerciseIds: List<Long>): Flow<List<ExerciseEntity>> = workoutDao.getExercisesByIds(exerciseIds)
    fun getAllCompletedWorkouts(): Flow<List<CompletedWorkoutWithExercise>> = workoutDao.getCompletedWorkoutsWithExercise()
    fun getCompletedWorkoutsForExercise(exerciseId: Long): Flow<List<CompletedWorkoutWithExercise>> = workoutDao.getCompletedWorkoutsForExercise(exerciseId)
    fun getUserHeight(): Flow<Int> = userPrefs.userHeight
    fun getUserWeight(): Flow<Double> = userPrefs.userWeight
    fun getUserAge(): Flow<Int> = userPrefs.userAge
    suspend fun saveBiometrics(height: Int, weight: Double, age: Int) = userPrefs.saveBiometrics(height, weight, age)

    // --- PLAN GENERATION (No Changes) ---
    suspend fun generateAndSavePlan(
        goal: String,
        duration: Int,
        days: List<String>,
        programType: String = "Hypertrophy"
    ): Long {
        Log.d("WorkoutRepo", "Generating Plan for: $goal")

        val workoutHistory = workoutDao.getCompletedWorkoutsWithExercise().first()
        val availableExercises = workoutDao.getAllExercises().first()

        val aiResponse = bedrockClient.generateWorkoutPlan(
            goal = goal,
            duration = duration.toFloat(),
            days = days,
            programType = programType,
            workoutHistory = workoutHistory,
            availableExercises = availableExercises
        )

        val startDate = System.currentTimeMillis()
        val planEntity = WorkoutPlanEntity(
            name = "$programType for $goal",
            startDate = startDate,
            goal = goal,
            programType = programType
        )

        val fullPlanData = mutableMapOf<DailyWorkoutEntity, List<WorkoutSetEntity>>()
        val totalWeeks = 4

        for (weekNum in 1..totalWeeks) {
            aiResponse.schedule.forEach { templateDay ->
                val workoutDate = calculateSmartDate(startDate, weekNum, templateDay.day)

                val workoutEntity = DailyWorkoutEntity(
                    planId = 0,
                    scheduledDate = workoutDate,
                    title = templateDay.title,
                    isCompleted = false
                )

                val setsForThisWorkout = mutableListOf<WorkoutSetEntity>()

                templateDay.exercises.forEach { aiEx ->
                    val realEx = availableExercises.find { it.name.equals(aiEx.name, ignoreCase = true) }

                    realEx?.let { validExercise ->
                        val adjustedSets = when (weekNum) {
                            4 -> (aiEx.sets / 2).coerceAtLeast(2)
                            2, 3 -> aiEx.sets + 1
                            else -> aiEx.sets
                        }

                        val adjustedLbs = if (weekNum == 3) {
                            (aiEx.suggestedLbs * 1.05f).toInt()
                        } else {
                            aiEx.suggestedLbs.toInt()
                        }

                        val safeLbs = if (adjustedLbs == 0) 45 else adjustedLbs

                        for (setNum in 1..adjustedSets) {
                            setsForThisWorkout.add(
                                WorkoutSetEntity(
                                    workoutId = 0,
                                    exerciseId = validExercise.exerciseId,
                                    setNumber = setNum,
                                    suggestedReps = aiEx.suggestedReps,
                                    suggestedRpe = if (weekNum == 4) (aiEx.suggestedRpe - 1).coerceAtLeast(5) else aiEx.suggestedRpe,
                                    suggestedLbs = safeLbs,
                                    isCompleted = false
                                )
                            )
                        }
                    }
                }
                fullPlanData[workoutEntity] = setsForThisWorkout
            }
        }

        val planId = workoutDao.saveFullWorkoutPlan(planEntity, fullPlanData)
        return planId
    }

    suspend fun getPlanDetails(planId: Long): com.example.myapplication.data.WorkoutPlan {
        val workouts = workoutDao.getWorkoutsForPlan(planId)
        val allExercises = workoutDao.getAllExercisesOneShot()

        val weeklyPlans = mutableListOf<com.example.myapplication.data.WeeklyPlan>()

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

                        com.example.myapplication.data.Exercise(
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

                weeklyPlans.add(com.example.myapplication.data.WeeklyPlan(week = weekNum, days = dailyWorkouts))
            }
        }

        return com.example.myapplication.data.WorkoutPlan(
            explanation = "Your custom 4-week progression has been generated and saved.",
            weeks = weeklyPlans
        )
    }

    private fun getDayNameFromDate(date: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = date
        return cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault())
            ?: "Day"
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

    // --- UPDATED COMPLETE FUNCTION ---
    suspend fun completeWorkout(workoutId: Long): List<String> {
        val workout = workoutDao.getWorkoutById(workoutId) ?: return emptyList()
        val sets = workoutDao.getSetsForWorkoutList(workoutId)

        // 1. Archive to History
        val historyEntries = sets.filter { it.isCompleted }.map { set ->
            CompletedWorkoutEntity(
                exerciseId = set.exerciseId,
                date = workout.scheduledDate,
                reps = set.actualReps ?: set.suggestedReps,
                weight = (set.actualLbs ?: set.suggestedLbs).toInt(),
                rpe = (set.actualRpe ?: set.suggestedRpe).toInt()
            )
        }

        if (historyEntries.isNotEmpty()) {
            workoutDao.insertCompletedWorkouts(historyEntries)
        }

        // 2. Mark as Complete
        workoutDao.markWorkoutAsComplete(workoutId)

        // 3. Run Optimization and return the Report
        return autoRegulateFutureWorkouts(workout.planId, sets, workout.scheduledDate)
    }

    private suspend fun autoRegulateFutureWorkouts(
        planId: Long,
        completedSets: List<WorkoutSetEntity>,
        currentDate: Long
    ): List<String> {
        val adjustmentLogs = mutableListOf<String>()

        val exercisePerformance = completedSets
            .filter { it.isCompleted && it.actualRpe != null }
            .groupBy { it.exerciseId }

        if (exercisePerformance.isEmpty()) return emptyList()

        val futureWorkouts = workoutDao.getWorkoutsForPlan(planId)
            .filter { it.scheduledDate > currentDate && !it.isCompleted }

        if (futureWorkouts.isEmpty()) return emptyList()

        // Cache Exercise Names for the report
        val exercisesMap = workoutDao.getAllExercisesOneShot().associateBy { it.exerciseId }

        // Track which exercises we already adjusted to avoid duplicate logs
        val adjustedExerciseIds = mutableSetOf<Long>()

        futureWorkouts.forEach { futureWorkout ->
            val futureSets = workoutDao.getSetsForWorkoutList(futureWorkout.workoutId)
            var hasUpdates = false

            val updatedSets = futureSets.map { targetSet ->
                val pastPerformance = exercisePerformance[targetSet.exerciseId]

                if (pastPerformance != null) {
                    val avgActualRpe = pastPerformance.mapNotNull { it.actualRpe }.average()
                    val avgTargetRpe = pastPerformance.map { it.suggestedRpe }.average()
                    val rpeDiff = avgActualRpe - avgTargetRpe

                    val newWeight = when {
                        rpeDiff >= 1.0 -> (targetSet.suggestedLbs * 0.95).toInt()
                        rpeDiff <= -1.5 -> (targetSet.suggestedLbs * 1.05).toInt()
                        else -> targetSet.suggestedLbs
                    }

                    if (newWeight != targetSet.suggestedLbs) {
                        hasUpdates = true

                        // Log only once per exercise
                        if (!adjustedExerciseIds.contains(targetSet.exerciseId)) {
                            val exName = exercisesMap[targetSet.exerciseId]?.name ?: "Exercise"
                            val direction = if (newWeight > targetSet.suggestedLbs) "Increased" else "Decreased"
                            val reason = if (newWeight > targetSet.suggestedLbs) "Easy RPE" else "High Fatigue"
                            adjustmentLogs.add("$exName: $direction load ($reason)")
                            adjustedExerciseIds.add(targetSet.exerciseId)
                        }

                        targetSet.copy(suggestedLbs = newWeight)
                    } else {
                        targetSet
                    }
                } else {
                    targetSet
                }
            }

            if (hasUpdates) {
                workoutDao.insertSets(updatedSets)
            }
        }
        return adjustmentLogs
    }

    suspend fun getBestAlternatives(currentExercise: ExerciseEntity): List<ExerciseEntity> {
        // Provide a default empty string if muscleGroup is null to satisfy the DAO requirement
        val muscleGroup = currentExercise.muscleGroup ?: ""

        val candidates = workoutDao.getCandidatesByMuscleGroup(muscleGroup, currentExercise.exerciseId)

        return candidates.sortedWith(compareBy(
            { it.tier != currentExercise.tier },
            // Use safe comparison for equipment as well, which is also nullable in ExerciseEntity
            { it.equipment == currentExercise.equipment }
        )).take(2)
    }

    suspend fun swapExercise(workoutId: Long, oldExerciseId: Long, newExerciseId: Long) {
        workoutDao.swapExerciseInSets(workoutId, oldExerciseId, newExerciseId)
    }
}
