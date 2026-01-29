package com.example.myapplication.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.DailyWorkout
import com.example.myapplication.data.local.CompletedWorkoutEntity
import com.example.myapplication.data.local.WorkoutDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkoutUiState(
    val workoutTitle: String = "",
    val exercises: List<Exercise> = emptyList(),
    val currentExerciseIndex: Int = 0,
    val workoutFinished: Boolean = false
)

data class Exercise(
    val exerciseId: Long,
    val name: String,
    val tier: String, 
    val sets: List<WorkoutSet>
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
                tier = "", 
                sets = workoutSets
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
    }

    fun nextExercise() {
        _uiState.update { currentState ->
            if (currentState.currentExerciseIndex < currentState.exercises.size - 1) {
                currentState.copy(currentExerciseIndex = currentState.currentExerciseIndex + 1)
            } else {
                currentState
            }
        }
    }

    fun previousExercise() {
        _uiState.update { currentState ->
            if (currentState.currentExerciseIndex > 0) {
                currentState.copy(currentExerciseIndex = currentState.currentExerciseIndex - 1)
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