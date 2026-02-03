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

    fun swapExercise(oldExerciseId: Long, newExerciseId: Long) {
        viewModelScope.launch {
            repository.swapExercise(_workoutId.value, oldExerciseId, newExerciseId)
        }
    }

    suspend fun getTopAlternatives(exercise: ExerciseEntity): List<ExerciseEntity> {
        return repository.getBestAlternatives(exercise)
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
        // Avoid multiple overlapping timers for the same exercise
        if (timerJobs[exerciseId]?.isActive == true) return

        timerJobs[exerciseId] = viewModelScope.launch {
            val exerciseState = _exerciseStates.value.find { it.exercise.exerciseId == exerciseId } ?: return@launch
            val sets = exerciseState.sets
            // Tier-based rest time: estimatedTimePerSet is used as the baseline
            val restTime = (exerciseState.exercise.estimatedTimePerSet * 60).toLong()

            for (index in sets.indices) {
                val currentSet = sets[index]

                // Auto-advance: skip sets that are already finished
                if (currentSet.isCompleted) continue

                var remainingTime = restTime
                updateTimerState(exerciseId, remainingTime, isRunning = true, isFinished = false)

                // Standard countdown loop
                while (remainingTime > 0 && isActive) {
                    delay(1000)
                    remainingTime--
                    updateTimerState(exerciseId, remainingTime, isRunning = true, isFinished = false)
                }

                // Post-countdown logic
                if (isActive) {
                    // Automatically record set completion in the DB
                    updateSetCompletion(currentSet, true)

                    // If it's the final set, stop and mark finished
                    if (index == sets.lastIndex) {
                        updateTimerState(exerciseId, 0, isRunning = false, isFinished = true)
                        break
                    }
                    // Loop continues to next set automatically
                }
            }
        }
    }

    fun skipSetTimer(exerciseId: Long) {
        // Instead of cancelling the job (which kills the loop),
        // we start a new job that just forces the state to 0.
        // The startSetTimer loop is designed to continue when remainingTime reaches 0.

        val currentState = _exerciseStates.value.find { it.exercise.exerciseId == exerciseId }
        if (currentState != null && currentState.timerState.isRunning) {
            // We cancel the current countdown job
            timerJobs[exerciseId]?.cancel()

            // We immediately mark the current set as complete and trigger the NEXT timer
            viewModelScope.launch {
                val sets = currentState.sets
                val currentSetIndex = sets.indexOfFirst { !it.isCompleted }

                if (currentSetIndex != -1) {
                    val currentSet = sets[currentSetIndex]
                    updateSetCompletion(currentSet, true)

                    // If not the last set, restart timer for the next set immediately
                    if (currentSetIndex < sets.lastIndex) {
                        startSetTimer(exerciseId)
                    } else {
                        updateTimerState(exerciseId, 0, isRunning = false, isFinished = true)
                    }
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
            } else it
        }
    }
    fun finishWorkout(workoutId: Long) {
        viewModelScope.launch {
            val report = repository.completeWorkout(workoutId)
            _workoutSummary.value = report
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