package com.example.myapplication.ui.insights

import com.example.myapplication.data.local.CompletedWorkoutWithExercise
import com.example.myapplication.data.local.ExerciseEntity

data class InsightsUiState(
    val isLoading: Boolean = true,
    // List of exercises available to analyze
    val availableExercises: List<ExerciseEntity> = emptyList(),
    // The currently selected exercise for the graph
    val selectedExercise: ExerciseEntity? = null,
    // Data points for the 1RM Graph
    val oneRepMaxHistory: List<Pair<Long, Float>> = emptyList(),
    // Data for Muscle Balance
    val muscleVolumeDistribution: Map<String, Double> = emptyMap(),
    // NEW: Recent History (Limited to last 5-10 items)
    val recentWorkouts: List<CompletedWorkoutWithExercise> = emptyList()
)