package com.example.myapplication.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Helper data classes to manage the flow combining limit
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
    private val repository: WorkoutRepository
) : ViewModel() {

    // 1. Combine first batch (5 flows)
    private val _biometricsFlow = combine(
        repository.getUserHeight(),
        repository.getUserWeight(),
        repository.getUserAge(),
        repository.getUserGender(),
        repository.getUserActivity()
    ) { h, w, a, g, act ->
        Biometrics(h, w, a, g, act)
    }

    // 2. Combine second batch (3 flows)
    private val _lifestyleFlow = combine(
        repository.getUserBodyFat(),
        repository.getUserDiet(),
        repository.getUserGoalPace()
    ) { bf, d, p ->
        Lifestyle(bf, d, p)
    }

    // 3. Combine batches with history (3 flows total) - Safe for KSP
    val uiState: StateFlow<ProfileUiState> = combine(
        repository.getAllCompletedWorkouts(),
        _biometricsFlow,
        _lifestyleFlow
    ) { completed, bio, life ->

        val items = completed.map {
            CompletedWorkoutItem(
                completedWorkout = it.completedWorkout,
                exercise = it.exercise
            )
        }.sortedByDescending { it.completedWorkout.date }

        if (items.isEmpty() && bio.height == 0 && bio.weight == 0.0) {
            ProfileUiState.Empty
        } else {
            ProfileUiState.Success(
                completedWorkouts = items,
                height = bio.height,
                weight = bio.weight,
                age = bio.age,
                gender = bio.gender,
                activityLevel = bio.activity,
                bodyFat = life.bodyFat,
                dietType = life.diet,
                goalPace = life.pace
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProfileUiState.Loading
    )

    fun saveProfile(
        height: Int, weight: Double, age: Int,
        gender: String, activity: String, bodyFat: Double?,
        diet: String, pace: String
    ) {
        viewModelScope.launch {
            repository.saveProfile(height, weight, age, gender, activity, bodyFat, diet, pace)
        }
    }
}