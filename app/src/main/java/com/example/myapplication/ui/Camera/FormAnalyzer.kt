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
    private val coach: VoiceCoach,
    private val onVisualFeedback: (String) -> Unit,
    private val onPoseUpdated: (Pose, Int, Int) -> Unit,
    private val onFormIssueDetected: (FormFeedback) -> Unit
) {
    private var repCount = 0
    private var isInRep = false
    private var hasHitDepth = false
    private val profile = getProfileForExercise(exerciseName)

    private val detector = PoseDetection.getClient(
        AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
            .build()
    )

    @OptIn(ExperimentalGetImage::class)
    fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val rotation = imageProxy.imageInfo.rotationDegrees

            // FIX 1: Handle Rotation for UI Scaling
            // If portrait (90 or 270), swap width/height so the UI scales correctly
            val (rotatedWidth, rotatedHeight) = if (rotation == 90 || rotation == 270) {
                imageProxy.height to imageProxy.width
            } else {
                imageProxy.width to imageProxy.height
            }

            val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

            detector.process(inputImage)
                .addOnSuccessListener { pose ->
                    // FIX 2: Check Confidence to prevent "ghost" reps
                    if (isPoseHighConfidence(pose)) {
                        processPose(pose)
                        onPoseUpdated(pose, rotatedWidth, rotatedHeight)
                    } else {
                        // Clear skeleton if confidence is low
                        onVisualFeedback("Align full body in frame")
                    }
                }
                .addOnCompleteListener { imageProxy.close() }
        } else {
            imageProxy.close()
        }
    }

    // Check if critical landmarks (Shoulders, Hips, Knees) are actually visible
    private fun isPoseHighConfidence(pose: Pose): Boolean {
        val criticalLandmarks = listOf(
            PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE
        )

        val validLandmarks = criticalLandmarks.mapNotNull { pose.getPoseLandmark(it) }
        if (validLandmarks.size < criticalLandmarks.size) return false

        // Require at least 60% probability that these points are in frame
        return validLandmarks.all { it.inFrameLikelihood > 0.6f }
    }

    private fun processPose(pose: Pose) {
        val landMarks = profile.landmarks.map { pose.getPoseLandmark(it) }

        if (landMarks.any { it == null }) {
            return
        }

        val angle = calculateAngle(landMarks[0]!!, landMarks[1]!!, landMarks[2]!!)
        trackRepProgress(angle)

        onVisualFeedback("REPS: $repCount  |  ANGLE: ${angle}°")
    }

    private fun trackRepProgress(angle: Int) {
        if (angle >= profile.extensionThreshold) {
            if (isInRep && hasHitDepth) {
                repCount++
                coach.onGoodRep(repCount)
                onFormIssueDetected(FormFeedback.NEUTRAL)
                isInRep = false
                hasHitDepth = false
            } else if (isInRep && !hasHitDepth) {
                coach.onFormIssue(FormFeedback.TOO_HIGH)
                onFormIssueDetected(FormFeedback.TOO_HIGH)
                isInRep = false
            }
        } else if (angle <= profile.flexionThreshold) {
            if (!isInRep) isInRep = true
            if (!hasHitDepth) {
                hasHitDepth = true
                coach.onFormIssue(FormFeedback.GOOD_DEPTH)
                onFormIssueDetected(FormFeedback.GOOD_DEPTH)
            }
        } else {
            if (!isInRep && angle < profile.extensionThreshold - 10) isInRep = true
        }
    }

    private fun getProfileForExercise(name: String): MotionProfile {
        return when {
            name.contains("Squat", true) -> MotionProfile(
                landmarks = listOf(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE),
                flexionThreshold = 85,
                extensionThreshold = 160
            )
            name.contains("Push-Up", true) || name.contains("Bench", true) -> MotionProfile(
                landmarks = listOf(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST),
                flexionThreshold = 90,
                extensionThreshold = 160
            )
            name.contains("Press", true) -> MotionProfile(
                landmarks = listOf(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST),
                flexionThreshold = 80,
                extensionThreshold = 165
            )
            else -> MotionProfile(
                landmarks = listOf(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE),
                flexionThreshold = 90,
                extensionThreshold = 170
            )
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

    companion object {
        fun isSupported(name: String): Boolean {
            return name.contains("Squat", true) ||
                    name.contains("Push-Up", true) ||
                    name.contains("Press", true) ||
                    name.contains("Bench", true)
        }
    }
}

data class MotionProfile(
    val landmarks: List<Int>,
    val flexionThreshold: Int,
    val extensionThreshold: Int
)