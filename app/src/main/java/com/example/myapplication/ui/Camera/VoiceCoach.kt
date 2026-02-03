package com.example.myapplication.ui.camera

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale
import kotlin.random.Random

class VoiceCoach(context: Context) {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var lastSpeakTime = 0L
    private val minIntervalMs = 2500L

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                setupNaturalVoice()
                isReady = true
            }
        }
    }

    private fun setupNaturalVoice() {
        val ttsInstance = tts ?: return
        ttsInstance.language = Locale.US
        ttsInstance.setSpeechRate(0.9f) // Slower = more conversational
        ttsInstance.setPitch(0.9f)      // Lower pitch = more authoritative

        // Try to find a high-quality "Network" voice
        val voices = ttsInstance.voices
        val bestVoice = voices?.firstOrNull {
            it.name.contains("network", true) && it.locale == Locale.US
        } ?: voices?.firstOrNull { it.locale == Locale.US }

        if (bestVoice != null) ttsInstance.voice = bestVoice
    }

    fun speak(text: String, force: Boolean = false) {
        if (!isReady) return
        val now = System.currentTimeMillis()
        if (force || (now - lastSpeakTime) > minIntervalMs) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            lastSpeakTime = now
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }

    // --- ADAPTIVE FEEDBACK SYSTEM ---

    fun onGoodRep(repCount: Int) {
        // Mix counting with motivation to sound natural
        val phrases = listOf(
            "$repCount",
            "$repCount",
            "$repCount",
            "$repCount! Good.",
            "$repCount! Easy weight.",
            "That's $repCount. Keep moving.",
            "Strong. That's $repCount."
        )
        // High priority (force=true) so the count is never skipped
        speak(phrases.random(), force = true)
    }

    fun onFormIssue(issue: FormFeedback) {
        val message = when (issue) {
            FormFeedback.TOO_HIGH -> listOf("Get lower.", "Sink it deeper.", "Range of motion!").random()
            FormFeedback.TOO_LOW -> listOf("Too deep, stay tight.", "Control the bottom.").random()
            FormFeedback.BAD_LOCKOUT -> listOf("Finish the rep.", "Lock it out.", "Squeeze at the top.").random()
            FormFeedback.GOOD_DEPTH -> listOf("Perfect depth.", "Yes! There it is.").random()
            else -> ""
        }
        if (message.isNotEmpty()) speak(message)
    }

    fun onSetComplete() {
        speak("Set complete. Great work! Rack it.", force = true)
    }
}

enum class FormFeedback {
    TOO_HIGH, TOO_LOW, BAD_LOCKOUT, GOOD_DEPTH, NEUTRAL
}