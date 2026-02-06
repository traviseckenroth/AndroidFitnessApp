package com.example.myapplication.data

import kotlinx.serialization.Serializable

@Serializable
data class NutritionPlan(
    val calories: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fats: String = "",
    val timing: String = "",
    val explanation: String = ""
)