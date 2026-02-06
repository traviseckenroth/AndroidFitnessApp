package com.example.myapplication.ui.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.NutritionPlan
import com.example.myapplication.data.local.FoodLogEntity
import com.example.myapplication.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface NutritionUiState {
    object Loading : NutritionUiState
    data class Success(val plan: NutritionPlan) : NutritionUiState
    object Empty : NutritionUiState // Renamed from Locked
    data class Error(val msg: String) : NutritionUiState
}

@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val repository: WorkoutRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<NutritionUiState>(NutritionUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _foodLogs = MutableStateFlow<List<FoodLogEntity>>(emptyList())
    val foodLogs = _foodLogs.asStateFlow()

    private val _isLogging = MutableStateFlow(false)
    val isLogging = _isLogging.asStateFlow()

    init {
        loadNutrition()
        loadFoodLogs()
    }

    private fun loadFoodLogs() {
        viewModelScope.launch {
            repository.getTodayFoodLogs().collect {
                _foodLogs.value = it
            }
        }
    }

    fun logFood(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _isLogging.value = true
            try {
                repository.logFood(query)
            } catch (e: Exception) {
                // handle error
            } finally {
                _isLogging.value = false
            }
        }
    }

    private fun loadNutrition() {
        viewModelScope.launch {
            _uiState.value = NutritionUiState.Loading
            try {
                val plan = repository.getPlanDetails(0)
                if (plan.nutrition != null) {
                    _uiState.value = NutritionUiState.Success(plan.nutrition!!)
                } else {
                    _uiState.value = NutritionUiState.Empty
                }
            } catch (e: Exception) {
                _uiState.value = NutritionUiState.Empty
            }
        }
    }

    fun generateNutrition() {
        viewModelScope.launch {
            _uiState.value = NutritionUiState.Loading
            try {
                val newPlan = repository.generateAndSaveNutrition()
                _uiState.value = NutritionUiState.Success(newPlan)
            } catch (e: Exception) {
                _uiState.value = NutritionUiState.Error(e.message ?: "Failed")
            }
        }
    }
}