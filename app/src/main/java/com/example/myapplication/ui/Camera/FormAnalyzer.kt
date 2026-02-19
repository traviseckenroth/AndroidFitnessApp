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
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
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
    
    // Optimization: Throttle
    private var lastProcessedTime = 0L
    private val FRAME_INTERVAL_MS = 100L // 10 FPS limit

    // Optimization: Use Standard detection instead of Accurate for speed
    private val detector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
    )

    @OptIn(ExperimentalGetImage::class)
    fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProcessedTime < FRAME_INTERVAL_MS) {
            imageProxy.close()
            return
        }
        lastProcessedTime = currentTime

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val rotation = imageProxy.imageInfo.rotationDegrees

            // Optimization: Only swap dimensions if needed, avoid complex tuple destructuring if speed critical
            val rotatedWidth = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
            val rotatedHeight = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height

            val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

            detector.process(inputImage)
                .addOnSuccessListener { pose ->
                    if (isPoseHighConfidence(pose)) {
                        onPoseUpdated(pose, rotatedWidth, rotatedHeight)

                        if (isStable(pose)) {
                            processPose(pose)
                        } else {
                            onVisualFeedback("Stabilizing...")
                        }
                    } else {
                        onVisualFeedback("Align full body")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("FormAnalyzer", "Detection failed", e)
                }
                .addOnCompleteListener { imageProxy.close() }
        } else {
            imageProxy.close()
        }
    }

    private fun isStable(pose: Pose): Boolean {
        val hip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP) ?: return false
        val knee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE) ?: return false

        val deltaHip = abs(hip.position.x - lastHipX)
        val deltaKnee = abs(knee.position.x - lastKneeX)

        lastHipX = hip.position.x
        lastKneeX = knee.position.x

        // Reduced stability count due to frame throttling (5 frames @ 10fps = 0.5s)
        return if (deltaHip < STABILITY_THRESHOLD && deltaKnee < STABILITY_THRESHOLD) {
            framesStable++
            framesStable > 5
        } else {
            framesStable = 0
            false
        }
    }

    private fun isPoseHighConfidence(pose: Pose): Boolean {
        val hip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val knee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        return (hip != null && hip.inFrameLikelihood > 0.7f && knee != null && knee.inFrameLikelihood > 0.7f)
    }

    private fun processPose(pose: Pose) {
        // Optimization: Access landmarks directly instead of mapping a list every frame
        val landmarks = arrayOfNulls<PoseLandmark>(3)
        var allFound = true
        for (i in profile.landmarks.indices) {
            val lm = pose.getPoseLandmark(profile.landmarks[i])
            if (lm == null) {
                allFound = false
                break
            }
            landmarks[i] = lm
        }

        if (!allFound) return

        val angle = calculateAngle(landmarks[0]!!, landmarks[1]!!, landmarks[2]!!)

        onVisualFeedback("Reps: $repCount | Angle: ${angle}°")
        trackRepProgress(angle)
    }

    private fun trackRepProgress(angle: Int) {
        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < 1500) return // 1.5s lockout

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
                Log.d("FormAnalyzer", "Depth Hit")
                onFormIssueDetected(FormFeedback.GOOD_DEPTH)
                lastTriggerTime = now
            }
        }
    }

    private fun getProfileForExercise(name: String): MotionProfile {
        return when {
            name.contains("Squat", true) -> MotionProfile(
                landmarks = listOf(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE),
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

    fun shutdown() {
        try {
            detector.close()
        } catch (e: Exception) {
            Log.e("FormAnalyzer", "Error closing detector", e)
        }
    }

    companion object {
        fun isSupported(name: String): Boolean =
            name.contains("Squat", true) || name.contains("Push-Up", true) || name.contains("Press", true)
    }
}

data class MotionProfile(val landmarks: List<Int>, val flexionThreshold: Int, val extensionThreshold: Int)