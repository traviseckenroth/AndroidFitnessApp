package com.example.myapplication.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.CompletedWorkoutEntity
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.local.WorkoutDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CompletedWorkoutItem(
    val completedWorkout: CompletedWorkoutEntity,
    val exercise: ExerciseEntity
)

sealed interface ProfileUiState {
    object Loading : ProfileUiState
    data class Success(val completedWorkouts: List<CompletedWorkoutItem>) : ProfileUiState
    object Empty : ProfileUiState
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val workoutDao: WorkoutDao
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val completedWorkoutsFlow = workoutDao.getAllCompletedWorkouts()
            val exercisesFlow = workoutDao.getAllExercises()

            completedWorkoutsFlow.combine(exercisesFlow) { completedWorkouts, exercises ->
                val items = completedWorkouts.mapNotNull { completedWorkout ->
                    val exercise = exercises.find { it.exerciseId == completedWorkout.exerciseId }
                    exercise?.let { CompletedWorkoutItem(completedWorkout, it) }
                }.sortedByDescending { it.completedWorkout.date }

                if (items.isEmpty()) {
                    ProfileUiState.Empty
                } else {
                    ProfileUiState.Success(items)
                }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
}
