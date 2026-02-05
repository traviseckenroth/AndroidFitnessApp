// app/src/main/java/com/example/myapplication/ui/MainScreen.kt

package com.example.myapplication.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant // Import the Restaurant icon
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.ui.navigation.NavGraph
import com.example.myapplication.ui.theme.PrimaryIndigo
import com.example.myapplication.ui.theme.StudioSurface

@Composable
fun MainScreen() {
    val navController = rememberNavController()

    // List of Top-Level Destinations
    val items = listOf(
        Screen.Home,
        Screen.Plan,
        Screen.Nutrition, // Add Nutrition here
        Screen.Insights,
        Screen.Profile
    )

    Scaffold(
        bottomBar = {
            // Updated to "Studio" Aesthetic: White Surface with subtle top border
            NavigationBar(
                containerColor = StudioSurface,
                tonalElevation = 8.dp, // Slight elevation for separation
                contentColor = PrimaryIndigo
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { screen ->
                    val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(screen.label) },
                        selected = isSelected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        // Custom "Studio" Colors for the Navigation Items
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryIndigo,
                            selectedTextColor = PrimaryIndigo,
                            indicatorColor = PrimaryIndigo.copy(alpha = 0.1f), // Soft indigo bubble
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        )
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

// Simple Sealed Class for Navigation Items
sealed class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Plan : Screen("plan", "Plan", Icons.Default.CalendarToday)
    object Nutrition : Screen("nutrition", "Diet", Icons.Default.Restaurant) // Add Nutrition Object
    object Insights : Screen("insights", "Insights", Icons.Default.Analytics)
    object Profile : Screen("profile", "Profile", Icons.Default.Person)
}