// app/src/main/java/com/example/myapplication/ui/camera/CameraFormCheckScreen.kt

package com.example.myapplication.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

@Composable
fun CameraFormCheckScreen(
    exerciseName: String,
    onClose: () -> Unit,
    targetWeight: Int,
    targetReps: Int,
    fetchAiCue: suspend (String) -> String
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val fallbackPhrases = listOf("Good depth!", "Explode up!", "Stay tight!", "Nice rep!")
    val coach = remember { VoiceCoach(context) }

    // Track AI state to prevent overlapping requests
    var isAiThinking by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { coach.shutdown() }
    }

    var feedbackText by remember { mutableStateOf("Align full body...") }
    var currentPose by remember { mutableStateOf<Pose?>(null) }
    var sourceWidth by remember { mutableIntStateOf(640) }
    var sourceHeight by remember { mutableIntStateOf(480) }
    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_FRONT_CAMERA) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    previewView
                },
                modifier = Modifier.fillMaxSize(),
                update = { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val analyzer = FormAnalyzer(
                            exerciseName = exerciseName,
                            coach = coach,
                            onVisualFeedback = { feedback -> feedbackText = feedback },
                            onPoseUpdated = { pose, w, h ->
                                currentPose = pose
                                sourceWidth = w
                                sourceHeight = h
                            },
                            onFormIssueDetected = { issue: FormFeedback ->
                                if (!isAiThinking) {
                                    scope.launch {
                                        try {
                                            isAiThinking = true
                                            Log.d("CameraScreen", "Calling AI for issue: $issue")

                                            // Call API
                                            val aiComment = fetchAiCue(issue.name)
                                            val textToSpeak = if (aiComment.isNotBlank()) aiComment else fallbackPhrases.random()

                                            Log.d("CameraScreen", "Coach Speaking: $textToSpeak")
                                            coach.speak(textToSpeak, force = true, flush = false)
                                        } catch (e: Exception) {
                                            Log.e("CameraScreen", "AI Failed", e)
                                            coach.speak(fallbackPhrases.random(), force = true)
                                        } finally {
                                            isAiThinking = false
                                        }
                                    }
                                }
                            }
                        )

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { analysis ->
                                analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                    analyzer.analyze(imageProxy)
                                }
                            }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
                        } catch (exc: Exception) {
                            Log.e("CameraFormCheck", "Binding failed", exc)
                        }
                    }, ContextCompat.getMainExecutor(context))
                }
            )

            // VISUAL OVERLAYS
            if (currentPose != null) {
                SkeletonOverlay(
                    pose = currentPose!!,
                    sourceWidth = sourceWidth,
                    sourceHeight = sourceHeight,
                    isFrontCamera = cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA
                )
            }

            FeedbackOverlay(
                text = if (isAiThinking) "AI Thinking..." else feedbackText
            )

            // CONTROLS
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconButton(
                    onClick = {
                        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        } else {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        }
                    },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.Cameraswitch, contentDescription = "Switch Camera", tint = Color.White)
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.background(Color.Red.copy(alpha = 0.8f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    } else {
        PermissionDeniedView { launcher.launch(Manifest.permission.CAMERA) }
    }
}

@Composable
fun SkeletonOverlay(
    pose: Pose,
    sourceWidth: Int,
    sourceHeight: Int,
    isFrontCamera: Boolean
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val screenWidth = size.width
        val screenHeight = size.height

        val scaleX = screenWidth / sourceWidth
        val scaleY = screenHeight / sourceHeight
        val scale = maxOf(scaleX, scaleY)

        val offsetX = (screenWidth - sourceWidth * scale) / 2
        val offsetY = (screenHeight - sourceHeight * scale) / 2

        fun translate(landmark: PoseLandmark): Offset {
            val x = landmark.position.x * scale + offsetX
            val y = landmark.position.y * scale + offsetY
            val finalX = if (isFrontCamera) screenWidth - x else x
            return Offset(finalX, y)
        }

        val connections = listOf(
            Pair(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER),
            Pair(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP),
            Pair(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP),
            Pair(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP),
            Pair(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE),
            Pair(PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE),
            Pair(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE),
            Pair(PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE)
        )

        connections.forEach { (startType, endType) ->
            val start = pose.getPoseLandmark(startType)
            val end = pose.getPoseLandmark(endType)
            if (start != null && end != null && start.inFrameLikelihood > 0.5f && end.inFrameLikelihood > 0.5f) {
                drawLine(Color.Green, translate(start), translate(end), 8f)
            }
        }
    }
}

@Composable
fun FeedbackOverlay(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(bottom = 80.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.7f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Text(
                text = text,
                color = Color.Green,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(20.dp)
            )
        }
    }
}

@Composable
fun PermissionDeniedView(onGrantClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Camera permission required", color = Color.White)
            Button(onClick = onGrantClick) { Text("Grant") }
        }
    }
}
