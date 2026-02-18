// File: app/src/main/java/com/example/myapplication/ui/insights/InsightsScreen.kt
package com.example.myapplication.ui.insights

import android.graphics.Paint // Explicit import
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.data.local.CompletedWorkoutWithExercise
import com.example.myapplication.data.local.ContentSourceEntity
import com.example.myapplication.data.local.UserSubscriptionEntity
import com.example.myapplication.ui.navigation.Screen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun InsightsScreen(
    onNavigate: (String) -> Unit,
    viewModel: InsightsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val subscriptions by viewModel.subscriptions.collectAsState()
    var showExerciseDropdown by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Text(
                    "Performance Insights",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            item { AIStatusCard() }

            item {
                InsightCard(title = "Estimated 1 Rep Max") {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showExerciseDropdown = true }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = state.selectedExercise?.name ?: "Select Exercise",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select")
                        }

                        DropdownMenu(
                            expanded = showExerciseDropdown,
                            onDismissRequest = { showExerciseDropdown = false }
                        ) {
                            state.availableExercises.forEach { exercise ->
                                DropdownMenuItem(
                                    text = { Text(exercise.name) },
                                    onClick = {
                                        viewModel.selectExercise(exercise)
                                        showExerciseDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (state.oneRepMaxHistory.isNotEmpty()) {
                        OneRepMaxGraph(
                            dataPoints = state.oneRepMaxHistory,
                            lineColor = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Text("No history for this exercise.", color = Color.Gray)
                        }
                    }
                }
            }

            item {
                InsightCard(title = "Volume Distribution") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val maxVol = state.muscleVolumeDistribution.values.maxOrNull() ?: 1.0
                        state.muscleVolumeDistribution.entries
                            .sortedByDescending { it.value }
                            .forEach { (muscle, volume) ->
                                MuscleVolumeRow(muscle, volume, maxVol)
                            }
                        if (state.muscleVolumeDistribution.isEmpty()) {
                            Text("No workout data yet.", color = Color.Gray)
                        }
                    }
                }
            }

            // --- KNOWLEDGE HUB SECTION (INTERESTS ONLY) ---
            item {
                Column {
                    Text(
                        "Knowledge Hub",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Personalized intelligence from your favorite sports and athletes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                InsightCard(title = "Manage Interests") {
                    KnowledgeHubControl(viewModel)
                }
            }

            item {
                Text(
                    "Recent Activity",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (state.recentWorkouts.isEmpty()) {
                item { Text("No recent workouts logged.", color = Color.Gray) }
            } else {
                items(state.recentWorkouts) { item ->
                    CompletedWorkoutCard(item)
                }
            }

            item { Spacer(modifier = Modifier.height(48.dp)) }
        }
    }
}

// --- COMPONENTS ---

@Composable
fun AIStatusCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Hybrid Optimization Active",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "AI designs your macro-cycle. Local algorithms auto-regulate your weights weekly based on RPE.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
fun CompletedWorkoutCard(item: CompletedWorkoutWithExercise) {
    val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
    val dateString = sdf.format(Date(item.completedWorkout.date))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    dateString,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                VerticalStat(label = "REPS", value = "${item.completedWorkout.reps}")
                VerticalStat(label = "LBS", value = "${item.completedWorkout.weight}")
                VerticalStat(label = "RPE", value = "${item.completedWorkout.rpe}")
            }
        }
    }
}

@Composable
fun VerticalStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun InsightCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun MuscleVolumeRow(muscle: String, volume: Double, maxVolume: Double) {
    val percentage = (volume / maxVolume).toFloat()

    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(muscle, style = MaterialTheme.typography.bodyMedium)
            Text("${(volume / 1000).roundToInt()}k lbs", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { percentage },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
        )
    }
}

@Composable
fun OneRepMaxGraph(dataPoints: List<Pair<Long, Float>>, lineColor: Color) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .padding(16.dp)
    ) {
        val values = dataPoints.map { it.second }
        val maxVal = values.maxOrNull() ?: 100f
        val minVal = values.minOrNull() ?: 0f
        val range = (maxVal - minVal).coerceAtLeast(10f)

        val width = size.width
        val height = size.height
        val pointSpacing = width / (dataPoints.size - 1).coerceAtLeast(1)

        for (i in 0..2) {
            val yRatio = i / 2f
            val yPos = height * (1 - yRatio)
            val labelValue = (minVal + (range * yRatio)).roundToInt()

            drawLine(
                color = Color.Gray.copy(alpha = 0.2f),
                start = Offset(0f, yPos),
                end = Offset(width, yPos),
                strokeWidth = 1.dp.toPx()
            )

            drawContext.canvas.nativeCanvas.drawText(
                labelValue.toString(),
                0f,
                yPos - 10f,
                android.graphics.Paint().apply {
                    color = textColor
                    textSize = 32f
                }
            )
        }

        val path = Path()
        dataPoints.forEachIndexed { index, pair ->
            val value = pair.second
            val normalizedY = (value - minVal) / range
            val x = index * pointSpacing
            val y = height - (normalizedY * height)

            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(color = lineColor, center = Offset(x, y), radius = 4.dp.toPx())
        }

        drawPath(path = path, color = lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))

        val fillPath = Path()
        fillPath.addPath(path)
        fillPath.lineTo(width, height)
        fillPath.lineTo(0f, height)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.3f), Color.Transparent)
            )
        )
    }
}

@Composable
fun KnowledgeHubControl(viewModel: InsightsViewModel) {
    val subscriptions by viewModel.subscriptions.collectAsState()
    val recommendations by viewModel.recommendations.collectAsState()
    var customInterest by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // 1. ADD CUSTOM INTEREST
        OutlinedTextField(
            value = customInterest,
            onValueChange = { customInterest = it },
            label = { Text("Add Sport or Athlete") },
            trailingIcon = {
                IconButton(onClick = {
                    viewModel.addInterest(customInterest)
                    customInterest = ""
                }) {
                    Icon(Icons.Default.Add, "Add")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // 2. ACTIVE SUBSCRIPTIONS
        if (subscriptions.isNotEmpty()) {
            Text("Following", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                subscriptions.forEach { sub ->
                    InputChip(
                        selected = true,
                        onClick = { viewModel.toggleSubscription(sub.tagName, sub.type) },
                        label = { Text(sub.tagName) },
                        trailingIcon = { Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        }

        // 3. AI RECOMMENDATIONS
        if (recommendations.isNotEmpty()) {
            Text("Recommended for You", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recommendations) { rec ->
                    AssistChip(
                        onClick = { viewModel.addInterest(rec) },
                        label = { Text(rec) },
                        leadingIcon = { Icon(Icons.Default.AutoMode, null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        }
    }
}
