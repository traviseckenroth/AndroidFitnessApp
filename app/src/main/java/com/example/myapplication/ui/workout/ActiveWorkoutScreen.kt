package com.example.myapplication.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.data.local.WorkoutSetEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    workoutId: Long,
    viewModel: ActiveSessionViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val exerciseStates by viewModel.exerciseStates.collectAsState()
    val coachBriefing by viewModel.coachBriefing.collectAsState()
    val workoutSummary by viewModel.workoutSummary.collectAsState()

    LaunchedEffect(workoutId) {
        viewModel.loadWorkout(workoutId)
    }

    // --- NEW: SUMMARY DIALOG ---
    if (workoutSummary != null) {
        WorkoutSummaryDialog(
            report = workoutSummary!!,
            onDismiss = {
                viewModel.clearSummary()
                onBack() // Navigate back only after dismissing dialog
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Active Workout") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (exerciseStates.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    CoachBriefingCard(briefing = coachBriefing)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                items(exerciseStates) { exerciseState ->
                    ExerciseHeader(
                        exerciseState = exerciseState,
                        onToggleVisibility = { viewModel.toggleExerciseVisibility(exerciseState.exercise.exerciseId) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (exerciseState.areSetsVisible) {
                        SetsTable(sets = exerciseState.sets, viewModel = viewModel)
                        Spacer(modifier = Modifier.height(16.dp))
                        SetTimer(exerciseState = exerciseState, viewModel = viewModel)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
                item {
                    Button(onClick = { viewModel.finishWorkout(workoutId) }) {
                        Text("Finish Workout")
                    }
                }
            }
        }
    }
}

// --- NEW COMPONENT: SUMMARY DIALOG ---
@Composable
fun WorkoutSummaryDialog(report: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { }, // Force user to click button
        icon = { Icon(Icons.Default.Check, contentDescription = "Success") },
        title = { Text("Workout Complete!") },
        text = {
            Column {
                Text("Great job finishing today's session.")
                Spacer(modifier = Modifier.height(16.dp))

                if (report.isNotEmpty()) {
                    Text("Auto-Regulation Updates:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    report.forEach { log ->
                        Text("• $log", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Future weeks have been updated based on your RPE performance today.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                } else {
                    Text("No weight adjustments needed. You are right on track!", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Return Home")
            }
        }
    )
}

@Composable
fun CoachBriefingCard(briefing: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = "Coach Briefing",
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "COACH'S BRIEFING",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = briefing,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
fun SetsTable(sets: List<WorkoutSetEntity>, viewModel: ActiveSessionViewModel) {
    var showRpeInfo by remember { mutableStateOf(false) }

    if (showRpeInfo) {
        RpeInfoDialog { showRpeInfo = false }
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("SET", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("LBS", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("REPS", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text("RPE", color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { showRpeInfo = true }) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "RPE Info",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text("DONE", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))

        sets.forEach { set ->
            SetRow(set = set, viewModel = viewModel)
        }
    }
}

@Composable
fun SetRow(set: WorkoutSetEntity, viewModel: ActiveSessionViewModel) {
    val backgroundColor = if (set.isCompleted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val borderColor = if (set.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val focusManager = LocalFocusManager.current

    var weightText by remember(set.actualLbs, set.suggestedLbs) {
        mutableStateOf(
            if (set.actualLbs != null && set.actualLbs > 0f) set.actualLbs.toInt().toString()
            else set.suggestedLbs.toInt().toString()
        )
    }
    var repsText by remember(set.actualReps) {
        mutableStateOf(if (set.actualReps != null && set.actualReps > 0) set.actualReps.toString() else "")
    }
    var rpeText by remember(set.actualRpe) {
        mutableStateOf(
            if (set.actualRpe != null && set.actualRpe > 0f) set.actualRpe.toInt().toString()
            else ""
        )
    }

    val weightColor = if (set.actualLbs == null || set.actualLbs == 0f) Color.Gray else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, shape = RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, shape = RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = set.setNumber.toString(), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))

        TextField(
            value = weightText,
            onValueChange = { weightText = it.filter { char -> char.isDigit() } },
            modifier = Modifier
                .weight(1f)
                .width(50.dp)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused && weightText.isNotEmpty()) {
                        viewModel.updateSetWeight(set, weightText)
                    }
                },
            placeholder = { Text(set.suggestedLbs.toInt().toString(), color = Color.Gray) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            colors = TextFieldDefaults.colors(
                unfocusedTextColor = weightColor,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent
            )
        )

        TextField(
            value = repsText,
            onValueChange = { repsText = it.filter { char -> char.isDigit() } },
            modifier = Modifier
                .weight(1f)
                .width(50.dp)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused && repsText.isNotEmpty()) {
                        viewModel.updateSetReps(set, repsText)
                    }
                },
            placeholder = { Text(set.suggestedReps.toString(), color = Color.Gray) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            colors = TextFieldDefaults.colors(
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent
            )
        )

        TextField(
            value = rpeText,
            onValueChange = { rpeText = it.filter { char -> char.isDigit() } },
            modifier = Modifier
                .weight(1f)
                .width(50.dp)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused && rpeText.isNotEmpty()) {
                        viewModel.updateSetRpe(set, rpeText)
                    }
                },
            placeholder = { Text(set.suggestedRpe.toString(), color = Color.Gray) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                viewModel.updateSetRpe(set, rpeText)
                focusManager.clearFocus()
            }),
            colors = TextFieldDefaults.colors(
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent
            )
        )

        Checkbox(
            checked = set.isCompleted,
            onCheckedChange = { viewModel.updateSetCompletion(set, it) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ExerciseHeader(exerciseState: ExerciseState, onToggleVisibility: () -> Unit) {
    val exercise = exerciseState.exercise
    var showDescriptionDialog by remember { mutableStateOf(false) }

    if (showDescriptionDialog) {
        AlertDialog(
            onDismissRequest = { showDescriptionDialog = false },
            title = { Text(text = exercise.name) },
            text = {
                Column {
                    Text(
                        text = "Instructions:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = exercise.description)
                }
            },
            confirmButton = {
                TextButton(onClick = { showDescriptionDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleVisibility() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = exercise.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { showDescriptionDialog = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Help,
                        contentDescription = "How to perform",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (exercise.tier > 0) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp)) {
                        Text(
                            text = "Tier ${exercise.tier}",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                if (!exercise.equipment.isNullOrBlank()) {
                    if (exercise.tier > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = exercise.equipment,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
        Icon(
            imageVector = if (exerciseState.areSetsVisible) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = "Toggle Exercise Visibility"
        )
    }
}

@Composable
fun SetTimer(exerciseState: ExerciseState, viewModel: ActiveSessionViewModel) {
    val timerState = exerciseState.timerState
    val minutes = timerState.remainingTime / 60
    val seconds = timerState.remainingTime % 60

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text("Set Timer", style = MaterialTheme.typography.titleMedium)

        if (timerState.isFinished) {
            Text(
                text = "Finished",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Text(
                text = String.format("%02d:%02d", minutes, seconds),
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(horizontalArrangement = Arrangement.Center) {
            if (timerState.isRunning) {
                OutlinedButton(onClick = { viewModel.skipSetTimer(exerciseState.exercise.exerciseId) }) {
                    Text("Skip Timer")
                }
            } else if (!timerState.isFinished) {
                Button(onClick = { viewModel.startSetTimer(exerciseState.exercise.exerciseId) }) {
                    Text("Start Timer")
                }
            } else {
                OutlinedButton(onClick = { viewModel.startSetTimer(exerciseState.exercise.exerciseId) }) {
                    Text("Restart Exercise")
                }
            }
        }
    }
}

@Composable
fun RpeInfoDialog(onDismissRequest: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("RPE Scale (5-10)") },
        text = {
            Column {
                Text("RPE 10: Max Effort, No reps left")
                Text("RPE 9:   1 rep left")
                Text("RPE 8:   2 reps left")
                Text("RPE 7:   3 reps left")
                Text("RPE 6:   4-5 reps left")
                Text("RPE 5:   5-6 reps left")
            }
        },
        confirmButton = {
            Button(onClick = onDismissRequest) {
                Text("Close")
            }
        }
    )
}