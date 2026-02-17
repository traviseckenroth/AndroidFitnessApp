package com.example.myapplication.util

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceManager @Inject constructor(@ApplicationContext context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var onSpeechDone: (() -> Unit)? = null

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

                    // 3. Set Listener (Preserved from previous fix)
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}

                        override fun onDone(utteranceId: String?) {
                            onSpeechDone?.invoke()
                            onSpeechDone = null
                        }

                        @Suppress("Deprecated")
                        override fun onError(utteranceId: String?) {
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

            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "AI_RESPONSE")
        }
    }

    fun stop() {
        tts?.stop()
        onSpeechDone = null
    }
}