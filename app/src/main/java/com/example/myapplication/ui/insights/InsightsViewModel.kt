package com.example.myapplication.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.CompletedWorkoutWithExercise
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val repository: WorkoutRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    // Cache the full history so we can re-filter without hitting DB
    private var fullHistory: List<CompletedWorkoutWithExercise> = emptyList()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            repository.getAllCompletedWorkouts().collectLatest { history ->
                fullHistory = history

                // 1. Get unique exercises that actually have history
                val uniqueExercises = history.map { it.exercise }
                    .distinctBy { it.exerciseId }
                    .sortedBy { it.name }

                // 2. Calculate Volume per Muscle Group (Sets * Reps * Weight)
                val volumeMap = history.filter { it.exercise.muscleGroup != null }
                    .groupBy { it.exercise.muscleGroup!! }
                    .mapValues { (_, workouts) ->
                        workouts.sumOf { (it.completedWorkout.weight * it.completedWorkout.reps).toDouble() }
                    }

                // 3. Set default state (default to first exercise if available)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        availableExercises = uniqueExercises,
                        muscleVolumeDistribution = volumeMap,
                        totalWorkouts = history.size
                    )
                }

                // Default selection: If we haven't selected one, pick the first
                if (_uiState.value.selectedExercise == null && uniqueExercises.isNotEmpty()) {
                    selectExercise(uniqueExercises.first())
                } else {
                    // Refresh graph if we already have a selection
                    _uiState.value.selectedExercise?.let { selectExercise(it) }
                }
            }
        }
    }

    fun selectExercise(exercise: ExerciseEntity) {
        // Filter history for this specific exercise
        val exerciseHistory = fullHistory.filter { it.exercise.exerciseId == exercise.exerciseId }
            .sortedBy { it.completedWorkout.date }

        // Calculate 1RM using Epley Formula: Weight * (1 + Reps/30)
        val dataPoints = exerciseHistory.map { item ->
            val w = item.completedWorkout.weight.toFloat()
            val r = item.completedWorkout.reps.toFloat()
            val oneRM = if (r > 0) w * (1 + r / 30f) else 0f

            Pair(item.completedWorkout.date, oneRM)
        }

        _uiState.update {
            it.copy(
                selectedExercise = exercise,
                oneRepMaxHistory = dataPoints
            )
        }
    }
}