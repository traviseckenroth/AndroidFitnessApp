package com.example.myapplication.ui.workout

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.data.repository.WorkoutSummaryResult
import com.example.myapplication.ui.theme.FormaBlue
import com.example.myapplication.ui.theme.FormaTeal
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun WorkoutSummaryScreen(
    workoutId: Long,
    onNavigateHome: () -> Unit,
    viewModel: ActiveSessionViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()
    val summary by viewModel.workoutSummary.collectAsState()

    LaunchedEffect(workoutId) {
        viewModel.loadSummary(workoutId)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. HIDDEN LAYER FOR CAPTURING (Full Story size)
        if (summary != null) {
            Box(
                modifier = Modifier
                    .size(width = 1080.dp / 3f, height = 1920.dp / 3f)
                    .align(Alignment.Center)
                    .zIndex(0f)
                    .drawWithCache {
                        onDrawWithContent {
                            graphicsLayer.record {
                                this@onDrawWithContent.drawContent()
                            }
                        }
                    }
            ) {
                ShareableWorkoutCard(summary!!)
            }
        }

        // 2. MAIN UI
        Scaffold(
            modifier = Modifier.zIndex(1f),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                if (summary != null) {
                                    val bitmap = graphicsLayer.toImageBitmap()
                                    shareToStory(context, bitmap.asAndroidBitmap())
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FormaBlue,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = summary != null
                    ) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SHARE TO STORY", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onNavigateHome,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("DONE", fontWeight = FontWeight.Bold)
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Icon(Icons.Default.CheckCircle, null, tint = FormaTeal, modifier = Modifier.size(64.dp))
                Text(
                    text = summary?.title ?: "Workout Complete!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = summary?.subtitle ?: "Great effort today.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Preview Card (Scrollable in UI, but static for sharing)
                Card(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    if (summary != null) {
                        // In the UI preview, we make it scrollable so user can see all items
                        Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            ShareableWorkoutCard(summary!!, isPreview = true)
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = FormaBlue)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun ShareableWorkoutCard(data: WorkoutSummaryResult, isPreview: Boolean = false) {
    // If we're not in preview mode (i.e., we are capturing), we use a fixed height and fit content
    val containerModifier = if (isPreview) Modifier.fillMaxWidth() else Modifier.fillMaxSize()

    // Hardcoded vibrant export colors (Deep Slate theme) so it always looks premium on Instagram
    val exportBackgroundStart = Color(0xFF0F172A) // BackgroundDark
    val exportBackgroundEnd = Color(0xFF1E293B)   // SurfaceDark
    val exportAccent = Color(0xFF60A5FA)          // Bright Blue for contrast against slate

    Box(
        modifier = containerModifier
            .background(Brush.verticalGradient(listOf(exportBackgroundStart, exportBackgroundEnd)))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 40.dp.toPx()
            for (x in 0..size.width.toInt() step step.toInt()) {
                for (y in 0..size.height.toInt() step step.toInt()) {
                    drawCircle(Color.White.copy(alpha = 0.04f), radius = 1.5.dp.toPx(), center = Offset(x.toFloat(), y.toFloat()))
                }
            }
        }

        Column(
            modifier = Modifier.padding(24.dp).then(if (isPreview) Modifier.wrapContentHeight() else Modifier.fillMaxSize()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (isPreview) Arrangement.Top else Arrangement.Center
        ) {
            Text(
                "WORKOUT SUMMARY",
                style = MaterialTheme.typography.labelLarge,
                color = exportAccent,
                letterSpacing = 3.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM dd")),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF94A3B8) // Slate Gray
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BigStatItem("VOLUME MOVED", "${data.totalVolume}", "LBS", exportAccent)
                BigStatItem("PRS BROKEN", "${data.prsBroken}", "RECORDS", exportAccent)
            }

            if (data.topPR != null) {
                Spacer(modifier = Modifier.height(32.dp))
                Surface(
                    color = exportAccent.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, exportAccent.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("TOP HIGHLIGHT", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                            Text(data.topPR, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Lifts Summary - Show ALL exercises
            val liftFontSize = if (data.highlights.size > 8) 12.sp else 14.sp

            Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                data.highlights.forEach { highlight ->
                    Text(
                        text = "• $highlight",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = liftFontSize,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FitnessCenter, null, tint = exportAccent, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Forma Fitness", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            }

            if (isPreview) {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun BigStatItem(label: String, value: String, unit: String, accentColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 36.sp
        )
        Text(unit, style = MaterialTheme.typography.labelSmall, color = accentColor, fontWeight = FontWeight.Bold)
    }
}

private fun shareToStory(context: Context, bitmap: Bitmap) {
    try {
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = File(cachePath, "workout_story.png")
        val stream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()

        val contentUri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)

        val storyIntent = Intent("com.instagram.share.ADD_TO_STORY").apply {
            setDataAndType(contentUri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        if (context.packageManager.resolveActivity(storyIntent, 0) != null) {
            context.startActivity(storyIntent)
        } else {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Your Workout"))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}