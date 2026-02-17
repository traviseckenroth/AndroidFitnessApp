package com.example.myapplication.ui.workout

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.local.WorkoutSetEntity
import com.example.myapplication.data.remote.BedrockClient
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import com.example.myapplication.data.local.UserPreferencesRepository
import com.example.myapplication.data.repository.HealthConnectManager
import com.example.myapplication.data.repository.WorkoutExecutionRepository
import com.example.myapplication.service.WorkoutTimerService
import com.example.myapplication.util.SpeechToTextManager
import com.example.myapplication.util.VoiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

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
    private val bedrockClient: BedrockClient
) : ViewModel() {

    // --- 1. STATE PROPERTIES ---

    private val _workoutId = MutableStateFlow<Long>(-1)
    private val _sets = MutableStateFlow<List<WorkoutSetEntity>>(emptyList())
    private val _exercises = MutableStateFlow<List<ExerciseEntity>>(emptyList())

    private val _exerciseStates = MutableStateFlow<List<ExerciseState>>(emptyList())
    val exerciseStates: StateFlow<List<ExerciseState>> = _exerciseStates

    private val _coachBriefing = MutableStateFlow("Loading briefing...")
    val coachBriefing: StateFlow<String> = _coachBriefing

    private val _workoutSummary = MutableStateFlow<List<String>?>(null)
    val workoutSummary: StateFlow<List<String>?> = _workoutSummary

    private val _chatHistory = MutableStateFlow(
        listOf(ChatMessage("Coach", "Get exercise tips, address muscle or joint pains, etc."))
    )
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private var workoutStartTime: Instant = Instant.now()
    private var transcribeJob: Job? = null

    // --- 2. INITIALIZATION ---
    private var isInitialTimerCheck = true

    init {
        workoutStartTime = Instant.now()

        // Sync with Timer Service
        viewModelScope.launch {
            WorkoutTimerService.timerState.collect { serviceState ->
                // 1. Check for Stale State on Startup
                if (isInitialTimerCheck) {
                    isInitialTimerCheck = false
                    if (serviceState.hasFinished) {
                        Log.w("WorkoutTimer", "Ignoring stale 'Finished' state from previous session.")
                        stopTimerService() // Clear the stale state in the service
                        // Do NOT run completion logic here. Just update UI to 00:00.
                        updateLocalTimerState(
                            activeExerciseId = serviceState.activeExerciseId,
                            remainingTime = 0,
                            isRunning = false
                        )
                        return@collect
                    }
                }

                // 2. Normal Logic (Only runs for subsequent events)
                val isTimerFinished = serviceState.remainingTime == 0 && serviceState.activeExerciseId != null
                if (serviceState.hasFinished && serviceState.activeExerciseId != null) {
                    val exerciseId = serviceState.activeExerciseId!!
                    val currentState = _exerciseStates.value.find { it.exercise.exerciseId == exerciseId }
                    val currentSet = currentState?.sets?.firstOrNull { !it.isCompleted }

                    if (currentSet != null) {
                        updateSetCompletion(currentSet, true)
                        startSetTimer(exerciseId)
                    }
                }

                updateLocalTimerState(
                    activeExerciseId = serviceState.activeExerciseId,
                    remainingTime = serviceState.remainingTime.toLong(),
                    isRunning = serviceState.isRunning
                )
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

        // Construct UI State (ExerciseStates)
        viewModelScope.launch {
            combine(
                _exercises,
                _sets,
                userPrefs.recoveryScore
            ) { allExercises: List<ExerciseEntity>, sessionSets: List<WorkoutSetEntity>, recovery: Int ->
                val sessionExerciseIds = sessionSets.map { it.exerciseId }.toSet()
                val sessionExercises = allExercises
                    .filter { it.exerciseId in sessionExerciseIds }
                    .sortedBy { it.tier }

                if (sessionExercises.isNotEmpty() && sessionSets.isNotEmpty()) {
                    _coachBriefing.value = generateCoachBriefing(sessionExercises, sessionSets, recovery)
                }

                val setsByExercise = sessionSets.groupBy { it.exerciseId }
                sessionExercises.mapNotNull { exercise ->
                    setsByExercise[exercise.exerciseId]?.let { exerciseSets ->
                        val existingState = _exerciseStates.value.find { it.exercise.exerciseId == exercise.exerciseId }
                        ExerciseState(
                            exercise = exercise,
                            sets = exerciseSets.sortedBy { it.setNumber },
                            timerState = existingState?.timerState ?: ExerciseTimerState(
                                remainingTime = (exercise.estimatedTimePerSet * 60).toLong()
                            ),
                            areSetsVisible = existingState?.areSetsVisible ?: true
                        )
                    }
                }
            }.collect { newStates ->
                if (newStates.isNotEmpty() || _sets.value.isEmpty()) {
                    _exerciseStates.value = newStates
                }
            }
        }
    }

    // --- 3. SESSION MANAGEMENT ---

    fun loadWorkout(workoutId: Long) {
        viewModelScope.launch {
            _workoutId.value = workoutId
            val workoutExercises = repository.getExercisesByIds(
                repository.getSetsForSession(workoutId).first().map { it.exerciseId }.distinct()
            ).first().sortedBy { it.tier }

            val rawSets = repository.getSetsForSession(workoutId).first()
            val recoveryScore = userPrefs.recoveryScore.first()
            val rpeReduction = when {
                recoveryScore < 40 -> 2
                recoveryScore < 70 -> 1
                else -> 0
            }

            val adjustedSets = rawSets.map { set ->
                if (rpeReduction > 0) {
                    set.copy(suggestedRpe = (set.suggestedRpe - rpeReduction).coerceAtLeast(1))
                } else {
                    set
                }
            }

            val groupedSets = adjustedSets.groupBy { it.exerciseId }
            _exerciseStates.value = workoutExercises.map { exercise ->
                ExerciseState(
                    exercise = exercise,
                    sets = groupedSets[exercise.exerciseId] ?: emptyList(),
                    timerState = ExerciseTimerState()
                )
            }

            var briefing = generateCoachBriefing(workoutExercises, adjustedSets, recoveryScore)
            if (rpeReduction > 0) {
                briefing = "⚠️ RECOVERY LOW ($recoveryScore%)\nTargets reduced by $rpeReduction RPE. Take it easy today.\n\n$briefing"
            }
            _coachBriefing.value = briefing
        }
    }

    fun finishWorkout(workoutId: Long) {
        stopTimerService() // FIX: Correctly stop the service to clear state
        viewModelScope.launch {
            val report = repository.completeWorkout(workoutId)
            _workoutSummary.value = report

            val endTime = Instant.now()
            val durationMin = ChronoUnit.MINUTES.between(workoutStartTime, endTime)
            val estimatedCalories = (durationMin * 4.5).coerceAtLeast(10.0)

            try {
                if (healthConnectManager.hasPermissions()) {
                    healthConnectManager.writeWorkout(
                        workoutId = workoutId,
                        startTime = workoutStartTime,
                        endTime = endTime,
                        calories = estimatedCalories,
                        title = "Strength Workout"
                    )
                }
            } catch (e: Exception) {
                Log.e("Workout", "Failed to sync with Health Connect", e)
            }
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
        viewModelScope.launch { repository.updateSet(set.copy(actualReps = repsInt)) }
    }

    fun updateSetWeight(set: WorkoutSetEntity, newLbs: String) {
        val lbsFloat = newLbs.toFloatOrNull() ?: return
        viewModelScope.launch { repository.updateSet(set.copy(actualLbs = lbsFloat)) }
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

    // FIX: Helper to explicitly stop the service and reset state
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

            val currentExercises = _exerciseStates.value.filter {
                it.sets.any { set -> !set.isCompleted }
            }.joinToString { it.exercise.name }
            val available = repository.getAllExercises().first()
            val response = bedrockClient.coachInteraction(currentExercises, userText, available)

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
                val incompleteSets = _sets.value.filter { !it.isCompleted }
                repository.deleteSets(incompleteSets)

                val newSets = response.exercises.mapNotNull { genEx ->
                    val match = available.find { it.name.equals(genEx.name, ignoreCase = true) }
                    if (match != null) {
                        List(genEx.sets) { setNum ->
                            WorkoutSetEntity(
                                workoutId = workoutId,
                                exerciseId = match.exerciseId,
                                setNumber = setNum + 1,
                                suggestedReps = genEx.suggestedReps,
                                suggestedLbs = genEx.suggestedLbs.toInt(),
                                suggestedRpe = 8
                            )
                        }
                    } else null
                }.flatten()

                if (newSets.isNotEmpty()) {
                    repository.insertSets(newSets)
                    loadWorkout(workoutId)
                }
            }
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
        stopTimerService() // FIX: Ensure service stops and state is cleared on exit
    }

    // --- PRIVATE HELPERS ---

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