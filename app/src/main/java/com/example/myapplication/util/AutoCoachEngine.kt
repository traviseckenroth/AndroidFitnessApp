package com.example.myapplication.util

import android.util.Log
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.local.WorkoutSetEntity
import com.example.myapplication.data.local.UserPreferencesRepository
import com.example.myapplication.data.remote.BedrockClient
import com.example.myapplication.data.remote.Message
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

enum class AutoCoachState {
    OFF, SPEAKING, LISTENING, RESTING, SET_IN_PROGRESS
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

    fun startWorkout(
        exercises: List<Pair<ExerciseEntity, List<WorkoutSetEntity>>>,
        onUpdateReps: (WorkoutSetEntity, String) -> Unit,
        onUpdateWeight: (WorkoutSetEntity, String) -> Unit,
        onSetCompleted: (WorkoutSetEntity) -> Unit,
        onStartTimer: (Long) -> Unit
    ) {
        if (_state.value != AutoCoachState.OFF) return

        chatHistory.clear()
        // streamer.connect() // Handled dynamically now by startNewGeneration()

        job = scope.launch {
            try {
                // Wait briefly for initial setup
                delay(500)

                // 1. Initial Greeting
                pushContextAndSpeak("The user just started their workout. Give a quick, hype intro.")

                for ((exercise, sets) in exercises) {
                    for ((index, set) in sets.withIndex()) {
                        if (set.isCompleted) continue

                        val weight = (set.actualLbs ?: set.suggestedLbs.toFloat())
                        val reps = set.actualReps ?: set.suggestedReps

                        // 2. Dynamic Set Intro
                        pushContextAndSpeak("We are on exercise: ${exercise.name}. Set ${index + 1} of ${sets.size}. The goal is $reps reps at $weight lbs. Give a quick form cue and tell them to start.")

                        _state.value = AutoCoachState.SET_IN_PROGRESS
                        delay((reps * 3500L)) // Simulated set duration (3.5s per rep)

                        // 3. Post-Set Conversation
                        pushContextAndSpeak("User should be done with the set. Ask how the weight felt or if they hit the reps.")

                        // 4. Live Listening
                        listenAndRespond(set, exercise, weight, onUpdateReps, onUpdateWeight)

                        // 5. Update UI & Rest
                        onSetCompleted(set)
                        onStartTimer(exercise.exerciseId)

                        if (index < sets.lastIndex) {
                            handleRestPeriod()
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

    private suspend fun listenAndRespond(
        set: WorkoutSetEntity,
        exercise: ExerciseEntity,
        weight: Float,
        onUpdateReps: (WorkoutSetEntity, String) -> Unit,
        onUpdateWeight: (WorkoutSetEntity, String) -> Unit
    ) {
        _state.value = AutoCoachState.LISTENING
        val response = sttManager.listenForResponse(timeoutMillis = 5000).lowercase()

        if (response.isBlank()) {
            chatHistory.add(Message(role = "user", content = "[User stayed silent]"))
            return
        }

        Log.d("AutoCoach", "User said: $response")

        // Basic offline logic for UI updates
        if (response.contains("too heavy") || response.contains("lower")) {
            onUpdateWeight(set, (weight - 5).toString())
        }

        // Push user's speech to LLM Memory
        chatHistory.add(Message(role = "user", content = response))

        // Let the LLM answer contextually via streaming
        _state.value = AutoCoachState.SPEAKING

        try {
            // Wake up ElevenLabs for a new generation
            streamer.startNewGeneration()

            val aiResponse = bedrockClient.streamConversationalResponse(chatHistory) { textChunk ->
                streamer.streamTextChunk(textChunk)
            }
            streamer.flush()
            chatHistory.add(Message(role = "assistant", content = aiResponse))

            // Wait for audio to finish (approx 60ms per character)
            delay((aiResponse.length * 60L).coerceAtLeast(2000L))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("AutoCoach", "Failed to get AI response in listenAndRespond", e)
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
        chatHistory.add(Message(role = "user", content = "[SYSTEM CONTEXT]: $systemContext"))

        try {
            // Wake up ElevenLabs for a new generation
            streamer.startNewGeneration()

            val fullResponse = bedrockClient.streamConversationalResponse(chatHistory) { textChunk ->
                streamer.streamTextChunk(textChunk)
            }
            streamer.flush()

            chatHistory.add(Message(role = "assistant", content = fullResponse))
            Log.d("AutoCoach", "Coach said: $fullResponse")

            // Wait for the audio to actually finish playing
            delay((fullResponse.length * 60L).coerceAtLeast(2000L))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("AutoCoach", "Failed to get AI response in pushContextAndSpeak", e)
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
