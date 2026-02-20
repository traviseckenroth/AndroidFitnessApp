package com.example.myapplication.ui.workout

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognizerIntent
import android.view.KeyEvent
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
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
import com.example.myapplication.ui.camera.CameraFormCheckScreen
import com.example.myapplication.ui.camera.FormAnalyzer
import com.example.myapplication.util.PlateCalculator
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    val estimatedTime by viewModel.totalEstimatedTime.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showChatSheet by remember { mutableStateOf(false) }
    var userText by remember { mutableStateOf("") }
    val chatHistory by viewModel.chatHistory.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val scope = rememberCoroutineScope()
    val barWeight by viewModel.barWeight.collectAsState()
    val userGender by viewModel.userGender.collectAsState()

    var showAddExerciseDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val currentView = LocalView.current
    val workoutListState = rememberLazyListState()

    val totalSets = remember(exerciseStates) { exerciseStates.sumOf { it.sets.size } }
    val completedSets = remember(exerciseStates) { exerciseStates.sumOf { it.sets.count { s -> s.isCompleted } } }
    val progress = if (totalSets > 0) completedSets.toFloat() / totalSets else 0f

    // Auto-Scroll Logic: Triggered whenever a set is completed
    LaunchedEffect(completedSets) {
        if (exerciseStates.isNotEmpty()) {
            val activeExerciseIndex = exerciseStates.indexOfFirst { state ->
                state.sets.any { !it.isCompleted }
            }
            if (activeExerciseIndex != -1) {
                workoutListState.animateScrollToItem(activeExerciseIndex + 1)
            }
        }
    }

    DisposableEffect(Unit) {
        currentView.keepScreenOn = true
        currentView.setOnKeyListener { v, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_HEADSETHOOK || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {

                val activeExercise = viewModel.exerciseStates.value.firstOrNull { state ->
                    state.sets.any { !it.isCompleted }
                }

                if (activeExercise != null) {
                    if (activeExercise.timerState.isRunning) {
                        viewModel.startSetTimer(activeExercise.exercise.exerciseId)
                    } else {
                        val nextSet = activeExercise.sets.firstOrNull { !it.isCompleted }
                        if (nextSet != null) {
                            viewModel.updateSetCompletion(nextSet, true)
                            viewModel.startSetTimer(activeExercise.exercise.exerciseId)
                        }
                    }
                    return@setOnKeyListener true
                }
            }
            false
        }
        onDispose { currentView.keepScreenOn = false }
    }

    var permissionsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(RequestMultiplePermissions()) { permissions ->
        permissionsGranted = permissions.values.all { it }
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    var activeCameraExerciseState by remember { mutableStateOf<ExerciseState?>(null) }

    if (activeCameraExerciseState != null) {
        if (permissionsGranted) {
            val nextSet = activeCameraExerciseState!!.sets.firstOrNull { !it.isCompleted }
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                CameraFormCheckScreen(
                    exerciseName = activeCameraExerciseState!!.exercise.name,
                    targetWeight = nextSet?.suggestedLbs?.toInt() ?: 0,
                    targetReps = nextSet?.suggestedReps ?: 0,
                    onClose = { activeCameraExerciseState = null },
                    fetchAiCue = { issue -> viewModel.generateCoachingCue(activeCameraExerciseState!!.exercise.name, issue) }
                )
            }
        }
        return
    }

    LaunchedEffect(workoutId) { viewModel.loadWorkout(workoutId) }

    LaunchedEffect(workoutSummary) {
        if (workoutSummary != null) {
            viewModel.clearSummary()
            onWorkoutComplete(workoutId)
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            if (!spokenText.isNullOrBlank()) { userText = spokenText }
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Active Session", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Est. Time: $estimatedTime min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                FloatingActionButton(
                    onClick = { showAddExerciseDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.padding(bottom = 16.dp),
                    shape = CircleShape
                ) { Icon(Icons.Default.Add, "Add Exercise") }

                FloatingActionButton(
                    onClick = { showChatSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Chat, "AI Coach")
                        if (isListening) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(56.dp),
                                color = MaterialTheme.colorScheme.error,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (showChatSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    showChatSheet = false
                    if (isListening) viewModel.toggleLiveCoaching()
                },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                CoachChatContent(
                    chatHistory = chatHistory,
                    userText = userText,
                    isListening = isListening,
                    onUserTextChange = { userText = it },
                    onSend = {
                        if (userText.isNotBlank()) {
                            viewModel.interactWithCoach(userText)
                            userText = ""
                        }
                    },
                    onVoiceInput = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        }
                        voiceLauncher.launch(intent)
                    },
                    onToggleLive = { viewModel.toggleLiveCoaching() },
                    onQuickAction = { actionText -> viewModel.interactWithCoach(actionText) },
                    onDismiss = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) showChatSheet = false
                        }
                    }
                )
            }
        }

        if (exerciseStates.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                state = workoutListState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { CoachBriefingCard(briefing = coachBriefing) }

                items(exerciseStates, key = { it.exercise.exerciseId }) { exerciseState ->
                    ExerciseCard(
                        exerciseState = exerciseState,
                        viewModel = viewModel,
                        barWeight = barWeight,
                        userGender = userGender,
                        onLaunchCamera = { activeCameraExerciseState = exerciseState }
                    )
                }

                item {
                    Button(
                        onClick = { viewModel.finishWorkout(workoutId) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("FINISH WORKOUT", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

@Composable
fun ExerciseCard(
    exerciseState: ExerciseState,
    viewModel: ActiveSessionViewModel,
    barWeight: Double,
    userGender: String,
    onLaunchCamera: () -> Unit
) {
    val isActive = exerciseState.sets.any { !it.isCompleted }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ExerciseHeader(
                exerciseState = exerciseState,
                viewModel = viewModel,
                onToggleVisibility = { viewModel.toggleExerciseVisibility(exerciseState.exercise.exerciseId) },
                onLaunchCamera = onLaunchCamera
            )

            AnimatedVisibility(visible = exerciseState.areSetsVisible) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp).alpha(0.2f))
                    SetsTable(
                        exerciseId = exerciseState.exercise.exerciseId,
                        sets = exerciseState.sets,
                        equipment = exerciseState.exercise.equipment,
                        viewModel = viewModel,
                        barWeight = barWeight,
                        userGender = userGender
                    )
                    SetTimer(exerciseState = exerciseState, viewModel = viewModel)
                }
            }
        }
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
    val isAllCompleted = exerciseState.sets.all { it.isCompleted }

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
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isAllCompleted) Color.Gray else MaterialTheme.colorScheme.onSurface
            )
            Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "T${exercise.tier}",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(exercise.equipment ?: "Bodyweight", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { showDescriptionDialog = true }) {
                Icon(Icons.AutoMirrored.Filled.Help, "Info", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
            }

            if (FormAnalyzer.isSupported(exercise.name)) {
                IconButton(onClick = onLaunchCamera) {
                    Icon(Icons.Default.Videocam, "Camera", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
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

            Icon(
                imageVector = if (exerciseState.areSetsVisible) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
fun SetsTable(
    exerciseId: Long,
    sets: List<WorkoutSetEntity>,
    equipment: String?,
    viewModel: ActiveSessionViewModel,
    barWeight: Double,
    userGender: String
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("SET", modifier = Modifier.weight(0.5f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = Color.Gray)
            Text("LBS", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = Color.Gray)
            Text("REPS", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = Color.Gray)
            Text("RPE", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = Color.Gray)
            Text("DONE", modifier = Modifier.weight(0.5f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = Color.Gray)
        }
        sets.forEachIndexed { index, set ->
            SetRow(
                setNumber = index + 1,
                set = set,
                isBarbell = equipment?.contains("Barbell", ignoreCase = true) == true,
                viewModel = viewModel,
                barWeight = barWeight,
                userGender = userGender,
                exerciseId = exerciseId
            )
        }

        TextButton(
            onClick = { viewModel.addSet(exerciseId) },
            modifier = Modifier.align(Alignment.Start),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("ADD SET", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SetRow(
    setNumber: Int,
    set: WorkoutSetEntity,
    isBarbell: Boolean,
    viewModel: ActiveSessionViewModel,
    barWeight: Double,
    userGender: String,
    exerciseId: Long
) {
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    var weightText by remember(set.actualLbs) { mutableStateOf(set.actualLbs?.toInt()?.toString() ?: "") }
    var repsText by remember(set.actualReps) { mutableStateOf(set.actualReps?.toString() ?: "") }
    var rpeText by remember(set.actualRpe) { mutableStateOf(set.actualRpe?.toInt()?.toString() ?: "") }
    var showPlateDialog by remember { mutableStateOf(false) }

    val rowAlpha = if (set.isCompleted) 0.5f else 1f
    val backgroundColor = if (set.isCompleted) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)

    if (showPlateDialog) {
        val displayWeight = weightText.toDoubleOrNull() ?: set.actualLbs?.toDouble() ?: set.suggestedLbs.toDouble()
        PlateCalculatorDialog(displayWeight, barWeight, userGender, onDismiss = { showPlateDialog = false })
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .alpha(rowAlpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$setNumber",
            modifier = Modifier.weight(0.5f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )

        // WEIGHT FIELD
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            TextField(
                value = weightText,
                onValueChange = { weightText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            weightText = ""
                        } else {
                            if (weightText.isNotBlank()) {
                                viewModel.updateSetWeight(set, weightText)
                            } else {
                                // Restore value if blurred while empty
                                weightText = set.actualLbs?.toInt()?.toString() ?: ""
                            }
                        }
                    },
                placeholder = { Text(set.suggestedLbs.toString(), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Medium),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                colors = activeSetTextFieldColors()
            )
            if (isBarbell && !set.isCompleted) {
                IconButton(onClick = { showPlateDialog = true }, modifier = Modifier.size(24.dp).padding(start = 4.dp) ) {
                    Icon(Icons.Default.Album, "Plates", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                }
            }
        }

        // REPS FIELD
        TextField(
            value = repsText,
            onValueChange = { repsText = it },
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        repsText = ""
                    } else {
                        if (repsText.isNotBlank()) {
                            viewModel.updateSetReps(set, repsText)
                        } else {
                            repsText = set.actualReps?.toString() ?: ""
                        }
                    }
                },
            placeholder = { Text(set.suggestedReps.toString(), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Medium),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            colors = activeSetTextFieldColors()
        )

        // RPE FIELD
        TextField(
            value = rpeText,
            onValueChange = { rpeText = it },
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        rpeText = ""
                    } else {
                        if (rpeText.isNotBlank()) {
                            viewModel.updateSetRpe(set, rpeText)
                        } else {
                            rpeText = set.actualRpe?.toInt()?.toString() ?: ""
                        }
                    }
                },
            placeholder = { Text(set.suggestedRpe.toString(), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Medium),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            colors = activeSetTextFieldColors()
        )

        Box(modifier = Modifier.weight(0.5f), contentAlignment = Alignment.Center) {
            Checkbox(
                checked = set.isCompleted,
                onCheckedChange = { isChecked ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (isChecked) {
                        if (weightText.isEmpty()) viewModel.updateSetWeight(set, set.suggestedLbs.toString())
                        if (repsText.isEmpty()) viewModel.updateSetReps(set, set.suggestedReps.toString())
                        if (rpeText.isEmpty()) viewModel.updateSetRpe(set, set.suggestedRpe.toString())
                        viewModel.startSetTimer(exerciseId)
                    }
                    viewModel.updateSetCompletion(set, isChecked)
                }
            )
        }
    }
}

@Composable
fun activeSetTextFieldColors() = TextFieldDefaults.colors(
    unfocusedContainerColor = Color.Transparent,
    focusedContainerColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent
)

@Composable
fun CoachChatContent(
    chatHistory: List<ChatMessage>,
    userText: String,
    isListening: Boolean,
    onUserTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoiceInput: () -> Unit,
    onToggleLive: () -> Unit,
    onQuickAction: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val chatListState = rememberLazyListState()
    LaunchedEffect(chatHistory.size) { if (chatHistory.isNotEmpty()) chatListState.animateScrollToItem(chatHistory.size - 1) }

    Column(modifier = Modifier.fillMaxHeight(0.8f).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("AI Coach Interaction", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
        }
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f), state = chatListState, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(chatHistory) { msg ->
                val isCoach = msg.sender == "Coach"
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (isCoach) Alignment.CenterStart else Alignment.CenterEnd) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (isCoach) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (isCoach) 0.dp else 16.dp, bottomEnd = if (isCoach) 16.dp else 0.dp),
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) {
                        Text(text = msg.text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = if (isCoach) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }

        LazyRow(modifier = Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val suggestions = listOf("I'm feeling pain", "Swap this", "Need form tips", "I'm tired")
            items(suggestions) { text ->
                SuggestionChip(onClick = { onQuickAction(text) }, label = { Text(text) })
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = userText,
                onValueChange = onUserTextChange,
                placeholder = { Text("Ask your coach...") },
                modifier = Modifier.weight(1f),
                shape = CircleShape,
                leadingIcon = { IconButton(onClick = onVoiceInput) { Icon(Icons.Default.Mic, null) } },
                trailingIcon = { IconButton(onClick = onSend, enabled = userText.isNotBlank()) { Icon(Icons.Default.Send, null) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )
            Spacer(modifier = Modifier.width(12.dp))
            FloatingActionButton(onClick = onToggleLive, containerColor = if (isListening) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(56.dp), shape = CircleShape) {
                Icon(if (isListening) Icons.Default.Hearing else Icons.Default.HearingDisabled, null)
            }
        }
        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}

@Composable
fun PlateCalculatorDialog(lbs: Double, bar: Double, gender: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Plate Math: $lbs lbs") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Per Side:", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Text(PlateCalculator.calculatePlates(lbs, bar), fontSize = 32.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Includes ${bar.toInt()}lb $gender barbell", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Got it") } }
    )
}

@Composable
fun SetTimer(exerciseState: ExerciseState, viewModel: ActiveSessionViewModel) {
    val timerState = exerciseState.timerState
    if (exerciseState.sets.all { it.isCompleted }) return

    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("WORKOUT TIMER", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(
                    text = String.format(Locale.US, "%02d:%02d", timerState.remainingTime / 60, timerState.remainingTime % 60),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
            }
            Button(
                onClick = {
                    if (timerState.isRunning) viewModel.skipSetTimer(exerciseState.exercise.exerciseId)
                    else viewModel.startSetTimer(exerciseState.exercise.exerciseId)
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (timerState.isRunning) "SKIP REST" else "START TIMER", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CoachBriefingCard(briefing: String) {
    if (briefing.isBlank()) return
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = briefing, style = MaterialTheme.typography.bodyMedium, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        }
    }
}

@Composable
fun SwapExerciseDialog(alternatives: List<ExerciseEntity>, onDismiss: () -> Unit, onSwap: (Long) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Swap Exercise") },
        text = {
            if (alternatives.isEmpty()) Text("No alternatives found.")
            else Column {
                alternatives.forEach { ex ->
                    ListItem(
                        headlineContent = { Text(ex.name) },
                        supportingContent = { Text(ex.majorMuscle) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                        modifier = Modifier.clickable { onSwap(ex.exerciseId) }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddExerciseDialog(viewModel: ActiveSessionViewModel, onDismiss: () -> Unit, onExerciseSelected: (Long) -> Unit) {
    var query by remember { mutableStateOf("") }
    val exercises by viewModel.getAllExercises().collectAsState(initial = emptyList())
    val filtered = remember(query, exercises) { exercises.filter { it.name.contains(query, true) }.take(15) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Exercise") },
        text = {
            Column(modifier = Modifier.height(400.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text("Search exercises...") }, modifier = Modifier.fillMaxWidth(), shape = CircleShape)
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filtered) { ex ->
                        ListItem(
                            headlineContent = { Text(ex.name) },
                            supportingContent = { Text(ex.majorMuscle) },
                            modifier = Modifier.clickable { onExerciseSelected(ex.exerciseId) }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
