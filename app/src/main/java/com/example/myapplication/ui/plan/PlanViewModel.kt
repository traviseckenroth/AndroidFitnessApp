package com.example.myapplication.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.WorkoutPlan
import com.example.myapplication.data.repository.PlanRepository
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
    data class Success(val plan: WorkoutPlan) : PlanUiState
    data class Error(val msg: String) : PlanUiState
    object Empty : PlanUiState
}

@HiltViewModel
class PlanViewModel @Inject constructor(
    private val repository: PlanRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlanUiState>(PlanUiState.Empty)
    val uiState: StateFlow<PlanUiState> = _uiState.asStateFlow()

    private val _isPlanAccepted = MutableStateFlow(false)
    val isPlanAccepted = _isPlanAccepted.asStateFlow()

    private val _nextPhaseNumber = MutableStateFlow<Int?>(null)
    val nextPhaseNumber = _nextPhaseNumber.asStateFlow()

    private var currentPlanId: Long = -1L

    init {
        checkNextPhaseEligibility()
    }

    private fun checkNextPhaseEligibility() {
        viewModelScope.launch {
            _nextPhaseNumber.value = repository.getNextPhaseNumber()
        }
    }

    fun generatePlan(goal: String, program: String, duration: Float, days: List<String>, phase: Int = 1) {
        if (goal.isBlank()) {
            _uiState.value = PlanUiState.Error("Please enter a goal.")
            return
        }

        viewModelScope.launch {
            _uiState.value = PlanUiState.Loading
            _isPlanAccepted.value = false
            try {
                val planId = withContext(Dispatchers.IO) {
                    repository.generateAndSavePlan(
                        goal = goal,
                        duration = duration.toInt(),
                        days = days,
                        programType = program,
                        phase = phase
                    )
                }
                currentPlanId = planId
                val fullPlan = withContext(Dispatchers.IO) {
                    repository.getPlanDetails(planId)
                }
                _uiState.value = PlanUiState.Success(fullPlan)

            } catch (e: Exception) {
                _uiState.value = PlanUiState.Error("Failed: ${e.message}")
            }
        }
    }

    fun generateNextPhase() {
        viewModelScope.launch {
            val nextPhase = _nextPhaseNumber.value ?: return@launch
            val activePlan = repository.getActivePlan()
            if (activePlan != null) {
                generatePlan(
                    goal = activePlan.goal,
                    program = activePlan.programType,
                    duration = 1f, 
                    days = listOf("Mon", "Wed", "Fri"),
                    phase = nextPhase
                )
            }
        }
    }

    fun acceptCurrentPlan() {
        if (currentPlanId != -1L) {
            viewModelScope.launch {
                repository.activatePlan(currentPlanId)
                _isPlanAccepted.value = true
                _nextPhaseNumber.value = null // Reset until nearing end of this new phase
            }
        }
    }

    fun resetState() {
        _uiState.value = PlanUiState.Empty
        _isPlanAccepted.value = false
    }
}