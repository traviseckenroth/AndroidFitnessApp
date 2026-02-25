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
    private var webSocket: WebSocket? = null
    private val apiKey = BuildConfig.ELEVENLABS_API_KEY
    private val voiceId = "hpp4J3VqNfWAUOO0d1Us"
    private var audioTrack: AudioTrack? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val sampleRate = 16000

    fun connect() {
        if (webSocket != null) return

        if (apiKey.isNullOrBlank() || apiKey == "null") {
            Log.e("AutoCoach", "ElevenLabsStreamer: API Key is empty!")
            return
        }

        requestAudioFocus()

        // 1. Setup AudioTrack once if needed
        if (audioTrack == null) {
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize * 2,
                AudioTrack.MODE_STREAM
            )
            audioTrack?.play()
        }

        // 2. Connect to WebSocket
        val url = "wss://api.elevenlabs.io/v1/text-to-speech/$voiceId/stream-input?model_id=eleven_turbo_v2_5"
        Log.d("AutoCoach", "Connecting to ElevenLabs WS: $url")

        val request = Request.Builder()
            .url(url)
            .addHeader("xi-api-key", apiKey)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("AutoCoach", "ElevenLabs WS Opened")
                // Initial space message to wake up the stream is now handled in startNewGeneration
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    if (json.has("audio") && !json.isNull("audio")) {
                        val audioBase64 = json.getString("audio")
                        if (audioBase64.isNotEmpty()) {
                            val audioBytes = Base64.decode(audioBase64.replace("\n", "").replace("\r", ""), Base64.DEFAULT)
                            if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                                audioTrack?.play()
                            }
                            audioTrack?.write(audioBytes, 0, audioBytes.size)
                        }
                    }

                    if (json.optBoolean("isFinal", false)) {
                        Log.d("AutoCoach", "ElevenLabs: Received isFinal flag.")
                    }
                } catch (e: Exception) {
                    Log.e("AutoCoach", "ElevenLabs WS Parse Error", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("AutoCoach", "ElevenLabs WS Failed: ${t.message}")
                this@ElevenLabsStreamer.webSocket = null
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("AutoCoach", "ElevenLabs WS Closed")
                this@ElevenLabsStreamer.webSocket = null
            }
        })
    }

    fun startNewGeneration() {
        // If websocket is dead or not yet opened, connect.
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
            // Only send output_format on the first message of a connection
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
        
        // We close and nullify the websocket here to ensure the NEXT generation starts with a fresh connection.
        // ElevenLabs handles multiple generations poorly on a single long-lived socket if there are gaps.
        webSocket?.close(1000, "Generation complete")
        webSocket = null
    }

    fun interrupt() {
        Log.d("AutoCoach", "ElevenLabs: Interrupting audio")
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
