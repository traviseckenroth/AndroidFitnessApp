// app/src/main/java/com/example/myapplication/ui/profile/ProfileUIState.kt
package com.example.myapplication.ui.profile

import com.example.myapplication.data.local.CompletedWorkoutEntity
import com.example.myapplication.data.local.ExerciseEntity

// Helper class for history items
data class CompletedWorkoutItem(
    val completedWorkout: CompletedWorkoutEntity,
    val exercise: ExerciseEntity
)

// The single, correct State class (Data Class)
data class ProfileUIState(
    val isLoading: Boolean = false,
    val userName: String = "User",
    val height: String = "",
    val weight: String = "",
    val age: String = "",

    // Biometrics & Goals
    val gender: String = "",
    val activityLevel: String = "",
    val bodyFat: String = "",
    val dietType: String = "",
    val goalPace: String = "",

    // Integration States
    val isHealthConnectSyncing: Boolean = false,
    val isHealthConnectLinked: Boolean = false,
    val lastSyncTime: String? = null,

    // AI Usage
    val aiRequestsToday: Int = 0,
    val aiDailyLimit: Int = 50
)