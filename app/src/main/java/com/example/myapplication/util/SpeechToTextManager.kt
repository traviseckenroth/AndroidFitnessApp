package com.example.myapplication.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechToTextManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun startListening(): Flow<String> = callbackFlow {
        val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                // Determine if we should restart based on your UX needs
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    else -> "Error code: $error"
                }
                Log.w("SpeechToText", "Error: $errorMessage")

                // Critical: Don't close the flow on simple errors (like silence), just restart
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    speechRecognizer.startListening(intent)
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    trySend(matches[0])
                }
                // Continuous listening: Restart after result
                speechRecognizer.startListening(intent)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // Optional: Send partials if you want "live" typing effect
                // val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                // if (!matches.isNullOrEmpty()) trySend(matches[0])
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        speechRecognizer.setRecognitionListener(listener)
        speechRecognizer.startListening(intent)

        awaitClose {
            speechRecognizer.stopListening()
            speechRecognizer.destroy()
        }
    }
}