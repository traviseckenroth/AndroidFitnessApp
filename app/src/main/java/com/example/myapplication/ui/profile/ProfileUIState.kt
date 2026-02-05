package com.example.myapplication.ui.profile

import com.example.myapplication.data.local.CompletedWorkoutEntity
import com.example.myapplication.data.local.ExerciseEntity

sealed interface ProfileUiState {
    object Loading : ProfileUiState

    data class Success(
        val completedWorkouts: List<CompletedWorkoutItem>,
        val height: Int,
        val weight: Double,
        val age: Int,
        // New Fields
        val gender: String,
        val activityLevel: String,
        val bodyFat: Double?,
        val dietType: String,
        val goalPace: String
    ) : ProfileUiState

    object Empty : ProfileUiState
}

data class CompletedWorkoutItem(
    val completedWorkout: CompletedWorkoutEntity,
    val exercise: ExerciseEntity
)