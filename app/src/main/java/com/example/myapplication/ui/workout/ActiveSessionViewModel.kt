package com.example.myapplication.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.local.WorkoutSetEntity
import com.example.myapplication.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
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
    private val repository: WorkoutRepository
) : ViewModel() {

    private val _workoutId = MutableStateFlow<Long>(-1)
    private val _sets = MutableStateFlow<List<WorkoutSetEntity>>(emptyList())
    private val _exercises = MutableStateFlow<List<ExerciseEntity>>(emptyList())
    private val _exerciseStates = MutableStateFlow<List<ExerciseState>>(emptyList())
    val exerciseStates: StateFlow<List<ExerciseState>> = _exerciseStates

    private val _coachBriefing = MutableStateFlow("Loading briefing...")
    val coachBriefing: StateFlow<String> = _coachBriefing

    // --- NEW: SUMMARY STATE ---
    private val _workoutSummary = MutableStateFlow<List<String>?>(null)
    val workoutSummary: StateFlow<List<String>?> = _workoutSummary

    private val timerJobs = mutableMapOf<Long, Job>()

    init {
        viewModelScope.launch {
            _workoutId.collectLatest { id ->
                if (id != -1L) {
                    repository.getSetsForSession(id).collect { loadedSets ->
                        val filledSets = loadedSets.map { set ->
                            set.copy(
                                actualReps = if (set.actualReps == 0) set.suggestedReps else set.actualReps,
                                actualLbs = if (set.actualLbs == 0f) set.suggestedLbs.toFloat() else set.actualLbs,
                                actualRpe = if (set.actualRpe == 0.0f) set.suggestedRpe.toFloat() else set.actualRpe
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
                val sessionExercises = allExercises.filter { it.exerciseId in sessionExerciseIds }

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

    fun loadWorkout(workoutId: Long) {
        _workoutId.value = workoutId
    }

    fun updateSetCompletion(set: WorkoutSetEntity, isCompleted: Boolean) {
        viewModelScope.launch {
            repository.updateSet(set.copy(isCompleted = isCompleted))
        }
    }

    fun updateSetReps(set: WorkoutSetEntity, newReps: String) {
        val repsInt = newReps.toIntOrNull() ?: return
        viewModelScope.launch {
            repository.updateSet(set.copy(actualReps = repsInt))
        }
    }

    fun updateSetWeight(set: WorkoutSetEntity, newLbs: String) {
        val lbsFloat = newLbs.toFloatOrNull() ?: return
        viewModelScope.launch {
            repository.updateSet(set.copy(actualLbs = lbsFloat))
        }
    }

    fun updateSetRpe(set: WorkoutSetEntity, newRpe: String) {
        val rpeFloat = newRpe.toFloatOrNull() ?: return
        viewModelScope.launch {
            repository.updateSet(set.copy(actualRpe = rpeFloat))
        }
    }

    fun toggleExerciseVisibility(exerciseId: Long) {
        _exerciseStates.value = _exerciseStates.value.map {
            if (it.exercise.exerciseId == exerciseId) {
                it.copy(areSetsVisible = !it.areSetsVisible)
            } else {
                it
            }
        }
    }

    fun startSetTimer(exerciseId: Long) {
        timerJobs[exerciseId]?.cancel()
        timerJobs[exerciseId] = viewModelScope.launch {
            val exerciseState = _exerciseStates.value.find { it.exercise.exerciseId == exerciseId } ?: return@launch
            val restTime = (exerciseState.exercise.estimatedTimePerSet * 60).toLong()
            val totalSets = exerciseState.sets.size
            var setsCompleted = 1

            while (isActive && setsCompleted <= totalSets) {
                var remainingTime = restTime
                updateTimerState(exerciseId, remainingTime, isRunning = true, isFinished = false)

                while (remainingTime > 0 && isActive) {
                    delay(1000)
                    remainingTime--
                    updateTimerState(exerciseId, remainingTime, isRunning = true, isFinished = false)
                }

                if (setsCompleted == totalSets) {
                    updateTimerState(exerciseId, 0, isRunning = false, isFinished = true)
                    break
                } else {
                    setsCompleted++
                }
            }
        }
    }

    private fun updateTimerState(exerciseId: Long, time: Long, isRunning: Boolean, isFinished: Boolean) {
        _exerciseStates.value = _exerciseStates.value.map {
            if (it.exercise.exerciseId == exerciseId) {
                it.copy(timerState = ExerciseTimerState(
                    remainingTime = time,
                    isRunning = isRunning,
                    isFinished = isFinished
                ))
            } else {
                it
            }
        }
    }

    fun skipSetTimer(exerciseId: Long) {
        timerJobs[exerciseId]?.cancel()
        timerJobs.remove(exerciseId)
        viewModelScope.launch {
            _exerciseStates.value = _exerciseStates.value.map {
                if (it.exercise.exerciseId == exerciseId) {
                    it.copy(timerState = it.timerState.copy(isRunning = false, remainingTime = 0, isFinished = false))
                } else {
                    it
                }
            }
        }
    }

    // --- UPDATED: Wait for result and update State ---
    fun finishWorkout(workoutId: Long) {
        viewModelScope.launch {
            val report = repository.completeWorkout(workoutId)
            _workoutSummary.value = report // This triggers the Dialog in UI
        }
    }

    fun clearSummary() {
        _workoutSummary.value = null
    }
}

private fun generateCoachBriefing(exercises: List<ExerciseEntity>, sets: List<WorkoutSetEntity>): String {
    if (exercises.isEmpty() || sets.isEmpty()) return "Ready to start your workout?"
    val tiers = exercises.map { it.tier }
    val tier1Count = tiers.count { it == 1 }
    val avgReps = if (sets.isNotEmpty()) sets.map { it.suggestedReps }.average() else 0.0
    val totalSets = sets.size

    val dominantMuscle = exercises.groupingBy { it.muscleGroup }
        .eachCount()
        .maxByOrNull { it.value }?.key ?: "Full Body"

    return when {
        tier1Count >= 2 && avgReps < 8 -> {
            "Mission: Heavy $dominantMuscle Day.\n" +
                    "Prioritize your rest periods on the big lifts. Intensity is key today, not speed."
        }
        totalSets > 18 -> {
            "Mission: High Volume $dominantMuscle.\n" +
                    "This session is a marathon. Focus on surviving the burn and keeping your form strict as you fatigue."
        }
        else -> {
            "Mission: $dominantMuscle Hypertrophy.\n" +
                    "Focus on the mind-muscle connection. Control the negatives and squeeze at the top."
        }
    }
}