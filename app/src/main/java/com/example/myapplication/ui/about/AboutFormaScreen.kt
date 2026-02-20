package com.example.myapplication.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
            title = "Iterative Mesocycles",
            description = "Continuous macrocycle planning that auto-adapts based on performance.",
            inDepthDescription = "Instead of rigid 4-week templates, Forma connects your workouts into continuous macrocycles. At the end of each block, the AI analyzes the raw tonnage you moved and your RPE logs, automatically generating your next training phase with calculated progressive overload and exercise variation.",
            icon = Icons.Default.Autorenew
        ),
        AppFeature(
            title = "Dynamic Readiness Score",
            description = "Auto-regulation driven by your lifting volume and biometric sleep data.",
            inDepthDescription = "Forma doesn't just guess how you feel. It calculates an Acute:Chronic Workload Ratio (ACWR) based on your workout history and combines it with sleep data pulled securely from Android Health Connect. If your Readiness Score drops, the AI Coach automatically scales back your target weight and volume to prevent injury.",
            icon = Icons.Default.BatteryChargingFull
        ),
        AppFeature(
            title = "AI Vision Spotter",
            description = "On-device computer vision to track barbell paths and count reps.",
            inDepthDescription = "Using Google's ML Kit Pose Detection, Forma acts as your digital spotter. Point your camera at your squat rack, and the app processes your skeletal movement in real-time. It automatically counts your reps and triggers a natural Voice Coach to give you instant form corrections.",
            icon = Icons.Default.Videocam
        ),
        AppFeature(
            title = "Muscle Gamification",
            description = "Turn your lifting volume into XP to level up your digital body.",
            inDepthDescription = "Every pound you lift is tracked. Hit your chest hard, and the muscle avatar glows red to indicate fatigue (48-72 hours). Once recovered, that volume is converted into lifetime Hypertrophy XP, leveling up your muscle groups from bronze, to silver, to gold.",
            icon = Icons.Default.AccessibilityNew
        ),
        AppFeature(
            title = "Daily Intelligence Briefing",
            description = "Curated fitness news synthesized into a daily 3-sentence brief.",
            inDepthDescription = "Forma's Knowledge Hub crawls the latest articles and videos based on your specific athletic interests (e.g., CrossFit, Hyrox). Claude AI then cross-references this content with your scheduled workout for the day to generate a highly personalized, actionable intelligence briefing.",
            icon = Icons.Default.Newspaper
        ),
        AppFeature(
            title = "Community Social Heat",
            description = "Real-time upvotes and trending community workout picks.",
            inDepthDescription = "Workout in a digital community. Forma uses Firebase Firestore to track trending articles and massive PRs within the user base. You can upvote 'Community Picks' directly from your dashboard to share the motivation.",
            icon = Icons.Default.Whatshot
        ),
        AppFeature(
            title = "Zero-Knowledge Privacy",
            description = "Enterprise-grade AWS security that keeps your personal data on-device.",
            inDepthDescription = "Your identifiable data never trains public models. Forma uses a prompt sanitizer to strip Personally Identifiable Information (PII) before utilizing AWS Bedrock for AI generation. Video form-checking is completely on-device, meaning your gym videos are never sent to the cloud.",
            icon = Icons.Default.GppGood
        )
    )

    // Feature Detail Dialog
    if (selectedFeature != null) {
        AlertDialog(
            onDismissRequest = { selectedFeature = null },
            icon = {
                Icon(
                    imageVector = selectedFeature!!.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = selectedFeature!!.title,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            text = {
                Text(
                    text = selectedFeature!!.inDepthDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { selectedFeature = null }) {
                    Text("Got it")
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface
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
                            "Version 2.0.0 (Proxima)",
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