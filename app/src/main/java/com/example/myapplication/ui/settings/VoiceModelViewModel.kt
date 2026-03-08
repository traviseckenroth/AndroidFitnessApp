package com.example.myapplication.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.remote.VoiceModelDownloader
import com.example.myapplication.util.VoiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VoiceModelViewModel @Inject constructor(
    private val modelDownloader: VoiceModelDownloader,
    private val voiceManager: VoiceManager
) : ViewModel() {

    val downloadStatus = modelDownloader.downloadStatus
    val isReady = modelDownloader.isReady

    fun startDownload() {
        viewModelScope.launch {
            // Trigger the download manually
            modelDownloader.ensureModelsDownloaded()

            // Boot the engine as soon as it finishes
            if (modelDownloader.isReady.value) {
                voiceManager.initializeVoiceEngines()
            }
        }
    }
}