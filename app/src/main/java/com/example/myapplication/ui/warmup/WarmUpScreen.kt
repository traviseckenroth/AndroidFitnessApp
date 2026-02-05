// app/src/main/java/com/example/myapplication/ui/warmup/WarmUpScreen.kt

package com.example.myapplication.ui.warmup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.PrimaryIndigo
import com.example.myapplication.ui.theme.SecondaryIndigo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarmUpScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            // Using CenterAlignedTopAppBar for the balanced "Studio" look
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Warm Up Protocols",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 24.dp) // Increased horizontal margin
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(32.dp) // Generous vertical rhythm (was 24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "PRE-SESSION PRIMERS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // OPTION 1: HYPERTROPHY (Monochromatic: Indigo)
            WarmUpCard(
                title = "Hypertrophy Primer",
                subtitle = "For Bodybuilding & Volume Days",
                accentColor = PrimaryIndigo,
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

            // OPTION 2: STRENGTH (Monochromatic: Secondary Indigo)
            WarmUpCard(
                title = "CNS Potentiation",
                subtitle = "For Heavy Strength & Power Days",
                accentColor = SecondaryIndigo,
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

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun WarmUpCard(
    title: String,
    subtitle: String,
    accentColor: Color,
    icon: ImageVector,
    goal: String,
    steps: List<String>
) {
    ElevatedCard(
        shape = MaterialTheme.shapes.medium, // Uses the global 16.dp shape
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icon Container: Uses brand color with low opacity for a clean look
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accentColor.copy(alpha = 0.1f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = accentColor)
                    }
                }

                Spacer(modifier = Modifier.width(20.dp))

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Text(
                "GOAL:",
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = goal,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                "ROUTINE:",
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            steps.forEachIndexed { index, step ->
                Row(modifier = Modifier.padding(vertical = 6.dp)) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.width(24.dp)
                    )
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}