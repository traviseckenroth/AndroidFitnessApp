package com.example.myapplication.ui.plan

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.data.WorkoutPlan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Sync
import com.example.myapplication.data.repository.PlanProgress
import kotlinx.coroutines.delay

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

    var showExerciseDialog by remember { mutableStateOf(false) }

    var goalInput by remember { mutableStateOf("") }
    val programs = listOf("Strength", "Physique", "Endurance")
    var selectedProgram by remember { mutableStateOf(programs[0]) }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var durationHours by remember { mutableFloatStateOf(1.0f) }
    val daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val selectedDays = remember { mutableStateListOf<String>() }

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

            CycleInformationBlock(goalInput, selectedProgram)
            Spacer(modifier = Modifier.height(24.dp))

            if (nextBlockNumber != null && uiState !is PlanUiState.Loading) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(8.dp))
                            Text("Block $nextBlockNumber Ready!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "You are nearing the end of your current mesocycle. Generate Block $nextBlockNumber to automatically apply Progressive Overload and Variation based on your performance.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.generateNextBlock() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Generate Block $nextBlockNumber with AI")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))
            }

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
                            Text("Workout Schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            
                            planProgress?.let {
                                Surface(
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = "${it.completedWorkouts}/${it.totalWorkouts}",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        
                        Text(plan.explanation, style = MaterialTheme.typography.bodyMedium, maxLines = 2, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        
                        planProgress?.let {
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { it.percentage },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Tap to view details", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = uiState is PlanUiState.Loading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                    .padding(16.dp)
            ) {
                SkeletonPlanLoader(currentThought = (uiState as? PlanUiState.Loading)?.thought)
            }
        }

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
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
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
fun SkeletonPlanLoader(currentThought: String? = null) {
    val defaultThoughts = listOf(
        "Analyzing past volume...",
        "Calculating Progressive Overload...",
        "Tailoring exercise selection...",
        "Optimizing intensity splits...",
        "Finalizing Mesocycle..."
    )
    var thoughtIndex by remember { mutableIntStateOf(0) }
    
    // Auto-cycle through default thoughts if no real thinking is provided yet
    LaunchedEffect(currentThought) {
        if (currentThought == null) {
            while (true) {
                delay(2500)
                thoughtIndex = (thoughtIndex + 1) % defaultThoughts.size
            }
        }
    }

    val displayThought = currentThought ?: defaultThoughts[thoughtIndex]

    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerTranslate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(shimmerTranslate - 200f, shimmerTranslate - 200f),
        end = Offset(shimmerTranslate, shimmerTranslate)
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(brush)
            )
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(48.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        AnimatedContent(
            targetState = displayThought,
            transitionSpec = {
                fadeIn(animationSpec = tween(800)) + slideInVertically { it / 2 } togetherWith
                fadeOut(animationSpec = tween(800)) + slideOutVertically { -it / 2 }
            },
            label = "thought"
        ) { thought ->
            Text(
                text = thought,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
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
                    tint = MaterialTheme.colorScheme.secondary,
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
    Slider(
        value = durationHours, 
        onValueChange = onDurationChange, 
        valueRange = 0.5f..2.0f, 
        steps = 2,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.secondary,
            activeTrackColor = MaterialTheme.colorScheme.secondary,
            inactiveTrackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
        )
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text("Days Available:", style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        daysOfWeek.forEach { day ->
            FilterChip(
                selected = selectedDays.contains(day), 
                onClick = { onDaySelected(day) }, 
                label = { Text(day) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Button(onClick = onGenerateClick, enabled = !isLoading, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
        Text("Generate Block 1 with AI")
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
                color = MaterialTheme.colorScheme.secondary
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
                        color = MaterialTheme.colorScheme.secondary,
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
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
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
