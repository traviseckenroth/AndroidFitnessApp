// app/src/main/java/com/example/myapplication/ui/profile/ProfileViewModel.kt
package com.example.myapplication.ui.profile

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// FIX: Added missing imports
import com.example.myapplication.data.local.UserPreferencesRepository
import com.example.myapplication.data.repository.HealthConnectManager
import com.example.myapplication.data.repository.WorkoutRepository
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
    private val repository: WorkoutRepository,
    private val userPrefs: UserPreferencesRepository,
    private val healthConnectManager: HealthConnectManager,
    private val application: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUIState())
    val uiState: StateFlow<ProfileUIState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val biometricsFlow = combine(
                repository.getUserHeight(),
                repository.getUserWeight(),
                repository.getUserAge(),
                repository.getUserGender(),
                repository.getUserActivity()
            ) { h, w, a, g, act ->
                Biometrics(h, w, a, g, act)
            }

            val lifestyleFlow = combine(
                repository.getUserBodyFat(),
                repository.getUserDiet(),
                repository.getUserGoalPace()
            ) { bf, d, p ->
                Lifestyle(bf, d, p)
            }

            combine(
                repository.getAllCompletedWorkouts(),
                biometricsFlow,
                lifestyleFlow
            ) { completed, bio, life ->
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
        height: Int, weight: Double, age: Int,
        gender: String, activity: String, bodyFat: Double?,
        diet: String, pace: String
    ) {
        viewModelScope.launch {
            repository.saveProfile(height, weight, age, gender, activity, bodyFat, diet, pace)
        }
    }

    fun syncHealthConnect() {
        viewModelScope.launch {
            // FIX: Changed hasAllPermissions() to hasPermissions()
            if (!healthConnectManager.hasPermissions()) {
                Toast.makeText(application, "Permissions required for Health Connect", Toast.LENGTH_SHORT).show()
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

    fun syncGarmin() {
        viewModelScope.launch {
            _uiState.update { it.copy(isGarminSyncing = true) }
            try {
                // Simulate Garmin Sync
                delay(2000)

                // MOCK DATA: Simulate a "Bad Day" or "Good Day"
                // In real app, this comes from Garmin API (Body Battery)
                val simulatedRecovery = (30..100).random()
                userPrefs.saveRecoveryScore(simulatedRecovery)

                _uiState.update { it.copy(isGarminConnected = !it.isGarminConnected) }
                val status = if (_uiState.value.isGarminConnected) "Connected" else "Disconnected"

                val msg = if (simulatedRecovery < 50)
                    "Synced. Recovery Low ($simulatedRecovery%). Workouts will be easier."
                else
                    "Synced. Recovery Good ($simulatedRecovery%)."

                Toast.makeText(application, msg, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(application, "Garmin Connection Failed", Toast.LENGTH_SHORT).show()
            } finally {
                _uiState.update { it.copy(isGarminSyncing = false) }
            }
        }
    }
}