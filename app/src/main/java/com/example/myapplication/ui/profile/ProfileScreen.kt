// app/src/main/java/com/example/myapplication/ui/profile/ProfileScreen.kt
package com.example.myapplication.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sync
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Form State (Local)
    var height by remember(uiState.height) { mutableStateOf(uiState.height) }
    var weight by remember(uiState.weight) { mutableStateOf(uiState.weight) }
    var age by remember(uiState.age) { mutableStateOf(uiState.age) }
    var bodyFat by remember(uiState.bodyFat) { mutableStateOf(uiState.bodyFat) }
    var gender by remember(uiState.gender) { mutableStateOf(if(uiState.gender.isBlank()) "Male" else uiState.gender) }
    var activityLevel by remember(uiState.activityLevel) { mutableStateOf(if(uiState.activityLevel.isBlank()) "Sedentary" else uiState.activityLevel) }
    var dietType by remember(uiState.dietType) { mutableStateOf(if(uiState.dietType.isBlank()) "Standard" else uiState.dietType) }
    var goalPace by remember(uiState.goalPace) { mutableStateOf(if(uiState.goalPace.isBlank()) "Maintain" else uiState.goalPace) }

    // Auto-save effect
    LaunchedEffect(height, weight, age, gender, activityLevel, bodyFat, dietType, goalPace) {
        val h = height.toIntOrNull() ?: 170
        val w = weight.toDoubleOrNull() ?: 70.0
        val a = age.toIntOrNull() ?: 25
        val bf = bodyFat.toDoubleOrNull()
        viewModel.saveProfile(h, w, a, gender, activityLevel, bf, dietType, goalPace)
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Your Profile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }

            // --- INTEGRATIONS SECTION ---
            item {
                Text(
                    text = "Integrations",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Health Connect
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Health Connect", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Reads weight & body fat automatically.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                            if (uiState.isHealthConnectSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                IconButton(onClick = { viewModel.syncHealthConnect() }) {
                                    Icon(Icons.Default.Sync, contentDescription = "Sync Health Connect")
                                }
                            }
                        }

                    }
                }
            }

            // --- BIOMETRICS HEADER ---
            item {
                Text(
                    text = "Biometrics",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                )
            }

            // --- COMPACT INPUT ROWS ---

            // Row 1: Height & Weight
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = height,
                        onValueChange = { height = it },
                        label = { Text("Height (cm)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = { Text("Weight (kg)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }

            // Row 2: Age & Body Fat
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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

            // Row 3: Gender & Activity
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileDropdown("Gender", listOf("Male", "Female", "Other"), gender, Modifier.weight(1f)) { gender = it }
                    ProfileDropdown("Activity", listOf("Sedentary", "Light", "Moderate", "High"), activityLevel, Modifier.weight(1f)) { activityLevel = it }
                }
            }

            // Row 4: Diet & Pace
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileDropdown("Diet", listOf("Standard", "Keto", "Paleo", "Vegan", "Veg"), dietType, Modifier.weight(1f)) { dietType = it }
                    ProfileDropdown("Goal", listOf("Lose Fast", "Lose Slow", "Maintain", "Gain Slow", "Gain Fast"), goalPace, Modifier.weight(1f)) { goalPace = it }
                }
            }
        }
    }
}

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
                    Text(selected, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
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