package com.example.myapplication.ui.insights

import com.example.myapplication.data.local.ExerciseEntity

data class RecentWorkoutSummary(
    val workoutId: Long? = null,
    val date: Long,
    val topExercises: List<String>,
    val totalVolume: Double,
    val totalExercises: Int
)

data class InsightsUiState(
    val isLoading: Boolean = true,
    // List of exercises available to analyze
    val availableExercises: List<ExerciseEntity> = emptyList(),
    // Top performed exercises (for quick selection)
    val topExercises: List<ExerciseEntity> = emptyList(),
    // The currently selected exercise for the graph
    val selectedExercise: ExerciseEntity? = null,
    // Data points for the 1RM Graph
    val oneRepMaxHistory: List<Pair<Long, Float>> = emptyList(),
    // Data for Muscle Balance (Filtered by date range)
    val muscleVolumeDistribution: Map<String, Double> = emptyMap(),
    // Lifetime stats aggregated in SQL
    val lifetimeMuscleVolume: Map<String, Double> = emptyMap(),
    val weeklyTonnage: List<Pair<String, Double>> = emptyList(),
    // Recent History grouped by session
    val recentWorkouts: List<RecentWorkoutSummary> = emptyList(),
    
    // Knowledge Hub Optimizations
    val knowledgeBriefing: String = "",
    val selectedKnowledgeCategory: String = "All", // "All", "Articles", "Videos"
    val isBriefingLoading: Boolean = false
)
