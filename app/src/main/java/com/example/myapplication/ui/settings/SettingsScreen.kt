package com.example.myapplication.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.ui.theme.SuccessGreen

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
    val isDynamicAutoregEnabled by viewModel.isDynamicAutoregEnabled.collectAsState()

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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()), // FIX: Added scrollability so the bottom isn't cut off!
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // --- SECTION: PREFERENCES ---
            Text(
                "Preferences",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
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
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isHealthConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Health Connect", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                if (isHealthConnected) "Connected" else "Sync workouts to Google Fit",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (isHealthConnected) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                    } else {
                        Button(onClick = {
                            permissionLauncher.launch(viewModel.permissions)
                        }) {
                            Text("Connect")
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                ListItem(
                    headlineContent = { Text("Dynamic Autoregulation", fontWeight = FontWeight.Bold) },
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
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            HorizontalDivider()

            // --- SECTION: APP INFO ---
            Text(
                "App Info",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            SettingsTile(
                title = "About Forma",
                subtitle = "Explore the features and technology",
                icon = Icons.Default.Info,
                onClick = onNavigateToAbout
            )

            HorizontalDivider()

            // --- SECTION: ACCOUNT (SIGN OUT) ---
            Text(
                "Account",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            // FIX: Upgraded to a highly-visible Button instead of a Card
            Button(
                onClick = { viewModel.logout(onLogoutSuccess) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp), // Force a chunky, clickable height
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Sign Out"
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Sign Out",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(48.dp)) // Extra bottom padding for comfortable scrolling
        }
    }
}

@Composable
fun SettingsTile(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
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
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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