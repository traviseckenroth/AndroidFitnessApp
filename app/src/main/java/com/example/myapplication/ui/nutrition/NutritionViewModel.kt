package com.example.myapplication.ui.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.NutritionPlan
import com.example.myapplication.data.local.FoodLogEntity
import com.example.myapplication.data.local.UserPreferencesRepository
import com.example.myapplication.data.remote.BedrockClient
import com.example.myapplication.data.remote.MacroSummary
import com.example.myapplication.data.repository.NutritionRepository
import com.example.myapplication.data.repository.PlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface NutritionUiState {
    object Loading : NutritionUiState
    data class Success(val plan: NutritionPlan, val alignedGoal: String = "") : NutritionUiState
    object NoExercisePlan : NutritionUiState
    object Empty : NutritionUiState
    data class Error(val msg: String) : NutritionUiState
}

@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val nutritionRepository: NutritionRepository,
    private val planRepository: PlanRepository,
    private val bedrockClient: BedrockClient,
    private val userPrefs: UserPreferencesRepository
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MacroSummary(0, 0, 0, 0))

    private val _isLogging = MutableStateFlow(false)
    val isLogging = _isLogging.asStateFlow()

    private val _recentFoods = MutableStateFlow<List<FoodLogEntity>>(emptyList())
    val recentFoods = _recentFoods.asStateFlow()

    init {
        generateNutritionTiedToPlan()
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

    private fun generateNutritionTiedToPlan() {
        viewModelScope.launch {
            _uiState.value = NutritionUiState.Loading

            // 1. Fetch the Active Workout Plan
            val activePlan = planRepository.getActivePlan()

            if (activePlan == null) {
                _uiState.value = NutritionUiState.NoExercisePlan
                return@launch
            }

            val strategy = when (activePlan.goal) {
                "Hypertrophy" -> "Caloric Surplus (+500 kcal) for mTOR stimulation."
                "Body Sculpting" -> "Caloric Deficit (-500 kcal) with high protein (3.0g/kg)."
                else -> "Maintenance calories for performance."
            }

            // 2. Map Workout Program Type to Nutritional Paradigm
            val nutritionStrategy = when (activePlan.programType) {
                "Hypertrophy" -> "Caloric Surplus (+300-500 kcal). Evenly distributed protein boluses to stimulate mTOR."
                "Body Sculpting" -> "Caloric Deficit (-300-500 kcal). Extremely high protein to preserve tissue while maximizing fat oxidation."
                "Strength" -> "Maintenance or slight surplus. High carbohydrates for CNS recovery."
                "Endurance" -> "Maintenance. Aggressive carbohydrate periodization and hydration focus."
                else -> "Maintenance calories with balanced macros."
            }

            // 3. Fetch User Stats with safe defaults
            val weight = userPrefs.userWeight.first()
            val height = userPrefs.userHeight.first()
            val age = userPrefs.userAge.first()
            val gender = userPrefs.userGender.first()

            // 4. Generate AI Nutrition Plan
            try {
                // We call Bedrock with the full parameter list required by BedrockClient
                val remotePlan = bedrockClient.generateNutritionPlan(
                    userAge = age,
                    userHeight = height,
                    userWeight = weight,
                    gender = gender,
                    dietType = "Balanced",
                    goalPace = nutritionStrategy, // Override goal pace with the strategy tied to the lifting plan
                    weeklyWorkoutDays = 4,
                    avgWorkoutDurationMins = 60
                )

                // Convert Remote plan to Local NutritionPlan domain model
                val nutritionPlan = NutritionPlan(
                    calories = (remotePlan.calories.filter { it.isDigit() }.toIntOrNull() ?: 2000).toString(),
                    protein = (remotePlan.protein.filter { it.isDigit() }.toIntOrNull() ?: 150).toString(),
                    carbs = (remotePlan.carbs.filter { it.isDigit() }.toIntOrNull() ?: 200).toString(),
                    fats = (remotePlan.fats.filter { it.isDigit() }.toIntOrNull() ?: 70).toString(),
                    explanation = remotePlan.explanation
                )

                _uiState.value = NutritionUiState.Success(nutritionPlan, activePlan.programType)
            } catch (e: Exception) {
                _uiState.value = NutritionUiState.Error("Nutrition generation failed: ${e.message}")
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
