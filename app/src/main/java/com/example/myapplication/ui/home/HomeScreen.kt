// app/src/main/java/com/example/myapplication/ui/home/HomeScreen.kt
package com.example.myapplication.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.data.local.DailyWorkoutEntity
import com.example.myapplication.data.local.ContentSourceEntity
import com.example.myapplication.ui.navigation.Screen
import com.example.myapplication.ui.theme.PrimaryIndigo
import com.example.myapplication.ui.theme.SecondaryIndigo
import com.example.myapplication.ui.theme.SuccessGreen
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val workout by viewModel.dailyWorkout.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val workoutDates by viewModel.workoutDates.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()

    val dailyIntel by viewModel.dailyIntel.collectAsState()
    // NEW: Observe navigation events
    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { route ->
            onNavigate(route)
        }
    }

    val userName = "Travis"

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                HeaderSection(userName = userName)
                Spacer(modifier = Modifier.height(8.dp))
                InfiniteScrollingCalendar(
                    initialDate = LocalDate.now(),
                    selectedDate = selectedDate,
                    workoutDates = workoutDates,
                    onDateSelected = { viewModel.updateSelectedDate(it) }
                )
            }

            item {
                Text("Today's Session", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (workout != null) {
                    WorkoutCard(workout = workout!!, onNavigate = onNavigate)
                } else {
                    RestDayRecoveryCard(
                        onGenerateStretching = { viewModel.generateRecoverySession("Stretching") },
                        onGenerateAccessory = { viewModel.generateRecoverySession("Accessory") },
                        isGenerating = isGenerating
                    )
                }
            }

            item {
                val dailyIntel by viewModel.dailyIntel.collectAsState()
                dailyIntel?.let { intel ->
                    DailyIntelCard(
                        intel = intel,
                        onClick = { onNavigate(Screen.ContentDiscovery.createRoute(intel.sourceId)) }
                    )
                }
            }

            item { QuickActionsSection(onNavigate = onNavigate) }
        }
    }
}

@Composable
fun RestDayRecoveryCard(
    onGenerateStretching: () -> Unit,
    onGenerateAccessory: () -> Unit,
    isGenerating: Boolean
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SelfImprovement, null, tint = PrimaryIndigo, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("REST & RECHARGE", style = MaterialTheme.typography.labelLarge, color = PrimaryIndigo)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("Your body grows while you rest. Use today to stay mobile.",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(24.dp))

            if (isGenerating) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = PrimaryIndigo)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onGenerateStretching,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
                    ) {
                        Icon(Icons.Default.AutoMode, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("GENERATE STRETCHING FLOW")
                    }
                    OutlinedButton(
                        onClick = onGenerateAccessory,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, SecondaryIndigo)
                    ) {
                        Icon(Icons.Default.FitnessCenter, null, tint = SecondaryIndigo, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("GENERATE ACCESSORY WORK", color = SecondaryIndigo)
                    }
                }
            }
        }
    }
}
@Composable
fun QuickActionsSection(onNavigate: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Action 1: New Plan
        QuickActionCard(
            title = "New Plan",
            icon = Icons.Default.AutoMode,
            color = PrimaryIndigo,
            modifier = Modifier.weight(1f),
            onClick = { onNavigate(Screen.GeneratePlan.route) }
        )

        // Action 2: Manual Workout
        QuickActionCard(
            title = "Free Lift",
            icon = Icons.Default.FitnessCenter,
            color = SecondaryIndigo,
            modifier = Modifier.weight(1f),
            onClick = { onNavigate(Screen.ManualPlan.route) }
        )

        // Action 3: Log Food
        QuickActionCard(
            title = "Log Food",
            icon = Icons.Default.Restaurant,
            color = SuccessGreen,
            modifier = Modifier.weight(1f),
            onClick = { onNavigate(Screen.Nutrition.route) }
        )
    }
}

@Composable
fun QuickActionCard(
    title: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(100.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun HeaderSection(userName: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Welcome back,",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = userName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        // Profile Icon placeholder
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("T", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun WorkoutCard(workout: DailyWorkoutEntity, onNavigate: (String) -> Unit) {
    // Check if the title indicates a stretching/mobility session
    val isStretching = workout.title.contains("Recovery", ignoreCase = true) ||
            workout.title.contains("Stretching", ignoreCase = true)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp)
                ) {
                    // Dynamic icon based on workout type
                    Box(contentAlignment = Alignment.Center) {
                        Text(if (isStretching) "🧘" else "💪", fontSize = 24.sp)
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(workout.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (isStretching) "Mobility Session" else "Scheduled Session",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    // CONDITIONAL NAVIGATION:
                    if (isStretching) {
                        onNavigate(Screen.StretchingSession.createRoute(workout.workoutId))
                    } else {
                        onNavigate(Screen.ActiveWorkout.createRoute(workout.workoutId))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = if (isStretching) "START MOBILITY" else "START SESSION",
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun DailyIntelCard(intel: ContentSourceEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // Icon based on type
            Text(if (intel.mediaType == "Video") "📺" else "📰", fontSize = 32.sp)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Recommended for You",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = intel.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${intel.sportTag} • ${intel.summary.take(20)}...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
