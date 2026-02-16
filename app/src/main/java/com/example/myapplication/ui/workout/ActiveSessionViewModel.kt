package com.example.myapplication.ui.workout

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.local.WorkoutEntity
import com.example.myapplication.data.local.WorkoutSetEntity
import com.example.myapplication.data.local.UserPreferencesRepository
import com.example.myapplication.data.remote.BedrockClient
import com.example.myapplication.data.repository.HealthConnectManager
import com.example.myapplication.data.repository.WorkoutExecutionRepository
import com.example.myapplication.service.TimerState
import com.example.myapplication.service.WorkoutTimerService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

// --- State Classes ---

data class ExerciseTimerState(
    val remainingTime: Long = 0L,
    val isRunning: Boolean = false,
    val isFinished: Boolean = false
)

data class ExerciseState(
    val exercise: ExerciseEntity,
    val sets: List<WorkoutSetEntity>,
    val timerState: ExerciseTimerState = ExerciseTimerState(),
    val areAllSetsCompleted: Boolean = false
)

data class WorkoutSummary(
    val completedId: Long,
    val totalVolume: Double,
    val durationMs: Long
)

data class ActiveSessionUIState(
    val isLoading: Boolean = true,
    val workout: WorkoutEntity? = null,
    val exercises: List<ExerciseState> = emptyList(),
    val isTimerRunning: Boolean = false,
    val activeSetId: Long? = null,
    val workoutSummary: WorkoutSummary? = null,
    // Bio-Sync Flag
    val showRecoveryDialog: Boolean = false
)

@HiltViewModel
class ActiveSessionViewModel @Inject constructor(
    private val repository: WorkoutExecutionRepository,
    private val healthConnectManager: HealthConnectManager,
    private val userPrefs: UserPreferencesRepository,
    private val bedrockClient: BedrockClient,
    private val application: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActiveSessionUIState())
    val uiState: StateFlow<ActiveSessionUIState> = _uiState.asStateFlow()

    private val _workoutSummary = MutableStateFlow<WorkoutSummary?>(null)
    val workoutSummary: StateFlow<WorkoutSummary?> = _workoutSummary.asStateFlow()

    init {
        // Listen to Timer Service Broadcasts
        viewModelScope.launch {
            WorkoutTimerService.timerState.collectLatest { timerState ->
                val setId = timerState.activeExerciseId ?: -1L
                val remaining = timerState.remainingTime.toLong()

                if (timerState.isRunning) {
                    updateTimerState(setId, remaining, isRunning = true)
                } else if (timerState.hasFinished) {
                    updateTimerState(setId, 0, isRunning = false, isFinished = true)
                } else {
                    updateTimerState(-1L, 0, isRunning = false, isFinished = false)
                }
            }
        }
    }

    private fun updateTimerState(setId: Long, remaining: Long, isRunning: Boolean, isFinished: Boolean = false) {
        _uiState.update { currentState ->
            val updatedExercises = currentState.exercises.map { exerciseState ->
                val hasRunningSet = exerciseState.sets.any { it.setId == setId }
                if (hasRunningSet) {
                    exerciseState.copy(
                        timerState = ExerciseTimerState(remaining, isRunning, isFinished)
                    )
                } else {
                    exerciseState
                }
            }
            currentState.copy(
                exercises = updatedExercises,
                isTimerRunning = isRunning,
                activeSetId = if (isRunning) setId else null
            )
        }
    }

    fun loadWorkout(workoutId: Long) {
        viewModelScope.launch {
            // FIXED: Now calls the method added to Repository
            val workout = repository.getWorkoutById(workoutId)
            val setsFlow = repository.getSetsForSession(workoutId)
            val exercisesFlow = repository.getAllExercises()

            if (workout != null) {
                // Bio-Sync Check: explicit null check handled by enclosing if
                val isRecovery = workout.name.startsWith("Recovery:")

                combine(setsFlow, exercisesFlow) { sets, allExercises ->
                    val workoutExercises = sets.map { it.exerciseId }.distinct()

                    workoutExercises.mapNotNull { exerciseId ->
                        val exercise = allExercises.find { it.exerciseId == exerciseId }
                        val exerciseSets = sets.filter { it.exerciseId == exerciseId }.sortedBy { it.setNumber }

                        if (exercise != null) {
                            ExerciseState(
                                exercise = exercise,
                                sets = exerciseSets,
                                areAllSetsCompleted = exerciseSets.all { it.isCompleted }
                            )
                        } else null
                    }
                }.collect { exerciseStates ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            workout = workout,
                            exercises = exerciseStates,
                            showRecoveryDialog = isRecovery
                        )
                    }
                }
            }
        }
    }

    // --- Actions ---

    fun startSet(setId: Long, durationSeconds: Long) {
        val intent = Intent(application, WorkoutTimerService::class.java).apply {
            action = WorkoutTimerService.ACTION_START
            putExtra(WorkoutTimerService.EXTRA_EXERCISE_ID, setId)
            putExtra(WorkoutTimerService.EXTRA_SECONDS, durationSeconds.toInt())
        }
        application.startService(intent)
    }

    fun finishSet(setId: Long) {
        val intent = Intent(application, WorkoutTimerService::class.java).apply {
            action = WorkoutTimerService.ACTION_STOP
        }
        application.startService(intent)
    }

    fun updateSet(set: WorkoutSetEntity) {
        viewModelScope.launch {
            repository.updateSet(set)
        }
    }

    fun updateSetCompletion(set: WorkoutSetEntity, isCompleted: Boolean) {
        viewModelScope.launch {
            repository.updateSet(set.copy(isCompleted = isCompleted))
        }
    }

    fun saveWorkout(workoutId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            repository.completeWorkout(workoutId)

            val allSets = _uiState.value.exercises.flatMap { it.sets }.filter { it.isCompleted }

            // FIXED: Type ambiguity resolution
            val totalVolume: Double = allSets.sumOf { set ->
                val weight = set.actualLbs ?: 0.0
                val reps = (set.actualReps ?: 0).toDouble()
                weight * reps
            }

            _workoutSummary.value = WorkoutSummary(
                completedId = workoutId,
                totalVolume = totalVolume,
                durationMs = 3600000L
            )

            try {
                if (healthConnectManager.hasPermissions()) {
                    val workoutStartTime = Instant.now().minus(1, ChronoUnit.HOURS)
                    val endTime = Instant.now()
                    healthConnectManager.writeWorkout(
                        workoutId = workoutId,
                        startTime = workoutStartTime,
                        endTime = endTime,
                        calories = 300.0,
                        title = uiState.value.workout?.name ?: "Strength Workout"
                    )
                }
            } catch (e: Exception) {
                Log.e("Workout", "Sync error", e)
            }
        }
    }

    fun clearSummary() {
        _workoutSummary.value = null
    }

    suspend fun generateCoachingCue(exerciseName: String, issue: String): String {
        return bedrockClient.generateCoachingCue(exerciseName, issue, 0)
    }

    // --- Bio-Sync Actions ---

    fun applyRecoveryAdjustment() {
        viewModelScope.launch {
            val currentExercises = _uiState.value.exercises
            currentExercises.forEach { exerciseState ->
                exerciseState.sets.forEach { set ->
                    val newReps = (set.suggestedReps * 0.7).toInt().coerceAtLeast(1)
                    repository.updateSet(set.copy(suggestedReps = newReps))
                }
            }
            _uiState.update { it.copy(showRecoveryDialog = false) }
        }
    }

    fun dismissRecoveryDialog() {
        _uiState.update { it.copy(showRecoveryDialog = false) }
    }
}