package com.example.myapplication // Make sure this matches your package

import android.app.Application
import androidx.room.Room
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.WorkoutDatabaseCallback

class WorkoutApp : Application() {

    // 1. Create the database instance
    // "lazy" means it won't be built until you actually ask for it first
    val database by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "workout_db"
        )
        // 2. Attach the callback we wrote earlier to pre-fill the data
        .addCallback(WorkoutDatabaseCallback(applicationContext) {
            database.workoutDao()
        })
        .build()
    }
}