package com.example.myapplication.ui.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.NutritionPlan
import com.example.myapplication.data.local.FoodLogEntity
import com.example.myapplication.data.remote.MacroSummary // <--- IMPORT ADDED
import com.example.myapplication.data.repository.NutritionRepository
import com.example.myapplication.data.repository.PlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted // <--- IMPORT ADDED
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map // <--- IMPORT ADDED
import kotlinx.coroutines.flow.stateIn // <--- IMPORT ADDED
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface NutritionUiState {
    object Loading : NutritionUiState
    data class Success(val plan: NutritionPlan) : NutritionUiState
    object NoExercisePlan : NutritionUiState
    object Empty : NutritionUiState // Renamed from Locked
    data class Error(val msg: String) : NutritionUiState
}

@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val nutritionRepository: NutritionRepository,
    private val planRepository: PlanRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<NutritionUiState>(NutritionUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _foodLogs = MutableStateFlow<List<FoodLogEntity>>(emptyList())
    val foodLogs = _foodLogs.asStateFlow()

    val consumedMacros = _foodLogs.map { logs ->
        MacroSummary(
            calories = logs.sumOf { it.totalCalories },
            protein = logs.sumOf { it.totalProtein },
            carbs = logs.sumOf { it.totalCarbs },
            fats = logs.sumOf { it.totalFats }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MacroSummary(0,0,0,0))

    private val _isLogging = MutableStateFlow(false)
    val isLogging = _isLogging.asStateFlow()

    private val _recentFoods = MutableStateFlow<List<FoodLogEntity>>(emptyList())
    val recentFoods = _recentFoods.asStateFlow()

    init {
        loadNutrition()
        loadFoodLogs()
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            nutritionRepository.getRecentFoodLogs().collect { _recentFoods.value = it }
        }
    }

    private fun loadFoodLogs() {
        viewModelScope.launch {
            nutritionRepository.getTodayFoodLogs().collect {
                _foodLogs.value = it
            }
        }
    }
    private fun loadNutrition() {
        viewModelScope.launch {
            _uiState.value = NutritionUiState.Loading
            try {
                // Fetch the plan. We check if an exercise plan exists at all.
                val plan = planRepository.getPlanDetails(0)

                // Logic: If the plan object itself is empty/null (no exercise plan),
                // show NoExercisePlan. If it exists but has no nutrition, show Empty.
                if (plan.weeks.isEmpty()) {
                    _uiState.value = NutritionUiState.NoExercisePlan
                } else if (plan.nutrition != null) {
                    _uiState.value = NutritionUiState.Success(plan.nutrition!!)
                } else {
                    _uiState.value = NutritionUiState.Empty
                }
            } catch (e: Exception) {
                // Actual data/network errors now go to Error state instead of defaulting to Empty
                _uiState.value = NutritionUiState.Error("Failed to connect to Bedrock: ${e.message}")
            }
        }
    }


    fun logFood(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _isLogging.value = true
            try {
                nutritionRepository.logFood(query)
            } catch (e: Exception) {
                // handle error
            } finally {
                _isLogging.value = false
            }
        }
    }
    fun logManual(name: String, cals: String, pro: String, carb: String, fat: String, meal: String) {
        viewModelScope.launch {
            nutritionRepository.logManualFood(
                name,
                cals.toIntOrNull() ?: 0,
                pro.toIntOrNull() ?: 0,
                carb.toIntOrNull() ?: 0,
                fat.toIntOrNull() ?: 0,
                meal
            )
        }
    }
    fun generateNutrition() {
        viewModelScope.launch {
            _uiState.value = NutritionUiState.Loading
            try {
                val newPlan = nutritionRepository.generateAndSaveNutrition()
                _uiState.value = NutritionUiState.Success(newPlan)
            } catch (e: Exception) {
                _uiState.value = NutritionUiState.Error(e.message ?: "Failed")
            }
        }
    }
}