package com.example.myapplication.ui.profile

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.CompletedWorkoutWithExercise
import com.example.myapplication.data.local.UserPreferencesRepository
import com.example.myapplication.data.repository.HealthConnectManager
import com.example.myapplication.data.repository.NutritionRepository
import com.example.myapplication.data.repository.WorkoutExecutionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Helper data classes
private data class Biometrics(
    val height: Int,
    val weight: Double,
    val age: Int,
    val gender: String,
    val activity: String
)

private data class Lifestyle(
    val bodyFat: Double?,
    val diet: String,
    val pace: String
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val nutritionRepository: NutritionRepository,
    private val executionRepository: WorkoutExecutionRepository,
    private val userPrefs: UserPreferencesRepository,
    private val healthConnectManager: HealthConnectManager,
    private val application: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUIState())
    val uiState: StateFlow<ProfileUIState> = _uiState.asStateFlow()
    val requiredPermissions = healthConnectManager.permissions

    init {
        viewModelScope.launch {
            val biometricsFlow = combine(
                userPrefs.userHeight,
                userPrefs.userWeight,
                userPrefs.userAge,
                userPrefs.userGender,
                userPrefs.userActivity
            ) { h, w, a, g, act -> Biometrics(h, w, a, g, act) }

            val lifestyleFlow = combine(
                userPrefs.userBodyFat,
                userPrefs.userDiet,
                userPrefs.userGoalPace
            ) { bf, d, p -> Lifestyle(bf, d, p) }

            // Combine all data sources
            combine(
                executionRepository.getAllCompletedWorkouts(),
                biometricsFlow,
                lifestyleFlow
            ) { completed: List<CompletedWorkoutWithExercise>, bio: Biometrics, life: Lifestyle ->
                val items = completed.map {
                    CompletedWorkoutItem(
                        completedWorkout = it.completedWorkout,
                        exercise = it.exercise
                    )
                }.sortedByDescending { it.completedWorkout.date }

                Triple(items, bio, life)
            }.collect { (items, bio, life) ->
                _uiState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        height = bio.height.toString(),
                        weight = bio.weight.toString(),
                        age = bio.age.toString(),
                        gender = bio.gender,
                        activityLevel = bio.activity,
                        bodyFat = life.bodyFat?.toString() ?: "",
                        dietType = life.diet,
                        goalPace = life.pace
                    )
                }
            }
        }
    }

    fun saveProfile(
        h: Int,
        w: Double,
        a: Int,
        g: String,
        act: String,
        bf: Double?,
        d: String,
        p: String
    ) {
        viewModelScope.launch {
            userPrefs.saveProfile(h, w, a, g, act, bf, d, p)
        }
    }

    fun onSyncClicked(onPermissionRequired: (Set<String>) -> Unit) {
        viewModelScope.launch {
            if (healthConnectManager.hasPermissions()) {
                syncHealthConnect()
            } else {
                onPermissionRequired(requiredPermissions)
            }
        }
    }

    fun syncHealthConnect() {
        viewModelScope.launch {
            // 1. Start Loading
            _uiState.update { it.copy(isHealthConnectSyncing = true) }

            try {
                // FIX: Define isLinked variable here so it can be used below
                val isLinked = healthConnectManager.hasPermissions()

                // 2. Perform Sync (Simulated delay or actual fetch)
                delay(1000)

                // 3. Get Current Time and Update State
                val formatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm")
                val currentTimestamp = LocalDateTime.now().format(formatter)

                _uiState.update {
                    it.copy(
                        isHealthConnectSyncing = false,
                        isHealthConnectLinked = isLinked, // <--- Now this reference resolves
                        lastSyncTime = currentTimestamp
                    )
                }

                Toast.makeText(application, "Health Connect Synced", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                _uiState.update { it.copy(isHealthConnectSyncing = false) }
                Toast.makeText(application, "Sync Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}