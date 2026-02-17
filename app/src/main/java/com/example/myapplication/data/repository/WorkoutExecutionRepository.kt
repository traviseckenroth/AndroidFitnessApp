package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.data.local.CompletedWorkoutEntity
import com.example.myapplication.data.local.CompletedWorkoutWithExercise
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.local.WorkoutDao
import com.example.myapplication.data.local.WorkoutEntity
import com.example.myapplication.data.local.WorkoutSetEntity
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

data class WorkoutSummaryResult(
    val title: String,
    val subtitle: String,
    val totalVolume: Int,
    val totalExercises: Int
)

@Singleton
class WorkoutExecutionRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val healthConnectManager: HealthConnectManager
) {
    // --- READS ---
    fun getSetsForSession(workoutId: Long): Flow<List<WorkoutSetEntity>> = workoutDao.getSetsForWorkout(workoutId)

    fun getAllExercises(): Flow<List<ExerciseEntity>> = workoutDao.getAllExercises()

    fun getExercisesByIds(exerciseIds: List<Long>): Flow<List<ExerciseEntity>> = workoutDao.getExercisesByIds(exerciseIds)

    fun getAllCompletedWorkouts(): Flow<List<CompletedWorkoutWithExercise>> = workoutDao.getCompletedWorkoutsWithExercise()

    fun getCompletedWorkoutsForExercise(exerciseId: Long): Flow<List<CompletedWorkoutWithExercise>> =
        workoutDao.getCompletedWorkoutsForExercise(exerciseId)

    // --- WRITES ---
    suspend fun updateSet(set: WorkoutSetEntity) = workoutDao.updateSet(set)

    // FIX 1: Bio-Sync Title Adjustment
    // Intercepts the workout creation to check recovery status
    suspend fun insertWorkout(workout: WorkoutEntity): Long {
        val isRecovery = checkRecoveryNeeded()

        val finalWorkout = if (isRecovery) {
            Log.d("BioSync", "Recovery Mode Detected. Marking workout as Recovery Session.")
            workout.copy(name = "Recovery: ${workout.name}")
        } else {
            workout
        }
        return workoutDao.insertWorkout(finalWorkout)
    }

    // FIX 2: Bio-Sync Volume Reduction (30%)
    // Intercepts the set creation to reduce reps if recovery is needed
    suspend fun insertSets(sets: List<WorkoutSetEntity>) {
        val isRecovery = checkRecoveryNeeded()

        val finalSets = if (isRecovery) {
            Log.d("BioSync", "Reducing workout volume by 30% for recovery.")
            sets.map { set ->
                set.copy(
                    // Reduce suggested reps by 30% (keep at least 1 rep)
                    suggestedReps = (set.suggestedReps * 0.7).toInt().coerceAtLeast(1)
                )
            }
        } else {
            sets
        }
        workoutDao.insertSets(finalSets)
    }
    suspend fun getWorkoutSummary(workoutId: Long): WorkoutSummaryResult {
        val workout = workoutDao.getWorkoutById(workoutId)
            ?: return WorkoutSummaryResult("Workout Complete", "Good job!", 0, 0)

        val sets = workoutDao.getSetsForWorkoutList(workoutId).filter { it.isCompleted }
        val exercises = workoutDao.getExercisesForWorkoutOneShot(workoutId)

        // 1. Calculate Stats
        val volume = sets.sumOf { (it.actualLbs ?: 0f).toInt() * (it.actualReps ?: 0) }

        // 2. Determine Focus
        val focus = exercises.groupingBy { it.majorMuscle }.eachCount().maxByOrNull { it.value }?.key ?: "Full Body"

        // 3. Check for PRs
        var isPr = false
        var prMessage = "Great job sticking to the plan."

        for (exercise in exercises) {
            val exerciseSets = sets.filter { it.exerciseId == exercise.exerciseId }
            val maxWeightSession = exerciseSets.maxOfOrNull { it.actualLbs ?: 0f } ?: 0f

            try {
                val history = workoutDao.getCompletedWorkoutsForExercise(exercise.exerciseId).firstOrNull() ?: emptyList()
                // Filter out current workout entries if they were already saved to avoid self-comparison
                val previousHistory = history.filter { it.completedWorkout.date != workout.scheduledDate }
                val maxWeightHistory = previousHistory.maxOfOrNull { it.completedWorkout.weight } ?: 0

                if (maxWeightSession > maxWeightHistory && maxWeightSession > 0) {
                    isPr = true
                    prMessage = "You set a new ${exercise.name} PR of ${maxWeightSession.toInt()}lbs!"
                    break
                }
            } catch (e: Exception) {
                Log.e("Summary", "Error checking PR", e)
            }
        }

        val title = if (isPr) "New Record!" else "$focus Workout"
        val subtitle = if (isPr) prMessage else "Way to destroy those $focus muscles."

        return WorkoutSummaryResult(title, subtitle, volume, exercises.size)
    }
    suspend fun completeWorkout(workoutId: Long): WorkoutSummaryResult {
        val workout = workoutDao.getWorkoutById(workoutId)
            ?: return WorkoutSummaryResult("Workout Complete", "Good job!", 0, 0)

        val sets = workoutDao.getSetsForWorkoutList(workoutId).filter { it.isCompleted }
        val exercises = workoutDao.getExercisesForWorkoutOneShot(workoutId)

        // 1. Calculate Stats
        val volume = sets.sumOf { (it.actualLbs ?: 0f).toInt() * (it.actualReps ?: 0) }

        // 2. Determine Focus
        val focus = exercises.groupingBy { it.majorMuscle }.eachCount().maxByOrNull { it.value }?.key ?: "Full Body"

        // 3. Check for PRs
        var isPr = false
        var prMessage = "Great job sticking to the plan."

        for (exercise in exercises) {
            val exerciseSets = sets.filter { it.exerciseId == exercise.exerciseId }
            val maxWeightSession = exerciseSets.maxOfOrNull { it.actualLbs ?: 0f } ?: 0f

            // Check history
            try {
                // Using firstOrNull to avoid crashes if flow is empty
                val history = workoutDao.getCompletedWorkoutsForExercise(exercise.exerciseId).firstOrNull() ?: emptyList()
                val maxWeightHistory = history.maxOfOrNull { it.completedWorkout.weight } ?: 0

                if (maxWeightSession > maxWeightHistory && maxWeightSession > 0) {
                    isPr = true
                    prMessage = "You set a new ${exercise.name} PR of ${maxWeightSession.toInt()}lbs!"
                    break
                }
            } catch (e: Exception) {
                Log.e("Summary", "Error checking PR", e)
            }
        }

        val title = if (isPr) "New Record!" else "$focus Workout"
        val subtitle = if (isPr) prMessage else "Way to destroy those $focus muscles."

        // Save History
        val historyEntries = sets.map { set ->
            CompletedWorkoutEntity(
                exerciseId = set.exerciseId,
                date = workout.scheduledDate,
                reps = set.actualReps ?: set.suggestedReps,
                weight = (set.actualLbs ?: set.suggestedLbs).toInt(),
                rpe = (set.actualRpe ?: set.suggestedRpe).toInt()
            )
        }
        if (historyEntries.isNotEmpty()) workoutDao.insertCompletedWorkouts(historyEntries)
        workoutDao.markWorkoutAsComplete(workoutId)

        return WorkoutSummaryResult(title, subtitle, volume, exercises.size)
    }

    suspend fun injectWarmUpSets(workoutId: Long, exerciseId: Long, workingWeight: Int) {
        val currentSets = workoutDao.getSetsForWorkoutList(workoutId).filter { it.exerciseId == exerciseId }.sortedBy { it.setNumber }
        if (currentSets.isEmpty()) return

        fun roundToFive(w: Double) = (w / 5).toInt() * 5

        val warmups = listOf(
            WorkoutSetEntity(workoutId = workoutId, exerciseId = exerciseId, setNumber = 1, suggestedReps = 10, suggestedLbs = 45, suggestedRpe = 0, isCompleted = false),
            WorkoutSetEntity(workoutId = workoutId, exerciseId = exerciseId, setNumber = 2, suggestedReps = 5, suggestedLbs = roundToFive(workingWeight * 0.5), suggestedRpe = 0, isCompleted = false),
            WorkoutSetEntity(workoutId = workoutId, exerciseId = exerciseId, setNumber = 3, suggestedReps = 3, suggestedLbs = roundToFive(workingWeight * 0.75), suggestedRpe = 0, isCompleted = false)
        )
        val updatedOriginalSets = currentSets.map { it.copy(setNumber = it.setNumber + 3) }
        updatedOriginalSets.forEach { workoutDao.updateSet(it) }
        workoutDao.insertSets(warmups)
    }

    suspend fun getBestAlternatives(currentExercise: ExerciseEntity): List<ExerciseEntity> {
        val candidates = workoutDao.getAlternativesByMajorMuscleAndTier(currentExercise.majorMuscle, currentExercise.tier, currentExercise.exerciseId)
        return candidates.distinctBy { it.name }.sortedByDescending { it.equipment == currentExercise.equipment }.take(5)
    }
    suspend fun deleteSets(sets: List<WorkoutSetEntity>): Int {
        return if (sets.isNotEmpty()) {
            workoutDao.deleteSets(sets)
        } else {
            0
        }
    }
    suspend fun swapExercise(workoutId: Long, oldExerciseId: Long, newExerciseId: Long) {
        workoutDao.swapExerciseInSets(workoutId, oldExerciseId, newExerciseId)
    }

    // --- HELPER FUNCTION ---
    // Centralized logic to check if sleep was under 6 hours in the last 24h
    private suspend fun checkRecoveryNeeded(): Boolean {
        // 1. Define window: Look at sleep from the last 24 hours
        val now = Instant.now()
        val yesterday = now.minus(Duration.ofHours(24))

        // 2. Fetch Data
        val sleepDuration = healthConnectManager.getDailySleepDuration(yesterday, now)

        // 3. Evaluate: Sleep is positive but less than 6 hours (360 minutes)
        return sleepDuration.toMinutes() in 1..359
    }
}
