package com.example.myapplication.data.repository

import com.example.myapplication.data.local.CompletedWorkoutWithExercise
import com.example.myapplication.data.local.DailyWorkoutEntity
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.local.WorkoutDao
import com.example.myapplication.data.local.WorkoutSetEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class WorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao
) {
    // Fetch a workout for a specific date (Epoch Millis)
    fun getWorkoutForDate(date: Long): Flow<DailyWorkoutEntity?> {
        return workoutDao.getWorkoutByDate(date)
    }

    // Fetch sets for an active session
    fun getSetsForSession(workoutId: Long): Flow<List<WorkoutSetEntity>> {
        return workoutDao.getSetsForWorkout(workoutId)
    }

    // Update a set (e.g., when completed or reps changed)
    suspend fun updateSet(set: WorkoutSetEntity) {
        workoutDao.updateSet(set)
    }

    fun getAllExercises(): Flow<List<ExerciseEntity>> {
        return workoutDao.getAllExercises()
    }

    fun getCompletedWorkoutsForExercise(exerciseId: Long): Flow<List<CompletedWorkoutWithExercise>> {
        return workoutDao.getCompletedWorkoutsForExercise(exerciseId)
    }
}