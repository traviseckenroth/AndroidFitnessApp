// app/src/main/java/com/example/myapplication/ui/insights/InsightsViewModel.kt

package com.example.myapplication.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val repository: WorkoutRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            repository.getAllExercises().collect { exercises ->
                _uiState.value = _uiState.value.copy(availableExercises = exercises)
                if (exercises.isNotEmpty() && _uiState.value.selectedExercise == null) {
                    selectExercise(exercises.first())
                }
            }
        }

        viewModelScope.launch {
            // Updated to fetch both past history AND the newly accepted plan volume
            repository.getAllCompletedWorkouts().collect { completedWorkouts ->
                // Calculate volume from actually performed workouts
                val completedVolume = completedWorkouts
                    .filter { it.exercise.muscleGroup != null }
                    .groupBy { it.exercise.muscleGroup!! }
                    .mapValues { (_, workouts) ->
                        workouts.sumOf { it.completedWorkout.totalVolume.toDouble() }
                    }

                // Update state with current progress
                _uiState.value = _uiState.value.copy(
                    muscleVolumeDistribution = completedVolume,
                    totalWorkouts = completedWorkouts.size,
                    isLoading = false
                )
            }
        }
    }

    fun selectExercise(exercise: ExerciseEntity) {
        _uiState.value = _uiState.value.copy(selectedExercise = exercise)
        viewModelScope.launch {
            // Using existing repository method to fetch history for the specific exercise
            repository.getCompletedWorkoutsForExercise(exercise.exerciseId).collect { completed ->
                val oneRepMaxHistory = completed.map {
                    // Estimated 1RM formula: Weight * (1 + Reps/30)
                    val estimated1RM = it.completedWorkout.totalVolume * (1 + (it.completedWorkout.totalReps / 30.0f))
                    Pair(it.completedWorkout.date, estimated1RM)
                }
                _uiState.value = _uiState.value.copy(oneRepMaxHistory = oneRepMaxHistory)
            }
        }
    }
}