// Save this as SquatAnalyzer.kt in com.example.myapplication.ui.camera

package com.example.myapplication.ui.camera

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import kotlin.math.abs
import kotlin.math.atan2

class SquatAnalyzer(
    private val onFeedback: (String) -> Unit
) {
    private val options = AccuratePoseDetectorOptions.Builder()
        .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
        .build()
    private val detector = PoseDetection.getClient(options)

    @OptIn(ExperimentalGetImage::class)
    fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val inputImage = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            detector.process(inputImage)
                .addOnSuccessListener { pose ->
                    analyzeSquatForm(pose)
                }
                .addOnFailureListener {
                    // Handle error if needed
                }
                .addOnCompleteListener {
                    // CRITICAL: Close the proxy when the TASK is done, not before.
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun analyzeSquatForm(pose: Pose) {
        val hip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val knee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val ankle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        if (hip != null && knee != null && ankle != null) {
            val angle = calculateAngle(hip, knee, ankle)
            if (angle < 75) {
                onFeedback("Excellent depth! (Angle: ${angle.toInt()}°)")
            } else if (angle > 100) {
                onFeedback("Go lower! (Angle: ${angle.toInt()}°)")
            } else {
                onFeedback("Good rep.")
            }
        } else {
            onFeedback("Align full body in frame...")
        }
    }

    private fun calculateAngle(first: PoseLandmark, middle: PoseLandmark, last: PoseLandmark): Double {
        val result = Math.toDegrees(
            atan2((last.position.y - middle.position.y).toDouble(), (last.position.x - middle.position.x).toDouble()) -
                    atan2((first.position.y - middle.position.y).toDouble(), (first.position.x - middle.position.x).toDouble())
        )
        var angle = abs(result)
        if (angle > 180) {
            angle = 360.0 - angle
        }
        return angle
    }
}