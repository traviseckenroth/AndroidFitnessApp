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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.data.local.DailyWorkoutEntity
import com.example.myapplication.ui.theme.MutedGrey
import com.example.myapplication.ui.theme.PrimaryIndigo
import com.example.myapplication.ui.theme.SecondaryIndigo
import com.example.myapplication.ui.theme.SuccessGreen
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

    LaunchedEffect(Unit) {
        homeViewModel.checkHealthSyncStatus()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar( // Changed to CenterAligned for the "Studio" look
                title = {
                    Text(
                        "My Fitness",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp) // Increased padding for "Breathability"
        ) {
            // Calendar Component
            InfiniteScrollingCalendar(
                initialDate = LocalDate.now(),
                selectedDate = selectedDate,
                workoutDates = workoutDates,
                onDateSelected = { newDate -> homeViewModel.updateSelectedDate(newDate) }
            )

            Spacer(modifier = Modifier.height(32.dp)) // Increased vertical rhythm

            // --- HEALTH SYNC STATUS ---
            HealthSyncCard(
                isSynced = isHealthSynced,
                onNavigateToSettings = onSettingsClick
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "TODAY'S SCHEDULE",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.2.sp // Technical tracking
            )
            Spacer(modifier = Modifier.height(12.dp))

            dailyWorkout?.let {
                TodayWorkoutCard(it, onNavigateToWorkout)
            } ?: RestDayRecoveryCard(onWarmUpClick = onWarmUpClick)

            Spacer(modifier = Modifier.height(32.dp))

            Text("QUICK ACTIONS", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(12.dp))

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
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SelfImprovement,
                    contentDescription = null,
                    tint = PrimaryIndigo, // Updated from ElectricBlue
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "REST & RECOVER",
                    style = MaterialTheme.typography.labelLarge,
                    color = PrimaryIndigo // Updated from ElectricBlue
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
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Suggested Mobility Drill Preview
            Surface(
                color = MaterialTheme.colorScheme.background, // Clean studio background
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = SecondaryIndigo, // Updated from NeonLime
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Suggested: World's Greatest Stretch",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedButton(
                onClick = onWarmUpClick,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, PrimaryIndigo), // Updated from ElectricBlue
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryIndigo)
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
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        onClick = onNavigateToSettings
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    // Updated: Use PrimaryIndigo for active state
                    color = if (isSynced) PrimaryIndigo else MutedGrey,
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
                        text = if (isSynced) "Activity syncing active" else "Not connected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isSynced) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Synced",
                    tint = SuccessGreen, // Updated from hardcoded hex
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
    // Removed specific container colors to enforce the "Uniform Neutral" style
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickActionItem(
            icon = Icons.Default.FitnessCenter,
            label = "Exercises",
            onClick = onNavigateToExerciseList,
            modifier = Modifier.weight(1f)
        )

        QuickActionItem(
            icon = Icons.Default.Edit,
            label = "Log Work",
            onClick = onManualLogClick,
            modifier = Modifier.weight(1f)
        )

        QuickActionItem(
            icon = Icons.Default.DirectionsRun,
            label = "Warm Up",
            onClick = onWarmUpClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun QuickActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Redesigned to be "Outlined" and subtle
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.height(90.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, // Uniform Primary Color
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun TodayWorkoutCard(workout: DailyWorkoutEntity, onNavigate: (Long) -> Unit) {
    ElevatedCard(
        onClick = { onNavigate(workout.workoutId) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 3.dp, // Increased slightly for hierarchy
            pressedElevation = 6.dp
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    modifier = Modifier.size(48.dp)
                ) { Box(contentAlignment = Alignment.Center) { Text("💪") } }

                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = workout.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Scheduled Session",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { onNavigate(workout.workoutId) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("START SESSION", fontWeight = FontWeight.Bold)
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