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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Videocam // <--- NEW IMPORT
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
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.local.WorkoutSetEntity
import com.example.myapplication.ui.camera.CameraFormCheckScreen // <--- NEW IMPORT
import kotlinx.coroutines.launch
import com.example.myapplication.ui.camera.FormAnalyzer

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

    var activeCameraExercise by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(workoutId) {
        viewModel.loadWorkout(workoutId)
    }

    if (activeCameraExercise != null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            CameraFormCheckScreen(
                exerciseName = activeCameraExercise!!,
                onClose = { activeCameraExercise = null }
            )
        }
        return // Return early to hide underlying UI
    }

    if (workoutSummary != null) {
        WorkoutSummaryDialog(
            report = workoutSummary!!,
            onDismiss = {
                viewModel.clearSummary()
                onBack()
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
                },

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
                        viewModel = viewModel,
                        onToggleVisibility = { viewModel.toggleExerciseVisibility(exerciseState.exercise.exerciseId) },
                        // PASS THE CAMERA TRIGGER:
                        onLaunchCamera = {
                            activeCameraExercise = exerciseState.exercise.name
                        }
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
                    Button(
                        onClick = { viewModel.finishWorkout(workoutId) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    ) {
                        Text("Finish Workout")
                    }
                }
            }
        }
    }
}

// ... (Rest of the file remains exactly the same: ExerciseHeader, SetsTable, etc.) ...
@Composable
fun ExerciseHeader(
    exerciseState: ExerciseState,
    viewModel: ActiveSessionViewModel,
    onToggleVisibility: () -> Unit,
    onLaunchCamera: () -> Unit
) {
    val exercise = exerciseState.exercise
    var showDescriptionDialog by remember { mutableStateOf(false) }
    var showSwapDialog by remember { mutableStateOf(false) }
    var alternatives by remember { mutableStateOf<List<ExerciseEntity>>(emptyList()) }
    val scope = rememberCoroutineScope()

    if (showDescriptionDialog) {
        AlertDialog(
            onDismissRequest = { showDescriptionDialog = false },
            title = { Text(text = exercise.name) },
            text = {
                Column {
                    Text("Instructions:", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = exercise.description)
                }
            },
            confirmButton = { TextButton(onClick = { showDescriptionDialog = false }) { Text("Close") } }
        )
    }

    if (showSwapDialog) {
        AlertDialog(
            onDismissRequest = { showSwapDialog = false },
            title = { Text("Smart Swaps for ${exercise.name}") },
            text = {
                Column {
                    Text("Recommended Alternatives:", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(alternatives) { alt ->
                            ListItem(
                                headlineContent = { Text(alt.name) },
                                supportingContent = { Text("${alt.equipment} • Tier ${alt.tier}") },
                                leadingContent = { Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700)) },
                                modifier = Modifier.clickable {
                                    viewModel.swapExercise(exercise.exerciseId, alt.exerciseId)
                                    showSwapDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSwapDialog = false }) { Text("Close") } }
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
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
// 1. Info Button
                IconButton(
                    onClick = { showDescriptionDialog = true },
                    modifier = Modifier.size(24.dp) // <--- Added for consistency
                ) {
                    Icon(Icons.Default.Help, contentDescription = "Info", tint = MaterialTheme.colorScheme.primary)
                }

                // 2. AI CAMERA BUTTON (Conditional)
                if (FormAnalyzer.isSupported(exercise.name)) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onLaunchCamera,
                        modifier = Modifier.size(24.dp) // This matches the others now
                    ) {
                        Icon(Icons.Default.Videocam, "Form Check", tint = MaterialTheme.colorScheme.error)
                    }
                }

                // 3. Swap Button
                Spacer(modifier = Modifier.width(8.dp)) // Add spacing before swap
                IconButton(
                    onClick = {
                        scope.launch {
                            alternatives = viewModel.getTopAlternatives(exercise)
                            showSwapDialog = true
                        }
                    },
                    modifier = Modifier.size(24.dp) // <--- Added for consistency
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = "Swap", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                Badge { Text("Tier ${exercise.tier}") }
                Spacer(modifier = Modifier.width(8.dp))
                if (!exercise.equipment.isNullOrBlank()) {
                    Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(exercise.equipment)
                    }
                }
            }
        }
        Icon(
            imageVector = if (exerciseState.areSetsVisible) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null
        )
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
                    Icon(Icons.Default.Info, contentDescription = "RPE Info", modifier = Modifier.size(16.dp))
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
    val focusManager = LocalFocusManager.current

    var weightText by remember(set.actualLbs) {
        mutableStateOf(
            if ((set.actualLbs ?: 0f) > 0f) set.actualLbs?.toInt().toString() else ""
        )
    }
    var repsText by remember(set.actualReps) {
        mutableStateOf(
            if ((set.actualReps ?: 0) > 0) set.actualReps.toString() else ""
        )
    }
    var rpeText by remember(set.actualRpe) {
        mutableStateOf(
            if ((set.actualRpe ?: 0f) > 0f) set.actualRpe?.toInt().toString() else ""
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, shape = RoundedCornerShape(8.dp))
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = set.setNumber.toString(), modifier = Modifier.weight(1f))

        TextField(
            value = weightText,
            onValueChange = { weightText = it },
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { if (!it.isFocused) viewModel.updateSetWeight(set, weightText) },
            placeholder = { Text(set.suggestedLbs.toString(), color = Color.Gray) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent
            )
        )

        TextField(
            value = repsText,
            onValueChange = { repsText = it },
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { if (!it.isFocused) viewModel.updateSetReps(set, repsText) },
            placeholder = { Text(set.suggestedReps.toString(), color = Color.Gray) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent
            )
        )

        TextField(
            value = rpeText,
            onValueChange = { rpeText = it },
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { if (!it.isFocused) viewModel.updateSetRpe(set, rpeText) },
            placeholder = { Text(set.suggestedRpe.toString(), color = Color.Gray) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            colors = TextFieldDefaults.colors(
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
fun SetTimer(exerciseState: ExerciseState, viewModel: ActiveSessionViewModel) {
    val timerState = exerciseState.timerState
    val minutes = timerState.remainingTime / 60
    val seconds = timerState.remainingTime % 60

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(text = String.format("%02d:%02d", minutes, seconds), fontSize = 48.sp, fontWeight = FontWeight.Bold)
        Row {
            Button(onClick = {
                if (timerState.isRunning) viewModel.skipSetTimer(exerciseState.exercise.exerciseId)
                else viewModel.startSetTimer(exerciseState.exercise.exerciseId)
            }) {
                Text(if (timerState.isRunning) "Skip Timer" else "Start Timer")
            }
        }
    }
}

@Composable
fun CoachBriefingCard(briefing: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Notifications, contentDescription = null)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("COACH'S BRIEFING", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(text = briefing, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun WorkoutSummaryDialog(report: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        icon = { Icon(Icons.Default.Check, contentDescription = null) },
        title = { Text("Workout Complete!") },
        text = {
            Column {
                Text("Summary of improvements:")
                report.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Return Home") } }
    )
}

@Composable
fun RpeInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("RPE Scale (5-10)") },
        text = { Text("10: Max Effort\n9: 1 rep left\n8: 2 reps left\n7: 3 reps left") },
        confirmButton = { Button(onClick = onDismiss) { Text("Close") } }
    )
}
