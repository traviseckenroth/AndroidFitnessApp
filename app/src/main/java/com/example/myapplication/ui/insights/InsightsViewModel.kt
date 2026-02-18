// File: app/src/main/java/com/example/myapplication/ui/insights/InsightsViewModel.kt
package com.example.myapplication.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.ContentSourceEntity
import com.example.myapplication.data.local.DailyWorkoutEntity
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.local.UserSubscriptionEntity
import com.example.myapplication.data.local.WorkoutDao
import com.example.myapplication.data.remote.BedrockClient
import com.example.myapplication.data.repository.ContentRepository
import com.example.myapplication.data.repository.PlanRepository
import com.example.myapplication.data.repository.WorkoutExecutionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val planRepository: PlanRepository,
    private val executionRepository: WorkoutExecutionRepository,
    private val workoutDao: WorkoutDao,
    private val contentRepository: ContentRepository,
    private val bedrockClient: BedrockClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    // 1. Active Subscriptions
    val subscriptions = workoutDao.getAllSubscriptions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 2. Today's Workout
    private val todayEpochMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val dailyWorkout: StateFlow<DailyWorkoutEntity?> = planRepository.getWorkoutForDate(todayEpochMillis)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 3. Feed of Content
    private val _rawSubscribedContent = workoutDao.getSubscribedContent()
    
    val filteredContent: StateFlow<List<ContentSourceEntity>> = combine(
        _rawSubscribedContent,
        _uiState
    ) { content, state ->
        when (state.selectedKnowledgeCategory) {
            "Articles" -> content.filter { it.mediaType == "Article" }
            "Videos" -> content.filter { it.mediaType == "Video" }
            else -> content
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _recommendations = MutableStateFlow<List<String>>(emptyList())
    val recommendations: StateFlow<List<String>> = _recommendations.asStateFlow()

    init {
        loadInitialData()
        observeSubscriptions()
        observeContentForBriefing()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            planRepository.getAllExercises().collect { exercises ->
                val currentSelected = _uiState.value.selectedExercise
                _uiState.value = _uiState.value.copy(
                    availableExercises = exercises,
                    selectedExercise = currentSelected ?: exercises.firstOrNull()
                )
                if (currentSelected == null && exercises.isNotEmpty()) {
                    selectExercise(exercises.first())
                }
            }
        }

        viewModelScope.launch {
            executionRepository.getAllCompletedWorkouts().collect { completedWorkouts ->
                val volumeByMuscle = completedWorkouts
                    .filter { it.exercise.muscleGroup != null }
                    .groupBy { it.exercise.muscleGroup!! }
                    .mapValues { (_, workouts) ->
                        workouts.sumOf { it.completedWorkout.totalVolume.toDouble() }
                    }

                val sortedHistory = completedWorkouts
                    .sortedByDescending { it.completedWorkout.date }
                    .take(10)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    muscleVolumeDistribution = volumeByMuscle,
                    recentWorkouts = sortedHistory
                )
            }
        }
    }

    private fun observeSubscriptions() {
        viewModelScope.launch {
            subscriptions.collectLatest { subs ->
                val names = subs.map { it.tagName }
                
                // 1. Fetch Recommendations (Pass names even if empty to get defaults)
                val suggestions = bedrockClient.getInterestRecommendations(names)
                _recommendations.value = suggestions

                if (subs.isNotEmpty()) {
                    // 2. Fetch fresh content for each subscription to populate the feed
                    subs.forEach { sub ->
                        contentRepository.fetchRealContent(sub.tagName)
                    }
                }
            }
        }
    }

    private fun observeContentForBriefing() {
        viewModelScope.launch {
            // Combine subscriptions and daily workout to trigger briefing
            combine(
                subscriptions,
                dailyWorkout,
                _rawSubscribedContent
            ) { subs, workout, content ->
                Triple(subs, workout, content)
            }.collectLatest { (subs, workout, content) ->
                if (subs.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        knowledgeBriefing = "Follow your favorite sports or athletes to get a daily intelligence briefing."
                    )
                    return@collectLatest
                }

                if (content.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        knowledgeBriefing = "No recent intelligence found for your interests. Try adding more specific topics."
                    )
                    return@collectLatest
                }

                val interestNames = subs.map { it.tagName }
                val workoutTitle = workout?.title
                
                // Check cache in Repository to prevent frequent refreshes
                val cached = contentRepository.getCachedBriefing(interestNames, workoutTitle)
                if (cached != null) {
                    _uiState.value = _uiState.value.copy(knowledgeBriefing = cached)
                } else {
                    generateKnowledgeBriefing(content, interestNames, workoutTitle)
                }
            }
        }
    }

    private fun generateKnowledgeBriefing(
        content: List<ContentSourceEntity>,
        interests: List<String>,
        workoutTitle: String?
    ) {
        if (_uiState.value.isBriefingLoading) return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBriefingLoading = true)
            // Limit to 10 items for the briefing
            val briefing = bedrockClient.generateKnowledgeBriefing(content.take(10), workoutTitle)
            
            // Save to cache in the Singleton Repository
            contentRepository.updateBriefingCache(briefing, interests, workoutTitle)
            
            _uiState.value = _uiState.value.copy(
                knowledgeBriefing = briefing,
                isBriefingLoading = false
            )
        }
    }

    fun forceRefreshBriefing() {
        val subs = subscriptions.value
        val workout = dailyWorkout.value

        viewModelScope.launch {
             _rawSubscribedContent.take(1).collect { content ->
                 generateKnowledgeBriefing(content, subs.map { it.tagName }, workout?.title)
             }
        }
    }

    fun setKnowledgeCategory(category: String) {
        _uiState.value = _uiState.value.copy(selectedKnowledgeCategory = category)
    }

    fun addInterest(name: String) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                workoutDao.insertSubscription(UserSubscriptionEntity(name.trim(), "Custom"))
            }
        }
    }

    fun selectExercise(exercise: ExerciseEntity) {
        _uiState.value = _uiState.value.copy(selectedExercise = exercise)
        viewModelScope.launch {
            executionRepository.getCompletedWorkoutsForExercise(exercise.exerciseId).collect { completed ->
                val oneRepMaxHistory = completed.sortedBy { it.completedWorkout.date }.map {
                    val estimated1RM = it.completedWorkout.totalVolume * (1 + (it.completedWorkout.totalReps / 30.0f))
                    Pair(it.completedWorkout.date, estimated1RM.toFloat())
                }
                _uiState.value = _uiState.value.copy(oneRepMaxHistory = oneRepMaxHistory)
            }
        }
    }

    fun toggleSubscription(tagName: String, type: String) {
        viewModelScope.launch {
            val current = subscriptions.value.find { it.tagName == tagName }
            if (current != null) {
                workoutDao.deleteSubscription(tagName)
            } else {
                workoutDao.insertSubscription(UserSubscriptionEntity(tagName, type))
            }
        }
    }
}