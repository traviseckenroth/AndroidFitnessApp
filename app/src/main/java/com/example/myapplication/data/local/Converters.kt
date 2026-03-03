// app/src/main/java/com/example/myapplication/data/local/Converters.kt
package com.example.myapplication.data.local

import androidx.room.TypeConverter
import com.example.myapplication.data.NutritionPlan
import org.json.JSONObject

class Converters {
    @TypeConverter
    fun fromNutritionPlan(plan: NutritionPlan?): String? {
        if (plan == null) return null
        val json = JSONObject()
        json.put("calories", plan.calories)
        json.put("protein", plan.protein)
        json.put("carbs", plan.carbs)
        json.put("fats", plan.fats)
        json.put("timing", plan.timing)
        json.put("explanation", plan.explanation)
        return json.toString()
    }

    @TypeConverter
    fun toNutritionPlan(jsonString: String?): NutritionPlan? {
        if (jsonString.isNullOrBlank()) return null
        return try {
            val json = JSONObject(jsonString)
            NutritionPlan(
                calories = json.optString("calories", "2000"),
                protein = json.optString("protein", "150"),
                carbs = json.optString("carbs", "200"),
                fats = json.optString("fats", "70"),
                timing = json.optString("timing", ""),
                explanation = json.optString("explanation", "")
            )
        } catch (e: Exception) {
            null
        }
    }
}