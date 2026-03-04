// app/src/main/java/com/example/myapplication/ui/exercise/ExerciseListScreen.kt
package com.example.myapplication.ui.exercise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.myapplication.data.local.ExerciseEntity
import com.example.myapplication.ui.navigation.ExerciseHistory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseListScreen(
    viewModel: ExerciseViewModel = hiltViewModel(),
    isPickerMode: Boolean,
    onNavigateBack: () -> Unit,
    navController: NavController
) {
    val exercises by viewModel.exercises.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exercises") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues)) {
            items(exercises) { exercise ->
                // FIXED: Changed from ExerciseRow to ExerciseListItem to match the function below
                ExerciseListItem(
                    exercise = exercise,
                    onClick = {
                        if (isPickerMode) {
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("selected_exercise_id", exercise.exerciseId)
                            navController.popBackStack()
                        } else {
                            navController.navigate(ExerciseHistory(exercise.exerciseId))
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ExerciseListItem(exercise: ExerciseEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onClick() }
    ) {
        Text(
            text = exercise.name,
            modifier = Modifier.padding(16.dp)
        )
    }
}