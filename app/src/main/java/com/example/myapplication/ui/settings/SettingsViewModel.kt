package com.example.myapplication.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.UserPreferencesRepository
import com.example.myapplication.data.repository.AuthRepository
import com.example.myapplication.data.repository.HealthConnectManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
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
    val userVoiceSid = userPrefs.userVoiceSid

    val isHealthConnected: StateFlow<Boolean> = healthConnectManager.isAuthorized
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val permissions = healthConnectManager.permissions

    init {
        checkPermissions()
    }

    fun logout(onLogoutSuccess: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onLogoutSuccess()
        }
    }

    val isDynamicAutoregEnabled: StateFlow<Boolean> = userPrefs.isDynamicAutoregEnabled
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    fun toggleDynamicAutoreg(enabled: Boolean) {
        viewModelScope.launch {
            userPrefs.setDynamicAutoregEnabled(enabled)
        }
    }

    fun checkPermissions() {
        viewModelScope.launch {
            healthConnectManager.hasPermissions()
        }
    }

    fun setGymType(type: String) {
        viewModelScope.launch {
            userPrefs.saveGymType(type)
            when(type) {
                "Commercial" -> {
                    val allEquipment = listOf(
                        "Barbell", "Dumbbell", "Kettlebell", "Cable", "Machine",
                        "Smith Machine", "Pull Up Bar", "Dip Station", "Bench",
                        "Squat Rack", "Leg Press", "EZ Bar"
                    )
                    allEquipment.forEach { userPrefs.toggleEquipmentExclusion(it, false) }
                }
                "Home Gym" -> {
                    listOf("Machine", "Smith Machine", "Leg Press", "Cable").forEach {
                        userPrefs.toggleEquipmentExclusion(it, true)
                    }
                    listOf("Barbell", "Dumbbell", "Bench", "Squat Rack", "Pull Up Bar").forEach {
                        userPrefs.toggleEquipmentExclusion(it, false)
                    }
                }
                "Limited" -> {
                    listOf(
                        "Barbell", "Cable", "Machine", "Smith Machine",
                        "Leg Press", "Squat Rack", "EZ Bar", "Dip Station"
                    ).forEach { userPrefs.toggleEquipmentExclusion(it, true) }
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

    fun setCoachVoice(sid: Int) {
        viewModelScope.launch {
            userPrefs.saveUserVoiceSid(sid)
        }
    }
}
