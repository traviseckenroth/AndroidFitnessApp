package com.example.myapplication.util

import android.util.Log
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.local.WorkoutSetEntity
import com.example.myapplication.data.local.UserPreferencesRepository
import com.example.myapplication.data.remote.BedrockClient
import com.example.myapplication.data.remote.Message
import com.example.myapplication.service.WorkoutTimerService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
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
    private val userReadySignal = MutableSharedFlow<Unit>()

    fun notifySetCompletedManually() {
        Log.d("AutoCoach", "Manual set completion received")
        scope.launch { setCompletionSignal.emit(Unit) }
    }

    fun notifyUserReadyManually() {
        Log.d("AutoCoach", "Manual user ready received")
        scope.launch { userReadySignal.emit(Unit) }
    }

    fun startWorkout(
        exercises: List<Pair<ExerciseEntity, List<WorkoutSetEntity>>>,
        onUpdateReps: (WorkoutSetEntity, String) -> Unit,
        onUpdateWeight: (WorkoutSetEntity, String) -> Unit,
        onUpdateRpe: (WorkoutSetEntity, String) -> Unit,
        onSetCompleted: (WorkoutSetEntity) -> Unit,
        onStartTimer: (Long) -> Unit,
        onGenericIntent: (String) -> Unit
    ) {
        if (_state.value != AutoCoachState.OFF) return

        chatHistory.clear()

        job = scope.launch {
            try {
                delay(500)

                // 1. Initial Greeting
                pushContextAndSpeak("The user just started their workout. Give a quick, hype intro.")

                for ((exercise, sets) in exercises) {
                    
                    // --- REQ 5: Ensure user is ready for the new exercise ---
                    val firstSet = sets.firstOrNull()
                    if (firstSet != null && !firstSet.isCompleted) {
                        pushContextAndSpeak("We are starting ${exercise.name}. Ask the user if they are set up and ready to begin.")
                        _state.value = AutoCoachState.WAITING_FOR_READY
                        waitForReadyOrTimeout()
                    }

                    for ((index, set) in sets.withIndex()) {
                        if (set.isCompleted) continue

                        val weight = (set.actualLbs ?: set.suggestedLbs.toFloat())
                        val reps = set.actualReps ?: set.suggestedReps
                        val rpe = set.actualRpe ?: set.suggestedRpe

                        // --- REQ 4: Relay Workout Info (Reps, Weight, RPE) ---
                        // 2. Dynamic Set Intro
                        pushContextAndSpeak("Set ${index + 1} of ${sets.size} for ${exercise.name}. Target: $reps reps, $weight lbs, RPE $rpe. Give a quick form cue and say 'GO' to start.")

                        // --- REQ 2: Control Screen (Start "Timer" logic if implied, or just set status) ---
                        _state.value = AutoCoachState.SET_IN_PROGRESS

                        // --- REQ 2 & 3: Wait for completion (Voice or UI) ---
                        // "auto Done check when the set timer is complete" -> modeled as timeout or voice "Done"
                        waitForCompletionOrTimeout(reps * 15000L) // 15s per rep max wait

                        // 3. Post-Set Conversation
                        pushContextAndSpeak("Set done. Ask specifically how the $weight lbs felt or if they hit the $reps reps.")

                        // 4. Live Listening & Stats Update
                        listenAndRespond(set, exercise, weight, onUpdateReps, onUpdateWeight, onUpdateRpe, onGenericIntent)

                        // 5. Update UI & Rest
                        onSetCompleted(set)
                        
                        // --- REQ 2: Coach starts the timer ---
                        onStartTimer(exercise.exerciseId)

                        if (index < sets.lastIndex) {
                            handleRestPeriod(exercise.exerciseId)
                        }
                    }
                    pushContextAndSpeak("User finished all sets of ${exercise.name}. Acknowledge it briefly.")
                }

                pushContextAndSpeak("Workout complete. Give a quick, proud sign off.")
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

    private suspend fun waitForReadyOrTimeout() {
        try {
            withTimeout(30000) { // Wait 30s for ready
                val manualSignal = async { userReadySignal.first() }
                val voiceSignal = async {
                    while (isActive) {
                        try {
                            _state.value = AutoCoachState.LISTENING
                            val result = sttManager.listenForResponse(4000)
                            _state.value = AutoCoachState.WAITING_FOR_READY
                            
                            val lower = result.lowercase()
                            if (lower.contains("yes") || lower.contains("ready") || lower.contains("good") || lower.contains("start")) {
                                break
                            }
                        } catch (e: Exception) { delay(200) }
                    }
                }
                
                select<Unit> {
                    manualSignal.onAwait { }
                    voiceSignal.onAwait { }
                }
                manualSignal.cancel()
                voiceSignal.cancel()
            }
        } catch (e: TimeoutCancellationException) {
            pushContextAndSpeak("Assuming you're ready. Let's get to it.")
        }
    }

    private suspend fun waitForCompletionOrTimeout(timeoutMs: Long) {
         try {
             withTimeout(timeoutMs) {
                 val manualSignal = async { setCompletionSignal.first() }
                 val voiceSignal = async {
                     while (isActive) {
                         // Continuous listening loop during set
                         try {
                             _state.value = AutoCoachState.LISTENING // Show listening state
                             val result = sttManager.listenForResponse(3000)
                             _state.value = AutoCoachState.SET_IN_PROGRESS // Revert to in-progress if not done
                             
                             if (isCompletionCommand(result)) {
                                 break
                             }
                         } catch (e: Exception) {
                             delay(200)
                         }
                     }
                 }
                 
                 select<Unit> {
                     manualSignal.onAwait { }
                     voiceSignal.onAwait { }
                 }
                 
                 manualSignal.cancel()
                 voiceSignal.cancel()
             }
         } catch (e: TimeoutCancellationException) {
             Log.d("AutoCoach", "Set time limit reached, assuming done.")
         }
    }

    private fun isCompletionCommand(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("done") || lower.contains("finished") || lower.contains("complete") || lower.contains("next") || lower.contains("ready") || lower.contains("yep") || lower.contains("yeah")
    }

    private suspend fun listenAndRespond(
        set: WorkoutSetEntity,
        exercise: ExerciseEntity,
        weight: Float,
        onUpdateReps: (WorkoutSetEntity, String) -> Unit,
        onUpdateWeight: (WorkoutSetEntity, String) -> Unit,
        onUpdateRpe: (WorkoutSetEntity, String) -> Unit,
        onGenericIntent: (String) -> Unit
    ) {
        _state.value = AutoCoachState.LISTENING
        val response = sttManager.listenForResponse(maxTimeoutMillis = 6000).lowercase()

        if (response.isBlank()) {
            chatHistory.add(Message(role = "user", content = "[User stayed silent]"))
            return
        }

        Log.d("AutoCoach", "User said: $response")

        // --- REQ 6: Update Stats Logic ---
        if (response.contains("heavy") || response.contains("lower")) {
            onUpdateWeight(set, (weight - 5).toString())
        }
        if (response.contains("light") || response.contains("raise") || response.contains("more weight")) {
            onUpdateWeight(set, (weight + 5).toString())
        }
        
        // --- REQ 7: Swap / Generic Intent ---
        if (response.contains("swap") || response.contains("change exercise") || response.contains("hurt") || response.contains("pain")) {
            onGenericIntent(response)
            // Wait for VM to handle it, but we continue flow or break?
            // For now, continue conversation
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

            val estimatedAudioDurationMs = (aiResponse.length * 70L).coerceAtLeast(2000L)

            interruptJob = scope.launch {
                sttManager.volumeLevel.collect { dbLevel ->
                    if (dbLevel > 8.0f) { 
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
            Log.e("AutoCoach", "Failed to get AI response in listenAndRespond", e)
        } finally {
            interruptJob?.cancel()
        }
    }

    private suspend fun handleRestPeriod(exerciseId: Long) {
        _state.value = AutoCoachState.RESTING
        
        val timerState = WorkoutTimerService.timerState.value
        if (timerState.isRunning && timerState.activeExerciseId == exerciseId) {
             val remaining = timerState.remainingTime
             if (remaining > 0) {
                 pushContextAndSpeak("Rest timer started. $remaining seconds. Breath.")
             }
        }

        // Wait for timer to finish or at least 30s
        withTimeoutOrNull(180000L) { // Cap rest at 3 mins
            WorkoutTimerService.timerState.first { !it.isRunning || (it.activeExerciseId == exerciseId && it.hasFinished) }
        }
        
        pushContextAndSpeak("Rest time is up. Get ready for the next set.")
    }

    private suspend fun pushContextAndSpeak(systemContext: String) {
        _state.value = AutoCoachState.SPEAKING
        
        val timerState = WorkoutTimerService.timerState.value
        val timerContext = if (timerState.isRunning) {
            "[TIMER INFO]: Rest timer has ${timerState.remainingTime}s left."
        } else ""

        chatHistory.removeAll { it.role == "user" && it.content.startsWith("[SYSTEM CONTEXT]") }
        chatHistory.add(Message(role = "user", content = "[SYSTEM CONTEXT]: $systemContext $timerContext"))

        var interruptJob: Job? = null

        try {
            streamer.startNewGeneration()

            val fullResponse = bedrockClient.streamConversationalResponse(chatHistory) { textChunk ->
                streamer.streamTextChunk(textChunk)
            }
            streamer.flush()

            chatHistory.add(Message(role = "assistant", content = fullResponse))
            Log.d("AutoCoach", "Coach said: $fullResponse")

            val estimatedAudioDurationMs = (fullResponse.length * 70L).coerceAtLeast(2000L)

            interruptJob = scope.launch {
                sttManager.volumeLevel.collect { dbLevel ->
                    if (dbLevel > 8.0f) {
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
            Log.e("AutoCoach", "Failed to get AI response in pushContextAndSpeak", e)
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