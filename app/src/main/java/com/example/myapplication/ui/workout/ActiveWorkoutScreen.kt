// app/src/main/java/com/example/myapplication/ui/workout/ActiveWorkoutScreen.kt
package com.example.myapplication.ui.workout

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.myapplication.ui.navigation.*
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.navigation.NavController
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.local.WorkoutSetEntity
import com.example.myapplication.ui.theme.AmrapOrange
import com.example.myapplication.ui.theme.EmomPink
import com.example.myapplication.ui.theme.SuccessGreen
import com.example.myapplication.util.AutoCoachState
import com.example.myapplication.util.PlateCalculator
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ActiveWorkoutScreen(
    workoutId: Long,
    onBack: () -> Unit,
    navController: NavController,
    onWorkoutComplete: (Long) -> Unit,
    onNavigateToLiveCoach: () -> Unit,
    viewModel: ActiveSessionViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current

    // Keep screen on while this screen is active
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
        }
    }

    val exerciseStates by viewModel.exerciseStates.collectAsState()
    val workoutSummary by viewModel.workoutSummary.collectAsState()
    val isBleConnected by viewModel.isBleConnected.collectAsState()
    val foundDevices by viewModel.foundBleDevices.collectAsState()
    val autoCoachState by viewModel.autoCoachState.collectAsState()
    val coachBriefing by viewModel.coachBriefing.collectAsState()
    val totalEstimatedTime by viewModel.totalEstimatedTime.collectAsState()

    var showAddExerciseDialog by remember { mutableStateOf(false) }

    var showBleDialog by remember { mutableStateOf(false) }
    var isPocketModeActive by remember { mutableStateOf(false) }
    val workoutListState = rememberLazyListState()

    val progress by viewModel.workoutProgress.collectAsState()

    var permissionsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                    } else {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    })
        )
    }

    val launcher = rememberLauncherForActivityResult(RequestMultiplePermissions()) { permissions ->
        permissionsGranted = permissions.values.all { it }
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            val neededPermissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                neededPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
                neededPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                neededPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            launcher.launch(neededPermissions.toTypedArray())
        }
    }

    LaunchedEffect(workoutId) { viewModel.loadWorkout(workoutId) }

    LaunchedEffect(workoutSummary) {
        if (workoutSummary != null) {
            viewModel.clearSummary()
            onWorkoutComplete(workoutId)
        }
    }

    if (showAddExerciseDialog) {
        AddExerciseDialog(
            viewModel = viewModel,
            onDismiss = { showAddExerciseDialog = false },
            onExerciseSelected = { exerciseId ->
                viewModel.addExercise(exerciseId)
                showAddExerciseDialog = false
            }
        )
    }

    if (showBleDialog) {
        BleDeviceDialog(
            foundDevices = foundDevices,
            onDismiss = {
                viewModel.stopBleScan()
                showBleDialog = false
            },
            onConnect = { device ->
                viewModel.connectBleDevice(device)
                showBleDialog = false
            }
        )
    }
// FIXED: Explicitly added import and ensured types are inferable
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    // Observe the saved state handle result with explicit type
    val selectedExerciseId: State<Long?> = navBackStackEntry?.savedStateHandle
        ?.getStateFlow<Long?>("selected_exercise_id", null)
        ?.collectAsState() ?: remember { mutableStateOf(null) }

    // REACT TO THE SELECTION
    LaunchedEffect(selectedExerciseId.value) {
        selectedExerciseId.value?.let { id ->
            viewModel.addExercise(id)
            // Clear the result immediately
            navBackStackEntry?.savedStateHandle?.remove<Long>("selected_exercise_id")
        }
    }
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = "Active Session",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Est. Time: $totalEstimatedTime min",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                viewModel.startBleScan()
                                showBleDialog = true
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Connect Heart Rate",
                                    tint = if (isBleConnected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            IconButton(onClick = { isPocketModeActive = true }) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Lock Screen",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = {
                                    onNavigateToLiveCoach()
                                    if (autoCoachState == AutoCoachState.OFF) {
                                        viewModel.toggleAutoCoach()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = "Open Auto-Coach",
                                    tint = if (autoCoachState != AutoCoachState.OFF) SuccessGreen else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                    )
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        navController.navigate(ExerciseList(isPickerMode = true))},
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Exercise")
                }
            }
        ) { innerPadding ->
            LazyColumn(
                state = workoutListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                item {
                    CoachBriefingCard(briefing = coachBriefing)
                }

                items(exerciseStates, key = { it.exercise.exerciseId }) { state ->
                    ExerciseCard(
                        state = state,
                        viewModel = viewModel,
                        haptic = haptic,
                        modifier = Modifier.animateItemPlacement()
                    )
                }

                item {
                    Button(
                        onClick = { viewModel.finishWorkout(workoutId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Complete Workout",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }

        if (isPocketModeActive) {
            PocketModeOverlay(
                onUnlock = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    isPocketModeActive = false
                }
            )
        }
    }
}

@Composable
fun CoachBriefingCard(briefing: String) {
    if (briefing.isBlank() || briefing == "Loading briefing...") return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Coach Briefing",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = briefing,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                lineHeight = 20.sp
            )
        }
    }
}

