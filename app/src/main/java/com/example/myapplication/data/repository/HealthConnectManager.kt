package com.example.myapplication.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Check availability status
    val availability: Int
        get() = HealthConnectClient.getSdkStatus(context)

    // Check if available on device
    private val healthConnectClient by lazy {
        if (availability == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    private val _isAuthorized = MutableStateFlow(false)
    val isAuthorized: StateFlow<Boolean> = _isAuthorized.asStateFlow()

    // Updated permissions for Bio-Syncing
    val permissions = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(HeightRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class)
    )

    suspend fun hasPermissions(): Boolean {
        val authorized = healthConnectClient?.permissionController?.getGrantedPermissions()?.containsAll(permissions) == true
        _isAuthorized.value = authorized
        return authorized
    }

    fun promptInstall() {
        if (availability == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=com.google.android.apps.healthdata")
                setPackage("com.android.vending")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
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

    suspend fun getLatestHeight(): Double? {
        val client = healthConnectClient ?: return null
        return try {
            // Using a broader time range and logging all records found
            val request = ReadRecordsRequest(
                recordType = HeightRecord::class,
                timeRangeFilter = TimeRangeFilter.after(Instant.EPOCH),
                ascendingOrder = false,
                pageSize = 5
            )
            val response = client.readRecords(request)
            Log.d("HealthConnect", "getLatestHeight: Found ${response.records.size} records")
            response.records.forEach { Log.d("HealthConnect", "Height record: ${it.height.inInches} inches at ${it.time}") }

            val weight = response.records.firstOrNull()?.height?.inInches
            weight
        } catch (e: Exception) {
            Log.e("HealthConnect", "Error reading height", e)
            null
        }
    }

    suspend fun getLatestWeight(): Double? {
        val client = healthConnectClient ?: run {
            Log.e("HealthConnect", "getLatestWeight: healthConnectClient is NULL")
            return null
        }

        return try {
            // 1. Explicitly check permission for Weight
            val granted = client.permissionController.getGrantedPermissions()
            val weightReadPermission = HealthPermission.getReadPermission(WeightRecord::class)
            Log.d("HealthConnect", "getLatestWeight: Checking permission $weightReadPermission. Granted: ${granted.contains(weightReadPermission)}")

            if (!granted.contains(weightReadPermission)) {
                Log.e("HealthConnect", "getLatestWeight: Weight read permission NOT granted according to SDK")
                return null
            }

            // 2. Query with after(EPOCH) which is standard for "all data"
            val request = ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.after(Instant.EPOCH),
                ascendingOrder = false,
                pageSize = 10
            )
            val response = client.readRecords(request)
            Log.d("HealthConnect", "getLatestWeight: Found ${response.records.size} records")

            response.records.forEach { record ->
                Log.d("HealthConnect", "getLatestWeight: Found record: ${record.weight.inPounds} lbs at ${record.time} from ${record.metadata.dataOrigin.packageName}")
            }

            val weight = response.records.firstOrNull()?.weight?.inPounds
            Log.d("HealthConnect", "getLatestWeight: Returning $weight lbs")
            weight
        } catch (e: Exception) {
            Log.e("HealthConnect", "getLatestWeight: SDK Error", e)
            null
        }
    }

    /**
     * Gets the sleep duration for the last 24 hours.
     */
    suspend fun getLastNightSleepDuration(): Duration {
        val endTime = Instant.now()
        val startTime = endTime.minus(24, ChronoUnit.HOURS)
        return getDailySleepDuration(startTime, endTime)
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
                startZoneOffset = ZoneOffset.UTC,
                endTime = endTime,
                endZoneOffset = ZoneOffset.UTC,
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                title = title,
                metadata = Metadata(clientRecordId = "workout_$workoutId")
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
