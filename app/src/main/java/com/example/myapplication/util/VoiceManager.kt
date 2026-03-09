package com.example.myapplication.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.AudioFocusRequest
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.myapplication.data.remote.VoiceModelDownloader
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelDownloader: VoiceModelDownloader
) {
    // Sherpa-ONNX Engines
    private var ttsEngine: OfflineTts? = null
    private var asrEngine: OfflineRecognizer? = null
    private var isInitialized = false

    // Coroutines & AudioTrack for background playback
    private val audioScope = CoroutineScope(Dispatchers.IO)
    private var speechJob: Job? = null
    private var audioTrack: AudioTrack? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    /**
     * Call this ONLY AFTER VoiceModelDownloader confirms files are downloaded!
     */
    fun initializeVoiceEngines() {
        if (isInitialized) return

        try {
            val modelsPath = modelDownloader.getModelDirectoryPath()

            // Safe Path Constructors
            val kokoroModelPath = File(modelsPath, "kokoro_model/model.onnx").absolutePath
            val kokoroVoicesPath = File(modelsPath, "kokoro_model/voices.bin").absolutePath
            val tokensPath = File(modelsPath, "tokens.txt").absolutePath

            val decoderPath = File(modelsPath, "decoder-epoch-99-avg-1.onnx").absolutePath
            val encoderPath = File(modelsPath, "encoder-epoch-99-avg-1.onnx").absolutePath
            val joinerPath = File(modelsPath, "joiner-epoch-99-avg-1.onnx").absolutePath

            // 1. Initialize Kokoro Text-To-Speech (Coach Voice)
            val ttsConfig = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    kokoro = OfflineTtsKokoroModelConfig(
                        model = kokoroModelPath,
                        voices = kokoroVoicesPath,
                        tokens = tokensPath
                    ),
                    numThreads = 2,
                    debug = false
                ),
                maxNumSentences = 1
            )
            // Fix: Pass null for AssetManager when using file paths
            ttsEngine = OfflineTts(null, ttsConfig)

            // 2. Initialize Transducer Speech-To-Text (Listening to User)
            val asrConfig = OfflineRecognizerConfig(
                modelConfig = OfflineModelConfig(
                    transducer = OfflineTransducerModelConfig(
                        encoder = encoderPath,
                        decoder = decoderPath,
                        joiner = joinerPath
                    ),
                    tokens = tokensPath,
                    numThreads = 2,
                    debug = false
                )
            )
            // Fix: Pass null for AssetManager when using file paths
            asrEngine = OfflineRecognizer(null, asrConfig)

            isInitialized = true
            Log.d("VoiceManager", "Sherpa-ONNX TTS \u0026 ASR Engines Booted Successfully!")
        } catch (e: Exception) {
            Log.e("VoiceManager", "Failed to boot Sherpa-ONNX. Check file paths!", e)
        }
    }

    // --- TEXT TO SPEECH (Coach Talking) ---
    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!isInitialized || ttsEngine == null) {
            Log.w("VoiceManager", "Sherpa TTS not initialized. Skipping speech.")
            onComplete?.invoke()
            return
        }

        stop()

        speechJob = audioScope.launch {
            try {
                requestAudioFocus()

                // Generate audio array using Sherpa's Kokoro model
                // Fix: Added speaker ID and speed parameters
                val generatedAudio = ttsEngine!!.generate(text, 0, 1.0f)
                val samples = generatedAudio.samples
                val sampleRate = generatedAudio.sampleRate

                val bufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_FLOAT
                )

                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack?.play()
                audioTrack?.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)

            } catch (e: Exception) {
                Log.e("VoiceManager", "Error generating or playing speech", e)
            } finally {
                cleanupAudioTrack()
                abandonAudioFocus()
                onComplete?.invoke()
            }
        }
    }

    // --- SPEECH TO TEXT (Listening to User) ---
    fun transcribeAudio(samples: FloatArray, sampleRate: Int = 16000): String {
        if (!isInitialized || asrEngine == null) {
            Log.w("VoiceManager", "Sherpa ASR not initialized.")
            return ""
        }

        return try {
            val stream = asrEngine!!.createStream()
            stream.acceptWaveform(samples, sampleRate)
            // OfflineStream does not have inputFinished(); acceptWaveform is sufficient before decode
            asrEngine!!.decode(stream)

            val result = asrEngine!!.getResult(stream).text
            stream.release()
            result
        } catch (e: Exception) {
            Log.e("VoiceManager", "Error transcribing audio", e)
            ""
        }
    }

    fun stop() {
        speechJob?.cancel()
        cleanupAudioTrack()
        abandonAudioFocus()
    }

    private fun cleanupAudioTrack() {
        try {
            audioTrack?.apply {
                if (state == AudioTrack.STATE_INITIALIZED) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e("VoiceManager", "Error releasing AudioTrack", e)
        } finally {
            audioTrack = null
        }
    }

    private fun requestAudioFocus() {
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .build()
        audioManager.requestAudioFocus(focusRequest)
    }

    private fun abandonAudioFocus() {
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).build()
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    fun vibrateSuccess() {
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 50, 50, 100), -1))
        }
    }

    fun vibrateTimerEnd() {
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
