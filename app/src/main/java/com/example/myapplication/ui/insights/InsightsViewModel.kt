package com.example.myapplication.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.repository.PlanRepository
import com.example.myapplication.data.repository.WorkoutExecutionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val planRepository: PlanRepository,
    private val executionRepository: WorkoutExecutionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        // 1. Load Exercises
        viewModelScope.launch {
            planRepository.getAllExercises().collect { exercises ->
                val currentSelected = _uiState.value.selectedExercise
                _uiState.value = _uiState.value.copy(
                    availableExercises = exercises,
                    // Preserve selection if exists, else pick first
                    selectedExercise = currentSelected ?: exercises.firstOrNull()
                )

                // If we just set the default exercise, load its graph data
                if (currentSelected == null && exercises.isNotEmpty()) {
                    selectExercise(exercises.first())
                }
            }
        }

        // 2. Load History & Volume Stats
        viewModelScope.launch {
            executionRepository.getAllCompletedWorkouts().collect { completedWorkouts ->
                // Calculate Volume by Muscle Group
                val volumeByMuscle = completedWorkouts
                    .filter { it.exercise.muscleGroup != null }
                    .groupBy { it.exercise.muscleGroup!! }
                    .mapValues { (_, workouts) ->
                        workouts.sumOf { it.completedWorkout.totalVolume.toDouble() }
                    }

                // Get Recent History (Last 10 items)
                val sortedHistory = completedWorkouts
                    .sortedByDescending { it.completedWorkout.date }
                    .take(10)

                _uiState.value = _uiState.value.copy(
                    muscleVolumeDistribution = volumeByMuscle,
                    recentWorkouts = sortedHistory
                )
            }
        }
    }

    fun selectExercise(exercise: ExerciseEntity) {
        _uiState.value = _uiState.value.copy(selectedExercise = exercise)
        viewModelScope.launch {
            executionRepository.getCompletedWorkoutsForExercise(exercise.exerciseId).collect { completed ->
                val oneRepMaxHistory = completed.sortedBy { it.completedWorkout.date }.map {
                    // Epley Formula: Weight * (1 + Reps/30)
                    val estimated1RM = it.completedWorkout.totalVolume * (1 + (it.completedWorkout.totalReps / 30.0f))
                    Pair(it.completedWorkout.date, estimated1RM.toFloat())
                }
                _uiState.value = _uiState.value.copy(oneRepMaxHistory = oneRepMaxHistory)
            }
        }
    }
}