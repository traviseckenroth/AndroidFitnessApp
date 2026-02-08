// app/src/main/java/com/example/myapplication/ui/settings/GymSettingsScreen.kt
package com.example.myapplication.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val gymType by viewModel.gymType.collectAsState(initial = "Commercial")
    val excludedEquipment by viewModel.excludedEquipment.collectAsState(initial = emptySet())

    // Standard list of equipment (This should ideally match your ExerciseEntity types)
    val allEquipment = listOf(
        "Barbell", "Dumbbell", "Kettlebell", "Cable", "Machine",
        "Smith Machine", "Pull Up Bar", "Dip Station", "Bench",
        "Squat Rack", "Leg Press", "EZ Bar"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gym Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Gym Type Selection
            item {
                Text("Gym Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                GymTypeCard(
                    selected = gymType == "Commercial",
                    title = "Commercial Gym",
                    subtitle = "Access to all standard equipment (Machines, Cables, Free Weights).",
                    onClick = { viewModel.setGymType("Commercial") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                GymTypeCard(
                    selected = gymType == "Home Gym",
                    title = "Home Gym",
                    subtitle = "Typically Power Rack, Barbell, Dumbbells, and Bench.",
                    onClick = { viewModel.setGymType("Home Gym") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                GymTypeCard(
                    selected = gymType == "Limited",
                    title = "Limited / Hotel",
                    subtitle = "Dumbbells and Bodyweight only.",
                    onClick = { viewModel.setGymType("Limited") }
                )
            }

            item { Divider(modifier = Modifier.padding(vertical = 8.dp)) }

            // 2. Equipment Availability
            item {
                Text("Equipment Availability", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Uncheck items you do NOT have access to.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(allEquipment) { equipment ->
                val isAvailable = !excludedEquipment.contains(equipment)
                EquipmentToggleRow(
                    name = equipment,
                    isAvailable = isAvailable,
                    onToggle = { checked ->
                        viewModel.toggleEquipment(equipment, !checked) // if checked is true, we WANT it (so excluded is false)
                    }
                )
            }
        }
    }
}

@Composable
fun GymTypeCard(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun EquipmentToggleRow(name: String, isAvailable: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable { onToggle(!isAvailable) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = isAvailable, onCheckedChange = onToggle)
    }
}