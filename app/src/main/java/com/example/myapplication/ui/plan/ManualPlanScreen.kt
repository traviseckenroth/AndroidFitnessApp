package com.example.myapplication.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check // <--- FIX: Added this import
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.data.local.ExerciseEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualPlanScreen(
    onNavigateToActiveWorkout: (Long) -> Unit,
    viewModel: ManualPlanViewModel = hiltViewModel()
) {
    val selectedExercises by viewModel.selectedExercises.collectAsState()
    val filteredExercises by viewModel.filteredExercises.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showBottomSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Exercise")
            }
        },
        bottomBar = {
            if (selectedExercises.isNotEmpty()) {
                Surface(
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Button(
                        onClick = {
                            viewModel.saveAndStartWorkout { newWorkoutId ->
                                onNavigateToActiveWorkout(newWorkoutId)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Start Workout (${selectedExercises.size} Exercises)")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Build Your Session",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Add exercises to create a custom plan for today.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (selectedExercises.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Tap + to add exercises", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(selectedExercises, key = { it.exerciseId }) { exercise ->
                        SelectedExerciseCard(
                            exercise = exercise,
                            onRemove = { viewModel.removeExercise(exercise) }
                        )
                    }
                }
            }
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearch(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search exercises...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        items(filteredExercises, key = { it.exerciseId }) { exercise ->
                            val isSelected = selectedExercises.contains(exercise)
                            LibraryExerciseItem(
                                exercise = exercise,
                                isSelected = isSelected,
                                onAdd = { viewModel.addExercise(exercise) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SelectedExerciseCard(exercise: ExerciseEntity, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("3 Sets x 10 Reps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun LibraryExerciseItem(exercise: ExerciseEntity, isSelected: Boolean, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = !isSelected) { onAdd() }
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                exercise.name,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            Text(
                exercise.muscleGroup ?: "General",
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.Gray
            )
        }
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        } else {
            Icon(Icons.Default.Add, contentDescription = null, tint = Color.Gray)
        }
    }
}