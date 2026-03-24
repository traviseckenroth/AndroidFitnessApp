// File: app/src/main/java/com/example/myapplication/ui/home/HomeViewModel.kt
package com.example.myapplication.ui.home

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.myapplication.data.local.ContentSourceEntity
import com.example.myapplication.data.local.DailyWorkoutEntity
import com.example.myapplication.data.local.UserPreferencesRepository
import com.example.myapplication.data.local.WorkoutDao
import com.example.myapplication.data.local.UserSubscriptionEntity
import com.example.myapplication.data.remote.BedrockClient
import com.example.myapplication.data.remote.CommunityPick
import com.example.myapplication.data.repository.CommunityRepository
import com.example.myapplication.data.repository.ContentRepository
import com.example.myapplication.data.repository.HealthConnectManager
import com.example.myapplication.data.repository.PlanProgress
import com.example.myapplication.data.repository.PlanRepository
import com.example.myapplication.service.DiscoveryWorker
import com.example.myapplication.ui.navigation.ActiveWorkout
import com.example.myapplication.ui.navigation.StretchingSession
import com.example.myapplication.util.FormaScore
import com.example.myapplication.util.ReadinessEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class HomeUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val isGenerating: Boolean = false,
    val selectedCategory: String = "All",
    val knowledgeBriefing: String = "",
    val isBriefingLoading: Boolean = false,
    val errorMessage: String? = null,
    val formaScore: FormaScore? = null,
    val showHealthConnectOnboarding: Boolean = false,
    val planProgress: PlanProgress? = null,
    val userName: String = "User",
    val workoutDates: List<LocalDate> = emptyList(),
    val dailyWorkout: DailyWorkoutEntity? = null,
    val filteredContent: List<ContentSourceEntity> = emptyList(),
    val communityPick: CommunityPick? = null,
    val subscriptions: List<UserSubscriptionEntity> = emptyList(),
    val healthConnectAvailability: Int = HealthConnectClient.SDK_UNAVAILABLE
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PlanRepository,
    private val bedrockClient: BedrockClient,
    private val workoutDao: WorkoutDao,
    private val contentRepository: ContentRepository,
    private val communityRepository: CommunityRepository,
    private val userPrefs: UserPreferencesRepository,
    val healthConnectManager: HealthConnectManager,
    private val readinessEngine: ReadinessEngine
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<Any>()
    val navigationEvents = _navigationEvents.asSharedFlow()

    private val _knowledgeBriefing = MutableStateFlow("")
    val knowledgeBriefing: StateFlow<String> = _knowledgeBriefing.asStateFlow()

    private val _isBriefingLoading = MutableStateFlow(false)
    val isBriefingLoading: StateFlow<Boolean> = _isBriefingLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _formaScore = MutableStateFlow<FormaScore?>(null)
    val formaScore: StateFlow<FormaScore?> = _formaScore.asStateFlow()

    private val _showHealthConnectOnboarding = MutableStateFlow(false)
    val showHealthConnectOnboarding: StateFlow<Boolean> = _showHealthConnectOnboarding.asStateFlow()

    val healthConnectAvailability = healthConnectManager.availability

    val planProgress: StateFlow<PlanProgress?> = repository.getActivePlanProgressFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
    private val dailyWorkoutFlow = _selectedDate
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

    private val subscriptionsFlow = workoutDao.getAllSubscriptions()
    private val _rawSubscribedContent = workoutDao.getSubscribedContent()
    
    private val filteredContentFlow = combine(
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
    }

    private val communityPickFlow = communityRepository.getTopCommunityPick()

    val uiState: StateFlow<HomeUiState> = combine(
        _selectedDate,
        _isGenerating,
        _selectedCategory,
        _knowledgeBriefing,
        _isBriefingLoading,
        _errorMessage,
        _formaScore,
        _showHealthConnectOnboarding,
        planProgress,
        userName,
        workoutDates,
        dailyWorkoutFlow,
        filteredContentFlow,
        communityPickFlow,
        subscriptionsFlow
    ) { args ->
        HomeUiState(
            selectedDate = args[0] as LocalDate,
            isGenerating = args[1] as Boolean,
            selectedCategory = args[2] as String,
            knowledgeBriefing = args[3] as String,
            isBriefingLoading = args[4] as Boolean,
            errorMessage = args[5] as String?,
            formaScore = args[6] as FormaScore?,
            showHealthConnectOnboarding = args[7] as Boolean,
            planProgress = args[8] as PlanProgress?,
            userName = args[9] as String,
            workoutDates = args[10] as List<LocalDate>,
            dailyWorkout = args[11] as DailyWorkoutEntity?,
            filteredContent = args[12] as List<ContentSourceEntity>,
            communityPick = args[13] as CommunityPick?,
            subscriptions = args[14] as List<UserSubscriptionEntity>,
            healthConnectAvailability = healthConnectAvailability
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    init {
        observeContextForIntel()
        observeContentForBriefing()
        calculateFormaScore()
        checkHealthConnectStatus()
    }

    private fun checkHealthConnectStatus() {
        viewModelScope.launch {
            val shown = userPrefs.healthConnectOnboardingShown.first()
            if (!shown) {
                if (healthConnectAvailability != HealthConnectClient.SDK_UNAVAILABLE) {
                     val hasPerms = healthConnectManager.hasPermissions()
                     if (!hasPerms) {
                         _showHealthConnectOnboarding.value = true
                     }
                }
            }
        }
    }

    fun dismissHealthConnectOnboarding() {
        _showHealthConnectOnboarding.value = false
        viewModelScope.launch {
            userPrefs.setHealthConnectOnboardingShown(true)
        }
    }

    fun performHealthConnectAction() {
        if (healthConnectAvailability == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
            healthConnectManager.promptInstall()
        }
    }

    private fun calculateFormaScore() {
        viewModelScope.launch {
            uiState.map { it.dailyWorkout }.distinctUntilChanged().collectLatest { workout ->
                val title = workout?.title ?: "training"
                // Move heavy logic off the UI thread
                val score = withContext(Dispatchers.Default) {
                    readinessEngine.calculateReadiness(title)
                }
                _formaScore.value = score
            }
        }
    }

    private fun observeContextForIntel() {
        viewModelScope.launch {
            uiState.map { state -> state.subscriptions.map { it.tagName }.toSet() }
                .distinctUntilChanged()
                .collectLatest { tagSet ->
                    if (tagSet.isNotEmpty()) {
                        val inputData = Data.Builder()
                            .putStringArray("tagNames", tagSet.toTypedArray())
                            .build()
                        
                        val discoveryWork = OneTimeWorkRequestBuilder<DiscoveryWorker>()
                            .setInputData(inputData)
                            .build()
                        
                        WorkManager.getInstance(context).enqueue(discoveryWork)
                    }
                }
        }
    }

    private fun observeContentForBriefing() {
        viewModelScope.launch {
            combine(
                uiState.map { it.subscriptions.map { s -> s.tagName }.toSet() }.distinctUntilChanged(),
                uiState.map { it.dailyWorkout?.title }.distinctUntilChanged(),
                _rawSubscribedContent.map { list ->
                    // Optimization: Hash of first 10 item IDs to detect content changes specifically for what's sent to AI
                    list.take(10).map { it.sourceId }.hashCode()
                }.distinctUntilChanged()
            ) { _, workoutTitle, contentHash ->
                workoutTitle to contentHash
            }.collectLatest { (workoutTitle, contentHash) ->
                val cached = contentRepository.getCachedBriefing(contentHash, workoutTitle)
                
                if (cached != null) {
                    _knowledgeBriefing.value = cached
                } else {
                    val currentContent = _rawSubscribedContent.first()
                    if (currentContent.isNotEmpty()) {
                        generateKnowledgeBriefing(currentContent, contentHash, workoutTitle)
                    }
                }
            }
        }
    }

    private fun generateKnowledgeBriefing(
        content: List<ContentSourceEntity>,
        contentHash: Int,
        workoutTitle: String?
    ) {
        if (_isBriefingLoading.value) return
        
        viewModelScope.launch {
            _isBriefingLoading.value = true
            val briefing = bedrockClient.generateKnowledgeBriefing(content.take(10), workoutTitle)
            contentRepository.updateBriefingCache(briefing, contentHash = contentHash, workoutTitle = workoutTitle)
            _knowledgeBriefing.value = briefing
            _isBriefingLoading.value = false
        }
    }

    fun forceRefreshBriefing() {
        viewModelScope.launch {
            val content = _rawSubscribedContent.first()
            val contentHash = content.take(10).map { it.sourceId }.hashCode()
            generateKnowledgeBriefing(content, contentHash, uiState.value.dailyWorkout?.title)
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
        val pickId = uiState.value.communityPick?.id ?: return
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
                val activePlan = repository.getActivePlan() ?: repository.getLatestPlan()
                if (activePlan == null) {
                    _isGenerating.value = false
                    _errorMessage.value = "An AI generated plan is required to generate stretching and accessory workouts."
                    return@launch
                }

                repository.getWorkoutHistory().take(1).first()
                val availableExercises = repository.getAllExercises().take(1).first()
                val dateMillis = _selectedDate.value.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

                val aiResponse = if (type == "Stretching") {
                    bedrockClient.generateStretchingFlow(activePlan.goal, availableExercises)
                } else {
                    bedrockClient.generateAccessoryWorkout(activePlan.goal, availableExercises)
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
                        StretchingSession(workoutId = workoutId)
                    } else {
                        ActiveWorkout(workoutId = workoutId)
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

    fun generateSingleDayWorkout(programType: String, durationMinutes: Int) {
        viewModelScope.launch {
            _isGenerating.value = true
            try {
                val activePlan = repository.getActivePlan()
                val planId = activePlan?.planId ?: 0L

                val history = repository.getWorkoutHistory().take(1).first()
                val availableExercises = repository.getAllExercises().take(1).first()

                // Fetch user preferences for accurate generation
                val excludedEq = userPrefs.excludedEquipment.first()
                val age = userPrefs.userAge.first()
                val height = userPrefs.userHeight.first()
                val weight = userPrefs.userWeight.first()

                val dateMillis = _selectedDate.value.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

                // FIX: Switch from generateAccessoryWorkout to generateWorkoutPlan.
                // The accessory prompt specifically limits the AI to short "add-on" volume.
                // generateWorkoutPlan uses your full logic engine to scale sets/reps perfectly to the requested duration.
                val aiResponse = bedrockClient.generateWorkoutPlan(
                    goal = "Standalone Session",
                    programType = programType,
                    days = listOf("Today"),
                    duration = durationMinutes / 60f, // Convert to hours so the AI multiplies it back to exact minutes
                    workoutHistory = history,
                    allExercises = availableExercises,
                    excludedEquipment = excludedEq,
                    userAge = age,
                    userHeight = height,
                    userWeight = weight,
                    block = 1
                )

                if (aiResponse.schedule.isNotEmpty()) {
                    val generatedDay = aiResponse.schedule.first()
                    val workoutId = repository.saveSingleDayWorkout(
                        planId = planId,
                        date = dateMillis,
                        title = generatedDay.title,
                        exercises = generatedDay.exercises
                    )

                    _navigationEvents.emit(ActiveWorkout(workoutId = workoutId))
                } else {
                    _errorMessage.value = "AI failed to generate a session. Please try again."
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
