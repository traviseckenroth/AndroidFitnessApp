// app/src/main/java/com/example/myapplication/util/ElevenLabsStreamer.kt
package com.example.myapplication.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.AudioFocusRequest
import android.os.Build
import android.util.Base64
import android.util.Log
import com.example.myapplication.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ElevenLabsStreamer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client = OkHttpClient()
    
    @Volatile
    private var webSocket: WebSocket? = null
    private val apiKey = BuildConfig.ELEVENLABS_API_KEY
    
    // Eric Voice ID
    private val voiceId = "xctasy8XvGp2cVO9HL9k" 
    
    @Volatile
    private var audioTrack: AudioTrack? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val audioQueue = Channel<ByteArray>(Channel.UNLIMITED)
    private val sampleRate = 16000

    private var _lastConnectionError: String? = null
    val lastConnectionError: String? get() = _lastConnectionError

    init {
        // Launch a dedicated player coroutine
        scope.launch(Dispatchers.IO) {
            for (chunk in audioQueue) {
                var track = audioTrack
                while (track == null) {
                    delay(50)
                    track = audioTrack
                }
                
                try {
                    if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        track.play()
                    }
                    val result = track.write(chunk, 0, chunk.size)
                    if (result < 0) {
                        Log.e("ElevenLabsStreamer", "AudioTrack write error: $result")
                    }
                } catch (e: Exception) {
                    Log.e("ElevenLabsStreamer", "Error writing to AudioTrack", e)
                }
            }
        }
    }

    fun isAvailable(): Boolean {
        return !apiKey.isNullOrBlank() && apiKey != "null"
    }

    fun connect() {
        if (webSocket != null) return

        if (!isAvailable()) {
            _lastConnectionError = "API Key is missing"
            Log.e("AutoCoach", "ElevenLabsStreamer: API Key is missing or invalid!")
            return
        }

        requestAudioFocus()

        if (audioTrack == null) {
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
                .setBufferSizeInBytes(minBufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
        }

        // FIX: Added output_format=pcm_16000 to the URL query parameters as required by ElevenLabs for PCM streaming
        val url = "wss://api.elevenlabs.io/v1/text-to-speech/$voiceId/stream-input?model_id=eleven_turbo_v2_5&output_format=pcm_16000"
        Log.d("AutoCoach", "Connecting to ElevenLabs WS: $url")

        val request = Request.Builder()
            .url(url)
            .addHeader("xi-api-key", apiKey.trim())
            .build()

        _lastConnectionError = null
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(_webSocket: WebSocket, _response: Response) {
                Log.d("AutoCoach", "ElevenLabs WS Opened")
            }

            override fun onMessage(_webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    if (json.has("audio") && !json.isNull("audio")) {
                        val audioBase64 = json.getString("audio")
                        if (audioBase64.isNotEmpty()) {
                            val audioBytes = Base64.decode(audioBase64.replace("\n", "").replace("\r", ""), Base64.DEFAULT)
                            audioQueue.trySend(audioBytes)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AutoCoach", "ElevenLabs WS Parse Error", e)
                }
            }

            override fun onFailure(_webSocket: WebSocket, t: Throwable, _response: Response?) {
                val errorMsg = t.message ?: "Unknown WS Error"
                Log.e("AutoCoach", "ElevenLabs WS Failed: $errorMsg")
                _lastConnectionError = errorMsg
                this@ElevenLabsStreamer.webSocket = null
            }

            override fun onClosed(_webSocket: WebSocket, _code: Int, _reason: String) {
                Log.d("AutoCoach", "ElevenLabs WS Closed")
                this@ElevenLabsStreamer.webSocket = null
            }
        })
    }

    fun startNewGeneration() {
        if (webSocket == null) {
            connect()
        }

        Log.d("AutoCoach", "ElevenLabs: Initializing new generation stream.")
        val initPayload = JSONObject().apply {
            put("text", " ")
            put("voice_settings", JSONObject().apply {
                put("stability", 0.5)
                put("similarity_boost", 0.75)
            })
            // output_format should be in URL, but keeping here as well for safety
            put("output_format", "pcm_16000")
        }
        webSocket?.send(initPayload.toString())
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                .build()
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }
    }

    fun streamTextChunk(textChunk: String) {
        if (textChunk.isBlank()) return
        val payload = JSONObject().apply {
            put("text", textChunk)
            put("try_trigger_generation", true)
        }
        webSocket?.send(payload.toString())
    }

    fun flush() {
        Log.d("AutoCoach", "ElevenLabs: Flushing text buffer")
        webSocket?.send(JSONObject().apply { put("text", "") }.toString())
        
        scope.launch {
            delay(2000) 
            webSocket?.close(1000, "Generation complete")
            webSocket = null
        }
    }

    fun interrupt() {
        Log.d("AutoCoach", "ElevenLabs: Interrupting audio")
        while (audioQueue.tryReceive().isSuccess) { }
        audioTrack?.pause()
        audioTrack?.flush()
        audioTrack?.play()
    }

    fun disconnect() {
        webSocket?.close(1000, "Workout Ended")
        webSocket = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) { }
        audioTrack = null
    }
}
