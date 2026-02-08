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
                "Commercial" -> listOf("Barbell", "Dumbbell", "Cable", "Machine").forEach { userPrefs.toggleEquipmentExclusion(it, false) }
                "Limited" -> listOf("Barbell", "Cable", "Machine", "Smith Machine", "Leg Press").forEach { userPrefs.toggleEquipmentExclusion(it, true) }
            }
        }
    }

    fun toggleEquipment(equipment: String, isExcluded: Boolean) {
        viewModelScope.launch {
            userPrefs.toggleEquipmentExclusion(equipment, isExcluded)
        }
    }
}