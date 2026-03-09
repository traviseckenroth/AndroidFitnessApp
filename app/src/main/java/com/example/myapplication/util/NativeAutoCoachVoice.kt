// app/src/main/java/com/example/myapplication/util/NativeAutoCoachVoice.kt
package com.example.myapplication.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeAutoCoachVoice @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var ttsEngine: OfflineTts? = null
    private var audioTrack: AudioTrack? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val engineMutex = Mutex()

    var currentVoiceSid: Int = 0
    private val sampleRate = 24000
    private var isInterrupted = false
    private var hasAttemptedInit = false

    private fun copyFile(path: String, targetFile: File) {
        try {
            context.assets.open(path).use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        } catch (e: Exception) {
            Log.e("NativeAutoCoachVoice", "Failed to copy asset file: $path", e)
        }
    }

    // UPDATED: Bulletproof recursive asset copier
    private fun copyDirectoryFromAssets(assetPath: String, targetFileOrDir: File) {
        val files = context.assets.list(assetPath) ?: emptyArray()
        if (files.isEmpty()) {
            copyFile(assetPath, targetFileOrDir) // It's a file
        } else {
            if (!targetFileOrDir.exists()) targetFileOrDir.mkdirs() // It's a directory
            for (file in files) {
                copyDirectoryFromAssets("$assetPath/$file", File(targetFileOrDir, file))
            }
        }
    }

    suspend fun initEngineIfNeeded() = engineMutex.withLock {
        if (ttsEngine != null || hasAttemptedInit) return@withLock
        hasAttemptedInit = true

        try {
            val s3ModelsDir = File(context.filesDir, "voice_models")
            val kokoroModelFile = File(s3ModelsDir, "kokoro_model/model.onnx")
            val kokoroVoicesFile = File(s3ModelsDir, "kokoro_model/voices.bin")
            val tokensFile = File(s3ModelsDir, "tokens.txt")

            // --- THE FIREWALL ---
            // If AWS sent us a fake XML error file, do NOT pass it to C++!
            if (kokoroVoicesFile.length() < 5000) {
                Log.e("NativeAutoCoachVoice", "CRITICAL FIREWALL: voices.bin is corrupt (${kokoroVoicesFile.length()} bytes). This is likely an AWS XML error file. Fix S3!")
                return@withLock
            }
            if (kokoroModelFile.length() < 50_000_000) {
                Log.e("NativeAutoCoachVoice", "CRITICAL FIREWALL: model.onnx is corrupt (${kokoroModelFile.length()} bytes). This is likely an AWS XML error file. Fix S3!")
                return@withLock
            }
            if (tokensFile.length() < 100) {
                Log.e("NativeAutoCoachVoice", "CRITICAL FIREWALL: tokens.txt is missing or corrupt.")
                return@withLock
            }

            // Extract lightweight assets
            val localAssetsDir = File(context.filesDir, "kokoro_assets")
            val lexiconFile = File(localAssetsDir, "lexicon-us-en.txt")
            val phontabFile = File(localAssetsDir, "espeak-ng-data/phontab")

            if (!lexiconFile.exists() || !phontabFile.exists()) {
                Log.d("NativeAutoCoachVoice", "Missing lightweight espeak assets. Copying now...")
                if (localAssetsDir.exists()) localAssetsDir.deleteRecursively()
                localAssetsDir.mkdirs()

                copyFile("kokoro_model/lexicon-us-en.txt", lexiconFile)
                copyDirectoryFromAssets("kokoro_model/espeak-ng-data", File(localAssetsDir, "espeak-ng-data"))

                if (!lexiconFile.exists() || !phontabFile.exists()) {
                    Log.e("NativeAutoCoachVoice", "CRITICAL: Asset copy failed! Ensure 'kokoro_model' is in your assets folder.")
                    return@withLock
                }
                Log.d("NativeAutoCoachVoice", "Asset copy complete.")
            }

            val modelConfig = OfflineTtsKokoroModelConfig(
                model = kokoroModelFile.absolutePath,
                voices = kokoroVoicesFile.absolutePath,
                tokens = tokensFile.absolutePath,
                dataDir = File(localAssetsDir, "espeak-ng-data").absolutePath,
                dictDir = "",
                lexicon = lexiconFile.absolutePath
            )

            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    kokoro = modelConfig,
                    numThreads = 2,
                    debug = false
                ),
                maxNumSentences = 1
            )

            ttsEngine = OfflineTts(null, config)

            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(minBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            Log.d("NativeAutoCoachVoice", "Engine and AudioTrack ready.")

        } catch (e: Exception) {
            Log.e("NativeAutoCoachVoice", "Failed to initialize TTS", e)
        }
    }

    suspend fun speakAndWait(text: String) = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext
        initEngineIfNeeded()

        engineMutex.withLock {
            val engine = ttsEngine
            val track = audioTrack
            if (engine == null || track == null) return@withLock

            isInterrupted = false

            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .build()

            audioManager.requestAudioFocus(focusRequest)

            try {
                track.play()
                val generatedAudio = engine.generate(text, currentVoiceSid, 1.0f)
                val samples = generatedAudio.samples
                val chunkSize = 4096
                val shortArray = ShortArray(chunkSize)
                var offset = 0

                while (offset < samples.size && !isInterrupted) {
                    val currentChunkSize = minOf(chunkSize, samples.size - offset)
                    for (i in 0 until currentChunkSize) {
                        var sample = samples[offset + i]
                        if (sample > 1.0f) sample = 1.0f
                        if (sample < -1.0f) sample = -1.0f
                        shortArray[i] = (sample * 32767.0f).toInt().toShort()
                    }
                    track.write(shortArray, 0, currentChunkSize, AudioTrack.WRITE_BLOCKING)
                    offset += currentChunkSize
                }
            } catch (e: Exception) {
                Log.e("NativeAutoCoachVoice", "Playback error", e)
            } finally {
                audioManager.abandonAudioFocusRequest(focusRequest)
                try {
                    track.pause()
                    track.flush()
                } catch (e: Exception) {}
            }
        }
    }

    fun interrupt() {
        isInterrupted = true
        try {
            if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack?.pause()
                audioTrack?.flush()
            }
        } catch (e: Exception) { }
    }

    fun shutdown() {
        isInterrupted = true
        CoroutineScope(Dispatchers.IO).launch {
            engineMutex.withLock {
                val trackToRelease = audioTrack
                audioTrack = null
                val engineToRelease = ttsEngine
                ttsEngine = null
                hasAttemptedInit = false

                try {
                    if (trackToRelease?.state == AudioTrack.STATE_INITIALIZED) {
                        trackToRelease.stop()
                        trackToRelease.release()
                    }
                    engineToRelease?.release()
                } catch (e: Exception) {
                    Log.e("NativeAutoCoachVoice", "Error during shutdown", e)
                }
            }
        }
    }
}