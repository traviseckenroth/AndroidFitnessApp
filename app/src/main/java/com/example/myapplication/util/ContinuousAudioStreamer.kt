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
import kotlin.math.abs
import kotlin.math.sqrt

@Singleton
class ContinuousAudioStreamer @Inject constructor(
    private val voiceManager: VoiceManager
) {

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    private var audioRecord: AudioRecord? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null

    private val _audioDataFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 10)
    val audioDataFlow: SharedFlow<ByteArray> = _audioDataFlow.asSharedFlow()

    private val _interruptionFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val interruptionFlow: SharedFlow<Unit> = _interruptionFlow.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var streamingJob: Job? = null

    // VAD Configuration
    // FIX: Increased RMS threshold to 2500.0 to reduce false interruptions from background gym noise
    private val rmsThreshold = 2500.0 
    private var lastInterruptionTime = 0L
    private val interruptionCooldown = 2500L 

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

            if (AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(sessionId)
                aec?.enabled = true
            }

            if (NoiseSuppressor.isAvailable()) {
                ns = NoiseSuppressor.create(sessionId)
                ns?.enabled = true
            }

            audioRecord?.startRecording()
            Log.d("AudioStreamer", "Continuous mic stream started for VAD")

            streamingJob = scope.launch {
                val data = ByteArray(bufferSize)
                while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val read = audioRecord?.read(data, 0, data.size) ?: -1
                    if (read > 0) {
                        checkVoiceActivity(data, read)
                        _audioDataFlow.emit(data.copyOf(read))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AudioStreamer", "Error starting audio stream", e)
        }
    }

    private fun checkVoiceActivity(buffer: ByteArray, size: Int) {
        if (size < 2) return
        
        var sum = 0.0
        for (i in 0 until size - 1 step 2) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toDouble()
            sum += sample * sample
        }
        val rms = sqrt(sum / (size / 2))

        if (rms > rmsThreshold) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastInterruptionTime > interruptionCooldown) {
                Log.d("AudioStreamer", "VAD: User speech detected (RMS: $rms). Stopping AI.")
                lastInterruptionTime = currentTime
                
                voiceManager.stop()
                
                scope.launch {
                    _interruptionFlow.emit(Unit)
                }
            }
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
    }
}
