package com.example.myapplication.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.UserPreferencesRepository
import com.example.myapplication.data.repository.AuthRepository
import com.example.myapplication.data.repository.HealthConnectManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val healthConnectManager: HealthConnectManager,
    private val userPrefs: UserPreferencesRepository
) : ViewModel() {

    // --- Gym Settings State ---
    val gymType = userPrefs.gymType
    val excludedEquipment = userPrefs.excludedEquipment

    // --- Actions ---

    fun logout(onLogoutSuccess: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            onLogoutSuccess()
        }
    }

    fun syncHealthConnect() {
        viewModelScope.launch {
            healthConnectManager.sync()
        }
    }

    fun setGymType(type: String) {
        viewModelScope.launch {
            userPrefs.saveGymType(type)

            // Auto-configure exclusions based on preset
            when(type) {
                "Commercial" -> {
                    // Reset all exclusions (assume everything is available)
                    listOf("Barbell", "Dumbbell", "Cable", "Machine").forEach {
                        userPrefs.toggleEquipmentExclusion(it, false)
                    }
                }
                "Limited" -> {
                    // Exclude heavy machinery defaults
                    listOf("Barbell", "Cable", "Machine", "Smith Machine", "Leg Press").forEach {
                        userPrefs.toggleEquipmentExclusion(it, true)
                    }
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