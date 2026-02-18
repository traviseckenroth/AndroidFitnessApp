// File: app/src/main/java/com/example/myapplication/ui/workout/ContentViewModel.kt
package com.example.myapplication.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.ContentSourceEntity
import com.example.myapplication.data.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContentViewModel @Inject constructor(
    private val repository: ContentRepository
) : ViewModel() {

    private val _content = MutableStateFlow<ContentSourceEntity?>(null)
    val content: StateFlow<ContentSourceEntity?> = _content.asStateFlow()

    fun loadContent(id: Long) {
        viewModelScope.launch {
            repository.getContentById(id).collect {
                _content.value = it
            }
        }
    }
}