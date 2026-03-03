package com.example.myapplication.ui.nutrition

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.NutritionPlan
import com.example.myapplication.data.local.FoodLogEntity
import com.example.myapplication.data.local.UserPreferencesRepository
import com.example.myapplication.data.local.WorkoutPlanEntity
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
import org.json.JSONObject
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
        loadNutritionData()
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

    private fun loadNutritionData() {
        viewModelScope.launch {
            _uiState.value = NutritionUiState.Loading

            val activePlan = planRepository.getActivePlan()

            if (activePlan == null) {
                _uiState.value = NutritionUiState.NoExercisePlan
                return@launch
            }

            // CACHE CHECK
            if (!activePlan.nutritionJson.isNullOrBlank()) {
                try {
                    val json = JSONObject(activePlan.nutritionJson)
                    val cachedExplanation = json.optString("explanation", "")

                    if (cachedExplanation.contains("error", ignoreCase = true) || cachedExplanation.isBlank()) {
                        Log.w("NutritionVM", "Cache contained an error. Stopping auto-generation loop.")
                        // FIX: Do not auto-spam the API. Show the Empty state so user can click "Generate" manually.
                        _uiState.value = NutritionUiState.Empty
                        return@launch
                    } else {
                        val cachedPlan = NutritionPlan(
                            calories = json.optString("calories", "2000"),
                            protein = json.optString("protein", "150"),
                            carbs = json.optString("carbs", "200"),
                            fats = json.optString("fats", "70"),
                            explanation = cachedExplanation
                        )
                        _uiState.value = NutritionUiState.Success(cachedPlan, activePlan.programType)
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.e("NutritionVM", "Failed to parse cached nutrition", e)
                    _uiState.value = NutritionUiState.Empty
                    return@launch
                }
            }

            // Only auto-generate if it's completely empty (first time setting up the plan)
            generateAndCacheNutrition(activePlan)
        }
    }

    private suspend fun generateAndCacheNutrition(activePlan: WorkoutPlanEntity) {
        try {
            val nutritionStrategy = when (activePlan.programType) {
                "Hypertrophy" -> "Caloric Surplus (+300-500 kcal). Evenly distributed protein boluses to stimulate mTOR."
                "Body Sculpting" -> "Caloric Deficit (-300-500 kcal). Extremely high protein to preserve tissue while maximizing fat oxidation."
                "Strength" -> "Maintenance or slight surplus. High carbohydrates for CNS recovery."
                "Endurance" -> "Maintenance. Aggressive carbohydrate periodization and hydration focus."
                else -> "Maintenance calories with balanced macros."
            }

            val weight = userPrefs.userWeight.first()
            val height = userPrefs.userHeight.first()
            val age = userPrefs.userAge.first()
            val gender = userPrefs.userGender.first()

            val remotePlan = bedrockClient.generateNutritionPlan(
                userAge = age,
                userHeight = height,
                userWeight = weight,
                gender = gender,
                dietType = "Balanced",
                goalPace = nutritionStrategy,
                weeklyWorkoutDays = 4,
                avgWorkoutDurationMins = 60
            )

            // FIX: Never save a failed AI response to the database
            if (remotePlan.explanation.contains("Error", ignoreCase = true)) {
                _uiState.value = NutritionUiState.Error("AI Generation Failed. Please try again.")
                return
            }

            // FIX: Bulletproof parsing. Safely handles "180.5g", "180", or "180g" without crashing.
            fun parseSafeInt(value: String, default: Int): String {
                val match = Regex("([0-9]+(?:\\.[0-9]+)?)").find(value)
                val numStr = match?.value ?: return default.toString()
                return numStr.toDoubleOrNull()?.toInt()?.toString() ?: default.toString()
            }

            val nutritionPlan = NutritionPlan(
                calories = parseSafeInt(remotePlan.calories, 2000),
                protein = parseSafeInt(remotePlan.protein, 150),
                carbs = parseSafeInt(remotePlan.carbs, 200),
                fats = parseSafeInt(remotePlan.fats, 70),
                explanation = remotePlan.explanation
            )

            val jsonObj = JSONObject().apply {
                put("calories", nutritionPlan.calories)
                put("protein", nutritionPlan.protein)
                put("carbs", nutritionPlan.carbs)
                put("fats", nutritionPlan.fats)
                put("explanation", nutritionPlan.explanation)
            }

            val updatedPlan = activePlan.copy(nutritionJson = jsonObj.toString())
            planRepository.updatePlan(updatedPlan)

            _uiState.value = NutritionUiState.Success(nutritionPlan, activePlan.programType)
        } catch (e: Exception) {
            _uiState.value = NutritionUiState.Error("Nutrition generation failed: ${e.message}")
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
            val activePlan = planRepository.getActivePlan()
            if (activePlan != null) {
                generateAndCacheNutrition(activePlan)
            } else {
                _uiState.value = NutritionUiState.NoExercisePlan
            }
        }
    }
}