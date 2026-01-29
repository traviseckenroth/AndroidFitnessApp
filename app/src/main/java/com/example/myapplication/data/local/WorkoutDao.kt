package com.example.myapplication.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    // --- READS ---
    @Query("SELECT * FROM exercises")
    fun getAllExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM daily_workouts WHERE scheduledDate = :date LIMIT 1")
    fun getWorkoutByDate(date: Long): Flow<DailyWorkoutEntity?>

    @Query("SELECT * FROM workout_sets WHERE workoutId = :workoutId ORDER BY exerciseId, setNumber")
    fun getSetsForWorkout(workoutId: Long): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM daily_workouts WHERE isCompleted = 1 ORDER BY scheduledDate DESC")
    fun getCompletedWorkouts(): Flow<List<DailyWorkoutEntity>>

    @Query("""
        SELECT cw.*, e.name
        FROM completed_workouts cw
        JOIN exercises e ON cw.exerciseId = e.exerciseId
        WHERE cw.exerciseId = :exerciseId 
        ORDER BY cw.date DESC
    """)
    fun getCompletedWorkoutsForExercise(exerciseId: Long): Flow<List<CompletedWorkoutWithExercise>>

    @Query("SELECT * FROM completed_workouts")
    fun getAllCompletedWorkouts(): Flow<List<CompletedWorkoutEntity>>

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
}