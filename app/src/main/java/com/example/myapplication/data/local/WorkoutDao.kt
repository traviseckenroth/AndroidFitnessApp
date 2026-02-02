package com.example.myapplication.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    // --- READS ---
    @Query("SELECT * FROM exercises")
    fun getAllExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE exerciseId IN (:exerciseIds)")
    fun getExercisesByIds(exerciseIds: List<Long>): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises")
    suspend fun getAllExercisesOneShot(): List<ExerciseEntity>

    // --- ADDED THIS QUERY ---
    @Query("SELECT * FROM daily_workouts WHERE workoutId = :workoutId")
    suspend fun getWorkoutById(workoutId: Long): DailyWorkoutEntity?
    // ------------------------

    @Query("SELECT * FROM daily_workouts WHERE scheduledDate >= :startOfDay AND scheduledDate < :endOfDay LIMIT 1")
    fun getWorkoutByDate(startOfDay: Long, endOfDay: Long): Flow<DailyWorkoutEntity?>

    @Query("SELECT DISTINCT scheduledDate FROM daily_workouts")
    fun getAllWorkoutDates(): Flow<List<Long>>

    @Query("SELECT * FROM workout_sets WHERE workoutId = :workoutId ORDER BY exerciseId, setNumber")
    fun getSetsForWorkout(workoutId: Long): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM daily_workouts WHERE isCompleted = 1 ORDER BY scheduledDate DESC")
    fun getCompletedWorkouts(): Flow<List<DailyWorkoutEntity>>

    @Transaction
    @Query("SELECT * FROM completed_workouts")
    fun getCompletedWorkoutsWithExercise(): Flow<List<CompletedWorkoutWithExercise>>

    @Transaction
    @Query("SELECT * FROM completed_workouts WHERE exerciseId = :exerciseId")
    fun getCompletedWorkoutsForExercise(exerciseId: Long): Flow<List<CompletedWorkoutWithExercise>>

    @Query("SELECT * FROM exercises WHERE name = :name LIMIT 1")
    suspend fun getExerciseByName(name: String): ExerciseEntity?

    // --- NEW QUERIES FOR UI DISPLAY ---
    @Query("SELECT * FROM daily_workouts WHERE planId = :planId ORDER BY scheduledDate ASC")
    suspend fun getWorkoutsForPlan(planId: Long): List<DailyWorkoutEntity>

    @Query("SELECT * FROM workout_sets WHERE workoutId = :workoutId ORDER BY setNumber ASC")
    suspend fun getSetsForWorkoutList(workoutId: Long): List<WorkoutSetEntity>

    // --- OPTIMIZATION: SQL AGGREGATION ---
    @MapInfo(keyColumn = "muscleGroup", valueColumn = "volume")
    @Query("""
        SELECT 
            e.muscleGroup as muscleGroup, 
            SUM(c.weight * c.reps) as volume 
        FROM completed_workouts c
        JOIN exercises e ON c.exerciseId = e.exerciseId
        WHERE e.muscleGroup IS NOT NULL
        GROUP BY e.muscleGroup
    """)
    fun getVolumeByMuscleGroup(): Flow<Map<String, Double>>

    // --- WRITES ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: WorkoutPlanEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyWorkout(workout: DailyWorkoutEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: ExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSets(sets: List<WorkoutSetEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletedWorkouts(completedWorkouts: List<CompletedWorkoutEntity>): List<Long>

    @Update
    suspend fun updateSet(set: WorkoutSetEntity): Int

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

    @Query("UPDATE daily_workouts SET isCompleted = 1 WHERE workoutId = :workoutId")
    suspend fun markWorkoutAsComplete(workoutId: Long): Int
}