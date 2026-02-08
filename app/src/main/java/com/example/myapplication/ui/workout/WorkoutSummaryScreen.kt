package com.example.myapplication.ui.summary

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
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

    // We keep the receipt invisible but ready to capture
    Box(modifier = Modifier.fillMaxSize()) {

        // 1. Main UI (What the user sees)
        Scaffold(
            bottomBar = {
                Button(
                    onClick = onNavigateHome,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("DONE")
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = PrimaryIndigo,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Workout Complete!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Great job sticking to the plan.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)

                Spacer(modifier = Modifier.height(32.dp))

                // The "Share" Button
                Button(
                    onClick = {
                        scope.launch {
                            // Capture the invisible receipt
                            val bitmap = graphicsLayer.toImageBitmap()
                            shareToInstagramStory(context, bitmap.asAndroidBitmap())
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share to Instagram Story")
                }
            }
        }

        // 2. The "Receipt" (Invisible to user, but rendered for capture)
        // We place it in a Box with zIndex or alpha 0 so it doesn't block interaction but is rendered.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                // The magic: Draw content into the graphics layer for capture
                .drawWithCache {
                    onDrawWithContent {
                        graphicsLayer.record {
                            this@onDrawWithContent.drawContent()
                        }
                        // We do NOT draw it to screen, so it remains invisible
                        // drawLayer(graphicsLayer)
                    }
                }
                .alpha(0f) // Double ensure it's not visible
        ) {
            WorkoutReceiptCard()
        }
    }
}

@Composable
fun WorkoutReceiptCard() {
    // A stylized card specifically designed for Instagram's 9:16 aspect ratio roughly
    Card(
        modifier = Modifier
            .width(350.dp) // Fixed width for consistent image generation
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White), // White background for clean look
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Branding
            Text("MY APPLICATION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Stats
            Text("SESSION COMPLETE", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = PrimaryIndigo)
            Text(
                LocalDate.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Receipt "Items"
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Volume", fontWeight = FontWeight.Bold)
                Text("12,450 lbs") // Replace with actual data from ViewModel
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Duration", fontWeight = FontWeight.Bold)
                Text("45m 20s")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Exercises", fontWeight = FontWeight.Bold)
                Text("6")
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Barcode visual (Fake)
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
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
        // 1. Save Bitmap to cache directory
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs() // make the directory
        val stream = FileOutputStream("$cachePath/workout_receipt.png") // Overwrites this image every time
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()

        // 2. Get URI via FileProvider
        val newFile = File(cachePath, "workout_receipt.png")
        val contentUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            newFile
        )

        // 3. Create Intent specifically for Instagram Stories
        val storiesIntent = Intent("com.instagram.share.ADD_TO_STORY").apply {
            setDataAndType(contentUri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra("content_url", "https://www.yourwebsite.com") // Optional: Link back
        }

        // 4. Verify Instagram is installed
        if (context.packageManager.resolveActivity(storiesIntent, 0) != null) {
            context.startActivity(storiesIntent)
        } else {
            // Fallback: Standard Share Sheet
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