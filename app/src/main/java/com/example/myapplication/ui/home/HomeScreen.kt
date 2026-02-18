// File: app/src/main/java/com/example/myapplication/ui/home/HomeScreen.kt
package com.example.myapplication.ui.home

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.data.local.DailyWorkoutEntity
import com.example.myapplication.data.local.ContentSourceEntity
import com.example.myapplication.data.local.UserSubscriptionEntity
import com.example.myapplication.data.remote.CommunityPick
import com.example.myapplication.ui.navigation.Screen
import com.example.myapplication.ui.theme.PrimaryIndigo
import com.example.myapplication.ui.theme.SecondaryIndigo
import com.example.myapplication.ui.theme.SuccessGreen
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val workout by viewModel.dailyWorkout.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val workoutDates by viewModel.workoutDates.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    
    val contentFeed by viewModel.filteredContent.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val subscriptions by viewModel.subscriptions.collectAsState()
    
    val briefing by viewModel.knowledgeBriefing.collectAsState()
    val isBriefingLoading by viewModel.isBriefingLoading.collectAsState()

    val communityPick by viewModel.communityPick.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val userName by viewModel.userName.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { route ->
            onNavigate(route)
        }
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearErrorMessage() },
            title = { Text("AI Plan Required") },
            text = { Text(errorMessage!!) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearErrorMessage() }) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                HeaderSection(userName = userName)
                Spacer(modifier = Modifier.height(8.dp))
                InfiniteScrollingCalendar(
                    initialDate = LocalDate.now(),
                    selectedDate = selectedDate,
                    workoutDates = workoutDates,
                    onDateSelected = { viewModel.updateSelectedDate(it) }
                )
            }

            item {
                Text("Today's Session", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (workout != null) {
                    WorkoutCard(workout = workout!!, onNavigate = onNavigate)
                } else {
                    RestDayRecoveryCard(
                        onGenerateStretching = { viewModel.generateRecoverySession("Stretching") },
                        onGenerateAccessory = { viewModel.generateRecoverySession("Accessory") },
                        isGenerating = isGenerating
                    )
                }
            }

            // Discovery Feed & Daily Briefing
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Discovery Feed",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            
                            KnowledgeCategorySelector(
                                selectedCategory = selectedCategory,
                                onCategorySelected = { viewModel.setCategory(it) }
                            )
                        }
                        Text(
                            text = "Manage topics and athletes in the Insights screen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Combined Briefing Card
                    if (subscriptions.isNotEmpty()) {
                        KnowledgeBriefingCard(
                            briefing = briefing,
                            isLoading = isBriefingLoading,
                            onRefresh = { viewModel.forceRefreshBriefing() },
                            subscriptions = subscriptions
                        )
                    }

                    // Discovery Feed Row
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Community Pick First
                        if (communityPick != null && selectedCategory == "All") {
                            item {
                                CommunityPickCard(
                                    pick = communityPick!!,
                                    onUpvote = { viewModel.upvoteCommunityPick() }
                                )
                            }
                        }

                        items(contentFeed, key = { it.sourceId }) { item ->
                            KnowledgeFeedCard(
                                item = item,
                                onClick = { onNavigate(Screen.ContentDiscovery.createRoute(item.sourceId)) },
                                onUpvote = { viewModel.upvoteContent(item) }
                            )
                        }
                    }

                    if (contentFeed.isEmpty() && communityPick == null) {
                        if (subscriptions.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No ${selectedCategory.lowercase()} found for your interests.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Follow some interests to see your intelligence feed.", 
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            item { QuickActionsSection(onNavigate = onNavigate) }
            
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun CommunityPickCard(pick: CommunityPick, onUpvote: () -> Unit) {
    Card(
        modifier = Modifier
            .width(240.dp)
            .height(140.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "COMMUNITY PICK",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                IconButton(onClick = onUpvote, modifier = Modifier.size(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${pick.upvotes}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ThumbUp, contentDescription = "Upvote", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = pick.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = pick.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun KnowledgeFeedCard(item: ContentSourceEntity, onClick: () -> Unit, onUpvote: () -> Unit) {
    Card(
        modifier = Modifier
            .width(240.dp)
            .height(140.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onClick() }
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = item.sportTag.uppercase(),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                val icon = when (item.mediaType) {
                    "Video" -> Icons.Default.PlayCircle
                    "Social" -> Icons.Default.Share
                    else -> Icons.AutoMirrored.Filled.Article
                }
                
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp).align(Alignment.End)
                )
            }

            IconButton(
                onClick = onUpvote,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ThumbUp,
                    contentDescription = "Upvote",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun KnowledgeBriefingCard(
    briefing: String,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    subscriptions: List<UserSubscriptionEntity>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Daily Briefing",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onRefresh() },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            AnimatedContent(targetState = briefing, label = "BriefingText") { text ->
                val displayText = when {
                    text.isNotEmpty() -> text
                    isLoading -> {
                        val tags = subscriptions.take(2).joinToString(", ") { it.tagName }
                        val more = if (subscriptions.size > 2) " and ${subscriptions.size - 2} more" else ""
                        "Analyzing latest intelligence for $tags$more..."
                    }
                    else -> "Personalizing your briefing..."
                }
                
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
fun KnowledgeCategorySelector(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    val categories = listOf("All", "Articles", "Videos", "Social")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        categories.forEach { category ->
            val isSelected = selectedCategory == category
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onCategorySelected(category) },
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Text(
                    text = category,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun RestDayRecoveryCard(
    onGenerateStretching: () -> Unit,
    onGenerateAccessory: () -> Unit,
    isGenerating: Boolean
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SelfImprovement, null, tint = PrimaryIndigo, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("REST & RECHARGE", style = MaterialTheme.typography.labelLarge, color = PrimaryIndigo)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("Your body grows while you rest. Use today to stay mobile.",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            Text(
                text = "Note: An AI generated plan is required to generate stretching and accessory workouts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isGenerating) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = PrimaryIndigo)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onGenerateStretching,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
                    ) {
                        Icon(Icons.Default.AutoMode, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("GENERATE STRETCHING FLOW")
                    }
                    OutlinedButton(
                        onClick = onGenerateAccessory,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, SecondaryIndigo)
                    ) {
                        Icon(Icons.Default.FitnessCenter, null, tint = SecondaryIndigo, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("GENERATE ACCESSORY WORK", color = SecondaryIndigo)
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionsSection(onNavigate: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickActionCard(
            title = "New Plan",
            icon = Icons.Default.AutoMode,
            color = PrimaryIndigo,
            modifier = Modifier.weight(1f),
            onClick = { onNavigate(Screen.GeneratePlan.route) }
        )

        QuickActionCard(
            title = "Free Lift",
            icon = Icons.Default.FitnessCenter,
            color = SecondaryIndigo,
            modifier = Modifier.weight(1f),
            onClick = { onNavigate(Screen.ManualPlan.route) }
        )

        QuickActionCard(
            title = "Log Food",
            icon = Icons.Default.Restaurant,
            color = SuccessGreen,
            modifier = Modifier.weight(1f),
            onClick = { onNavigate(Screen.Nutrition.route) }
        )
    }
}

@Composable
fun QuickActionCard(
    title: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(100.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun HeaderSection(userName: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Welcome back,",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = userName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(userName.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun WorkoutCard(workout: DailyWorkoutEntity, onNavigate: (String) -> Unit) {
    val isStretching = workout.title.contains("Recovery", ignoreCase = true) ||
            workout.title.contains("Stretching", ignoreCase = true)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(if (isStretching) "🧘" else "💪", fontSize = 24.sp)
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(workout.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (isStretching) "Mobility Session" else "Scheduled Session",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    if (isStretching) {
                        onNavigate(Screen.StretchingSession.createRoute(workout.workoutId))
                    } else {
                        onNavigate(Screen.ActiveWorkout.createRoute(workout.workoutId))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = if (isStretching) "START MOBILITY" else "START SESSION",
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}