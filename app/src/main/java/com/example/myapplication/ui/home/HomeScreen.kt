package com.example.myapplication.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.data.local.DailyWorkoutEntity
import com.example.myapplication.ui.theme.ElectricBlue
import com.example.myapplication.ui.theme.MutedGrey
import com.example.myapplication.ui.theme.NeonLime
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = hiltViewModel(),
    onNavigateToWorkout: (Long) -> Unit,
    onNavigateToExerciseList: () -> Unit,
    onManualLogClick: () -> Unit,
    onWarmUpClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val selectedDate by homeViewModel.selectedDate.collectAsState()
    val dailyWorkout by homeViewModel.dailyWorkout.collectAsState()
    val workoutDates by homeViewModel.workoutDates.collectAsState()
    val isHealthSynced by homeViewModel.isHealthSynced.collectAsState()

    // Refresh sync status every time the screen is composed/returned to
    LaunchedEffect(Unit) {
        homeViewModel.checkHealthSyncStatus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Fitness", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Calendar Component
            InfiniteScrollingCalendar(
                initialDate = LocalDate.now(),
                selectedDate = selectedDate,
                workoutDates = workoutDates,
                onDateSelected = { newDate -> homeViewModel.updateSelectedDate(newDate) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // --- HEALTH SYNC STATUS ---
            HealthSyncCard(
                isSynced = isHealthSynced,
                onNavigateToSettings = onSettingsClick
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "TODAY'S SCHEDULE",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            dailyWorkout?.let {
                TodayWorkoutCard(it, onNavigateToWorkout)
            } ?: RestDayRecoveryCard(onWarmUpClick = onWarmUpClick)

            Spacer(modifier = Modifier.height(24.dp))

            Text("QUICK ACTIONS", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))

            QuickActionsSection(
                onNavigateToExerciseList = onNavigateToExerciseList,
                onManualLogClick = onManualLogClick,
                onWarmUpClick = onWarmUpClick
            )
        }
    }
}
@Composable
fun RestDayRecoveryCard(onWarmUpClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SelfImprovement, // Or use a custom recovery icon
                    contentDescription = null,
                    tint = ElectricBlue, // Consistent with the new "Performance" palette
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "REST & RECOVER",
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricBlue
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "No lifting today, but don't stay static.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Active recovery improves blood flow and speeds up muscle repair.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Suggested Mobility Drill Preview
            Surface(
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.4f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = NeonLime,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Suggested: World's Greatest Stretch (5 reps/side)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onWarmUpClick,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, ElectricBlue),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricBlue)
            ) {
                Text("VIEW ALL MOBILITY PROTOCOLS")
            }
        }
    }
}
@Composable
fun HealthSyncCard(
    isSynced: Boolean,
    onNavigateToSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium, // Using theme-defined shapes
        colors = CardDefaults.cardColors(
            containerColor = if (isSynced)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
        ),
        onClick = onNavigateToSettings
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    // Brand Consistency: Use ElectricBlue instead of Pink
                    color = if (isSynced) ElectricBlue else MutedGrey,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Health Connect",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isSynced) "Activity syncing active" else "Not connected to Google Fit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isSynced) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Synced",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(
                    text = "FIX",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun QuickActionsSection(
    onNavigateToExerciseList: () -> Unit,
    onManualLogClick: () -> Unit,
    onWarmUpClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickActionItem(
            icon = Icons.Default.FitnessCenter,
            label = "Exercises",
            color = MaterialTheme.colorScheme.primaryContainer,
            iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = onNavigateToExerciseList,
            modifier = Modifier.weight(1f)
        )

        QuickActionItem(
            icon = Icons.Default.Edit,
            label = "Log Work",
            color = MaterialTheme.colorScheme.secondaryContainer,
            iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = onManualLogClick,
            modifier = Modifier.weight(1f)
        )

        QuickActionItem(
            icon = Icons.Default.DirectionsRun,
            label = "Warm Up",
            color = MaterialTheme.colorScheme.tertiaryContainer,
            iconColor = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = onWarmUpClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun QuickActionItem(
    icon: ImageVector,
    label: String,
    color: Color,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(90.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = iconColor)
        }
    }
}

@Composable
fun TodayWorkoutCard(workout: DailyWorkoutEntity, onNavigate: (Long) -> Unit) {
    ElevatedCard(
        onClick = { onNavigate(workout.workoutId) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(40.dp)
                ) { Box(contentAlignment = Alignment.Center) { Text("💪") } }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(workout.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onNavigate(workout.workoutId) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("START WORKOUT")
            }
        }
    }
}

@Composable
fun NoWorkoutCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("Rest Day or No Plan Generated", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
