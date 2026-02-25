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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechToTextManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Expose volume levels for Voice Activity Detection (Interruptibility)
    private val _volumeLevel = MutableSharedFlow<Float>(extraBufferCapacity = 10)
    val volumeLevel = _volumeLevel.asSharedFlow()

    fun startListeningForSingleUtterance(): Flow<String> = callbackFlow {
        val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Tell Android we only want a short response before it closes the mic
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                // Stream the volume level out for the Engine to monitor
                _volumeLevel.tryEmit(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d("SpeechToText", "User stopped talking.")
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    else -> "Error code: $error"
                }
                Log.w("SpeechToText", "Error: $errorMessage")

                // If the user stayed silent, yield an empty string so the flow can terminate
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    trySend("")
                    close()
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    trySend(matches[0])
                } else {
                    trySend("")
                }
                // Close the flow IMMEDIATELY after getting the result to eliminate the 5-second awkward silence
                close()
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        speechRecognizer.setRecognitionListener(listener)
        speechRecognizer.startListening(intent)

        awaitClose {
            speechRecognizer.stopListening()
            speechRecognizer.destroy()
        }
    }

    /**
     * Listens until the user finishes a single thought, OR until the max timeout is hit.
     */
    suspend fun listenForResponse(maxTimeoutMillis: Long = 10000L): String {
        return try {
            val response = withTimeoutOrNull(maxTimeoutMillis) {
                startListeningForSingleUtterance().first()
            }
            response ?: "" // Return empty string if they hit the max timeout without speaking
        } catch (e: Exception) {
            Log.e("SpeechToText", "Error during listening", e)
            ""
        }
    }
}