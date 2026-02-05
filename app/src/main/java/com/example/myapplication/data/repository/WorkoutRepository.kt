package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.data.local.CompletedWorkoutEntity
import com.example.myapplication.data.local.CompletedWorkoutWithExercise
import com.example.myapplication.data.local.DailyWorkoutEntity
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.local.WorkoutDao
import com.example.myapplication.data.local.WorkoutSetEntity
import com.example.myapplication.data.local.UserPreferencesRepository
import com.example.myapplication.data.remote.BedrockClient
import com.example.myapplication.data.remote.NutritionPlan
import com.example.myapplication.data.remote.GeneratedDay
import com.example.myapplication.data.remote.GeneratedExercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@Singleton
class WorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val userPrefs: UserPreferencesRepository,
    private val bedrockClient: BedrockClient
) {
    // --- READS ---
    fun getWorkoutForDate(date: Long): Flow<DailyWorkoutEntity?> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = cal.timeInMillis
        return workoutDao.getWorkoutByDate(startOfDay, endOfDay)
    }

    fun getAllWorkoutDates(): Flow<List<Long>> = workoutDao.getAllWorkoutDates()
    fun getSetsForSession(workoutId: Long): Flow<List<WorkoutSetEntity>> = workoutDao.getSetsForWorkout(workoutId)
    suspend fun updateSet(set: WorkoutSetEntity) = workoutDao.updateSet(set)
    fun getAllExercises(): Flow<List<ExerciseEntity>> = workoutDao.getAllExercises()
    fun getExercisesByIds(exerciseIds: List<Long>): Flow<List<ExerciseEntity>> = workoutDao.getExercisesByIds(exerciseIds)
    fun getAllCompletedWorkouts(): Flow<List<CompletedWorkoutWithExercise>> = workoutDao.getCompletedWorkoutsWithExercise()
    fun getCompletedWorkoutsForExercise(exerciseId: Long): Flow<List<CompletedWorkoutWithExercise>> = workoutDao.getCompletedWorkoutsForExercise(exerciseId)

    // --- PROFILE READS ---
    fun getUserHeight(): Flow<Int> = userPrefs.userHeight
    fun getUserWeight(): Flow<Double> = userPrefs.userWeight
    fun getUserAge(): Flow<Int> = userPrefs.userAge

    // New Profile Fields
    fun getUserGender(): Flow<String> = userPrefs.userGender
    fun getUserActivity(): Flow<String> = userPrefs.userActivity
    fun getUserBodyFat(): Flow<Double?> = userPrefs.userBodyFat
    fun getUserDiet(): Flow<String> = userPrefs.userDiet
    fun getUserGoalPace(): Flow<String> = userPrefs.userGoalPace

    // --- PROFILE WRITES ---
    suspend fun saveProfile(
        height: Int, weight: Double, age: Int,
        gender: String, activity: String, bodyFat: Double?,
        diet: String, pace: String
    ) {
        userPrefs.saveProfile(height, weight, age, gender, activity, bodyFat, diet, pace)
    }

    // --- 1. WORKOUT GENERATION ONLY ---
    suspend fun generateAndSavePlan(
        goal: String,
        duration: Int,
        days: List<String>,
        programType: String = "Hypertrophy"
    ): Long {
        Log.d("WorkoutRepo", "Generating Plan for: $goal")

        // Cleanup previous future scheduled workouts
        workoutDao.deleteFutureUncompletedWorkouts(System.currentTimeMillis())

        val workoutHistory = workoutDao.getCompletedWorkoutsWithExercise().first()
        val availableExercises = workoutDao.getAllExercises().first()
        val height = userPrefs.userHeight.first()
        val weight = userPrefs.userWeight.first()
        val age = userPrefs.userAge.first()

        // 1. Fetch AI Response (Exercises Only)
        val aiResponse = bedrockClient.generateWorkoutPlan(
            goal = goal,
            programType = programType,
            days = days,
            duration = duration.toFloat(),
            workoutHistory = workoutHistory,
            availableExercises = availableExercises,
            userAge = age,
            userHeight = height,
            userWeight = weight
        )

        val startDate = System.currentTimeMillis()

        // Create Plan Entity with NULL nutrition initially
        val planEntity = WorkoutPlanEntity(
            name = "$programType for $goal",
            startDate = startDate,
            goal = goal,
            programType = programType,
            nutritionJson = null
        )

        // 2. Apply Time Constraints to AI schedule
        val adjustedSchedule = enforceTimeConstraints(aiResponse.schedule, duration.toFloat())

        // 3. Determine Deload Ratios
        val totalWeeks = if (programType == "Endurance") 4 else 5
        val deloadWeekIndex = if (programType == "Endurance") 4 else 5

        val fullPlanData = mutableMapOf<DailyWorkoutEntity, List<WorkoutSetEntity>>()

        for (weekNum in 1..totalWeeks) {
            val isDeload = weekNum == deloadWeekIndex

            adjustedSchedule.forEach { templateDay ->
                val workoutDate = calculateSmartDate(startDate, weekNum, templateDay.day)

                val workoutEntity = DailyWorkoutEntity(
                    planId = 0,
                    scheduledDate = workoutDate,
                    title = if (isDeload) "DELOAD: ${templateDay.title}" else templateDay.title,
                    isCompleted = false
                )

                val setsForThisWorkout = mutableListOf<WorkoutSetEntity>()

                templateDay.exercises.forEach { aiEx ->
                    val realEx = availableExercises.find { it.name.equals(aiEx.name, ignoreCase = true) }

                    realEx?.let { validExercise ->
                        val finalSets = if (isDeload) {
                            (aiEx.sets * 0.6f).roundToInt().coerceAtLeast(2)
                        } else {
                            when (weekNum) {
                                4 -> aiEx.sets + 1 // Peaking week
                                else -> aiEx.sets
                            }
                        }

                        val finalLbs = if (isDeload) {
                            (aiEx.suggestedLbs * 0.7f).toInt()
                        } else {
                            aiEx.suggestedLbs.toInt()
                        }

                        for (setNum in 1..finalSets) {
                            setsForThisWorkout.add(
                                WorkoutSetEntity(
                                    workoutId = 0,
                                    exerciseId = validExercise.exerciseId,
                                    setNumber = setNum,
                                    suggestedReps = aiEx.suggestedReps,
                                    suggestedRpe = if (isDeload) 5 else aiEx.suggestedRpe,
                                    suggestedLbs = if (finalLbs == 0) 45 else finalLbs,
                                    isCompleted = false
                                )
                            )
                        }
                    }
                }
                fullPlanData[workoutEntity] = setsForThisWorkout
            }
        }

        return workoutDao.saveFullWorkoutPlan(planEntity, fullPlanData)
    }

    // --- 2. NUTRITION GENERATION ONLY ---
    suspend fun generateAndSaveNutrition(): NutritionPlan {
        val height = userPrefs.userHeight.first()
        val weight = userPrefs.userWeight.first()
        val age = userPrefs.userAge.first()
        val gender = userPrefs.userGender.first()
        val activity = userPrefs.userActivity.first()
        val diet = userPrefs.userDiet.first()
        val pace = userPrefs.userGoalPace.first()

        // Get current active plan to know the goal
        // Assumes WorkoutDao has a method to get the latest plan
        val currentPlan = workoutDao.getLatestPlan() ?: throw Exception("No active workout plan found")

        val nutrition = bedrockClient.generateNutritionPlan(
            goal = currentPlan.goal,
            userAge = age,
            userHeight = height,
            userWeight = weight,
            gender = gender,
            activityLevel = activity,
            dietType = diet,
            goalPace = pace
        )

        // Update DB
        val nutritionJson = Json.encodeToString(nutrition)
        workoutDao.updateNutrition(currentPlan.planId, nutritionJson)

        return nutrition
    }

    /**
     * Post-processes the AI schedule to strictly enforce session duration.
     * Rules: T1=4m, T2=2.5m, T3=1.5m.
     */
    private fun enforceTimeConstraints(schedule: List<GeneratedDay>, targetHours: Float): List<GeneratedDay> {
        val targetMinutes = (targetHours * 60).toInt()
        val tolerance = 5 // +/- 5 minutes is acceptable

        return schedule.map { day ->
            val exercises = day.exercises.toMutableList()
            var currentMinutes = calculateTotalMinutes(exercises)

            // 1. CUT VOLUME if too long
            while (currentMinutes > targetMinutes + tolerance) {
                // Find a candidate to cut. Priority: Tier 3 (lowest importance) -> Tier 2 -> Tier 1
                val cutCandidateIndex = exercises.indexOfFirst {
                    it == exercises.filter { e -> e.sets > 2 }
                        .sortedWith(compareByDescending<GeneratedExercise> { ex -> ex.tier }.thenByDescending { ex -> ex.sets })
                        .firstOrNull()
                }

                if (cutCandidateIndex != -1) {
                    val ex = exercises[cutCandidateIndex]
                    exercises[cutCandidateIndex] = ex.copy(sets = ex.sets - 1)
                    currentMinutes = calculateTotalMinutes(exercises)
                } else {
                    break
                }
            }

            // 2. ADD VOLUME if too short
            while (currentMinutes < targetMinutes - tolerance) {
                val addCandidateIndex = exercises.indexOfFirst {
                    it == exercises.filter { e -> e.tier <= 2 && e.sets < 6 }
                        .sortedBy { ex -> ex.tier } // Prioritize T1
                        .firstOrNull()
                }

                if (addCandidateIndex != -1) {
                    val ex = exercises[addCandidateIndex]
                    exercises[addCandidateIndex] = ex.copy(sets = ex.sets + 1)
                    currentMinutes = calculateTotalMinutes(exercises)
                } else {
                    break
                }
            }

            day.copy(exercises = exercises)
        }
    }

    private fun calculateTotalMinutes(exercises: List<GeneratedExercise>): Double {
        return exercises.sumOf { ex ->
            val timePerSet = when (ex.tier) {
                1 -> 4.0  // Heavy/Rest
                2 -> 2.5  // Hypertrophy
                else -> 1.5 // Metabolic
            }
            ex.sets * timePerSet
        }
    }

    suspend fun getPlanDetails(planId: Long): com.example.myapplication.data.WorkoutPlan {
        // If planId is 0, try to get the active/latest plan
        val targetPlanId = if (planId == 0L) {
            workoutDao.getLatestPlan()?.planId ?: 0L
        } else {
            planId
        }

        // Fetch specific plan entity to get Nutrition JSON
        val planEntity = workoutDao.getPlanById(targetPlanId)
        val nutrition = planEntity?.nutritionJson?.let {
            try { Json.decodeFromString<NutritionPlan>(it) } catch(e: Exception) { null }
        }

        val workouts = workoutDao.getWorkoutsForPlan(targetPlanId)
        val allExercises = workoutDao.getAllExercisesOneShot()

        val weeklyPlans = mutableListOf<com.example.myapplication.data.WeeklyPlan>()

        if (workouts.isNotEmpty()) {
            val startDate = workouts.first().scheduledDate
            val workoutsByWeek = workouts.groupBy {
                val diff = it.scheduledDate - startDate
                (diff / (1000L * 60 * 60 * 24 * 7)).toInt() + 1
            }

            workoutsByWeek.forEach { (weekNum, dailyEntities) ->
                val dailyWorkouts = dailyEntities.map { entity ->
                    val sets = workoutDao.getSetsForWorkoutList(entity.workoutId)
                    val exercisesForDay = sets.groupBy { it.exerciseId }.map { (exId, setList) ->
                        val realEx = allExercises.find { it.exerciseId == exId }
                        val firstSet = setList.first()

                        com.example.myapplication.data.Exercise(
                            exerciseId = exId,
                            name = realEx?.name ?: "Unknown",
                            sets = setList.size,
                            reps = firstSet.suggestedReps.toString(),
                            rest = "${(realEx?.estimatedTimePerSet ?: 2.0).toInt() * 60}s",
                            tier = realEx?.tier ?: 1,
                            explanation = realEx?.notes ?: "",
                            estimatedTimePerSet = realEx?.estimatedTimePerSet ?: 2.0,
                            rpe = firstSet.suggestedRpe,
                            suggestedLbs = firstSet.suggestedLbs.toFloat()
                        )
                    }

                    com.example.myapplication.data.DailyWorkout(
                        day = getDayNameFromDate(entity.scheduledDate),
                        title = entity.title,
                        exercises = exercisesForDay
                    )
                }

                weeklyPlans.add(com.example.myapplication.data.WeeklyPlan(week = weekNum, days = dailyWorkouts))
            }
        }

        return com.example.myapplication.data.WorkoutPlan(
            explanation = planEntity?.name ?: "Your Plan",
            weeks = weeklyPlans,
            nutrition = nutrition
        )
    }

    private fun getDayNameFromDate(date: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = date
        return cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault())
            ?: "Day"
    }

    private fun calculateSmartDate(startDate: Long, weekNum: Int, dayName: String): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = startDate
        val lowerName = dayName.lowercase(Locale.getDefault())
        val targetDay = when {
            lowerName.startsWith("sun") -> Calendar.SUNDAY
            lowerName.startsWith("mon") -> Calendar.MONDAY
            lowerName.startsWith("tue") -> Calendar.TUESDAY
            lowerName.startsWith("wed") -> Calendar.WEDNESDAY
            lowerName.startsWith("thu") -> Calendar.THURSDAY
            lowerName.startsWith("fri") -> Calendar.FRIDAY
            lowerName.startsWith("sat") -> Calendar.SATURDAY
            else -> Calendar.MONDAY
        }
        val currentDay = cal.get(Calendar.DAY_OF_WEEK)
        var dayOffset = targetDay - currentDay
        if (dayOffset < 0) dayOffset += 7
        val totalDaysToAdd = dayOffset + ((weekNum - 1) * 7)
        cal.add(Calendar.DAY_OF_YEAR, totalDaysToAdd)
        cal.set(Calendar.HOUR_OF_DAY, 12)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        return cal.timeInMillis
    }

    // --- WORKOUT COMPLETION & AUTO-REGULATION ---
    suspend fun completeWorkout(workoutId: Long): List<String> {
        val workout = workoutDao.getWorkoutById(workoutId) ?: return emptyList()
        val sets = workoutDao.getSetsForWorkoutList(workoutId)

        // 1. Archive to History
        val historyEntries = sets.filter { it.isCompleted }.map { set ->
            CompletedWorkoutEntity(
                exerciseId = set.exerciseId,
                date = workout.scheduledDate,
                reps = set.actualReps ?: set.suggestedReps,
                weight = (set.actualLbs ?: set.suggestedLbs).toInt(),
                rpe = (set.actualRpe ?: set.suggestedRpe).toInt()
            )
        }

        if (historyEntries.isNotEmpty()) {
            workoutDao.insertCompletedWorkouts(historyEntries)
        }

        // 2. Mark as Complete
        workoutDao.markWorkoutAsComplete(workoutId)

        // 3. Run Optimization
        return autoRegulateFutureWorkouts(workout.planId, sets, workout.scheduledDate)
    }

    suspend fun injectWarmUpSets(workoutId: Long, exerciseId: Long, workingWeight: Int) {
        // 1. Get existing sets
        val currentSets = workoutDao.getSetsForWorkoutList(workoutId)
            .filter { it.exerciseId == exerciseId }
            .sortedBy { it.setNumber }

        if (currentSets.isEmpty()) return

        // 2. Define Warm-up Progression (Bar -> 50% -> 75%)
        fun roundToFive(w: Double) = (w / 5).toInt() * 5

        val warmups = listOf(
            WorkoutSetEntity(
                workoutId = workoutId, exerciseId = exerciseId, setNumber = 1,
                suggestedReps = 10, suggestedLbs = 45, suggestedRpe = 0, isCompleted = false
            ),
            WorkoutSetEntity(
                workoutId = workoutId, exerciseId = exerciseId, setNumber = 2,
                suggestedReps = 5, suggestedLbs = roundToFive(workingWeight * 0.5), suggestedRpe = 0, isCompleted = false
            ),
            WorkoutSetEntity(
                workoutId = workoutId, exerciseId = exerciseId, setNumber = 3,
                suggestedReps = 3, suggestedLbs = roundToFive(workingWeight * 0.75), suggestedRpe = 0, isCompleted = false
            )
        )

        // 3. Shift existing sets down by 3
        val updatedOriginalSets = currentSets.map { it.copy(setNumber = it.setNumber + 3) }

        // 4. Save everything
        updatedOriginalSets.forEach { workoutDao.updateSet(it) }
        workoutDao.insertSets(warmups)
    }

    private suspend fun autoRegulateFutureWorkouts(
        planId: Long,
        completedSets: List<WorkoutSetEntity>,
        currentDate: Long
    ): List<String> {
        val adjustmentLogs = mutableListOf<String>()

        val exercisePerformance = completedSets
            .filter { it.isCompleted && it.actualRpe != null }
            .groupBy { it.exerciseId }

        if (exercisePerformance.isEmpty()) return emptyList()

        val futureWorkouts = workoutDao.getWorkoutsForPlan(planId)
            .filter { it.scheduledDate > currentDate && !it.isCompleted }

        if (futureWorkouts.isEmpty()) return emptyList()

        val exercisesMap = workoutDao.getAllExercisesOneShot().associateBy { it.exerciseId }
        val adjustedExerciseIds = mutableSetOf<Long>()

        futureWorkouts.forEach { futureWorkout ->
            val futureSets = workoutDao.getSetsForWorkoutList(futureWorkout.workoutId)
            var hasUpdates = false

            val updatedSets = futureSets.map { targetSet ->
                val pastPerformance = exercisePerformance[targetSet.exerciseId]

                if (pastPerformance != null) {
                    val avgActualRpe = pastPerformance.mapNotNull { it.actualRpe }.average()
                    val avgTargetRpe = pastPerformance.map { it.suggestedRpe }.average()
                    val rpeDiff = avgActualRpe - avgTargetRpe

                    val newWeight = when {
                        rpeDiff >= 1.0 -> (targetSet.suggestedLbs * 0.95).toInt()
                        rpeDiff <= -1.5 -> (targetSet.suggestedLbs * 1.05).toInt()
                        else -> targetSet.suggestedLbs
                    }

                    if (newWeight != targetSet.suggestedLbs) {
                        hasUpdates = true
                        if (!adjustedExerciseIds.contains(targetSet.exerciseId)) {
                            val exName = exercisesMap[targetSet.exerciseId]?.name ?: "Exercise"
                            val direction = if (newWeight > targetSet.suggestedLbs) "Increased" else "Decreased"
                            val reason = if (newWeight > targetSet.suggestedLbs) "Easy RPE" else "High Fatigue"
                            adjustmentLogs.add("$exName: $direction load ($reason)")
                            adjustedExerciseIds.add(targetSet.exerciseId)
                        }
                        targetSet.copy(suggestedLbs = newWeight)
                    } else {
                        targetSet
                    }
                } else {
                    targetSet
                }
            }

            if (hasUpdates) {
                workoutDao.insertSets(updatedSets)
            }
        }
        return adjustmentLogs
    }

    // --- SWAP LOGIC ---
    suspend fun getBestAlternatives(currentExercise: ExerciseEntity): List<ExerciseEntity> {
        val majorMuscle = currentExercise.majorMuscle

        val candidates = workoutDao.getAlternativesByMajorMuscle(majorMuscle, currentExercise.exerciseId)

        return candidates
            .distinctBy { it.name }
            .sortedWith(compareBy(
                { it.tier != currentExercise.tier },
                { it.equipment == currentExercise.equipment }
            )).take(2)
    }

    suspend fun swapExercise(workoutId: Long, oldExerciseId: Long, newExerciseId: Long) {
        workoutDao.swapExerciseInSets(workoutId, oldExerciseId, newExerciseId)
    }
}