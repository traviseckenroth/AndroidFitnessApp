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
import com.example.myapplication.data.local.UserPreferencesRepository
import com.example.myapplication.data.repository.PlanRepository
import com.example.myapplication.data.repository.WorkoutExecutionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val planRepository: PlanRepository,
    private val executionRepository: WorkoutExecutionRepository,
    private val workoutDao: WorkoutDao,
    private val contentRepository: ContentRepository,
    private val bedrockClient: BedrockClient,
    private val userPrefs: UserPreferencesRepository
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
    }
        .flowOn(Dispatchers.Default) // <--- OFF-LOAD FILTERING TO BACKGROUND
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _recommendations = MutableStateFlow<List<String>>(emptyList())
    val recommendations: StateFlow<List<String>> = _recommendations.asStateFlow()

    // 4. Reactive Exercise Stats
    private val _selectedExerciseId = MutableStateFlow<Long?>(null)

    val selectedExerciseStats = combine(
        _selectedExerciseId,
        userPrefs.userWeight
    ) { id, bodyWeight ->
        id to bodyWeight
    }.flatMapLatest { (id, bodyWeight) ->
        if (id == null) flowOf(emptyList())
        else executionRepository.getCompletedWorkoutsForExercise(id).map { completed ->

            // Group sets by the start of the day to prevent zig-zagging.
            val groupedByDate = completed.groupBy {
                Instant.ofEpochMilli(it.completedWorkout.date)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }

            groupedByDate.map { (dateMillis, sets) ->
                val max1RMForDay = sets.maxOf { set ->
                    val isBodyweight = set.exercise.equipment?.contains("Bodyweight", ignoreCase = true) == true

                    val weight = if (isBodyweight) {
                        if (bodyWeight > 0) bodyWeight.toFloat() else 150f
                    } else {
                        set.completedWorkout.weight.toFloat()
                    }

                    val reps = set.completedWorkout.reps

                    // Epley Formula for 1RM
                    weight * (1 + (reps / 30.0f))
                }
                Pair(dateMillis, max1RMForDay)
            }.sortedBy { it.first }
        }.flowOn(Dispatchers.Default) // <--- OFF-LOAD HEAVY MATH/DATES TO BACKGROUND
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
        val thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS)

        viewModelScope.launch {
            combine(
                executionRepository.getCompletedWorkoutsRecent(thirtyDaysAgo),
                workoutDao.getCompletedWorkouts()
            ) { completedSets, completedSessions ->

                // 1. Current Block Muscle Focus (30 Days)
                val volumeByMuscle = completedSets
                    .filter { it.exercise.muscleGroup != null }
                    .groupBy { it.exercise.muscleGroup!! }
                    .mapValues { (_, workouts) ->
                        workouts.sumOf { it.completedWorkout.totalVolume.toDouble() }
                    }

                // 2. Weekly Tonnage (Progressive Overload Tracking)
                val workoutsByWeek = completedSets.groupBy {
                    val daysAgo = ChronoUnit.DAYS.between(
                        Instant.ofEpochMilli(it.completedWorkout.date).atZone(ZoneId.systemDefault()).toLocalDate(),
                        LocalDate.now()
                    )
                    when {
                        daysAgo <= 7 -> "This Week"
                        daysAgo <= 14 -> "Last Week"
                        daysAgo <= 21 -> "Week 3"
                        else -> "Week 4"
                    }
                }

                val tonnage = listOf("Week 4", "Week 3", "Last Week", "This Week").mapNotNull { week ->
                    val total = workoutsByWeek[week]?.sumOf { it.completedWorkout.totalVolume.toDouble() }
                    if (total != null && total > 0) week to total else null
                }

                // 3. Top Exercises for 1RM Quick Select
                val topExerciseIds = completedSets
                    .groupBy { it.exercise.exerciseId }
                    .entries.sortedByDescending { it.value.size }
                    .take(5)
                    .map { it.key }

                val topExercises = completedSets
                    .map { it.exercise }
                    .distinctBy { it.exerciseId }
                    .filter { it.exerciseId in topExerciseIds }

                // 4. Group Recent Activity by Workout Session
                val groupedHistory = completedSets.groupBy {
                    Instant.ofEpochMilli(it.completedWorkout.date).atZone(ZoneId.systemDefault()).toLocalDate()
                }.map { (localDate, sets) ->
                    val distinctExercises = sets.map { it.exercise.name }.distinct()
                    val sessionDate = localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

                    val matchedSession = completedSessions.find {
                        Instant.ofEpochMilli(it.scheduledDate).atZone(ZoneId.systemDefault()).toLocalDate() == localDate
                    }

                    RecentWorkoutSummary(
                        workoutId = matchedSession?.workoutId,
                        date = sessionDate,
                        topExercises = distinctExercises.take(3),
                        totalVolume = sets.sumOf { it.completedWorkout.totalVolume.toDouble() },
                        totalExercises = distinctExercises.size
                    )
                }.sortedByDescending { it.date }.take(5)

                // 5. Muscle Fatigue (Last 7 Days)
                val fatigueMap = mutableMapOf<String, Float>()
                val now = Instant.now().toEpochMilli()
                val sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS).toEpochMilli()

                completedSets.filter { it.completedWorkout.date >= sevenDaysAgo }.forEach { set ->
                    val daysAgo = (now - set.completedWorkout.date) / (1000 * 60 * 60 * 24).toFloat()
                    if (daysAgo in 0f..7f) {
                        val baseImpact = 0.08f * (1f - (daysAgo / 7f))
                        val primary = set.exercise.muscleGroup?.lowercase()
                        if (primary != null) {
                            fatigueMap[primary] = (fatigueMap[primary] ?: 0f) + baseImpact
                        }
                    }
                }
                val finalFatigueMap = fatigueMap.mapValues { it.value.coerceIn(0f, 1f) }

                Triple(Triple(volumeByMuscle, tonnage, topExercises), groupedHistory, finalFatigueMap)
            }
                .flowOn(Dispatchers.Default) // <--- CRITICAL FIX: MOVES HEAVY MATH OFF THE UI THREAD
                .collect { (tripleStats, groupedHistory, finalFatigueMap) ->
                    val (volumeByMuscle, tonnage, topExercises) = tripleStats
                    _uiState.update { state ->
                        val newSelected = if (state.selectedExercise == null) topExercises.firstOrNull() else state.selectedExercise
                        if (newSelected != null && _selectedExerciseId.value == null) {
                            _selectedExerciseId.value = newSelected.exerciseId
                        }

                        state.copy(
                            isLoading = false,
                            muscleVolumeDistribution = volumeByMuscle,
                            weeklyTonnage = tonnage,
                            topExercises = topExercises,
                            recentWorkouts = groupedHistory,
                            muscleFatigue = finalFatigueMap
                        )
                    }
                }
        }

        viewModelScope.launch {
            executionRepository.getLifetimeMuscleVolume()
                .flowOn(Dispatchers.Default)
                .collect { aggregations ->
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
                .flowOn(Dispatchers.Default)
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
            }
                .flowOn(Dispatchers.Default)
                .collectLatest { (interestTags, workoutTitle, contentSize) ->
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
