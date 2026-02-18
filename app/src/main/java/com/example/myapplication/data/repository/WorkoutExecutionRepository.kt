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
    val totalExercises: Int,
    val prsBroken: Int = 0,
    val topPR: String? = null,
    val highlights: List<String> = emptyList()
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

    suspend fun updateSetsWeight(workoutId: Long, exerciseId: Long, fromSetNumber: Int, newLbs: Float) {
        val allSets = workoutDao.getSetsForWorkoutList(workoutId)
        val setsToUpdate = allSets.filter { 
            it.exerciseId == exerciseId && it.setNumber >= fromSetNumber 
        }.map { 
            if (it.setNumber == fromSetNumber || !it.isCompleted) {
                it.copy(actualLbs = newLbs)
            } else {
                it
            }
        }
        if (setsToUpdate.isNotEmpty()) {
            workoutDao.insertSets(setsToUpdate)
        }
    }

    suspend fun updateSetsReps(workoutId: Long, exerciseId: Long, fromSetNumber: Int, newReps: Int) {
        val allSets = workoutDao.getSetsForWorkoutList(workoutId)
        val setsToUpdate = allSets.filter { 
            it.exerciseId == exerciseId && it.setNumber >= fromSetNumber 
        }.map { 
            if (it.setNumber == fromSetNumber || !it.isCompleted) {
                it.copy(actualReps = newReps)
            } else {
                it
            }
        }
        if (setsToUpdate.isNotEmpty()) {
            workoutDao.insertSets(setsToUpdate)
        }
    }

    suspend fun insertWorkout(workout: WorkoutEntity): Long {
        val isRecovery = checkRecoveryNeeded()
        val finalWorkout = if (isRecovery) {
            workout.copy(name = "Recovery: ${workout.name}")
        } else {
            workout
        }
        return workoutDao.insertDailyWorkout(
            com.example.myapplication.data.local.DailyWorkoutEntity(
                planId = 0,
                scheduledDate = workout.date,
                title = finalWorkout.name
            )
        )
    }

    suspend fun insertSets(sets: List<WorkoutSetEntity>) {
        val isRecovery = checkRecoveryNeeded()
        val finalSets = if (isRecovery) {
            sets.map { it.copy(suggestedReps = (it.suggestedReps * 0.7).toInt().coerceAtLeast(1)) }
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

        val volume = sets.sumOf { (it.actualLbs ?: 0f).toInt() * (it.actualReps ?: 0) }
        val focus = exercises.groupingBy { it.majorMuscle }.eachCount().maxByOrNull { it.value }?.key ?: "Full Body"

        var prsCount = 0
        var topPRString: String? = null
        val highlights = mutableListOf<String>()

        for (exercise in exercises) {
            val exerciseSets = sets.filter { it.exerciseId == exercise.exerciseId }
            val maxWeightSession = exerciseSets.maxOfOrNull { it.actualLbs ?: 0f } ?: 0f
            val totalReps = exerciseSets.sumOf { it.actualReps ?: 0 }

            if (totalReps > 0) highlights.add("${exercise.name}: ${exerciseSets.size} sets")

            try {
                val history = workoutDao.getCompletedWorkoutsForExercise(exercise.exerciseId).firstOrNull() ?: emptyList()
                val previousHistory = history.filter { it.completedWorkout.date != workout.scheduledDate }
                val maxWeightHistory = previousHistory.maxOfOrNull { it.completedWorkout.weight } ?: 0

                if (maxWeightSession > maxWeightHistory && maxWeightSession > 0) {
                    prsCount++
                    if (topPRString == null) {
                        topPRString = "${exercise.name} @ ${maxWeightSession.toInt()}lbs"
                    }
                }
            } catch (e: Exception) {
                Log.e("Summary", "Error checking PR", e)
            }
        }

        val title = if (prsCount > 0) "New Records!" else "$focus Session"
        val subtitle = if (prsCount > 0) "Smashed $prsCount PRs today!" else "High volume $focus training complete."

        return WorkoutSummaryResult(
            title = title,
            subtitle = subtitle,
            totalVolume = volume,
            totalExercises = exercises.size,
            prsBroken = prsCount,
            topPR = topPRString,
            highlights = highlights // FIX: Removed .take(3) to include all exercises
        )
    }

    suspend fun completeWorkout(workoutId: Long): WorkoutSummaryResult {
        val summary = getWorkoutSummary(workoutId)
        val workout = workoutDao.getWorkoutById(workoutId) ?: return summary
        val sets = workoutDao.getSetsForWorkoutList(workoutId).filter { it.isCompleted }

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

        return summary
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

    suspend fun addSet(workoutId: Long, exerciseId: Long) {
        val sets = workoutDao.getSetsForWorkoutList(workoutId).filter { it.exerciseId == exerciseId }.sortedBy { it.setNumber }
        val lastSet = sets.lastOrNull()
        val newSet = WorkoutSetEntity(
            workoutId = workoutId,
            exerciseId = exerciseId,
            setNumber = (lastSet?.setNumber ?: 0) + 1,
            suggestedReps = lastSet?.suggestedReps ?: 10,
            suggestedLbs = lastSet?.suggestedLbs ?: 0,
            suggestedRpe = lastSet?.suggestedRpe ?: 8,
            isCompleted = false
        )
        workoutDao.insertSets(listOf(newSet))
    }

    suspend fun addExercise(workoutId: Long, exerciseId: Long) {
        val newSet = WorkoutSetEntity(
            workoutId = workoutId,
            exerciseId = exerciseId,
            setNumber = 1,
            suggestedReps = 10,
            suggestedLbs = 0,
            suggestedRpe = 8,
            isCompleted = false
        )
        workoutDao.insertSets(listOf(newSet))
    }

    private suspend fun checkRecoveryNeeded(): Boolean {
        val now = Instant.now()
        val yesterday = now.minus(Duration.ofHours(24))
        val sleepDuration = healthConnectManager.getDailySleepDuration(yesterday, now)
        return sleepDuration.toMinutes() in 1..359
    }
}
