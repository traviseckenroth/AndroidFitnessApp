package com.example.myapplication.ui.auth

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.AuthRepository
import com.example.myapplication.data.repository.LoginResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    var uiState by mutableStateOf<LoginResult?>(null)
    var isLoading by mutableStateOf(false)
    var isNewPasswordRequired by mutableStateOf(false) // Trigger for the UI

    fun login(u: String, p: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            val result = authRepository.signIn(u, p)
            isLoading = false

            when (result) {
                is LoginResult.Success -> onSuccess()
                is LoginResult.NewPasswordRequired -> isNewPasswordRequired = true // Switch UI mode
                is LoginResult.Error -> uiState = result
            }
        }
    }

    fun confirmNewPassword(newPass: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            val result = authRepository.completeNewPasswordChallenge(newPass)
            isLoading = false

            if (result is LoginResult.Success) {
                onSuccess()
            } else if (result is LoginResult.Error) {
                uiState = result
            }
        }
    }
}

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    val activity = (LocalContext.current as? Activity)
// If the user is on the login screen, back button should exit the app
    BackHandler {
        activity?.finish()
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!viewModel.isNewPasswordRequired) {
            // === STANDARD LOGIN FORM ===
            Text("Welcome Back", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Email / Username") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { viewModel.login(username, password, onLoginSuccess) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !viewModel.isLoading
            ) {
                if (viewModel.isLoading) CircularProgressIndicator(color = Color.White)
                else Text("Log In")
            }
        } else {
            // === SET NEW PASSWORD FORM (This appears for new users) ===
            Text("Set New Password", style = MaterialTheme.typography.headlineMedium)
            Text("For your security, please update your temporary password.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("New Permanent Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { viewModel.confirmNewPassword(newPassword, onLoginSuccess) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !viewModel.isLoading
            ) {
                if (viewModel.isLoading) CircularProgressIndicator(color = Color.White)
                else Text("Confirm & Sign In")
            }
        }
        Spacer(Modifier.height(16.dp))

        TextButton(onClick = onNavigateToSignUp) { // You need to add this parameter
            Text("Don't have an account? Sign Up")
        }

        // Error Display
        if (viewModel.uiState is LoginResult.Error) {
            Text(
                text = (viewModel.uiState as LoginResult.Error).message,
                color = Color.Red,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}