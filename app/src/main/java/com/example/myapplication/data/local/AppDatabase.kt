package com.example.myapplication.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.myapplication.data.local.FoodLogEntity

@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutPlanEntity::class,
        DailyWorkoutEntity::class,
        WorkoutSetEntity::class,
        CompletedWorkoutEntity::class,
        FoodLogEntity::class
    ],
    version = 17,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
}