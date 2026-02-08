package com.example.myapplication.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogoutSuccess: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    // 1. Create the Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        // When user returns from system dialog, check status again
        viewModel.checkPermissions()
    }

    // 2. Check permissions when screen opens
    LaunchedEffect(Unit) {
        viewModel.checkPermissions()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // --- SECTION: INTEGRATIONS ---
            Text(
                "Integrations",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (viewModel.isHealthConnected) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Red)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Health Connect", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (viewModel.isHealthConnected) "Connected" else "Sync workouts to Google Fit",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (viewModel.isHealthConnected) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50)
                        )
                    } else {
                        Button(onClick = {
                            // Launch System Permission Screen
                            permissionLauncher.launch(viewModel.healthConnectManager.permissions)
                        }) {
                            Text("Connect")
                        }
                    }
                }
            }

            HorizontalDivider()

            // --- SECTION: ACCOUNT ---
            Text(
                "Account",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Button(
                onClick = { viewModel.logout(onLogoutSuccess) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign Out")
            }
        }
    }
}
@Composable
fun SettingsScreen(
    onNavigateToGymSettings: () -> Unit,
    onBack: () -> Unit,
    // ...
) {
    // ...
    item {
        SettingsTile(
            title = "Gym Settings",
            subtitle = "Customize equipment and gym type",
            icon = Icons.Default.FitnessCenter,
            onClick = onNavigateToGymSettings
        )
    }
}