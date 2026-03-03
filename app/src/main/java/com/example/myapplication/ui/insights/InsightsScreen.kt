// app/src/main/java/com/example/myapplication/ui/insights/InsightsScreen.kt
package com.example.myapplication.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.R
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.ui.navigation.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InsightsScreen(
    onNavigate: (Any) -> Unit,
    viewModel: InsightsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

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

            // Muscle Recovery (Moved from Home)
            item {
                MuscleRecoveryCard(fatigueMap = state.muscleFatigue)
            }

            // 1. Progress Graph with Scrolling Tabs
            item {
                InsightCard(title = "Estimated 1 Rep Max") {
                    Column {
                        if (state.availableExercises.isNotEmpty()) {
                            ScrollableTabRow(
                                selectedTabIndex = state.availableExercises.indexOf(state.selectedExercise).coerceAtLeast(0),
                                containerColor = Color.Transparent,
                                edgePadding = 0.dp,
                                divider = {}
                            ) {
                                state.availableExercises.forEach { exercise ->
                                    val selected = state.selectedExercise?.exerciseId == exercise.exerciseId
                                    Tab(
                                        selected = selected,
                                        onClick = { viewModel.selectExercise(exercise) },
                                        text = {
                                            Text(
                                                text = exercise.name,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    )
                                }
                            }
                        } else {
                            Text("No exercises available.", color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (state.oneRepMaxHistory.isNotEmpty()) {
                            OneRepMaxGraph(
                                dataPoints = state.oneRepMaxHistory,
                                lineColor = MaterialTheme.colorScheme.secondary, // Use Highlight color
                                surfaceColor = MaterialTheme.colorScheme.surface
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                                Text("Log sets to track your 1RM trend.", color = Color.Gray)
                            }
                        }
                    }
                }
            }

            // 2. Mesocycle Progression (Tonnage Trend)
            item {
                InsightCard(title = "Mesocycle Progression") {
                    Column {
                        Text(
                            "Total volume moved per week. Consistent increases indicate successful progressive overload.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        if (state.weeklyTonnage.size == 1) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                            ) {
                                Text(
                                    "Baseline Established: ${state.weeklyTonnage.first().second.roundToInt()} lbs.\nKeep logging workouts to build a weekly trend.",
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (state.weeklyTonnage.isNotEmpty()) {
                            val maxTonnage = state.weeklyTonnage.maxOfOrNull { it.second } ?: 1.0
                            state.weeklyTonnage.forEach { (week, volume) ->
                                val percentage = (volume / maxTonnage).toFloat()
                                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(week, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        Text("${(volume).roundToInt()} lbs", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.ExtraBold)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { percentage },
                                        modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape),
                                        color = MaterialTheme.colorScheme.secondary,
                                        trackColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        } else {
                            Text("Complete a week of workouts to see your progression.", color = Color.Gray)
                        }
                    }
                }
            }

            // 3. Muscle Distribution (Relative Percentages)
            item {
                InsightCard(title = "30-Day Muscle Focus") {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            "Your volume distribution across muscle groups. Percentage shows relative focus.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val maxVol = state.muscleVolumeDistribution.values.maxOrNull() ?: 1.0
                        val totalVol = state.muscleVolumeDistribution.values.sum()

                        state.muscleVolumeDistribution.entries
                            .sortedByDescending { it.value }
                            .forEach { (muscle, volume) ->
                                MuscleVolumeRow(muscle, volume, maxVol, totalVol)
                            }
                        if (state.muscleVolumeDistribution.isEmpty()) {
                            Text("No workout data in the last 30 days.", color = Color.Gray)
                        }
                    }
                }
            }

            // 4. Knowledge Hub (Interests)
            item {
                Column {
                    Text(
                        "Knowledge Hub",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    KnowledgeHubControl(viewModel)
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        "Recent Activity",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(onClick = { /* Could navigate to a full history screen if one existed */ }) {
                        Text("Past Sessions")
                    }
                }
            }

            if (state.recentWorkouts.isEmpty() && !state.isLoading) {
                item { Text("No recent workouts logged.", color = Color.Gray) }
            } else {
                items(state.recentWorkouts) { summary ->
                    WorkoutSummaryCard(
                        item = summary,
                        onClick = {
                            summary.workoutId?.let { id ->
                                onNavigate(WorkoutSummary(workoutId = id))
                            }
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(48.dp)) }
        }
    }
}

@Composable
fun AIStatusCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "AI-Optimized Training",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "Your performance data is being analyzed locally to refine your next session's RPE targets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun MuscleRecoveryCard(fatigueMap: Map<String, Float>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Muscle Recovery", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Red indicates fatigued muscles. Green indicates fully recovered.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Parse Fatigue Data (Consolidating similar names)
                val chest = fatigueMap.entries.filter { it.key.contains("chest", true) || it.key.contains("pec", true) }.maxOfOrNull { it.value } ?: 0f
                val back = fatigueMap.entries.filter { it.key.contains("back", true) || it.key.contains("lat", true) }.maxOfOrNull { it.value } ?: 0f
                val shoulders = fatigueMap.entries.filter { it.key.contains("shoulder", true) || it.key.contains("delt", true) }.maxOfOrNull { it.value } ?: 0f
                val arms = fatigueMap.entries.filter { it.key.contains("arm", true) || it.key.contains("bicep", true) || it.key.contains("tricep", true) }.maxOfOrNull { it.value } ?: 0f
                val core = fatigueMap.entries.filter { it.key.contains("ab", true) || it.key.contains("core", true) }.maxOfOrNull { it.value } ?: 0f
                val quads = fatigueMap.entries.filter { it.key.contains("quad", true) || it.key.contains("leg", true) }.maxOfOrNull { it.value } ?: 0f
                val hams = fatigueMap.entries.filter { it.key.contains("ham", true) || it.key.contains("glute", true) }.maxOfOrNull { it.value } ?: 0f

                // Left Labels
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    FatigueLabel("Shoulders", shoulders)
                    FatigueLabel("Arms", arms)
                    FatigueLabel("Quads", quads)
                }

                // IMAGE-BASED HEATMAP WITH PRECISE OVERLAYS
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.height(240.dp).width(120.dp)
                ) {
                    // 1. Draw your uploaded image as the base layer
                    Image(
                        painter = painterResource(id = R.drawable.male_body), // Must match your drawable filename
                        contentDescription = "Human Body Outline",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    // 2. Draw localized glowing heat spots OVER the image
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        // Helper function to draw a glowing spot
                        fun drawHeatSpot(xPercent: Float, yPercent: Float, radiusPercent: Float, fatigue: Float) {
                            if (fatigue <= 0.05f) return // Don't draw if fully rested to keep UI clean

                            val center = Offset(w * xPercent, h * yPercent)
                            val radius = w * radiusPercent
                            val color = androidx.compose.ui.graphics.lerp(Color(0xFF34A853), Color(0xFFEA4335), fatigue)

                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(color.copy(alpha = 0.7f), Color.Transparent),
                                    center = center,
                                    radius = radius
                                ),
                                radius = radius,
                                center = center
                            )
                        }

                        // --- COORDINATES ---
                        // Tweak these xPercent and yPercent values to perfectly align with your specific image!

                        // Chest (Pecs)
                        drawHeatSpot(0.4f, 0.28f, 0.15f, chest) // Left Pec
                        drawHeatSpot(0.6f, 0.28f, 0.15f, chest) // Right Pec

                        // Core (Abs)
                        drawHeatSpot(0.5f, 0.42f, 0.18f, core)

                        // Shoulders (Delts)
                        drawHeatSpot(0.28f, 0.25f, 0.12f, shoulders) // Left Delt
                        drawHeatSpot(0.72f, 0.25f, 0.12f, shoulders) // Right Delt

                        // Arms (Biceps/Triceps area)
                        drawHeatSpot(0.22f, 0.38f, 0.12f, arms) // Left Arm
                        drawHeatSpot(0.78f, 0.38f, 0.12f, arms) // Right Arm

                        // Legs (Quads)
                        drawHeatSpot(0.4f, 0.65f, 0.18f, maxOf(quads, hams)) // Left Thigh
                        drawHeatSpot(0.6f, 0.65f, 0.18f, maxOf(quads, hams)) // Right Thigh

                        // Calves
                        drawHeatSpot(0.42f, 0.85f, 0.1f, maxOf(quads, hams)) // Left Calf
                        drawHeatSpot(0.58f, 0.85f, 0.1f, maxOf(quads, hams)) // Right Calf
                    }
                }

                // Right Labels
                Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    FatigueLabel("Chest", chest)
                    FatigueLabel("Core", core)
                    FatigueLabel("Back", back)
                }
            }
        }
    }
}

