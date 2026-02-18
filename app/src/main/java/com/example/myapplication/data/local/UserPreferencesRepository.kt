package com.example.myapplication.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    private object PreferencesKeys {
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_HEIGHT = intPreferencesKey("user_height")
        val USER_WEIGHT = doublePreferencesKey("user_weight")
        val USER_AGE = intPreferencesKey("user_age")
        val USER_GENDER = stringPreferencesKey("user_gender")
        val USER_ACTIVITY = stringPreferencesKey("user_activity")
        val USER_BODY_FAT = doublePreferencesKey("user_body_fat")
        val USER_DIET = stringPreferencesKey("user_diet")
        val USER_GOAL_PACE = stringPreferencesKey("user_goal_pace")

        // FIXED: Added missing RECOVERY_SCORE key
        val RECOVERY_SCORE = intPreferencesKey("recovery_score")

        val GYM_TYPE = stringPreferencesKey("gym_type")
        val EXCLUDED_EQUIPMENT = stringSetPreferencesKey("excluded_equipment")
    }

    val userName: Flow<String> = dataStore.data.map { it[PreferencesKeys.USER_NAME] ?: "User" }
    val recoveryScore: Flow<Int> = context.dataStore.data.map { it[PreferencesKeys.RECOVERY_SCORE] ?: 100 }

    val userHeight: Flow<Int> = dataStore.data.map { it[PreferencesKeys.USER_HEIGHT] ?: 170 }
    val userWeight: Flow<Double> = dataStore.data.map { it[PreferencesKeys.USER_WEIGHT] ?: 70.0 }
    val userAge: Flow<Int> = dataStore.data.map { it[PreferencesKeys.USER_AGE] ?: 25 }
    val userGender: Flow<String> = dataStore.data.map { it[PreferencesKeys.USER_GENDER] ?: "Male" }
    val userActivity: Flow<String> = dataStore.data.map { it[PreferencesKeys.USER_ACTIVITY] ?: "Moderate" }
    val userBodyFat: Flow<Double?> = dataStore.data.map { it[PreferencesKeys.USER_BODY_FAT] }
    val userDiet: Flow<String> = dataStore.data.map { it[PreferencesKeys.USER_DIET] ?: "Standard" }
    val userGoalPace: Flow<String> = dataStore.data.map { it[PreferencesKeys.USER_GOAL_PACE] ?: "Maintain" }

    val gymType: Flow<String> = dataStore.data.map { it[PreferencesKeys.GYM_TYPE] ?: "Commercial" }
    val excludedEquipment: Flow<Set<String>> = dataStore.data.map { it[PreferencesKeys.EXCLUDED_EQUIPMENT] ?: emptySet() }

    suspend fun saveUserName(name: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USER_NAME] = name
        }
    }

    suspend fun saveProfile(h: Int, w: Double, a: Int, g: String, act: String, bf: Double?, d: String, p: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USER_HEIGHT] = h
            preferences[PreferencesKeys.USER_WEIGHT] = w
            preferences[PreferencesKeys.USER_AGE] = a
            preferences[PreferencesKeys.USER_GENDER] = g
            preferences[PreferencesKeys.USER_ACTIVITY] = act
            if (bf != null) preferences[PreferencesKeys.USER_BODY_FAT] = bf
            preferences[PreferencesKeys.USER_DIET] = d
            preferences[PreferencesKeys.USER_GOAL_PACE] = p
        }
    }

    suspend fun saveGymType(type: String) {
        dataStore.edit { it[PreferencesKeys.GYM_TYPE] = type }
    }

    suspend fun toggleEquipmentExclusion(equipment: String, isExcluded: Boolean) {
        dataStore.edit { preferences ->
            val currentSet = preferences[PreferencesKeys.EXCLUDED_EQUIPMENT] ?: emptySet()
            val newSet = if (isExcluded) {
                currentSet + equipment
            } else {
                currentSet - equipment
            }
            preferences[PreferencesKeys.EXCLUDED_EQUIPMENT] = newSet
        }
    }
}