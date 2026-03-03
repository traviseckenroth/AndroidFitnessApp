// app/src/main/java/com/example/myapplication/ui/navigation/Routes.kt
package com.example.myapplication.ui.navigation

import kotlinx.serialization.Serializable

// --- AUTH ROUTES ---
@Serializable data object Login
@Serializable data object SignUp

// --- MAIN NAV ROUTES ---
@Serializable data object Home
@Serializable data object GeneratePlan
@Serializable data object ManualPlan
@Serializable data object Insights
@Serializable data object Nutrition
@Serializable data object Profile

// --- SECONDARY ROUTES ---
@Serializable data object Settings
@Serializable data object GymSettings
@Serializable data object About
@Serializable data object WarmUp
@Serializable data class ExerciseList(
    val isPickerMode: Boolean = false
)
@Serializable data object LiveCoach

// --- ROUTES WITH ARGUMENTS (Type-Safe!) ---
@Serializable data class StretchingSession(val workoutId: Long)
@Serializable data class ContentDiscovery(val contentId: Long)
@Serializable data class ExerciseHistory(val exerciseId: Long)
@Serializable data class ActiveWorkout(val workoutId: Long)
@Serializable data class WorkoutSummary(val workoutId: Long)