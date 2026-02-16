package com.example.myapplication.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import java.util.Locale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.local.WorkoutSetEntity
import com.example.myapplication.ui.Camera.CameraFormCheckScreen
import kotlinx.coroutines.delay

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

    // Handle Workout Completion Navigation
    LaunchedEffect(workoutSummary) {
        workoutSummary?.let {
            onWorkoutComplete(it.completedId)
            viewModel.clearSummary()
        }
    }

    LaunchedEffect(workoutId) {
        viewModel.loadWorkout(workoutId)
    }

    // --- NEW: BIO-SYNC RECOVERY DIALOG ---
    if (uiState.showRecoveryDialog) {
        AlertDialog(
            onDismissRequest = { /* Force user to choose */ },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFFA000) // Amber color for warning
                )
            },
            title = { Text("Bio-Sync: High Fatigue Detected") },
            text = {
                Text("Health Connect data shows you slept less than 6 hours last night. Training at full volume may increase injury risk.\n\nRecommended: Reduce reps by 30%.")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.applyRecoveryAdjustment() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Accept Reduction")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRecoveryDialog() }) {
                    Text("Override (Keep Normal)")
                }
            }
        )
    }
    // -------------------------------------

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.workout?.name ?: "Active Workout",
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (uiState.isTimerRunning) {
                            Text(
                                text = "Resting...", // You can format seconds here
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(onClick = { viewModel.saveWorkout(workoutId) }) {
                        Text("Finish")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(uiState.exercises) { exerciseState ->
                    ExerciseCard(
                        exerciseState = exerciseState,
                        viewModel = viewModel
                    )
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
        // Full screen camera overlay
        androidx.compose.ui.window.Dialog(onDismissRequest = { showCamera = false }) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                CameraFormCheckScreen(
                    onClose = { showCamera = false }
                )
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = exerciseState.exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { showCamera = true }) {
                    Icon(Icons.Default.Videocam, contentDescription = "Form Check")
                }
            }

            if (exerciseState.exercise.notes != null) {
                Text(
                    text = "Note: ${exerciseState.exercise.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Column Headers
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Set", modifier = Modifier.width(40.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("Previous", modifier = Modifier.weight(1f), fontSize = 12.sp, color = Color.Gray)
                Text("Lbs", modifier = Modifier.width(60.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
                Text("Reps", modifier = Modifier.width(60.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
                Text("RPE", modifier = Modifier.width(60.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
                // Small Help Icon for RPE
                Box(modifier = Modifier.width(24.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Help, "RPE Guide", tint = MaterialTheme.colorScheme.primary)
                }
            }

            Text("DONE", modifier = Modifier.weight(0.5f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        exerciseState.sets.forEachIndexed { index, set ->
            // Inline "isBarbell" check
            SetRow(
                setNumber = index + 1,
                set = set,
                isBarbell = exerciseState.exercise.equipment.contains("Barbell", ignoreCase = true) == true,
                viewModel = viewModel
            )
        }
    }
}

@Composable
fun SetRow(setNumber: Int, set: WorkoutSetEntity, isBarbell: Boolean, viewModel: ActiveSessionViewModel) {
    val focusManager = LocalFocusManager.current
    var weightText by remember(set.actualLbs) { mutableStateOf(set.actualLbs?.toInt()?.toString() ?: "") }
    var repsText by remember(set.actualReps) { mutableStateOf(set.actualReps?.toString() ?: "") }
    var rpeText by remember(set.actualRpe) { mutableStateOf(set.actualRpe?.toInt()?.toString() ?: "") }
    var showPlateDialog by remember { mutableStateOf(false) }

    if (showPlateDialog) {
        AlertDialog(
            onDismissRequest = { showPlateDialog = false },
            title = { Text("Load ${set.suggestedLbs} lbs") },
            text = {
                // In a real app, calculate plates here
                Text("45s: 1 per side\n25s: 0\n10s: 1\n(Example)")
            },
            confirmButton = {
                TextButton(onClick = { showPlateDialog = false }) { Text("Close") }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (set.isCompleted) Color.Green.copy(alpha = 0.1f) else Color.Transparent)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Set Number
        Text(
            text = "$setNumber",
            modifier = Modifier.width(40.dp).padding(start = 8.dp),
            fontWeight = FontWeight.Bold
        )

        // Previous (Placeholder)
        Text(
            text = "${set.suggestedLbs}x${set.suggestedReps}",
            modifier = Modifier.weight(1f),
            color = Color.Gray,
            fontSize = 12.sp
        )

        // Weight Input
        OutlinedTextField(
            value = weightText,
            onValueChange = {
                weightText = it
                // Update VM immediately or on focus lost
                if(it.isNotEmpty()) viewModel.updateSet(set.copy(actualLbs = it.toDoubleOrNull()))
            },
            modifier = Modifier.width(60.dp).height(50.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
            placeholder = { Text("${set.suggestedLbs.toInt()}") }
        )

        // Plate Calc Icon (if barbell)
        if (isBarbell) {
            IconButton(onClick = { showPlateDialog = true }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Calculate, contentDescription = "Plates")
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Reps Input
        OutlinedTextField(
            value = repsText,
            onValueChange = {
                repsText = it
                if(it.isNotEmpty()) viewModel.updateSet(set.copy(actualReps = it.toIntOrNull()))
            },
            modifier = Modifier.width(60.dp).height(50.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
            placeholder = { Text("${set.suggestedReps}") }
        )

        Spacer(modifier = Modifier.width(4.dp))

        // RPE Input
        OutlinedTextField(
            value = rpeText,
            onValueChange = {
                rpeText = it
                if(it.isNotEmpty()) viewModel.updateSet(set.copy(actualRpe = it.toIntOrNull()))
            },
            modifier = Modifier.width(60.dp).height(50.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
            placeholder = { Text("8") }
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Checkbox
        Checkbox(
            checked = set.isCompleted,
            onCheckedChange = { isChecked ->
                viewModel.updateSetCompletion(set, isChecked)
                if (isChecked) {
                    // Logic to start rest timer could go here
                    // viewModel.startSet(set.setId, set.restPeriod)
                }
            }
        )
    }
}