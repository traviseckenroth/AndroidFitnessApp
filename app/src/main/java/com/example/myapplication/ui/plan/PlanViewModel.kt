package com.example.myapplication.ui.plan

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.DailyWorkout
import com.example.myapplication.data.WeeklyPlan
import com.example.myapplication.data.WorkoutPlan
// UPDATED: Added necessary imports for DB Entities and Calendar
import com.example.myapplication.data.local.DailyWorkoutEntity
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.local.WorkoutDao
import com.example.myapplication.data.local.WorkoutPlanEntity
import com.example.myapplication.data.local.WorkoutSetEntity
import com.example.myapplication.data.remote.invokeBedrock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

sealed interface PlanUiState {
    object Idle : PlanUiState
    object Loading : PlanUiState
    data class Success(val plan: WorkoutPlan, val startDate: Long = System.currentTimeMillis()) : PlanUiState
    data class Error(val msg: String) : PlanUiState
    object Empty : PlanUiState
}

@HiltViewModel
class PlanViewModel @Inject constructor(
    private val workoutDao: WorkoutDao
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlanUiState>(PlanUiState.Empty)
    val uiState: StateFlow<PlanUiState> = _uiState.asStateFlow()

    fun generatePlan(goal: String, program: String, duration: Float, days: List<String>) {
        if (goal.isBlank()) {
            _uiState.value = PlanUiState.Error("Please enter a goal.")
            return
        }

        viewModelScope.launch {
            _uiState.value = PlanUiState.Loading
            try {
                val generatedPlanResponse = withContext(Dispatchers.IO) {
                    // UPDATED: Fetch all exercises first to pass to Bedrock
                    val allExercises = workoutDao.getAllExercises().first()
                    val workoutHistory = workoutDao.getAllCompletedWorkouts().first()
                    // UPDATED: Passed 'allExercises' so AI knows DB content
                    invokeBedrock(goal, program, days, duration, workoutHistory, allExercises)
                }

                val generatedDays = generatedPlanResponse.schedule

                // --- UPDATED: ROBUST ID & TIME LOGIC ---
                // 1. Create a map of existing exercises (Name -> ID) for fast, safe lookup
                val existingList = workoutDao.getAllExercises().first()
                val nameToIdMap = existingList.associateBy { it.name.lowercase().trim() }
                    .mapValues { it.value.exerciseId }
                    .toMutableMap()

                val allGeneratedExercises = generatedDays.flatMap { it.exercises }
                    .distinctBy { it.name.lowercase().trim() }

                allGeneratedExercises.forEach { generatedExercise ->
                    val key = generatedExercise.name.lowercase().trim()

                    // If exercise doesn't exist, insert it with calculated Time
                    if (!nameToIdMap.containsKey(key)) {
                        // UPDATED: Auto-calculate time based on Tier (T1=3m, T2=2.5m, T3=2m)
                        val timeVal = when (generatedExercise.tier) {
                            1 -> 3.0
                            2 -> 2.5
                            else -> 2.0
                        }

                        val newEntity = ExerciseEntity(
                            name = generatedExercise.name,
                            muscleGroup = generatedExercise.muscleGroup,
                            equipment = generatedExercise.equipment,
                            tier = generatedExercise.tier,
                            loadability = generatedExercise.loadability,
                            fatigue = generatedExercise.fatigue,
                            notes = generatedExercise.notes,
                            estimatedTimePerSet = timeVal // UPDATED: Save time to DB
                        )

                        // UPDATED: Capture the new ID immediately from Insert (requires DAO to return Long)
                        var newId = workoutDao.insertExercise(newEntity)

                        // Fallback: If insert returns -1 (conflict), fetch manually
                        if (newId == -1L) {
                            val existing = workoutDao.getExerciseByName(generatedExercise.name)
                            newId = existing?.exerciseId ?: 0L
                        }

                        if (newId > 0) {
                            nameToIdMap[key] = newId
                        }
                    }
                }
                // --- END UPDATED LOGIC ---

                // Refresh full list for mapping details
                val updatedAllExercises = workoutDao.getAllExercises().first()

                val weeklyPlans = generatedDays.groupBy { it.week }.map { (week, days) ->
                    WeeklyPlan(
                        week = week,
                        days = days.map { day ->
                            DailyWorkout(
                                day = day.day ?: "Day ${days.indexOf(day) + 1}", // UPDATED: Handle nulls
                                title = day.title ?: "Workout",
                                exercises = day.exercises.map { ex ->
                                    // Use the Safe Map or Find to get the source of truth
                                    val safeName = ex.name.lowercase().trim()
                                    val realId = nameToIdMap[safeName] ?: 0L
                                    val exerciseEntity = updatedAllExercises.find { it.name.lowercase().trim() == safeName }

                                    // UPDATED: Get time from DB or default
                                    val minutesPerSet = exerciseEntity?.estimatedTimePerSet ?: 2.0
                                    val secondsPerSet = (minutesPerSet * 60).toInt()

                                    com.example.myapplication.data.Exercise(
                                        exerciseId = realId,
                                        name = ex.name,
                                        sets = ex.sets,
                                        reps = ex.suggestedReps.toString(),
                                        rest = "${secondsPerSet - 45}s", // UPDATED: Dynamic Rest
                                        tier = ex.tier,
                                        explanation = ex.notes,
                                        estimatedTimePerSet = secondsPerSet.toDouble(), // UPDATED: Pass calculated time
                                        rpe = ex.suggestedRpe
                                    )
                                }
                            )
                        }
                    )
                }

                val plan = WorkoutPlan(weeks = weeklyPlans, explanation = generatedPlanResponse.explanation)

                // UPDATED: Call savePlan to persist to DB
                savePlan(plan, goal, program)

                _uiState.value = PlanUiState.Success(plan)

            } catch (e: Exception) {
                val errorMessage = if (e.message?.contains("identity") == true || e.message?.contains("Credentials") == true) {
                    "AWS Error: Check your Access Key and Secret Key in local.properties."
                } else {
                    "Generation Failed: ${e.message}"
                }
                _uiState.value = PlanUiState.Error(errorMessage)
                Log.e("PlanViewModel", "Error generating plan", e)
            }
        }
    }

    // UPDATED: Re-implemented savePlan to handle DB persistence and correct Date logic
    private suspend fun savePlan(plan: WorkoutPlan, goal: String, program: String) {
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        val planId = workoutDao.insertPlan(
            WorkoutPlanEntity(
                name = "My Plan",
                startDate = today.timeInMillis,
                goal = goal,
                programType = program
            )
        )

        val dayMap = mapOf(
            "sunday" to Calendar.SUNDAY,
            "monday" to Calendar.MONDAY,
            "tuesday" to Calendar.TUESDAY,
            "wednesday" to Calendar.WEDNESDAY,
            "thursday" to Calendar.THURSDAY,
            "friday" to Calendar.FRIDAY,
            "saturday" to Calendar.SATURDAY
        )

        plan.weeks.forEach { week ->
            week.days.forEach { day ->
                val targetDayName = day.day.lowercase().trim()
                val targetDayInt = dayMap[targetDayName] ?: return@forEach

                // Calculate exact date for this workout relative to this week
                val workoutCal = Calendar.getInstance()
                workoutCal.set(Calendar.DAY_OF_WEEK, targetDayInt)
                workoutCal.add(Calendar.WEEK_OF_YEAR, week.week - 1)

                // Normalize
                workoutCal.set(Calendar.HOUR_OF_DAY, 0)
                workoutCal.set(Calendar.MINUTE, 0)
                workoutCal.set(Calendar.SECOND, 0)
                workoutCal.set(Calendar.MILLISECOND, 0)

                // Skip past days (e.g., don't schedule a "Monday" workout if today is Friday)
                if (workoutCal.before(today)) {
                    return@forEach
                }

                val workoutId = workoutDao.insertDailyWorkout(
                    DailyWorkoutEntity(
                        planId = planId,
                        scheduledDate = workoutCal.timeInMillis,
                        title = day.title,
                        isCompleted = false
                    )
                )

                val sets = mutableListOf<WorkoutSetEntity>()
                day.exercises.forEach { exercise ->
                    if (exercise.exerciseId > 0) {
                        repeat(exercise.sets) { index ->
                            sets.add(
                                WorkoutSetEntity(
                                    workoutId = workoutId,
                                    exerciseId = exercise.exerciseId,
                                    setNumber = index + 1,
                                    suggestedReps = exercise.reps.toIntOrNull() ?: 0,
                                    suggestedRpe = exercise.rpe, // This line is correct
                                    isCompleted = false
                                )
                            )
                        }
                    }
                }
                workoutDao.insertSets(sets)
            }
        }
    }

    fun resetState() {
        _uiState.value = PlanUiState.Empty
    }
}
