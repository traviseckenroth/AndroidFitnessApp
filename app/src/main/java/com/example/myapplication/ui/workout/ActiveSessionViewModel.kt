// app/src/main/java/com/example/myapplication/ui/workout/ActiveSessionViewModel.kt
package com.example.myapplication.ui.workout

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.local.WorkoutSetEntity
import com.example.myapplication.data.remote.BedrockClient
import com.example.myapplication.data.local.UserPreferencesRepository
import com.example.myapplication.data.repository.HealthConnectManager
import com.example.myapplication.data.repository.WorkoutExecutionRepository
import com.example.myapplication.data.repository.WorkoutSummaryResult
import com.example.myapplication.service.WorkoutTimerService
import com.example.myapplication.service.WorkoutSyncWorker
import com.example.myapplication.util.PlateCalculator
import com.example.myapplication.util.ReadinessEngine
import com.example.myapplication.util.SpeechToTextManager
import com.example.myapplication.util.VoiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

// --- DATA MODELS ---

data class ExerciseTimerState(
    val remainingTime: Long = 0L,
    val isRunning: Boolean = false,
    val isFinished: Boolean = false
)

data class ExerciseState(
    val exercise: ExerciseEntity,
    val sets: List<WorkoutSetEntity>,
    val timerState: ExerciseTimerState,
    val areSetsVisible: Boolean = true
)

data class ChatMessage(val sender: String, val text: String)

// --- VIEWMODEL ---

