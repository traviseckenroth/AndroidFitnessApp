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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.first

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
        _uiState.map { it.selectedKnowledgeCategory }.distinctUntilChanged()
    ) { content, category ->
        when (category) {
            "Articles" -> content.filter { it.mediaType == "Article" }
            "Videos" -> content.filter { it.mediaType == "Video" }
            else -> content
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _recommendations = MutableStateFlow<List<String>>(emptyList())
    val recommendations: StateFlow<List<String>> = _recommendations.asStateFlow()

    // 4. Reactive Exercise Stats
    private val _selectedExerciseId = MutableStateFlow<Long?>(null)
    
    val selectedExerciseStats = _selectedExerciseId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else executionRepository.getCompletedWorkoutsForExercise(id)
    }.map { completed ->
        completed.sortedBy { it.completedWorkout.date }.map {
            // FIX: Corrected Epley Formula for 1RM: Weight * (1 + Reps/30.0)
            val weight = it.completedWorkout.weight.toFloat()
            val reps = it.completedWorkout.reps
            val estimated1RM = weight * (1 + (reps / 30.0f))
            Pair(it.completedWorkout.date, estimated1RM)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadExercises()
        observeWorkoutHistory()
        observeSubscriptions()
        observeContentForBriefing()
        
        // Sync exercise stats into UI State
        viewModelScope.launch {
            selectedExerciseStats.collect { history ->
                _uiState.update { it.copy(oneRepMaxHistory = history) }
            }
        }
    }

    private fun loadExercises() {
        viewModelScope.launch {
            planRepository.getAllExercises().collectLatest { exercises ->
                _uiState.update { state ->
                    val newSelected = if (state.selectedExercise == null) exercises.firstOrNull() else state.selectedExercise
                    if (newSelected != null && _selectedExerciseId.value == null) {
                        _selectedExerciseId.value = newSelected.exerciseId
                    }
                    state.copy(
                        availableExercises = exercises,
                        selectedExercise = newSelected
                    )
                }
            }
        }
    }

    private fun observeWorkoutHistory() {
        val sixMonthsAgo = Instant.now().minus(180, ChronoUnit.DAYS)

        viewModelScope.launch {
            // 1. Observe Recent Workouts (last 6 months) for graphs and lists
            executionRepository.getCompletedWorkoutsRecent(sixMonthsAgo).collect { completedWorkouts ->
                val volumeByMuscle = completedWorkouts
                    .filter { it.exercise.muscleGroup != null }
                    .groupBy { it.exercise.muscleGroup!! }
                    .mapValues { (_, workouts) ->
                        workouts.sumOf { it.completedWorkout.totalVolume.toDouble() }
                    }

                val sortedHistory = completedWorkouts
                    .sortedByDescending { it.completedWorkout.date }
                    .take(10)

                _uiState.update { it.copy(
                    isLoading = false,
                    muscleVolumeDistribution = volumeByMuscle,
                    recentWorkouts = sortedHistory
                ) }
            }
        }

        viewModelScope.launch {
            // 2. Observe Lifetime Stats via SQL aggregation
            executionRepository.getLifetimeMuscleVolume().collect { aggregations ->
                val lifetimeMap = aggregations.associate { it.muscleGroup to it.totalVolume }
                _uiState.update { it.copy(lifetimeMuscleVolume = lifetimeMap) }
            }
        }
    }

    private fun observeSubscriptions() {
        viewModelScope.launch {
            subscriptions
                .map { it.map { s -> s.tagName }.toSet() }
                .distinctUntilChanged()
                .collectLatest { tagSet ->
                    val names = tagSet.toList()
                    val suggestions = bedrockClient.getInterestRecommendations(names)
                    _recommendations.value = suggestions

                    if (tagSet.isNotEmpty()) {
                        tagSet.forEach { tagName ->
                            contentRepository.fetchRealContent(tagName)
                        }
                    }
                }
        }
    }

    private fun observeContentForBriefing() {
        viewModelScope.launch {
            combine(
                subscriptions.map { it.map { s -> s.tagName }.toSet() }.distinctUntilChanged(),
                dailyWorkout.map { it?.title }.distinctUntilChanged(),
                _rawSubscribedContent.map { it.size }.distinctUntilChanged()
            ) { interestTags, workoutTitle, contentSize ->
                Triple(interestTags, workoutTitle, contentSize)
            }.collectLatest { (interestTags, workoutTitle, contentSize) ->
                if (interestTags.isEmpty()) {
                    _uiState.update { it.copy(knowledgeBriefing = "Follow your favorite sports or athletes to get a daily intelligence briefing.") }
                    return@collectLatest
                }

                val interestList = interestTags.toList()
                val cached = contentRepository.getCachedBriefing(interestList, workoutTitle)
                
                if (cached != null) {
                    _uiState.update { it.copy(knowledgeBriefing = cached) }
                } else {
                    val currentContent = _rawSubscribedContent.first()
                    if (currentContent.isNotEmpty()) {
                        generateKnowledgeBriefing(currentContent, interestList, workoutTitle)
                    }
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
            _uiState.update { it.copy(isBriefingLoading = true) }
            val briefing = bedrockClient.generateKnowledgeBriefing(content.take(10), workoutTitle)
            contentRepository.updateBriefingCache(briefing, interests, workoutTitle)
            _uiState.update { it.copy(knowledgeBriefing = briefing, isBriefingLoading = false) }
        }
    }

    fun forceRefreshBriefing() {
        viewModelScope.launch {
             val content = _rawSubscribedContent.first()
             generateKnowledgeBriefing(content, subscriptions.value.map { it.tagName }, dailyWorkout.value?.title)
        }
    }

    fun setKnowledgeCategory(category: String) {
        _uiState.update { it.copy(selectedKnowledgeCategory = category) }
    }

    fun addInterest(name: String) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                workoutDao.insertSubscription(UserSubscriptionEntity(name.trim(), "Custom"))
            }
        }
    }

    fun selectExercise(exercise: ExerciseEntity) {
        _selectedExerciseId.value = exercise.exerciseId
        _uiState.update { it.copy(selectedExercise = exercise) }
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
