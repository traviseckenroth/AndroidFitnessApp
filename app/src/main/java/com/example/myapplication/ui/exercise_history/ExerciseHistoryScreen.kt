package com.example.myapplication.ui.exercise_history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.myapplication.data.local.CompletedWorkoutWithExercise
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseHistoryScreen(
    navController: NavController,
    viewModel: ExerciseHistoryViewModel = hiltViewModel()
) {
    val exerciseHistory by viewModel.exerciseHistory.collectAsState()
    val exerciseName = exerciseHistory.firstOrNull()?.exercise?.name ?: "Exercise History"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(exerciseName) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (exerciseHistory.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No history for this exercise yet.")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(paddingValues)) {
                items(exerciseHistory) { completedWorkout ->
                    HistoryListItem(completedWorkout)
                }
            }
        }
    }
}

@Composable
fun HistoryListItem(item: CompletedWorkoutWithExercise) {
    val date = Date(item.completedWorkout.date)
    val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(dateFormat.format(date), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Reps: ${item.completedWorkout.reps}")
                Text("RPE: ${item.completedWorkout.rpe}")
                Text("Weight: ${item.completedWorkout.weight} lbs")
            }
        }
    }
}