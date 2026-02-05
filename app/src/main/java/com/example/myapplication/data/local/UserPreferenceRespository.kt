package com.example.myapplication.data.local

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Delegate must be at top level
private val Context.dataStore by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Standard Biometrics
    private val HEIGHT_KEY = intPreferencesKey("user_height")
    private val WEIGHT_KEY = doublePreferencesKey("user_weight")
    private val AGE_KEY = intPreferencesKey("user_age")

    // New Advanced Fields
    private val GENDER_KEY = stringPreferencesKey("user_gender")
    private val ACTIVITY_KEY = stringPreferencesKey("user_activity")
    private val BODY_FAT_KEY = doublePreferencesKey("user_body_fat")
    private val DIET_KEY = stringPreferencesKey("user_diet")
    private val GOAL_PACE_KEY = stringPreferencesKey("user_goal_pace")

    // Flows
    val userHeight: Flow<Int> = context.dataStore.data.map { it[HEIGHT_KEY] ?: 180 }
    val userWeight: Flow<Double> = context.dataStore.data.map { it[WEIGHT_KEY] ?: 75.0 }
    val userAge: Flow<Int> = context.dataStore.data.map { it[AGE_KEY] ?: 25 }

    val userGender: Flow<String> = context.dataStore.data.map { it[GENDER_KEY] ?: "Male" }
    val userActivity: Flow<String> = context.dataStore.data.map { it[ACTIVITY_KEY] ?: "Sedentary" }
    val userBodyFat: Flow<Double?> = context.dataStore.data.map { it[BODY_FAT_KEY] }
    val userDiet: Flow<String> = context.dataStore.data.map { it[DIET_KEY] ?: "Standard" }
    val userGoalPace: Flow<String> = context.dataStore.data.map { it[GOAL_PACE_KEY] ?: "Maintain" }

    suspend fun saveProfile(
        height: Int, weight: Double, age: Int,
        gender: String, activity: String, bodyFat: Double?,
        diet: String, pace: String
    ) {
        context.dataStore.edit { prefs: MutablePreferences ->
            prefs[HEIGHT_KEY] = height
            prefs[WEIGHT_KEY] = weight
            prefs[AGE_KEY] = age
            prefs[GENDER_KEY] = gender
            prefs[ACTIVITY_KEY] = activity

            if (bodyFat != null) {
                prefs[BODY_FAT_KEY] = bodyFat
            } else {
                prefs.remove(BODY_FAT_KEY)
            }

            prefs[DIET_KEY] = diet
            prefs[GOAL_PACE_KEY] = pace
        }
    }

    // Legacy support if needed
    suspend fun saveBiometrics(height: Int, weight: Double, age: Int) {
        context.dataStore.edit { prefs ->
            prefs[HEIGHT_KEY] = height
            prefs[WEIGHT_KEY] = weight
            prefs[AGE_KEY] = age
        }
    }
}