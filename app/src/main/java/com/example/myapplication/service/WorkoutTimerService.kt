package com.example.myapplication.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.myapplication.MainActivity
import com.example.myapplication.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TimerState(
    val isRunning: Boolean = false,
    val remainingTime: Int = 0,
    val activeExerciseId: Long? = null,
    val hasFinished: Boolean = false,
    val isRest: Boolean = true
)

class WorkoutTimerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var timerJob: Job? = null
    private var toneGenerator: ToneGenerator? = null

    companion object {
        private val _timerState = MutableStateFlow(TimerState())
        val timerState = _timerState.asStateFlow()

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_ADD_TIME = "ACTION_ADD_TIME"

        const val EXTRA_SECONDS = "EXTRA_SECONDS"
        const val EXTRA_ADD_TIME_SECONDS = "EXTRA_ADD_TIME_SECONDS"
        const val EXTRA_EXERCISE_ID = "EXTRA_EXERCISE_ID"
        const val EXTRA_IS_REST = "EXTRA_IS_REST"
        
        const val CHANNEL_ID = "workout_timer_channel"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        try {
            // Using STREAM_MUSIC ensures the beeps are heard even if notifications are silenced,
            // which is common during workouts with music/headphones.
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val seconds = intent.getIntExtra(EXTRA_SECONDS, 60)
                val exerciseId = intent.getLongExtra(EXTRA_EXERCISE_ID, -1L)
                val isRest = intent.getBooleanExtra(EXTRA_IS_REST, true)
                startTimer(seconds, exerciseId, isRest)
            }
            ACTION_STOP -> stopTimer()
            ACTION_ADD_TIME -> {
                val additionalSeconds = intent.getIntExtra(EXTRA_ADD_TIME_SECONDS, 0)
                if (additionalSeconds > 0 && _timerState.value.isRunning) {
                    _timerState.update { it.copy(remainingTime = it.remainingTime + additionalSeconds) }
                    updateNotification(_timerState.value.remainingTime, _timerState.value.isRest)
                }
            }
        }
        return START_STICKY
    }

    private fun startTimer(durationSeconds: Int, exerciseId: Long, isRest: Boolean) {
        timerJob?.cancel()
        createNotificationChannel()

        timerJob = serviceScope.launch {
            _timerState.update {
                TimerState(
                    isRunning = true,
                    remainingTime = durationSeconds,
                    activeExerciseId = exerciseId,
                    hasFinished = false,
                    isRest = isRest
                )
            }
            startForeground(NOTIFICATION_ID, buildNotification(durationSeconds, isRest))

            while (_timerState.value.remainingTime > 0) {
                delay(1000L)
                val newTime = _timerState.value.remainingTime - 1
                _timerState.update { it.copy(remainingTime = newTime) }
                updateNotification(newTime, isRest)

                // Beeps at 3, 2, 1 to prepare the user.
                // We always play these beats regardless of isRest to ensure the user is alerted.
                if (newTime in 1..3) {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                }
            }

            // Final distinct long beep at 0.
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500)

            _timerState.update {
                it.copy(
                    isRunning = false,
                    remainingTime = 0,
                    hasFinished = true
                )
            }

            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        _timerState.update { TimerState(false, 0, null, false) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(timeLeft: Int, isRest: Boolean): android.app.Notification {
        val minutes = timeLeft / 60
        val seconds = timeLeft % 60
        val timeString = String.format("%02d:%02d", minutes, seconds)
        val title = if (isRest) "Rest Timer" else "Set Timer"

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Time Remaining: $timeString")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(timeLeft: Int, isRest: Boolean) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(timeLeft, isRest))
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
        toneGenerator?.release()
        toneGenerator = null
    }
}
