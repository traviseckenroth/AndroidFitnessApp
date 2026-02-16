// app/src/main/java/com/example/myapplication/data/remote/LiveTranscribeClient.kt

package com.example.myapplication.data.remote

import aws.sdk.kotlin.services.transcribestreaming.TranscribeStreamingClient
import aws.sdk.kotlin.services.transcribestreaming.model.*
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.repository.AuthRepository
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveTranscribeClient @Inject constructor(private val authRepository: AuthRepository) {
    private val client by lazy {
        val cognitoProvider = CognitoCredentialsProvider(
            authRepository = authRepository,
            identityPoolId = BuildConfig.COGNITO_IDENTITY_POOL_ID,
            region = BuildConfig.AWS_REGION
        )
        TranscribeStreamingClient {
            region = BuildConfig.AWS_REGION
            credentialsProvider = cognitoProvider
        }
    }

    // FIX: Use channelFlow to keep the HTTP/2 stream alive
    fun startStreaming(audioStreamFlow: Flow<ByteArray>): Flow<String> = channelFlow {
        val request = StartStreamTranscriptionRequest {
            languageCode = LanguageCode.EnUs
            mediaEncoding = MediaEncoding.Pcm
            mediaSampleRateHertz = 16000
            audioStream = audioStreamFlow.map { bytes ->
                AudioStream.AudioEvent(AudioEvent { audioChunk = bytes })
            }
        }

        try {
            client.startStreamTranscription(request) { response ->
                // This block keeps the connection open
                response.transcriptResultStream?.collect { event ->
                    if (event is TranscriptResultStream.TranscriptEvent) {
                        // Only send finalized phrases to the AI Coach
                        val result = event.value.transcript?.results
                            ?.firstOrNull { !it.isPartial }
                            ?.alternatives?.firstOrNull()?.transcript

                        if (!result.isNullOrBlank()) {
                            send(result) // Send to ViewModel
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e !is kotlinx.coroutines.CancellationException) {
                android.util.Log.e("Transcribe", "Stream error", e)
            }
            close(e)
        }
    }
}