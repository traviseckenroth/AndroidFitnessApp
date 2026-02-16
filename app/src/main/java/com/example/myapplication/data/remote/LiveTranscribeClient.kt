// app/src/main/java/com/example/myapplication/data/remote/LiveTranscribeClient.kt
package com.example.myapplication.data.remote

import aws.sdk.kotlin.services.transcribestreaming.TranscribeStreamingClient
import aws.sdk.kotlin.services.transcribestreaming.model.*
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
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

    suspend fun startStreaming(audioFlow: Flow<ByteArray>): Flow<String> {
        val request = StartStreamTranscriptionRequest {
            languageCode = LanguageCode.EnUs
            mediaEncoding = MediaEncoding.Pcm
            mediaSampleRateHertz = 16000
            audioStream = audioFlow.map { bytes ->
                AudioStream.AudioEvent(AudioEvent { audioChunk = bytes })
            }
        }

        return client.startStreamTranscription(request) { response ->
            response.transcriptResultStream?.mapNotNull { event ->
                if (event is TranscriptResultStream.TranscriptEvent) {
                    event.value.transcript?.results?.firstOrNull()?.alternatives?.firstOrNull()?.transcript
                } else null
            } ?: emptyFlow()
        }
    }
}