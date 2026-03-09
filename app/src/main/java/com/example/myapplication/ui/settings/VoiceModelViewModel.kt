package com.example.myapplication.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.remote.VoiceModelDownloader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VoiceModelViewModel @Inject constructor(
    private val modelDownloader: VoiceModelDownloader
    // Removed VoiceManager from here so it doesn't crash the Settings UI!
) : ViewModel() {

    val downloadStatus = modelDownloader.downloadStatus
    val isReady = modelDownloader.isReady
    val isDownloading = modelDownloader.isDownloading

    init {
        viewModelScope.launch(Dispatchers.IO) {
            modelDownloader.checkLocalFiles()
        }
    }

    fun startDownload() {
        viewModelScope.launch(Dispatchers.IO) {
            modelDownloader.ensureModelsDownloaded()
        }
    }
}