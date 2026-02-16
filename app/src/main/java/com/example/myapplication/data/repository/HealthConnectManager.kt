package com.example.myapplication.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord // Added
import androidx.health.connect.client.records.SleepSessionRecord // Added
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.request.ReadRecordsRequest // Added
import androidx.health.connect.client.time.TimeRangeFilter // Added
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord // Added
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration // Added
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Check if available on device
    private val healthConnectClient by lazy {
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    // Updated permissions for Bio-Syncing
    val permissions = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class) // Essential for Bio-Sync
    )

    suspend fun hasPermissions(): Boolean {
        return healthConnectClient?.permissionController?.getGrantedPermissions()?.containsAll(permissions) == true
    }

    suspend fun getDailySleepDuration(startTime: Instant, endTime: Instant): Duration {
        if (healthConnectClient == null || !hasPermissions()) return Duration.ZERO

        return try {
            val request = ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val response = healthConnectClient?.readRecords(request)
            response?.records?.fold(Duration.ZERO) { acc, record ->
                acc.plus(Duration.between(record.startTime, record.endTime))
            } ?: Duration.ZERO
        } catch (e: Exception) {
            Log.e("HealthConnect", "Error reading sleep", e)
            Duration.ZERO
        }
    }

    @SuppressLint("RestrictedApi")
    suspend fun writeWorkout(
        workoutId: Long,
        startTime: Instant,
        endTime: Instant,
        calories: Double,
        title: String
    ) {
        if (healthConnectClient == null || !hasPermissions()) {
            Log.e("HealthConnect", "Client unavailable or permissions missing")
            return
        }

        try {
            // 1. Create the Session Record (The main wrapper)
            val sessionRecord = ExerciseSessionRecord(
                startTime = startTime,
                startZoneOffset = ZoneOffset.UTC, // Or system default
                endTime = endTime,
                endZoneOffset = ZoneOffset.UTC,
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, // Default to strength
                title = title,
                metadata = Metadata(clientRecordId = "workout_$workoutId") // Unique ID prevents dupes
            )

            // 2. Create Calorie Record (Associated with the session)
            val calorieRecord = ActiveCaloriesBurnedRecord(
                startTime = startTime,
                startZoneOffset = ZoneOffset.UTC,
                endTime = endTime,
                endZoneOffset = ZoneOffset.UTC,
                energy = Energy.kilocalories(calories),
                metadata = Metadata(clientRecordId = "cals_$workoutId")
            )

            // 3. Write data
            healthConnectClient?.insertRecords(listOf<Record>(sessionRecord, calorieRecord))
            Log.d("HealthConnect", "Successfully wrote workout: $title")

        } catch (e: Exception) {
            Log.e("HealthConnect", "Error writing workout", e)
        }
    }
}