// File: app/src/main/java/com/example/myapplication/ui/home/HomeViewModel.kt
package com.example.myapplication.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.DailyWorkoutEntity
import com.example.myapplication.data.repository.PlanRepository
import com.example.myapplication.data.remote.BedrockClient
import com.example.myapplication.data.repository.ContentRepository
import com.example.myapplication.data.local.ContentSourceEntity
import com.example.myapplication.data.local.UserPreferencesRepository
import com.example.myapplication.data.local.WorkoutDao
import com.example.myapplication.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: PlanRepository,
    private val bedrockClient: BedrockClient,
    private val workoutDao: WorkoutDao,
    private val contentRepository: ContentRepository,
    private val userPrefs: UserPreferencesRepository
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<String>()
    val navigationEvents = _navigationEvents.asSharedFlow()

    // Briefing State
    private val _knowledgeBriefing = MutableStateFlow("")
    val knowledgeBriefing: StateFlow<String> = _knowledgeBriefing.asStateFlow()

    private val _isBriefingLoading = MutableStateFlow(false)
    val isBriefingLoading: StateFlow<Boolean> = _isBriefingLoading.asStateFlow()

    val userName: StateFlow<String> = userPrefs.userName.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "User"
    )

    val workoutDates: StateFlow<List<LocalDate>> = repository.getAllWorkoutDates()
        .map { dates ->
            dates.map { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val dailyWorkout: StateFlow<DailyWorkoutEntity?> = _selectedDate
        .flatMapLatest { date ->
            val epochMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            repository.getWorkoutForDate(epochMillis)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val subscriptions = workoutDao.getAllSubscriptions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _rawSubscribedContent = workoutDao.getSubscribedContent()
    
    val filteredContent: StateFlow<List<ContentSourceEntity>> = combine(
        _rawSubscribedContent,
        _selectedCategory
    ) { content, category ->
        val filtered = when (category) {
            "Articles" -> content.filter { it.mediaType == "Article" }
            "Videos" -> content.filter { it.mediaType == "Video" }
            "Social" -> content.filter { it.mediaType == "Social" }
            else -> content
        }
        filtered.sortedWith(compareByDescending<ContentSourceEntity> { it.dateFetched }.thenByDescending { it.sourceId })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        observeContextForIntel()
        observeContentForBriefing()
    }

    private fun observeContextForIntel() {
        viewModelScope.launch {
            subscriptions.collectLatest { subs ->
                if (subs.isNotEmpty()) {
                    subs.forEach { sub ->
                        try {
                            contentRepository.fetchRealContent(sub.tagName)
                            
                            // Mock "Social" content logic remains here if needed
                            if (sub.tagName.contains("Hyrox", true) || sub.tagName.contains("CrossFit", true) || sub.tagName.contains("Athlete", true)) {
                                val cleanTag = sub.tagName.replace(" ", "").lowercase()
                                workoutDao.insertContentSource(
                                    ContentSourceEntity(
                                        title = "Recent updates for #${sub.tagName}",
                                        summary = "Tap to view the latest training and competition posts for ${sub.tagName} on Instagram.",
                                        url = "https://www.instagram.com/explore/tags/$cleanTag/",
                                        mediaType = "Social",
                                        sportTag = sub.tagName,
                                        dateFetched = 1700000000000L
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Log.e("HomeViewModel", "Error fetching content for ${sub.tagName}: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    private fun observeContentForBriefing() {
        viewModelScope.launch {
            combine(
                subscriptions,
                dailyWorkout,
                _rawSubscribedContent
            ) { subs, workout, content ->
                Triple(subs, workout, content)
            }.collectLatest { (subs, workout, content) ->
                if (subs.isEmpty() || content.isEmpty()) {
                    _knowledgeBriefing.value = ""
                    return@collectLatest
                }

                val interestNames = subs.map { it.tagName }
                val workoutTitle = workout?.title
                
                val cached = contentRepository.getCachedBriefing(interestNames, workoutTitle)
                if (cached != null) {
                    _knowledgeBriefing.value = cached
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
        if (_isBriefingLoading.value) return
        
        viewModelScope.launch {
            _isBriefingLoading.value = true
            val briefing = bedrockClient.generateKnowledgeBriefing(content.take(10), workoutTitle)
            contentRepository.updateBriefingCache(briefing, interests, workoutTitle)
            _knowledgeBriefing.value = briefing
            _isBriefingLoading.value = false
        }
    }

    fun forceRefreshBriefing() {
        viewModelScope.launch {
            val content = _rawSubscribedContent.first()
            generateKnowledgeBriefing(content, subscriptions.value.map { it.tagName }, dailyWorkout.value?.title)
        }
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    fun updateSelectedDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun generateRecoverySession(type: String) {
        viewModelScope.launch {
            _isGenerating.value = true
            try {
                var activePlan = repository.getActivePlan() ?: repository.getLatestPlan()
                if (activePlan == null) {
                    _isGenerating.value = false
                    return@launch
                }

                val history = repository.getWorkoutHistory().take(1).first()
                val availableExercises = repository.getAllExercises().take(1).first()
                val dateMillis = _selectedDate.value.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

                val aiResponse = if (type == "Stretching") {
                    bedrockClient.generateStretchingFlow(activePlan.goal, history, availableExercises)
                } else {
                    bedrockClient.generateAccessoryWorkout(activePlan.goal, history, availableExercises)
                }

                if (aiResponse.schedule.isNotEmpty()) {
                    val generatedDay = aiResponse.schedule.first()
                    val workoutId = repository.saveSingleDayWorkout(
                        planId = activePlan.planId,
                        date = dateMillis,
                        title = generatedDay.title,
                        exercises = generatedDay.exercises
                    )

                    val route = if (type == "Stretching") {
                        Screen.StretchingSession.createRoute(workoutId)
                    } else {
                        Screen.ActiveWorkout.createRoute(workoutId)
                    }
                    _navigationEvents.emit(route)
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Generation error: ${e.message}")
            } finally {
                _isGenerating.value = false
            }
        }
    }
}