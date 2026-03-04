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
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
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

    private fun copyDirectoryFromAssets(assetPath: String, targetDir: File) {
        val files = context.assets.list(assetPath) ?: return
        if (files.isEmpty()) return // Empty directory or file not found

        for (file in files) {
            val fullAssetPath = "$assetPath/$file"
            // Simple heuristic: if it has an extension, it's likely a file.
            // But espeak-ng-data has files without extensions (e.g. phontab).
            // We try to open it as a stream. If it works, it's a file.
            // If it throws, it might be a directory.
            
            var isDirectory = false
            try {
                context.assets.open(fullAssetPath).close()
            } catch (e: Exception) {
                isDirectory = true
            }

            if (!isDirectory) {
                copyFile(fullAssetPath, File(targetDir, file))
            } else {
                val subDir = File(targetDir, file)
                if (!subDir.exists()) subDir.mkdirs()
                copyDirectoryFromAssets(fullAssetPath, subDir)
            }
        }
    }

    suspend fun initEngineIfNeeded() = engineMutex.withLock {
        if (ttsEngine != null || hasAttemptedInit) return@withLock
        hasAttemptedInit = true

        try {
            val modelDir = File(context.filesDir, "kokoro_model")
            val completionFlag = File(modelDir, "copy_complete_v2.flag") // Bump version to force recopy if needed

            if (!completionFlag.exists()) {
                Log.d("NativeAutoCoachVoice", "Model files missing or update required. Copying assets...")
                if (modelDir.exists()) modelDir.deleteRecursively()
                modelDir.mkdirs()

                val rootAssets = listOf("model.onnx", "voices.bin", "tokens.txt", "lexicon-us-en.txt")
                for (asset in rootAssets) {
                    copyFile("kokoro_model/$asset", File(modelDir, asset))
                }

                val espeakDir = File(modelDir, "espeak-ng-data")
                if (!espeakDir.exists()) espeakDir.mkdirs()
                copyDirectoryFromAssets("kokoro_model/espeak-ng-data", espeakDir)

                // Verify critical files
                val phontab = File(espeakDir, "phontab")
                if (!phontab.exists()) {
                    Log.e("NativeAutoCoachVoice", "CRITICAL: espeak-ng-data/phontab is missing!")
                }

                completionFlag.createNewFile()
                Log.d("NativeAutoCoachVoice", "Copy complete.")
            }

            val modelConfig = OfflineTtsKokoroModelConfig(
                model = File(modelDir, "model.onnx").absolutePath,
                voices = File(modelDir, "voices.bin").absolutePath,
                tokens = File(modelDir, "tokens.txt").absolutePath,
                dataDir = File(modelDir, "espeak-ng-data").absolutePath,
                dictDir = "",
                lexicon = File(modelDir, "lexicon-us-en.txt").absolutePath
            )
            val config = com.k2fsa.sherpa.onnx.OfflineTtsConfig(
                model = com.k2fsa.sherpa.onnx.OfflineTtsModelConfig(kokoro = modelConfig),
                maxNumSentences = 1
            )

            ttsEngine = OfflineTts(null, config)

            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
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

            // FIX: Removed the SDK_INT >= 26 check and made focusRequest a direct val
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
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
                // FIX: Removed the SDK_INT >= 26 and null checks
                audioManager.abandonAudioFocusRequest(focusRequest)

                try {
                    track.pause()
                    track.flush()
                } catch (e: Exception) {}
            }
        }
    }

    fun interrupt() {
        // Sets the flag so any currently running loop in speakAndWait exits immediately
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