package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.local.WorkoutDao
import com.example.myapplication.data.local.populateDatabase
import com.example.myapplication.ui.MainScreen
import com.example.myapplication.ui.theme.MyApplicationTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Inject DAO to run population logic
    @Inject lateinit var workoutDao: WorkoutDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Run the populator in background to ensure exercises exist
        lifecycleScope.launch(Dispatchers.IO) {
            populateDatabase(workoutDao)
        }

        setContent {
            MyApplicationTheme {
                MainScreen()
            }
        }
    }
}