// app/src/main/java/com/example/myapplication/ui/navigation/BottomNavigationBar.kt
package com.example.myapplication.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavDestination.Companion.hasRoute

@Composable
fun BottomNavigationBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Define the items using the new Serialized Objects
    val items = listOf<Triple<String, ImageVector, Any>>(
        Triple("Home", Icons.Default.Home, Home),
        Triple("Plan", Icons.Default.DateRange, GeneratePlan),
        Triple("Nutrition", Icons.Default.Restaurant, Nutrition),
        Triple("Insights", Icons.Default.Analytics, Insights),
        Triple("Profile", Icons.Default.AccountCircle, Profile)
    )

    // Check if the current destination is one of the top-level routes
    val showBottomBar = items.any { (_, _, route) ->
        currentDestination?.hasRoute(route::class) == true
    }

    if (showBottomBar) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            items.forEach { (title, icon, route) ->
                NavigationBarItem(
                    icon = { Icon(icon, contentDescription = title) },
                    label = { Text(title) },
                    selected = currentDestination?.hasRoute(route::class) == true,
                    onClick = {
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}
