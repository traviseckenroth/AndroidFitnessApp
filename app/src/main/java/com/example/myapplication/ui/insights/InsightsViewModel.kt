package com.example.myapplication.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.WorkoutPlan
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
    private val _selectedExercise = MutableStateFlow<com.example.myapplication.data.local.ExerciseEntity?>(null)
    val uiState: StateFlow<InsightsUIState> = combine(
        repository.getAllExercises(),
        repository.getMuscleVolumeDistribution(),
        _selectedExercise
    ) { exercises, distribution, selected ->
        val history = selected?.let { repository.getOneRepMaxHistory(it.id) } ?: emptyList()
        InsightsUIState(
            availableExercises = exercises,
            selectedExercise = selected ?: exercises.firstOrNull(),
            oneRepMaxHistory = history,
            muscleVolumeDistribution = distribution
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000), // 5s timeout to handle config changes
        initialValue = InsightsUIState()
    )
    fun selectExercise(exercise: com.example.myapplication.data.local.ExerciseEntity) {
        _selectedExercise.value = exercise
    }
}
    private val _currentPlan = MutableStateFlow<WorkoutPlan?>(null)
    val currentPlan: StateFlow<WorkoutPlan?> = _currentPlan.asStateFlow()
    init {
        loadInitialData()
    }
    private fun loadCurrentPlan() {
        viewModelScope.launch {
            _currentPlan.value = repository.getLatestPlanDetails()
        }
    }
    private fun loadInitialData() {
        viewModelScope.launch {
            repository.getAllExercises().collect { exercises ->
                _uiState.value = _uiState.value.copy(availableExercises = exercises)
                if (exercises.isNotEmpty()) {
                    selectExercise(exercises.first())
                }
            }
        }
        viewModelScope.launch {
            repository.getAllCompletedWorkouts().collect { completedWorkouts ->
                val volumeByMuscle = completedWorkouts
                    .filter { it.exercise.muscleGroup != null }
                    .groupBy { it.exercise.muscleGroup!! }
                    .mapValues { (_, workouts) ->
                        workouts.sumOf { it.completedWorkout.totalVolume.toDouble() }
                    }
                _uiState.value = _uiState.value.copy(muscleVolumeDistribution = volumeByMuscle)
            }
        }
    }

    fun selectExercise(exercise: ExerciseEntity) {
        _uiState.value = _uiState.value.copy(selectedExercise = exercise)
        viewModelScope.launch {
            repository.getCompletedWorkoutsForExercise(exercise.exerciseId).collect { completed ->
                val oneRepMaxHistory = completed.map {
                    val estimated1RM = it.completedWorkout.totalVolume * (1 + (it.completedWorkout.totalReps / 30.0f))
                    Pair(it.completedWorkout.date, estimated1RM.toFloat())
                }
                _uiState.value = _uiState.value.copy(oneRepMaxHistory = oneRepMaxHistory)
            }
        }
    }
}
