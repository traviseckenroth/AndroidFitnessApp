package com.example.myapplication.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.myapplication.ui.exercise.ExerciseListScreen
import com.example.myapplication.ui.exercise_history.ExerciseHistoryScreen
import com.example.myapplication.ui.home.HomeScreen
import com.example.myapplication.ui.plan.GeneratePlanScreen
import com.example.myapplication.ui.plan.ManualPlanScreen
import com.example.myapplication.ui.plan.PlanViewModel
import com.example.myapplication.ui.profile.ProfileScreen
import com.example.myapplication.ui.workout.ActiveWorkoutScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    planViewModel: PlanViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier
    ) {
        composable("home") {
            HomeScreen(
                planViewModel = planViewModel,
                // MATCHES THE ROUTE DEFINED BELOW
                onNavigateToWorkout = { workoutId: Long ->
                    navController.navigate("active_workout/$workoutId")
                },
                onNavigateToExerciseHistory = { exerciseId: Long ->
                    navController.navigate("exercise_history/$exerciseId")
                },
                onNavigateToExerciseList = { navController.navigate("exercise_list") }
            )
        }

        composable("plan") {
            GeneratePlanScreen(
                viewModel = planViewModel,
                onManualCreateClick = { navController.navigate("manual_creator") },
                // FIX: Just pop back. The ViewModel holds the data,
                // so Home will update automatically when revealed.
                onPlanGenerated = {
                    navController.popBackStack()
                }
            )
        }

        composable("profile") {
            ProfileScreen()
        }

        composable("manual_creator") {
            ManualPlanScreen(
                onSavePlan = { navController.popBackStack() }
            )
        }

        composable(
            route = "active_workout/{workoutId}", // UPDATED ROUTE NAME
            arguments = listOf(navArgument("workoutId") { type = NavType.LongType })
        ) { backStackEntry ->
            val workoutId = backStackEntry.arguments?.getLong("workoutId") ?: 0L

            // Pass the shared PlanViewModel instance
            ActiveWorkoutScreen(
                workoutId = workoutId,
                planViewModel = planViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "exercise_history/{exerciseId}",
            arguments = listOf(navArgument("exerciseId") { type = NavType.LongType })
        ) { backStackEntry ->
            ExerciseHistoryScreen(
                navController = navController
            )
        }

        composable("exercise_list") {
            ExerciseListScreen(
                navController = navController,
                onNavigateToExerciseHistory = { exerciseId ->
                    navController.navigate("exercise_history/$exerciseId")
                }
            )
        }
    }
}