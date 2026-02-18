package com.example.myapplication.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.UserPreferencesRepository
import com.example.myapplication.data.repository.AuthRepository
import com.example.myapplication.data.repository.SignUpResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userPrefs: UserPreferencesRepository
) : ViewModel() {
    var isLoading by mutableStateOf(false)
    var errorMsg by mutableStateOf<String?>(null)
    var isCodeSent by mutableStateOf(false)

    fun register(name: String, email: String, pass: String) {
        viewModelScope.launch {
            isLoading = true
            errorMsg = null
            when (val result = authRepository.signUp(name, email, pass)) {
                is SignUpResult.Success -> {
                    isCodeSent = true
                    userPrefs.saveUserName(name)
                }
                is SignUpResult.Error -> errorMsg = result.message
                else -> {}
            }
            isLoading = false
        }
    }

    fun confirm(email: String, code: String, onVerified: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            errorMsg = null
            when (val result = authRepository.confirmUser(email, code)) {
                is SignUpResult.Confirmed -> onVerified()
                is SignUpResult.Error -> errorMsg = result.message
                else -> {}
            }
            isLoading = false
        }
    }
}

@Composable
fun SignUpScreen(
    onSignUpSuccess: () -> Unit,
    onBackToLogin: () -> Unit,
    viewModel: SignUpViewModel = hiltViewModel()
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!viewModel.isCodeSent) {
            Text("Create Account", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("Email") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { viewModel.register(name, email, password) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !viewModel.isLoading
            ) {
                if (viewModel.isLoading) CircularProgressIndicator(color = Color.White)
                else Text("Sign Up")
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onBackToLogin) {
                Text("Already have an account? Log In")
            }

        } else {
            Text("Verify Email", style = MaterialTheme.typography.headlineMedium)
            Text("Enter the code sent to $email", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = code, onValueChange = { code = it },
                label = { Text("Confirmation Code") }, modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { viewModel.confirm(email, code, onSignUpSuccess) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !viewModel.isLoading
            ) {
                if (viewModel.isLoading) CircularProgressIndicator(color = Color.White)
                else Text("Verify & Login")
            }
        }

        if (viewModel.errorMsg != null) {
            Text(viewModel.errorMsg!!, color = Color.Red, modifier = Modifier.padding(top = 16.dp))
        }
    }
}