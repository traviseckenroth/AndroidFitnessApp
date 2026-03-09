package com.example.myapplication.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale // IMPORT FOR SCALING
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale // IMPORT FOR SCALING BEHAVIOR
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.R

@Composable
fun SplashScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val isAuthCheckComplete by viewModel.isAuthCheckComplete.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    LaunchedEffect(isAuthCheckComplete) {
        if (isAuthCheckComplete) {
            if (isLoggedIn) onNavigateToHome() else onNavigateToLogin()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.forma_logo),
            contentDescription = "Forma AI Fitness Logo",
            // This is the magic line!
            // 1.0f = 100%, 1.5f = 150%, 2.0f = 200%
            modifier = Modifier.scale(1.8f),
            contentScale = ContentScale.Fit
        )
    }
}