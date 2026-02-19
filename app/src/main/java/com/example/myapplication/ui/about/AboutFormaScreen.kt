package com.example.myapplication.ui.about

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class AppFeature(
    val title: String,
    val description: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutFormaScreen(onBack: () -> Unit) {
    val features = listOf(
        AppFeature(
            "Iterative Mesocycle",
            "Continuous planning that adapts your training block based on real-time performance and progressive overload.",
            Icons.Default.Autorenew
        ),
        AppFeature(
            "AI Plan Generation",
            "Custom-tailored workout plans based on your specific goals, available equipment, and weekly schedule.",
            Icons.Default.AutoAwesome
        ),
        AppFeature(
            "Local-First Intelligence",
            "Instant exercise alternatives and plate loading math without the wait for cloud AI.",
            Icons.Default.OfflineBolt
        ),
        AppFeature(
            "Discovery Feed",
            "Curated fitness intelligence including articles, videos, and social updates with smart offline caching.",
            Icons.Default.Newspaper
        ),
        AppFeature(
            "Health Connect Integration",
            "Biometric syncing for sleep, heart rate, and workout data to provide a holistic view of your recovery.",
            Icons.Default.Sync
        ),
        AppFeature(
            "AI Live Coaching",
            "Voice-activated interaction and real-time coaching cues to perfect your form during active sets.",
            Icons.Default.RecordVoiceOver
        ),
        AppFeature(
            "Performance Insights",
            "Detailed tracking of 1RM trends, muscle volume distribution, and lifetime training statistics.",
            Icons.Default.BarChart
        ),
        AppFeature(
            "Smart Nutrition Logging",
            "AI-powered food analysis that converts your text or voice descriptions into accurate macro tracking.",
            Icons.Default.Restaurant
        ),
        AppFeature(
            "Background Sync",
            "Reliable data updates and health integration handled by WorkManager while you stay focused on your day.",
            Icons.Default.CloudUpload
        ),
        AppFeature(
            "Secure Authentication",
            "Enterprise-grade security with AWS Cognito and transparent AI usage controls.",
            Icons.Default.Https
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About Forma", fontWeight = FontWeight.Bold) },
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
                    "Forma is your personal AI training partner, designed to automate the complex math of muscle growth and recovery.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
            }

            items(features) { feature ->
                FeatureItem(feature)
            }
            
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "Version 1.0.0",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun FeatureItem(feature: AppFeature) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = feature.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp).padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = feature.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = feature.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}