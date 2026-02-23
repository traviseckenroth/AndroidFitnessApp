package com.example.myapplication.util

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContinuousAudioStreamer @Inject constructor() {

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    private var audioRecord: AudioRecord? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null

    private val _audioDataFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 10)
    val audioDataFlow: SharedFlow<ByteArray> = _audioDataFlow.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var streamingJob: Job? = null

    @SuppressLint("MissingPermission")
    fun startStreaming() {
        if (streamingJob?.isActive == true) return

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("AudioStreamer", "AudioRecord initialization failed")
                return
            }

            val sessionId = audioRecord!!.audioSessionId

            // Enable Acoustic Echo Canceler if available
            if (AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(sessionId)
                aec?.enabled = true
                Log.d("AudioStreamer", "AcousticEchoCanceler enabled")
            }

            // Enable Noise Suppressor if available
            if (NoiseSuppressor.isAvailable()) {
                ns = NoiseSuppressor.create(sessionId)
                ns?.enabled = true
                Log.d("AudioStreamer", "NoiseSuppressor enabled")
            }

            audioRecord?.startRecording()
            Log.d("AudioStreamer", "Recording started")

            streamingJob = scope.launch {
                val data = ByteArray(bufferSize)
                while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val read = audioRecord?.read(data, 0, data.size) ?: -1
                    if (read > 0) {
                        _audioDataFlow.emit(data.copyOf(read))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AudioStreamer", "Error starting audio stream", e)
        }
    }

    fun stopStreaming() {
        streamingJob?.cancel()
        streamingJob = null

        audioRecord?.let {
            if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                it.stop()
            }
            it.release()
        }
        audioRecord = null

        aec?.release()
        aec = null
        ns?.release()
        ns = null

        Log.d("AudioStreamer", "Recording stopped and resources released")
    }
}
