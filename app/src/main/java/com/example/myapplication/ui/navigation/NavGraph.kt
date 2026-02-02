package com.example.myapplication.ui.navigation

import androidx.compose.material3.Text
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
import com.example.myapplication.ui.plan.GeneratePlanScreen
import com.example.myapplication.ui.plan.ManualPlanScreen
import com.example.myapplication.ui.plan.PlanViewModel
import com.example.myapplication.ui.profile.ProfileScreen
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
        startDestination = "home",
        modifier = modifier
    ) {
        composable("home") {
            HomeScreen(
                onNavigateToWorkout = { workoutId: Long ->
                    navController.navigate("active_workout/$workoutId")
                },
                onNavigateToExerciseList = { navController.navigate("exercise_list") }
            )
        }

        composable("plan") {
            GeneratePlanScreen(
                viewModel = planViewModel,
                onManualCreateClick = { navController.navigate("manual_creator") },
                onPlanGenerated = {
                    navController.popBackStack()
                }
            )
        }

        composable("insights") {
            InsightsScreen()
        }

        composable("profile") {
            ProfileScreen()
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
        ) { backStackEntry ->
            ExerciseHistoryScreen(
                navController = navController
            )
        }
    }
}