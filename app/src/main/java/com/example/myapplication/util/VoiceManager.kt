package com.example.myapplication.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceManager @Inject constructor(@ApplicationContext private val context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var onSpeechDone: (() -> Unit)? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // 1. Basic Setup
                val result = tts?.setLanguage(Locale.US)

                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("VoiceManager", "Language not supported")
                } else {
                    // 2. APPLY NATURAL VOICE SETTINGS
                    setupNaturalVoice()

                    // 3. Set Listener with Audio Focus handling
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            requestAudioFocus()
                        }

                        override fun onDone(utteranceId: String?) {
                            abandonAudioFocus()
                            onSpeechDone?.invoke()
                            onSpeechDone = null
                        }

                        @Suppress("Deprecated")
                        override fun onError(utteranceId: String?) {
                            abandonAudioFocus()
                            onSpeechDone = null
                        }
                    })

                    isInitialized = true
                }
            } else {
                Log.e("VoiceManager", "TTS Initialization failed")
            }
        }
    }

    private fun setupNaturalVoice() {
        val ttsInstance = tts ?: return

        // TUNING: Slightly slower and lower pitch sounds more "conversational" and less "assistant-like"
        ttsInstance.setSpeechRate(0.9f)
        ttsInstance.setPitch(0.95f)

        try {
            val voices = ttsInstance.voices
            if (voices.isNullOrEmpty()) return

            // LOGIC: Find a voice that is "Network" (High Quality) and "US English"
            // We prioritize "Network" voices because they use server-side generation (WaveNet)
            val bestVoice = voices.firstOrNull {
                it.name.contains("network", ignoreCase = true) &&
                        it.locale == Locale.US
            } ?: voices.firstOrNull {
                // Fallback: Just get any high quality US voice
                it.quality == Voice.QUALITY_VERY_HIGH &&
                        it.locale == Locale.US
            } ?: voices.firstOrNull {
                it.locale == Locale.US
            }

            if (bestVoice != null) {
                Log.d("VoiceManager", "Using Voice: ${bestVoice.name}")
                ttsInstance.voice = bestVoice
            }
        } catch (e: Exception) {
            Log.e("VoiceManager", "Failed to set natural voice", e)
        }
    }

    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (isInitialized) {
            onSpeechDone = onComplete

            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "AI_RESPONSE")
            // Use AUDIO_STREAM_MUSIC so it ducks correctly
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)

            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "AI_RESPONSE")
        }
    }

    fun stop() {
        tts?.stop()
        abandonAudioFocus()
        onSpeechDone = null
    }

    // --- AUDIO DUCKING LOGIC ---
    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).build()
            audioManager.abandonAudioFocusRequest(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    // --- HAPTIC FEEDBACK ---
    fun vibrateSuccess() {
        if (vibrator.hasVibrator()) {
            // Double tap pattern (Set Complete)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 50, 50, 100), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 50, 50, 100), -1)
            }
        }
    }

    fun vibrateTimerEnd() {
        if (vibrator.hasVibrator()) {
            // Long buzz pattern (Timer End)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(1000)
            }
        }
    }
}
