package com.example.myapplication.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String? = null, val icon: ImageVector? = null) {
    // Bottom Navigation Items
    object Login : Screen("login", "Login")
    object SignUp : Screen("signup", "Sign Up")
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Plan : Screen("generate_plan", "Plan", Icons.Default.DateRange)
    object Nutrition : Screen("nutrition", "Nutrition", Icons.Default.Restaurant)
    object Insights : Screen("insights", "Insights", Icons.Default.BarChart)
    object Profile : Screen("profile", "Profile", Icons.Default.Person)

    // Feature Screens (Added these)
    object GeneratePlan : Screen("generate_plan")
    object ManualPlan : Screen("manual_plan")
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)

    // Full Screen Features
    object ActiveWorkout : Screen("active_workout/{workoutId}") {
        fun createRoute(workoutId: Long) = "active_workout/$workoutId"
    }

    object WorkoutSummary : Screen("workout_summary/{workoutId}") {
        fun createRoute(workoutId: Long) = "workout_summary/$workoutId"
    }
}