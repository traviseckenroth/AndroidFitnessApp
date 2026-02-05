// app/src/main/java/com/example/myapplication/ui/plan/GeneratePlanScreen.kt
package com.example.myapplication.ui.plan

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.data.WorkoutPlan

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GeneratePlanScreen(
    onManualCreateClick: () -> Unit,
    onPlanGenerated: () -> Unit,
    viewModel: PlanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    // FIX: Observe acceptance state from ViewModel instead of local state
    val isAccepted by viewModel.isPlanAccepted.collectAsState()

    val context = LocalContext.current

    // -- LOCAL UI STATE --
    var showExerciseDialog by remember { mutableStateOf(false) }
    var showNutritionDialog by remember { mutableStateOf(false) }

    // -- FORM STATE --
    var goalInput by remember { mutableStateOf("") }
    val programs = listOf("Strength", "Physique", "Endurance")
    var selectedProgram by remember { mutableStateOf(programs[0]) }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var durationHours by remember { mutableFloatStateOf(1.0f) }
    val daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val selectedDays = remember { mutableStateListOf<String>() }

    // -- AUTO-OPEN DIALOG ON SUCCESS --
    // Only open if Success AND NOT accepted yet
    LaunchedEffect(uiState, isAccepted) {
        if (uiState is PlanUiState.Success && !isAccepted) {
            showExerciseDialog = true
        }
    }

    if (uiState is PlanUiState.Error) {
        Toast.makeText(context, (uiState as PlanUiState.Error).msg, Toast.LENGTH_LONG).show()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Plan Generator", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))

            // 1. ALWAYS SHOW INPUT FORM
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

            // 2. SHOW SUMMARY CARDS (Only if Accepted)
            if (isAccepted && uiState is PlanUiState.Success) {
                val plan = (uiState as PlanUiState.Success).plan
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Active Plan", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))

                // Nutrition Card
                ElevatedCard(
                    onClick = { showNutritionDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Nutrition Plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("View personalized macros & targets", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // Workout Card
                ElevatedCard(
                    onClick = { showExerciseDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Workout Schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(plan.explanation, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    }
                }
            }
        }

        // -- DIALOGS --

        // Exercise Popup
        if (showExerciseDialog && uiState is PlanUiState.Success) {
            val plan = (uiState as PlanUiState.Success).plan
            androidx.compose.ui.window.Dialog(onDismissRequest = { showExerciseDialog = false }) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(8.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (!isAccepted) "New Plan Generated" else "Workout Details",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            PlanDisplay(plan)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (!isAccepted) {
                            Button(
                                onClick = {
                                    viewModel.acceptCurrentPlan() // Update VM state
                                    showExerciseDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Accept Plan")
                            }
                        } else {
                            TextButton(onClick = { showExerciseDialog = false }, modifier = Modifier.fillMaxWidth()) {
                                Text("Close")
                            }
                        }
                    }
                }
            }
        }

        // Nutrition Popup
        if (showNutritionDialog && uiState is PlanUiState.Success) {
            val nutrition = (uiState as PlanUiState.Success).plan.nutrition
            androidx.compose.ui.window.Dialog(onDismissRequest = { showNutritionDialog = false }) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("Nutrition Targets", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(16.dp))
                        if (nutrition != null) {
                            Text("Calories: ${nutrition.calories}", style = MaterialTheme.typography.bodyLarge)
                            Text("Protein: ${nutrition.protein}", style = MaterialTheme.typography.bodyLarge)
                            Text("Carbs: ${nutrition.carbs}", style = MaterialTheme.typography.bodyLarge)
                            Text("Fats: ${nutrition.fats}", style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Timing: ${nutrition.timing}", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text("No nutrition data available.")
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        TextButton(onClick = { showNutritionDialog = false }, modifier = Modifier.align(Alignment.End)) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

// Reuse existing Form and Display components
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

    // DURATION SLIDER
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