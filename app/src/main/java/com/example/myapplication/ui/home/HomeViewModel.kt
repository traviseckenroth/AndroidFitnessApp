// app/src/main/java/com/example/myapplication/ui/home/HomeViewModel.kt
package com.example.myapplication.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.DailyWorkoutEntity
import com.example.myapplication.data.repository.PlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: PlanRepository
) : ViewModel() {

    // UI State: The currently selected date (Defaults to Today)
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    // All workout dates for the calendar view
    val workoutDates: StateFlow<List<LocalDate>> = repository.getAllWorkoutDates()
        .map { dates ->
            dates.map { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Database Logic: When date changes, fetch the workout for that timestamp
    @OptIn(ExperimentalCoroutinesApi::class)
    val dailyWorkout: StateFlow<DailyWorkoutEntity?> = _selectedDate
        .flatMapLatest { date ->
            // Convert LocalDate to Epoch Millis (Start of day)
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
}