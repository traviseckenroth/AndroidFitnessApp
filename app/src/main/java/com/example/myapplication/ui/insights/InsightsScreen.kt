package com.example.myapplication.ui.insights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.ui.plan.NutrientItem // Ensure this is accessible or redefined below

@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentPlan by viewModel.currentPlan.collectAsState()

    // Alias to match your existing logic
    val state = uiState
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
            // --- HEADER ---
            item {
                Text(
                    "Performance Insights",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // --- NEW: CURRENT PLAN STRATEGY & NUTRITION ---
            currentPlan?.let { plan ->
                item {
                    InsightCard(title = "Active Plan: ${plan.explanation.take(30)}...") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Explanation
                            Text(
                                text = plan.explanation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Nutrition Section
                            plan.nutrition?.let { nutrition ->
                                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                                Text(
                                    "Nutrition Targets",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    NutrientStat("Calories", nutrition.calories)
                                    NutrientStat("Protein", nutrition.protein)
                                    NutrientStat("Carbs", nutrition.carbs)
                                    NutrientStat("Fats", nutrition.fats)
                                }

                                if (nutrition.timing.isNotBlank()) {
                                    Text(
                                        text = "Timing: ${nutrition.timing}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- 1RM GRAPH SECTION ---
            item {
                InsightCard(title = "Estimated 1 Rep Max") {
                    // Exercise Selector
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showExerciseDropdown = !showExerciseDropdown } // Fix toggle logic
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

                    // THE GRAPH
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

            // --- MUSCLE BALANCE SECTION ---
            item {
                InsightCard(title = "Volume Distribution") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.muscleVolumeDistribution.isNotEmpty()) {
                            val maxVol = state.muscleVolumeDistribution.values.maxOrNull() ?: 1.0

                            state.muscleVolumeDistribution.entries
                                .sortedByDescending { it.value }
                                .forEach { (muscle, volume) ->

                                    MuscleVolumeRow(muscle, volume, maxVol)
                                }
                        } else {
                            Text("No workout data yet.", color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

// --- HELPER COMPOSABLES ---

@Composable
fun NutrientStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Re-defining InsightCard here if it wasn't global,
// otherwise you can remove this if it exists elsewhere.
@Composable
fun InsightCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
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