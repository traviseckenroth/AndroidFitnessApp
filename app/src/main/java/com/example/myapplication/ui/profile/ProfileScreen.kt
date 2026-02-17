package com.example.myapplication.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    var height by remember(uiState.height) { mutableStateOf(uiState.height) }
    var weight by remember(uiState.weight) { mutableStateOf(uiState.weight) }
    var age by remember(uiState.age) { mutableStateOf(uiState.age) }
    var gender by remember(uiState.gender) { mutableStateOf(uiState.gender) }
    var activityLevel by remember(uiState.activityLevel) { mutableStateOf(uiState.activityLevel) }
    var bodyFat by remember(uiState.bodyFat) { mutableStateOf(uiState.bodyFat) }
    var dietType by remember(uiState.dietType) { mutableStateOf(uiState.dietType) }
    var goalPace by remember(uiState.goalPace) { mutableStateOf(uiState.goalPace) }

    LaunchedEffect(Unit) {
        viewModel.syncHealthConnect()
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) {
        // The callback fires when the user returns from the permission screen.
        // We verify the actual status from the manager instead of relying on the 'granted' list.
        viewModel.syncHealthConnect()
    }

    LaunchedEffect(height, weight, age, gender, activityLevel, bodyFat, dietType, goalPace) {
        delay(1000)
        viewModel.saveProfile(
            h = height.toIntOrNull() ?: 0,
            w = weight.toDoubleOrNull() ?: 0.0,
            a = age.toIntOrNull() ?: 0,
            g = gender,
            act = activityLevel,
            bf = bodyFat.toDoubleOrNull(),
            d = dietType,
            p = goalPace
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Profile",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    // Settings Icon to navigate to the Settings Screen
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Profile",
                    modifier = Modifier.size(100.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = uiState.userName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.isHealthConnectLinked)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Health Connect",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Bed,
                                        "Sleep",
                                        Modifier.size(16.dp),
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(" Sleep  ", style = MaterialTheme.typography.bodySmall)

                                    Icon(
                                        Icons.Default.Favorite,
                                        "Heart Rate",
                                        Modifier.size(16.dp),
                                        Color.Red.copy(alpha = 0.7f)
                                    )
                                    Text(" Heart  ", style = MaterialTheme.typography.bodySmall)

                                    Icon(
                                        Icons.Default.FitnessCenter,
                                        "Workouts",
                                        Modifier.size(16.dp),
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(" Workouts", style = MaterialTheme.typography.bodySmall)
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                if (uiState.isHealthConnectLinked) {
                                    Text(
                                        text = "Status: Active & Synced",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF2E7D32) // Green Text
                                    )
                                    if (uiState.lastSyncTime != null) {
                                        Text(
                                            text = "Last synced: ${uiState.lastSyncTime}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "Tap sync to enable bio-tracking",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            if (uiState.isHealthConnectSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                IconButton(onClick = {
                                    viewModel.onSyncClicked { permissions ->
                                        permissionsLauncher.launch(permissions)
                                    }
                                }) {
                                    if (uiState.isHealthConnectLinked) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Connected",
                                            tint = Color(0xFF4CAF50), // Green Checkmark
                                            modifier = Modifier.size(32.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Sync,
                                            contentDescription = "Connect",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Biometrics",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = height,
                        onValueChange = { height = it },
                        label = { Text("Height (in)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = { Text("Weight (lbs)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = age,
                        onValueChange = { age = it },
                        label = { Text("Age") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = bodyFat,
                        onValueChange = { bodyFat = it },
                        label = { Text("Body Fat %") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }

            item {
                ProfileDropdown(
                    label = "Gender",
                    options = listOf("Male", "Female", "Other"),
                    selected = gender,
                    onSelected = { gender = it }
                )
            }

            item {
                ProfileDropdown(
                    label = "Activity Level",
                    options = listOf("Sedentary", "Lightly Active", "Moderately Active", "Very Active"),
                    selected = activityLevel,
                    onSelected = { activityLevel = it }
                )
            }

            item {
                ProfileDropdown(
                    label = "Goal Pace",
                    options = listOf("Lose Fat Fast", "Slow Cut", "Maintain", "Slow Bulk", "Gain Muscle Fast"),
                    selected = goalPace,
                    onSelected = { goalPace = it }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDropdown(
    label: String,
    options: List<String>,
    selected: String,
    modifier: Modifier = Modifier,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = selected.ifEmpty { "Select" }, style = MaterialTheme.typography.bodyMedium)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
