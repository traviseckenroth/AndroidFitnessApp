package com.example.myapplication

import android.app.Application
import android.os.StrictMode
import dagger.hilt.android.HiltAndroidApp
import net.sqlcipher.database.SQLiteDatabase

@HiltAndroidApp
class WorkoutApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize SQLCipher
        SQLiteDatabase.loadLibs(this)

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
        }
    }
}