package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme // FIX: Added this import
import com.example.myapplication.ui.MainScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // FIX: Using standard MaterialTheme directly to avoid "Unresolved Theme" errors
            MaterialTheme {
                MainScreen()
            }
        }
    }
}