// app/src/main/java/com/example/myapplication/ui/profile/ProfileViewModel.kt
package com.example.myapplication.ui.profile

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.CompletedWorkoutWithExercise
import com.example.myapplication.data.local.MemoryDao
import com.example.myapplication.data.local.UserMemoryEntity
import com.example.myapplication.data.local.UserPreferencesRepository
import com.example.myapplication.data.repository.WorkoutExecutionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    private val memoryDao: MemoryDao,
    private val application: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUIState())
    val uiState: StateFlow<ProfileUIState> = _uiState.asStateFlow()

    val activeLimitations: StateFlow<List<UserMemoryEntity>> = memoryDao.getLimitationsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun removeLimitation(memory: UserMemoryEntity) {
        viewModelScope.launch {
            memoryDao.deleteMemory(memory)
        }
    }

    init {
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

            val homeEquipmentFlow = userPrefs.userHomeEquipment

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
                aiUsageFlow,
                homeEquipmentFlow
            ) { completed, bio, life, usage, equipment ->
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
                        aiDailyLimit = usage.limit,
                        homeEquipment = equipment
                    )
                }
            }.collect { }
        }
    }

    fun toggleHomeEquipment(item: String) {
        val current = _uiState.value.homeEquipment.toMutableSet()
        if (item == "None (Bodyweight Only)") {
            current.clear()
            current.add(item)
        } else {
            current.remove("None (Bodyweight Only)")
            if (current.contains(item)) current.remove(item) else current.add(item)
        }
        if (current.isEmpty()) current.add("None (Bodyweight Only)")

        viewModelScope.launch {
            userPrefs.updateHomeEquipment(current)
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
            userPrefs.saveProfile(h, w, a, g, bf, d, "")
        }
    }
}