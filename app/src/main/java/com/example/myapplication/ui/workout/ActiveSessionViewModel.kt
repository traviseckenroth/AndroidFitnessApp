package com.example.myapplication.ui.workout

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.local.WorkoutSetEntity
import com.example.myapplication.data.remote.BedrockClient
import com.example.myapplication.data.repository.HealthConnectManager
import com.example.myapplication.data.repository.WorkoutRepository
import com.example.myapplication.service.WorkoutTimerService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class ExerciseTimerState(
    val remainingTime: Long = 0L,
    val isRunning: Boolean = false,
    val isFinished: Boolean = false
)

data class ExerciseState(
    val exercise: ExerciseEntity,
    val sets: List<WorkoutSetEntity>,
    val timerState: ExerciseTimerState,
    val areSetsVisible: Boolean = true
)

@HiltViewModel
class ActiveSessionViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    private val application: Application,
    private val healthConnectManager: HealthConnectManager,
    val bedrockClient: BedrockClient
) : ViewModel() {

    private val _workoutId = MutableStateFlow<Long>(-1)
    private val _sets = MutableStateFlow<List<WorkoutSetEntity>>(emptyList())
    private val _exercises = MutableStateFlow<List<ExerciseEntity>>(emptyList())
    private val _exerciseStates = MutableStateFlow<List<ExerciseState>>(emptyList())
    val exerciseStates: StateFlow<List<ExerciseState>> = _exerciseStates

    private val _coachBriefing = MutableStateFlow("Loading briefing...")
    val coachBriefing: StateFlow<String> = _coachBriefing

    private val _workoutSummary = MutableStateFlow<List<String>?>(null)
    val workoutSummary: StateFlow<List<String>?> = _workoutSummary

    // Track workout start time for Health Connect
    private var workoutStartTime: Instant = Instant.now()

    init {
        workoutStartTime = Instant.now()

        // --- LISTEN TO TIMER SERVICE ---
        viewModelScope.launch {
            WorkoutTimerService.timerState.collect { serviceState ->
                // FIX: Trigger strictly on 'hasFinished' state to avoid race conditions
                if (serviceState.hasFinished && serviceState.activeExerciseId != null) {
                    val exerciseId = serviceState.activeExerciseId!!
                    val currentState = _exerciseStates.value.find { it.exercise.exerciseId == exerciseId }
                    val currentSet = currentState?.sets?.firstOrNull { !it.isCompleted }

                    if (currentSet != null) {
                        updateSetCompletion(currentSet, true)
                        // Immediate restart - no delay needed as Service is still alive
                        startSetTimer(exerciseId)
                    }
                }

                updateLocalTimerState(
                    activeExerciseId = serviceState.activeExerciseId,
                    remainingTime = serviceState.remainingTime.toLong(),
                    isRunning = serviceState.isRunning
                )
            }
        }

        // --- LOAD DATA ---
        viewModelScope.launch {
            _workoutId.collectLatest { id ->
                if (id != -1L) {
                    repository.getSetsForSession(id).collect { loadedSets ->
                        val filledSets = loadedSets.map { set ->
                            set.copy(
                                actualReps = if (set.actualReps == null || set.actualReps == 0) set.suggestedReps else set.actualReps,
                                actualLbs = if (set.actualLbs == null || set.actualLbs == 0f) set.suggestedLbs.toFloat() else set.actualLbs,
                                actualRpe = if (set.actualRpe == null || set.actualRpe == 0.0f) set.suggestedRpe.toFloat() else set.actualRpe
                            )
                        }
                        _sets.value = filledSets
                    }
                }
            }
        }

        viewModelScope.launch {
            _sets.collectLatest { sets ->
                if (sets.isNotEmpty()) {
                    val exerciseIds = sets.map { it.exerciseId }.distinct()
                    repository.getExercisesByIds(exerciseIds).collect {
                        _exercises.value = it
                    }
                } else {
                    _exercises.value = emptyList()
                }
            }
        }

        viewModelScope.launch {
            combine(_exercises, _sets) { allExercises, sessionSets ->
                val sessionExerciseIds = sessionSets.map { it.exerciseId }.toSet()
                val sessionExercises = allExercises
                    .filter { it.exerciseId in sessionExerciseIds }
                    .sortedBy { it.tier }

                if (sessionExercises.isNotEmpty() && sessionSets.isNotEmpty()) {
                    _coachBriefing.value = generateCoachBriefing(sessionExercises, sessionSets)
                }

                val setsByExercise = sessionSets.groupBy { it.exerciseId }

                sessionExercises.mapNotNull { exercise ->
                    setsByExercise[exercise.exerciseId]?.let { exerciseSets ->
                        val existingState = _exerciseStates.value.find { it.exercise.exerciseId == exercise.exerciseId }
                        ExerciseState(
                            exercise = exercise,
                            sets = exerciseSets.sortedBy { it.setNumber },
                            timerState = existingState?.timerState ?: ExerciseTimerState(
                                remainingTime = (exercise.estimatedTimePerSet * 60).toLong()
                            ),
                            areSetsVisible = existingState?.areSetsVisible ?: true
                        )
                    }
                }
            }.collect { newStates ->
                if (newStates.isNotEmpty() || _sets.value.isEmpty()) {
                    _exerciseStates.value = newStates
                }
            }
        }
    }

    // --- STANDARD OPERATIONS ---

    fun loadWorkout(workoutId: Long) { _workoutId.value = workoutId }

    fun updateSetCompletion(set: WorkoutSetEntity, isCompleted: Boolean) {
        viewModelScope.launch { repository.updateSet(set.copy(isCompleted = isCompleted)) }
    }

    fun updateSetReps(set: WorkoutSetEntity, newReps: String) {
        val repsInt = newReps.toIntOrNull() ?: return
        viewModelScope.launch { repository.updateSet(set.copy(actualReps = repsInt)) }
    }

    fun updateSetWeight(set: WorkoutSetEntity, newLbs: String) {
        val lbsFloat = newLbs.toFloatOrNull() ?: return
        viewModelScope.launch { repository.updateSet(set.copy(actualLbs = lbsFloat)) }
    }

    fun updateSetRpe(set: WorkoutSetEntity, newRpe: String) {
        val rpeFloat = newRpe.toFloatOrNull() ?: return
        viewModelScope.launch { repository.updateSet(set.copy(actualRpe = rpeFloat)) }
    }

    fun swapExercise(oldExerciseId: Long, newExerciseId: Long) {
        viewModelScope.launch { repository.swapExercise(_workoutId.value, oldExerciseId, newExerciseId) }
    }

    suspend fun getTopAlternatives(exercise: ExerciseEntity): List<ExerciseEntity> {
        return repository.getBestAlternatives(exercise)
    }

    fun toggleExerciseVisibility(exerciseId: Long) {
        _exerciseStates.value = _exerciseStates.value.map {
            if (it.exercise.exerciseId == exerciseId) it.copy(areSetsVisible = !it.areSetsVisible) else it
        }
    }

    fun addWarmUpSets(exerciseId: Long, workingWeight: Int) {
        viewModelScope.launch {
            val workoutId = _workoutId.value
            if (workoutId != -1L) {
                repository.injectWarmUpSets(workoutId, exerciseId, workingWeight)
            }
        }
    }

    // --- TIMER LOGIC (Using Foreground Service) ---

    fun startSetTimer(exerciseId: Long) {
        val exerciseState = _exerciseStates.value.find { it.exercise.exerciseId == exerciseId } ?: return
        val durationSeconds = (exerciseState.exercise.estimatedTimePerSet * 60).toInt()

        val intent = Intent(application, WorkoutTimerService::class.java).apply {
            action = WorkoutTimerService.ACTION_START
            putExtra(WorkoutTimerService.EXTRA_SECONDS, durationSeconds)
            putExtra(WorkoutTimerService.EXTRA_EXERCISE_ID, exerciseId)
        }
        application.startService(intent)
    }

    fun skipSetTimer(exerciseId: Long) {
        // 1. Mark current set as Done
        val currentState = _exerciseStates.value.find { it.exercise.exerciseId == exerciseId }
        val currentSet = currentState?.sets?.firstOrNull { !it.isCompleted }
        if (currentSet != null) {
            updateSetCompletion(currentSet, true)
        }

        // 2. Restart the timer immediately (Skip Rest)
        startSetTimer(exerciseId)
    }

    private fun updateLocalTimerState(activeExerciseId: Long?, remainingTime: Long, isRunning: Boolean) {
        _exerciseStates.value = _exerciseStates.value.map { state ->
            if (state.exercise.exerciseId == activeExerciseId) {
                state.copy(timerState = ExerciseTimerState(
                    remainingTime = remainingTime,
                    isRunning = isRunning,
                    isFinished = !isRunning && remainingTime == 0L
                ))
            } else {
                val defaultDuration = (state.exercise.estimatedTimePerSet * 60).toLong()
                state.copy(timerState = ExerciseTimerState(
                    remainingTime = defaultDuration,
                    isRunning = false,
                    isFinished = false
                ))
            }
        }
    }

    // --- FINISH WORKOUT (Database + Health Connect) ---

    fun finishWorkout(workoutId: Long) {
        skipSetTimer(-1) // Stop Service

        viewModelScope.launch {
            // 1. Save to Local DB & Generate Report
            val report = repository.completeWorkout(workoutId)
            _workoutSummary.value = report

            // 2. Health Connect Sync
            val endTime = Instant.now()
            val durationMin = ChronoUnit.MINUTES.between(workoutStartTime, endTime)
            val estimatedCalories = (durationMin * 4.5).coerceAtLeast(10.0) // ~4.5 kcal/min estimate

            try {
                if (healthConnectManager.hasPermissions()) {
                    healthConnectManager.writeWorkout(
                        workoutId = workoutId,
                        startTime = workoutStartTime,
                        endTime = endTime,
                        calories = estimatedCalories,
                        title = "Strength Workout"
                    )
                } else {
                    Log.d("Workout", "Health Connect permissions missing. Workout saved locally only.")
                }
            } catch (e: Exception) {
                Log.e("Workout", "Failed to sync with Health Connect", e)
            }
        }
    }

    fun clearSummary() {
        _workoutSummary.value = null
    }
}

private fun generateCoachBriefing(exercises: List<ExerciseEntity>, sets: List<WorkoutSetEntity>): String {
    if (exercises.isEmpty() || sets.isEmpty()) return "Ready to start your workout?"
    val tier1Count = exercises.count { it.tier == 1 }
    val avgReps = if (sets.isNotEmpty()) sets.map { it.suggestedReps }.average() else 0.0
    val totalSets = sets.size

    val dominantMuscle = exercises.groupingBy { it.muscleGroup }
        .eachCount()
        .maxByOrNull { it.value }?.key ?: "Full Body"

    return when {
        tier1Count >= 2 && avgReps < 8 -> {
            "Mission: Heavy $dominantMuscle Day.\nIntensity is key today, not speed."
        }
        totalSets > 18 -> {
            "Mission: High Volume $dominantMuscle.\nFocus on surviving the burn and keeping form strict."
        }
        else -> {
            "Mission: $dominantMuscle Hypertrophy.\nFocus on the mind-muscle connection."
        }
    }
}
