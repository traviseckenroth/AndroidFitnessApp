// app/src/main/java/com/example/myapplication/util/AutoCoachEngine.kt
package com.example.myapplication.util

import android.util.Log
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.local.WorkoutSetEntity
import com.example.myapplication.data.local.UserPreferencesRepository
import com.example.myapplication.data.local.MemoryDao
import com.example.myapplication.data.remote.BedrockClient
import com.example.myapplication.data.remote.Message
import com.example.myapplication.service.WorkoutTimerService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import javax.inject.Inject

enum class AutoCoachState {
    OFF, SPEAKING, LISTENING, RESTING, SET_IN_PROGRESS
}

class AutoCoachEngine @Inject constructor(
    private val streamer: ElevenLabsStreamer,
    private val voiceManager: VoiceManager,
    private val sherpaVoice: NativeAutoCoachVoice,
    private val sttManager: SpeechToTextManager,
    private val bleHeartRateManager: BleHeartRateManager,
    private val bedrockClient: BedrockClient,
    private val userPrefs: UserPreferencesRepository,
    private val memoryDao: MemoryDao
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var job: Job? = null

    private val _state = MutableStateFlow(AutoCoachState.OFF)
    val state: StateFlow<AutoCoachState> = _state

    private val chatHistory = mutableListOf<Message>()
    private val setCompletionSignal = MutableSharedFlow<Unit>()

    // Live Workout State variables for the JSON payload
    private var currentExerciseName = ""
    private var currentSetString = ""
    private var currentTarget = ""

    private val TESTING_SHERPA = true

    fun notifySetCompletedManually() {
        scope.launch { setCompletionSignal.emit(Unit) }
    }

    fun startWorkout(
        exercises: List<Pair<ExerciseEntity, List<WorkoutSetEntity>>>,
        onUpdateReps: (WorkoutSetEntity, String) -> Unit,
        onUpdateWeight: (WorkoutSetEntity, String) -> Unit,
        onSetCompleted: (WorkoutSetEntity) -> Unit,
        onStartTimer: (Long, Boolean) -> Unit,
        onExtendTimer: ((Long) -> Unit)? = null,
        onWorkoutCompleted: (() -> Unit)? = null
    ) {
        if (_state.value != AutoCoachState.OFF) return
        chatHistory.clear()

        job = scope.launch {
            try {
                // Set the user's preferred voice ID
                val voiceSid = userPrefs.userVoiceSid.first()
                sherpaVoice.currentVoiceSid = voiceSid

                delay(500)

                // OPTIMIZED PERSONA: Focus on natural, fluid, human-like conversation.
                chatHistory.add(Message(role = "system", content = """
                    You are 'Forma', an elite, empathetic, and highly conversational AI personal trainer. 
                    Speak exactly like a real human trainer standing right next to the user in the gym.
                    Vary your vocabulary. Be punchy, natural, and fluid. Do not sound robotic or repetitive.
                    Keep responses brief (1-3 sentences maximum). NEVER simulate or predict the user's responses.
                    If you need input, end with a natural, conversational question. Otherwise, casually guide them.
                """.trimIndent()))

                pushContextAndSpeak("We are just kicking off the workout. Give a highly energetic, natural, 1-to-2 sentence intro to get them hyped up.")

                for ((exerciseIndex, pair) in exercises.withIndex()) {
                    val (exercise, sets) = pair
                    currentExerciseName = exercise.name
                    val isBodyweight = exercise.equipment?.contains("Bodyweight", ignoreCase = true) == true

                    if (exerciseIndex > 0) {
                        pushContextAndSpeak("We are moving to a new exercise. Tell the user to head over to the $currentExerciseName. Keep it casual and tell them to take their time getting set up.")
                        _state.value = AutoCoachState.RESTING
                        delay(20000)
                    }

                    for ((index, set) in sets.withIndex()) {
                        if (set.isCompleted) continue

                        val weight = (set.actualLbs ?: set.suggestedLbs.toFloat())
                        val reps = set.actualReps ?: set.suggestedReps
                        currentSetString = "Set ${index + 1} of ${sets.size}"
                        currentTarget = if (isBodyweight) "$reps reps" else "$weight lbs x $reps reps"

                        // OPTIMIZED WORKFLOW: Fluid pre-set cues without strict "say this" formatting.
                        if (index == 0) {
                            pushContextAndSpeak("We're about to start the first set of $currentExerciseName. The target is $currentTarget. Give a brief, helpful form cue and seamlessly transition into hyping them up to start.")
                        } else {
                            pushContextAndSpeak("We are on $currentSetString. The target is $currentTarget. Give a quick, varied word of encouragement to kick off the set.")
                        }

                        // Immediately start the set
                        onStartTimer(exercise.exerciseId, false)
                        _state.value = AutoCoachState.SET_IN_PROGRESS

                        var setFailedOrBailed = false

                        val setJob = launch { setCompletionSignal.first() }
                        val listenJob = launch {
                            while (isActive) {
                                val speech = sttManager.listenForResponse(maxTimeoutMillis = 4000).lowercase()
                                if (speech.contains("bail") || speech.contains("failed") || speech.contains("help")) {
                                    setFailedOrBailed = true
                                    setCompletionSignal.emit(Unit)
                                    break
                                }
                                delay(2000)
                            }
                        }

                        setJob.join()
                        listenJob.cancel()

                        // OPTIMIZED POST-SET: Natural check-ins.
                        if (setFailedOrBailed) {
                            val failPrompt = if (isBodyweight) {
                                "The user had to bail or stop the set early. Be highly supportive and empathetic. Check if they are okay and casually suggest taking a longer rest."
                            } else {
                                "The user had to bail or stop the set early. Be highly supportive and empathetic. Check if they are okay and casually suggest dropping the weight for the next set."
                            }
                            pushContextAndSpeak(failPrompt)
                        } else {
                            val successPrompt = if (isBodyweight) {
                                "The user just finished the set. Acknowledge the completion naturally. Ask a quick, casual question about how the intensity felt to check in on them."
                            } else {
                                "The user just finished the set. Acknowledge the completion naturally. Ask a quick, casual question about how the weight felt to check in on them."
                            }
                            pushContextAndSpeak(successPrompt)
                        }

                        listenAndRespond(set, exercise, weight, onUpdateWeight, onExtendTimer)
                        onSetCompleted(set)

                        if (index < sets.lastIndex) {
                            handleRestPeriod()
                        }
                    }
                }

                pushContextAndSpeak("The workout is officially complete! Give a very quick, warm congratulations and ask how the session felt overall.")
                listenAndRespond(exercises.last().second.last(), exercises.last().first, 0f,
                    onUpdateWeight, onExtendTimer)

                pushContextAndSpeak("Awesome work today. Take care and I'll see you at our next session!")
                _state.value = AutoCoachState.OFF
                streamer.disconnect()
                sherpaVoice.shutdown()
                onWorkoutCompleted?.invoke()

            } catch (e: CancellationException) {
                streamer.disconnect()
                sherpaVoice.shutdown()
            } catch (e: Exception) {
                Log.e("AutoCoach", "Engine error", e)
                _state.value = AutoCoachState.OFF
                streamer.disconnect()
                sherpaVoice.shutdown()
            }
        }
    }

    private suspend fun listenAndRespond(
        set: WorkoutSetEntity,
        exercise: ExerciseEntity,
        weight: Float,
        onUpdateWeight: (WorkoutSetEntity, String) -> Unit,
        onExtendTimer: ((Long) -> Unit)?
    ) {
        _state.value = AutoCoachState.LISTENING
        val response = sttManager.listenForResponse(maxTimeoutMillis = 6000).lowercase()

        if (response.isBlank()) {
            chatHistory.add(Message(role = "user", content = "[User stayed silent. Proceed naturally based on the workout plan.]"))
            return
        }

        val isBodyweight = exercise.equipment?.contains("Bodyweight", ignoreCase = true) == true

        if (!isBodyweight) {
            if (response.contains("too heavy") || response.contains("lower") || response.contains("drop") || response.contains("down") || response.contains("decrease") || response.contains("lighter")) {
                val match = Regex("(\\d+)").find(response)
                val amount = match?.value?.toFloat() ?: (weight * 0.1f)
                val newWeight = weight - amount
                onUpdateWeight(set, newWeight.toString())
            }

            if (response.contains("too light") || response.contains("higher") || response.contains("increase") || response.contains("up") || response.contains("add") || response.contains("heavier")) {
                val match = Regex("(\\d+)").find(response)
                val amount = match?.value?.toFloat() ?: 10f
                val newWeight = weight + amount
                onUpdateWeight(set, newWeight.toString())
            }
        }

        if (response.contains("rpe 10") || response.contains("rpe 9") || response.contains("extremely hard")) {
            onExtendTimer?.invoke(60000L)
        }

        chatHistory.add(Message(role = "user", content = response))

        scope.launch {
            try {
                val memory = bedrockClient.extractUserMemory(response)
                if (memory != null) {
                    memoryDao.insertMemory(memory)
                    Log.d("RAG", "Live Coach saved new memory: ${memory.note}")
                }
            } catch (e: Exception) { }
        }

        triggerAgenticResponse()
    }

    private suspend fun handleRestPeriod() {
        _state.value = AutoCoachState.RESTING
        if (bleHeartRateManager.isConnected.value) {
            val currentHR = bleHeartRateManager.heartRate.value
            val age = userPrefs.userAge.first()
            val targetHR = ((220 - age) * 0.60).toInt()

            pushContextAndSpeak("The user is resting. Let them know their heart rate is currently $currentHR BPM, and tell them to casually focus on their breathing until it drops to around $targetHR BPM.")

            val proactiveJob = scope.launch {
                bleHeartRateManager.heartRate.collect { hr ->
                    if (hr <= targetHR + 5 && _state.value == AutoCoachState.RESTING) {
                        pushContextAndSpeak("Your heart rate is dropping beautifully. Feel free to skip the rest of the timer if you feel ready.")
                        cancel()
                    }
                }
            }

            withTimeoutOrNull(120000L) {
                bleHeartRateManager.heartRate.first { it in 1..targetHR }
            }
            proactiveJob.cancel()
            pushContextAndSpeak("Their heart rate has successfully dropped to the target zone. Casually let them know they are recovered and it's time to get ready for the next set.")
        } else {
            WorkoutTimerService.timerState.filter { it.hasFinished || (!it.isRunning && it.remainingTime == 0) }.first()
            pushContextAndSpeak("The rest timer is up. Transition naturally to the next set.")
        }
    }

    private suspend fun pushContextAndSpeak(systemPrompt: String) {
        val memories = memoryDao.getRecentMemories(10)
        val memoryContext = if (memories.isEmpty()) {
            "No known physical limitations or preferences."
        } else {
            memories.joinToString("\n") { "- ${it.category}: ${it.note}" }
        }

        // OPTIMIZED STATE INJECTION: Softer constraints for a more conversational tone
        val statePayload = """
            [SYSTEM CONTEXT - DO NOT READ ALOUD]
            Current Workout State:
            - Active Exercise: $currentExerciseName
            - Progress: $currentSetString
            - Target: $currentTarget
            - Live Heart Rate: ${if (bleHeartRateManager.isConnected.value) "${bleHeartRateManager.heartRate.value} BPM" else "Not Tracked"}
            
            User Health Notes & Memories:
            $memoryContext
            
            Your Directives: $systemPrompt
            (Note: Keep in mind the User Health Notes. If anything conflicts directly with the active exercise, naturally suggest an alternative.)
        """.trimIndent()

        chatHistory.add(Message(role = "user", content = statePayload))
        triggerAgenticResponse()
    }

    private suspend fun triggerAgenticResponse() {
        _state.value = AutoCoachState.SPEAKING
        var interruptJob: Job? = null

        try {
            val aiResponse = bedrockClient.streamConversationalResponse(chatHistory) { }
            chatHistory.add(Message(role = "assistant", content = aiResponse))

            interruptJob = scope.launch {
                sttManager.volumeLevel.collect { dbLevel ->
                    if (dbLevel > 12.0f && _state.value == AutoCoachState.SPEAKING) {
                        Log.d("AutoCoach", "VAD TRIGGERED! User interrupted.")
                        sherpaVoice.interrupt()
                        chatHistory.add(Message(role = "system", content = "[The user interrupted you mid-sentence. Listen to what they say next and adapt naturally.]"))
                        this.coroutineContext.cancelChildren()
                    }
                }
            }

            if (TESTING_SHERPA) {
                sherpaVoice.speakAndWait(aiResponse)
            } else {
                voiceManager.speak(aiResponse)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("AutoCoach", "Failed to get AI response", e)
        } finally {
            interruptJob?.cancel()
        }
    }

    fun interrupt() {
        streamer.interrupt()
        sherpaVoice.interrupt()
        voiceManager.stop()
    }

    fun stopSpeaking() = interrupt()

    fun stop() {
        job?.cancel()
        streamer.disconnect()
        sherpaVoice.shutdown()
        voiceManager.stop()
        _state.value = AutoCoachState.OFF
    }
}