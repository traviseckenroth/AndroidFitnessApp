package com.example.myapplication.util

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MicrophoneManager @Inject constructor(
    private val voiceManager: VoiceManager
) {
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val recordingScope = CoroutineScope(Dispatchers.IO)

    // Sherpa-ONNX specifically expects 16kHz for its transducer models
    private val sampleRate = 16000
    private var isRecording = false

    // A dynamic buffer to hold the audio while the user is holding the button / talking
    private val audioBuffer = mutableListOf<Short>()

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (isRecording) return

        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        // Using VOICE_RECOGNITION automatically applies acoustic echo cancellation
        // and noise suppression on most modern Android devices.
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        audioBuffer.clear()

        try {
            audioRecord?.startRecording()
            isRecording = true
            Log.d("MicrophoneManager", "Started recording...")
        } catch (e: Exception) {
            Log.e("MicrophoneManager", "Failed to start AudioRecord", e)
            return
        }

        recordingJob = recordingScope.launch {
            val shortBuffer = ShortArray(bufferSize)
            while (isActive && isRecording) {
                val readResult = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: 0
                if (readResult > 0) {
                    // Append the chunk of audio to our running buffer
                    audioBuffer.addAll(shortBuffer.take(readResult))
                }
            }
        }
    }

    /**
     * Stops the microphone, converts the audio to the required Float format,
     * and sends it to Sherpa-ONNX for transcription.
     */
    suspend fun stopRecordingAndTranscribe(): String = withContext(Dispatchers.IO) {
        if (!isRecording) return@withContext ""

        isRecording = false
        recordingJob?.cancel()

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e("MicrophoneManager", "Error stopping AudioRecord", e)
        } finally {
            audioRecord = null
        }

        if (audioBuffer.isEmpty()) {
            Log.w("MicrophoneManager", "No audio was captured.")
            return@withContext ""
        }

        Log.d("MicrophoneManager", "Processing ${audioBuffer.size} audio samples...")

        // Sherpa-ONNX expects floats between -1.0f and 1.0f.
        // We convert the 16-bit PCM shorts into floats by dividing by the max Short value (32768.0f)
        val floatArray = FloatArray(audioBuffer.size) { i ->
            audioBuffer[i] / 32768.0f
        }

        audioBuffer.clear()

        // Hand the audio off to the VoiceManager and return the transcribed text!
        val transcript = voiceManager.transcribeAudio(floatArray, sampleRate)
        Log.d("MicrophoneManager", "Transcription Result: $transcript")

        return@withContext transcript
    }

    fun cancelRecording() {
        isRecording = false
        recordingJob?.cancel()
        audioRecord?.release()
        audioRecord = null
        audioBuffer.clear()
    }
}