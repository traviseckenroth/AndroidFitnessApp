// app/src/main/java/com/example/myapplication/ui/MainScreen.kt

package com.example.myapplication.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.ui.navigation.GeneratePlan
import com.example.myapplication.ui.navigation.Home
import com.example.myapplication.ui.navigation.Insights
import com.example.myapplication.ui.navigation.NavGraph
import com.example.myapplication.ui.navigation.Nutrition
import com.example.myapplication.ui.navigation.Profile


@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            val showBottomBar = currentDestination?.let { dest ->
                dest.hasRoute(Home::class) ||
                dest.hasRoute(GeneratePlan::class) ||
                dest.hasRoute(Nutrition::class) ||
                dest.hasRoute(Insights::class) ||
                dest.hasRoute(Profile::class)
            } ?: false

            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        selected = currentDestination?.hasRoute(Home::class) == true,
                        onClick = {
                            navController.navigate(Home) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Plan") },
                        label = { Text("Plan") },
                        selected = currentDestination?.hasRoute(GeneratePlan::class) == true,
                        onClick = {
                            navController.navigate(GeneratePlan) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Restaurant, contentDescription = "Nutrition") },
                        label = { Text("Nutrition") },
                        selected = currentDestination?.hasRoute(Nutrition::class) == true,
                        onClick = {
                            navController.navigate(Nutrition) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Timeline, contentDescription = "Insights") },
                        label = { Text("Insights") },
                        selected = currentDestination?.hasRoute(Insights::class) == true,
                        onClick = {
                            navController.navigate(Insights) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Person, contentDescription = "Profile") },
                        label = { Text("Profile") },
                        selected = currentDestination?.hasRoute(Profile::class) == true,
                        onClick = {
                            navController.navigate(Profile) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavGraph(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}