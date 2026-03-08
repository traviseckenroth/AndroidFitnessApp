package com.example.myapplication.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.remote.VoiceModelDownloader
import com.example.myapplication.data.repository.AuthRepository
import com.example.myapplication.util.VoiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val modelDownloader: VoiceModelDownloader,
    private val voiceManager: VoiceManager,
    private val authRepository: AuthRepository
) : ViewModel() {

    // Pass the state directly to the UI
    val downloadStatus = modelDownloader.downloadStatus
    val isReady = modelDownloader.isReady

    init {
        viewModelScope.launch {
            // 1. Try to refresh existing session so we have authenticated credentials if possible
            authRepository.autoLogin()

            // 2. Start the secure S3 download (or verification)
            modelDownloader.ensureModelsDownloaded()

            // 3. ONLY once the files exist, safely boot Sherpa-ONNX
            if (modelDownloader.isReady.value) {
                voiceManager.initializeVoiceEngines()
            }
        }
    }
}
