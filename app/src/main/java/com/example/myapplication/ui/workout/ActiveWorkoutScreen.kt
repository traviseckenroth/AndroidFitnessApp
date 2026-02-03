package com.example.myapplication.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.local.WorkoutSetEntity
import com.example.myapplication.ui.camera.CameraFormCheckScreen
import com.example.myapplication.ui.camera.FormAnalyzer
import kotlinx.coroutines.launch

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

    // Fixed variable naming to match usage
    var activeCameraExerciseState by remember { mutableStateOf<ExerciseState?>(null) }

    // Camera Overlay Logic
    if (activeCameraExerciseState != null) {
        val nextSet = activeCameraExerciseState!!.sets.firstOrNull { !it.isCompleted }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            CameraFormCheckScreen(
                exerciseName = activeCameraExerciseState!!.exercise.name,
                targetWeight = nextSet?.suggestedLbs?.toInt() ?: 0,
                targetReps = nextSet?.suggestedReps ?: 0,
                onClose = { activeCameraExerciseState = null }
            )
        }
        return
    }

    LaunchedEffect(workoutId) {
        viewModel.loadWorkout(workoutId)
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Active Workout", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
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

                items(exerciseStates) { exerciseState ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            ExerciseHeader(
                                exerciseState = exerciseState,
                                viewModel = viewModel,
                                onToggleVisibility = { viewModel.toggleExerciseVisibility(exerciseState.exercise.exerciseId) },
                                onLaunchCamera = { activeCameraExerciseState = exerciseState }
                            )

                            if (exerciseState.areSetsVisible) {
                                Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.DarkGray)
                                SetsTable(sets = exerciseState.sets, viewModel = viewModel)
                                SetTimer(exerciseState = exerciseState, viewModel = viewModel)
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = { viewModel.finishWorkout(workoutId) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Finish Workout", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun WorkoutHeader(title: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
        Text(text = "Tuesday, February 3", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
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

    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggleVisibility() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = exercise.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Row(modifier = Modifier.padding(top = 4.dp)) {
                Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("Tier ${exercise.tier}", color = Color.Black) }
                Spacer(modifier = Modifier.width(8.dp))
                if (!exercise.equipment.isNullOrBlank()) {
                    Badge(containerColor = Color.DarkGray) { Text(exercise.equipment, color = Color.White) }
                }
            }
        }

        IconButton(onClick = { showDescriptionDialog = true }) {
            Icon(Icons.Default.Help, "Info", tint = MaterialTheme.colorScheme.primary)
        }

        if (FormAnalyzer.isSupported(exercise.name)) {
            IconButton(onClick = onLaunchCamera) {
                Icon(Icons.Default.Videocam, "Camera", tint = Color.Red)
            }
        }

        IconButton(onClick = {
            scope.launch {
                alternatives = viewModel.getTopAlternatives(exercise)
                showSwapDialog = true
            }
        }) {
            Icon(Icons.Default.SwapHoriz, "Swap", tint = Color.Gray)
        }
    }
}

@Composable
fun SetsTable(sets: List<WorkoutSetEntity>, viewModel: ActiveSessionViewModel) {
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text("SET", modifier = Modifier.weight(0.5f), color = Color.Gray, fontSize = 12.sp)
            Text("LBS", modifier = Modifier.weight(1f), color = Color.Gray, fontSize = 12.sp)
            Text("REPS", modifier = Modifier.weight(1f), color = Color.Gray, fontSize = 12.sp)
            Text("RPE", modifier = Modifier.weight(1f), color = Color.Gray, fontSize = 12.sp)
            Text("DONE", modifier = Modifier.weight(0.5f), color = Color.Gray, fontSize = 12.sp)
        }
        sets.forEachIndexed { index, set ->
            SetRow(setNumber = index + 1, set = set, viewModel = viewModel)
        }
    }
}

@Composable
fun SetRow(setNumber: Int, set: WorkoutSetEntity, viewModel: ActiveSessionViewModel) {
    val focusManager = LocalFocusManager.current
    var weightText by remember(set.actualLbs) { mutableStateOf(set.actualLbs?.toInt()?.toString() ?: "") }
    var repsText by remember(set.actualReps) { mutableStateOf(set.actualReps?.toString() ?: "") }
    var rpeText by remember(set.actualRpe) { mutableStateOf(set.actualRpe?.toInt()?.toString() ?: "") }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$setNumber", modifier = Modifier.weight(0.5f), color = Color.White, fontWeight = FontWeight.Bold)

        TextField(
            value = weightText,
            onValueChange = { weightText = it },
            modifier = Modifier.weight(1f).onFocusChanged { if(!it.isFocused) viewModel.updateSetWeight(set, weightText) },
            placeholder = { Text(set.suggestedLbs.toString(), color = Color.DarkGray) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            colors = TextFieldDefaults.colors(unfocusedContainerColor = Color.Transparent, focusedContainerColor = Color.Transparent)
        )

        TextField(
            value = repsText,
            onValueChange = { repsText = it },
            modifier = Modifier.weight(1f).onFocusChanged { if(!it.isFocused) viewModel.updateSetReps(set, repsText) },
            placeholder = { Text(set.suggestedReps.toString(), color = Color.DarkGray) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            colors = TextFieldDefaults.colors(unfocusedContainerColor = Color.Transparent, focusedContainerColor = Color.Transparent)
        )

        TextField(
            value = rpeText,
            onValueChange = { rpeText = it },
            modifier = Modifier.weight(1f).onFocusChanged { if(!it.isFocused) viewModel.updateSetRpe(set, rpeText) },
            placeholder = { Text("0", color = Color.DarkGray) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            colors = TextFieldDefaults.colors(unfocusedContainerColor = Color.Transparent, focusedContainerColor = Color.Transparent)
        )

        Checkbox(
            checked = set.isCompleted,
            onCheckedChange = { viewModel.updateSetCompletion(set, it) },
            modifier = Modifier.weight(0.5f)
        )
    }
}

@Composable
fun SetTimer(exerciseState: ExerciseState, viewModel: ActiveSessionViewModel) {
    val timerState = exerciseState.timerState
    val minutes = timerState.remainingTime / 60
    val seconds = timerState.remainingTime % 60

    if (timerState.remainingTime > 0 || timerState.isRunning) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Text(text = String.format("%02d:%02d", minutes, seconds), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = { viewModel.skipSetTimer(exerciseState.exercise.exerciseId) }) {
                Text("Skip Rest")
            }
        }
    }
}

@Composable
fun CoachBriefingCard(briefing: String) {
    if (briefing.isBlank()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = briefing, style = MaterialTheme.typography.bodyMedium, color = Color.White)
        }
    }
}

@Composable
fun WorkoutSummaryDialog(report: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Green, modifier = Modifier.size(48.dp)) },
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
        title = { Text("RPE Guide") },
        text = { Text("10: Failure\n9: 1 rep left\n8: 2 reps left\n7: 3 reps left") },
        confirmButton = { Button(onClick = onDismiss) { Text("Got it") } }
    )
}