fun getTierName(tier: Int): String {
    return when (tier) {
        1 -> "Compound"
        2 -> "Secondary"
        3 -> "Isolation"
        else -> "Tier $tier"
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ExerciseCard(
    state: ExerciseState,
    viewModel: ActiveSessionViewModel,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    modifier: Modifier = Modifier
) {
    val exercise = state.exercise
    val isBodyweight = exercise.equipment?.contains("Bodyweight", ignoreCase = true) == true
    val isRunning = exercise.name.contains("run", ignoreCase = true) ||
            exercise.name.contains("sprint", ignoreCase = true) ||
            exercise.name.contains("treadmill", ignoreCase = true) ||
            exercise.movementPattern.contains("cardio", ignoreCase = true)

    val allSetsCompleted = state.sets.isNotEmpty() && state.sets.all { it.isCompleted }
    var showSwapDialog by remember { mutableStateOf(false) }
    var showDescriptionDialog by remember { mutableStateOf(false) }

    // Evaluate if the entire exercise block should be styled for AMRAP/EMOM
    val isAMRAP = state.sets.any { it.isAMRAP } || exercise.name.contains("AMRAP", true)
    val isEMOM = state.sets.any { it.isEMOM } || exercise.name.contains("EMOM", true)
    val isCircuit = isAMRAP || isEMOM

    // FIX: Force hide LBS and RPE completely if it's a circuit
    val hideLbs = isBodyweight || isCircuit
    val hideRpe = isCircuit || isBodyweight

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = exercise.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "Description",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp).clickable { showDescriptionDialog = true }
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${exercise.muscleGroup} • ${getTierName(exercise.tier)} • ${exercise.equipment ?: ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (isAMRAP) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(color = AmrapOrange.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                Text("AMRAP", color = AmrapOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal=4.dp, vertical=2.dp))
                            }
                        } else if (isEMOM) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(color =  EmomPink.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                Text("EMOM", color =  EmomPink, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal=4.dp, vertical=2.dp))
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { showSwapDialog = true }
                        ) {
                            Text(
                                text = "Swap",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                Icon(
                    imageVector = if (state.areSetsVisible) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { viewModel.toggleExerciseVisibility(exercise.exerciseId) }
                )
            }

            // DYNAMIC NOTES & INSTRUCTIONS BOX
            val displayNotes = exercise.notes?.takeIf { it.isNotBlank() } ?: exercise.description.takeIf { it.isNotBlank() }

            if (!displayNotes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                if (isCircuit) {
                    // Colored box for AMRAP / EMOM Circuits
                    Surface(
                        color = if (isAMRAP) Color(0xFFFFF3E0) else Color(0xFFFCE4EC),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = if (isAMRAP) AmrapOrange else  EmomPink,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.6.dp))
                                Text(
                                    text = if (isAMRAP) "CIRCUIT INSTRUCTIONS" else "EMOM INSTRUCTIONS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAMRAP) AmrapOrange else  EmomPink
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = displayNotes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    // Clean box for standard exercise notes
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Notes",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = displayNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }
                }
            }

            if (state.areSetsVisible) {
                Spacer(modifier = Modifier.height(16.dp))

                // Table Header
                Row(modifier = Modifier.fillMaxWidth()) {
                    val headerStyle = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                    Text(if (isCircuit && state.sets.size == 1) "SCORE" else "SET", modifier = Modifier.weight(0.8f), style = headerStyle, textAlign = TextAlign.Center)

                    if (!hideLbs) {
                        Text("LBS", modifier = Modifier.weight(1.5f), style = headerStyle, textAlign = TextAlign.Center)
                    }

                    val repsWeight = 1.2f + (if (hideLbs) 1.5f else 0f) + (if (hideRpe) 1f else 0f)
                    Text(if (isCircuit) "ROUNDS / REPS" else "REPS", modifier = Modifier.weight(repsWeight), style = headerStyle, textAlign = TextAlign.Center)

                    if (!hideRpe) {
                        Text("RPE", modifier = Modifier.weight(1f), style = headerStyle, textAlign = TextAlign.Center)
                    }

                    Text("DONE", modifier = Modifier.weight(1f), style = headerStyle, textAlign = TextAlign.Center)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Set Rows
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    state.sets.forEach { set ->
                        SetRow(set = set, exercise = exercise, viewModel = viewModel, haptic = haptic)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Footer
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (!isCircuit) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { viewModel.addSet(exercise.exerciseId) }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Set", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }

                        if (!isBodyweight) {
                            Spacer(modifier = Modifier.width(24.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { viewModel.addWarmUpSets(exercise.exerciseId, state.sets.firstOrNull()?.suggestedLbs ?: 135, exercise.equipment) }
                            ) {
                                Icon(Icons.Default.Whatshot, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Warm Up", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        // Keep spacing clean for circuits
                        Text("Log your final circuit score.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    SetTimerPill(
                        timerState = state.timerState,
                        onStart = { viewModel.startSetTimer(exercise.exerciseId, isRest = false) },
                        onSkip = { viewModel.skipSetTimer(exercise.exerciseId) }
                    )
                }
            }
        }
    }

    if (showSwapDialog) {
        SwapExerciseDialog(
            originalExercise = exercise,
            viewModel = viewModel,
            onDismiss = { showSwapDialog = false },
            onSwap = { newExerciseId ->
                viewModel.swapExercise(exercise.exerciseId, newExerciseId)
                showSwapDialog = false
            }
        )
    }

    if (showDescriptionDialog) {
        AlertDialog(
            onDismissRequest = { showDescriptionDialog = false },
            title = { Text(exercise.name, color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(exercise.description, color = MaterialTheme.colorScheme.onSurface) },
            confirmButton = {
                TextButton(onClick = { showDescriptionDialog = false }) {
                    Text("Close", color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun StyledInputBox(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    placeholderText: String = ""
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(IntrinsicSize.Min),
        placeholder = {
            Text(
                text = placeholderText,
                style = textStyle.copy(color = textStyle.color.copy(alpha = 0.5f)),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        textStyle = textStyle.copy(textAlign = TextAlign.Center),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary
        ),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = true
    )
}

@Composable
fun SetRow(
    set: WorkoutSetEntity,
    exercise: ExerciseEntity,
    viewModel: ActiveSessionViewModel,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val focusManager = LocalFocusManager.current
    var showPlateCalc by remember { mutableStateOf(false) }

    val isBodyweight = exercise.equipment?.contains("Bodyweight", ignoreCase = true) == true
    val isRunning = exercise.name.contains("run", ignoreCase = true) ||
            exercise.name.contains("sprint", ignoreCase = true) ||
            exercise.name.contains("treadmill", ignoreCase = true) ||
            exercise.movementPattern.contains("cardio", ignoreCase = true)

    val isCircuit = set.isAMRAP || set.isEMOM || exercise.name.contains("AMRAP", true) || exercise.name.contains("EMOM", true)

    val hideLbs = isBodyweight || isCircuit
    val hideRpe = isCircuit || isBodyweight

    if (showPlateCalc) {
        PlateCalculatorDialog(
            targetWeight = set.actualLbs?.toDouble() ?: set.suggestedLbs.toDouble(),
            barWeight = viewModel.barWeight.collectAsState().value,
            onDismiss = { showPlateCalc = false }
        )
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // SET NUMBER
        Text(
            text = set.setNumber.toString(),
            modifier = Modifier.weight(0.8f),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        // DYNAMIC UI BLOCK
        if (isRunning) {
            val actualWeightStr = set.actualLbs?.let { if (it % 1 == 0f) it.toInt().toString() else it.toString() } ?: ""
            StyledInputBox(
                value = actualWeightStr,
                placeholderText = set.suggestedLbs.toString(),
                onValueChange = { viewModel.updateSetWeight(set, it) },
                modifier = Modifier.weight(1.5f),
                textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )
            Text(" mi", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            val actualRepsStr = set.actualReps?.toString() ?: ""
            StyledInputBox(
                value = actualRepsStr,
                placeholderText = set.suggestedReps.toString(),
                onValueChange = { viewModel.updateSetReps(set, it) },
                modifier = Modifier.weight(if (isBodyweight) 2.4f else 1.2f),
                textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )
            Text(" min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.weight(1f))

        } else {
            // --- STANDARD, AMRAP & EMOM UI ---
            if (!hideLbs) {
                Row(
                    modifier = Modifier.weight(1.5f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val actualWeightStr = set.actualLbs?.let { if (it % 1 == 0f) it.toInt().toString() else it.toString() } ?: ""

                    val weightColor = if (set.isAutoAdjusted && set.actualLbs == null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }

                    StyledInputBox(
                        value = actualWeightStr,
                        placeholderText = set.suggestedLbs.toString(),
                        onValueChange = { viewModel.updateSetWeight(set, it) },
                        modifier = Modifier.width(80.dp),
                        textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = weightColor),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )

                    if (set.isAutoAdjusted && set.actualLbs == null) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Auto-adjusted",
                            modifier = Modifier.size(12.dp).padding(start = 2.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else if (exercise.equipment?.contains("Barbell", ignoreCase = true) == true) {
                        Icon(
                            imageVector = Icons.Default.Calculate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp).clickable { showPlateCalc = true },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // REPS
            val repsWeight = 1.2f + (if (hideLbs) 1.5f else 0f) + (if (hideRpe) 1f else 0f)
            val actualRepsStr = set.actualReps?.toString() ?: ""
            val repsPlaceholder = if (isCircuit) "Total Score" else set.suggestedReps.toString()

            StyledInputBox(
                value = actualRepsStr,
                placeholderText = repsPlaceholder,
                onValueChange = { viewModel.updateSetReps(set, it) },
                modifier = Modifier.weight(repsWeight),
                textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )

            // RPE
            if (!hideRpe) {
                val actualRpeStr = set.actualRpe?.let { if (it % 1 == 0f) it.toInt().toString() else it.toString() } ?: ""

                StyledInputBox(
                    value = actualRpeStr,
                    placeholderText = set.suggestedRpe.toString(),
                    onValueChange = { viewModel.updateSetRpe(set, it) },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )
            }
        }

        // DONE CHECKBOX
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .border(2.dp, if (set.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.updateSetCompletion(set, !set.isCompleted)
                    },
                contentAlignment = Alignment.Center
            ) {
                if (set.isCompleted) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun SetTimerPill(
    timerState: ExerciseTimerState,
    onStart: () -> Unit,
    onSkip: () -> Unit
) {
    val displayMinutes = timerState.remainingTime / 60
    val displaySeconds = timerState.remainingTime % 60
    val timeString = String.format(Locale.US, "%02d:%02d", displayMinutes, displaySeconds)

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.clickable {
            if (timerState.isRunning) onSkip() else onStart()
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.6.dp))
            Text(
                text = timeString,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PocketModeOverlay(onUnlock: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onUnlock() }) },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Screen Locked", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Long press to unlock", color = Color.LightGray, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            cursorColor = MaterialTheme.colorScheme.primary
        ),
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        singleLine = true
    )
}

@Composable
fun AddExerciseDialog(viewModel: ActiveSessionViewModel, onDismiss: () -> Unit, onExerciseSelected: (Long) -> Unit) {
    val exercises by viewModel.getAllExercises().collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    val filtered = exercises.filter { it.name.contains(query, true) || it.muscleGroup?.contains(query, true) == true }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(16.dp).clip(RoundedCornerShape(16.dp)),
        title = {
            Column {
                Text(
                    text = "Add Exercise",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search..."
                )
            }
        },
        text = {
            LazyColumn(modifier = Modifier.height(400.dp)) {
                items(filtered) { ex ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onExerciseSelected(ex.exerciseId) }
                            .padding(vertical = 12.dp, horizontal = 4.dp)
                    ) {
                        Text(
                            text = ex.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${ex.muscleGroup} • ${getTierName(ex.tier)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun BleDeviceDialog(foundDevices: List<BluetoothDevice>, onDismiss: () -> Unit, onConnect: (BluetoothDevice) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect HR Monitor", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            LazyColumn {
                items(foundDevices) { device ->
                    ListItem(
                        headlineContent = { Text(try { device.name ?: "Unknown" } catch (e: SecurityException) { "Unknown" }, color = MaterialTheme.colorScheme.onSurface) },
                        modifier = Modifier.clickable { onConnect(device) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun AMRAPPill(isAMRAP: Boolean, timeRemaining: Int?) {
    if (isAMRAP) {
        Surface(
            color = AmrapOrange, // High-visibility orange for AMRAP
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Timer, "AMRAP", tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "AMRAP: ${timeRemaining ?: "PUSH TO FAILURE"}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
fun SwapExerciseDialog(originalExercise: ExerciseEntity, viewModel: ActiveSessionViewModel, onDismiss: () -> Unit, onSwap: (Long) -> Unit) {
    var alternatives by remember { mutableStateOf<List<ExerciseEntity>>(emptyList()) }
    LaunchedEffect(originalExercise) { alternatives = viewModel.getTopAlternatives(originalExercise) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Swap ${originalExercise.name}",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            LazyColumn {
                items(alternatives) { alt ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSwap(alt.exerciseId) }
                            .padding(vertical = 12.dp)
                    ) {
                        Text(text = alt.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(text = alt.muscleGroup ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun PlateCalculatorDialog(targetWeight: Double, barWeight: Double, onDismiss: () -> Unit) {
    val platesText = PlateCalculator.calculatePlates(targetWeight, barWeight)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Load for $targetWeight lbs", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = { Text(if (targetWeight <= barWeight) "Just the bar!" else "Plates per side: $platesText", color = MaterialTheme.colorScheme.onSurface) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) } },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}