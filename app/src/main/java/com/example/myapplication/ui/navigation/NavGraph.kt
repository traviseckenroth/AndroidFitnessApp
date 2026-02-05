package com.example.myapplication.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.myapplication.ui.exercise.ExerciseListScreen
import com.example.myapplication.ui.exercise_history.ExerciseHistoryScreen
import com.example.myapplication.ui.home.HomeScreen
import com.example.myapplication.ui.insights.InsightsScreen
import com.example.myapplication.ui.settings.SettingsScreen
import com.example.myapplication.ui.plan.GeneratePlanScreen
import com.example.myapplication.ui.plan.ManualPlanScreen
import com.example.myapplication.ui.plan.PlanViewModel
import com.example.myapplication.ui.profile.ProfileScreen
import com.example.myapplication.ui.warmup.WarmUpScreen
import com.example.myapplication.ui.workout.ActiveSessionViewModel
import com.example.myapplication.ui.workout.ActiveWorkoutScreen
import com.example.myapplication.ui.auth.LoginScreen
import com.example.myapplication.ui.auth.SignUpScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    planViewModel: PlanViewModel = hiltViewModel()
) {
    NavHost(
        navController = navController,
        startDestination = "home", // Set this to "home" to skip login
        modifier = modifier
    ) {
        // --- 1. HOME ROUTE ---
        composable("home") {
            HomeScreen(
                onNavigateToWorkout = { workoutId ->
                    navController.navigate("active_workout/$workoutId")
                },
                onNavigateToExerciseList = { navController.navigate("exercise_list") },
                onManualLogClick = { navController.navigate("manual_creator") },
                onWarmUpClick = { navController.navigate("warm_up") },
                onSettingsClick = { navController.navigate("settings") }
            )
        }

        // --- 2. AUTH ROUTES (Disabled/Unused for testing) ---
        /* composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onNavigateToSignUp = { navController.navigate("signup") }
            )
        }

        composable("signup") {
            SignUpScreen(
                onSignUpSuccess = {
                    navController.navigate("login") {
                        popUpTo("signup") { inclusive = true }
                    }
                },
                onBackToLogin = { navController.popBackStack() }
            )
        }
        */

        // --- 3. APP WORKFLOWS ---
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLogoutSuccess = {
                    // Navigate back to home instead of login while testing
                    navController.navigate("home") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable("plan") {
            GeneratePlanScreen(
                viewModel = planViewModel,
                onManualCreateClick = { navController.navigate("manual_creator") },
                onPlanGenerated = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }

        composable("insights") {
            InsightsScreen()
        }

        composable("profile") {
            ProfileScreen()
        }

        composable("warm_up") {
            WarmUpScreen(onBack = { navController.popBackStack() })
        }

        composable("manual_creator") {
            ManualPlanScreen(
                planViewModel = planViewModel,
                onSavePlan = { navController.popBackStack() }
            )
        }

        composable(
            route = "active_workout/{workoutId}",
            arguments = listOf(navArgument("workoutId") { type = NavType.LongType })
        ) { backStackEntry ->
            val workoutId = backStackEntry.arguments?.getLong("workoutId") ?: return@composable
            val viewModel: ActiveSessionViewModel = hiltViewModel()
            ActiveWorkoutScreen(
                workoutId = workoutId,
                viewModel = viewModel,
                onBack = {
                    viewModel.finishWorkout(workoutId)
                    navController.popBackStack()
                }
            )
        }

        composable("exercise_list") {
            ExerciseListScreen(
                onBack = { navController.popBackStack() },
                onNavigateToExerciseHistory = { exerciseId ->
                    navController.navigate("exercise_history/$exerciseId")
                }
            )
        }

        composable(
            route = "exercise_history/{exerciseId}",
            arguments = listOf(navArgument("exerciseId") { type = NavType.LongType })
        ) {
            ExerciseHistoryScreen(navController = navController)
        }
    }
}