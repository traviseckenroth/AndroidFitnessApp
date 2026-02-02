package com.example.myapplication.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.myapplication.data.local.CompletedWorkoutEntity
import com.example.myapplication.data.local.DailyWorkoutEntity
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.data.local.WorkoutPlanEntity
import com.example.myapplication.data.local.WorkoutSetEntity

@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutPlanEntity::class,
        DailyWorkoutEntity::class,
        WorkoutSetEntity::class,
        CompletedWorkoutEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
}