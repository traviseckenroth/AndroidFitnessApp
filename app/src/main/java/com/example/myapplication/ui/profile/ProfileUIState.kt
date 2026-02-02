package com.example.myapplication.ui.profile

import com.example.myapplication.data.local.CompletedWorkoutEntity
import com.example.myapplication.data.local.ExerciseEntity

// 1. Define the UI model here so it's accessible to Screen and ViewModel
data class CompletedWorkoutItem(
    val completedWorkout: CompletedWorkoutEntity,
    val exercise: ExerciseEntity
)

sealed interface ProfileUiState {
    data object Loading : ProfileUiState

    data class Success(
        // 2. Change this from List<CompletedWorkoutWithExercise> to List<CompletedWorkoutItem>
        val completedWorkouts: List<CompletedWorkoutItem>,
        val height: Int,
        val weight: Double,
        val age: Int
    ) : ProfileUiState

    data object Empty : ProfileUiState
}