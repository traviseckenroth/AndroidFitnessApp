package com.example.myapplication.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _isAuthCheckComplete = MutableStateFlow(false)
    val isAuthCheckComplete = _isAuthCheckComplete.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn = _isLoggedIn.asStateFlow()

    init {
        viewModelScope.launch {
            // Check if we have a valid saved session
            _isLoggedIn.value = authRepository.autoLogin()

            // Add a brief 1.5 second delay so the Splash logo doesn't instantly flash and disappear
            delay(1500)
            _isAuthCheckComplete.value = true
        }
    }
}