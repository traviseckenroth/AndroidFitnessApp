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
import com.example.myapplication.ui.camera.CameraFormCheckScreen
import com.example.myapplication.ui.camera.FormAnalyzer
import com.example.myapplication.util.PlateCalculator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    workoutId: Long,
    viewModel: ActiveSessionViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onWorkoutComplete: (Long) -> Unit
) {
    val exerciseStates by viewModel.exerciseStates.collectAsState()
    val coachBriefing by viewModel.coachBriefing.collectAsState()
    val workoutSummary by viewModel.workoutSummary.collectAsState()

    val currentView = LocalView.current
    DisposableEffect(Unit) {
        currentView.keepScreenOn = true
        onDispose {
            currentView.keepScreenOn = false
        }
    }

    var activeCameraExerciseState by remember { mutableStateOf<ExerciseState?>(null) }

    if (activeCameraExerciseState != null) {
        val nextSet = activeCameraExerciseState!!.sets.firstOrNull { !it.isCompleted }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            CameraFormCheckScreen(
                exerciseName = activeCameraExerciseState!!.exercise.name,
                targetWeight = nextSet?.suggestedLbs?.toInt() ?: 0,
                targetReps = nextSet?.suggestedReps ?: 0,
                onClose = { activeCameraExerciseState = null },
                // NEW: Connect the UI to the Bedrock Client in the ViewModel
                fetchAiCue = { issue ->
                    viewModel.bedrockClient.generateCoachingCue(
                        activeCameraExerciseState!!.exercise.name,
                        issue,
                        0 // You can pass actual rep count if available
                    )
                }
            )
        }
        return
    }
    LaunchedEffect(workoutId) {
        viewModel.loadWorkout(workoutId)
    }

    LaunchedEffect(workoutSummary) {
        if (workoutSummary != null) {
            // Once the viewmodel generates the summary (meaning db is updated), we navigate
            viewModel.clearSummary() // Optional cleanup
            onWorkoutComplete(workoutId)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Active Workout",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (exerciseStates.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    WorkoutHeader(title = "Today's Session")
                    CoachBriefingCard(briefing = coachBriefing)
                }

                items(
                    items = exerciseStates,
                    key = { it.exercise.exerciseId }
                ) { exerciseState ->
                    ExerciseHeader(
                        exerciseState = exerciseState,
                        viewModel = viewModel,
                        onToggleVisibility = { viewModel.toggleExerciseVisibility(exerciseState.exercise.exerciseId) },
                        onLaunchCamera = { activeCameraExerciseState = exerciseState }
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (exerciseState.areSetsVisible) {
                        SetsTable(
                            sets = exerciseState.sets,
                            equipment = exerciseState.exercise.equipment,
                            viewModel = viewModel
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SetTimer(exerciseState = exerciseState, viewModel = viewModel)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    Button(
                        onClick = { viewModel.finishWorkout(workoutId) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Finish Workout", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun WorkoutHeader(title: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Focus: Progressive Overload",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

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
            title = { Text(exercise.name) },
            text = { Text(exercise.description) },
            confirmButton = { TextButton(onClick = { showDescriptionDialog = false }) { Text("Close") } }
        )
    }

    if (showSwapDialog) {
        SwapExerciseDialog(
            alternatives = alternatives,
            onDismiss = { showSwapDialog = false },
            onSwap = { newExerciseId ->
                viewModel.swapExercise(exercise.exerciseId, newExerciseId)
                showSwapDialog = false
            }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggleVisibility() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = exercise.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false)
                )
                IconButton(onClick = { showDescriptionDialog = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Help, // Updated icon
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Row(modifier = Modifier.padding(top = 4.dp)) {
                Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("Tier ${exercise.tier}", color = Color.Black) }
                Spacer(modifier = Modifier.width(8.dp))
                if (!exercise.equipment.isNullOrBlank()) {
                    Badge(containerColor = Color.DarkGray) { Text(exercise.equipment, color = Color.White) }
                }
            }
        }

        // Inline "hasWarmups" check
        if (exerciseState.exercise.tier == 1 && !exerciseState.sets.any { it.suggestedRpe == 0 }) {
            Spacer(modifier = Modifier.width(8.dp))
            AssistChip(
                onClick = {
                    val target = exerciseState.sets.firstOrNull()?.suggestedLbs ?: 135
                    viewModel.addWarmUpSets(exerciseState.exercise.exerciseId, target)
                },
                label = { Text("Warm-up") },
                leadingIcon = { Icon(Icons.Default.LocalFireDepartment, null, tint = Color(0xFFFF9800)) }
            )
        }

        if (FormAnalyzer.isSupported(exercise.name)) {
            IconButton(onClick = onLaunchCamera) {
                Icon(Icons.Default.Videocam, "Camera", tint = MaterialTheme.colorScheme.error)
            }
        }

        IconButton(onClick = {
            scope.launch {
                alternatives = viewModel.getTopAlternatives(exercise)
                showSwapDialog = true
            }
        }) {
            Icon(Icons.Default.SwapHoriz, "Swap", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SetsTable(sets: List<WorkoutSetEntity>, equipment: String?, viewModel: ActiveSessionViewModel) {
    var showRpeInfo by remember { mutableStateOf(false) }

    if (showRpeInfo) {
        RpeInfoDialog(onDismiss = { showRpeInfo = false })
    }

    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("SET", modifier = Modifier.weight(0.5f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("LBS", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("REPS", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)

            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text("RPE", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = { showRpeInfo = true }, modifier = Modifier.size(16.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Help, "RPE Guide", tint = MaterialTheme.colorScheme.primary)
                }
            }

            Text("DONE", modifier = Modifier.weight(0.5f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        sets.forEachIndexed { index, set ->
            // Inline "isBarbell" check
            SetRow(
                setNumber = index + 1,
                set = set,
                isBarbell = equipment?.contains("Barbell", ignoreCase = true) == true,
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
                Column {
                    Text("Per side:", fontWeight = FontWeight.Bold)
                    Text(PlateCalculator.calculatePlates(set.suggestedLbs.toDouble()), fontSize = 18.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showPlateDialog = false }) { Text("Close") } }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$setNumber",
            modifier = Modifier.weight(0.5f),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            TextField(
                value = weightText,
                onValueChange = { weightText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (!it.isFocused) viewModel.updateSetWeight(set, weightText) },
                placeholder = {
                    Text(
                        text = set.suggestedLbs.toString(),
                        color = Color.Gray
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary
                )
            )
            if (isBarbell) {
                IconButton(
                    onClick = { showPlateDialog = true },
                    modifier = Modifier.size(24.dp).padding(end = 4.dp)
                ) {
                    Icon(Icons.Default.Album, contentDescription = "Plates", tint = Color.Gray)
                }
            }
        }

        TextField(
            value = repsText,
            onValueChange = { repsText = it },
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { if (!it.isFocused) viewModel.updateSetReps(set, repsText) },
            placeholder = { Text(set.suggestedReps.toString(), color = Color.Gray) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            colors = TextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary
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
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary
            )
        )

        Checkbox(
            checked = set.isCompleted,
            onCheckedChange = { viewModel.updateSetCompletion(set, it) },
            modifier = Modifier.weight(0.5f),
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                uncheckedColor = Color.Gray
            )
        )
    }
}

@Composable
fun SetTimer(exerciseState: ExerciseState, viewModel: ActiveSessionViewModel) {
    val timerState = exerciseState.timerState

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        // Inline minutes/seconds calculation
        Text(
            text = String.format(
                Locale.US,
                "%02d:%02d",
                timerState.remainingTime / 60,
                timerState.remainingTime % 60
            ),
            style = MaterialTheme.typography.displayMedium,
            color = if (timerState.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )

        Button(onClick = {
            if (timerState.isRunning) {
                viewModel.skipSetTimer(exerciseState.exercise.exerciseId)
            } else {
                viewModel.startSetTimer(exerciseState.exercise.exerciseId)
            }
        }) {
            Text(if (timerState.isRunning) "Skip Rest" else "Start Timer")
        }
    }
}

@Composable
fun CoachBriefingCard(briefing: String) {
    if (briefing.isBlank()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = briefing, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun WorkoutSummaryDialog(report: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(48.dp)) },
        title = { Text("Session Complete!") },
        text = {
            Column {
                report.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Finish") } }
    )
}

@Composable
fun RpeInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("RPE Scale (1-10)") },
        text = {
            Column {
                Text("10: Max Effort (0 reps left)")
                Text("9: Heavy (1 rep left)")
                Text("8: Moderate-Heavy (2 reps left)")
                Text("7: Moderate (3 reps left)")
                Text("6: Light (4+ reps left)")
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun SwapExerciseDialog(
    alternatives: List<ExerciseEntity>,
    onDismiss: () -> Unit,
    onSwap: (newExerciseId: Long) -> Unit
) {
    if (alternatives.isEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("No Alternatives Found") },
            text = { Text("Sorry, we couldn't find any suitable alternative exercises at the moment.") },
            confirmButton = { Button(onClick = onDismiss) { Text("OK") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Swap Exercise") },
        text = {
            Column {
                alternatives.forEach { exercise ->
                    TextButton(onClick = { onSwap(exercise.exerciseId) }) {
                        Text(exercise.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}