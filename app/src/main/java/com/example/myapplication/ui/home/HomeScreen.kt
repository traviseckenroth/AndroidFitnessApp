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
    // ADDED: State collection for the calendar
    val selectedDate by viewModel.selectedDate.collectAsState()
    val workoutDates by viewModel.workoutDates.collectAsState()

    val userName = "Travis"

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header & Date
            item {
                Spacer(modifier = Modifier.height(16.dp))
                HeaderSection(userName = userName)
                Spacer(modifier = Modifier.height(8.dp))

                // RESTORED: Replaced static DateCard with InfiniteScrollingCalendar
                InfiniteScrollingCalendar(
                    initialDate = LocalDate.now(),
                    selectedDate = selectedDate,
                    workoutDates = workoutDates,
                    onDateSelected = { viewModel.updateSelectedDate(it) }
                )
            }



            // 3. Today's Workout Card
            item {
                Text(
                    "Today's Session",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (workout != null) {
                    WorkoutCard(workout = workout!!, onNavigate = onNavigate)
                } else {
                    NoWorkoutCard()
                }
            }

            item { Spacer(modifier = Modifier.height(15.dp)) }

            // 2. QUICK ACTIONS
            item {
                QuickActionsSection(onNavigate = onNavigate)
            }
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
                Icon(
                    Icons.Default.SelfImprovement,
                    null,
                    tint = PrimaryIndigo,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "REST & RECOVER",
                    style = MaterialTheme.typography.labelLarge,
                    color = PrimaryIndigo // Updated from ElectricBlue
                )
                Text(
                    "REST & RECOVER",
                    style = MaterialTheme.typography.labelLarge,
                    color = PrimaryIndigo
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

            Text(
                "No lifting today, but don't stay static.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Active recovery improves blood flow and speeds up muscle repair.",
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
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = SecondaryIndigo, // Updated from NeonLime
                        modifier = Modifier.size(18.dp)
                    )
                    Icon(
                        Icons.Default.Bolt,
                        null,
                        tint = SecondaryIndigo,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Suggested: World's Greatest Stretch",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Suggested: World's Greatest Stretch",
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
                OutlinedButton(
                    onClick = onWarmUpClick,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, PrimaryIndigo),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryIndigo)
                ) {
                    Text("VIEW ALL MOBILITY PROTOCOLS")
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
fun DateCard() {
    val today = LocalDate.now()
    val dateString = "${today.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}, ${today.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${today.dayOfMonth}"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = dateString,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun WorkoutCard(workout: DailyWorkoutEntity, onNavigate: (String) -> Unit) {
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
                    Box(contentAlignment = Alignment.Center) { Text("💪", fontSize = 24.sp) }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(workout.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Scheduled Session", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { onNavigate(Screen.ActiveWorkout.createRoute(workout.workoutId)) },                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("START SESSION", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun NoWorkoutCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Weekend, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Rest Day or No Plan Generated", color = MaterialTheme.colorScheme.onSurface, fontStyle = FontStyle.Italic)
            }
        }
    }
}