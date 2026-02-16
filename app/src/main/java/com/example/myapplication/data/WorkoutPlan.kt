package com.example.myapplication.data

import kotlinx.serialization.Serializable

@Serializable
data class WorkoutPlan(
    val explanation: String,
    val weeks: List<WeeklyPlan>,
    val nutrition: NutritionPlan? = null
)


@Serializable
data class WeeklyPlan(
    val week: Int,
    val days: List<DailyWorkout>
)

@Serializable
data class DailyWorkout(
    val day: String,
    val title: String,
    val exercises: List<Exercise>
)

@Serializable
data class Exercise(
    val exerciseId: Long,
    val name: String,
    val sets: Int,
    val reps: String,
    val rest: String,
    val tier: Int,
    val explanation: String,
    val estimatedTimePerSet: Double,
    val rpe: Int,
    val suggestedLbs: Float = 0f
)
