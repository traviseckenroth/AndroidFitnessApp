package com.example.myapplication.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutPlanEntity::class,
        DailyWorkoutEntity::class,
        WorkoutSetEntity::class,
        CompletedWorkoutEntity::class,
        FoodLogEntity::class,
        WorkoutEntity::class,
        ContentSourceEntity::class,
        UserSubscriptionEntity::class,
        UserMemoryEntity::class
    ],
    version = 34,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun memoryDao(): MemoryDao

    companion object {
        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
            }
        }
    }
}