package com.example.myapplication.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.WorkoutPlan
import com.example.myapplication.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface PlanUiState {
    object Loading : PlanUiState
    // Success now holds the FULL DATA object
    data class Success(val plan: WorkoutPlan) : PlanUiState
    data class Error(val msg: String) : PlanUiState
    object Empty : PlanUiState
}

@HiltViewModel
class PlanViewModel @Inject constructor(
    private val repository: WorkoutRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlanUiState>(PlanUiState.Empty)
    val uiState: StateFlow<PlanUiState> = _uiState.asStateFlow()

    // New: Track acceptance in ViewModel so it survives navigation
    private val _isPlanAccepted = MutableStateFlow(false)
    val isPlanAccepted = _isPlanAccepted.asStateFlow()
    private var currentPlanId: Long = -1L
    fun generatePlan(goal: String, program: String, duration: Float, days: List<String>) {
        if (goal.isBlank()) {
            _uiState.value = PlanUiState.Error("Please enter a goal.")
            return
        }

        viewModelScope.launch {
            _uiState.value = PlanUiState.Loading
            _isPlanAccepted.value = false // Reset for new generation
            try {
                // 1. Generate & Save
                val planId = withContext(Dispatchers.IO) {
                    repository.generateAndSavePlan(
                        goal = goal,
                        duration = duration.toInt(),
                        days = days,
                        programType = program
                    )
                }
                currentPlanId = planId
                // 2. Fetch Full Details
                val fullPlan = withContext(Dispatchers.IO) {
                    repository.getPlanDetails(planId)
                }
                // 3. Update State
                _uiState.value = PlanUiState.Success(fullPlan)

            } catch (e: Exception) {
                _uiState.value = PlanUiState.Error("Failed: ${e.message}")
            }
        }
    }

    fun acceptCurrentPlan() {
        if (currentPlanId != -1L) {
            viewModelScope.launch {
                // Activate in DB
                repository.activatePlan(currentPlanId)
                // Update UI
                _isPlanAccepted.value = true
            }
        }

        fun resetState() {
            _uiState.value = PlanUiState.Empty
            _isPlanAccepted.value = false
        }
    }
}