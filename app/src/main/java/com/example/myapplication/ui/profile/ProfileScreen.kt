package com.example.myapplication.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    // Form State
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var bodyFat by remember { mutableStateOf("") }

    var gender by remember { mutableStateOf("Male") }
    var activityLevel by remember { mutableStateOf("Sedentary") }
    var dietType by remember { mutableStateOf("Standard") }
    var goalPace by remember { mutableStateOf("Maintain") }

    var showCheckmark by remember { mutableStateOf(false) }

    // Update local state when UI State loads
    LaunchedEffect(uiState) {
        if (uiState is ProfileUiState.Success) {
            val state = uiState as ProfileUiState.Success
            height = state.height.toString()
            weight = state.weight.toString()
            age = state.age.toString()
            bodyFat = state.bodyFat?.toString() ?: ""
            gender = state.gender
            activityLevel = state.activityLevel
            dietType = state.dietType
            goalPace = state.goalPace
        }
    }

    when (val state = uiState) {
        is ProfileUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is ProfileUiState.Success -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Your Profile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }

                // 1. Biometrics Row
                item {
                    Text("Biometrics", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = height, onValueChange = { height = it }, label = { Text("Height (cm)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Weight (kg)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(value = age, onValueChange = { age = it }, label = { Text("Age") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = bodyFat, onValueChange = { bodyFat = it }, label = { Text("Body Fat % (Opt)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

                        // Gender Selector
                        Box(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                            ProfileDropdown(
                                label = "Gender",
                                options = listOf("Male", "Female"),
                                selected = gender,
                                onSelected = { gender = it }
                            )
                        }
                    }
                }

                // 2. Lifestyle Section
                item {
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Lifestyle & Nutrition", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

                    ProfileDropdown(
                        label = "Activity Level",
                        options = listOf("Sedentary", "Lightly Active", "Moderately Active", "Very Active"),
                        selected = activityLevel,
                        onSelected = { activityLevel = it }
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ProfileDropdown(
                        label = "Diet Preference",
                        options = listOf("Standard", "Keto", "Paleo", "Vegan", "Vegetarian"),
                        selected = dietType,
                        onSelected = { dietType = it }
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ProfileDropdown(
                        label = "Goal Pace",
                        options = listOf("Lose 1 lb/week", "Lose 0.5 lb/week", "Maintain", "Gain 0.5 lb/week", "Gain 1 lb/week"),
                        selected = goalPace,
                        onSelected = { goalPace = it }
                    )
                }

                // 3. Save Button
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = {
                                viewModel.saveProfile(
                                    height = height.toIntOrNull() ?: 180,
                                    weight = weight.toDoubleOrNull() ?: 75.0,
                                    age = age.toIntOrNull() ?: 25,
                                    gender = gender,
                                    activity = activityLevel,
                                    bodyFat = bodyFat.toDoubleOrNull(),
                                    diet = dietType,
                                    pace = goalPace
                                )
                                showCheckmark = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Profile Settings")
                        }
                    }
                }

                // 4. Status & History
                item { AIStatusCard() }
                item { Text("Workout History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
                items(state.completedWorkouts) { item -> CompletedWorkoutCard(item) }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
        is ProfileUiState.Empty -> { EmptyHistoryState() }
    }

    // Feedback Animation
    if (showCheckmark) {
        LaunchedEffect(showCheckmark) {
            delay(2000)
            showCheckmark = false
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(shadowElevation = 4.dp, shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.padding(24.dp).size(48.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// Helper Composable for Dropdowns
@Composable
fun ProfileDropdown(label: String, options: List<String>, selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(selected, style = MaterialTheme.typography.bodyMedium)
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

// ... AIStatusCard, CompletedWorkoutCard, VerticalStat, EmptyHistoryState remain unchanged ...
// (Include them below as they were in the original file, or use these standard implementations)

@Composable
fun AIStatusCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Hybrid Optimization Active", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text("AI uses your history and new profile stats to dial in nutrition and progressive overload.", style = MaterialTheme.typography.bodySmall)
            }
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
    }
}

@Composable
fun CompletedWorkoutCard(item: CompletedWorkoutItem) {
    val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
    val dateString = sdf.format(Date(item.completedWorkout.date))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(dateString, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                VerticalStat(label = "REPS", value = "${item.completedWorkout.reps}")
                VerticalStat(label = "LBS", value = "${item.completedWorkout.weight}")
                VerticalStat(label = "RPE", value = "${item.completedWorkout.rpe}")
            }
        }
    }
}

@Composable
fun VerticalStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun EmptyHistoryState() {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No History Yet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Complete a workout to see progress and AI insights here.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
    }
}