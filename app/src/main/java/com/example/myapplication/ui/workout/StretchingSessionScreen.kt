// File: app/src/main/java/com/example/myapplication/ui/workout/StretchingSessionScreen.kt
package com.example.myapplication.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.local.WorkoutSetEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StretchingSessionScreen(
    workoutId: Long,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    viewModel: ActiveSessionViewModel = hiltViewModel()
) {
    LaunchedEffect(workoutId) { viewModel.loadWorkout(workoutId) }

    val exerciseStates by viewModel.exerciseStates.collectAsState()

    // Determine the current active set (first incomplete set)
    val activeState = exerciseStates.find { state -> state.sets.any { !it.isCompleted } }
    val activeSet = activeState?.sets?.find { !it.isCompleted }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Active Mobility", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (exerciseStates.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (activeState == null || activeSet == null) {
                // All sets complete
                SessionCompleteView(onFinish = {
                    viewModel.finishWorkout(workoutId)
                    onComplete()
                })
            } else {
                ActiveStretchView(
                    exercise = activeState.exercise,
                    set = activeSet,
                    timerState = activeState.timerState,
                    onStartHold = { viewModel.startSetTimer(activeState.exercise.exerciseId) },
                    onSkip = { viewModel.updateSetCompletion(activeSet, true) },
                    onCompleteSet = { viewModel.updateSetCompletion(activeSet, true) }
                )
            }
        }
    }
}

@Composable
fun ActiveStretchView(
    exercise: ExerciseEntity,
    set: WorkoutSetEntity,
    timerState: ExerciseTimerState,
    onStartHold: () -> Unit,
    onSkip: () -> Unit,
    onCompleteSet: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Exercise Header
        Text(
            text = exercise.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        val sideInNameOrNotes = exercise.name.contains("left", ignoreCase = true) ||
                exercise.name.contains("right", ignoreCase = true) ||
                exercise.notes.contains("left side", ignoreCase = true) ||
                exercise.notes.contains("right side", ignoreCase = true)

        val isUnilateral = !sideInNameOrNotes && (
                exercise.notes.contains("side", ignoreCase = true) ||
                        exercise.name.contains("single", ignoreCase = true)
                )

        if (isUnilateral) {
            val side = if (set.setNumber % 2 != 0) "LEFT SIDE" else "RIGHT SIDE"
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text(
                    text = side,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 3. Timer Visualization
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
            CircularProgressIndicator(
                progress = {
                    val total = (exercise.estimatedTimePerSet * 60).toFloat()
                    if (total > 0) timerState.remainingTime.toFloat() / total else 0f
                },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 8.dp,
                color = if (timerState.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatTime(timerState.remainingTime),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (timerState.isRunning) "HOLDING" else "READY",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 4. Instructions Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("How to perform", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = exercise.description.ifBlank { exercise.notes }.ifBlank { "Focus on deep breathing and relax into the stretch." },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 5. Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.SkipNext, null)
                Spacer(Modifier.width(8.dp))
                Text("Skip")
            }

            if (!timerState.isRunning && !timerState.isFinished) {
                Button(
                    onClick = onStartHold,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start Hold")
                }
            } else if (timerState.isFinished || timerState.remainingTime == 0L) {
                Button(
                    onClick = onCompleteSet,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Complete Set")
                }
            }
        }
    }
}

@Composable
fun SessionCompleteView(onFinish: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🧘", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text("Mobility Complete", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("You've successfully completed your restorative flow.", textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
            Text("FINISH SESSION")
        }
    }
}

private fun formatTime(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}