package com.example.myapplication.data.repository

import android.util.Log
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
    // --- READS (Keep existing) ---
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

    // --- PLAN GENERATION (FIXED) ---

    suspend fun generateAndSavePlan(
        goal: String,
        duration: Int,
        days: List<String>,
        programType: String = "Hypertrophy"
    ): Long {
        Log.d("WorkoutRepo", "Generating Plan for: $goal")

        // 1. Get Context
        val workoutHistory = workoutDao.getCompletedWorkoutsWithExercise().first()
        val availableExercises = workoutDao.getAllExercises().first()

        // 2. Get Week 1 Template from AI
        val aiResponse = bedrockClient.generateWorkoutPlan(
            goal = goal,
            duration = duration.toFloat(),
            days = days,
            programType = programType,
            workoutHistory = workoutHistory,
            availableExercises = availableExercises
        )

        // 3. PREPARE BATCH DATA (Do not save yet)
        val startDate = System.currentTimeMillis()
        val planEntity = WorkoutPlanEntity(
            name = "$programType for $goal",
            startDate = startDate,
            goal = goal,
            programType = programType
        )

        val fullPlanData = mutableMapOf<DailyWorkoutEntity, List<WorkoutSetEntity>>()
        val totalWeeks = 4

        // 4. LOOP 4 TIMES (Generate 4 weeks from 1 template)
        for (weekNum in 1..totalWeeks) {
            aiResponse.schedule.forEach { templateDay ->

                // A. Smart Date Calculation (Fixes the "Past Date" bug)
                val workoutDate = calculateSmartDate(startDate, weekNum, templateDay.day)

                // B. Create Workout Object (ID is 0, will be set by DAO)
                val workoutEntity = DailyWorkoutEntity(
                    planId = 0,
                    scheduledDate = workoutDate,
                    title = templateDay.title,
                    isCompleted = false
                )

                // C. Generate Sets
                val setsForThisWorkout = mutableListOf<WorkoutSetEntity>()

                templateDay.exercises.forEach { aiEx ->
                    val realEx = availableExercises.find { it.name.equals(aiEx.name, ignoreCase = true) }

                    realEx?.let { validExercise ->
                        // Periodization Logic
                        val adjustedSets = when (weekNum) {
                            4 -> (aiEx.sets / 2).coerceAtLeast(2) // Deload
                            2, 3 -> aiEx.sets + 1                 // Progression
                            else -> aiEx.sets                     // Baseline
                        }

                        val adjustedLbs = if (weekNum == 3) {
                            (aiEx.suggestedLbs * 1.05f).toInt()   // Intensity Peak
                        } else {
                            aiEx.suggestedLbs.toInt()
                        }

                        // Safety check for empty weight
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

        // 5. ATOMIC SAVE (Using the DAO Transaction function)
        val planId = workoutDao.saveFullWorkoutPlan(planEntity, fullPlanData)
        return planId
    }

    // --- NEW: Fetch full plan for UI display ---
    suspend fun getPlanDetails(planId: Long): com.example.myapplication.data.WorkoutPlan {
        // 1. Fetch raw lists synchronously
        val workouts = workoutDao.getWorkoutsForPlan(planId)
        val allExercises = workoutDao.getAllExercisesOneShot()

        val weeklyPlans = mutableListOf<com.example.myapplication.data.WeeklyPlan>()

        if (workouts.isNotEmpty()) {
            val startDate = workouts.first().scheduledDate

            // Group workouts by Week # (1-based index)
            // Logic: (WorkoutDate - FirstDate) / DaysInMilliseconds + 1
            val workoutsByWeek = workouts.groupBy {
                val diff = it.scheduledDate - startDate
                // 1 week = 604800000 ms. We add a buffer or use integer division.
                // Adding 1 ensures the first workout is Week 1.
                (diff / (1000L * 60 * 60 * 24 * 7)).toInt() + 1
            }

            workoutsByWeek.forEach { (weekNum, dailyEntities) ->
                val dailyWorkouts = dailyEntities.map { entity ->
                    val sets = workoutDao.getSetsForWorkoutList(entity.workoutId)

                    // Group sets by exerciseId to reconstruct the "Exercise" object
                    val exercisesForDay = sets.groupBy { it.exerciseId }.map { (exId, setList) ->
                        val realEx = allExercises.find { it.exerciseId == exId }
                        val firstSet = setList.first() // Use first set for shared metadata (RPE, etc)

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
        return cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()) ?: "Day"
    }

    /**
     * Logic: If today is Wednesday and target is Monday, schedule for NEXT Monday (offset 5 days),
     * NOT last Monday (offset -2 days).
     */
    private fun calculateSmartDate(startDate: Long, weekNum: Int, dayName: String): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = startDate

        // 1. Parse Target Day (Handle abbreviations like "Mon", "Tue")
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

        // 2. Calculate Offset from Today
        val currentDay = cal.get(Calendar.DAY_OF_WEEK)
        var dayOffset = targetDay - currentDay

        // If the target day is earlier in the week than today, move to next week
        if (dayOffset < 0) {
            dayOffset += 7
        }

        // 3. Add Week Multiplier
        // weekNum 1 = +0 weeks
        // weekNum 2 = +1 week (7 days)
        val totalDaysToAdd = dayOffset + ((weekNum - 1) * 7)

        cal.add(Calendar.DAY_OF_YEAR, totalDaysToAdd)

        // 4. Normalize Time
        cal.set(Calendar.HOUR_OF_DAY, 12)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)

        return cal.timeInMillis
    }

    suspend fun markWorkoutAsComplete(workoutId: Long) = workoutDao.markWorkoutAsComplete(workoutId)
}