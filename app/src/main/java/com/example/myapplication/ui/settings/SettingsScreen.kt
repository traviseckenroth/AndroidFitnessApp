package com.example.myapplication.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    onNavigateToGymSettings: () -> Unit,
    onNavigateToAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        viewModel.checkPermissions()
    }

    val userVoiceSid by viewModel.userVoiceSid.collectAsState(initial = 0)
    val isHealthConnected by viewModel.isHealthConnected.collectAsState()

    val kokoroVoices = mapOf(
        "Bella (Warm Female)" to 2,
        "Heart (Premium Female)" to 3,
        "Nicole (Pro Female)" to 6,
        "Sarah (Mature Female)" to 9,
        "Adam (Male)" to 11,
        "Michael (Deep Male)" to 16,
        "Onyx (Gritty Male)" to 17
    )

    // Reverse map to find name from ID
    val currentVoiceName = kokoroVoices.entries.find { it.value == userVoiceSid }?.key ?: "Bella (Warm Female)"

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

            SettingsTile(
                title = "Gym Settings",
                subtitle = "Customize equipment and gym type",
                icon = Icons.Default.FitnessCenter,
                onClick = onNavigateToGymSettings
            )

            SettingsDropdownTile(
                title = "Coach Voice",
                subtitle = currentVoiceName,
                icon = Icons.Default.RecordVoiceOver,
                options = kokoroVoices,
                onOptionSelected = { sid -> viewModel.setCoachVoice(sid) }
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
                    containerColor = if (isHealthConnected) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
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
                                if (isHealthConnected) "Connected" else "Sync workouts to Google Fit",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (isHealthConnected) {
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
            val isDynamicAutoregEnabled by viewModel.isDynamicAutoregEnabled.collectAsState()

            ListItem(
                headlineContent = { Text("Dynamic Autoregulation") },
                supportingContent = { Text("Automatically adjust the weight of remaining sets if you miss reps or max out your RPE on earlier sets.") },
                leadingContent = {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = {
                    Switch(
                        checked = isDynamicAutoregEnabled,
                        onCheckedChange = { viewModel.toggleDynamicAutoreg(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                    )
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // --- SECTION: APP INFO ---
            Text(
                "App Info",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            SettingsTile(
                title = "About Forma",
                subtitle = "Explore the features and technology",
                icon = Icons.Default.Info,
                onClick = onNavigateToAbout
            )

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

@Composable
fun SettingsDropdownTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    options: Map<String, Int>,
    onOptionSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select")
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            options.forEach { (name, sid) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onOptionSelected(sid)
                        expanded = false
                    }
                )
            }
        }
    }
}
