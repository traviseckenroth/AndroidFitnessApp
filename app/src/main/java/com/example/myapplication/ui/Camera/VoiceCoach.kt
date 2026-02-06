// app/src/main/java/com/example/myapplication/ui/camera/VoiceCoach.kt

package com.example.myapplication.ui.camera

import android.content.Context
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

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val ttsInstance = tts ?: return@TextToSpeech

                val result = ttsInstance.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("VoiceCoach", "US English not supported")
                }

                // Try to find a high-quality voice, prioritizing "network" voices
                val voices = ttsInstance.voices ?: emptySet()
                val bestVoice = voices.find {
                    val name = it.name.lowercase()
                    (name.contains("network") || name.contains("en-us-x-iom")) && !name.contains("local")
                } ?: voices.firstOrNull { it.locale == Locale.US }

                if (bestVoice != null) {
                    ttsInstance.voice = bestVoice
                }

                // FIX: Speed increased to 0.95f (was 0.85f) to fix "very slow" complaint
                ttsInstance.setSpeechRate(0.95f)
                ttsInstance.setPitch(0.85f)

                isReady = true
            }
        }
    }

    fun speak(text: String, force: Boolean = false, flush: Boolean = false) {
        if (!isReady) return
        val now = System.currentTimeMillis()

        if (force || (now - lastSpeakTime) > 2500) {
            val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts?.speak(text, queueMode, null, "messageID")
            lastSpeakTime = now
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }

    fun onGoodRep(repCount: Int) {
        // Always flush rep counts so they are immediate
        speak("$repCount", force = true, flush = true)
    }

    fun onSetComplete() {
        speak("Rack it! Great set.", force = true, flush = false)
    }
}