package com.example.myapplication.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.data.local.DailyWorkoutEntity
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = hiltViewModel(),
    onNavigateToWorkout: (Long) -> Unit,
    onNavigateToExerciseList: () -> Unit,
    onManualLogClick: () -> Unit,
    onWarmUpClick: () -> Unit
) {
    val selectedDate by homeViewModel.selectedDate.collectAsState()
    val dailyWorkout by homeViewModel.dailyWorkout.collectAsState()
    val workoutDates by homeViewModel.workoutDates.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        InfiniteScrollingCalendar(
            initialDate = LocalDate.now(),
            selectedDate = selectedDate,
            workoutDates = workoutDates,
            onDateSelected = { newDate -> homeViewModel.updateSelectedDate(newDate) }
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text("TODAY'S SCHEDULE", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))

        dailyWorkout?.let {
            TodayWorkoutCard(it, onNavigateToWorkout)
        } ?: NoWorkoutCard()

        Spacer(modifier = Modifier.height(24.dp))

        // --- UPDATED QUICK ACTIONS ---
        Text("QUICK ACTIONS", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))

        QuickActionsSection(
            onNavigateToExerciseList = onNavigateToExerciseList,
            onManualLogClick = onManualLogClick,
            onWarmUpClick = onWarmUpClick
        )
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
        // 1. View Exercises
        QuickActionItem(
            icon = Icons.Default.FitnessCenter,
            label = "Exercises",
            color = MaterialTheme.colorScheme.primaryContainer,
            iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = onNavigateToExerciseList,
            modifier = Modifier.weight(1f)
        )

        // 2. Log Workout
        QuickActionItem(
            icon = Icons.Default.Edit,
            label = "Log Work",
            color = MaterialTheme.colorScheme.secondaryContainer,
            iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = onManualLogClick,
            modifier = Modifier.weight(1f)
        )

        // 3. Warm Up
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
        modifier = modifier.height(90.dp), // Slightly compact height
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