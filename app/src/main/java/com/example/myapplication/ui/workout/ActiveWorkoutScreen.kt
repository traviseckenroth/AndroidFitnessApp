package com.example.myapplication.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.ui.plan.PlanUiState
import com.example.myapplication.ui.plan.PlanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    workoutId: Long,
    planViewModel: PlanViewModel,
    workoutViewModel: WorkoutViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val planState by planViewModel.uiState.collectAsState()
    val workoutState by workoutViewModel.uiState.collectAsState()

    // Load data when screen opens
    LaunchedEffect(planState, workoutId) {
        if (planState is PlanUiState.Success) {
            val plan = (planState as PlanUiState.Success).plan

            // Search all weeks to find the workout that matches the ID
            val foundWorkout = plan.weeks.flatMap { week ->
                week.days.map { day ->
                    // REGENERATE THE ID exactly how HomeScreen generated it
                    val id = "${week.week}-${day.day}".hashCode().toLong()
                    id to day
                }
            }.find { it.first == workoutId }?.second

            foundWorkout?.let {
                workoutViewModel.loadWorkout(it)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(workoutState.workoutTitle) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (workoutState.exercises.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (workoutState.workoutFinished) {
            WorkoutFinishedScreen(onNavigateBack)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    workoutState.exercises.getOrNull(workoutState.currentExerciseIndex)?.let { exercise ->
                        ExerciseHeader(workoutState, exercise)
                        Spacer(modifier = Modifier.height(16.dp))
                        ExerciseTimer(workoutState, workoutViewModel)
                        Spacer(modifier = Modifier.height(16.dp))
                        SetsTable(exercise, workoutState.currentExerciseIndex, workoutViewModel)
                        Spacer(modifier = Modifier.height(24.dp))
                        NextExercises(workoutState.exercises, workoutState.currentExerciseIndex)
                        Spacer(modifier = Modifier.height(24.dp))
                        NavigationButtons(workoutViewModel, onNavigateBack)
                    }
                }
            }
        }
    }
}


@Composable
fun ExerciseHeader(uiState: WorkoutUiState, exercise: Exercise) {
    Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
        Text("EXERCISE ${uiState.currentExerciseIndex + 1} OF ${uiState.exercises.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Text(exercise.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold)
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
    }
}

@Composable
fun ExerciseTimer(uiState: WorkoutUiState, viewModel: WorkoutViewModel) {
    val minutes = uiState.timerValue / 60
    val seconds = uiState.timerValue % 60
    val currentExercise = uiState.exercises[uiState.currentExerciseIndex]

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "Set ${uiState.currentSet} of ${currentExercise.sets.size}", fontSize = 20.sp)
        Text(text = String.format("%02d:%02d", minutes, seconds), fontSize = 48.sp, fontWeight = FontWeight.Bold)

        if (uiState.isExerciseComplete) {
            Text("Exercise Complete", fontSize = 20.sp)
        } else if (uiState.isTimerRunning) {
            OutlinedButton(onClick = { viewModel.skipTimer() }) {
                Text("Skip Rest")
            }
        } else {
            Button(onClick = { viewModel.startTimer() }) {
                Text("Start Timer")
            }
        }
    }
}

@Composable
fun SetsTable(exercise: Exercise, exerciseIndex: Int, viewModel: WorkoutViewModel) {
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
        exercise.sets.forEachIndexed { index, set ->
            SetRow(set = set, isCurrent = index == 0, exerciseIndex, index, viewModel)
        }
    }
}

@Composable
fun SetRow(set: WorkoutSet, isCurrent: Boolean, exerciseIndex: Int, setIndex: Int, viewModel: WorkoutViewModel) {
    val backgroundColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val borderColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, shape = RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, shape = RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(set.setNumber.toString(), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        TextField(value = set.lbs, onValueChange = { viewModel.onSetFieldChange(exerciseIndex, setIndex, "lbs", it) }, modifier = Modifier.weight(1f).width(50.dp), colors = TextFieldDefaults.colors(unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant))
        TextField(value = set.reps, onValueChange = { viewModel.onSetFieldChange(exerciseIndex, setIndex, "reps", it) }, modifier = Modifier.weight(1f).width(50.dp),  colors = TextFieldDefaults.colors(unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant))
        TextField(value = set.rpe, onValueChange = { viewModel.onSetFieldChange(exerciseIndex, setIndex, "rpe", it) }, modifier = Modifier.weight(1f).width(50.dp),  colors = TextFieldDefaults.colors(unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant))
        Checkbox(checked = set.isDone, onCheckedChange = { viewModel.onSetDone(exerciseIndex, setIndex, it) }, modifier = Modifier.weight(1f))
    }
}

@Composable
fun NextExercises(exercises: List<Exercise>, currentIndex: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        exercises.drop(currentIndex + 1).forEachIndexed { index, exercise ->
            ExerciseItem(number = currentIndex + index + 2, name = exercise.name)
        }
    }
}

@Composable
fun ExerciseItem(number: Int, name: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$number.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(16.dp))
            Text(name, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun NavigationButtons(viewModel: WorkoutViewModel, onNavigateBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val isLastExercise = uiState.currentExerciseIndex == uiState.exercises.size - 1

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Button(onClick = { viewModel.previousExercise() }) {
            Text("Previous")
        }
        if (isLastExercise) {
            Button(onClick = {
                viewModel.finishWorkout()
                onNavigateBack()
            }) {
                Text("Finish Workout")
            }
        } else {
            Button(onClick = { viewModel.nextExercise() }) {
                Text("Next")
            }
        }
    }
}

@Composable
fun WorkoutFinishedScreen(onNavigateBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Workout Finished!", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onNavigateBack) {
                Text("Back to Home")
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
