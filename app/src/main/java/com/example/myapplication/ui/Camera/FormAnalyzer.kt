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
    private val onVisualFeedback: (String) -> Unit
) {
    // State for Rep Counting
    private var repCount = 0
    private var isInRep = false
    private var hasHitDepth = false

    // Config for the current exercise
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
            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            detector.process(inputImage)
                .addOnSuccessListener { pose -> processPose(pose) }
                .addOnCompleteListener { imageProxy.close() }
        } else {
            imageProxy.close()
        }
    }

    private fun processPose(pose: Pose) {
        // 1. Get the 3 distinct landmarks defined by the profile (e.g., Hip, Knee, Ankle)
        val landMarks = profile.landmarks.map { pose.getPoseLandmark(it) }

        // Ensure all points are visible
        if (landMarks.any { it == null }) {
            onVisualFeedback("Align full body in frame")
            return
        }

        // 2. Calculate the generic angle
        val angle = calculateAngle(landMarks[0]!!, landMarks[1]!!, landMarks[2]!!)

        // 3. Run the State Machine logic
        trackRepProgress(angle)

        onVisualFeedback("Reps: $repCount | Angle: ${angle}°")
    }

    private fun trackRepProgress(angle: Int) {
        // PHASE 1: START OF REP (Extended)
        // e.g. Standing up in a squat (Angle ~170+)
        if (angle >= profile.extensionThreshold) {
            if (isInRep && hasHitDepth) {
                // REP COMPLETE!
                repCount++
                coach.onGoodRep(repCount)
                isInRep = false
                hasHitDepth = false
            } else if (isInRep && !hasHitDepth) {
                // They went down a bit but not enough, then stood up
                coach.onFormIssue(FormFeedback.TOO_HIGH)
                isInRep = false // Reset
            }
        }

        // PHASE 2: BOTTOM OF REP (Flexed)
        // e.g. Bottom of squat (Angle < 80)
        else if (angle <= profile.flexionThreshold) {
            if (!isInRep) isInRep = true // Started descending

            if (!hasHitDepth) {
                hasHitDepth = true
                coach.onFormIssue(FormFeedback.GOOD_DEPTH) // "Perfect depth"
            }
        }

        // PHASE 3: MOVEMENT (In Between)
        else {
            if (!isInRep && angle < profile.extensionThreshold - 10) {
                // Just started moving down
                isInRep = true
            }
        }
    }

    // --- CONFIGURATION LOADER ---
    private fun getProfileForExercise(name: String): MotionProfile {
        return when {
            // SQUAT VARIATIONS
            name.contains("Squat", true) -> MotionProfile(
                landmarks = listOf(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE),
                flexionThreshold = 85,   // Depth (Go lower than this)
                extensionThreshold = 160 // Lockout (Go higher than this)
            )
            // PUSH-UP / BENCH
            name.contains("Push-Up", true) || name.contains("Bench", true) -> MotionProfile(
                landmarks = listOf(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST),
                flexionThreshold = 90,   // Chest to floor/bar
                extensionThreshold = 160 // Lockout
            )
            // OVERHEAD PRESS
            name.contains("Press", true) -> MotionProfile(
                landmarks = listOf(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST),
                flexionThreshold = 80,   // Bar at shoulders
                extensionThreshold = 165 // Bar overhead
            )
            // DEFAULT (Fallback)
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

    // Helper check for UI button visibility
    companion object {
        fun isSupported(name: String): Boolean {
            return name.contains("Squat", true) ||
                    name.contains("Push-Up", true) ||
                    name.contains("Press", true) ||
                    name.contains("Bench", true)
        }
    }
}

// Simple configuration data class
data class MotionProfile(
    val landmarks: List<Int>, // Which 3 joints?
    val flexionThreshold: Int, // The "Bottom" angle
    val extensionThreshold: Int // The "Top" angle
)