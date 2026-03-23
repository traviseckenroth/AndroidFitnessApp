package com.example.myapplication.data.repository

import android.content.Context
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.smithy.kotlin.runtime.content.asByteStream
import com.example.myapplication.data.local.WorkoutDao
import com.example.myapplication.data.remote.CognitoCredentialsProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject

class MLDataUploader @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val credentialsProvider: CognitoCredentialsProvider,
    @ApplicationContext private val context: Context
) {
    private val bucketName = "your-ml-training-data-bucket-name" // Replace with your S3 bucket name
    private val region = "us-east-1" // Replace with your AWS Region

    suspend fun uploadTrainingData() = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch the flat data from Room
            val trainingDataList = workoutDao.getMLTrainingData()
            if (trainingDataList.isEmpty()) return@withContext

            // 2. Convert to JSON format
            val jsonString = Json.encodeToString(trainingDataList)

            // 3. Write to a temporary local file
            val tempFile = File(context.cacheDir, "training_data_${System.currentTimeMillis()}.json")
            tempFile.writeText(jsonString)

            // 4. Initialize S3 Client using your existing Cognito credentials
            val s3 = S3Client {
                region = this@MLDataUploader.region
                credentialsProvider = this@MLDataUploader.credentialsProvider
            }

            // 5. Upload to S3
            val request = PutObjectRequest {
                bucket = bucketName
                key = "android_uploads/${tempFile.name}" // Folder structure in S3
                body = tempFile.asByteStream()
                contentType = "application/json"
            }

            s3.putObject(request)

            // 6. Cleanup local temp file
            tempFile.delete()

        } catch (e: Exception) {
            e.printStackTrace()
            // Handle upload failure (e.g., log to Crashlytics or retry later)
        }
    }
}