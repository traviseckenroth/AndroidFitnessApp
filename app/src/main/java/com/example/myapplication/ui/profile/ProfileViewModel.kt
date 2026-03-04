package com.example.myapplication.ui.profile

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.CompletedWorkoutWithExercise
import com.example.myapplication.data.local.UserPreferencesRepository
import com.example.myapplication.data.repository.HealthConnectManager
import com.example.myapplication.data.repository.WorkoutExecutionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// Helper data classes (Defined strictly in this file)
private data class Biometrics(
    val userName: String,
    val height: Double,
    val weight: Double,
    val age: Int,
    val gender: String
)

private data class Lifestyle(
    val bodyFat: Double?,
    val diet: String
)

private data class AiUsage(
    val today: Int,
    val limit: Int
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val executionRepository: WorkoutExecutionRepository,
    private val userPrefs: UserPreferencesRepository,
    private val healthConnectManager: HealthConnectManager,
    private val application: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUIState())
    val uiState: StateFlow<ProfileUIState> = _uiState.asStateFlow()
    val requiredPermissions = healthConnectManager.permissions

    init {
        checkHealthConnectStatus()

        viewModelScope.launch {
            val biometricsFlow = combine(
                userPrefs.userName,
                userPrefs.userHeight,
                userPrefs.userWeight,
                userPrefs.userAge,
                userPrefs.userGender
            ) { name, h, w, a, g ->
                Biometrics(
                    userName = name,
                    height = h,
                    weight = w,
                    age = a,
                    gender = g
                )
            }

            val lifestyleFlow = combine(
                userPrefs.userBodyFat,
                userPrefs.userDiet
            ) { bf, d -> Lifestyle(bf, d) }

            val aiUsageFlow = combine(
                userPrefs.aiRequestsToday,
                userPrefs.aiDailyLimit
            ) { today, limit -> AiUsage(today, limit) }

            combine(
                executionRepository.getAllCompletedWorkouts(),
                biometricsFlow,
                lifestyleFlow,
                aiUsageFlow
            ) { completed: List<CompletedWorkoutWithExercise>, bio: Biometrics, life: Lifestyle, usage: AiUsage ->
                val items = completed.map {
                    CompletedWorkoutItem(
                        completedWorkout = it.completedWorkout,
                        exercise = it.exercise
                    )
                }.sortedByDescending { it.completedWorkout.date }

                _uiState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        userName = bio.userName,
                        height = bio.height.toString(),
                        weight = bio.weight.toString(),
                        age = bio.age.toString(),
                        gender = bio.gender,
                        bodyFat = life.bodyFat?.toString() ?: "",
                        dietType = life.diet,
                        aiRequestsToday = usage.today,
                        aiDailyLimit = usage.limit
                    )
                }
            }.collect { }
        }
    }

    fun checkHealthConnectStatus() {
        viewModelScope.launch {
            val isLinked = healthConnectManager.hasPermissions()
            _uiState.update { it.copy(isHealthConnectLinked = isLinked) }
        }
    }

    fun saveProfile(
        h: Double,
        w: Double,
        a: Int,
        g: String,
        bf: Double?,
        d: String
    ) {
        viewModelScope.launch {
            // We pass empty strings for the removed activity/pace variables to satisfy the repo signature
            userPrefs.saveProfile(h, w, a, g, bf, d, "")
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
            _uiState.update { it.copy(isHealthConnectSyncing = true) }

            try {
                val isLinked = healthConnectManager.hasPermissions()

                val sleepDuration = healthConnectManager.getLastNightSleepDuration()
                val sleepHours = sleepDuration.toMinutes() / 60.0
                val recoveryScore = ((sleepHours / 8.0) * 100).toInt().coerceIn(0, 100)
                userPrefs.updateRecoveryScore(recoveryScore)

                val latestWeight = healthConnectManager.getLatestWeight()
                if (latestWeight != null) {
                    userPrefs.updateWeight(latestWeight)
                }

                val latestHeight = healthConnectManager.getLatestHeight()
                if (latestHeight != null) {
                    userPrefs.updateHeight(latestHeight)
                }

                delay(500)

                val formatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm")
                val currentTimestamp = LocalDateTime.now().format(formatter)

                _uiState.update {
                    it.copy(
                        isHealthConnectSyncing = false,
                        isHealthConnectLinked = isLinked,
                        lastSyncTime = currentTimestamp
                    )
                }

                Toast.makeText(application, "Bio-Sync Complete!", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                _uiState.update { it.copy(isHealthConnectSyncing = false) }
                Toast.makeText(application, "Sync Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}