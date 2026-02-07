package com.example.myapplication.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.local.WorkoutEntity
import com.example.myapplication.data.local.WorkoutSetEntity
import com.example.myapplication.data.repository.PlanRepository
import com.example.myapplication.data.repository.WorkoutExecutionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ManualPlanViewModel @Inject constructor(
    private val planRepository: PlanRepository,
    private val executionRepository: WorkoutExecutionRepository
) : ViewModel() {

    private val _allExercises = planRepository.getAllExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val filteredExercises = combine(_allExercises, _searchQuery) { exercises, query ->
        if (query.isBlank()) exercises
        else exercises.filter { it.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedExercises = MutableStateFlow<List<ExerciseEntity>>(emptyList())
    val selectedExercises = _selectedExercises.asStateFlow()

    fun updateSearch(query: String) {
        _searchQuery.value = query
    }

    fun addExercise(exercise: ExerciseEntity) {
        if (!_selectedExercises.value.contains(exercise)) {
            _selectedExercises.value = _selectedExercises.value + exercise
        }
    }

    fun removeExercise(exercise: ExerciseEntity) {
        _selectedExercises.value = _selectedExercises.value - exercise
    }

    fun saveAndStartWorkout(onSuccess: (Long) -> Unit) {
        viewModelScope.launch {
            val exercises = _selectedExercises.value
            if (exercises.isEmpty()) return@launch

            val newWorkout = WorkoutEntity(
                date = LocalDate.now().toEpochDay(),
                duration = 60f,
                notes = "Manual Session"
            )
            val workoutId = executionRepository.insertWorkout(newWorkout)

            val setsToInsert = mutableListOf<WorkoutSetEntity>()

            exercises.forEach { exercise ->
                repeat(3) { setIndex ->
                    setsToInsert.add(
                        WorkoutSetEntity(
                            workoutId = workoutId,
                            exerciseId = exercise.exerciseId,
                            setNumber = setIndex + 1,
                            suggestedLbs = 0,
                            suggestedReps = 10,
                            suggestedRpe = 8,
                            isCompleted = false
                        )
                    )
                }
            }

            executionRepository.insertSets(setsToInsert)

            onSuccess(workoutId)
        }
    }
}