package com.example.myapplication.ui.workout

import android.app.Application
import android.content.Intent // <--- NEW IMPORTS
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.local.WorkoutSetEntity
import com.example.myapplication.data.repository.WorkoutRepository
import com.example.myapplication.service.WorkoutTimerService // <--- IMPORT YOUR SERVICE
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
// import kotlinx.coroutines.delay // <--- REMOVED (Service handles ticking)
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive // <--- REMOVED
import kotlinx.coroutines.launch
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
    private val application: Application
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

    // private val timerJobs = mutableMapOf<Long, Job>() // <--- REMOVED (No longer managing jobs manually)

    init {
        // --- NEW: LISTEN TO THE SERVICE ---
        // This ensures that even if the app restarts, the UI picks up the running timer immediately.
        viewModelScope.launch {
            WorkoutTimerService.timerState.collect { serviceState ->
                updateLocalTimerState(
                    activeExerciseId = serviceState.activeExerciseId,
                    remainingTime = serviceState.remainingTime.toLong(),
                    isRunning = serviceState.isRunning
                )
            }
        }
        // ----------------------------------

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

    // ... (Keep loadWorkout, updateSetCompletion, updateSetReps, updateSetWeight, updateSetRpe, swapExercise, getTopAlternatives, toggleExerciseVisibility, addWarmUpSets exactly as they are) ...

    fun loadWorkout(workoutId: Long) { _workoutId.value = workoutId }

    fun updateSetCompletion(set: WorkoutSetEntity, isCompleted: Boolean) {
        viewModelScope.launch { repository.updateSet(set.copy(isCompleted = isCompleted)) }
        // Optional: Auto-start timer when set is checked
        // if(isCompleted) startSetTimer(set.parentExerciseId)
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

    // --- UPDATED TIMER LOGIC (Using Service) ---

    fun startSetTimer(exerciseId: Long) {
        // Find the duration based on the exercise
        val exerciseState = _exerciseStates.value.find { it.exercise.exerciseId == exerciseId } ?: return
        val durationSeconds = (exerciseState.exercise.estimatedTimePerSet * 60).toInt()

        // Send "Start" command to the Foreground Service
        val intent = Intent(application, WorkoutTimerService::class.java).apply {
            action = WorkoutTimerService.ACTION_START
            putExtra(WorkoutTimerService.EXTRA_SECONDS, durationSeconds)
            putExtra(WorkoutTimerService.EXTRA_EXERCISE_ID, exerciseId)
        }
        application.startService(intent)
    }

    fun skipSetTimer(exerciseId: Long) {
        // Send "Stop" command to the Foreground Service
        val intent = Intent(application, WorkoutTimerService::class.java).apply {
            action = WorkoutTimerService.ACTION_STOP
        }
        application.startService(intent)
    }

    // --- NEW: Helper to update UI based on Service State ---
    private fun updateLocalTimerState(activeExerciseId: Long?, remainingTime: Long, isRunning: Boolean) {
        _exerciseStates.value = _exerciseStates.value.map { state ->
            if (state.exercise.exerciseId == activeExerciseId) {
                // This is the active timer
                state.copy(timerState = ExerciseTimerState(
                    remainingTime = remainingTime,
                    isRunning = isRunning,
                    isFinished = !isRunning && remainingTime == 0L
                ))
            } else {
                // This is NOT the active timer, ensure it looks idle
                // We keep the default duration if it's idle
                val defaultDuration = (state.exercise.estimatedTimePerSet * 60).toLong()
                state.copy(timerState = ExerciseTimerState(
                    remainingTime = defaultDuration,
                    isRunning = false,
                    isFinished = false
                ))
            }
        }
    }

    fun finishWorkout(workoutId: Long) {
        // Stop any running timer service
        skipSetTimer(-1)

        viewModelScope.launch {
            val report = repository.completeWorkout(workoutId)
            _workoutSummary.value = report
        }
    }

    fun clearSummary() {
        _workoutSummary.value = null
    }
}

// ... (Keep generateCoachBriefing) ...
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