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

class FormAnalyzer(
    private val exerciseName: String,
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
                    // ROUTER: Switch logic based on exercise name
                    when {
                        exerciseName.contains("Squat", ignoreCase = true) -> analyzeSquat(pose)
                        exerciseName.contains("Push-Up", ignoreCase = true) -> analyzePushUp(pose)
                        exerciseName.contains("Press", ignoreCase = true) -> analyzeOverheadPress(pose)
                        else -> onFeedback("Exercise not yet trained.")
                    }
                }
                .addOnFailureListener { /* Handle error */ }
                .addOnCompleteListener { imageProxy.close() }
        } else {
            imageProxy.close()
        }
    }

    // --- LOGIC 1: SQUATS (Hip/Knee/Ankle Angle) ---
    private fun analyzeSquat(pose: Pose) {
        val hip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val knee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val ankle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        if (hip != null && knee != null && ankle != null) {
            val angle = calculateAngle(hip, knee, ankle)
            if (angle < 75) onFeedback("Excellent depth! ($angle°)")
            else if (angle > 100) onFeedback("Go Lower! Break parallel. ($angle°)")
            else onFeedback("Good depth.")
        } else {
            onFeedback("Align full body in frame (Side View)")
        }
    }

    // --- LOGIC 2: PUSH-UPS (Shoulder/Elbow/Wrist Angle) ---
    private fun analyzePushUp(pose: Pose) {
        val shoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val elbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val wrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)

        if (shoulder != null && elbow != null && wrist != null) {
            val angle = calculateAngle(shoulder, elbow, wrist)
            if (angle < 90) onFeedback("Good depth! Push up.")
            else if (angle > 160) onFeedback("Lockout at top.")
            else onFeedback("Lower your chest.")
        } else {
            onFeedback("Align arm in frame (Side View)")
        }
    }

    // --- LOGIC 3: OVERHEAD PRESS (Elbow Extension + Bar Path) ---
    private fun analyzeOverheadPress(pose: Pose) {
        val shoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val elbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val wrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)

        if (shoulder != null && elbow != null && wrist != null) {
            val angle = calculateAngle(shoulder, elbow, wrist)
            // Ideally 170-180 is full lockout
            if (angle > 165) onFeedback("Good Lockout!")
            else if (angle < 90) onFeedback("Drive up!")
            else onFeedback("Keep pressing.")
        } else {
            onFeedback("Align upper body in frame (Front/Side)")
        }
    }

    private fun calculateAngle(first: PoseLandmark, middle: PoseLandmark, last: PoseLandmark): Int {
        val result = Math.toDegrees(
            atan2((last.position.y - middle.position.y).toDouble(), (last.position.x - middle.position.x).toDouble()) -
                    atan2((first.position.y - middle.position.y).toDouble(), (first.position.x - middle.position.x).toDouble())
        )
        var angle = abs(result)
        if (angle > 180) angle = 360.0 - angle
        return angle.toInt()
    }

    // Helper to check if an exercise is supported before showing UI button
    companion object {
        fun isSupported(name: String): Boolean {
            return name.contains("Squat", true) ||
                    name.contains("Push-Up", true) ||
                    name.contains("Press", true)
        }
    }
}