package com.example.myapplication.ui.plan

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// --- MOCK DATABASE  ---
data class ManualExercise(
    val id: String,
    val name: String,
    val category: String
)

val exerciseDatabase = listOf(
    ManualExercise("1", "Barbell Squat", "Legs"),
    ManualExercise("2", "Leg Press", "Legs"),
    ManualExercise("3", "Romanian Deadlift", "Legs"),
    ManualExercise("4", "Bench Press", "Chest"),
    ManualExercise("5", "Incline Dumbbell Press", "Chest"),
    ManualExercise("6", "Pull Up", "Back"),
    ManualExercise("7", "Barbell Row", "Back"),
    ManualExercise("8", "Overhead Press", "Shoulders")
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ManualPlanScreen(onSavePlan: () -> Unit) {
    // State for selected exercises
    val selectedIds = remember { mutableStateListOf<String>() }

    // Group exercises by category for the UI
    val groupedExercises = remember { exerciseDatabase.groupBy { it.category } }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Manual Plan Creator") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onSavePlan,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Check, "Save") },
                text = { Text("Save Routine (${selectedIds.size})") }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            groupedExercises.forEach { (category, exercises) ->
                // Sticky Header for Categories
                stickyHeader {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                items(exercises) { exercise ->
                    ExerciseSelectionRow(
                        exercise = exercise,
                        isSelected = selectedIds.contains(exercise.id),
                        onToggle = {
                            if (selectedIds.contains(exercise.id)) {
                                selectedIds.remove(exercise.id)
                            } else {
                                selectedIds.add(exercise.id)
                            }
                        }
                    )
                }
            }
            // Spacer for FAB
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun ExerciseSelectionRow(
    exercise: ManualExercise,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = exercise.name, style = MaterialTheme.typography.bodyLarge)
        }
    }
}