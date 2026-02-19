// app/src/main/java/com/example/myapplication/ui/camera/CameraFormCheckScreen.kt

package com.example.myapplication.ui.camera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
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
import androidx.core.content.PermissionChecker
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import java.text.SimpleDateFormat
import java.util.Locale
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

    val fallbackPhrases = remember { listOf("Good depth!", "Explode up!", "Stay tight!", "Nice rep!") }
    val coach = remember { VoiceCoach(context) }
    
    // Optimization: Create Analyzer once and reuse
    // We pass lambdas that update mutableState to avoid recreating the analyzer
    var feedbackText by remember { mutableStateOf("Align full body...") }
    var currentPose by remember { mutableStateOf<Pose?>(null) }
    var sourceWidth by remember { mutableIntStateOf(640) }
    var sourceHeight by remember { mutableIntStateOf(480) }
    var isAiThinking by remember { mutableStateOf(false) }

    val analyzer = remember {
        FormAnalyzer(
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
                            val aiComment = fetchAiCue(issue.name)
                            val textToSpeak = if (aiComment.isNotBlank()) aiComment else fallbackPhrases.random()
                            coach.speak(textToSpeak, force = true, flush = false)
                        } catch (e: Exception) {
                            coach.speak(fallbackPhrases.random(), force = true)
                        } finally {
                            isAiThinking = false
                        }
                    }
                }
            }
        )
    }

    // Optimization: Dedicated single thread executor for image analysis
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // Video Recording State
    var recording: Recording? by remember { mutableStateOf(null) }
    var isRecording by remember { mutableStateOf(false) }
    
    // Video Capture Use Case
    val recorder = remember {
        Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
            .build()
    }
    val videoCapture = remember { VideoCapture.withOutput(recorder) }

    DisposableEffect(Unit) {
        onDispose {
            coach.shutdown()
            analyzer.shutdown()
            cameraExecutor.shutdown()
            recording?.stop()
        }
    }

    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_FRONT_CAMERA) }
    
    // Permissions
    val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    var hasPermissions by remember {
        mutableStateOf(
            permissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { result -> hasPermissions = result.values.all { it } }
    )

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            launcher.launch(permissions)
        }
    }

    fun startRecording() {
        val name = "Forma_Workout_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/FormaWorkouts")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        recording = videoCapture.output
            .prepareRecording(context, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PermissionChecker.PERMISSION_GRANTED) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        isRecording = true
                        coach.speak("Recording started. Let's go!", force = true)
                    }
                    is VideoRecordEvent.Finalize -> {
                        isRecording = false
                        if (!recordEvent.hasError()) {
                            val msg = "Video saved: ${recordEvent.outputResults.outputUri}"
                            Toast.makeText(context, "Saved to Gallery", Toast.LENGTH_SHORT).show()
                            Log.d("Camera", msg)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e("Camera", "Video capture ended with error: ${recordEvent.error}")
                        }
                    }
                }
            }
    }

    fun stopRecording() {
        recording?.stop()
        recording = null
        isRecording = false
        coach.speak("Recording stopped.", force = true)
    }

    if (hasPermissions) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            
            val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
            
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // Bind Camera Lifecycle
            LaunchedEffect(cameraSelector) {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor) { imageProxy ->
                                analyzer.analyze(imageProxy)
                            }
                        }

                    try {
                        cameraProvider.unbindAll()
                        // Bind Preview, ImageAnalysis AND VideoCapture
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis,
                            videoCapture
                        )
                    } catch (e: Exception) {
                        Log.e("CameraScreen", "Binding failed. Trying fallback without video...", e)
                        // Fallback: Bind without video if 3 use cases are too heavy for device
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
                            Toast.makeText(context, "Recording not supported while analyzing on this device", Toast.LENGTH_LONG).show()
                        } catch (e2: Exception) {
                            Log.e("CameraScreen", "Fallback binding failed", e2)
                        }
                    }
                }, ContextCompat.getMainExecutor(context))
            }

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
            
            // RECORDING INDICATOR
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 48.dp)
                        .background(Color.Red.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FiberManualRecord, null, tint = Color.White, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("REC", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // CONTROLS
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Record Button
                IconButton(
                    onClick = { if (isRecording) stopRecording() else startRecording() },
                    modifier = Modifier.background(if (isRecording) Color.White else Color.Red, CircleShape)
                ) {
                    Icon(
                        if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                        contentDescription = "Record",
                        tint = if (isRecording) Color.Red else Color.White
                    )
                }

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
        PermissionDeniedView { launcher.launch(permissions) }
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
        
        // Optimization: Pre-calculate scaling factors
        val scaleX = screenWidth / sourceWidth
        val scaleY = screenHeight / sourceHeight
        val scale = maxOf(scaleX, scaleY)
        val offsetX = (screenWidth - sourceWidth * scale) / 2
        val offsetY = (screenHeight - sourceHeight * scale) / 2

        // Optimization: Define points of interest for lines to avoid iterating all landmarks
        val pairs = arrayOf(
            PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
            PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
            PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
            PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
            PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE
        )

        for ((startIdx, endIdx) in pairs) {
            val start = pose.getPoseLandmark(startIdx)
            val end = pose.getPoseLandmark(endIdx)

            if (start != null && end != null && start.inFrameLikelihood > 0.5f && end.inFrameLikelihood > 0.5f) {
                val startX = if (isFrontCamera) screenWidth - (start.position.x * scale + offsetX) else start.position.x * scale + offsetX
                val startY = start.position.y * scale + offsetY
                val endX = if (isFrontCamera) screenWidth - (end.position.x * scale + offsetX) else end.position.x * scale + offsetX
                val endY = end.position.y * scale + offsetY

                drawLine(
                    color = Color.Green,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 8f
                )
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
            Text("Camera & Audio permission required", color = Color.White)
            Button(onClick = onGrantClick) { Text("Grant") }
        }
    }
}
