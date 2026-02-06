package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Modifier
import com.example.myapplication.data.local.WorkoutDao
import com.example.myapplication.data.local.populateDatabase
import com.example.myapplication.ui.navigation.BottomNavigationBar
import com.example.myapplication.ui.navigation.NavGraph
import com.example.myapplication.ui.nutrition.NutritionViewModel
import com.example.myapplication.ui.theme.MyApplicationTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var workoutDao: WorkoutDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            populateDatabase(workoutDao)
        }

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val nutritionViewModel: NutritionViewModel = hiltViewModel()

                LaunchedEffect(Unit) {
                    val data = intent?.data
                    if (data != null && data.scheme == "myfitness" && data.host == "log_food") {
                        val foodText = data.getQueryParameter("text")
                        if (!foodText.isNullOrBlank()) {
                            nutritionViewModel.logFood(foodText)
                            navController.navigate("nutrition")
                        }
                    }
                }

                Scaffold(
                    bottomBar = { BottomNavigationBar(navController) }
                ) { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        NavGraph(navController = navController)
                    }
                }
            }
        }
    }
}