@Composable
fun FatigueLabel(name: String, fatigue: Float) {
    val isRecovered = fatigue < 0.3f
    val color = androidx.compose.ui.graphics.lerp(Color(0xFF34A853), Color(0xFFEA4335), fatigue)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(
            text = if (isRecovered) "Ready" else "Fatigued",
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun InsightCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun MuscleVolumeRow(muscle: String, volume: Double, maxVolume: Double, totalVolume: Double) {
    val maxPercentage = (volume / maxVolume).toFloat()
    val relativePercentage = if (totalVolume > 0) ((volume / totalVolume) * 100).roundToInt() else 0

    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(muscle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Row {
                Text("$relativePercentage%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("(${(volume / 1000).roundToInt()}k lbs)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(maxPercentage) // Draw bar relative to max for good visual scaling
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.secondaryContainer)
                        )
                    )
            )
        }
    }
}

@Composable
fun OneRepMaxGraph(dataPoints: List<Pair<Long, Float>>, lineColor: Color, surfaceColor: Color) {
    val textPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 32f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(vertical = 16.dp)
    ) {
        val values = dataPoints.map { it.second }
        val maxVal = values.maxOrNull() ?: 100f
        val minVal = values.minOrNull() ?: 0f
        // Add 20% padding to range so graph lines don't hit the absolute top/bottom boundaries
        val range = ((maxVal - minVal).coerceAtLeast(10f)) * 1.2f
        val graphMin = (minVal - range * 0.1f).coerceAtLeast(0f)
        val graphMax = maxVal + range * 0.1f
        val actualRange = graphMax - graphMin

        val textWidth = 100f // Space reserved for the Y-Axis Labels
        val graphWidth = size.width - textWidth
        val graphHeight = size.height

        val pointSpacing = if (dataPoints.size > 1) graphWidth / (dataPoints.size - 1) else graphWidth / 2f

        // Draw Y-Axis Lines and Text
        for (i in 0..4) {
            val yRatio = i / 4f
            val yPos = graphHeight * (1 - yRatio)
            val labelValue = graphMin + (actualRange * yRatio)

            drawContext.canvas.nativeCanvas.drawText(
                "${labelValue.roundToInt()}",
                textWidth - 20f,
                yPos + 10f, // vertical alignment offset
                textPaint
            )

            drawLine(
                color = Color.Gray.copy(alpha = 0.2f),
                start = Offset(textWidth, yPos),
                end = Offset(size.width, yPos),
                strokeWidth = 1.dp.toPx()
            )
        }

        val path = Path()
        val points = mutableListOf<Offset>()

        dataPoints.forEachIndexed { index, pair ->
            val value = pair.second
            val normalizedY = (value - graphMin) / actualRange
            val x = if (dataPoints.size == 1) textWidth + (graphWidth / 2f) else textWidth + (index * pointSpacing)
            val y = graphHeight - (normalizedY * graphHeight)

            points.add(Offset(x, y))
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        if (points.isNotEmpty()) {
            drawPath(path = path, color = lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))

            // Draw points over the line
            points.forEach { point ->
                drawCircle(color = surfaceColor, center = point, radius = 6.dp.toPx())
                drawCircle(color = lineColor, center = point, radius = 4.dp.toPx())
            }

            // Fill area gradient
            val fillPath = Path().apply {
                addPath(path)
                lineTo(points.last().x, graphHeight)
                lineTo(points.first().x, graphHeight)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.3f), Color.Transparent),
                    startY = 0f,
                    endY = graphHeight
                )
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KnowledgeHubControl(viewModel: InsightsViewModel) {
    val subscriptions by viewModel.subscriptions.collectAsState()
    val recommendations by viewModel.recommendations.collectAsState()
    var customInterest by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = customInterest,
            onValueChange = { customInterest = it },
            placeholder = { Text("Search sports, athletes, topics...") },
            trailingIcon = {
                IconButton(onClick = {
                    if (customInterest.isNotBlank()) {
                        viewModel.addInterest(customInterest)
                        customInterest = ""
                    }
                }) {
                    Icon(Icons.Default.Search, "Add")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        if (subscriptions.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                subscriptions.forEach { sub ->
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.toggleSubscription(sub.tagName, sub.type) },
                        label = { Text(sub.tagName) },
                        trailingIcon = { Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }

        if (recommendations.isNotEmpty()) {
            Text("Suggested for you", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                recommendations.forEach { rec ->
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutSummaryCard(item: RecentWorkoutSummary, onClick: () -> Unit) {
    val sdf = SimpleDateFormat("EEEE, MMM dd", Locale.getDefault())
    val dateString = sdf.format(Date(item.date))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateString,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${item.totalVolume.roundToInt()} lbs",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Default.ChevronRight,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item.topExercises.forEach { exName ->
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = exName,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (item.totalExercises > 3) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "+${item.totalExercises - 3} more",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}