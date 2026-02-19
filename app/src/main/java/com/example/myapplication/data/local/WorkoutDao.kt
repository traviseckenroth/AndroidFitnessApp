// File: app/src/main/java/com/example/myapplication/data/local/WorkoutDao.kt
package com.example.myapplication.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class MuscleVolumeAggregation(
    val muscleGroup: String,
    val totalVolume: Double
)

@Dao
interface WorkoutDao {

    // --- FOOD LOGGING ---
    @Insert
    suspend fun insertFoodLog(log: FoodLogEntity): Long

    @Query("SELECT * FROM food_logs WHERE date >= :startOfDay AND date < :endOfDay ORDER BY date DESC")
    fun getFoodLogsForDate(startOfDay: Long, endOfDay: Long): Flow<List<FoodLogEntity>>

    @Query("SELECT * FROM food_logs ORDER BY date DESC LIMIT 50")
    fun getRecentFoodLogs(): Flow<List<FoodLogEntity>>

    @Query("SELECT DISTINCT inputQuery FROM food_logs ORDER BY date DESC LIMIT 20")
    fun getRecentFoodQueries(): Flow<List<String>>

    // --- EXERCISE READS ---
    @Query("SELECT * FROM exercises")
    fun getAllExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE exerciseId IN (:exerciseIds)")
    fun getExercisesByIds(exerciseIds: List<Long>): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises")
    suspend fun getAllExercisesOneShot(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE majorMuscle = :majorMuscle AND tier = :targetTier AND exerciseId != :currentId")
    suspend fun getAlternativesByMajorMuscleAndTier(majorMuscle: String, targetTier: Int, currentId: Long): List<ExerciseEntity>

    @Query("""
        SELECT DISTINCT e.* FROM exercises e
        INNER JOIN workout_sets s ON e.exerciseId = s.exerciseId
        WHERE s.workoutId = :workoutId
    """)
    suspend fun getExercisesForWorkoutOneShot(workoutId: Long): List<ExerciseEntity>

    @Query("SELECT * FROM workout_sets WHERE workoutId = :workoutId")
    suspend fun getSetsForWorkoutOneShot(workoutId: Long): List<WorkoutSetEntity>

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
    @Query("SELECT * FROM completed_workouts WHERE date >= :startTime")
    fun getCompletedWorkoutsRecent(startTime: Long): Flow<List<CompletedWorkoutWithExercise>>

    @Transaction
    @Query("SELECT * FROM completed_workouts WHERE exerciseId = :exerciseId")
    fun getCompletedWorkoutsForExercise(exerciseId: Long): Flow<List<CompletedWorkoutWithExercise>>

    @Query("""
        SELECT e.muscleGroup, SUM(c.reps * c.weight) as totalVolume
        FROM completed_workouts c
        JOIN exercises e ON c.exerciseId = e.exerciseId
        WHERE e.muscleGroup IS NOT NULL
        GROUP BY e.muscleGroup
    """)
    fun getLifetimeMuscleVolume(): Flow<List<MuscleVolumeAggregation>>

    // --- PLAN / NUTRITION ---
    @Query("SELECT * FROM workout_plans ORDER BY startDate DESC LIMIT 1")
    suspend fun getLatestPlan(): WorkoutPlanEntity?

    @Query("SELECT * FROM workout_plans WHERE planId = :planId")
    suspend fun getPlanById(planId: Long): WorkoutPlanEntity?

    // --- WRITES ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: WorkoutPlanEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyWorkout(workout: DailyWorkoutEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: WorkoutEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: ExerciseEntity): Long

    @Update
    suspend fun updateExercise(exercise: ExerciseEntity): Int

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

    @Query("UPDATE workout_plans SET isActive = 0")
    suspend fun deactivateAllPlans(): Int

    @Delete
    suspend fun deleteSets(sets: List<WorkoutSetEntity>): Int

    @Query("UPDATE workout_plans SET isActive = 1 WHERE planId = :planId")
    suspend fun activatePlan(planId: Long): Int

    @Query("SELECT * FROM workout_plans WHERE isActive = 1 LIMIT 1")
    suspend fun getActivePlan(): WorkoutPlanEntity?

    @Query("DELETE FROM exercises")
    suspend fun deleteAllExercises(): Int

    @Query("DELETE FROM daily_workouts WHERE workoutId = :workoutId")
    suspend fun deleteWorkout(workoutId: Long): Int

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

    // --- DISCOVERY / SUBSCRIPTIONS ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(subscription: UserSubscriptionEntity): Long

    @Query("DELETE FROM user_subscriptions WHERE tagName = :tagName")
    suspend fun deleteSubscription(tagName: String): Int

    @Query("SELECT * FROM user_subscriptions")
    fun getAllSubscriptions(): Flow<List<UserSubscriptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContentSource(content: ContentSourceEntity): Long

    @Query("""
        SELECT * FROM content_sources 
        WHERE sportTag IN (SELECT tagName FROM user_subscriptions)
        OR athleteTag IN (SELECT tagName FROM user_subscriptions)
        ORDER BY dateFetched DESC
    """)
    fun getSubscribedContent(): Flow<List<ContentSourceEntity>>

    // FIX: Added the missing @Query annotation here
    @Query("SELECT * FROM content_sources WHERE sourceId = :id")
    fun getContentSourceById(id: Long): Flow<ContentSourceEntity?>

    // --- CONTINUOUS PLANNING (ITERATIVE MESOCYCLE) ---
    @Query("""
        SELECT s.* FROM workout_sets s
        INNER JOIN daily_workouts w ON s.workoutId = w.workoutId
        WHERE s.exerciseId = :exerciseId 
        AND w.scheduledDate > :currentDate
        AND w.isCompleted = 0
        ORDER BY w.scheduledDate ASC
        LIMIT 10
    """)
    suspend fun getFutureSetsForExercise(exerciseId: Long, currentDate: Long): List<WorkoutSetEntity>

    @Query("SELECT COUNT(*) FROM daily_workouts WHERE planId = :planId AND isCompleted = 0")
    suspend fun getUncompletedWorkoutsCount(planId: Long): Int
}