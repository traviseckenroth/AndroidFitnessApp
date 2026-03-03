// app/src/main/java/com/example/myapplication/ui/navigation/NavGraph.kt
package com.example.myapplication.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
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
    fun getNavIndex(destination: NavDestination?): Int {
        if (destination == null) return -1
        return when {
            destination.hasRoute(Home::class) -> 0
            destination.hasRoute(GeneratePlan::class) -> 1
            destination.hasRoute(Nutrition::class) -> 2
            destination.hasRoute(Insights::class) -> 3
            destination.hasRoute(Profile::class) -> 4
            else -> -1
        }
    }

    NavHost(
        navController = navController,
        startDestination = Login,
        modifier = modifier,
        enterTransition = {
            val initialIndex = getNavIndex(initialState.destination)
            val targetIndex = getNavIndex(targetState.destination)
            if (initialIndex != -1 && targetIndex != -1) {
                slideIntoContainer(
                    if (targetIndex > initialIndex) AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(400)
                )
            } else {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) + fadeIn(animationSpec = tween(400))
            }
        },
        exitTransition = {
            val initialIndex = getNavIndex(initialState.destination)
            val targetIndex = getNavIndex(targetState.destination)
            if (initialIndex != -1 && targetIndex != -1) {
                slideOutOfContainer(
                    if (targetIndex > initialIndex) AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(400)
                )
            } else {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400))
            }
        }
    ) {
        composable<Login> {
            LoginScreen(
                onLoginSuccess = { navController.navigate(Home) { popUpTo(Login) { inclusive = true } } },
                onNavigateToSignUp = { navController.navigate(SignUp) }
            )
        }

        composable<SignUp> {
            SignUpScreen(
                onSignUpSuccess = { navController.navigate(Login) { popUpTo(SignUp) { inclusive = true } } },
                onBackToLogin = { navController.popBackStack() }
            )
        }

        composable<Home> {
            HomeScreen(onNavigate = { route -> navController.navigate(route as Any) })
        }

        composable<Profile> {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        composable<Settings> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLogoutSuccess = { navController.navigate(Login) { popUpTo(0) { inclusive = true } } },
                onNavigateToGymSettings = { navController.navigate(GymSettings) },
                onNavigateToAbout = { navController.navigate(About) }
            )
        }

        composable<GymSettings> { GymSettingsScreen(onBack = { navController.popBackStack() }) }
        composable<About> { AboutFormaScreen(onBack = { navController.popBackStack() }) }

        composable<GeneratePlan> {
            GeneratePlanScreen(
                viewModel = planViewModel,
                onManualCreateClick = { navController.navigate(ManualPlan) },
                onPlanGenerated = { navController.navigate(Home) { popUpTo(Home) { inclusive = true } } }
            )
        }

        composable<ManualPlan> {
            ManualPlanScreen(
                onNavigateToActiveWorkout = { workoutId ->
                    navController.navigate(ActiveWorkout(workoutId)) { popUpTo(Home) }
                }
            )
        }

        composable<Insights> {
            InsightsScreen(onNavigate = { route -> navController.navigate(route as Any) })
        }

        composable<Nutrition> { NutritionScreen() }
        composable<WarmUp> { WarmUpScreen(onBack = { navController.popBackStack() }) }

        // --- FIXED: Passing navController and isPickerMode correctly ---
        composable<ExerciseList> { backStackEntry ->
            val args = backStackEntry.toRoute<ExerciseList>()
            ExerciseListScreen(
                isPickerMode = args.isPickerMode,
                navController = navController,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<StretchingSession> { backStackEntry ->
            val args = backStackEntry.toRoute<StretchingSession>()
            StretchingSessionScreen(
                workoutId = args.workoutId,
                onBack = { navController.popBackStack() },
                onComplete = { navController.popBackStack() }
            )
        }

        composable<ContentDiscovery> { backStackEntry ->
            val args = backStackEntry.toRoute<ContentDiscovery>()
            ContentDiscoveryScreen(contentId = args.contentId, onBack = { navController.popBackStack() })
        }

        composable<ExerciseHistory> {
            ExerciseHistoryScreen(
                onBack = { navController.popBackStack() } // FIXED: Passes the required lambda instead of the controller
            )
        }

        composable<ActiveWorkout> { backStackEntry ->
            val args = backStackEntry.toRoute<ActiveWorkout>()
            ActiveWorkoutScreen(
                workoutId = args.workoutId,
                onBack = { navController.popBackStack() }, // FIXED: Parameter was previously missing or named incorrectly
                navController = navController,
                onWorkoutComplete = { id ->
                    navController.navigate(WorkoutSummary(id)) { popUpTo(Home) { inclusive = false } }
                },
                onNavigateToLiveCoach = { navController.navigate(LiveCoach) }
            )
        }

        composable<LiveCoach> {
            val parentEntry = remember(it) {
                try { navController.getBackStackEntry<ActiveWorkout>() } catch (e: Exception) { null }
            }
            if (parentEntry != null) {
                val viewModel: ActiveSessionViewModel = hiltViewModel(parentEntry)
                LiveAutoCoachScreen(viewModel = viewModel, onNavigateBack = { navController.popBackStack() })
            }
        }

        composable<WorkoutSummary> { backStackEntry ->
            val args = backStackEntry.toRoute<WorkoutSummary>()
            WorkoutSummaryScreen(
                workoutId = args.workoutId,
                onNavigateHome = { navController.navigate(Home) { popUpTo(Home) { inclusive = true } } }
            )
        }
    }
}