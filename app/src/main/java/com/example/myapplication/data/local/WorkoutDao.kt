package com.example.myapplication.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    // --- FOOD LOGGING (NEW) ---

    @Insert
    suspend fun insertFoodLog(log: FoodLogEntity): Long

    @Query("SELECT * FROM food_logs WHERE date >= :startOfDay AND date < :endOfDay ORDER BY date DESC")
    fun getFoodLogsForDate(startOfDay: Long, endOfDay: Long): Flow<List<FoodLogEntity>>

    @Query("SELECT * FROM food_logs ORDER BY date DESC LIMIT 50")
    fun getRecentFoodLogs(): Flow<List<FoodLogEntity>>

    // --- EXERCISE READS ---

    // NEW: Get distinct food names for autocomplete history
    @Query("SELECT DISTINCT inputQuery FROM food_logs ORDER BY date DESC LIMIT 20")
    fun getRecentFoodQueries(): Flow<List<String>>
    @Query("SELECT * FROM exercises")
    fun getAllExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE exerciseId IN (:exerciseIds)")
    fun getExercisesByIds(exerciseIds: List<Long>): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises")
    suspend fun getAllExercisesOneShot(): List<ExerciseEntity>

    // Updated Swap Logic: Enforce Same Tier
    @Query("SELECT * FROM exercises WHERE majorMuscle = :majorMuscle AND tier = :targetTier AND exerciseId != :currentId")
    suspend fun getAlternativesByMajorMuscleAndTier(majorMuscle: String, targetTier: Int, currentId: Long): List<ExerciseEntity>

    // --- WORKOUT READS ---
    @Query("SELECT * FROM daily_workouts WHERE workoutId = :workoutId")
    suspend fun getWorkoutById(workoutId: Long): DailyWorkoutEntity?

    @Query("SELECT * FROM daily_workouts WHERE scheduledDate >= :startOfDay AND scheduledDate < :endOfDay LIMIT 1")
    fun getWorkoutByDate(startOfDay: Long, endOfDay: Long): Flow<DailyWorkoutEntity?>

    @Query("SELECT DISTINCT scheduledDate FROM daily_workouts")
    fun getAllWorkoutDates(): Flow<List<Long>>

    @Query("SELECT * FROM workout_sets WHERE workoutId = :workoutId ORDER BY exerciseId, setNumber")
    fun getSetsForWorkout(workoutId: Long): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM daily_workouts WHERE isCompleted = 1 ORDER BY scheduledDate DESC")
    fun getCompletedWorkouts(): Flow<List<DailyWorkoutEntity>>

    @Query("SELECT * FROM daily_workouts WHERE planId = :planId ORDER BY scheduledDate ASC")
    suspend fun getWorkoutsForPlan(planId: Long): List<DailyWorkoutEntity>

    @Query("SELECT * FROM workout_sets WHERE workoutId = :workoutId ORDER BY setNumber ASC")
    suspend fun getSetsForWorkoutList(workoutId: Long): List<WorkoutSetEntity>

    // --- HISTORY / COMPLETED ---
    @Transaction
    @Query("SELECT * FROM completed_workouts")
    fun getCompletedWorkoutsWithExercise(): Flow<List<CompletedWorkoutWithExercise>>

    @Transaction
    @Query("SELECT * FROM completed_workouts WHERE exerciseId = :exerciseId")
    fun getCompletedWorkoutsForExercise(exerciseId: Long): Flow<List<CompletedWorkoutWithExercise>>

    // --- PLAN / NUTRITION ---
    @Query("SELECT * FROM workout_plans ORDER BY startDate DESC LIMIT 1")
    suspend fun getLatestPlan(): WorkoutPlanEntity?

    @Query("SELECT * FROM workout_plans WHERE planId = :planId")
    suspend fun getPlanById(planId: Long): WorkoutPlanEntity?

    // --- WRITES (Suspend functions returning Long/Int/List<Long>) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: WorkoutPlanEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyWorkout(workout: DailyWorkoutEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: WorkoutEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: ExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSets(sets: List<WorkoutSetEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletedWorkouts(completedWorkouts: List<CompletedWorkoutEntity>): List<Long>

    @Update
    suspend fun updateSet(set: WorkoutSetEntity): Int

    @Query("UPDATE workout_sets SET exerciseId = :newExerciseId WHERE workoutId = :workoutId AND exerciseId = :oldExerciseId")
    suspend fun swapExerciseInSets(workoutId: Long, oldExerciseId: Long, newExerciseId: Long): Int

    @Query("UPDATE workout_plans SET nutritionJson = :nutritionJson WHERE planId = :planId")
    suspend fun updateNutrition(planId: Long, nutritionJson: String): Int

    @Query("UPDATE daily_workouts SET isCompleted = 1 WHERE workoutId = :workoutId")
    suspend fun markWorkoutAsComplete(workoutId: Long): Int

    @Query("DELETE FROM daily_workouts WHERE scheduledDate >= :currentTime AND isCompleted = 0")
    suspend fun deleteFutureUncompletedWorkouts(currentTime: Long): Int

    @Query("DELETE FROM exercises")
    suspend fun deleteAllExercises(): Int

    @Transaction
    suspend fun saveFullWorkoutPlan(
        plan: WorkoutPlanEntity,
        workoutsWithSets: Map<DailyWorkoutEntity, List<WorkoutSetEntity>>
    ): Long {
        val planId = insertPlan(plan)
        workoutsWithSets.forEach { (workout, sets) ->
            val workoutId = insertDailyWorkout(workout.copy(planId = planId))
            val setsWithWorkoutId = sets.map { it.copy(workoutId = workoutId) }
            insertSets(setsWithWorkoutId)
        }
        return planId
    }
}