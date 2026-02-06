package com.example.myapplication.ui.camera

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

enum class FormFeedback {
    TOO_HIGH, TOO_LOW, BAD_LOCKOUT, GOOD_DEPTH, NEUTRAL
}

class VoiceCoach(context: Context) {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var lastSpeakTime = 0L
    private val minIntervalMs = 2000L

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // FIX: Explicitly Force US English
                val result = tts?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("VoiceCoach", "US English not supported on this device")
                } else {
                    setupNaturalVoice()
                    isReady = true
                }
            }
        }
    }

    private fun setupNaturalVoice() {
        val ttsInstance = tts ?: return

        val voices = ttsInstance.voices ?: emptySet()
        // Try to find a high quality US voice
        val bestVoice = voices.find { voice ->
            val name = voice.name.lowercase()
            val isUS = voice.locale == Locale.US
            isUS && (name.contains("journey") || name.contains("network") || name.contains("premium"))
        } ?: voices.firstOrNull { it.locale == Locale.US }

        if (bestVoice != null) {
            ttsInstance.voice = bestVoice
            ttsInstance.setPitch(0.9f) // Slightly deeper
        }
    }

    fun speak(text: String, force: Boolean = false, isInstruction: Boolean = false) {
        if (!isReady) return
        val now = System.currentTimeMillis()

        if (force || (now - lastSpeakTime) > minIntervalMs) {
            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "messageID")

            val rate = if (isInstruction) "0.9" else "1.1"

            // Using simple text if SSML fails on some devices, but trying SSML first
            val ssml = """
                <speak>
                    <prosody rate="$rate" pitch="+0%">
                        $text
                    </prosody>
                </speak>
            """.trimIndent()

            tts?.speak(ssml, TextToSpeech.QUEUE_FLUSH, params, "messageID")
            lastSpeakTime = now
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }

    fun onGoodRep(repCount: Int) {
        val phrase = when (repCount) {
            1 -> "Good start."
            in 2..9 -> "$repCount."
            10 -> "Ten! Halfway there."
            else -> "$repCount!"
        }
        speak(phrase, force = true, isInstruction = false)
    }

    fun onFormIssue(issue: FormFeedback) {
        val message = when (issue) {
            FormFeedback.TOO_HIGH -> "Get lower."
            FormFeedback.TOO_LOW -> "Too deep, stay tight."
            FormFeedback.BAD_LOCKOUT -> "Lock it out."
            FormFeedback.GOOD_DEPTH -> "Perfect depth."
            else -> ""
        }
        if (message.isNotEmpty()) {
            speak(message, force = false, isInstruction = true)
        }
    }

    fun onSetComplete() {
        speak("Rack it! Great set.", force = true)
    }
}