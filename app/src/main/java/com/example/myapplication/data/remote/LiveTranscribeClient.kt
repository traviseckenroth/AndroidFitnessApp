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

    fun startStreaming(audioFlow: Flow<ByteArray>): Flow<String> = channelFlow {
        val request = StartStreamTranscriptionRequest {
            languageCode = LanguageCode.EnUs
            mediaEncoding = MediaEncoding.Pcm
            mediaSampleRateHertz = 16000
            // Map the audio flow safely
            audioStream = audioFlow.map { bytes ->
                AudioStream.AudioEvent(AudioEvent { audioChunk = bytes })
            }
        }

        try {
            client.startStreamTranscription(request) { response ->
                response.transcriptResultStream?.collect { event ->
                    if (event is TranscriptResultStream.TranscriptEvent) {
                        // CRITICAL: Only send finalized phrases
                        val transcript = event.value.transcript?.results
                            ?.firstOrNull { !it.isPartial }
                            ?.alternatives?.firstOrNull()?.transcript

                        if (!transcript.isNullOrBlank()) {
                            send(transcript)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Log specifically for cancellation or reset
            android.util.Log.w("TranscribeClient", "Session ended: ${e.message}")
            close(e) // Pass exception to ViewModel
        }
    }
}