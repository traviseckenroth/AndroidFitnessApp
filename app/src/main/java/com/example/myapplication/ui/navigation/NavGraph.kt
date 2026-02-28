package com.example.myapplication.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.myapplication.ui.about.AboutFormaScreen
import com.example.myapplication.ui.workout.ContentDiscoveryScreen
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
import com.example.myapplication.ui.workout.StretchingSessionScreen
import com.example.myapplication.ui.workout.LiveAutoCoachScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    planViewModel: PlanViewModel = hiltViewModel()
) {
    fun getNavIndex(route: String?): Int {
        return when (route) {
            Screen.Home.route -> 0
            Screen.Plan.route -> 1
            Screen.GeneratePlan.route -> 1
            Screen.Nutrition.route -> 2
            Screen.Insights.route -> 3
            Screen.Profile.route -> 4
            else -> -1
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Login.route,
        modifier = modifier,
        enterTransition = {
            val initialIndex = getNavIndex(initialState.destination.route)
            val targetIndex = getNavIndex(targetState.destination.route)

            if (initialIndex != -1 && targetIndex != -1) {
                if (targetIndex > initialIndex) {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(400)
                    )
                } else {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(400)
                    )
                }
            } else {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(400)
                ) + fadeIn(animationSpec = tween(400))
            }
        },
        exitTransition = {
            val initialIndex = getNavIndex(initialState.destination.route)
            val targetIndex = getNavIndex(targetState.destination.route)

            if (initialIndex != -1 && targetIndex != -1) {
                if (targetIndex > initialIndex) {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(400)
                    )
                } else {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(400)
                    )
                }
            } else {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(400)
                ) + fadeOut(animationSpec = tween(400))
            }
        },
        popEnterTransition = {
            val initialIndex = getNavIndex(initialState.destination.route)
            val targetIndex = getNavIndex(targetState.destination.route)

            if (initialIndex != -1 && targetIndex != -1) {
                if (targetIndex > initialIndex) {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(400)
                    )
                } else {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(400)
                    )
                }
            } else {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(400)
                ) + fadeIn(animationSpec = tween(400))
            }
        },
        popExitTransition = {
            val initialIndex = getNavIndex(initialState.destination.route)
            val targetIndex = getNavIndex(targetState.destination.route)

            if (initialIndex != -1 && targetIndex != -1) {
                if (targetIndex > initialIndex) {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(400)
                    )
                } else {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(400)
                    )
                }
            } else {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(400)
                ) + fadeOut(animationSpec = tween(400))
            }
        }
    ) {
        // --- AUTH ROUTES ---
        composable(
            route = "login",
            enterTransition = { fadeIn(animationSpec = tween(500)) },
            exitTransition = { fadeOut(animationSpec = tween(500)) }
        ) {
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
        composable(
            route = Screen.StretchingSession.route,
            arguments = listOf(navArgument("workoutId") { type = NavType.LongType })
        ) { backStackEntry ->
            val workoutId = backStackEntry.arguments?.getLong("workoutId") ?: return@composable

            StretchingSessionScreen(
                workoutId = workoutId,
                onBack = { navController.popBackStack() },
                onComplete = { navController.popBackStack() }
            )
        }
        composable(Screen.Profile.route) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(
            route = Screen.ContentDiscovery.route,
            arguments = listOf(navArgument("contentId") { type = NavType.LongType })
        ) { backStackEntry ->
            val contentId = backStackEntry.arguments?.getLong("contentId") ?: 0L
            ContentDiscoveryScreen(contentId = contentId, onBack = { navController.popBackStack() })
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLogoutSuccess = {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToGymSettings = { navController.navigate("gym_settings") },
                onNavigateToAbout = { navController.navigate(Screen.About.route) }
            )
        }

        composable("gym_settings") {
            GymSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.About.route) {
            AboutFormaScreen(onBack = { navController.popBackStack() })
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
        composable(route = Screen.Insights.route) {
            InsightsScreen(
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        composable(Screen.Nutrition.route) { NutritionScreen() }

        composable("warm_up") { WarmUpScreen(onBack = { navController.popBackStack() }) }

        composable("exercise_list") {
            ExerciseListScreen(
                onNavigateBack = { navController.popBackStack() },
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

            ActiveWorkoutScreen(
                workoutId = workoutId,
                onBack = { // CHANGED from onNavigateBack
                    navController.popBackStack()
                },
                onWorkoutComplete = { id -> // CHANGED from onNavigateToSummary
                    navController.navigate(Screen.WorkoutSummary.createRoute(id)) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                onNavigateToLiveCoach = {
                    navController.navigate(Screen.LiveCoach.route)
                }
            )
        }

        composable(
            route = Screen.LiveCoach.route,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(400)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(400)
                )
            },
            popEnterTransition = { fadeIn(animationSpec = tween(400)) },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(400)
                )
            }
        ) {
            // Safer way to retrieve the shared ViewModel from the previous backstack entry
            val parentEntry = remember(it) {
                try {
                    navController.getBackStackEntry(Screen.ActiveWorkout.route)
                } catch (e: Exception) {
                    null
                }
            }

            if (parentEntry != null) {
                val viewModel: ActiveSessionViewModel = hiltViewModel(parentEntry)
                LiveAutoCoachScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
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