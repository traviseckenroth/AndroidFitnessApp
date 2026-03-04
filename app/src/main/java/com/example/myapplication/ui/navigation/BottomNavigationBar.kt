// app/src/main/java/com/example/myapplication/ui/navigation/BottomNavigationBar.kt
package com.example.myapplication.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState

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
