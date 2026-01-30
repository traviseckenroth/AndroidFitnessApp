package com.example.myapplication.ui.plan

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.data.WorkoutPlan
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GeneratePlanScreen(
    onManualCreateClick: () -> Unit,
    onPlanGenerated: () -> Unit,
    viewModel: PlanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Inputs
    var goalInput by remember { mutableStateOf("") }
    val programs = listOf("Strength", "Hypertrophy", "Olympic Lifting", "CrossFit", "Endurance")
    var selectedProgram by remember { mutableStateOf(programs[0]) }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var durationHours by remember { mutableFloatStateOf(1.0f) }
    val daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val selectedDays = remember { mutableStateListOf<String>() }

    if (uiState is PlanUiState.Error) {
        Toast.makeText(context, (uiState as PlanUiState.Error).msg, Toast.LENGTH_LONG).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Plan Generator", style = MaterialTheme.typography.headlineMedium)

        // Input Section
        Column(modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())) {
            Spacer(modifier = Modifier.height(16.dp))

            PlanInputForm(
                goalInput,
                { goalInput = it },
                selectedProgram,
                { selectedProgram = it },
                isDropdownExpanded,
                { isDropdownExpanded = it },
                durationHours,
                { durationHours = it },
                selectedDays,
                { day ->
                    if (selectedDays.contains(day)) {
                        selectedDays.remove(day)
                    } else {
                        selectedDays.add(day)
                    }
                },
                programs,
                daysOfWeek,
                { viewModel.generatePlan(goalInput, selectedProgram, durationHours, selectedDays) },
                onManualCreateClick,
                uiState is PlanUiState.Loading
            )

            if (uiState is PlanUiState.Success) {
                Spacer(modifier = Modifier.height(16.dp))
                PlanDisplay((uiState as PlanUiState.Success).plan)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanInputForm(
    goalInput: String,
    onGoalChange: (String) -> Unit,
    selectedProgram: String,
    onProgramChange: (String) -> Unit,
    isDropdownExpanded: Boolean,
    onDropdownExpand: (Boolean) -> Unit,
    durationHours: Float,
    onDurationChange: (Float) -> Unit,
    selectedDays: List<String>,
    onDaySelected: (String) -> Unit,
    programs: List<String>,
    daysOfWeek: List<String>,
    onGenerateClick: () -> Unit,
    onManualCreateClick: () -> Unit,
    isLoading: Boolean
) {
    val context = LocalContext.current

    OutlinedTextField(
        value = goalInput,
        onValueChange = onGoalChange,
        label = { Text("Specific Goal (e.g. Bench 225)") },
        modifier = Modifier.fillMaxWidth()
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            OutlinedButton(onClick = { onDropdownExpand(true) }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedProgram, maxLines = 1)
            }
            DropdownMenu(expanded = isDropdownExpanded, onDismissRequest = { onDropdownExpand(false) }) {
                programs.forEach { prog ->
                    DropdownMenuItem(
                        text = { Text(prog) },
                        onClick = { onProgramChange(prog); onDropdownExpand(false) })
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("Duration: ${durationHours}h", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = durationHours,
                onValueChange = onDurationChange,
                valueRange = 0.5f..2.0f,
                steps = 6
            )
        }
    }

    Text("Days:", style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        daysOfWeek.forEach { day ->
            val selected = selectedDays.contains(day)
            FilterChip(
                selected = selected,
                onClick = { onDaySelected(day) },
                label = { Text(day) })
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = {
                if (goalInput.isBlank() || selectedDays.isEmpty()) {
                    Toast.makeText(context, "Enter Goal & Select Days", Toast.LENGTH_SHORT).show()
                } else {
                    onGenerateClick()
                }
            },
            enabled = !isLoading,
            modifier = Modifier.weight(1f)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
                Text("AI Generate")
            }
        }
        OutlinedButton(onClick = onManualCreateClick, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Manual")
        }
    }
}

@Composable
fun PlanDisplay(plan: WorkoutPlan) {
    val daysOrder = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val dateFormatter = DateTimeFormatter.ofPattern("MMM d")
    val today = LocalDate.now()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Header with explanation
        Text(plan.explanation, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))

        plan.weeks.forEach { week ->
            val weekStartDate = today.plusWeeks((week.week - 1).toLong()).with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            val weekEndDate = weekStartDate.plusDays(6)
            val dateRange = "${weekStartDate.format(dateFormatter)} - ${weekEndDate.format(dateFormatter)}"

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Week ${week.week}", style = MaterialTheme.typography.headlineSmall)
                        Text(dateRange, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    week.days.sortedBy { daysOrder.indexOf(it.day) }.forEach { day ->
                        Row(modifier = Modifier.padding(bottom = 4.dp)) {
                            Text("${day.day}: ", style = MaterialTheme.typography.titleMedium)
                            Text(day.title, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
