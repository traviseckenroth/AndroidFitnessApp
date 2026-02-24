package com.example.myapplication.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.resume

class NativeAutoCoachVoice @Inject constructor(
    @ApplicationContext private val context: Context,
    private val elevenLabsClient: ElevenLabsClient,
    private val voiceManager: VoiceManager
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Speaks the text using ElevenLabs, ducks background music, 
     * and suspends the coroutine until speech is completely finished.
     * Falls back to local TTS (VoiceManager) if ElevenLabs fails.
     */
    suspend fun speakAndWait(text: String) = withContext(Dispatchers.IO) {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val focusRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { }
                .build()
        } else null

        try {
            // 1. Request Audio Focus (Ducking)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            }

            // 2. Call ElevenLabs API and play audio
            Log.d("NativeAutoCoachVoice", "Requesting ElevenLabs speech: $text")
            val success = elevenLabsClient.generateAndPlay(text)

            // 3. Fallback if ElevenLabs failed (e.g. 401 Unauthorized or Network Error)
            if (!success) {
                Log.w("NativeAutoCoachVoice", "ElevenLabs failed. Falling back to local TTS.")
                speakWithFallback(text)
            }

        } finally {
            // 4. Abandon audio focus so music goes back to full volume
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                audioManager.abandonAudioFocusRequest(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        }
    }

    private suspend fun speakWithFallback(text: String) = suspendCancellableCoroutine<Unit> { continuation ->
        voiceManager.speak(text) {
            if (continuation.isActive) continuation.resume(Unit)
        }
    }

    fun shutdown() {
        voiceManager.stop()
    }
}