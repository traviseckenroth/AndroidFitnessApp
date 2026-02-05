package com.example.myapplication.ui.plan

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.data.Exercise
import com.example.myapplication.data.WorkoutPlan

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GeneratePlanScreen(
    onManualCreateClick: () -> Unit,
    onPlanGenerated: () -> Unit,
    viewModel: PlanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // -- FORM STATE --
    var goalInput by remember { mutableStateOf("") }
    val programs = listOf("Strength", "Physique", "Endurance")
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
        // Dynamic Header
        Text(
            text = if (uiState is PlanUiState.Success) "Your 4-Week Plan" else "Plan Generator",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Content Area
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {

            if (uiState !is PlanUiState.Success) {
                // 1. SHOW INPUT FORM
                PlanInputForm(
                    goalInput, { goalInput = it },
                    selectedProgram, { selectedProgram = it },
                    isDropdownExpanded, { isDropdownExpanded = it },
                    durationHours, { durationHours = it },
                    selectedDays,
                    { day -> if (selectedDays.contains(day)) selectedDays.remove(day) else selectedDays.add(day) },
                    programs, daysOfWeek,
                    { viewModel.generatePlan(goalInput, selectedProgram, durationHours, selectedDays) },
                    onManualCreateClick,
                    uiState is PlanUiState.Loading
                )
            } else {
                // 2. SHOW FULL 4-WEEK PLAN
                val plan = (uiState as PlanUiState.Success).plan

                Button(
                    onClick = onPlanGenerated, // Navigates to Home
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text("Accept & Go to Calendar")
                }
                PlanOverviewCard(plan)
                PlanDisplay(plan)

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedButton(
                    onClick = { viewModel.resetState() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Discard & Try Again")
                }
            }
        }
    }
}

// --- FORM COMPONENT ---
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanInputForm(
    goalInput: String, onGoalChange: (String) -> Unit,
    selectedProgram: String, onProgramChange: (String) -> Unit,
    isDropdownExpanded: Boolean, onDropdownExpand: (Boolean) -> Unit,
    durationHours: Float, onDurationChange: (Float) -> Unit,
    selectedDays: List<String>, onDaySelected: (String) -> Unit,
    programs: List<String>, daysOfWeek: List<String>,
    onGenerateClick: () -> Unit, onManualCreateClick: () -> Unit,
    isLoading: Boolean
) {
    OutlinedTextField(
        value = goalInput,
        onValueChange = onGoalChange,
        label = { Text("Goal (e.g., 'Bigger Chest')") },
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(16.dp))

    // PROGRAM SELECTION
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Box(modifier = Modifier.weight(1f)) {
            OutlinedButton(onClick = { onDropdownExpand(true) }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedProgram)
            }
            DropdownMenu(expanded = isDropdownExpanded, onDismissRequest = { onDropdownExpand(false) }) {
                programs.forEach { prog ->
                    DropdownMenuItem(text = { Text(prog) }, onClick = { onProgramChange(prog); onDropdownExpand(false) })
                }
            }
        }
    }

    // DURATION SLIDER (Re-added)
    Spacer(modifier = Modifier.height(8.dp))
    Text("Session Duration: ${durationHours} Hours", style = MaterialTheme.typography.labelMedium)
    Slider(
        value = durationHours,
        onValueChange = onDurationChange,
        valueRange = 0.5f..2.0f,
        steps = 2 // Steps: 0.5, 1.0, 1.5, 2.0
    )

    Spacer(modifier = Modifier.height(16.dp))

    // DAYS SELECTION
    Text("Days Available:", style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        daysOfWeek.forEach { day ->
            FilterChip(
                selected = selectedDays.contains(day),
                onClick = { onDaySelected(day) },
                label = { Text(day) }
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Button(onClick = onGenerateClick, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) {
        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
        else Text("Generate Plan with AI")
    }

    Spacer(modifier = Modifier.height(16.dp))

    TextButton(onClick = onManualCreateClick, modifier = Modifier.fillMaxWidth()) {
        Text("Create Manual Plan")
    }
}

// --- FULL PLAN DISPLAY COMPONENT ---
@Composable
fun PlanDisplay(plan: WorkoutPlan) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        plan.weeks.forEach { week ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "WEEK ${week.week}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    week.days.forEach { day ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = day.day,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(day.title, style = MaterialTheme.typography.titleMedium)
                            }

                            // List exercises briefly
                            day.exercises.forEachIndexed { index, ex ->
                                Text(
                                    text = "${index + 1}. ${ex.name} (${ex.sets} x ${ex.reps})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, top = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}
@Composable
fun PlanOverviewCard(plan: WorkoutPlan) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Coach's Strategy",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = plan.explanation,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            if (plan.nutrition != null) {
                Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "Nutrition Targets",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                val n = plan.nutrition
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    NutrientItem("Calories", n.calories)
                    NutrientItem("Protein", n.protein)
                    NutrientItem("Carbs", n.carbs)
                    NutrientItem("Fats", n.fats)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Timing: ${n.timing}",
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun NutrientItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
