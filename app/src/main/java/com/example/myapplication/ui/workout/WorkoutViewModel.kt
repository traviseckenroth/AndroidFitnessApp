package com.example.myapplication.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.DailyWorkout
import com.example.myapplication.data.local.CompletedWorkoutEntity
import com.example.myapplication.data.local.WorkoutDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkoutUiState(
    val workoutTitle: String = "",
    val exercises: List<Exercise> = emptyList(),
    val currentExerciseIndex: Int = 0,
    val workoutFinished: Boolean = false,
    val timerValue: Long = 0,
    val isTimerRunning: Boolean = false,
    val currentSet: Int = 1,
    val isExerciseComplete: Boolean = false
)

data class Exercise(
    val exerciseId: Long,
    val name: String,
    val sets: List<WorkoutSet>,
    val tier: Int
)

data class WorkoutSet(
    val setNumber: Int,
    var lbs: String,
    var reps: String,
    var rpe: String,
    var isDone: Boolean
)

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val workoutDao: WorkoutDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkoutUiState())
    val uiState: StateFlow<WorkoutUiState> = _uiState

    private var timerJob: Job? = null

    fun loadWorkout(dailyWorkout: DailyWorkout) {
        val exercises = dailyWorkout.exercises.map { dataExercise ->
            val workoutSets = (1..dataExercise.sets).map { setNum ->
                WorkoutSet(
                    setNumber = setNum,
                    lbs = "",
                    reps = dataExercise.reps,
                    rpe = "",
                    isDone = false
                )
            }
            Exercise(
                exerciseId = dataExercise.exerciseId,
                name = dataExercise.name,
                sets = workoutSets,
                tier = dataExercise.tier
            )
        }
        _uiState.value = WorkoutUiState(
            workoutTitle = dailyWorkout.title,
            exercises = exercises
        )
    }

    fun onSetFieldChange(exerciseIndex: Int, setIndex: Int, field: String, value: String) {
        _uiState.update { currentState ->
            val newExercises = currentState.exercises.toMutableList()
            val newSets = newExercises[exerciseIndex].sets.toMutableList()
            val updatedSet = when (field) {
                "lbs" -> newSets[setIndex].copy(lbs = value)
                "reps" -> newSets[setIndex].copy(reps = value)
                "rpe" -> newSets[setIndex].copy(rpe = value)
                else -> newSets[setIndex]
            }
            newSets[setIndex] = updatedSet
            newExercises[exerciseIndex] = newExercises[exerciseIndex].copy(sets = newSets)
            currentState.copy(exercises = newExercises)
        }
    }

    fun onSetDone(exerciseIndex: Int, setIndex: Int, isDone: Boolean) {
        _uiState.update { currentState ->
            val newExercises = currentState.exercises.toMutableList()
            val newSets = newExercises[exerciseIndex].sets.toMutableList()
            newSets[setIndex] = newSets[setIndex].copy(isDone = isDone)
            newExercises[exerciseIndex] = newExercises[exerciseIndex].copy(sets = newSets)
            currentState.copy(exercises = newExercises)
        }

        if (isDone) {
            val currentExercise = _uiState.value.exercises[exerciseIndex]
            if (setIndex < currentExercise.sets.size - 1) {
                startTimer()
            } else {
                _uiState.update { it.copy(isExerciseComplete = true) }
            }
        }
    }

    fun startTimer() {
        if (_uiState.value.isTimerRunning) return

        val currentExercise = _uiState.value.exercises[_uiState.value.currentExerciseIndex]
        val restTime = when (currentExercise.tier) {
            1 -> 180L
            2 -> 150L
            3 -> 120L
            else -> 60L
        }
        _uiState.update { it.copy(isTimerRunning = true, timerValue = restTime) }

        timerJob = viewModelScope.launch {
            while (_uiState.value.timerValue > 0) {
                delay(1000)
                _uiState.update { it.copy(timerValue = it.timerValue - 1) }
            }
            _uiState.update { it.copy(isTimerRunning = false) }
            nextSet()
        }
    }

    fun skipTimer() {
        timerJob?.cancel()
        _uiState.update { it.copy(isTimerRunning = false, timerValue = 0) }
        nextSet()
    }

    private fun nextSet() {
        val currentExercise = _uiState.value.exercises[_uiState.value.currentExerciseIndex]
        if (_uiState.value.currentSet < currentExercise.sets.size) {
            _uiState.update { it.copy(currentSet = it.currentSet + 1) }
        } else {
            // Last set was finished, do nothing, wait for user to click next
        }
    }

    fun nextExercise() {
        timerJob?.cancel()
        _uiState.update { currentState ->
            if (currentState.currentExerciseIndex < currentState.exercises.size - 1) {
                currentState.copy(
                    currentExerciseIndex = currentState.currentExerciseIndex + 1,
                    currentSet = 1,
                    isTimerRunning = false,
                    timerValue = 0,
                    isExerciseComplete = false
                )
            } else {
                currentState
            }
        }
    }

    fun previousExercise() {
        timerJob?.cancel()
        _uiState.update { currentState ->
            if (currentState.currentExerciseIndex > 0) {
                currentState.copy(
                    currentExerciseIndex = currentState.currentExerciseIndex - 1,
                    currentSet = 1,
                    isTimerRunning = false,
                    timerValue = 0,
                    isExerciseComplete = false
                )
            } else {
                currentState
            }
        }
    }

    fun finishWorkout() {
        saveWorkout()
        _uiState.update { it.copy(workoutFinished = true) }
    }

    private fun saveWorkout() {
        viewModelScope.launch {
            val completedWorkouts = _uiState.value.exercises.flatMap { exercise ->
                exercise.sets.filter { it.isDone }.map {
                    CompletedWorkoutEntity(
                        exerciseId = exercise.exerciseId,
                        date = System.currentTimeMillis(),
                        reps = it.reps.toIntOrNull() ?: 0,
                        rpe = it.rpe.toIntOrNull() ?: 0,
                        weight = it.lbs.toIntOrNull() ?: 0
                    )
                }
            }
            workoutDao.insertCompletedWorkouts(completedWorkouts)
        }
    }
}