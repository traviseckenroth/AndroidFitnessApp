package com.example.myapplication.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.myapplication.ui.auth.LoginScreen
import com.example.myapplication.ui.auth.SignUpScreen
import com.example.myapplication.ui.exercise.ExerciseListScreen
import com.example.myapplication.ui.exercise_history.ExerciseHistoryScreen
import com.example.myapplication.ui.home.HomeScreen
import com.example.myapplication.ui.insights.InsightsScreen
import com.example.myapplication.ui.settings.GymSettingsScreen
import com.example.myapplication.ui.nutrition.NutritionScreen
import com.example.myapplication.ui.plan.GeneratePlanScreen
import com.example.myapplication.ui.plan.ManualPlanScreen
import com.example.myapplication.ui.plan.PlanViewModel
import com.example.myapplication.ui.profile.ProfileScreen
import com.example.myapplication.ui.settings.SettingsScreen
import com.example.myapplication.ui.summary.WorkoutSummaryScreen
import com.example.myapplication.ui.warmup.WarmUpScreen
import com.example.myapplication.ui.workout.ActiveSessionViewModel
import com.example.myapplication.ui.workout.ActiveWorkoutScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    planViewModel: PlanViewModel = hiltViewModel()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Login.route,
        modifier = modifier
    ) {
        // --- AUTH ROUTES ---
        composable("login") {
            LoginScreen(
                onLoginSuccess = { navController.navigate(Screen.Home.route) { popUpTo("login") { inclusive = true } } },
                onNavigateToSignUp = { navController.navigate("signup") }
            )
        }
        composable("signup") {
            SignUpScreen(
                onSignUpSuccess = { navController.navigate("login") { popUpTo("signup") { inclusive = true } } },
                onBackToLogin = { navController.popBackStack() }
            )
        }

        // --- MAIN APP ROUTES ---
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLogoutSuccess = {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToGymSettings = { navController.navigate("gym_settings") }
            )
        }

        composable("gym_settings") {
            GymSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.GeneratePlan.route) {
            GeneratePlanScreen(
                viewModel = planViewModel,
                onManualCreateClick = { navController.navigate(Screen.ManualPlan.route) },
                onPlanGenerated = { navController.navigate(Screen.Home.route) { popUpTo(Screen.Home.route) { inclusive = true } } }
            )
        }

        composable(Screen.ManualPlan.route) {
            ManualPlanScreen(
                onNavigateToActiveWorkout = { workoutId ->
                    navController.navigate(Screen.ActiveWorkout.createRoute(workoutId)) { popUpTo(Screen.Home.route) }
                }
            )
        }

        composable(Screen.Nutrition.route) { NutritionScreen() }
        composable(Screen.Insights.route) { InsightsScreen() }

        composable("warm_up") { WarmUpScreen(onBack = { navController.popBackStack() }) }

        composable("exercise_list") {
            ExerciseListScreen(
                onBack = { navController.popBackStack() },
                onNavigateToExerciseHistory = { exerciseId -> navController.navigate("exercise_history/$exerciseId") }
            )
        }

        composable(
            route = "exercise_history/{exerciseId}",
            arguments = listOf(navArgument("exerciseId") { type = NavType.LongType })
        ) { ExerciseHistoryScreen(navController = navController) }

        // --- WORKOUT ROUTES ---
        composable(
            route = Screen.ActiveWorkout.route,
            arguments = listOf(navArgument("workoutId") { type = NavType.LongType })
        ) { backStackEntry ->
            val workoutId = backStackEntry.arguments?.getLong("workoutId") ?: return@composable
            val viewModel: ActiveSessionViewModel = hiltViewModel()

            ActiveWorkoutScreen(
                workoutId = workoutId,
                viewModel = viewModel,
                onBack = {
                    navController.popBackStack()
                },
                onWorkoutComplete = { completedId ->
                    navController.navigate(Screen.WorkoutSummary.createRoute(completedId)) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                }
            )
        }

        composable(
            route = Screen.WorkoutSummary.route,
            arguments = listOf(navArgument("workoutId") { type = NavType.LongType })
        ) { backStackEntry ->
            val workoutId = backStackEntry.arguments?.getLong("workoutId") ?: return@composable

            WorkoutSummaryScreen(
                workoutId = workoutId,
                onNavigateHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }
    }
}