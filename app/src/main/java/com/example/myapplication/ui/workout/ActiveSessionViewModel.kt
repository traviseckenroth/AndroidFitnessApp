// app/src/main/java/com/example/myapplication/ui/workout/ActiveSessionViewModel.kt
package com.example.myapplication.ui.workout

import android.app.Application
import android.bluetooth.BluetoothDevice
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
import com.example.myapplication.data.local.MemoryDao
import com.example.myapplication.data.local.UserMemoryEntity
import com.example.myapplication.data.repository.HealthConnectManager
import com.example.myapplication.data.repository.WorkoutExecutionRepository
import com.example.myapplication.data.repository.WorkoutSummaryResult
import com.example.myapplication.service.WorkoutTimerService
import com.example.myapplication.service.WorkoutSyncWorker
import com.example.myapplication.util.PlateCalculator
import com.example.myapplication.util.ReadinessEngine
import com.example.myapplication.util.SpeechToTextManager
import com.example.myapplication.util.VoiceManager
import com.example.myapplication.util.AutoCoachEngine
import com.example.myapplication.util.AutoCoachState
import com.example.myapplication.util.BleHeartRateManager
import com.example.myapplication.util.ContinuousAudioStreamer
import com.example.myapplication.util.NativeAutoCoachVoice
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
    val isFinished: Boolean = false,
    val isRest: Boolean = false
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
    private val sttManager: SpeechToTextManager,
    private val bedrockClient: BedrockClient,
    private val readinessEngine: ReadinessEngine,
    private val autoCoachEngine: AutoCoachEngine,
    private val bleHeartRateManager: BleHeartRateManager,
    private val memoryDao: MemoryDao,
    private val audioStreamer: ContinuousAudioStreamer,
    private val nativeAutoCoachVoice: NativeAutoCoachVoice
) : ViewModel() {

    // --- 1. STATE PROPERTIES ---

    private val isDynamicEnabled = userPrefs.isDynamicAutoregEnabled
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

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

    val isListening: StateFlow<Boolean> = sttManager.isListening
    val partialTranscript: StateFlow<String> = sttManager.partialTranscript

    val autoCoachState: StateFlow<AutoCoachState> = autoCoachEngine.state

    val heartRate: StateFlow<Int> = bleHeartRateManager.heartRate
    val isBleConnected: StateFlow<Boolean> = bleHeartRateManager.isConnected
    val foundBleDevices: StateFlow<List<BluetoothDevice>> = bleHeartRateManager.foundDevices

    private val _selectedVoiceSid = MutableStateFlow(nativeAutoCoachVoice.currentVoiceSid)
    val selectedVoiceSid: StateFlow<Int> = _selectedVoiceSid

    private var workoutStartTime: Instant = Instant.now()
    private var transcribeJob: Job? = null
    private var briefingJob: Job? = null

    val totalEstimatedTime: StateFlow<Int> = _exerciseStates.map { states ->
        states.sumOf { (it.exercise.estimatedTimePerSet * it.sets.size).toInt() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val workoutProgress: StateFlow<Float> = _exerciseStates.map { states ->
        val total = states.sumOf { it.sets.size }
        val completed = states.sumOf { state -> state.sets.count { set -> set.isCompleted } }
        if (total > 0) completed.toFloat() / total.toFloat() else 0f
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    // --- 2. INITIALIZATION ---

    init {
        workoutStartTime = Instant.now()

        viewModelScope.launch {
            if (healthConnectManager.hasPermissions()) {
                val sleepDuration = healthConnectManager.getLastNightSleepDuration()
                val sleepHours = sleepDuration.toMinutes() / 60.0
                if (sleepHours > 0) {
                    val recoveryScore = ((sleepHours / 8.0) * 100).toInt().coerceIn(0, 100)
                    userPrefs.updateRecoveryScore(recoveryScore)
                }
            }
        }

        viewModelScope.launch {
            val formaScore = readinessEngine.calculateReadiness()
            userPrefs.updateRecoveryScore(formaScore.score)
        }

        viewModelScope.launch {
            userPrefs.userVoiceSid.collect { sid ->
                setCoachVoice(sid)
            }
        }

        viewModelScope.launch {
            WorkoutTimerService.timerState.collect { serviceState ->
                updateLocalTimerState(
                    activeExerciseId = serviceState.activeExerciseId,
                    remainingTime = serviceState.remainingTime.toLong(),
                    isRunning = serviceState.isRunning,
                    isRest = serviceState.isRest
                )

                if (serviceState.hasFinished && serviceState.activeExerciseId != null) {
                    val exerciseId = serviceState.activeExerciseId!!
                    val currentState = _exerciseStates.value.find { it.exercise.exerciseId == exerciseId }
                    val currentSet = currentState?.sets?.firstOrNull { !it.isCompleted }

                    if (currentSet != null) {
                        updateSetCompletion(currentSet, true)
                        startSetTimer(exerciseId, isRest = false)
                    }
                }
            }
        }

        viewModelScope.launch {
            _workoutId.collectLatest { id ->
                if (id != -1L) {
                    repository.getSetsForSession(id).collect { loadedSets ->
                        _sets.value = loadedSets
                    }
                }
            }
        }

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
                    .sortedWith(
                        compareBy<ExerciseEntity> { it.tier }
                            .thenByDescending { it.equipment?.contains("Barbell", ignoreCase = true) == true }
                    )

                if (sessionExercises.isNotEmpty() && _coachBriefing.value == "Loading briefing...") {
                    triggerCloudBriefing(recovery, sessionExercises)
                }

                val setsByExercise = sessionSets.groupBy { it.exerciseId }

                sessionExercises.map { exercise ->
                    val isBodyweight = exercise.equipment?.contains("Bodyweight", ignoreCase = true) == true
                    val rawSets = setsByExercise[exercise.exerciseId] ?: emptyList()
                    val adjustedSets = rawSets.map { set ->
                        var s = set
                        if (rpeReduction > 0) {
                            s = s.copy(suggestedRpe = (s.suggestedRpe - rpeReduction).coerceAtLeast(1))
                        }
                        if (isBodyweight) {
                            s = s.copy(suggestedLbs = 0, actualLbs = null)
                        }
                        s
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

        viewModelScope.launch {
            audioStreamer.interruptionFlow.collect {
                if (isListening.value) {
                    voiceManager.stop()
                    nativeAutoCoachVoice.shutdown()
                    autoCoachEngine.interrupt()
                    startLiveTranscription()
                }
            }
        }
    }

    private fun triggerCloudBriefing(recovery: Int, exercises: List<ExerciseEntity>) {
        if (briefingJob?.isActive == true) return

        briefingJob = viewModelScope.launch {
            try {
                val memories = memoryDao.getRecentMemories(5)
                val fatigueState = repository.getRecentFatigueState(7)
                val workoutId = _workoutId.value
                val workout = repository.getWorkoutById(workoutId)
                val workoutTitle = workout?.title ?: "Today's Session"
                val exerciseNames = exercises.map { it.name }

                val script = bedrockClient.generatePreWorkoutScript(
                    recovery = recovery,
                    workoutTitle = workoutTitle,
                    exercises = exerciseNames,
                    userMemories = memories,
                    recentFatigueState = fatigueState
                )
                _coachBriefing.value = script
            } catch (e: Exception) {
                _coachBriefing.value = "Let's have a great workout today!"
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
            val report = repository.completeWorkout(workoutId)
            _workoutSummary.value = report

            val endTime = Instant.now()
            val durationMin = ChronoUnit.MINUTES.between(workoutStartTime, endTime)
            val estimatedCalories = (durationMin * 4.5).coerceAtLeast(10.0)

            val syncRequest = OneTimeWorkRequestBuilder<WorkoutSyncWorker>()
                .setInputData(workDataOf(
                    "workoutId" to workoutId,
                    "startTime" to workoutStartTime.toString(),
                    "endTime" to endTime.toString(),
                    "calories" to estimatedCalories
                ))
                .build()

            WorkManager.getInstance(application).enqueue(syncRequest)
        }
    }

    fun clearSummary() {
        _workoutSummary.value = null
    }

    // --- 4. EXERCISE & SET UPDATES ---

    private fun optimisticUpdate(setId: Long, modifier: (WorkoutSetEntity) -> WorkoutSetEntity) {
        _sets.value = _sets.value.map { if (it.setId == setId) modifier(it) else it }
    }

    fun updateSetCompletion(set: WorkoutSetEntity, isCompleted: Boolean) {
        val freshSet = _sets.value.find { it.setId == set.setId } ?: set
        val finalReps = if (isCompleted) freshSet.actualReps ?: freshSet.suggestedReps else freshSet.actualReps
        val finalLbs = if (isCompleted) freshSet.actualLbs ?: freshSet.suggestedLbs.toFloat() else freshSet.actualLbs
        val finalRpe = if (isCompleted) freshSet.actualRpe ?: freshSet.suggestedRpe.toFloat() else freshSet.actualRpe

        val completedSet = freshSet.copy(
            isCompleted = isCompleted,
            actualReps = finalReps,
            actualLbs = finalLbs,
            actualRpe = finalRpe
        )

        optimisticUpdate(set.setId) { completedSet }

        viewModelScope.launch {
            repository.updateSet(completedSet)

            val isDynamicEnabled = userPrefs.isDynamicAutoregEnabled.first()

            if (isCompleted && isDynamicEnabled) {
                val exercise = _exerciseStates.value.find { it.exercise.exerciseId == completedSet.exerciseId }?.exercise
                if (exercise != null) {
                    applyDynamicAutoregulation(completedSet, exercise)
                }
            }

            if (isCompleted && autoCoachState.value != AutoCoachState.OFF) {
                autoCoachEngine.notifySetCompletedManually()
            }
        }
    }

    fun markSetNotAttempted(set: WorkoutSetEntity) {
        val freshSet = _sets.value.find { it.setId == set.setId } ?: set

        val skippedSet = freshSet.copy(
            isCompleted = true,
            actualReps = 0,
            actualLbs = freshSet.actualLbs ?: freshSet.suggestedLbs.toFloat(),
            actualRpe = freshSet.actualRpe ?: freshSet.suggestedRpe.toFloat()
        )

        optimisticUpdate(set.setId) { skippedSet }

        viewModelScope.launch {
            repository.updateSet(skippedSet)

            val isDynamicEnabled = userPrefs.isDynamicAutoregEnabled.first()
            if (isDynamicEnabled) {
                val exercise = _exerciseStates.value.find { it.exercise.exerciseId == skippedSet.exerciseId }?.exercise
                if (exercise != null) {
                    applyDynamicAutoregulation(skippedSet, exercise)
                }
            }

            if (autoCoachState.value != AutoCoachState.OFF) {
                autoCoachEngine.notifySetCompletedManually()
            }
        }
    }

    private suspend fun applyDynamicAutoregulation(completedSet: WorkoutSetEntity, exercise: ExerciseEntity) {
        val isBodyweight = exercise.equipment?.contains("Bodyweight", true) == true
        val isCircuit = completedSet.isAMRAP || completedSet.isEMOM || exercise.name.contains("AMRAP", true)

        val suggestedLbsFloat = completedSet.suggestedLbs.toFloat()
        if (isBodyweight || isCircuit || suggestedLbsFloat <= 0f) return

        val actualReps = completedSet.actualReps ?: completedSet.suggestedReps
        val actualLbs = completedSet.actualLbs?.toFloat() ?: suggestedLbsFloat
        val actualRpe = completedSet.actualRpe?.toFloat() ?: completedSet.suggestedRpe.toFloat()
        val targetRpe = completedSet.suggestedRpe.toFloat()

        var loadMultiplier = 1.0f

        // RULE 1: Underperformance or Skipped
        if (actualReps < completedSet.suggestedReps) {
            loadMultiplier = 0.90f
        } else if (actualReps == completedSet.suggestedReps && actualRpe > targetRpe) {
            loadMultiplier = 0.95f
        }
        // RULE 2: Overperformance
        else if (actualReps > completedSet.suggestedReps) {
            loadMultiplier = 1.05f
        } else if (actualRpe <= targetRpe - 2f) {
            loadMultiplier = 1.05f
        }

        if (loadMultiplier == 1.0f) return

        val newSuggestedLbs = (Math.round((actualLbs * loadMultiplier) / 5.0) * 5).toInt().coerceAtLeast(0)

        val currentState = _exerciseStates.value.find { it.exercise.exerciseId == exercise.exerciseId }
        currentState?.let { state ->
            val uncompletedSets = state.sets.filter { !it.isCompleted && it.setNumber > completedSet.setNumber }

            uncompletedSets.forEach { futureSet ->
                if (futureSet.suggestedLbs != newSuggestedLbs) {
                    repository.updateSet(
                        futureSet.copy(
                            suggestedLbs = newSuggestedLbs,
                            isAutoAdjusted = true
                        )
                    )
                }
            }
        }
    }

    fun updateSetReps(set: WorkoutSetEntity, newReps: String) {
        val repsInt = newReps.toIntOrNull()
        val updatedSet = set.copy(actualReps = repsInt)
        optimisticUpdate(set.setId) { updatedSet }
        viewModelScope.launch { repository.updateSet(updatedSet) }
    }

    fun updateSetWeight(set: WorkoutSetEntity, newLbs: String) {
        val lbsFloat = newLbs.toFloatOrNull()
        val updatedSet = set.copy(actualLbs = lbsFloat)
        optimisticUpdate(set.setId) { updatedSet }
        viewModelScope.launch { repository.updateSet(updatedSet) }
    }

    fun updateSetRpe(set: WorkoutSetEntity, newRpe: String) {
        val rpeFloat = newRpe.toFloatOrNull()
        val updatedSet = set.copy(actualRpe = rpeFloat)
        optimisticUpdate(set.setId) { updatedSet }
        viewModelScope.launch { repository.updateSet(updatedSet) }
    }

    fun toggleExerciseVisibility(exerciseId: Long) {
        _exerciseStates.value = _exerciseStates.value.map {
            if (it.exercise.exerciseId == exerciseId) it.copy(areSetsVisible = !it.areSetsVisible) else it
        }
    }

    // UPDATED: Swap Exercise with Permanent/Limitation flag
    fun swapExercise(oldExerciseId: Long, newExerciseId: Long, isPermanent: Boolean = false, oldExerciseName: String = "", newExerciseName: String = "") {
        viewModelScope.launch {
            // 1. Always swap for today's active session
            repository.swapExercise(_workoutId.value, oldExerciseId, newExerciseId)

            if (isPermanent) {
                // 2. Save limitation to RAG Memory so the AI avoids it in future Block generations
                try {
                    val memory = UserMemoryEntity(
                        timestamp = System.currentTimeMillis(),
                        category = "Pain",
                        exerciseName = oldExerciseName,
                        note = "User cannot perform $oldExerciseName due to an injury or limitation. Substituted with $newExerciseName."
                    )
                    memoryDao.insertMemory(memory)
                    Log.d("ActiveSession", "Saved permanent limitation memory for $oldExerciseName")
                } catch (e: Exception) {
                    Log.e("ActiveSession", "Failed to save limitation memory", e)
                }

                // 3. Update future weeks in the current block
                try {
                    // NOTE: If this method does not exist in your repository yet,
                    // you must add it to your WorkoutExecutionRepository to update future scheduled workouts.
                    repository.swapExerciseInFutureWorkouts(oldExerciseId, newExerciseId)
                } catch (e: Exception) {
                    Log.e("ActiveSession", "swapExerciseInFutureWorkouts needs to be implemented in Repository", e)
                }
            }
        }
    }

    fun addSet(exerciseId: Long) {
        viewModelScope.launch { repository.addSet(_workoutId.value, exerciseId) }
    }

    fun addExercise(exerciseId: Long) {
        viewModelScope.launch { repository.addExercise(_workoutId.value, exerciseId) }
    }

    fun getAllExercises(): Flow<List<ExerciseEntity>> = repository.getAllExercises()

    suspend fun getTopAlternatives(exercise: ExerciseEntity): List<ExerciseEntity> {
        return repository.getBestAlternatives(exercise)
    }

    fun addWarmUpSets(exerciseId: Long, workingWeight: Int, equipment: String? = null) {
        viewModelScope.launch {
            val workoutId = _workoutId.value
            if (workoutId != -1L) {
                repository.injectWarmUpSets(workoutId, exerciseId, workingWeight, equipment)
            }
        }
    }

    // --- 5. TIMER LOGIC ---

    fun startSetTimer(exerciseId: Long, isRest: Boolean = false) {
        val exerciseState = _exerciseStates.value.find { it.exercise.exerciseId == exerciseId } ?: return

        val currentSet = exerciseState.sets.firstOrNull { !it.isCompleted }
        val durationSeconds = if (currentSet?.isEMOM == true && !isRest) {
            60
        } else {
            (exerciseState.exercise.estimatedTimePerSet * 60).toInt()
        }
        val intent = Intent(application, WorkoutTimerService::class.java).apply {
            action = WorkoutTimerService.ACTION_START
            putExtra(WorkoutTimerService.EXTRA_SECONDS, durationSeconds)
            putExtra(WorkoutTimerService.EXTRA_EXERCISE_ID, exerciseId)
            putExtra(WorkoutTimerService.EXTRA_IS_REST, isRest)
        }
        application.startService(intent)
    }

    fun skipSetTimer(exerciseId: Long) {
        val currentState = _exerciseStates.value.find { it.exercise.exerciseId == exerciseId }
        val currentSet = currentState?.sets?.firstOrNull { !it.isCompleted }
        if (currentSet != null) {
            updateSetCompletion(currentSet, true)
        }
        startSetTimer(exerciseId, isRest = false)
    }

    private fun stopTimerService() {
        val intent = Intent(application, WorkoutTimerService::class.java).apply {
            action = WorkoutTimerService.ACTION_STOP
        }
        application.startService(intent)
    }

    private fun updateLocalTimerState(activeExerciseId: Long?, remainingTime: Long, isRunning: Boolean, isRest: Boolean) {
        _exerciseStates.value = _exerciseStates.value.map { state ->
            if (state.exercise.exerciseId == activeExerciseId) {
                state.copy(timerState = ExerciseTimerState(
                    remainingTime = remainingTime,
                    isRunning = isRunning,
                    isFinished = !isRunning && remainingTime == 0L,
                    isRest = isRest
                ))
            } else {
                val defaultDuration = (state.exercise.estimatedTimePerSet * 60).toLong()
                val newTimerState = ExerciseTimerState(
                    remainingTime = defaultDuration,
                    isRunning = false,
                    isFinished = false,
                    isRest = false
                )
                if (state.timerState != newTimerState) {
                    state.copy(timerState = newTimerState)
                } else {
                    state
                }
            }
        }
    }

    // --- 6. AUTO COACH LOGIC ---

    fun toggleAutoCoach() {
        if (autoCoachState.value == AutoCoachState.OFF) {
            val exercisesToCoach = _exerciseStates.value.mapNotNull { state ->
                val incompleteSets = state.sets.filter { !it.isCompleted }
                if (incompleteSets.isNotEmpty()) {
                    state.exercise to incompleteSets
                } else null
            }

            if (exercisesToCoach.isNotEmpty()) {
                val historyMap = exercisesToCoach.associate { (exercise, sets) ->
                    val lastWeight = sets.firstOrNull()?.suggestedLbs ?: 0
                    val lastReps = sets.firstOrNull()?.suggestedReps ?: 0
                    exercise.exerciseId to "Last time: $lastWeight lbs for $lastReps reps"
                }

                autoCoachEngine.startWorkout(
                    exercises = exercisesToCoach,
                    onUpdateReps = { set, reps -> updateSetReps(set, reps) },
                    onUpdateWeight = { set, weight -> updateSetWeight(set, weight) },
                    onSetCompleted = { set -> updateSetCompletion(set, true) },
                    onStartTimer = { exerciseId, isRest -> startSetTimer(exerciseId, isRest) },
                    onExtendTimer = { extraTimeMs -> extendSetTimer(extraTimeMs) },
                    onWorkoutCompleted = { finishWorkout(_workoutId.value) }
                )
            }
        } else {
            autoCoachEngine.stop()
        }
    }

    fun stopAutoCoach() {
        autoCoachEngine.stop()
        stopLiveTranscription()
    }

    private fun extendSetTimer(extraTimeMs: Long) {
        val extraSeconds = (extraTimeMs / 1000).toInt()
        val intent = Intent(application, WorkoutTimerService::class.java).apply {
            action = WorkoutTimerService.ACTION_ADD_TIME
            putExtra(WorkoutTimerService.EXTRA_ADD_TIME_SECONDS, extraSeconds)
        }
        application.startService(intent)
    }

    fun setCoachVoice(sid: Int) {
        nativeAutoCoachVoice.currentVoiceSid = sid
        _selectedVoiceSid.value = sid
    }

    // --- 7. AI & CHAT INTERACTION ---

    fun interactWithCoach(userText: String) {
        if (userText.isBlank()) return

        val currentHistory = _chatHistory.value.toMutableList()
        currentHistory.add(ChatMessage("User", userText))
        _chatHistory.value = currentHistory

        viewModelScope.launch {
            try {
                val memory = bedrockClient.extractUserMemory(userText)
                if (memory != null) {
                    memoryDao.insertMemory(memory)
                    Log.d("RAG", "Saved new user memory: ${memory.note}")
                }
            } catch (e: Exception) {
                Log.e("RAG", "Memory extraction failed", e)
            }
        }

        viewModelScope.launch {
            val wasListening = isListening.value
            if (wasListening) {
                transcribeJob?.cancel()
            }

            if (handleLocalIntent(userText)) return@launch

            val currentExercisesList = _exerciseStates.value.filter {
                it.sets.any { set -> !set.isCompleted }
            }.map { it.exercise.name }

            val available = repository.getAllExercises().first()
            val response = bedrockClient.coachInteraction(currentExercisesList.joinToString("\n"), userText, available)

            val newHistory = _chatHistory.value.toMutableList()
            newHistory.add(ChatMessage("Coach", response.explanation))
            _chatHistory.value = newHistory

            nativeAutoCoachVoice.speakAndWait(response.explanation)

            if (wasListening) {
                startLiveTranscription()
            }

            if (response.exercises.isNotEmpty()) {
                val workoutId = _workoutId.value
                val suggestedNames = response.exercises.map { it.name.lowercase().trim() }

                val exercisesToReplace = _exerciseStates.value.filter { existing ->
                    val existingName = existing.exercise.name.lowercase().trim()
                    val isExplicitlyNamed = response.replacingExerciseName?.lowercase()?.trim() == existingName
                    val matchesSuggested = suggestedNames.any { it == existingName || it.contains(existingName) || existingName.contains(it) }
                    isExplicitlyNamed || matchesSuggested
                }

                exercisesToReplace.forEach { ex ->
                    val incompleteSets = ex.sets.filter { !it.isCompleted }
                    if (incompleteSets.isNotEmpty()) {
                        repository.deleteSets(incompleteSets)
                    }
                }

                val setsToInsert = response.exercises.mapNotNull { genEx ->
                    val match = available.find {
                        it.name.equals(genEx.name, ignoreCase = true) ||
                                it.name.contains(genEx.name, ignoreCase = true)
                    }

                    if (match != null) {
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
                                suggestedRpe = 8,
                                isAMRAP = genEx.isAMRAP,
                                isEMOM = genEx.isEMOM
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

        if (lowerText.contains("clear coach memory") || lowerText.contains("reset coach memory") || lowerText.contains("forget everything")) {
            clearCoachMemories()
            addCoachResponse("Done. I've cleared my memory of any previous injuries or preferences. We're starting fresh.")
            return true
        }

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

                    // If done via voice, check if they mentioned an injury
                    val isPermanent = lowerText.contains("pain") || lowerText.contains("hurt") || lowerText.contains("injury") || lowerText.contains("bother")

                    val coachMsg = if (isPermanent) {
                        "Got it. I've noted the limitation. Swapping ${exerciseToSwap.exercise.name} for ${bestAlt.name} for today and future weeks."
                    } else {
                        "Swapping ${exerciseToSwap.exercise.name} for ${bestAlt.name}. It targets the same muscle groups."
                    }

                    addCoachResponse(coachMsg)
                    swapExercise(exerciseToSwap.exercise.exerciseId, bestAlt.exerciseId, isPermanent, exerciseToSwap.exercise.name, bestAlt.name)
                    return true
                }
            }
        }

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

    fun clearCoachMemories() {
        viewModelScope.launch {
            memoryDao.deleteAllMemories()
            _coachBriefing.value = "Loading briefing..."
        }
    }

    private fun addCoachResponse(text: String) {
        val newHistory = _chatHistory.value.toMutableList()
        newHistory.add(ChatMessage("Coach", text))
        _chatHistory.value = newHistory
        viewModelScope.launch {
            nativeAutoCoachVoice.speakAndWait(text)
        }
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

    // --- 8. LIVE VOICE COACHING ---

    fun toggleLiveCoaching() {
        if (isListening.value) {
            stopLiveTranscription()
        } else {
            startLiveTranscription()
        }
    }

    private fun startLiveTranscription() {
        transcribeJob?.cancel()
        audioStreamer.startStreaming()

        transcribeJob = viewModelScope.launch {
            try {
                sttManager.startListeningForSingleUtterance()
                    .collect { transcript ->
                        Log.d("LiveCoach", "Heard: $transcript")
                        interactWithCoach(transcript)
                    }
            } catch (e: Exception) {
                Log.e("LiveCoach", "Speech error: ${e.message}")
                audioStreamer.stopStreaming()
            }
        }
    }

    private fun stopLiveTranscription() {
        transcribeJob?.cancel()
        transcribeJob = null
        voiceManager.stop()
        nativeAutoCoachVoice.shutdown()
        audioStreamer.stopStreaming()
        sttManager.shutdown()
    }

    // --- BLE HEART RATE ---

    fun startBleScan() {
        bleHeartRateManager.startScan()
    }

    fun stopBleScan() {
        bleHeartRateManager.stopScan()
    }

    fun connectBleDevice(device: BluetoothDevice) {
        bleHeartRateManager.connectToDevice(device)
    }

    fun disconnectBle() {
        bleHeartRateManager.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        stopLiveTranscription()
        voiceManager.stop()
        nativeAutoCoachVoice.shutdown()
        stopTimerService()
        autoCoachEngine.stop()
        bleHeartRateManager.cleanup()
    }
}