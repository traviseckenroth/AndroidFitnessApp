package com.example.myapplication.util

import android.util.Log
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.local.WorkoutSetEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

enum class AutoCoachState {
    OFF, SPEAKING, LISTENING, RESTING, SET_IN_PROGRESS
}

class AutoCoachEngine @Inject constructor(
    private val voice: NativeAutoCoachVoice,
    private val sttManager: SpeechToTextManager,
    private val bleHeartRateManager: BleHeartRateManager
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var job: Job? = null

    private val _state = MutableStateFlow(AutoCoachState.OFF)
    val state: StateFlow<AutoCoachState> = _state

    fun startWorkout(
        exercises: List<Pair<ExerciseEntity, List<WorkoutSetEntity>>>,
        onUpdateReps: (WorkoutSetEntity, String) -> Unit,
        onUpdateWeight: (WorkoutSetEntity, String) -> Unit,
        onSetCompleted: (WorkoutSetEntity) -> Unit,
        onStartTimer: (Long) -> Unit
    ) {
        if (_state.value != AutoCoachState.OFF) return

        job = scope.launch {
            try {
                _state.value = AutoCoachState.SPEAKING
                voice.speakAndWait("Auto coach activated. Let's get to work.")

                for ((exercise, sets) in exercises) {
                    for ((index, set) in sets.withIndex()) {
                        if (set.isCompleted) continue

                        // 1. Announce Set
                        _state.value = AutoCoachState.SPEAKING
                        val weight = set.actualLbs ?: set.suggestedLbs.toFloat()
                        val reps = set.actualReps ?: set.suggestedReps

                        if (index == 0) {
                            voice.speakAndWait("Next up, ${exercise.name}. We are doing $reps reps at $weight pounds. Take 30 seconds to set up.")
                            _state.value = AutoCoachState.RESTING
                            delay(30000) // 30 sec setup
                        }

                        // 2. Countdown & Set
                        _state.value = AutoCoachState.SPEAKING
                        voice.speakAndWait("Starting in 3. 2. 1. Go.")

                        _state.value = AutoCoachState.SET_IN_PROGRESS
                        // Wait an estimated amount of time for the set to finish (e.g., 4 secs per rep)
                        delay((reps * 4000L))

                        // 3. Post-Set Logging Interaction
                        _state.value = AutoCoachState.SPEAKING
                        voice.speakAndWait("Set done. Did you hit all $reps reps at $weight pounds?")

                        _state.value = AutoCoachState.LISTENING
                        // Listen to mic for 5 seconds
                        val response = sttManager.listenForResponse(timeoutMillis = 5000).lowercase()
                        Log.d("AutoCoach", "User said: $response")

                        _state.value = AutoCoachState.SPEAKING

                        // Basic local NLP to adjust UI fields based on speech
                        if (response.contains("no") || response.contains("only")) {
                            // Try to extract a number if they failed
                            val detectedNumber = response.replace(Regex("[^0-9]"), "")
                            if (detectedNumber.isNotBlank()) {
                                voice.speakAndWait("Got it, changing reps to $detectedNumber and logging.")
                                onUpdateReps(set, detectedNumber)
                            } else {
                                voice.speakAndWait("I didn't catch the number. I'll log it as prescribed, you can change it on the screen.")
                            }
                        } else if (response.contains("too heavy") || response.contains("lower")) {
                            voice.speakAndWait("Alright, dropping the weight by 10 pounds for the next set.")
                            onUpdateWeight(set, (weight - 10).toString())
                        } else {
                            voice.speakAndWait("Perfect. Logging it.")
                        }

                        // 4. Update UI & Start Rest Timer
                        onSetCompleted(set) // Checks the box
                        onStartTimer(exercise.exerciseId) // Starts the UI timer

                        // 5. Announce Rest with Live HR Monitoring
                        if (index < sets.lastIndex) {
                            voice.speakAndWait("Take a break. I'll watch your heart rate and let you know when you're recovered.")
                            _state.value = AutoCoachState.RESTING
                            
                            // Dynamic recovery: Wait for HR < 115 or 2.5 min timeout
                            withTimeoutOrNull(150000L) {
                                bleHeartRateManager.heartRate.first { it in 1..114 }
                            }
                            
                            _state.value = AutoCoachState.SPEAKING
                            voice.speakAndWait("Heart rate recovered. Let's hit the next set.")
                        }
                    }

                    _state.value = AutoCoachState.SPEAKING
                    voice.speakAndWait("All sets complete for ${exercise.name}.")
                }

                voice.speakAndWait("Workout complete. Incredible job today.")
                _state.value = AutoCoachState.OFF

            } catch (e: CancellationException) {
                voice.speakAndWait("Auto coach paused.")
            }
        }
    }

    fun stop() {
        job?.cancel()
        _state.value = AutoCoachState.OFF
    }
}