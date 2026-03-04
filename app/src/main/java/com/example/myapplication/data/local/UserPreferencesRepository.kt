package com.example.myapplication.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

// --- 1. NEW MASTER DATA CLASS ---
data class UserProfile(
    val userName: String = "User",
    val height: Double = 70.0,
    val weight: Double = 160.0,
    val age: Int = 25,
    val gender: String = "Male",
    val bodyFat: Double? = null,
    val diet: String = "Standard",
    val goalPace: String = "Maintain",
    val recoveryScore: Int = 100,
    val gymType: String = "Commercial",
    val excludedEquipment: Set<String> = emptySet(),
    val isDynamicAutoregEnabled: Boolean = true,
    val healthConnectOnboardingShown: Boolean = false,
    val userVoiceSid: Int = 0,
    val aiDailyLimit: Int = 50,
    val aiRequestsToday: Int = 0
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    private object PreferencesKeys {
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_HEIGHT = doublePreferencesKey("user_height")
        val USER_WEIGHT = doublePreferencesKey("user_weight")
        val USER_AGE = intPreferencesKey("user_age")
        val USER_GENDER = stringPreferencesKey("user_gender")
        val USER_BODY_FAT = doublePreferencesKey("user_body_fat")
        val USER_DIET = stringPreferencesKey("user_diet")
        val USER_GOAL_PACE = stringPreferencesKey("user_goal_pace")
        val RECOVERY_SCORE = intPreferencesKey("recovery_score")
        val GYM_TYPE = stringPreferencesKey("gym_type")
        val EXCLUDED_EQUIPMENT = stringSetPreferencesKey("excluded_equipment")
        val HEALTH_CONNECT_ONBOARDING_SHOWN = booleanPreferencesKey("health_connect_onboarding_shown")
        val AI_DAILY_LIMIT = intPreferencesKey("ai_daily_limit")
        val AI_REQUESTS_TODAY = intPreferencesKey("ai_requests_today")
        val LAST_AI_REQUEST_DATE = stringPreferencesKey("last_ai_request_date")
        val USER_VOICE_SID = intPreferencesKey("user_voice_sid")
        val DYNAMIC_AUTOREG = booleanPreferencesKey("dynamic_autoregulation_enabled")
    }

    // --- 2. SINGLE MASTER FLOW ---
    val userProfileFlow: Flow<UserProfile> = dataStore.data.map { prefs ->
        val lastDate = prefs[PreferencesKeys.LAST_AI_REQUEST_DATE]
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val requestsToday = if (lastDate == today) prefs[PreferencesKeys.AI_REQUESTS_TODAY] ?: 0 else 0

        UserProfile(
            userName = prefs[PreferencesKeys.USER_NAME] ?: "User",
            height = prefs.getSafeDouble(PreferencesKeys.USER_HEIGHT, 70.0),
            weight = prefs.getSafeDouble(PreferencesKeys.USER_WEIGHT, 160.0),
            age = prefs[PreferencesKeys.USER_AGE] ?: 25,
            gender = prefs[PreferencesKeys.USER_GENDER] ?: "Male",
            bodyFat = prefs.getSafeDoubleNullable(PreferencesKeys.USER_BODY_FAT),
            diet = prefs[PreferencesKeys.USER_DIET] ?: "Standard",
            goalPace = prefs[PreferencesKeys.USER_GOAL_PACE] ?: "Maintain",
            recoveryScore = prefs[PreferencesKeys.RECOVERY_SCORE] ?: 100,
            gymType = prefs[PreferencesKeys.GYM_TYPE] ?: "Commercial",
            excludedEquipment = prefs[PreferencesKeys.EXCLUDED_EQUIPMENT] ?: emptySet(),
            isDynamicAutoregEnabled = prefs[PreferencesKeys.DYNAMIC_AUTOREG] ?: true,
            healthConnectOnboardingShown = prefs[PreferencesKeys.HEALTH_CONNECT_ONBOARDING_SHOWN] ?: false,
            userVoiceSid = prefs[PreferencesKeys.USER_VOICE_SID] ?: 0,
            aiDailyLimit = prefs[PreferencesKeys.AI_DAILY_LIMIT] ?: 50,
            aiRequestsToday = requestsToday
        )
    }

    // --- KEEP INDIVIDUAL FLOWS TEMPORARILY FOR COMPATIBILITY ---
    val userName: Flow<String> = dataStore.data.map { it[PreferencesKeys.USER_NAME] ?: "User" }
    val recoveryScore: Flow<Int> = dataStore.data.map { it[PreferencesKeys.RECOVERY_SCORE] ?: 100 }
    val userHeight: Flow<Double> = dataStore.data.map { it.getSafeDouble(PreferencesKeys.USER_HEIGHT, 70.0) }
    val userWeight: Flow<Double> = dataStore.data.map { it.getSafeDouble(PreferencesKeys.USER_WEIGHT, 160.0) }
    val userAge: Flow<Int> = dataStore.data.map { it[PreferencesKeys.USER_AGE] ?: 25 }
    val userGender: Flow<String> = dataStore.data.map { it[PreferencesKeys.USER_GENDER] ?: "Male" }
    val userBodyFat: Flow<Double?> = dataStore.data.map { it.getSafeDoubleNullable(PreferencesKeys.USER_BODY_FAT) }
    val userDiet: Flow<String> = dataStore.data.map { it[PreferencesKeys.USER_DIET] ?: "Standard" }
    val userGoalPace: Flow<String> = dataStore.data.map { it[PreferencesKeys.USER_GOAL_PACE] ?: "Maintain" }
    val gymType: Flow<String> = dataStore.data.map { it[PreferencesKeys.GYM_TYPE] ?: "Commercial" }
    val excludedEquipment: Flow<Set<String>> = dataStore.data.map { it[PreferencesKeys.EXCLUDED_EQUIPMENT] ?: emptySet() }
    val healthConnectOnboardingShown: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.HEALTH_CONNECT_ONBOARDING_SHOWN] ?: false }
    val userVoiceSid: Flow<Int> = dataStore.data.map { it[PreferencesKeys.USER_VOICE_SID] ?: 0 }
    val aiDailyLimit: Flow<Int> = dataStore.data.map { it[PreferencesKeys.AI_DAILY_LIMIT] ?: 50 }
    val aiRequestsToday: Flow<Int> = dataStore.data.map {
        val lastDate = it[PreferencesKeys.LAST_AI_REQUEST_DATE]
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        if (lastDate == today) it[PreferencesKeys.AI_REQUESTS_TODAY] ?: 0 else 0
    }
    val isDynamicAutoregEnabled: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.DYNAMIC_AUTOREG] ?: true }

    // --- MUTATION FUNCTIONS REMAIN UNCHANGED ---
    suspend fun setDynamicAutoregEnabled(enabled: Boolean) { dataStore.edit { it[PreferencesKeys.DYNAMIC_AUTOREG] = enabled } }
    suspend fun saveUserName(name: String) { dataStore.edit { it[PreferencesKeys.USER_NAME] = name } }
    suspend fun incrementAiUsage() {
        dataStore.edit { preferences ->
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val lastDate = preferences[PreferencesKeys.LAST_AI_REQUEST_DATE]
            if (lastDate == today) {
                val current = preferences[PreferencesKeys.AI_REQUESTS_TODAY] ?: 0
                preferences[PreferencesKeys.AI_REQUESTS_TODAY] = current + 1
            } else {
                preferences[PreferencesKeys.LAST_AI_REQUEST_DATE] = today
                preferences[PreferencesKeys.AI_REQUESTS_TODAY] = 1
            }
        }
    }
    suspend fun saveProfile(h: Double, w: Double, a: Int, g: String, bf: Double?, d: String, p: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USER_HEIGHT] = h
            preferences[PreferencesKeys.USER_WEIGHT] = w
            preferences[PreferencesKeys.USER_AGE] = a
            preferences[PreferencesKeys.USER_GENDER] = g
            if (bf != null) preferences[PreferencesKeys.USER_BODY_FAT] = bf
            preferences[PreferencesKeys.USER_DIET] = d
            preferences[PreferencesKeys.USER_GOAL_PACE] = p
        }
    }
    suspend fun updateWeight(weight: Double) { dataStore.edit { it[PreferencesKeys.USER_WEIGHT] = weight } }
    suspend fun updateHeight(height: Double) { dataStore.edit { it[PreferencesKeys.USER_HEIGHT] = height } }
    suspend fun updateRecoveryScore(score: Int) { dataStore.edit { it[PreferencesKeys.RECOVERY_SCORE] = score.coerceIn(0, 100) } }
    suspend fun saveGymType(type: String) { dataStore.edit { it[PreferencesKeys.GYM_TYPE] = type } }
    suspend fun toggleEquipmentExclusion(equipment: String, isExcluded: Boolean) {
        dataStore.edit { preferences ->
            val currentSet = preferences[PreferencesKeys.EXCLUDED_EQUIPMENT] ?: emptySet()
            preferences[PreferencesKeys.EXCLUDED_EQUIPMENT] = if (isExcluded) currentSet + equipment else currentSet - equipment
        }
    }
    suspend fun setHealthConnectOnboardingShown(shown: Boolean) { dataStore.edit { it[PreferencesKeys.HEALTH_CONNECT_ONBOARDING_SHOWN] = shown } }
    suspend fun saveUserVoiceSid(sid: Int) { dataStore.edit { it[PreferencesKeys.USER_VOICE_SID] = sid } }

    private fun Preferences.getSafeDouble(key: Preferences.Key<Double>, default: Double): Double {
        val value = asMap().entries.find { it.key.name == key.name }?.value
        return (value as? Number)?.toDouble() ?: default
    }

    private fun Preferences.getSafeDoubleNullable(key: Preferences.Key<Double>): Double? {
        val value = asMap().entries.find { it.key.name == key.name }?.value
        return (value as? Number)?.toDouble()
    }
}