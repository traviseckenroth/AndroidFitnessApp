package com.example.myapplication.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: WorkoutRepository
) : ViewModel() {

    private val _userHeight = repository.getUserHeight().stateIn(viewModelScope, SharingStarted.Lazily, 180)
    private val _userWeight = repository.getUserWeight().stateIn(viewModelScope, SharingStarted.Lazily, 75.0)

    val uiState: StateFlow<ProfileUiState> = combine(
        repository.getAllCompletedWorkouts(),
        _userHeight,
        _userWeight
    ) { completed, height, weight ->
        val items = completed.map {
            CompletedWorkoutItem(
                completedWorkout = it.completedWorkout,
                exercise = it.exercise
            )
        }.sortedByDescending { it.completedWorkout.date }

        if (items.isEmpty() && height == 0 && weight == 0.0) {
            ProfileUiState.Empty
        } else {
            ProfileUiState.Success(
                completedWorkouts = items,
                height = height,
                weight = weight
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProfileUiState.Loading
    )

    fun saveBiometrics(height: Int, weight: Double) {
        viewModelScope.launch {
            repository.saveBiometrics(height, weight)
        }
    }
}
