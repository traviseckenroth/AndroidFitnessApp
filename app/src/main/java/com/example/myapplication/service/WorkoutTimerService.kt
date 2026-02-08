package com.example.myapplication.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.myapplication.MainActivity
import com.example.myapplication.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// FIXED: Added hasFinished property
data class TimerState(
    val isRunning: Boolean = false,
    val remainingTime: Int = 0,
    val activeExerciseId: Long? = null,
    val hasFinished: Boolean = false
)

class WorkoutTimerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var timerJob: Job? = null

    companion object {
        private val _timerState = MutableStateFlow(TimerState())
        val timerState = _timerState.asStateFlow()

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_SECONDS = "EXTRA_SECONDS"
        const val EXTRA_EXERCISE_ID = "EXTRA_EXERCISE_ID"
        const val CHANNEL_ID = "workout_timer_channel"
        const val NOTIFICATION_ID = 1
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val seconds = intent.getIntExtra(EXTRA_SECONDS, 60)
                val exerciseId = intent.getLongExtra(EXTRA_EXERCISE_ID, -1L)
                startTimer(seconds, exerciseId)
            }
            ACTION_STOP -> stopTimer()
        }
        return START_STICKY
    }

    private fun startTimer(durationSeconds: Int, exerciseId: Long) {
        timerJob?.cancel()
        createNotificationChannel()

        timerJob = serviceScope.launch {
            var timeLeft = durationSeconds

            // Reset state: running = true, hasFinished = false
            _timerState.update {
                TimerState(
                    isRunning = true,
                    remainingTime = timeLeft,
                    activeExerciseId = exerciseId,
                    hasFinished = false
                )
            }
            startForeground(NOTIFICATION_ID, buildNotification(timeLeft))

            while (timeLeft > 0) {
                delay(1000L)
                timeLeft--
                _timerState.update { it.copy(remainingTime = timeLeft) }
                updateNotification(timeLeft)
            }

            // FIXED: Set hasFinished = true when done, keep activeExerciseId so UI knows what finished
            _timerState.update {
                it.copy(
                    isRunning = false,
                    remainingTime = 0,
                    hasFinished = true
                )
            }

            stopForeground(STOP_FOREGROUND_REMOVE)
            // Note: We do not call stopSelf() immediately so the UI has time to react to the state change
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        _timerState.update { TimerState(false, 0, null, false) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(timeLeft: Int): android.app.Notification {
        val minutes = timeLeft / 60
        val seconds = timeLeft % 60
        val timeString = String.format("%02d:%02d", minutes, seconds)

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Rest Timer")
            .setContentText("Time Remaining: $timeString")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(timeLeft: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(timeLeft))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Workout Timer",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}