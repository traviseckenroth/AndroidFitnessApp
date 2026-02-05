// app/src/main/java/com/example/myapplication/ui/insights/InsightsScreen.kt

package com.example.myapplication.ui.insights

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.roundToInt

@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showExerciseDropdown by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Text(
                    "Performance Insights",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
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
        }
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
fun OneRepMaxGraph(
    dataPoints: List<Pair<Long, Float>>,
    lineColor: Color
) {
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
                Paint().apply {
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
        drawPath(fillPath, brush = Brush.verticalGradient(colors = listOf(lineColor.copy(alpha = 0.3f), Color.Transparent)))
    }
}