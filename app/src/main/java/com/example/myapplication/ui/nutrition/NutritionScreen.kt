package com.example.myapplication.ui.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Bolt // Changed Icon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.data.NutritionPlan

@Composable
fun NutritionScreen(viewModel: NutritionViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("Nutrition Guide", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)

        when (val state = uiState) {
            is NutritionUiState.Loading -> {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is NutritionUiState.Empty -> {
                // STANDARD GENERATE UI
                EmptyNutritionCard(onGenerateClick = { viewModel.generateNutrition() })
            }
            is NutritionUiState.Success -> {
                // UNLOCKED CONTENT
                NutritionDetailCard(state.plan)
            }
            is NutritionUiState.Error -> {
                Text("Error: ${state.msg}", color = MaterialTheme.colorScheme.error)
                Button(onClick = { viewModel.generateNutrition() }) { Text("Retry") }
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
            Text(
                "Create a daily macro breakdown based on your active workout plan, body stats, and diet preferences.",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )

            Button(onClick = onGenerateClick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Restaurant, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate Plan")
            }
        }
    }
}

@Composable
fun NutritionDetailCard(plan: NutritionPlan) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Restaurant, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Daily Targets", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            MacroRow("Calories", plan.calories, MaterialTheme.colorScheme.primary)
            MacroRow("Protein", plan.protein, Color(0xFFE57373))
            MacroRow("Carbs", plan.carbs, Color(0xFF64B5F6))
            MacroRow("Fats", plan.fats, Color(0xFFFFD54F))

            Spacer(modifier = Modifier.height(24.dp))
            Text("Timing Strategy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(plan.timing, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
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