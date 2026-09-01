// @dikaacode
package com.example.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object RecordingState {
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused
    
    private val _durationSeconds = MutableStateFlow(0)
    val durationSeconds: StateFlow<Int> = _durationSeconds

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    private val _savedMessage = MutableStateFlow<String?>(null)
    val savedMessage: StateFlow<String?> = _savedMessage

    fun setRecording(recording: Boolean) { _isRecording.value = recording }
    fun setPaused(paused: Boolean) { _isPaused.value = paused }
    fun setDuration(seconds: Int) { _durationSeconds.value = seconds }
    fun setError(message: String?) { _error.value = message }
    fun setSavedMessage(message: String?) { _savedMessage.value = message }
}
