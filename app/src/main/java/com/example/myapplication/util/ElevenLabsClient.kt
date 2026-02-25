package com.example.myapplication.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.example.myapplication.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class ElevenLabsClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client = OkHttpClient()

    private val apiKey = BuildConfig.ELEVENLABS_API_KEY

    // Switching to "Eric" (xctasy8XvGp2cVO9HL9k) which is confirmed working for the user
    private val voiceId = "xctasy8XvGp2cVO9HL9k"

    suspend fun generateAndPlay(text: String): Boolean = suspendCancellableCoroutine { continuation ->
        var mediaPlayer: MediaPlayer? = null
        var tempAudioFile: File? = null

        if (apiKey.isBlank()) {
            Log.e("AutoCoach", "ElevenLabs: API Key is empty!")
            if (continuation.isActive) continuation.resume(false)
            return@suspendCancellableCoroutine
        }

        try {
            Log.d("AutoCoach", "ElevenLabs: Requesting REST TTS for: $text")
            
            val url = "https://api.elevenlabs.io/v1/text-to-speech/$voiceId?output_format=mp3_44100_128"

            val jsonBody = JSONObject().apply {
                put("text", text)
                put("model_id", "eleven_monolingual_v1")
                put("voice_settings", JSONObject().apply {
                    put("stability", 0.5)
                    put("similarity_boost", 0.75)
                })
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("xi-api-key", apiKey.trim())
                .addHeader("Accept", "audio/mpeg")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "No error body"
                    Log.e("AutoCoach", "ElevenLabs: REST API Error: ${response.code} - $errorBody")
                    if (continuation.isActive) continuation.resume(false)
                    return@suspendCancellableCoroutine
                }

                val tempFile = File(context.cacheDir, "temp_coach_audio_${System.currentTimeMillis()}.mp3")
                tempAudioFile = tempFile
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            val fileToPlay = tempAudioFile
            if (fileToPlay == null || !fileToPlay.exists()) {
                Log.e("AutoCoach", "ElevenLabs: Failed to save audio file")
                if (continuation.isActive) continuation.resume(false)
                return@suspendCancellableCoroutine
            }

            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .build()
                )
                setDataSource(fileToPlay.absolutePath)
                setOnCompletionListener { player ->
                    player.release()
                    fileToPlay.delete()
                    if (continuation.isActive) continuation.resume(true)
                }
                setOnErrorListener { player, what, extra ->
                    Log.e("AutoCoach", "ElevenLabs: MediaPlayer Error: $what, $extra")
                    player.release()
                    fileToPlay.delete()
                    if (continuation.isActive) continuation.resume(false)
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = mp

            continuation.invokeOnCancellation {
                try { if (mp.isPlaying) mp.stop() } catch (e: Exception) { }
                mp.release()
                fileToPlay.delete()
            }

        } catch (e: Exception) {
            Log.e("AutoCoach", "ElevenLabs: Network or Playback Error", e)
            mediaPlayer?.release()
            tempAudioFile?.delete()
            if (continuation.isActive) continuation.resume(false)
        }
    }
}
