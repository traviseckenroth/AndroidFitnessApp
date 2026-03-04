package com.example.myapplication.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.WorkoutPlan
import com.example.myapplication.data.repository.PlanProgress
import com.example.myapplication.data.repository.PlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface PlanUiState {
    data class Loading(val thought: String? = null) : PlanUiState
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

    // Reactive Flow for Next Block Eligibility
    val nextBlockNumber: StateFlow<Int?> = repository.getNextBlockNumberFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Reactive Flow for Active Plan Progress
    val planProgress: StateFlow<PlanProgress?> = repository.getActivePlanProgressFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private var currentPlanId: Long = -1L

    init {
        loadActivePlan()
    }

    private fun loadActivePlan() {
        viewModelScope.launch {
            val activePlan = repository.getActivePlan()
            if (activePlan != null) {
                try {
                    val fullPlan = repository.getPlanDetails(activePlan.planId)
                    _uiState.value = PlanUiState.Success(fullPlan)
                    _isPlanAccepted.value = true
                    currentPlanId = activePlan.planId
                } catch (e: Exception) {
                    // silent fail or handle
                }
            }
        }
    }

    fun generatePlan(goal: String, program: String, duration: Float, days: List<String>, block: Int = 1) {
        if (goal.isBlank()) {
            _uiState.value = PlanUiState.Error("Please enter a goal.")
            return
        }

        viewModelScope.launch {
            _uiState.value = PlanUiState.Loading()
            _isPlanAccepted.value = false
            try {
                val planId = withContext(Dispatchers.IO) {
                    repository.generateAndSavePlan(
                        goal = goal,
                        duration = duration.toInt(),
                        days = days,
                        programType = program,
                        block = block,
                        onThoughtReceived = { thought ->
                            _uiState.value = PlanUiState.Loading(thought)
                        }
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

    fun generateNextBlock() {
        viewModelScope.launch {
            val nextBlock = nextBlockNumber.value ?: return@launch
            val activePlan = repository.getActivePlan()
            if (activePlan != null) {
                generatePlan(
                    goal = activePlan.goal,
                    program = activePlan.programType,
                    duration = 1f, 
                    days = listOf("Mon", "Wed", "Fri"),
                    block = nextBlock
                )
            }
        }
    }

    fun acceptCurrentPlan() {
        if (currentPlanId != -1L) {
            viewModelScope.launch {
                repository.activatePlan(currentPlanId)
                _isPlanAccepted.value = true
            }
        }
    }

    fun resetState() {
        _uiState.value = PlanUiState.Empty
        _isPlanAccepted.value = false
    }
}
