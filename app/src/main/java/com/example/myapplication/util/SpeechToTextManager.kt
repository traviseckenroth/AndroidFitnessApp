// app/src/main/java/com/example/myapplication/util/SpeechToTextManager.kt
package com.example.myapplication.util

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.sqrt

@Singleton
class SpeechToTextManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _volumeLevel = MutableSharedFlow<Float>(extraBufferCapacity = 10)
    val volumeLevel = _volumeLevel.asSharedFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript = _partialTranscript.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening = _isListening.asStateFlow()

    private var recognizer: OnlineRecognizer? = null
    // Guard stream to ensure safe release order
    private var stream: OnlineStream? = null
    
    private var hasAttemptedInit = false
    private var currentJob: Job? = null
    private val recognizerMutex = Mutex()

    private suspend fun initRecognizerIfNeeded() = withContext(Dispatchers.IO) {
        recognizerMutex.withLock {
            if (recognizer != null || hasAttemptedInit) return@withLock
            hasAttemptedInit = true

            try {
                val modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = "encoder-epoch-99-avg-1.onnx",
                        decoder = "decoder-epoch-99-avg-1.onnx",
                        joiner = "joiner-epoch-99-avg-1.onnx"
                    ),
                    tokens = "tokens.txt",
                    numThreads = 4,
                    provider = "cpu",
                    debug = false
                )

                val config = OnlineRecognizerConfig(
                    modelConfig = modelConfig,
                    featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                    endpointConfig = EndpointConfig(
                        rule1 = EndpointRule(false, 2.0f, 0.0f),
                        rule2 = EndpointRule(true, 1.2f, 0.0f),
                        rule3 = EndpointRule(false, 0.0f, 20.0f)
                    ),
                    enableEndpoint = true,
                    decodingMethod = "greedy_search"
                )

                recognizer = OnlineRecognizer(context.assets, config)
                Log.d("SpeechToText", "Sherpa-ONNX Recognizer initialized successfully")
            } catch (e: Exception) {
                Log.e("SpeechToText", "Failed to initialize Sherpa-ONNX STT", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startListeningForSingleUtterance(): Flow<String> = callbackFlow {
        _isListening.value = true
        _partialTranscript.value = ""

        initRecognizerIfNeeded()

        // Safely create stream under lock
        val (recognizerInstance, streamInstance) = recognizerMutex.withLock {
            val r = recognizer
            if (r == null) {
                Log.e("SpeechToText", "Recognizer is null, cannot start listening")
                null to null
            } else {
                try {
                    val s = r.createStream()
                    stream = s
                    r to s
                } catch (e: Exception) {
                    Log.e("SpeechToText", "Failed to create Sherpa-ONNX stream", e)
                    null to null
                }
            }
        }

        if (recognizerInstance == null || streamInstance == null) {
            trySend("")
            close()
            return@callbackFlow
        }

        val sampleRate = 16000
        val bufferSizeInBytes = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2
        
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSizeInBytes
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("SpeechToText", "AudioRecord initialization failed")
            audioRecord.release()
            trySend("")
            close()
            return@callbackFlow
        }

        try {
            audioRecord.startRecording()
            if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e("SpeechToText", "AudioRecord failed to start recording (possibly mic in use)")
                trySend("")
                close()
                return@callbackFlow
            }
        } catch (e: Exception) {
            Log.e("SpeechToText", "Exception starting AudioRecord", e)
            trySend("")
            close()
            return@callbackFlow
        }

        var isDone = false
        val bufferSize = 3200
        val buffer = ShortArray(bufferSize)

        currentJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                while (isActive && !isDone) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var sum = 0.0
                        for (i in 0 until read) {
                            sum += buffer[i] * buffer[i]
                        }
                        val rms = sqrt(sum / read)
                        val db = if (rms > 0) 20 * log10(rms) else 0.0
                        val normalizedDb = (db - 30).toFloat().coerceAtLeast(0f)
                        _volumeLevel.tryEmit(normalizedDb)

                        val floatArray = FloatArray(read) { buffer[it] / 32768.0f }
                        
                        // We check if stream is still valid (not released) by relying on the fact that
                        // shutdown() cancels this job and waits for it to finish BEFORE releasing stream.
                        streamInstance.acceptWaveform(floatArray, sampleRate)

                        while (recognizerInstance.isReady(streamInstance)) {
                            recognizerInstance.decode(streamInstance)
                        }

                        val partialText = recognizerInstance.getResult(streamInstance).text
                        if (partialText.isNotBlank()) {
                            _partialTranscript.value = partialText
                        }

                        if (recognizerInstance.isEndpoint(streamInstance)) {
                            val finalText = recognizerInstance.getResult(streamInstance).text
                            trySend(finalText)
                            isDone = true
                            close()
                        }
                    } else if (read < 0) {
                        Log.e("SpeechToText", "AudioRecord read error: $read")
                        isDone = true
                        close()
                    }
                }
            } catch (e: Exception) {
                Log.e("SpeechToText", "Error during audio processing loop", e)
                if (!isClosedForSend) {
                    trySend("")
                    close(e)
                }
            }
        }

        awaitClose {
            isDone = true
            currentJob?.cancel()

            try {
                if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop()
                }
                audioRecord.release()
            } catch (e: Exception) {
                Log.e("SpeechToText", "Error releasing AudioRecord", e)
            }

            // Release stream safely under lock to coordinate with shutdown()
            runBlocking {
                recognizerMutex.withLock {
                    try {
                        stream?.release()
                    } catch (e: Exception) {
                        Log.e("SpeechToText", "Error releasing stream in awaitClose", e)
                    } finally {
                        stream = null
                    }
                }
            }

            _isListening.value = false
        }
    }

    suspend fun listenForResponse(maxTimeoutMillis: Long = 10000L): String {
        return try {
            val response = withTimeoutOrNull(maxTimeoutMillis) {
                startListeningForSingleUtterance().first()
            }
            _partialTranscript.value = ""
            response ?: ""
        } catch (e: Exception) {
            Log.e("SpeechToText", "Error during listening", e)
            _partialTranscript.value = ""
            ""
        }
    }

    fun shutdown() {
        val jobToJoin = currentJob
        currentJob?.cancel()

        CoroutineScope(Dispatchers.IO).launch {
            // CRITICAL: Wait for the recording loop to fully exit before we destroy native objects.
            // This prevents the loop from accessing a released stream or recognizer.
            try {
                jobToJoin?.join()
            } catch (e: Exception) {
                Log.e("SpeechToText", "Error joining job", e)
            }

            recognizerMutex.withLock {
                try {
                    // Double check stream release if awaitClose didn't get to it yet
                    stream?.release()
                    stream = null
                    
                    recognizer?.release()
                    recognizer = null
                    hasAttemptedInit = false
                    Log.d("SpeechToText", "Recognizer shutdown complete")
                } catch (e: Exception) {
                    Log.e("SpeechToText", "Error during shutdown", e)
                }
            }
        }
    }
}
