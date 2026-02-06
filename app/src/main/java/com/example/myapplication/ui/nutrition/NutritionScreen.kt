package com.example.myapplication.ui.nutrition

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import java.util.Locale

@Composable
fun NutritionScreen(viewModel: NutritionViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val foodLogs by viewModel.foodLogs.collectAsState()
    val isLogging by viewModel.isLogging.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    var showLogDialog by remember { mutableStateOf(false) }

    // --- VOICE RECOGNITION LAUNCHER ---
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                // Auto-log or just populate? Let's populate the dialog state
                // Since we can't easily pass state back into the open dialog from here without a shared state holder,
                // We will close the dialog and instantly log, OR re-open it.
                // Better UX: Just log it immediately.
                viewModel.logFood(spokenText)
                Toast.makeText(context, "Analyzing: $spokenText", Toast.LENGTH_SHORT).show()
                showLogDialog = false
            }
        }
    }

    if (showLogDialog) {
        VoiceLogDialog(
            onDismiss = { showLogDialog = false },
            onConfirm = { query ->
                viewModel.logFood(query)
                showLogDialog = false
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                "Nutrition Guide",
                style = MaterialTheme.typography.headlineLarge
            )

            // 1. TARGETS SECTION
            when (val state = uiState) {
                is NutritionUiState.Loading -> Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is NutritionUiState.Empty -> EmptyNutritionCard(onGenerateClick = { viewModel.generateNutrition() })
                is NutritionUiState.Success -> NutritionDetailCard(state.plan)
                is NutritionUiState.Error -> {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Setup Required", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text("Please create a Workout Plan first so we can calculate your caloric needs.", color = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { viewModel.generateNutrition() }) { Text("Retry") }
                        }
                    }
                }
            }

            // 2. LOGGING SECTION
            Text(
                "Today's Logs",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            // "Voice" Button
            Button(
                onClick = { showLogDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Icon(Icons.Default.Mic, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Food (AI Voice)")
            }

            if (isLogging) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("AI analyzing your meal...", style = MaterialTheme.typography.bodySmall)
            }

            if (foodLogs.isEmpty()) {
                Text("No food logged yet today.", color = Color.Gray)
            } else {
                foodLogs.forEach { log ->
                    FoodLogCard(log)
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun VoiceLogDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onMicClick: () -> Unit,
    isLoading: Boolean
) {
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
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("What did you eat?") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onMicClick,
                        modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Speak", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }, enabled = text.isNotBlank() && !isLoading) {
                Text("Log It")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
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
                Text("${log.totalProtein}g Pro", color = Color.Gray)
                Text("${log.totalCarbs}g Carb", color = Color.Gray)
                Text("${log.totalFats}g Fat", color = Color.Gray)
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
            Button(onClick = onGenerateClick, modifier = Modifier.fillMaxWidth()) {
                Text("Generate Plan")
            }
        }
    }
}

@Composable
fun NutritionDetailCard(plan: NutritionPlan) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Daily Targets", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            MacroRow("Calories", plan.calories, MaterialTheme.colorScheme.primary)
            MacroRow("Protein", plan.protein, Color(0xFFE57373))
            MacroRow("Carbs", plan.carbs, Color(0xFF64B5F6))
            MacroRow("Fats", plan.fats, Color(0xFFFFD54F))

            Spacer(modifier = Modifier.height(24.dp))

            // NEW: Explanation Section
            Text("Why this plan?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = plan.explanation.ifBlank { "Based on your biometrics and calculated activity level." },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            Text("Timing Strategy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(plan.timing, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun MacroRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}