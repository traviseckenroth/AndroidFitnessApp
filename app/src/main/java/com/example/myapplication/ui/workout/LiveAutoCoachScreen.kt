// app/src/main/java/com/example/myapplication/ui/workout/LiveAutoCoachScreen.kt
package com.example.myapplication.ui.workout

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.util.AutoCoachState
import java.util.Locale

// Simple data class to hold the UI state for the live transcript animation
private data class MessageUIState(val text: String, val isListening: Boolean, val isUser: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveAutoCoachScreen(
    viewModel: ActiveSessionViewModel,
    onNavigateBack: () -> Unit
) {
    val coachState by viewModel.autoCoachState.collectAsState()
    val chatHistory by viewModel.chatHistory.collectAsState()
    val exerciseStates by viewModel.exerciseStates.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val partialTranscript by viewModel.partialTranscript.collectAsState()

    val currentExerciseState by remember(exerciseStates) {
        derivedStateOf { exerciseStates.find { state -> state.sets.any { !it.isCompleted } } }
    }
    val currentExercise = currentExerciseState?.exercise?.name ?: "Active Session"

    val currentSet by remember(currentExerciseState) {
        derivedStateOf { currentExerciseState?.sets?.find { !it.isCompleted } }
    }

    val totalSetsForCurrent = currentExerciseState?.sets?.size ?: 0
    val currentSetNumber = currentSet?.setNumber ?: 0

    val overallProgress by remember(exerciseStates) {
        derivedStateOf {
            val total = exerciseStates.sumOf { it.sets.size }
            val completed = exerciseStates.sumOf { it.sets.count { set -> set.isCompleted } }
            if (total > 0) completed.toFloat() / total else 0f
        }
    }
    val animatedProgress by animateFloatAsState(targetValue = overallProgress, label = "progress")

    val activeTimerState by remember(exerciseStates) {
        derivedStateOf {
            exerciseStates.find { it.timerState.isRunning }?.timerState
                ?: exerciseStates.find { state -> state.sets.any { !it.isCompleted } }?.timerState
        }
    }

    val visibleMessages by remember(chatHistory) {
        derivedStateOf { chatHistory.filterNot { it.text.startsWith("[SYSTEM") } }
    }

    val messageUIState by remember(isListening, partialTranscript, visibleMessages) {
        derivedStateOf {
            if (isListening && partialTranscript.isNotBlank()) {
                MessageUIState("\"$partialTranscript...\"", isListening = true, isUser = true)
            } else {
                val lastMessage = visibleMessages.lastOrNull()?.text ?: "I'm your Auto Coach. Let's get started."
                val isUser = visibleMessages.lastOrNull()?.sender == "User"
                MessageUIState(lastMessage, isListening = false, isUser = isUser)
            }
        }
    }

    var hasStarted by remember { mutableStateOf(false) }
    var showVoiceMenu by remember { mutableStateOf(false) }
    var selectedVoiceName by remember { mutableStateOf("Bella (Warm Female)") }

    val kokoroVoices = remember {
        mapOf(
            "Bella (Warm Female)" to 2,
            "Heart (Premium Female)" to 3,
            "Nicole (Pro Female)" to 6,
            "Sarah (Mature Female)" to 9,
            "Adam (Male)" to 11,
            "Michael (Deep Male)" to 16,
            "Onyx (Gritty Male)" to 17
        )
    }

    LaunchedEffect(coachState) {
        if (coachState != AutoCoachState.OFF) hasStarted = true
        if (hasStarted && coachState == AutoCoachState.OFF) onNavigateBack()
    }

    val handleClose = {
        if (coachState != AutoCoachState.OFF) viewModel.toggleAutoCoach() else onNavigateBack()
    }

    val primaryColor by animateColorAsState(
        targetValue = when {
            isListening -> Color(0xFF34A853)
            coachState == AutoCoachState.SPEAKING -> Color(0xFF4285F4)
            coachState == AutoCoachState.LISTENING -> Color(0xFF34A853)
            coachState == AutoCoachState.SET_IN_PROGRESS -> Color(0xFFEA4335)
            coachState == AutoCoachState.RESTING -> Color(0xFFFBBC05) // Fixed: Removed WAITING_FOR_READY
            else -> Color(0xFF9AA0A6)
        },
        animationSpec = tween(800), label = "PrimaryColor"
    )

    val secondaryColor by animateColorAsState(
        targetValue = when {
            isListening -> Color(0xFF1E8E3E)
            coachState == AutoCoachState.SPEAKING -> Color(0xFFD96570)
            coachState == AutoCoachState.LISTENING -> Color(0xFF1E8E3E)
            coachState == AutoCoachState.SET_IN_PROGRESS -> Color(0xFFB31412)
            coachState == AutoCoachState.RESTING -> Color(0xFFF29900) // Fixed: Removed WAITING_FOR_READY
            else -> Color(0xFF5F6368)
        },
        animationSpec = tween(800), label = "SecondaryColor"
    )

    Scaffold(
        containerColor = Color(0xFF0B0F19),
        topBar = {
            TopAppBar(
                title = { Text("Auto Coach", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp) },
                navigationIcon = {
                    IconButton(onClick = handleClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    Box {
                        val currentSid by viewModel.selectedVoiceSid.collectAsState()
                        val currentVoiceName = kokoroVoices.entries.find { it.value == currentSid }?.key ?: selectedVoiceName

                        TextButton(onClick = { showVoiceMenu = true }) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = "Voice", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(currentVoiceName, color = Color.White, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                        }

                        DropdownMenu(
                            expanded = showVoiceMenu,
                            onDismissRequest = { showVoiceMenu = false },
                            modifier = Modifier.background(Color(0xFF1E2027))
                        ) {
                            kokoroVoices.forEach { (voiceName, sidInteger) ->
                                DropdownMenuItem(
                                    text = { Text(voiceName, color = Color.White, fontWeight = FontWeight.Medium) },
                                    onClick = {
                                        viewModel.setCoachVoice(sidInteger)
                                        selectedVoiceName = voiceName
                                        showVoiceMenu = false
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Box(modifier = Modifier.fillMaxSize().alpha(0.3f)) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(400.dp).align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, primaryColor.copy(alpha = 0.5f))))
                )
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = primaryColor,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = currentExercise.uppercase(),
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center
                )

                if (totalSetsForCurrent > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SetProgressIndicator(totalSets = totalSetsForCurrent, currentSet = currentSetNumber, activeColor = primaryColor)
                }

                if (currentSet != null) {
                    Row(
                        modifier = Modifier.padding(top = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TargetStat(label = "REPS", value = currentSet?.suggestedReps?.toString() ?: "0")
                        TargetStat(label = "LBS", value = currentSet?.suggestedLbs?.toString() ?: "0")
                        TargetStat(label = "RPE", value = currentSet?.suggestedRpe?.toString() ?: "0")
                    }
                }

                Spacer(modifier = Modifier.weight(0.5f))

                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(320.dp)) {
                    GeminiLiveOrb(primaryColor = primaryColor, secondaryColor = secondaryColor, state = coachState, isListening = isListening)

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Fixed: Removed WAITING_FOR_READY check
                        if (activeTimerState != null && (coachState == AutoCoachState.SET_IN_PROGRESS || coachState == AutoCoachState.RESTING) && !isListening) {
                            val displayMinutes = activeTimerState!!.remainingTime / 60
                            val displaySeconds = activeTimerState!!.remainingTime % 60
                            val timeString = String.format(Locale.US, "%02d:%02d", displayMinutes, displaySeconds)

                            Text(
                                text = timeString,
                                color = Color.White,
                                fontSize = 72.sp,
                                fontWeight = FontWeight.Light,
                                style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 20f))
                            )
                            Text(
                                text = when(coachState) {
                                    AutoCoachState.SET_IN_PROGRESS -> "SET IN PROGRESS"
                                    AutoCoachState.RESTING -> "REST PERIOD"
                                    else -> "" // Fixed: Removed WAITING FOR READY
                                },
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 3.sp
                            )
                        } else {
                            Icon(
                                imageVector = if (coachState == AutoCoachState.LISTENING || isListening) Icons.Default.Mic else Icons.Default.AutoAwesome,
                                contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(0.5f))

                val state = messageUIState
                Text(
                    text = state.text,
                    color = if (state.isListening) Color.White.copy(alpha = 0.8f) else if (state.isUser) Color.White.copy(alpha = 0.6f) else Color.White,
                    fontSize = if (state.isListening) 18.sp else 20.sp,
                    fontStyle = if (state.isListening) FontStyle.Italic else FontStyle.Normal,
                    fontWeight = if (state.isUser || state.isListening) FontWeight.Normal else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    lineHeight = 28.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ControlIconButton(
                        icon = Icons.Default.Close,
                        onClick = handleClose,
                        containerColor = Color.White.copy(alpha = 0.15f), iconColor = Color.White
                    )

                    LargeControlIconButton(
                        icon = if (coachState == AutoCoachState.LISTENING || isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                        onClick = {
                            // Fixed: Removed notifyUserReady logic
                            viewModel.toggleLiveCoaching()
                        },
                        containerColor = primaryColor, iconColor = Color.White,
                        isPulsing = isListening // Fixed: Removed WAITING_FOR_READY check
                    )

                    ControlIconButton(
                        icon = Icons.Default.SkipNext,
                        onClick = {
                            val activeExerciseId = exerciseStates.find { state -> state.sets.any { !it.isCompleted } }?.exercise?.exerciseId
                            if (activeExerciseId != null) viewModel.skipSetTimer(activeExerciseId)
                        },
                        containerColor = Color.White.copy(alpha = 0.15f), iconColor = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun SetProgressIndicator(totalSets: Int, currentSet: Int, activeColor: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..totalSets) {
            val isCompleted = i < currentSet
            val isCurrent = i == currentSet
            val dotColor = if (isCompleted || isCurrent) activeColor else Color.White.copy(alpha = 0.2f)

            val infiniteTransition = rememberInfiniteTransition(label = "dot_pulse_$i")
            val scale by infiniteTransition.animateFloat(
                initialValue = if (isCurrent) 0.8f else 1f, targetValue = if (isCurrent) 1.2f else 1f,
                animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dot_scale_$i"
            )

            Box(
                modifier = Modifier
                    .size(if (isCurrent) 12.dp else 8.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(dotColor, CircleShape)
            )
        }
    }
}

@Composable
fun TargetStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
    }
}

@Composable
fun GeminiLiveOrb(primaryColor: Color, secondaryColor: Color, state: AutoCoachState, isListening: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "OrbTransition")
    val duration = if (state == AutoCoachState.SPEAKING || state == AutoCoachState.LISTENING || isListening) 3000 else 8000

    val rotation1 by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(tween(duration, easing = LinearEasing)), label = "Rotation1")
    val rotation2 by infiniteTransition.animateFloat(initialValue = 360f, targetValue = 0f, animationSpec = infiniteRepeatable(tween(duration + 2000, easing = LinearEasing)), label = "Rotation2")
    val scale by infiniteTransition.animateFloat(initialValue = 0.95f, targetValue = if (state == AutoCoachState.SPEAKING || state == AutoCoachState.LISTENING || isListening) 1.1f else 0.95f, animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "Scale")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.size(240.dp).background(Brush.radialGradient(listOf(primaryColor.copy(alpha = 0.4f), Color.Transparent))))

        Box(
            modifier = Modifier
                .size(200.dp)
                .graphicsLayer {
                    rotationZ = rotation1
                    translationX = 20.dp.toPx()
                }
                .background(Brush.radialGradient(listOf(primaryColor.copy(alpha = 0.6f), Color.Transparent)))
        )

        Box(
            modifier = Modifier
                .size(220.dp)
                .graphicsLayer {
                    rotationZ = rotation2
                    translationY = 20.dp.toPx()
                }
                .background(Brush.radialGradient(listOf(secondaryColor.copy(alpha = 0.5f), Color.Transparent)))
        )

        Box(modifier = Modifier.size(180.dp).border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape).background(Color.Black.copy(alpha = 0.3f), CircleShape))
    }
}

@Composable
fun ControlIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, containerColor: Color, iconColor: Color) {
    Surface(onClick = onClick, shape = CircleShape, color = containerColor, modifier = Modifier.size(56.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun LargeControlIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, containerColor: Color, iconColor: Color, isPulsing: Boolean = false) {
    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
    val scale by infiniteTransition.animateFloat(initialValue = 1f, targetValue = if (isPulsing) 1.15f else 1f, animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ButtonPulse")

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        modifier = Modifier
            .size(80.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shadowElevation = if (isPulsing) 12.dp else 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(36.dp))
        }
    }
}