package com.example.myapplication.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.myapplication.data.repository.WorkoutExecutionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant

@HiltWorker
class WorkoutSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: WorkoutExecutionRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val workoutId = inputData.getLong("workoutId", -1)
        val startTime = inputData.getString("startTime")?.let { Instant.parse(it) }
        val endTime = inputData.getString("endTime")?.let { Instant.parse(it) }
        val calories = inputData.getDouble("calories", 0.0)

        if (workoutId == -1L || startTime == null || endTime == null) return Result.failure()

        return try {
            repository.syncWithHealthConnect(workoutId, startTime, endTime, calories)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
