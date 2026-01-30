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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.plan.PlanUiState
import com.example.myapplication.ui.plan.PlanViewModel
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToWorkout: (Long) -> Unit,
    onNavigateToExerciseHistory: (Long) -> Unit, // Keep this for future use
    onNavigateToExerciseList: () -> Unit,
    planViewModel: PlanViewModel // Ensure this is the SHARED instance
) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val planUiState by planViewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        InfiniteScrollingCalendar(
            initialDate = LocalDate.now(),
            selectedDate = selectedDate,
            onDateSelected = { newDate -> selectedDate = newDate }
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text("TODAY'S SCHEDULE", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))

        if (planUiState is PlanUiState.Success) {
            val successState = (planUiState as PlanUiState.Success)
            val plan = successState.plan
            val startDate = successState.startDate

            val selectedDayName = selectedDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

            val daysSinceStart = TimeUnit.MILLISECONDS.toDays(selectedDate.toEpochDay() * 86400000 - startDate)
            val currentWeek = (daysSinceStart / 7).toInt() + 1

            val todayWorkout = plan.weeks
                .find { it.week == currentWeek }
                ?.days
                ?.find { it.day.equals(selectedDayName, ignoreCase = true) || it.day.startsWith(selectedDayName, ignoreCase = true) }

            if (todayWorkout != null) {
                ElevatedCard(
                    onClick = {
                        val uniqueId = "${currentWeek}-${todayWorkout.day}".hashCode().toLong()
                        onNavigateToWorkout(uniqueId)
                    },
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
                                Text(todayWorkout.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("Week $currentWeek • ${todayWorkout.exercises.size} Exercises", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                val uniqueId = "${currentWeek}-${todayWorkout.day}".hashCode().toLong()
                                onNavigateToWorkout(uniqueId)
                            },
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
            } else {
                // Rest Day Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Rest Day or No Plan Generated", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            // Empty State
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No Plan Active. Go to Generator.", color = Color.Gray)
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Text("QUICK ACTIONS", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { onNavigateToExerciseList() }, label = { Text("View Exercises") })
            AssistChip(onClick = {}, label = { Text("Cardio") })
            AssistChip(onClick = {}, label = { Text("Stretch") })
        }
    }
}
