package com.example.myapplication.util

import android.util.Log
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.local.WorkoutSetEntity
import com.example.myapplication.data.local.UserPreferencesRepository
import com.example.myapplication.data.remote.BedrockClient
import com.example.myapplication.data.remote.Message
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

enum class AutoCoachState {
    OFF, SPEAKING, LISTENING, RESTING, SET_IN_PROGRESS, WAITING_FOR_READY
}

class AutoCoachEngine @Inject constructor(
    private val streamer: ElevenLabsStreamer,
    private val sttManager: SpeechToTextManager,
    private val bleHeartRateManager: BleHeartRateManager,
    private val bedrockClient: BedrockClient,
    private val userPrefs: UserPreferencesRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var job: Job? = null

    private val _state = MutableStateFlow(AutoCoachState.OFF)
    val state: StateFlow<AutoCoachState> = _state

    private val chatHistory = mutableListOf<Message>()
    private val setCompletionSignal = MutableSharedFlow<Unit>()
    private val readySignal = MutableSharedFlow<Unit>()

    fun notifySetCompletedManually() {
        scope.launch { setCompletionSignal.emit(Unit) }
    }
    
    fun notifyUserReadyManually() {
        scope.launch { readySignal.emit(Unit) }
    }

    fun startWorkout(
        exercises: List<Pair<ExerciseEntity, List<WorkoutSetEntity>>>,
        onUpdateReps: (WorkoutSetEntity, String) -> Unit,
        onUpdateWeight: (WorkoutSetEntity, String) -> Unit,
        onSetCompleted: (WorkoutSetEntity) -> Unit,
        onStartTimer: (Long) -> Unit,
        onExtendTimer: ((Long) -> Unit)? = null, // Added for Rest Auto-Regulation
        historicalData: Map<Long, String> = emptyMap() // Added for PR Recognition
    ) {
        if (_state.value != AutoCoachState.OFF) return

        chatHistory.clear()

        job = scope.launch {
            try {
                delay(500)

                // 1. Initial Greeting
                pushContextAndSpeak("The user just started their workout. Give a quick, hype intro.")

                for ((exerciseIndex, pair) in exercises.withIndex()) {
                    val (exercise, sets) = pair

                    // FEATURE: Setup Time Buffers
                    if (exerciseIndex > 0) {
                        pushContextAndSpeak("Tell the user to head over to the next exercise: ${exercise.name}. Tell them to take their time getting set up. Do NOT ask if they are ready yet.")
                        _state.value = AutoCoachState.RESTING
                        delay(20000) // 20-second physical setup buffer for gym transit
                    }

                    for ((index, set) in sets.withIndex()) {
                        if (set.isCompleted) continue

                        val weight = (set.actualLbs ?: set.suggestedLbs.toFloat())
                        val reps = set.actualReps ?: set.suggestedReps
                        val historyStr = historicalData[exercise.exerciseId] ?: "No previous data."

                        // FEATURE: Historical Context & Concise Form Cues
                        pushContextAndSpeak("We are on exercise: ${exercise.name}. Set ${index + 1} of ${sets.size}. The goal is $reps reps at $weight lbs. History: $historyStr. If the weight is a jump from history, congratulate them on the PR! Give ONE concise biomechanical form cue. Then ask if they are ready.")

                        _state.value = AutoCoachState.WAITING_FOR_READY
                        // Wait for manual signal or voice command "I'm ready"
                        // For now we just proceed to prevent blocking if voice isn't working perfectly
                        // Ideally we would wait: readySignal.first()

                        _state.value = AutoCoachState.SET_IN_PROGRESS
                        var setFailedOrBailed = false

                        // FEATURE: Seamless Interruption & Emergency Bail Commands
                        val setJob = launch {
                            withTimeoutOrNull(reps * 6000L) { // Max fallback timeout
                                setCompletionSignal.first()
                            }
                        }

                        val listenJob = launch {
                            while (isActive) {
                                val speech = sttManager.listenForResponse(maxTimeoutMillis = 5000).lowercase()
                                if (speech.isBlank()) continue

                                if (speech.contains("pause workout") || speech.contains("pause the workout")) {
                                    pushContextAndSpeak("Workout paused. Take your time. Say 'I'm back' when you are ready to resume.")
                                    while (isActive) {
                                        val resumeSpeech = sttManager.listenForResponse(maxTimeoutMillis = 5000).lowercase()
                                        if (resumeSpeech.contains("i'm back") || resumeSpeech.contains("resume")) {
                                            pushContextAndSpeak("Welcome back. Let's finish this set.")
                                            break
                                        }
                                    }
                                } else if (speech.contains("bail") || speech.contains("failed") || speech.contains("help")) {
                                    setFailedOrBailed = true
                                    setCompletionSignal.emit(Unit) // Immediately end the set
                                    break
                                }
                            }
                        }

                        setJob.join() // Suspends until the user completes the set or bails
                        listenJob.cancel() // Stop listening for interrupts

                        // 3. Post-Set Conversation
                        if (setFailedOrBailed) {
                            pushContextAndSpeak("The user just used an emergency bail command or failed the set safely. Immediately ask if they are okay, log the incomplete reps, and autonomously ask if they want to drop the weight by 10% for the next set.")
                        } else {
                            pushContextAndSpeak("User finished the set. Ask how the weight felt or if they hit the reps. If they say it was an RPE 9.5 or 10, autonomously suggest extending the rest timer to recover their central nervous system.")
                        }

                        // 4. Live Listening
                        listenAndRespond(set, exercise, weight, onUpdateReps, onUpdateWeight, onExtendTimer)

                        // 5. Update UI & Rest
                        onSetCompleted(set)
                        onStartTimer(exercise.exerciseId)

                        if (index < sets.lastIndex) {
                            handleRestPeriod()
                        }
                    }
                    pushContextAndSpeak("User finished all sets of ${exercise.name}. Acknowledge it briefly.")
                }

                // FEATURE: Session Debrief
                pushContextAndSpeak("The workout is complete! Provide a quick 10-second verbal summary of the session's effort. Ask: 'How did this session feel overall?' to log their subjective fatigue score.")
                listenAndRespond(exercises.last().second.last(), exercises.last().first, 0f, onUpdateReps, onUpdateWeight, onExtendTimer)

                pushContextAndSpeak("Great work today. Your feedback is logged. See you next time!")
                _state.value = AutoCoachState.OFF
                streamer.disconnect()

            } catch (e: CancellationException) {
                streamer.disconnect()
            } catch (e: Exception) {
                Log.e("AutoCoach", "Engine error", e)
                _state.value = AutoCoachState.OFF
                streamer.disconnect()
            }
        }
    }

    private suspend fun listenAndRespond(
        set: WorkoutSetEntity,
        exercise: ExerciseEntity,
        weight: Float,
        onUpdateReps: (WorkoutSetEntity, String) -> Unit,
        onUpdateWeight: (WorkoutSetEntity, String) -> Unit,
        onExtendTimer: ((Long) -> Unit)?
    ) {
        _state.value = AutoCoachState.LISTENING
        val response = sttManager.listenForResponse(maxTimeoutMillis = 6000).lowercase()

        if (response.isBlank()) {
            chatHistory.add(Message(role = "user", content = "[User stayed silent]"))
            return
        }

        Log.d("AutoCoach", "User said: $response")

        // Offline logic for UI updates based on conversation
        if (response.contains("too heavy") || response.contains("lower") || response.contains("drop")) {
            val newWeight = (weight * 0.9f) // Autonomously drop weight by 10%
            onUpdateWeight(set, newWeight.toString())
        }

        // FEATURE: Dynamic Rest Auto-Regulation
        if (response.contains("rpe 10") || response.contains("rpe 9") || response.contains("extremely hard")) {
            onExtendTimer?.invoke(60000L) // Adds 60 seconds to the UI timer
        }

        chatHistory.add(Message(role = "user", content = response))
        _state.value = AutoCoachState.SPEAKING

        var interruptJob: Job? = null

        try {
            streamer.startNewGeneration()

            val aiResponse = bedrockClient.streamConversationalResponse(chatHistory) { textChunk ->
                streamer.streamTextChunk(textChunk)
            }
            streamer.flush()
            chatHistory.add(Message(role = "assistant", content = aiResponse))

            val estimatedAudioDurationMs = (aiResponse.length * 60L).coerceAtLeast(1500L)

            // VAD Interruption Monitor (lets user cut off the coach)
            interruptJob = scope.launch {
                sttManager.volumeLevel.collect { dbLevel ->
                    if (dbLevel > 5.0f) {
                        Log.d("AutoCoach", "VAD TRIGGERED! User interrupted coach (dB: $dbLevel)")
                        streamer.interrupt()
                        this.coroutineContext.cancelChildren()
                    }
                }
            }

            withTimeoutOrNull(estimatedAudioDurationMs) {
                while(isActive) { delay(100) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("AutoCoach", "Failed to get AI response", e)
        } finally {
            interruptJob?.cancel()
        }
    }

    private suspend fun handleRestPeriod() {
        _state.value = AutoCoachState.RESTING
        if (bleHeartRateManager.isConnected.value) {
            val currentHR = bleHeartRateManager.heartRate.value
            val age = userPrefs.userAge.first()
            val targetHR = ((220 - age) * 0.60).toInt()

            pushContextAndSpeak("Tell the user their HR is $currentHR and we are resting until it drops to $targetHR.")

            withTimeoutOrNull(120000L) {
                bleHeartRateManager.heartRate.first { it in 1..targetHR }
            }
            pushContextAndSpeak("HR has recovered to target. Tell them it's time for the next set.")
        } else {
            delay(30000)
            pushContextAndSpeak("Rest time is up. Ask if they are ready.")
        }
    }

    private suspend fun pushContextAndSpeak(systemContext: String) {
        _state.value = AutoCoachState.SPEAKING

        // Anti-Bloat: Remove previous system contexts to save tokens and prevent confusion
        chatHistory.removeAll { it.role == "user" && it.content.startsWith("[SYSTEM CONTEXT]") }
        chatHistory.add(Message(role = "user", content = "[SYSTEM CONTEXT]: $systemContext"))

        var interruptJob: Job? = null

        try {
            streamer.startNewGeneration()

            val fullResponse = bedrockClient.streamConversationalResponse(chatHistory) { textChunk ->
                streamer.streamTextChunk(textChunk)
            }
            streamer.flush()

            chatHistory.add(Message(role = "assistant", content = fullResponse))
            Log.d("AutoCoach", "Coach said: $fullResponse")

            val estimatedAudioDurationMs = (fullResponse.length * 60L).coerceAtLeast(1500L)

            // VAD Interruption Monitor
            interruptJob = scope.launch {
                sttManager.volumeLevel.collect { dbLevel ->
                    if (dbLevel > 5.0f) {
                        Log.d("AutoCoach", "VAD TRIGGERED! User interrupted coach (dB: $dbLevel)")
                        streamer.interrupt()
                        this.coroutineContext.cancelChildren()
                    }
                }
            }

            withTimeoutOrNull(estimatedAudioDurationMs) {
                while(isActive) { delay(100) }
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
    }

    fun stopSpeaking() {
        interrupt()
    }

    fun stop() {
        job?.cancel()
        streamer.disconnect()
        _state.value = AutoCoachState.OFF
    }
}