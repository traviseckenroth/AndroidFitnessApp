package com.example.myapplication.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.myapplication.data.local.WorkoutDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.TimeUnit

@HiltWorker
class TelemetryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val workoutDao: WorkoutDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val history = workoutDao.getCompletedWorkoutsWithExercise().first()
            if (history.isEmpty()) return Result.success()

            val anonymousDeviceId = UUID.randomUUID().toString()

            val mlPayload = history.map {
                mapOf(
                    "anonUserId" to anonymousDeviceId,
                    "timestamp" to it.completedWorkout.date.toString(),
                    "muscleGroup" to (it.exercise.muscleGroup ?: "Unknown"),
                    "reps" to it.completedWorkout.reps.toString(),
                    "weight" to it.completedWorkout.weight.toString(),
                    "rpe" to it.completedWorkout.rpe.toString()
                )
            }

            val jsonPayload = Json.encodeToString(mlPayload)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresCharging(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<TelemetryWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "DailyMLTelemetryUpload",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
