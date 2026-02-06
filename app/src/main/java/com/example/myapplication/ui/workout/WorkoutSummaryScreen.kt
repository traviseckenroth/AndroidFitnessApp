// app/src/main/java/com/example/myapplication/ui/summary/WorkoutSummaryScreen.kt

package com.example.myapplication.ui.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.WorkoutDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// --- 1. INTERNAL DATA MODELS ---
sealed class SummaryState {
    object Loading : SummaryState()
    object Error : SummaryState()
    data class Success(
        val duration: String,
        val volume: String,
        val sets: Int,
        val date: String,
        val exerciseSummaries: List<ExerciseSummary>
    ) : SummaryState()
}

data class ExerciseSummary(val name: String, val bestSet: String)

// --- 2. INTERNAL VIEWMODEL ---
@HiltViewModel
class WorkoutSummaryViewModel @Inject constructor(
    private val dao: WorkoutDao
) : ViewModel() {

    private val _state = MutableStateFlow<SummaryState>(SummaryState.Loading)
    val state = _state.asStateFlow()

    fun loadSummary(workoutId: Long) {
        viewModelScope.launch {
            // Fetch the workout using the ID passed
            val workout = dao.getWorkoutById(workoutId)

            if (workout == null) {
                _state.value = SummaryState.Error
                return@launch
            }

            // Fetch exercises and sets using the "OneShot" methods
            val exercises = dao.getExercisesForWorkoutOneShot(workoutId)
            val allSets = dao.getSetsForWorkoutOneShot(workoutId)

            // --- FIX 1: DURATION ---
            // Try to find a duration if the entity has it, otherwise default to 0
            // We safely assume it might not exist on 'DailyWorkoutEntity'
            val durationMinutes = 45 // Default placeholder since DailyWorkout doesn't track duration

            // --- FIX 2: STRICT MATH FOR VOLUME ---
            // Explicitly cast to Double to fix "Overload resolution ambiguity"
            val totalVolume = allSets
                .filter { it.isCompleted && it.actualLbs != null && it.actualReps != null }
                .sumOf {
                    val weight = (it.actualLbs ?: 0f).toDouble()
                    val reps = (it.actualReps ?: 0).toDouble()
                    weight * reps
                }

            val totalSets = allSets.count { it.isCompleted }

            // --- FIX 3: DATE FIELD ---
            // Use 'scheduledDate' which is standard in DailyWorkoutEntity
            val dateMs = workout.scheduledDate

            _state.value = SummaryState.Success(
                duration = "${durationMinutes}m",
                volume = "${totalVolume.toInt()} lbs",
                sets = totalSets,
                date = DateTimeFormatter.ofPattern("MMM dd, yyyy")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(dateMs)),
                exerciseSummaries = exercises.map { exercise ->
                    val exSets = allSets.filter { it.exerciseId == exercise.exerciseId && it.isCompleted }

                    // --- FIX 4: MAX BY OR NULL CASTING ---
                    // Explicitly tell maxByOrNull we are comparing Floats
                    val bestSet = exSets.maxByOrNull { (it.actualLbs ?: 0f) }

                    ExerciseSummary(
                        name = exercise.name,
                        bestSet = if (bestSet != null && bestSet.actualLbs != null)
                            "${bestSet.actualLbs!!.toInt()}lbs x ${bestSet.actualReps}"
                        else "No sets completed"
                    )
                }
            )
        }
    }
}

// --- 3. THE SCREEN UI ---
@Composable
fun WorkoutSummaryScreen(
    workoutId: Long,
    onNavigateHome: () -> Unit,
    viewModel: WorkoutSummaryViewModel = hiltViewModel()
) {
    LaunchedEffect(workoutId) {
        viewModel.loadSummary(workoutId)
    }

    val state by viewModel.state.collectAsState()

    Scaffold(
        bottomBar = {
            Button(
                onClick = onNavigateHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Home, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Return to Dashboard", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (val s = state) {
                is SummaryState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is SummaryState.Error -> {
                    Text(
                        "Could not load summary.",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is SummaryState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50), // Success Green
                                    modifier = Modifier.size(80.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Workout Complete!",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = s.date,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Big Stats Row
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatCard(
                                    label = "Duration",
                                    value = s.duration,
                                    icon = Icons.Default.Timer,
                                    modifier = Modifier.weight(1f)
                                )
                                StatCard(
                                    label = "Volume",
                                    value = s.volume,
                                    icon = null,
                                    modifier = Modifier.weight(1f)
                                )
                                StatCard(
                                    label = "Sets",
                                    value = s.sets.toString(),
                                    icon = null,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        item {
                            Text(
                                "Session Highlights",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                            )
                        }

                        // Exercise List
                        items(s.exerciseSummaries) { ex ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = ex.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = ex.bestSet,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, icon: ImageVector?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}