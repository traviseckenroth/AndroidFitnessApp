package com.example.myapplication.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun BottomNavigationBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val items = listOf(
        Screen.Home,
        Screen.Plan,
        Screen.Nutrition,
        Screen.Insights,
        Screen.Profile
    )

    val showBottomBar = items.any { it.route == currentRoute }

    if (showBottomBar) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface, // Uses clean white/slate instead of default purple
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            items.forEach { screen ->
                if (screen.icon != null && screen.title != null) {
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary, // FormaBlue icon
                            selectedTextColor = MaterialTheme.colorScheme.primary, // FormaBlue text
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer, // Soft Blue pill background
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, // Slate grey unselected
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}