package com.example.myapplication.data.local

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// This delegate must be at the top level, outside the class
private val Context.dataStore by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Keys for storing data
    private val HEIGHT_KEY = intPreferencesKey("user_height")
    private val WEIGHT_KEY = doublePreferencesKey("user_weight")
    private val AGE_KEY = intPreferencesKey("user_age")

    // Flows to read data (defaulting to 180cm and 7.6kg if empty)
    val userHeight: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[HEIGHT_KEY] ?: 180
    }

    val userWeight: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[WEIGHT_KEY] ?: 7.6
    }

    val userAge: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[AGE_KEY] ?: 25
    }

    // Function to write data
    suspend fun saveBiometrics(height: Int, weight: Double, age: Int) {
        context.dataStore.edit { prefs: MutablePreferences ->
            prefs[HEIGHT_KEY] = height
            prefs[WEIGHT_KEY] = weight
            prefs[AGE_KEY] = age
        }
    }
}