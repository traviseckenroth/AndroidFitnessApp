package com.example.myapplication.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.data.local.WorkoutSetEntity
// FIXED: Changed package from ui.Camera to ui.camera based on conventions
import com.example.myapplication.ui.camera.CameraFormCheckScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    workoutId: Long,
    viewModel: ActiveSessionViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onWorkoutComplete: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val workoutSummary by viewModel.workoutSummary.collectAsState()

    LaunchedEffect(workoutSummary) {
        workoutSummary?.let {
            onWorkoutComplete(it.completedId)
            viewModel.clearSummary()
        }
    }

    LaunchedEffect(workoutId) {
        viewModel.loadWorkout(workoutId)
    }

    // --- BIO-SYNC RECOVERY DIALOG ---
    if (uiState.showRecoveryDialog) {
        AlertDialog(
            onDismissRequest = { /* Force Choice */ },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFA000)) },
            title = { Text("Bio-Sync Alert") },
            text = { Text("Fatigue detected (Sleep < 6h). Reduce volume by 30% to maintain recovery?") },
            confirmButton = {
                Button(
                    onClick = { viewModel.applyRecoveryAdjustment() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Accept") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRecoveryDialog() }) { Text("Override") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        // FIXED: Safe call on workout?.name
                        Text(uiState.workout?.name ?: "Workout")
                        if (uiState.isTimerRunning) {
                            Text("Timer Running...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(onClick = { viewModel.saveWorkout(workoutId) }) { Text("Finish") }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(uiState.exercises) { exerciseState ->
                    ExerciseCard(exerciseState, viewModel)
                }
            }
        }
    }
}

@Composable
fun ExerciseCard(
    exerciseState: ExerciseState,
    viewModel: ActiveSessionViewModel
) {
    var showCamera by remember { mutableStateOf(false) }

    if (showCamera) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showCamera = false }) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                CameraFormCheckScreen(
                    exerciseName = exerciseState.exercise.name,
                    onClose = { showCamera = false },
                    targetWeight = 0.0, // FIXED: Passed Double
                    targetReps = 0,     // Passed Int
                    fetchAiCue = { issue -> viewModel.generateCoachingCue(exerciseState.exercise.name, issue) }
                )
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp)) {
            // Header
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(exerciseState.exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { showCamera = true }) {
                    Icon(Icons.Default.Videocam, contentDescription = "Form Check")
                }
            }

            if (exerciseState.exercise.notes != null) {
                Text("Note: ${exerciseState.exercise.notes}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }

            Spacer(Modifier.height(8.dp))

            // Headers
            Row(Modifier.fillMaxWidth()) {
                Text("Set", Modifier.width(40.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("Goal", Modifier.weight(1f), fontSize = 12.sp, color = Color.Gray)
                Text("Lbs", Modifier.width(60.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
                Text("Reps", Modifier.width(60.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
                Text("RPE", Modifier.width(60.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
                Box(Modifier.width(24.dp))
            }

            // Sets
            exerciseState.sets.forEachIndexed { index, set ->
                SetRow(
                    index + 1,
                    set,
                    // FIXED: Safe null check for equipment string
                    exerciseState.exercise.equipment?.contains("Barbell", ignoreCase = true) == true,
                    viewModel
                )
            }
        }
    }
}

@Composable
fun SetRow(
    setNumber: Int,
    set: WorkoutSetEntity,
    isBarbell: Boolean,
    viewModel: ActiveSessionViewModel
) {
    val focusManager = LocalFocusManager.current
    var weightText by remember(set.actualLbs) { mutableStateOf(set.actualLbs?.toInt()?.toString() ?: "") }
    var repsText by remember(set.actualReps) { mutableStateOf(set.actualReps?.toString() ?: "") }
    var rpeText by remember(set.actualRpe) { mutableStateOf(set.actualRpe?.toInt()?.toString() ?: "") }
    var showPlateDialog by remember { mutableStateOf(false) }

    if (showPlateDialog) {
        AlertDialog(
            onDismissRequest = { showPlateDialog = false },
            title = { Text("Plate Calculator") },
            text = { Text("Load ${set.suggestedLbs} lbs") },
            confirmButton = { TextButton(onClick = { showPlateDialog = false }) { Text("Close") } }
        )
    }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$setNumber", Modifier.width(40.dp).padding(start = 8.dp), fontWeight = FontWeight.Bold)
        Text("${set.suggestedLbs.toInt()}x${set.suggestedReps}", Modifier.weight(1f), color = Color.Gray, fontSize = 12.sp)

        // Weight
        OutlinedTextField(
            value = weightText,
            onValueChange = {
                weightText = it
                if (it.isNotEmpty()) viewModel.updateSet(set.copy(actualLbs = it.toDoubleOrNull()))
            },
            modifier = Modifier.width(60.dp).height(50.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
            placeholder = { Text("${set.suggestedLbs.toInt()}") }
        )

        if (isBarbell) {
            // Optional Plate Calc Button
        }

        Spacer(Modifier.width(4.dp))

        // Reps
        OutlinedTextField(
            value = repsText,
            onValueChange = {
                repsText = it
                if (it.isNotEmpty()) viewModel.updateSet(set.copy(actualReps = it.toIntOrNull()))
            },
            modifier = Modifier.width(60.dp).height(50.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
            placeholder = { Text("${set.suggestedReps}") }
        )

        Spacer(Modifier.width(4.dp))

        // RPE
        OutlinedTextField(
            value = rpeText,
            onValueChange = {
                rpeText = it
                if (it.isNotEmpty()) viewModel.updateSet(set.copy(actualRpe = it.toIntOrNull()))
            },
            modifier = Modifier.width(60.dp).height(50.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
            placeholder = { Text("8") }
        )

        Spacer(Modifier.width(8.dp))

        Checkbox(
            checked = set.isCompleted,
            onCheckedChange = { isChecked ->
                viewModel.updateSetCompletion(set, isChecked)
            }
        )
    }
}