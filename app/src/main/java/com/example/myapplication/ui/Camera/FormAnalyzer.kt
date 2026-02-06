// app/src/main/java/com/example/myapplication/ui/camera/FormAnalyzer.kt

package com.example.myapplication.ui.camera

import android.util.Log
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

    // Stability Vars
    private var lastHipX = 0f
    private var lastKneeX = 0f
    private var framesStable = 0
    private val STABILITY_THRESHOLD = 20f
    private var lastTriggerTime = 0L

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

            val (rotatedWidth, rotatedHeight) = if (rotation == 90 || rotation == 270) {
                imageProxy.height to imageProxy.width
            } else {
                imageProxy.width to imageProxy.height
            }

            val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

            detector.process(inputImage)
                .addOnSuccessListener { pose ->
                    if (isPoseHighConfidence(pose)) {
                        onPoseUpdated(pose, rotatedWidth, rotatedHeight)

                        // FIX: Logic now depends strictly on stability
                        if (isStable(pose)) {
                            processPose(pose)
                        } else {
                            onVisualFeedback("Stabilizing...")
                        }
                    } else {
                        onVisualFeedback("Align full body")
                    }
                }
                .addOnCompleteListener { imageProxy.close() }
        } else {
            imageProxy.close()
        }
    }

    private fun isStable(pose: Pose): Boolean {
        // FIX: Check BOTH Hip and Knee to ensure entire body isn't drifting
        val hip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP) ?: return false
        val knee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE) ?: return false

        val deltaHip = abs(hip.position.x - lastHipX)
        val deltaKnee = abs(knee.position.x - lastKneeX)

        lastHipX = hip.position.x
        lastKneeX = knee.position.x

        // Must be stable (movement < 20px) for 10 frames (~300ms)
        return if (deltaHip < STABILITY_THRESHOLD && deltaKnee < STABILITY_THRESHOLD) {
            framesStable++
            framesStable > 10
        } else {
            framesStable = 0
            false
        }
    }

    private fun isPoseHighConfidence(pose: Pose): Boolean {
        val criticalLandmarks = listOf(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE)
        return criticalLandmarks.all {
            val lm = pose.getPoseLandmark(it)
            lm != null && lm.inFrameLikelihood > 0.7f
        }
    }

    private fun processPose(pose: Pose) {
        val landMarks = profile.landmarks.map { pose.getPoseLandmark(it) }
        if (landMarks.any { it == null }) return

        val angle = calculateAngle(landMarks[0]!!, landMarks[1]!!, landMarks[2]!!)

        onVisualFeedback("Reps: $repCount | Angle: ${angle}°")
        trackRepProgress(angle)
    }

    private fun trackRepProgress(angle: Int) {
        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < 2000) return

        if (angle >= profile.extensionThreshold) {
            if (isInRep && hasHitDepth) {
                repCount++
                coach.onGoodRep(repCount)
                isInRep = false
                hasHitDepth = false
                lastTriggerTime = now
            }
        }
        else if (angle <= profile.flexionThreshold) {
            if (!isInRep) isInRep = true

            if (!hasHitDepth) {
                hasHitDepth = true
                Log.d("FormAnalyzer", "Depth Hit: Triggering AI") // Debug Log
                onFormIssueDetected(FormFeedback.GOOD_DEPTH)
                lastTriggerTime = now
            }
        }
    }

    private fun getProfileForExercise(name: String): MotionProfile {
        return when {
            name.contains("Squat", true) -> MotionProfile(
                landmarks = listOf(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE),
                // FIX: Relaxed depth to 95 degrees to ensure it triggers easier
                flexionThreshold = 95,
                extensionThreshold = 160
            )
            name.contains("Push-Up", true) || name.contains("Bench", true) -> MotionProfile(
                landmarks = listOf(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST),
                flexionThreshold = 90,
                extensionThreshold = 160
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
        fun isSupported(name: String): Boolean =
            name.contains("Squat", true) || name.contains("Push-Up", true) || name.contains("Press", true)
    }
}

data class MotionProfile(val landmarks: List<Int>, val flexionThreshold: Int, val extensionThreshold: Int)