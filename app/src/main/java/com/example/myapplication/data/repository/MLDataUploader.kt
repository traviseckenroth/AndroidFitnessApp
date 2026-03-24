package com.example.myapplication.data.repository

import android.content.Context
import android.util.Log
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.smithy.kotlin.runtime.content.asByteStream
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.local.WorkoutDao
import com.example.myapplication.data.remote.CognitoCredentialsProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MLDataUploader @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val credentialsProvider: CognitoCredentialsProvider,
    @ApplicationContext private val context: Context
) {
    // These should ideally be in BuildConfig or a remote config, but keeping them here for now
    // while ensuring they match the project's AWS configuration.
    private val bucketName = "fitness-app-ml-training-data" 
    private val region = BuildConfig.AWS_REGION

    suspend fun uploadTrainingData() = withContext(Dispatchers.IO) {
        try {
            Log.d("MLDataUploader", "Starting ML data upload process...")
            
            // 1. Fetch the flat data from Room
            val trainingDataList = workoutDao.getMLTrainingData()
            if (trainingDataList.isEmpty()) {
                Log.d("MLDataUploader", "No completed training data found in database. Skipping upload.")
                return@withContext
            }

            Log.d("MLDataUploader", "Found ${trainingDataList.size} records to upload.")

            // 2. Convert to JSON format
            val jsonString = Json.encodeToString(trainingDataList)

            // 3. Write to a temporary local file
            val fileName = "training_data_${System.currentTimeMillis()}.json"
            val tempFile = File(context.cacheDir, fileName)
            tempFile.writeText(jsonString)
            
            Log.d("MLDataUploader", "Temporary file created: ${tempFile.absolutePath} (${tempFile.length()} bytes)")

            // 4. Initialize S3 Client using your existing Cognito credentials
            val s3 = S3Client {
                region = this@MLDataUploader.region
                credentialsProvider = this@MLDataUploader.credentialsProvider
            }

            // 5. Upload to S3
            Log.d("MLDataUploader", "Uploading to S3 bucket: $bucketName in region: $region")
            val request = PutObjectRequest {
                bucket = bucketName
                key = "android_uploads/$fileName" 
                body = tempFile.asByteStream()
                contentType = "application/json"
            }

            val response = s3.putObject(request)
            Log.d("MLDataUploader", "S3 Upload successful. ETag: ${response.eTag}")

            // 6. Cleanup local temp file
            if (tempFile.delete()) {
                Log.d("MLDataUploader", "Temporary file deleted.")
            } else {
                Log.w("MLDataUploader", "Failed to delete temporary file: ${tempFile.absolutePath}")
            }

        } catch (e: Exception) {
            Log.e("MLDataUploader", "CRITICAL ERROR during ML data upload to S3", e)
            // Rethrowing so that if this is called from a Worker, it can retry.
            throw e
        }
    }
}
