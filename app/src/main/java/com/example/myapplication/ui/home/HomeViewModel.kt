// app/src/main/java/com/example/myapplication/ui/home/HomeViewModel.kt
package com.example.myapplication.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.DailyWorkoutEntity
import com.example.myapplication.data.repository.PlanRepository
import com.example.myapplication.data.remote.BedrockClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import android.util.Log
import com.example.myapplication.data.local.CompletedWorkoutWithExercise
import com.example.myapplication.ui.navigation.Screen
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: PlanRepository,
    private val bedrockClient: BedrockClient
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    // NEW: Navigation Events
    private val _navigationEvents = MutableSharedFlow<String>()
    val navigationEvents = _navigationEvents.asSharedFlow()

    val workoutDates: StateFlow<List<LocalDate>> = repository.getAllWorkoutDates()
        .map { dates ->
            dates.map { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val dailyWorkout: StateFlow<DailyWorkoutEntity?> = _selectedDate
        .flatMapLatest { date ->
            val epochMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            repository.getWorkoutForDate(epochMillis)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun updateSelectedDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun generateRecoverySession(type: String) {
        viewModelScope.launch {
            _isGenerating.value = true
            Log.d("HomeViewModel", "Starting generation for: $type")
            try {
                // FIX: Fallback to latest plan if no active plan
                var activePlan = repository.getActivePlan() ?: repository.getLatestPlan()
                if (activePlan == null) {
                    Log.e("HomeViewModel", "No plan found to link the workout to.")
                    _isGenerating.value = false
                    return@launch
                }
                Log.d("HomeViewModel", ">>> STEP 2: Fetching DB context...")
                val history = repository.getWorkoutHistory().take(1).first()
                val availableExercises = repository.getAllExercises().take(1).first()
                val dateMillis = _selectedDate.value.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

                Log.d("HomeViewModel", ">>> STEP 3: Requesting AI response for goal: ${activePlan.goal}")
                // ROUTING LOGIC: Call the appropriate workflow
                val aiResponse = if (type == "Stretching") {
                    bedrockClient.generateStretchingFlow(activePlan.goal, history, availableExercises)
                } else {
                    bedrockClient.generateAccessoryWorkout(activePlan.goal, history, availableExercises)
                }
                Log.d("HomeViewModel", ">>> STEP 4: AI Response explanation: ${aiResponse.explanation}")
                if (aiResponse.schedule.isNotEmpty()) {
                    val generatedDay = aiResponse.schedule.first()
                    Log.d("HomeViewModel", ">>> STEP 5: Saving workout: ${generatedDay.title}")
                    // Capture workoutId
                    val workoutId = repository.saveSingleDayWorkout(
                        planId = activePlan.planId,
                        date = dateMillis,
                        title = generatedDay.title,
                        exercises = generatedDay.exercises
                    )

                    // Emit Navigation Event
                    Log.d("HomeViewModel", ">>> STEP 6: Navigating to ID: $workoutId")
                    val route = if (type == "Stretching") {
                        _navigationEvents.emit(Screen.StretchingSession.createRoute(workoutId))
                    } else {
                        _navigationEvents.emit(Screen.ActiveWorkout.createRoute(workoutId))
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Generation error: ${e.message}")
            } finally {
                _isGenerating.value = false
            }
        }
    }
}