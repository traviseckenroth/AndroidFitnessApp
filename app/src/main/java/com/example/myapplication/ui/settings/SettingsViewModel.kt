package com.example.myapplication.ui.settings

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.UserPreferencesRepository
import com.example.myapplication.data.repository.AuthRepository
import com.example.myapplication.data.repository.HealthConnectManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val healthConnectManager: HealthConnectManager,
    private val userPrefs: UserPreferencesRepository
) : ViewModel() {

    val gymType = userPrefs.gymType
    val excludedEquipment = userPrefs.excludedEquipment

    val isHealthConnected = mutableStateOf(false)
    val permissions = healthConnectManager.permissions // FIXED: Exposed permissions

    fun logout(onLogoutSuccess: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout() // FIXED: Use correct method name
            onLogoutSuccess()
        }
    }

    fun checkPermissions() {
        viewModelScope.launch {
            isHealthConnected.value = healthConnectManager.hasPermissions()
        }
    }

    fun setGymType(type: String) {
        viewModelScope.launch {
            userPrefs.saveGymType(type)
            when(type) {
                "Commercial" -> {
                    // Reset: Uncheck everything (User has everything)
                    val allEquipment = listOf(
                        "Barbell", "Dumbbell", "Kettlebell", "Cable", "Machine",
                        "Smith Machine", "Pull Up Bar", "Dip Station", "Bench",
                        "Squat Rack", "Leg Press", "EZ Bar"
                    )
                    allEquipment.forEach { userPrefs.toggleEquipmentExclusion(it, false) }
                }
                "Home Gym" -> {
                    // Home usually lacks Machines and specialized cables
                    listOf("Machine", "Smith Machine", "Leg Press", "Cable").forEach {
                        userPrefs.toggleEquipmentExclusion(it, true)
                    }
                    // Usually has these:
                    listOf("Barbell", "Dumbbell", "Bench", "Squat Rack", "Pull Up Bar").forEach {
                        userPrefs.toggleEquipmentExclusion(it, false)
                    }
                }
                "Limited" -> {
                    // Hotel/Limited: No large equipment.
                    // FIX: Expanded list to exclude ALL large equipment
                    listOf(
                        "Barbell", "Cable", "Machine", "Smith Machine",
                        "Leg Press", "Squat Rack", "EZ Bar", "Dip Station"
                    ).forEach { userPrefs.toggleEquipmentExclusion(it, true) }

                    // Ensure Dumbbells are available
                    userPrefs.toggleEquipmentExclusion("Dumbbell", false)
                }
            }
        }
    }

    fun toggleEquipment(equipment: String, isExcluded: Boolean) {
        viewModelScope.launch {
            userPrefs.toggleEquipmentExclusion(equipment, isExcluded)
        }
    }
}