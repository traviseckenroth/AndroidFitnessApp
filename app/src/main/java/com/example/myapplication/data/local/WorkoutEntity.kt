package com.example.myapplication.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true)
    val workoutId: Long = 0,
    val date: Long,
    val name: String = "New Workout",
    val duration: Float, // in minutes
    val notes: String? = null
)
