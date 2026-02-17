package com.example.myapplication.ui.summary

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.data.repository.WorkoutSummaryResult // Ensure this is imported
import com.example.myapplication.ui.theme.PrimaryIndigo
import com.example.myapplication.ui.workout.ActiveSessionViewModel
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

    // NEW: Trigger load when screen opens
    LaunchedEffect(workoutId) {
        viewModel.loadSummary(workoutId)
    }

    val summary by viewModel.workoutSummary.collectAsState()

    // Root Box
    Box(modifier = Modifier.fillMaxSize()) {

        // 1. The "Receipt" - Rendered BEHIND the Scaffold (z-index 0)
        // Only render if summary exists.
        if (summary != null) {
            Box(
                modifier = Modifier
                    .wrapContentSize()
                    .align(Alignment.Center)
                    .zIndex(0f)
                    .drawWithCache {
                        onDrawWithContent {
                            graphicsLayer.record {
                                this@onDrawWithContent.drawContent()
                            }
                            this@onDrawWithContent.drawContent()
                        }
                    }
            ) {
                WorkoutReceiptCard(summary!!)
            }
        }

        // 2. Main UI - Rendered ON TOP (z-index 1)
        Scaffold(
            modifier = Modifier.zIndex(1f),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Button(
                    onClick = onNavigateHome,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("DONE")
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PrimaryIndigo, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))

                // Dynamic Text from Summary
                Text(
                    text = summary?.title ?: "Workout Complete!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = summary?.subtitle ?: "Great job sticking to the plan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        scope.launch {
                            try {
                                if (summary != null) {
                                    val bitmap = graphicsLayer.toImageBitmap()
                                    shareToInstagramStory(context, bitmap.asAndroidBitmap())
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                    // Disable button until summary is loaded to prevent empty capture
                    enabled = summary != null
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share to Instagram Story")
                }
            }
        }
    }
}

@Composable
fun WorkoutReceiptCard(data: WorkoutSummaryResult) {
    // ... (Keep existing implementation) ...
    Card(
        modifier = Modifier.width(350.dp).padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("MY APPLICATION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(data.title.uppercase(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = PrimaryIndigo)
            Text(
                LocalDate.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Volume", fontWeight = FontWeight.Bold)
                Text("${data.totalVolume} lbs")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Exercises", fontWeight = FontWeight.Bold)
                Text("${data.totalExercises}")
            }

            Spacer(modifier = Modifier.height(32.dp))
            Row(modifier = Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                repeat(20) {
                    Box(modifier = Modifier.width(if (it % 3 == 0) 4.dp else 2.dp).fillMaxHeight().background(Color.Black))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Powered by AndroidFitnessApp", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

private fun shareToInstagramStory(context: Context, bitmap: Bitmap) {
    try {
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val stream = FileOutputStream("$cachePath/workout_receipt.png")
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()

        val newFile = File(cachePath, "workout_receipt.png")
        val contentUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            newFile
        )

        val storiesIntent = Intent("com.instagram.share.ADD_TO_STORY").apply {
            setDataAndType(contentUri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra("content_url", "https://www.yourwebsite.com")
        }

        if (context.packageManager.resolveActivity(storiesIntent, 0) != null) {
            context.startActivity(storiesIntent)
        } else {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Workout"))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}