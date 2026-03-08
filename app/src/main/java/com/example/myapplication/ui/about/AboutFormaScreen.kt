package com.example.myapplication.ui.about

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class AppFeature(
    val title: String,
    val description: String,
    val inDepthDescription: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutFormaScreen(onBack: () -> Unit) {
    var selectedFeature by remember { mutableStateOf<AppFeature?>(null) }

    val features = listOf(
        AppFeature(
            title = "Dynamic Autoregulation",
            description = "Real-time weight adjustments based on your set-by-set performance.",
            inDepthDescription = "If you miss reps or hit a maximum RPE early in your workout, Forma automatically recalculates the optimal weight for your remaining sets. This prevents overtraining, adapts to your daily strength levels, and ensures you stay within the optimal hypertrophy or strength stimulus range.",
            icon = Icons.Default.Tune
        ),
        AppFeature(
            title = "Live Audio Coach",
            description = "Real-time, hands-free coaching using premium AI voice models.",
            inDepthDescription = "Put your phone in your pocket and let Forma guide you. Powered by advanced TTS models, your coach dictates your next exercise, target weight, and rests. It seamlessly integrates with Pocket Mode, allowing you to stay completely focused on the lift without looking at your screen.",
            icon = Icons.Default.RecordVoiceOver
        ),
        AppFeature(
            title = "Intelligent Nutrition",
            description = "Macro optimization aligned directly with your active training cycle.",
            inDepthDescription = "Forma dynamically adjusts your daily caloric and macro targets based on your specific goal pace (e.g., Slow Cut, Recomp, Bulk). You can log food effortlessly using natural language voice commands, and the AI instantly parses the nutritional breakdown.",
            icon = Icons.Default.Restaurant
        ),
        AppFeature(
            title = "Iterative Mesocycles",
            description = "Continuous macrocycle planning that auto-adapts based on performance.",
            inDepthDescription = "Instead of rigid 4-week templates, Forma connects your workouts into continuous macrocycles. At the end of each block, the AI analyzes the raw tonnage you moved and your RPE logs, automatically generating your next training phase with calculated progressive overload and exercise variation.",
            icon = Icons.Default.Autorenew
        ),
        AppFeature(
            title = "Advanced Performance Insights",
            description = "Visualize your 1RM trends, weekly tonnage, and muscle fatigue.",
            inDepthDescription = "Forma turns your raw lifting data into actionable intelligence. Track your estimated 1-Rep Max across any exercise, monitor your Mesocycle volume progression, and view a visual heatmap of your muscular fatigue to optimize your recovery days.",
            icon = Icons.Default.Analytics
        ),
        AppFeature(
            title = "Bio-Sync via Health Connect",
            description = "Auto-regulation driven by your sleep and biometric data.",
            inDepthDescription = "Forma calculates your Acute:Chronic Workload Ratio (ACWR) and combines it with sleep and heart rate data pulled securely from Android Health Connect. If your Readiness Score drops due to poor recovery, the AI Coach automatically scales back your target volume to prevent injury.",
            icon = Icons.Default.Favorite
        ),
        AppFeature(
            title = "Type-Safe Architecture",
            description = "Crash-proof navigation and seamless full-screen interactions.",
            inDepthDescription = "Built on Jetpack Compose Navigation 2.8.0, Forma uses Kotlin Serialization to pass complex data between screens safely. This ensures a fluid, native-feeling experience whether you are swapping exercises mid-workout or generating a new training block.",
            icon = Icons.Default.Architecture
        ),
        AppFeature(
            title = "Zero-Knowledge Privacy",
            description = "Enterprise-grade security that keeps your personal data on-device.",
            inDepthDescription = "Your identifiable data never trains public models. Forma uses a prompt sanitizer to strip Personally Identifiable Information (PII) before utilizing local and secure cloud models for AI generation. Your health and fitness journey belongs strictly to you.",
            icon = Icons.Default.GppGood
        )
    )

// Feature Detail Dialog
    val currentFeature = selectedFeature
    if (currentFeature != null) {
        FeatureDetailDialog(
            feature = currentFeature,
            onDismiss = { selectedFeature = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About Forma AI", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Forma AI is an elite, autonomous training system. We utilize Generative AI and Biometric data to replicate the exact programming decisions of a professional strength coach.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(features) { feature ->
                FeatureItem(
                    feature = feature,
                    onClick = { selectedFeature = feature }
                )
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Forma AI System",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Version 2.5.0 (Titan)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// FIX: Extracted dialog to a separate composable to resolve "assigned but never read" warnings.
@Composable
fun FeatureDetailDialog(
    feature: AppFeature,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = feature.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = feature.title,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = feature.inDepthDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it")
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun FeatureItem(feature: AppFeature, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = feature.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = feature.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = feature.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "More Info",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}