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
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

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

    // --- ADDED: Missing method needed by ViewModel ---
    suspend fun getWorkoutById(workoutId: Long): WorkoutEntity? = workoutDao.getWorkoutById(workoutId)

    // --- WRITES ---
    suspend fun updateSet(set: WorkoutSetEntity) = workoutDao.updateSet(set)

    // FIX 1: Bio-Sync Title Adjustment
    suspend fun insertWorkout(workout: WorkoutEntity): Long {
        val isRecovery = checkRecoveryNeeded()
        val finalWorkout = if (isRecovery) {
            Log.d("BioSync", "Sleep < 6h. Tagging workout as Recovery Session.")
            workout.copy(name = "Recovery: ${workout.name}")
        } else {
            workout
        }
        return workoutDao.insertWorkout(finalWorkout)
    }

    // 2. Save Sets AS IS (Let the UI handle the reduction dialog)
    suspend fun insertSets(sets: List<WorkoutSetEntity>) {
        workoutDao.insertSets(sets)
    }

    suspend fun completeWorkout(workoutId: Long): List<String> {
        val workout = workoutDao.getWorkoutById(workoutId) ?: return emptyList()
        val sets = workoutDao.getSetsForWorkoutList(workoutId)
        val historyEntries = sets.filter { it.isCompleted }.map { set ->
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
        return emptyList()
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

    suspend fun swapExercise(workoutId: Long, oldExerciseId: Long, newExerciseId: Long) {
        workoutDao.swapExerciseInSets(workoutId, oldExerciseId, newExerciseId)
    }

    // --- HELPER FUNCTION ---
    private suspend fun checkRecoveryNeeded(): Boolean {
        val now = Instant.now()
        val yesterday = now.minus(Duration.ofHours(24))
        val sleepDuration = healthConnectManager.getDailySleepDuration(yesterday, now)
        return sleepDuration.toMinutes() in 1..359
    }
}