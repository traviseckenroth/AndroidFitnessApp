package com.example.myapplication.ui.warmup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarmUpScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Warm Up Protocols") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // OPTION 1: HYPERTROPHY
            WarmUpCard(
                title = "Hypertrophy Primer",
                subtitle = "For Bodybuilding & Volume Days",
                color = Color(0xFF4CAF50), // Green
                icon = Icons.Default.FitnessCenter,
                goal = "Raise core temp & lubricate joints.",
                steps = listOf(
                    "5-10 mins Low Intensity Cardio (Bike/Elliptical)",
                    "Arm Circles (15 reps forward/back)",
                    "Leg Swings (15 reps side/front)",
                    "Torso Twists (20 reps)",
                    "World's Greatest Stretch (5 per side)"
                )
            )

            // OPTION 2: STRENGTH
            WarmUpCard(
                title = "CNS Potentiation",
                subtitle = "For Heavy Strength & Power Days",
                color = Color(0xFFF44336), // Red
                icon = Icons.Default.Bolt,
                goal = "Wake up the Central Nervous System (CNS).",
                steps = listOf(
                    "5 mins General Cardio (Brisk Walk)",
                    "Dynamic Stretching (same as above)",
                    "3-5 Box Jumps (Max Height) OR",
                    "3-5 Medicine Ball Slams (Max Power)",
                    "Rest 2 mins before first heavy lift"
                )
            )
        }
    }
}

@Composable
fun WarmUpCard(
    title: String,
    subtitle: String,
    color: Color,
    icon: ImageVector,
    goal: String,
    steps: List<String>
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(color.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(subtitle, fontSize = 14.sp, color = Color.Gray)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Text("GOAL:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
            Text(goal, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp))

            Text("ROUTINE:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
            steps.forEachIndexed { index, step ->
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("${index + 1}.", fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                    Text(step)
                }
            }
        }
    }
}