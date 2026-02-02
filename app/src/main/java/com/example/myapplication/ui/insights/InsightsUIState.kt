package com.example.myapplication.ui.insights

import com.example.myapplication.data.local.ExerciseEntity

data class InsightsUiState(
    val isLoading: Boolean = true,
    // List of exercises available to analyze (for the dropdown)
    val availableExercises: List<ExerciseEntity> = emptyList(),
    // The currently selected exercise for the graph
    val selectedExercise: ExerciseEntity? = null,
    // Data points for the 1RM Graph: Pair<Timestamp, Estimated1RM>
    val oneRepMaxHistory: List<Pair<Long, Float>> = emptyList(),
    // Data for Muscle Balance: Map<MuscleGroup, TotalVolume>
    val muscleVolumeDistribution: Map<String, Double> = emptyMap(),
    // Total workouts completed (for the "Consistency" stat)
    val totalWorkouts: Int = 0
)