package com.example.myapplication

import android.app.Application
import com.example.myapplication.data.local.AppDatabase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@HiltAndroidApp
class WorkoutApp : Application() {
    // A scope that lives as long as the app is alive
    private val applicationScope = CoroutineScope(SupervisorJob())

    // Initialize the database once here
    val database by lazy { AppDatabase.getDatabase(this, applicationScope) }
}