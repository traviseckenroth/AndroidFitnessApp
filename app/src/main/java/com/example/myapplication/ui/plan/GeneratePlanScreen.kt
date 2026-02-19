package com.example.myapplication.ui.plan

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.data.WorkoutPlan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Sync
import com.example.myapplication.data.repository.PlanProgress

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GeneratePlanScreen(
    onManualCreateClick: () -> Unit,
    onPlanGenerated: () -> Unit,
    viewModel: PlanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isAccepted by viewModel.isPlanAccepted.collectAsState()
    val nextBlockNumber by viewModel.nextBlockNumber.collectAsState()
    val planProgress by viewModel.planProgress.collectAsState()
    val context = LocalContext.current

    // -- LOCAL UI STATE --
    var showExerciseDialog by remember { mutableStateOf(false) }

    // -- FORM STATE --
    var goalInput by remember { mutableStateOf("") }
    val programs = listOf("Strength", "Physique", "Endurance")
    var selectedProgram by remember { mutableStateOf(programs[0]) }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var durationHours by remember { mutableFloatStateOf(1.0f) }
    val daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val selectedDays = remember { mutableStateListOf<String>() }

    // Auto-open dialog on success if not accepted
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
            Text(
                text = "Training Strategy",
                style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            // --- CYCLE BLOCK (Macrocycle -> Mesocycle Link) ---
            CycleInformationBlock(goalInput, selectedProgram)
            Spacer(modifier = Modifier.height(24.dp))

            // 1. NEXT BLOCK OFFER (If Eligible)
            if (nextBlockNumber != null && uiState !is PlanUiState.Loading) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Block $nextBlockNumber Ready!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "You are nearing the end of your current mesocycle. Generate Block $nextBlockNumber to automatically apply Progressive Overload and Variation based on your performance.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.generateNextBlock() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Generate Block $nextBlockNumber with AI")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))
            }

            // 2. INPUT FORM (Action Plan)
            Text(
                text = "Action Plan",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            
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

            // 3. ACTIVE PLAN CARD (If Accepted)
            if (isAccepted && uiState is PlanUiState.Success) {
                val plan = (uiState as PlanUiState.Success).plan
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Active Plan", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))

                ElevatedCard(
                    onClick = { showExerciseDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Workout Schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            
                            // Progress Counter
                            planProgress?.let {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = "${it.completedWorkouts}/${it.totalWorkouts}",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        
                        Text(plan.explanation, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        
                        // Progress Bar
                        planProgress?.let {
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = it.percentage,
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Tap to view details", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // -- EXERCISE DIALOG --
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
                            text = if (!isAccepted) "Block Preview" else "Workout Details",
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
                                    viewModel.acceptCurrentPlan()
                                    showExerciseDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Accept Block")
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
    }
}

@Composable
fun CycleInformationBlock(goal: String, programType: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Timeline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Training Structure",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Macrocycle link
            Text(
                text = "Macrocycle: ${if (goal.isBlank()) "Define your goal" else goal}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "A long-term training journey (typically 6-12 months) focused on achieving your ultimate objective through progressive blocks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Mesocycle explanation
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Iterative Mesocycles",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Forma automatically splits your journey into 4-6 week blocks. Each block is analyzed to calculate your next block, ensuring continuous overload and adaptation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    val programDefinitions = mapOf(
        "Strength" to "Prioritize raw power and 1RM",
        "Physique" to "Focus on muscle size and aesthetics",
        "Endurance" to "High reps and cardio for stamina"
    )

    OutlinedTextField(
        value = goalInput,
        onValueChange = onGoalChange,
        label = { Text("Goal (e.g., 'Bigger Chest')") },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(16.dp))

    ExposedDropdownMenuBox(
        expanded = isDropdownExpanded,
        onExpandedChange = { onDropdownExpand(it) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = selectedProgram,
            onValueChange = {},
            readOnly = true,
            label = { Text("Program Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )
        ExposedDropdownMenu(
            expanded = isDropdownExpanded,
            onDismissRequest = { onDropdownExpand(false) }
        ) {
            programs.forEach { selectionOption ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(text = selectionOption, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = programDefinitions[selectionOption] ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onProgramChange(selectionOption)
                        onDropdownExpand(false)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text("Session Duration: ${durationHours} Hours", style = MaterialTheme.typography.labelMedium)
    Slider(value = durationHours, onValueChange = onDurationChange, valueRange = 0.5f..2.0f, steps = 2)
    Spacer(modifier = Modifier.height(16.dp))
    Text("Days Available:", style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        daysOfWeek.forEach { day ->
            FilterChip(selected = selectedDays.contains(day), onClick = { onDaySelected(day) }, label = { Text(day) })
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Button(onClick = onGenerateClick, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) {
        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
        else Text("Generate Block 1 with AI")
    }
    Spacer(modifier = Modifier.height(16.dp))
    TextButton(onClick = onManualCreateClick, modifier = Modifier.fillMaxWidth()) {
        Text("Create Manual Plan")
    }
}

@Composable
fun PlanDisplay(plan: WorkoutPlan) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (plan.explanation.isNotBlank()) {
            Text(
                text = "Coach's Strategy:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = plan.explanation,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            HorizontalDivider()
        }
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
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

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