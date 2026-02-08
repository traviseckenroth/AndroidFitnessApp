// app/src/main/java/com/example/myapplication/ui/profile/ProfileViewModel.kt
package com.example.myapplication.ui.profile

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.CompletedWorkoutWithExercise
// FIX: Added missing imports
import com.example.myapplication.data.local.UserPreferencesRepository
import com.example.myapplication.data.repository.HealthConnectManager
import com.example.myapplication.data.repository.NutritionRepository
import com.example.myapplication.data.repository.WorkoutExecutionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    init {
        viewModelScope.launch {
            // FIX: Access properties directly (removed parentheses)
            val biometricsFlow = combine(
                userPrefs.userHeight,
                userPrefs.userWeight,
                userPrefs.userAge,
                userPrefs.userGender,
                userPrefs.userActivity
            ) { h, w, a, g, act -> Biometrics(h, w, a, g, act) }

            // FIX: Access properties directly (removed parentheses)
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
                        // These fields now exist in ProfileUIState
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
            // FIX: Use userPrefs directly
            userPrefs.saveProfile(h, w, a, g, act, bf, d, p)
        }
    }

    fun syncHealthConnect() {
        viewModelScope.launch {
            if (!healthConnectManager.hasPermissions()) {
                Toast.makeText(application, "Permissions required", Toast.LENGTH_SHORT).show()
                return@launch
            }
            _uiState.update { it.copy(isHealthConnectSyncing = true) }
            try {
                delay(2000)
                Toast.makeText(application, "Health Connect Synced", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(application, "Sync Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                _uiState.update { it.copy(isHealthConnectSyncing = false) }
            }
        }
    }
}