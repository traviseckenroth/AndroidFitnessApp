package com.example.myapplication.data.remote

import android.content.Context
import android.util.Log
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.smithy.kotlin.runtime.content.writeToFile
import aws.smithy.kotlin.runtime.http.engine.okhttp.OkHttpEngine
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.repository.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Singleton
class VoiceModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository
) {
    private val _downloadStatus = MutableStateFlow("Required for offline coaching.")
    val downloadStatus: StateFlow<String> = _downloadStatus.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val requiredModels = listOf(
        "decoder-epoch-99-avg-1.onnx",
        "encoder-epoch-99-avg-1.onnx",
        "joiner-epoch-99-avg-1.onnx",
        "tokens.txt",
        "kokoro_model/model.onnx",
        "kokoro_model/voices.bin"
    )

    private val bucketName = "aicoach-voice-models"

    // STRICT FILE VALIDATOR: Catches fake AWS XML error files
    private fun isValidFile(file: File, fileName: String): Boolean {
        if (!file.exists()) return false
        val size = file.length()

        return when {
            fileName.contains("encoder") -> size > 50_000_000 // Must be > 50MB
            fileName.contains("model.onnx") -> size > 50_000_000 // Must be > 50MB
            fileName.contains("decoder") || fileName.contains("joiner") -> size > 500_000 // > 500KB
            else -> size > 100 // tokens.txt, voices.bin
        }
    }

    fun checkLocalFiles() {
        val modelsDir = File(context.filesDir, "voice_models")
        val allFilesExist = requiredModels.all { fileName ->
            isValidFile(File(modelsDir, fileName), fileName)
        }
        if (allFilesExist) {
            _downloadStatus.value = "Models ready."
            _isReady.value = true
        }
    }

    private fun getS3Client(): S3Client {
        val cognitoProvider = CognitoCredentialsProvider(
            authRepository = authRepository,
            identityPoolId = BuildConfig.COGNITO_IDENTITY_POOL_ID,
            region = BuildConfig.AWS_REGION
        )
        return S3Client {
            region = BuildConfig.AWS_REGION
            credentialsProvider = cognitoProvider
            httpClient = OkHttpEngine {
                connectTimeout = 30.seconds
                socketReadTimeout = 300.seconds
            }
        }
    }

    suspend fun ensureModelsDownloaded() = withContext(Dispatchers.IO) {
        val modelsDir = File(context.filesDir, "voice_models")
        if (!modelsDir.exists()) modelsDir.mkdirs()

        // 1. First Pass: Delete any corrupted files
        requiredModels.forEach { fileName ->
            val targetFile = File(modelsDir, fileName)
            if (targetFile.exists() && !isValidFile(targetFile, fileName)) {
                Log.w("ModelDownloader", "Trashing corrupted file: $fileName")
                targetFile.delete()
            }
        }

        // 2. Check if we still need to download anything
        val needsDownload = requiredModels.any { fileName ->
            !isValidFile(File(modelsDir, fileName), fileName)
        }

        if (!needsDownload) {
            _downloadStatus.value = "Models ready."
            _isReady.value = true
            return@withContext
        }

        _isDownloading.value = true
        _downloadStatus.value = "Connecting to secure server..."

        try {
            getS3Client().use { s3 ->
                for ((index, fileName) in requiredModels.withIndex()) {
                    val targetFile = File(modelsDir, fileName)
                    targetFile.parentFile?.mkdirs()

                    if (!targetFile.exists()) {
                        _downloadStatus.value = "Downloading model files (${index + 1}/${requiredModels.size})..."
                        Log.d("ModelDownloader", "Fetching $fileName from S3...")

                        val request = GetObjectRequest {
                            bucket = bucketName
                            key = fileName
                        }

                        s3.getObject(request) { response ->
                            response.body?.writeToFile(targetFile)
                        }
                    }
                }
            }

            // Final check to make sure the download actually worked
            checkLocalFiles()
            if (!_isReady.value) {
                _downloadStatus.value = "Download incomplete. Please try again."
            } else {
                _downloadStatus.value = "Download complete!"
            }

        } catch (e: Exception) {
            Log.e("ModelDownloader", "Failed to download models", e)
            _downloadStatus.value = "Error: Check connection and try again."
        } finally {
            _isDownloading.value = false
        }
    }

    fun getModelDirectoryPath(): String {
        return File(context.filesDir, "voice_models").absolutePath
    }
}