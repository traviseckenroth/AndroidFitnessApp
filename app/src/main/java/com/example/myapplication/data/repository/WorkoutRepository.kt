package com.example.myapplication.data.repository

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
    // --- Session & Real-time Tracking ---

    fun getWorkoutForDate(date: Long): Flow<DailyWorkoutEntity?> {
        val cal = Calendar.getInstance()
        cal.timeInMillis = date
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis

        cal.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = cal.timeInMillis

        return workoutDao.getWorkoutByDate(startOfDay, endOfDay)
    }

    fun getAllWorkoutDates(): Flow<List<Long>> = workoutDao.getAllWorkoutDates() // Ensure this exists in DAO or remove

    fun getSetsForSession(workoutId: Long): Flow<List<WorkoutSetEntity>> =
        workoutDao.getSetsForWorkout(workoutId)

    suspend fun updateSet(set: WorkoutSetEntity) =
        workoutDao.updateSet(set)

    fun getAllExercises(): Flow<List<ExerciseEntity>> =
        workoutDao.getAllExercises()

    // --- Profile & AI Plan Features ---

    fun getAllCompletedWorkouts(): Flow<List<CompletedWorkoutWithExercise>> =
        workoutDao.getCompletedWorkoutsWithExercise()

    fun getCompletedWorkoutsForExercise(exerciseId: Long): Flow<List<CompletedWorkoutWithExercise>> {
        return workoutDao.getCompletedWorkoutsForExercise(exerciseId)
    }

    fun getUserHeight(): Flow<Int> = userPrefs.userHeight

    fun getUserWeight(): Flow<Double> = userPrefs.userWeight

    suspend fun saveBiometrics(height: Int, weight: Double) {
        userPrefs.saveBiometrics(height, weight)
    }

    // --- PLAN GENERATION & SAVING ---

    /**
     * Generates a plan using AI, hydrates it with local DB data, and saves everything to Room.
     * Returns the ID of the newly created Plan.
     */
    suspend fun generateAndSavePlan(
        goal: String,
        duration: Int,
        days: List<String>,
        programType: String = "Hypertrophy" // Default if not provided
    ): Long {

        // 1. GATHER CONTEXT DATA
        // We use .first() to get a snapshot of the current state for the AI prompt
        val workoutHistory = workoutDao.getCompletedWorkoutsWithExercise().first()
        val availableExercises = workoutDao.getAllExercises().first()

        // 2. CALL AI CLIENT
        val aiResponse = bedrockClient.generateWorkoutPlan(
            goal = goal,
            duration = duration.toFloat(), // Convert int minutes/hours to float as expected by client
            days = days,
            programType = programType,
            workoutHistory = workoutHistory,
            availableExercises = availableExercises
        )

        // 3. HYDRATION: Merge AI Suggestions with Local Database Truth
        val localExercises = availableExercises

        val planEntity = WorkoutPlanEntity(
            name = "$programType for $goal",
            startDate = System.currentTimeMillis(),
            goal = goal,
            programType = programType
        )

        val workoutsWithSets = aiResponse.schedule.map { daySchedule ->
            val workoutDate = calculateDateForDay(planEntity.startDate, daySchedule.week, daySchedule.day)
            val workoutEntity = DailyWorkoutEntity(
                planId = 0, // Placeholder, will be updated by DAO
                scheduledDate = workoutDate,
                title = daySchedule.title,
                isCompleted = false
            )

            val sets = daySchedule.exercises.mapNotNull { aiEx ->
                localExercises.find { it.name.equals(aiEx.name, ignoreCase = true) }?.let { validExercise ->
                    (1..aiEx.sets).map { setNum ->
                        WorkoutSetEntity(
                            workoutId = 0, // Placeholder, will be updated by DAO
                            exerciseId = validExercise.exerciseId,
                            setNumber = setNum,
                            suggestedReps = aiEx.suggestedReps,
                            suggestedRpe = aiEx.suggestedRpe,
                            suggestedLbs = aiEx.suggestedLbs.toInt(),
                            isCompleted = false
                        )
                    }
                }
            }.flatten()

            workoutEntity to sets
        }.toMap()

        return workoutDao.saveFullWorkoutPlan(planEntity, workoutsWithSets)
    }


    /**
     * Helper to calculate the timestamp for "Week 2, Monday" based on a start date.
     */
    private fun calculateDateForDay(startDate: Long, week: Int, dayName: String): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = startDate

        // 1. Move to the correct week
        cal.add(Calendar.WEEK_OF_YEAR, week - 1)

        // 2. Map day name string (e.g., "Monday") to Calendar constant
        val targetDay = when (dayName.lowercase(Locale.getDefault())) {
            "sunday" -> Calendar.SUNDAY
            "monday" -> Calendar.MONDAY
            "tuesday" -> Calendar.TUESDAY
            "wednesday" -> Calendar.WEDNESDAY
            "thursday" -> Calendar.THURSDAY
            "friday" -> Calendar.FRIDAY
            "saturday" -> Calendar.SATURDAY
            else -> Calendar.MONDAY // Default fallback
        }

        // 3. Set the day of the week
        cal.set(Calendar.DAY_OF_WEEK, targetDay)

        // 4. Ensure we don't schedule in the past if possible,
        // or just accept the calc (for Week 1, if today is Wed and we schedule Mon, it might be past.
        // Simple logic usually suffices for plans).

        return cal.timeInMillis
    }
}