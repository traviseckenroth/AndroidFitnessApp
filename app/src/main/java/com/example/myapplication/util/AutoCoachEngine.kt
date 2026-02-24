package com.example.myapplication.util

import android.util.Log
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.local.WorkoutSetEntity
import com.example.myapplication.data.local.UserPreferencesRepository
import com.example.myapplication.data.remote.BedrockClient
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
    private val bleHeartRateManager: BleHeartRateManager,
    private val bedrockClient: BedrockClient,
    private val userPrefs: UserPreferencesRepository
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

                        // 1. Announce Set (Intro + Countdown)
                        _state.value = AutoCoachState.SPEAKING
                        val weight = (set.actualLbs ?: set.suggestedLbs.toFloat())
                        val reps = set.actualReps ?: set.suggestedReps

                        val introScript = try {
                            bedrockClient.generateSetIntroScript(
                                exerciseName = exercise.name,
                                reps = reps,
                                weight = weight,
                                setNumber = index + 1,
                                totalSets = sets.size
                            )
                        } catch (e: Exception) {
                            Log.e("AutoCoach", "Failed to gen intro script", e)
                            "Next up, ${exercise.name}. Set ${index + 1} of ${sets.size}. $reps reps at $weight lbs. Starting in 3. 2. 1. Go."
                        }

                        voice.speakAndWait(introScript)
                        
                        if (index == 0) {
                            // Short prep for first exercise of the day
                            _state.value = AutoCoachState.RESTING
                            delay(10000) 
                        }

                        // 2. Set Execution
                        _state.value = AutoCoachState.SET_IN_PROGRESS
                        // Wait an estimated amount of time for the set to finish (e.g., 4 secs per rep)
                        delay((reps * 4000L))

                        // 3. Post-Set Logging Interaction
                        _state.value = AutoCoachState.SPEAKING
                        
                        val postSetScript = try {
                            bedrockClient.generatePostSetScript(reps, weight)
                        } catch (e: Exception) {
                            Log.e("AutoCoach", "Failed to gen post script", e)
                            "Set complete. Did you get all $reps reps?"
                        }
                        
                        voice.speakAndWait(postSetScript)

                        _state.value = AutoCoachState.LISTENING
                        // Listen to mic for 5 seconds
                        val response = sttManager.listenForResponse(timeoutMillis = 5000).lowercase()
                        Log.d("AutoCoach", "User said: $response")

                        _state.value = AutoCoachState.SPEAKING

                        // Basic local NLP to adjust UI fields based on speech
                        if (response.contains("no") || response.contains("only")) {
                            val detectedNumber = response.replace(Regex("[^0-9]"), "")
                            if (detectedNumber.isNotBlank()) {
                                voice.speakAndWait("Got it, changing reps to $detectedNumber.")
                                onUpdateReps(set, detectedNumber)
                            } else {
                                voice.speakAndWait("Understood. I'll log it as prescribed, adjust it on screen if you need.")
                            }
                        } else if (response.contains("too heavy") || response.contains("lower")) {
                            voice.speakAndWait("Alright, I'll drop the weight for next time.")
                            onUpdateWeight(set, (weight - 5).toString())
                        } else {
                            voice.speakAndWait("Solid.")
                        }

                        // 4. Update UI & Start Rest Timer
                        onSetCompleted(set)
                        onStartTimer(exercise.exerciseId)

                        // 5. Announce Rest with Live HR Monitoring
                        if (index < sets.lastIndex) {
                            if (bleHeartRateManager.isConnected.value) {
                                val currentHR = bleHeartRateManager.heartRate.value
                                val age = userPrefs.userAge.first()
                                val targetHR = ((220 - age) * 0.60).toInt()
                                
                                voice.speakAndWait("Your heart rate is currently at $currentHR. Based on your profile, we are waiting until it hits $targetHR.")
                                _state.value = AutoCoachState.RESTING
                                
                                // Dynamic recovery: Wait for HR < targetHR or 2.5 min timeout
                                withTimeoutOrNull(150000L) {
                                    bleHeartRateManager.heartRate.first { it in 1..targetHR }
                                }
                                
                                _state.value = AutoCoachState.SPEAKING
                                voice.speakAndWait("Heart rate recovered. Ready for the next set?")
                            } else {
                                // If no HR monitor, just give a standard verbal cue for the timer started in step 4
                                _state.value = AutoCoachState.RESTING
                                voice.speakAndWait("Take a breather. I've started your rest timer.")
                                // We don't suspend here because the UI timer handles it, 
                                // but we might want a short delay so the coach doesn't immediately jump to next set
                                delay(30000) // Default 30s verbal break before coach checks in again
                                _state.value = AutoCoachState.SPEAKING
                                voice.speakAndWait("Ready for the next set?")
                            }
                        }
                    }

                    _state.value = AutoCoachState.SPEAKING
                    voice.speakAndWait("Nice work on ${exercise.name}.")
                }

                voice.speakAndWait("Session complete. You crushed it.")
                _state.value = AutoCoachState.OFF

            } catch (e: CancellationException) {
                Log.d("AutoCoach", "Engine cancelled")
            } catch (e: Exception) {
                Log.e("AutoCoach", "Engine error", e)
                _state.value = AutoCoachState.OFF
            }
        }
    }

    fun stopSpeaking() {
        job?.cancel()
        _state.value = AutoCoachState.OFF
    }

    fun stop() {
        job?.cancel()
        _state.value = AutoCoachState.OFF
    }
}