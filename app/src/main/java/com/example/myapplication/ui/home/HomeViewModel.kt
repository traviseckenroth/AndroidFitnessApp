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
import com.example.myapplication.data.repository.CommunityRepository
import com.example.myapplication.data.remote.CommunityPick
import com.example.myapplication.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import java.net.URLEncoder

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: PlanRepository,
    private val bedrockClient: BedrockClient,
    private val workoutDao: WorkoutDao,
    private val contentRepository: ContentRepository,
    private val communityRepository: CommunityRepository,
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

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

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

    val communityPick: StateFlow<CommunityPick?> = communityRepository.getTopCommunityPick()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        observeContextForIntel()
        observeContentForBriefing()
    }

    private fun observeContextForIntel() {
        viewModelScope.launch {
            subscriptions
                .map { it.map { s -> s.tagName }.toSet() }
                .distinctUntilChanged()
                .collectLatest { tagSet ->
                    if (tagSet.isNotEmpty()) {
                        tagSet.forEach { tagName ->
                            try {
                                contentRepository.fetchRealContent(tagName)
                                
                                // --- Expanded Social Media Sources ---
                                val cleanTag = tagName.replace(" ", "").lowercase()
                                val encodedTag = URLEncoder.encode(tagName, "UTF-8")

                                // 1. Instagram
                                workoutDao.insertContentSource(
                                    ContentSourceEntity(
                                        title = "Instagram: #$tagName",
                                        summary = "Latest trending posts and training reels for $tagName.",
                                        url = "https://www.instagram.com/explore/tags/$cleanTag/",
                                        mediaType = "Social",
                                        sportTag = tagName,
                                        dateFetched = System.currentTimeMillis()
                                    )
                                )

                                // 2. Reddit Community
                                val redditSub = if (tagName.contains("Hyrox", true)) "hyrox" else if (tagName.contains("CrossFit", true)) "crossfit" else "fitness"
                                workoutDao.insertContentSource(
                                    ContentSourceEntity(
                                        title = "Reddit: r/$redditSub",
                                        summary = "Join the community discussion and see what's trending in $tagName.",
                                        url = "https://www.reddit.com/r/$redditSub/new/",
                                        mediaType = "Social",
                                        sportTag = tagName,
                                        dateFetched = System.currentTimeMillis()
                                    )
                                )

                                // 3. YouTube Training Clips
                                workoutDao.insertContentSource(
                                    ContentSourceEntity(
                                        title = "YouTube: $tagName Training",
                                        summary = "Watch recent training footage and competition highlights for $tagName.",
                                        url = "https://www.youtube.com/results?search_query=$encodedTag+training+highlights",
                                        mediaType = "Video",
                                        sportTag = tagName,
                                        dateFetched = System.currentTimeMillis()
                                    )
                                )

                                // 4. X (Twitter) Hashtag
                                workoutDao.insertContentSource(
                                    ContentSourceEntity(
                                        title = "X: #$tagName Updates",
                                        summary = "Real-time news and athlete updates for $tagName.",
                                        url = "https://twitter.com/hashtag/$cleanTag",
                                        mediaType = "Social",
                                        sportTag = tagName,
                                        dateFetched = System.currentTimeMillis()
                                    )
                                )

                            } catch (e: Exception) {
                                Log.e("HomeViewModel", "Error fetching content for $tagName: ${e.message}")
                            }
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
                    _knowledgeBriefing.value = ""
                    return@collectLatest
                }

                val interestList = interestTags.toList()
                val cached = contentRepository.getCachedBriefing(interestList, workoutTitle)
                
                if (cached != null) {
                    _knowledgeBriefing.value = cached
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

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun upvoteCommunityPick() {
        val pickId = communityPick.value?.id ?: return
        viewModelScope.launch {
            try {
                communityRepository.upvoteExistingPick(pickId)
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Failed to upvote: ${e.message}")
            }
        }
    }

    fun upvoteContent(content: ContentSourceEntity) {
        viewModelScope.launch {
            try {
                communityRepository.upvoteContent(content)
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Failed to upvote content: ${e.message}")
            }
        }
    }

    fun generateRecoverySession(type: String) {
        viewModelScope.launch {
            _isGenerating.value = true
            _errorMessage.value = null
            try {
                var activePlan = repository.getActivePlan() ?: repository.getLatestPlan()
                if (activePlan == null) {
                    _isGenerating.value = false
                    _errorMessage.value = "An AI generated plan is required to generate stretching and accessory workouts."
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
                _errorMessage.value = "Failed to generate workout: ${e.message}"
            } finally {
                _isGenerating.value = false
            }
        }
    }
}