@HiltViewModel
class ActiveSessionViewModel @Inject constructor(
    private val repository: WorkoutExecutionRepository,
    private val application: Application,
    private val userPrefs: UserPreferencesRepository,
    private val healthConnectManager: HealthConnectManager,
    private val voiceManager: VoiceManager,
    private val speechToTextManager: SpeechToTextManager,
    private val bedrockClient: BedrockClient,
    private val readinessEngine: ReadinessEngine
) : ViewModel() {

    // --- 1. STATE PROPERTIES ---

    private val _workoutId = MutableStateFlow<Long>(-1)
    private val _sets = MutableStateFlow<List<WorkoutSetEntity>>(emptyList())
    private val _exercises = MutableStateFlow<List<ExerciseEntity>>(emptyList())

    private val _exerciseStates = MutableStateFlow<List<ExerciseState>>(emptyList())
    val exerciseStates: StateFlow<List<ExerciseState>> = _exerciseStates

    private val _coachBriefing = MutableStateFlow("Loading briefing...")
    val coachBriefing: StateFlow<String> = _coachBriefing

    private val _workoutSummary = MutableStateFlow<WorkoutSummaryResult?>(null)
    val workoutSummary: StateFlow<WorkoutSummaryResult?> = _workoutSummary

    val barWeight: StateFlow<Double> = userPrefs.userGender
        .map { if (it.equals("Female", ignoreCase = true)) 35.0 else 45.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 45.0)

    val userGender: StateFlow<String> = userPrefs.userGender
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Male")

    private val _chatHistory = MutableStateFlow(
        listOf(ChatMessage("Coach", "Get exercise tips, address muscle or joint pains, etc."))
    )
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private var workoutStartTime: Instant = Instant.now()
    private var transcribeJob: Job? = null

    // Optimistic Estimated Time calculation
    val totalEstimatedTime: StateFlow<Int> = _exerciseStates.map { states ->
        states.sumOf { (it.exercise.estimatedTimePerSet * it.sets.size).toInt() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // --- 2. INITIALIZATION ---

    init {
        workoutStartTime = Instant.now()

        // Sync with Health Connect on start for Bio-Syncing
        viewModelScope.launch {
            if (healthConnectManager.hasPermissions()) {
                val sleepDuration = healthConnectManager.getLastNightSleepDuration()
                val sleepHours = sleepDuration.toMinutes() / 60.0
                if (sleepHours > 0) {
                    val recoveryScore = ((sleepHours / 8.0) * 100).toInt().coerceIn(0, 100)
                    userPrefs.updateRecoveryScore(recoveryScore)
                    Log.d("ActiveSessionVM", "Bio-Sync: Sleep=$sleepHours hrs, Updated Recovery=$recoveryScore%")
                }
            }
        }

        // Sync Readiness Engine to Preferences
        viewModelScope.launch {
            val formaScore = readinessEngine.calculateReadiness()
            userPrefs.updateRecoveryScore(formaScore.score)
        }

        // Sync with Timer Service
        viewModelScope.launch {
            WorkoutTimerService.timerState.collect { serviceState ->
                updateLocalTimerState(
                    activeExerciseId = serviceState.activeExerciseId,
                    remainingTime = serviceState.remainingTime.toLong(),
                    isRunning = serviceState.isRunning
                )

                if (serviceState.hasFinished && serviceState.activeExerciseId != null) {
                    val exerciseId = serviceState.activeExerciseId!!
                    val currentState = _exerciseStates.value.find { it.exercise.exerciseId == exerciseId }
                    val currentSet = currentState?.sets?.firstOrNull { !it.isCompleted }

                    if (currentSet != null) {
                        updateSetCompletion(currentSet, true)
                        startSetTimer(exerciseId)
                    }
                }
            }
        }

        // Fetch Sets when ID changes
        viewModelScope.launch {
            _workoutId.collectLatest { id ->
                if (id != -1L) {
                    repository.getSetsForSession(id).collect { loadedSets ->
                        val filledSets = loadedSets.map { set ->
                            set.copy(
                                actualReps = if (set.actualReps == null || set.actualReps == 0) set.suggestedReps else set.actualReps,
                                actualLbs = if (set.actualLbs == null || set.actualLbs == 0f) set.suggestedLbs.toFloat() else set.actualLbs,
                                actualRpe = if (set.actualRpe == null || set.actualRpe == 0.0f) set.suggestedRpe.toFloat() else set.actualRpe
                            )
                        }
                        _sets.value = filledSets
                    }
                }
            }
        }

        // Fetch Exercises when Sets change
        viewModelScope.launch {
            _sets.collectLatest { sets ->
                if (sets.isNotEmpty()) {
                    val exerciseIds = sets.map { it.exerciseId }.distinct()
                    repository.getExercisesByIds(exerciseIds).collect {
                        _exercises.value = it
                    }
                } else {
                    _exercises.value = emptyList()
                }
            }
        }

        // Construct UI State (Unified Logic)
        viewModelScope.launch {
            combine(
                _exercises,
                _sets,
                userPrefs.recoveryScore
            ) { allExercises, sessionSets, recovery ->
                val rpeReduction = when {
                    recovery < 40 -> 2
                    recovery < 70 -> 1
                    else -> 0
                }

                val sessionExerciseIds = sessionSets.map { it.exerciseId }.toSet()
                val sessionExercises = allExercises
                    .filter { it.exerciseId in sessionExerciseIds }
                    .sortedBy { it.tier }

                if (sessionExercises.isNotEmpty() && sessionSets.isNotEmpty()) {
                    var briefing = this@ActiveSessionViewModel.generateCoachBriefing(sessionExercises, sessionSets, recovery)
                    if (rpeReduction > 0) {
                        briefing = "⚠️ RECOVERY LOW ($recovery%)\nTargets reduced by $rpeReduction RPE. Take it easy today.\n\n$briefing"
                    }
                    _coachBriefing.value = briefing
                }

                val setsByExercise = sessionSets.groupBy { it.exerciseId }

                sessionExercises.map { exercise ->
                    val rawSets = setsByExercise[exercise.exerciseId] ?: emptyList()
                    val adjustedSets = rawSets.map { set ->
                        if (rpeReduction > 0) {
                            set.copy(suggestedRpe = (set.suggestedRpe - rpeReduction).coerceAtLeast(1))
                        } else set
                    }.sortedBy { it.setNumber }

                    val existingState = _exerciseStates.value.find { it.exercise.exerciseId == exercise.exerciseId }
                    
                    val isAllCompleted = adjustedSets.isNotEmpty() && adjustedSets.all { it.isCompleted }
                    val wasAllCompletedBefore = existingState?.sets?.isNotEmpty() == true && existingState.sets.all { it.isCompleted }

                    ExerciseState(
                        exercise = exercise,
                        sets = adjustedSets,
                        timerState = existingState?.timerState ?: ExerciseTimerState(
                            remainingTime = (exercise.estimatedTimePerSet * 60).toLong()
                        ),
                        areSetsVisible = if (isAllCompleted && !wasAllCompletedBefore) false else (existingState?.areSetsVisible ?: true)
                    )
                }
            }.collect { newStates ->
                _exerciseStates.value = newStates
            }
        }
    }

    // --- 3. SESSION MANAGEMENT ---

    fun loadWorkout(workoutId: Long) {
        _workoutId.value = workoutId
    }

    fun loadSummary(workoutId: Long) {
        viewModelScope.launch {
            if (_workoutSummary.value == null) {
                _workoutSummary.value = repository.getWorkoutSummary(workoutId)
            }
        }
    }

    fun finishWorkout(workoutId: Long) {
        stopTimerService()
        viewModelScope.launch {
            // 1. Mark as complete in Room (Critical & Fast)
            val report = repository.completeWorkout(workoutId)
            _workoutSummary.value = report

            val endTime = Instant.now()
            val durationMin = ChronoUnit.MINUTES.between(workoutStartTime, endTime)
            val estimatedCalories = (durationMin * 4.5).coerceAtLeast(10.0)

            // 2. Offload Health Connect Sync to WorkManager (Background)
            val syncRequest = OneTimeWorkRequestBuilder<WorkoutSyncWorker>()
                .setInputData(workDataOf(
                    "workoutId" to workoutId,
                    "startTime" to workoutStartTime.toString(),
                    "endTime" to endTime.toString(),
                    "calories" to estimatedCalories
                ))
                .build()

            WorkManager.getInstance(application).enqueue(syncRequest)
            Log.d("Workout", "Enqueued background sync for workout $workoutId")
        }
    }

    fun clearSummary() {
        _workoutSummary.value = null
    }

    // --- 4. EXERCISE & SET UPDATES ---

    fun updateSetCompletion(set: WorkoutSetEntity, isCompleted: Boolean) {
        viewModelScope.launch { repository.updateSet(set.copy(isCompleted = isCompleted)) }
    }

    fun updateSetReps(set: WorkoutSetEntity, newReps: String) {
        val repsInt = newReps.toIntOrNull() ?: return
        viewModelScope.launch {
            repository.updateSetsReps(set.workoutId, set.exerciseId, set.setNumber, repsInt)
        }
    }

    fun updateSetWeight(set: WorkoutSetEntity, newLbs: String) {
        val lbsFloat = newLbs.toFloatOrNull() ?: return
        viewModelScope.launch {
            repository.updateSetsWeight(set.workoutId, set.exerciseId, set.setNumber, lbsFloat)
        }
    }

    fun updateSetRpe(set: WorkoutSetEntity, newRpe: String) {
        val rpeFloat = newRpe.toFloatOrNull() ?: return
        viewModelScope.launch { repository.updateSet(set.copy(actualRpe = rpeFloat)) }
    }

    fun toggleExerciseVisibility(exerciseId: Long) {
        _exerciseStates.value = _exerciseStates.value.map {
            if (it.exercise.exerciseId == exerciseId) it.copy(areSetsVisible = !it.areSetsVisible) else it
        }
    }

    fun swapExercise(oldExerciseId: Long, newExerciseId: Long) {
        viewModelScope.launch { repository.swapExercise(_workoutId.value, oldExerciseId, newExerciseId) }
    }

    fun addSet(exerciseId: Long) {
        viewModelScope.launch {
            repository.addSet(_workoutId.value, exerciseId)
        }
    }

    fun addExercise(exerciseId: Long) {
        viewModelScope.launch {
            repository.addExercise(_workoutId.value, exerciseId)
        }
    }

    fun getAllExercises(): Flow<List<ExerciseEntity>> = repository.getAllExercises()

    suspend fun getTopAlternatives(exercise: ExerciseEntity): List<ExerciseEntity> {
        return repository.getBestAlternatives(exercise)
    }

    fun addWarmUpSets(exerciseId: Long, workingWeight: Int) {
        viewModelScope.launch {
            val workoutId = _workoutId.value
            if (workoutId != -1L) {
                repository.injectWarmUpSets(workoutId, exerciseId, workingWeight)
            }
        }
    }

    // --- 5. TIMER LOGIC ---

    fun startSetTimer(exerciseId: Long) {
        val exerciseState = _exerciseStates.value.find { it.exercise.exerciseId == exerciseId } ?: return
        val durationSeconds = (exerciseState.exercise.estimatedTimePerSet * 60).toInt()

        val intent = Intent(application, WorkoutTimerService::class.java).apply {
            action = WorkoutTimerService.ACTION_START
            putExtra(WorkoutTimerService.EXTRA_SECONDS, durationSeconds)
            putExtra(WorkoutTimerService.EXTRA_EXERCISE_ID, exerciseId)
        }
        application.startService(intent)
    }

    fun skipSetTimer(exerciseId: Long) {
        val currentState = _exerciseStates.value.find { it.exercise.exerciseId == exerciseId }
        val currentSet = currentState?.sets?.firstOrNull { !it.isCompleted }
        if (currentSet != null) {
            updateSetCompletion(currentSet, true)
        }
        startSetTimer(exerciseId)
    }

    private fun stopTimerService() {
        val intent = Intent(application, WorkoutTimerService::class.java).apply {
            action = WorkoutTimerService.ACTION_STOP
        }
        application.startService(intent)
    }

    private fun updateLocalTimerState(activeExerciseId: Long?, remainingTime: Long, isRunning: Boolean) {
        _exerciseStates.value = _exerciseStates.value.map { state ->
            if (state.exercise.exerciseId == activeExerciseId) {
                state.copy(timerState = ExerciseTimerState(
                    remainingTime = remainingTime,
                    isRunning = isRunning,
                    isFinished = !isRunning && remainingTime == 0L
                ))
            } else {
                val defaultDuration = (state.exercise.estimatedTimePerSet * 60).toLong()
                state.copy(timerState = ExerciseTimerState(
                    remainingTime = defaultDuration,
                    isRunning = false,
                    isFinished = false
                ))
            }
        }
    }

    // --- 6. AI & CHAT INTERACTION ---

    fun interactWithCoach(userText: String) {
        val currentHistory = _chatHistory.value.toMutableList()
        currentHistory.add(ChatMessage("User", userText))
        _chatHistory.value = currentHistory

        viewModelScope.launch {
            val wasListening = _isListening.value
            if (wasListening) {
                transcribeJob?.cancel()
            }

            // --- LOCAL INTENT INTERCEPTION ---
            if (handleLocalIntent(userText)) return@launch

            // --- CLOUD AI FALLBACK ---
            val currentExercisesList = _exerciseStates.value.filter {
                it.sets.any { set -> !set.isCompleted }
            }.map { it.exercise.name }
            
            val available = repository.getAllExercises().first()
            val response = bedrockClient.coachInteraction(currentExercisesList.joinToString("\n"), userText, available)

            val newHistory = _chatHistory.value.toMutableList()
            newHistory.add(ChatMessage("Coach", response.explanation))
            _chatHistory.value = newHistory

            voiceManager.speak(response.explanation) {
                if (wasListening && _isListening.value) {
                    viewModelScope.launch {
                        startLiveTranscription()
                    }
                }
            }

            if (response.exercises.isNotEmpty()) {
                val workoutId = _workoutId.value
                
                // Identify all exercises to replace (explicitly named OR matching new suggestions)
                val suggestedNames = response.exercises.map { it.name.lowercase().trim() }
                
                val exercisesToReplace = _exerciseStates.value.filter { existing ->
                    val existingName = existing.exercise.name.lowercase().trim()
                    val isExplicitlyNamed = response.replacingExerciseName?.lowercase()?.trim() == existingName
                    val matchesSuggested = suggestedNames.any { it == existingName || it.contains(existingName) || existingName.contains(it) }
                    isExplicitlyNamed || matchesSuggested
                }

                // Remove uncompleted sets for identified exercises
                exercisesToReplace.forEach { ex ->
                    val incompleteSets = ex.sets.filter { !it.isCompleted }
                    if (incompleteSets.isNotEmpty()) {
                        repository.deleteSets(incompleteSets)
                    }
                }

                // Insert NEW suggested sets
                val setsToInsert = response.exercises.mapNotNull { genEx ->
                    val match = available.find {
                        it.name.equals(genEx.name, ignoreCase = true) ||
                        it.name.contains(genEx.name, ignoreCase = true)
                    }

                    if (match != null) {
                        // Find if we already had completed sets for this exercise to keep numbering correct
                        val completedCountForThisExercise = _exerciseStates.value
                            .find { it.exercise.exerciseId == match.exerciseId }
                            ?.sets?.count { it.isCompleted } ?: 0

                        List(genEx.sets) { setIdx ->
                            WorkoutSetEntity(
                                workoutId = workoutId,
                                exerciseId = match.exerciseId,
                                setNumber = completedCountForThisExercise + setIdx + 1,
                                suggestedReps = genEx.suggestedReps,
                                suggestedLbs = genEx.suggestedLbs.toInt(),
                                suggestedRpe = 8
                            )
                        }
                    } else null
                }.flatten()

                if (setsToInsert.isNotEmpty()) {
                    repository.insertSets(setsToInsert)
                }
            }
        }
    }

    private suspend fun handleLocalIntent(userText: String): Boolean {
        val lowerText = userText.lowercase()

        // 1. Swap Intent
        if (lowerText.contains("swap") || lowerText.contains("alternative") || lowerText.contains("different")) {
            val exerciseToSwap = _exerciseStates.value.find { state ->
                lowerText.contains(state.exercise.name.lowercase())
            } ?: _exerciseStates.value.firstOrNull { state ->
                state.sets.any { !it.isCompleted }
            }

            if (exerciseToSwap != null) {
                val alternatives = repository.getBestAlternatives(exerciseToSwap.exercise)
                if (alternatives.isNotEmpty()) {
                    val bestAlt = alternatives.first()
                    val coachMsg = "Swapping ${exerciseToSwap.exercise.name} for ${bestAlt.name}. It targets the same muscle groups and fits your plan's tier."
                    addCoachResponse(coachMsg)
                    swapExercise(exerciseToSwap.exercise.exerciseId, bestAlt.exerciseId)
                    return true
                }
            }
        }

        // 2. Plate Math Intent
        if (lowerText.contains("plate") || lowerText.contains("math") || lowerText.contains("weight")) {
            val weightMatch = Regex("(\\d+(\\.\\d+)?)").find(userText)
            if (weightMatch != null) {
                val targetWeight = weightMatch.value.toDouble()
                val mathResult = calculatePlateMath(targetWeight)
                addCoachResponse(mathResult)
                return true
            }
        }

        return false
    }

    private fun addCoachResponse(text: String) {
        val newHistory = _chatHistory.value.toMutableList()
        newHistory.add(ChatMessage("Coach", text))
        _chatHistory.value = newHistory
        voiceManager.speak(text) {}
    }

    private fun calculatePlateMath(target: Double): String {
        val bar = barWeight.value
        val platesString = PlateCalculator.calculatePlates(target, bar)
        return if (target <= bar) {
            "That's just the bar ($bar lbs)."
        } else {
            "For $target lbs, load $platesString."
        }
    }

    suspend fun generateCoachingCue(exerciseName: String, issue: String): String {
        return bedrockClient.generateCoachingCue(exerciseName, issue, 0)
    }

    // --- 7. LIVE VOICE COACHING ---

    fun toggleLiveCoaching() {
        if (_isListening.value) {
            stopLiveTranscription()
        } else {
            _isListening.value = true
            startLiveTranscription()
        }
    }

    private fun startLiveTranscription() {
        transcribeJob?.cancel()

        transcribeJob = viewModelScope.launch {
            try {
                speechToTextManager.startListening()
                    .collect { transcript ->
                        Log.d("LiveCoach", "Heard: $transcript")
                        interactWithCoach(transcript)
                    }
            } catch (e: Exception) {
                Log.e("LiveCoach", "Speech error: ${e.message}")
                _isListening.value = false
            }
        }
    }

    private fun stopLiveTranscription() {
        _isListening.value = false
        transcribeJob?.cancel()
        transcribeJob = null
        voiceManager.stop()
    }

    override fun onCleared() {
        super.onCleared()
        stopLiveTranscription()
        voiceManager.stop()
        stopTimerService()
    }

    private fun generateCoachBriefing(
        exercises: List<ExerciseEntity>,
        sets: List<WorkoutSetEntity>,
        recoveryScore: Int
    ): String {
        if (exercises.isEmpty() || sets.isEmpty()) return "Ready to start your workout?"

        val dominantMuscle = exercises.groupingBy { it.muscleGroup }
            .eachCount()
            .maxByOrNull { it.value }?.key ?: "Full Body"

        val tier1Count = exercises.count { it.tier == 1 }
        val avgReps = if (sets.isNotEmpty()) sets.map { it.suggestedReps }.average() else 0.0

        return when {
            tier1Count >= 2 && avgReps < 8 -> "Mission: Heavy $dominantMuscle Day.\nIntensity is key today."
            else -> "Mission: $dominantMuscle Hypertrophy.\nFocus on the mind-muscle connection."
        }
    }
}
