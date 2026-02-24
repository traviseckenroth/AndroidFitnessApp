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

    // Voice ID for "Marcus" (a deep, professional, coaching voice on ElevenLabs)
    private val voiceId = "bVMeCyTHy58xNoL34h3p"

    /**
     * Hits the ElevenLabs API, generates speech, saves it to a temp file,
     * plays it, and suspends the coroutine until the playback finishes.
     * Returns true if successful, false otherwise.
     */
    suspend fun generateAndPlay(text: String): Boolean = suspendCancellableCoroutine { continuation ->
        var mediaPlayer: MediaPlayer? = null
        var tempAudioFile: File? = null

        if (apiKey.isBlank()) {
            Log.w("ElevenLabs", "API Key is empty in BuildConfig. Please check your local.properties.")
            if (continuation.isActive) continuation.resume(false)
            return@suspendCancellableCoroutine
        }

        try {
            Log.d("ElevenLabs", "Attempting speech generation for: \"$text\"")
            
            // 1. Build the API Request
            val url = "https://api.elevenlabs.io/v1/text-to-speech/$voiceId?output_format=mp3_44100_128"

            val jsonBody = JSONObject().apply {
                put("text", text)
                put("model_id", "eleven_monolingual_v1") // Fastest model for short commands
                put("voice_settings", JSONObject().apply {
                    put("stability", 0.5)
                    put("similarity_boost", 0.75)
                })
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("xi-api-key", apiKey.trim()) // Ensure no extra whitespace
                .addHeader("Accept", "audio/mpeg")
                .build()

            // 2. Execute Request
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "No error body"
                    Log.e("ElevenLabs", "API Error: ${response.code} - $errorBody")
                    if (continuation.isActive) continuation.resume(false)
                    return@suspendCancellableCoroutine
                }

                // 3. Save Audio Stream to Temp File
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
                Log.e("ElevenLabs", "Failed to save audio to temp file")
                if (continuation.isActive) continuation.resume(false)
                return@suspendCancellableCoroutine
            }

            // 4. Play the Audio using MediaPlayer
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(fileToPlay.absolutePath)
                setOnCompletionListener { player ->
                    player.release()
                    fileToPlay.delete() // Clean up
                    if (continuation.isActive) continuation.resume(true)
                }
                setOnErrorListener { player, what, extra ->
                    Log.e("ElevenLabs", "MediaPlayer Error: $what, $extra")
                    player.release()
                    fileToPlay.delete()
                    if (continuation.isActive) continuation.resume(false)
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = mp

            // Handle Coroutine Cancellation (e.g., user hits 'Stop Coach')
            continuation.invokeOnCancellation {
                try {
                    if (mp.isPlaying) mp.stop()
                } catch (e: Exception) { /* already stopped */ }
                mp.release()
                fileToPlay.delete()
            }

        } catch (e: Exception) {
            Log.e("ElevenLabs", "Network or Playback Error", e)
            mediaPlayer?.release()
            tempAudioFile?.delete()
            if (continuation.isActive) continuation.resume(false)
        }
    }
}