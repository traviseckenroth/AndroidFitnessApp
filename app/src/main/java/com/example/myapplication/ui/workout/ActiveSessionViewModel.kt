package com.example.myapplication.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.WorkoutSetEntity
import com.example.myapplication.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ActiveSessionViewModel @Inject constructor(
    private val repository: WorkoutRepository
) : ViewModel() {

    private val _sets = MutableStateFlow<List<WorkoutSetEntity>>(emptyList())
    val sets: StateFlow<List<WorkoutSetEntity>> = _sets

    // Called when screen loads
    fun loadWorkout(workoutId: Long) {
        viewModelScope.launch {
            repository.getSetsForSession(workoutId).collect { loadedSets ->
                _sets.value = loadedSets
            }
        }
    }

    fun updateSetCompletion(set: WorkoutSetEntity, isCompleted: Boolean) {
        viewModelScope.launch {
            val updatedSet = set.copy(isCompleted = isCompleted)
            repository.updateSet(updatedSet)
        }
    }

    fun updateSetReps(set: WorkoutSetEntity, newReps: String) {
        val repsInt = newReps.toIntOrNull() ?: return
        viewModelScope.launch {
            val updatedSet = set.copy(actualReps = repsInt)
            repository.updateSet(updatedSet)
        }
    }
}