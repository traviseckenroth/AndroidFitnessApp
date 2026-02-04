package com.example.myapplication.util

object PlateCalculator {
    fun calculatePlates(targetWeight: Double, barWeight: Double = 45.0): String {
        if (targetWeight <= barWeight) return "Just the bar"

        var remaining = (targetWeight - barWeight) / 2
        val plates = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5)
        val result = mutableListOf<String>()

        for (plate in plates) {
            val count = (remaining / plate).toInt()
            if (count > 0) {
                result.add("${count}x${if (plate % 1.0 == 0.0) plate.toInt() else plate}")
                remaining -= (count * plate)
            }
        }

        return if (result.isEmpty()) "Just the bar" else result.joinToString(", ") + " per side"
    }
}