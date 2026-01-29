package com.example.myapplication.ui.plan

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.DailyWorkout
import com.example.myapplication.data.WeeklyPlan
import com.example.myapplication.data.WorkoutPlan
import com.example.myapplication.data.local.CompletedWorkoutEntity
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.local.WorkoutDao
import com.example.myapplication.data.remote.GeneratedDay
import com.example.myapplication.data.remote.invokeBedrock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface PlanUiState {
    object Idle : PlanUiState
    object Loading : PlanUiState
    data class Success(val plan: WorkoutPlan, val startDate: Long = System.currentTimeMillis()) : PlanUiState
    data class Error(val msg: String) : PlanUiState
    object Empty : PlanUiState
}

@HiltViewModel
class PlanViewModel @Inject constructor(
    private val workoutDao: WorkoutDao
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlanUiState>(PlanUiState.Empty)
    val uiState: StateFlow<PlanUiState> = _uiState.asStateFlow()

    fun generatePlan(goal: String, program: String, duration: Float, days: List<String>) {
        if (goal.isBlank()) {
            _uiState.value = PlanUiState.Error("Please enter a goal.")
            return
        }

        viewModelScope.launch {
            _uiState.value = PlanUiState.Loading
            try {
                val generatedDays = withContext(Dispatchers.IO) {
                    val allExercises = workoutDao.getAllExercises().first()
                    val workoutHistory = workoutDao.getAllCompletedWorkouts().first()
                    invokeBedrock(goal, program, days, duration, workoutHistory)
                }

                // --- UPSERT GENERATED EXERCISES ---
                val allGeneratedExercises = generatedDays.flatMap { it.exercises }.distinctBy { it.name.lowercase() }
                val existingExercises = workoutDao.getAllExercises().first()

                allGeneratedExercises.forEach { generatedExercise ->
                    val exists = existingExercises.any { it.name.equals(generatedExercise.name, ignoreCase = true) }
                    if (!exists) {
                        workoutDao.insertExercise(
                            ExerciseEntity(
                                name = generatedExercise.name,
                                muscleGroup = generatedExercise.muscleGroup,
                                equipment = generatedExercise.equipment,
                                tier = generatedExercise.tier,
                                loadability = generatedExercise.loadability,
                                fatigue = generatedExercise.fatigue,
                                notes = generatedExercise.notes
                            )
                        )
                    }
                }
                // --- END UPSERT ---

                val updatedAllExercises = workoutDao.getAllExercises().first()

                val weeklyPlans = generatedDays.groupBy { it.week }.map { (week, days) ->
                    WeeklyPlan(
                        week = week,
                        days = days.map { day ->
                            DailyWorkout(
                                day = day.day,
                                title = day.title,
                                exercises = day.exercises.map { ex ->
                                    val exerciseEntity = updatedAllExercises.find { it.name.equals(ex.name, ignoreCase = true) }
                                    com.example.myapplication.data.Exercise(
                                        exerciseId = exerciseEntity?.exerciseId ?: 0,
                                        name = ex.name,
                                        sets = ex.sets,
                                        reps = ex.suggestedReps.toString(),
                                        rest = "60s"
                                    )
                                }
                            )
                        }
                    )
                }

                val plan = WorkoutPlan(weeks = weeklyPlans)
                _uiState.value = PlanUiState.Success(plan)

            } catch (e: Exception) {
                val errorMessage = if (e.message?.contains("identity") == true || e.message?.contains("Credentials") == true) {
                    "AWS Error: Check your Access Key and Secret Key in local.properties."
                } else {
                    "Generation Failed: ${e.message}"
                }
                _uiState.value = PlanUiState.Error(errorMessage)
                Log.e("PlanViewModel", "Error generating plan", e)
            }
        }
    }

    fun resetState() {
        _uiState.value = PlanUiState.Empty
    }
}
