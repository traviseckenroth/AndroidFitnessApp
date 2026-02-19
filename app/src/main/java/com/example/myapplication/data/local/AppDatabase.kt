package com.example.myapplication.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.myapplication.data.local.FoodLogEntity

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
        UserSubscriptionEntity::class
    ],
    version = 32,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao

    companion object {
        // 2. DEFINE MIGRATION (Example: v23 to v24)
        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // If you added a new table or column, write the SQL here.
                // Example: db.execSQL("ALTER TABLE DailyWorkoutEntity ADD COLUMN notes TEXT NOT NULL DEFAULT ''")

                // If no schema changes, leave empty to preserve data.
            }
        }
    }
}