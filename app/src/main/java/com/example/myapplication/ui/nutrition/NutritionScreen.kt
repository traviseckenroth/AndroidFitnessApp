// app/src/main/java/com/example/myapplication/ui/nutrition/NutritionScreen.kt

package com.example.myapplication.ui.nutrition

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.data.NutritionPlan
import com.example.myapplication.data.local.FoodLogEntity
import com.example.myapplication.data.remote.MacroSummary
import com.example.myapplication.ui.theme.CarbColor
import com.example.myapplication.ui.theme.FatColor
import com.example.myapplication.ui.theme.ProteinColor
import java.util.Locale

@Composable
fun NutritionScreen(viewModel: NutritionViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val foodLogs by viewModel.foodLogs.collectAsState()
    val recentFoods by viewModel.recentFoods.collectAsState()
    val isLogging by viewModel.isLogging.collectAsState()
    val consumed by viewModel.consumedMacros.collectAsState()
    val currentGoalPace by viewModel.currentGoalPace.collectAsState()

    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // FIX: Destructured state completely eliminates the "Assigned value is never read" warnings
    val (showVoiceDialog, setShowVoiceDialog) = remember { mutableStateOf(false) }
    val (showManualDialog, setShowManualDialog) = remember { mutableStateOf(false) }
    val (showRecalculateDialog, setShowRecalculateDialog) = remember { mutableStateOf(false) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                viewModel.logFood(spokenText)
                Toast.makeText(context, "Analyzing: $spokenText", Toast.LENGTH_SHORT).show()
                setShowVoiceDialog(false)
            }
        }
    }

    // --- DIALOGS ---
    if (showRecalculateDialog) {
        RecalculatePlanDialog(
            currentPace = currentGoalPace,
            onDismiss = { setShowRecalculateDialog(false) },
            onConfirm = { selectedPace ->
                viewModel.generateNutrition(selectedPace)
                setShowRecalculateDialog(false)
            }
        )
    }

    if (showVoiceDialog) {
        VoiceLogDialog(
            onDismiss = { setShowVoiceDialog(false) },
            onConfirm = { query ->
                viewModel.logFood(query)
                setShowVoiceDialog(false)
            },
            onMicClick = {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "What did you eat?")
                }
                try {
                    speechLauncher.launch(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Voice input not supported on this device", Toast.LENGTH_SHORT).show()
                }
            },
            isLoading = isLogging
        )
    }

    if (showManualDialog) {
        ManualFoodDialog(
            recentFoods = recentFoods,
            onDismiss = { setShowManualDialog(false) },
            onConfirm = { name, cals, pro, carb, fat, meal ->
                viewModel.logManual(name, cals, pro, carb, fat, meal)
                setShowManualDialog(false)
            }
        )
    }

    // --- MAIN CONTENT ---
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text("Nutrition Guide", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)

            // UI STATE HANDLING
            when (val state = uiState) {
                is NutritionUiState.Loading -> Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                is NutritionUiState.NoExercisePlan -> {
                    Text(
                        "Please generate an exercise plan in the 'Plan' tab first to enable AI nutrition coaching.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                is NutritionUiState.Empty -> EmptyNutritionCard(onGenerateClick = { setShowRecalculateDialog(true) })

                is NutritionUiState.Success -> {
                    Column {
                        if (state.alignedGoal.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                            ) {
                                Text(
                                    text = "Nutrition automatically optimized for: ${state.alignedGoal}",
                                    modifier = Modifier.padding(12.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        NutritionDetailCard(
                            plan = state.plan,
                            consumed = consumed,
                            onRegenerateClick = { setShowRecalculateDialog(true) }
                        )
                    }
                }

                is NutritionUiState.Error -> {
                    Text("Error: ${state.msg}", color = MaterialTheme.colorScheme.error)
                }
            }

            Text("Today's Logs", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

            // BUTTONS ROW
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { setShowVoiceDialog(true) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Voice Log")
                }
                OutlinedButton(
                    onClick = { setShowManualDialog(true) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Manual Input")
                }
            }

            if (isLogging) {
                LinearProgressIndicator(progress = { 0f }, modifier = Modifier.fillMaxWidth())
                Text("AI analyzing your meal...", style = MaterialTheme.typography.bodySmall)
            }

            // LOGS LIST (Grouped)
            if (foodLogs.isEmpty()) {
                Text("No food logged yet today.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val grouped = foodLogs.groupBy { it.mealType }
                val order = listOf("Breakfast", "Lunch", "Dinner", "Snack")

                order.forEach { mealType ->
                    val logs = grouped[mealType]
                    if (!logs.isNullOrEmpty()) {
                        Text(
                            mealType,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        logs.forEach { log -> FoodLogCard(log) }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                grouped.filterKeys { it !in order }.forEach { (type, logs) ->
                    Text(
                        type,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    logs.forEach { log -> FoodLogCard(log) }
                }
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

// --- HELPER COMPOSABLES ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecalculatePlanDialog(
    currentPace: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selectedPace by remember { mutableStateOf(currentPace.ifBlank { "Maintain" }) }
    var expanded by remember { mutableStateOf(false) }

    // Upgrade options to include how the AI handles them
    val options = listOf(
        Pair("Lose Fat Fast", "Aggressive deficit. Max protein to preserve muscle tissue."),
        Pair("Slow Cut", "Moderate deficit. Steady fat loss while maintaining performance."),
        Pair("Maintain", "Maintenance calories. Optimized for recovery and recomposition."),
        Pair("Slow Bulk", "Slight surplus. Builds dense muscle with minimal fat gain."),
        Pair("Gain Muscle Fast", "Aggressive surplus. Maximizes raw size, strength, and energy.")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recalculate Nutrition", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Select a goal pace to override your daily caloric targets. Your macro split will still remain optimized for your active workout program.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedPace,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Goal Pace") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        options.forEach { (title, description) ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        Text(
                                            description,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedPace = title
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedPace) }) { Text("Generate Plan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ManualFoodDialog(
    recentFoods: List<FoodLogEntity>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fats by remember { mutableStateOf("") }
    var mealType by remember { mutableStateOf("Snack") }
    var showDropdown by remember { mutableStateOf(false) }

    val filteredHistory = recentFoods.filter {
        it.inputQuery.contains(name, ignoreCase = true) && it.inputQuery != name
    }.take(3)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manual Entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            showDropdown = true
                        },
                        label = { Text("Food Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = showDropdown && filteredHistory.isNotEmpty(),
                        onDismissRequest = { showDropdown = false }
                    ) {
                        filteredHistory.forEach { historyItem ->
                            DropdownMenuItem(
                                text = { Text(historyItem.inputQuery) },
                                onClick = {
                                    name = historyItem.inputQuery
                                    calories = historyItem.totalCalories.toString()
                                    protein = historyItem.totalProtein.toString()
                                    carbs = historyItem.totalCarbs.toString()
                                    fats = historyItem.totalFats.toString()
                                    mealType = historyItem.mealType
                                    showDropdown = false
                                }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = calories, onValueChange = { calories = it }, label = { Text("Cals") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = protein, onValueChange = { protein = it }, label = { Text("Pro") }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = carbs, onValueChange = { carbs = it }, label = { Text("Carb") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = fats, onValueChange = { fats = it }, label = { Text("Fat") }, modifier = Modifier.weight(1f))
                }

                Text("Meal Type:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    listOf("Breakfast", "Lunch", "Dinner", "Snack").forEach { type ->
                        FilterChip(
                            selected = mealType == type,
                            onClick = { mealType = type },
                            label = { Text(type.take(1)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, calories, protein, carbs, fats, mealType) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun NutritionDetailCard(
    plan: NutritionPlan,
    consumed: MacroSummary,
    onRegenerateClick: () -> Unit
) {
    // FIX: Destructured state for strategy popup
    val (showStrategyPopup, setShowStrategyPopup) = remember { mutableStateOf(false) }

    if (showStrategyPopup) {
        AlertDialog(
            onDismissRequest = { setShowStrategyPopup(false) },
            title = { Text("Nutrition Strategy", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    text = plan.explanation.ifBlank { "Based on your biometrics and calculated activity level." },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { setShowStrategyPopup(false) }) { Text("Close") }
            }
        )
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Daily Progress", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onRegenerateClick) {
                    Icon(Icons.Default.Refresh, contentDescription = "Regenerate")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            val targetCals = plan.calories.filter { it.isDigit() }.toIntOrNull() ?: 2000
            val targetPro = plan.protein.filter { it.isDigit() }.toIntOrNull() ?: 150
            val targetCarb = plan.carbs.filter { it.isDigit() }.toIntOrNull() ?: 200
            val targetFat = plan.fats.filter { it.isDigit() }.toIntOrNull() ?: 60

            ProgressMacroRow("Calories", consumed.calories, targetCals, MaterialTheme.colorScheme.primary)
            ProgressMacroRow("Protein", consumed.protein, targetPro, ProteinColor)
            ProgressMacroRow("Carbs", consumed.carbs, targetCarb, CarbColor)
            ProgressMacroRow("Fats", consumed.fats, targetFat, FatColor)

            Spacer(modifier = Modifier.height(16.dp))
            val remaining = targetCals - consumed.calories
            Text(
                text = if(remaining >= 0) "$remaining kcal remaining" else "${-remaining} kcal over",
                style = MaterialTheme.typography.labelLarge,
                color = if (remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Strategy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(
                    onClick = { setShowStrategyPopup(true) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = "Show Strategy Detail",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onRegenerateClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Recalculate Plan")
            }
        }
    }
}

@Composable
fun ProgressMacroRow(label: String, current: Int, target: Int, color: Color) {
    val progress = (current.toFloat() / target.toFloat()).coerceIn(0f, 1f)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text("$current / $target", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = color,
            trackColor = color.copy(alpha = 0.2f),
        )
    }
}

@Composable
fun VoiceLogDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit, onMicClick: () -> Unit, isLoading: Boolean) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Food") },
        text = {
            Column {
                Text("Type below or tap the Mic to speak.")
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text, onValueChange = { text = it }, label = { Text("What did you eat?") }, modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onMicClick, modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)) {
                        Icon(Icons.Default.Mic, contentDescription = "Speak", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(text) }, enabled = text.isNotBlank() && !isLoading) { Text("Log It") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun FoodLogCard(log: FoodLogEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(log.inputQuery, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(log.aiAnalysis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${log.totalCalories} kcal", fontWeight = FontWeight.Bold)
                Text("${log.totalProtein}g Pro", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${log.totalCarbs}g Carb", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${log.totalFats}g Fat", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun EmptyNutritionCard(onGenerateClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            }
            Text("Generate Nutrition Plan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(onClick = onGenerateClick, modifier = Modifier.fillMaxWidth()) { Text("Generate Plan") }
        }
    }
}