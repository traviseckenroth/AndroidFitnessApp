package com.example.myapplication.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter // FIXED: Import
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogoutSuccess: () -> Unit,
    onNavigateToGymSettings: () -> Unit, // FIXED: Added missing parameter
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        viewModel.checkPermissions()
    }

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
            // --- SECTION: PREFERENCES ---
            Text(
                "Preferences",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            // FIXED: SettingsTile now works
            SettingsTile(
                title = "Gym Settings",
                subtitle = "Customize equipment and gym type",
                icon = Icons.Default.FitnessCenter,
                onClick = onNavigateToGymSettings
            )

            HorizontalDivider()

            // --- SECTION: INTEGRATIONS ---
            Text(
                "Integrations",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (viewModel.isHealthConnected.value) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Red)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Health Connect", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (viewModel.isHealthConnected.value) "Connected" else "Sync workouts to Google Fit",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (viewModel.isHealthConnected.value) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                    } else {
                        Button(onClick = {
                            permissionLauncher.launch(viewModel.permissions)
                        }) {
                            Text("Connect")
                        }
                    }
                }
            }

            HorizontalDivider()

            Text("Account", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

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

// FIXED: Added missing Composable
@Composable
fun SettingsTile(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}