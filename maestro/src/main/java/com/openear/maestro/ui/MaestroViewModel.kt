package com.openear.maestro.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import com.openear.maestro.service.VoiceControlService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import android.util.Log

import android.media.MediaPlayer


class MaestroViewModel : ViewModel() {

  private val _uiState = MutableStateFlow(MaestroUiState())
  val uiState: StateFlow<MaestroUiState> = _uiState.asStateFlow()

  private lateinit var assetPlayer: AssetPlayer
  private var voiceControlPort: VoiceControlService.Port? = null

  // Added state to store the result of transcription (correct/incorrect feedback)
  private val _transcriptionResult = MutableStateFlow("")
  val transcriptionResult: StateFlow<String> = _transcriptionResult.asStateFlow()

   fun initialize(context: Context) {
    assetPlayer = AssetPlayer(context.applicationContext)
  }

  private suspend fun playProgression(progression: List<String>) {
    for (chord in progression) {
      assetPlayer.playChord(chord, "piano")
      delay(500) // gap between chords
    }
  }

  fun setVoiceControlPort(port: VoiceControlService.Port) {
    voiceControlPort = port
  }

  fun startChordProgressionExercise() {
    _uiState.value = _uiState.value.copy(
      showMainMenu = false,
      userMessage = "Listen to the progression and enter your answer."
    )

    // Trigger playback if needed
    requestProgressionPlayback()
  }

  fun reset() {
    _uiState.value = MaestroUiState()
  }

  fun requestProgressionPlayback() {
    val port = voiceControlPort ?: return

    _uiState.value = _uiState.value.copy(
      userMessage = "Listen to the progression...",
      isListening = false
    )

    viewModelScope.launch {
      playProgression(listOf("1", "4", "5", "4"))

      delay(1000)

      _uiState.value = _uiState.value.copy(
        userMessage = "Now say the progression",
        isListening = true
      )

      port.beginListening(
        expectedProgression = listOf("1", "4", "5", "4"),
        onCorrect = {
          _uiState.value = _uiState.value.copy(
            isFinished = true,
            isListening = false,
            userMessage = "Correct!"
          )
        }
      )
    }
  }

  fun checkTextAnswer(answer: String) {
    val isCorrect = answer.trim() == _uiState.value.correctAnswer

    _uiState.value = if (isCorrect) {
      _uiState.value.copy(
        isFinished = true,
        userMessage = "Correct!"
      )
    } else {
      _uiState.value.copy(
        userMessage = "Try again."
      )
    }
  }

  fun toggleVoiceRecording(isRecording: Boolean) {
    if (isRecording) {
      startVoiceListening()
    }
  }

  private fun startVoiceListening() {
    android.util.Log.d("VOICE_CONTROL", "Started voice listening.")
    voiceControlPort?.beginListening(
      expectedProgression = listOf("1", "4", "5", "4"),
      onCorrect = {
        updateTranscriptionResult("Correct!")
        _uiState.value = _uiState.value.copy(
          isFinished = true,
          userMessage = "Correct!"
        )
      }
    )
  }

  private fun stopVoiceListening() {
    android.util.Log.d("VOICE_CONTROL", "Stopped voice listening.")
  }

  // Helper method to update transcription results
  private fun updateTranscriptionResult(message: String) {
    _transcriptionResult.value = message
  }
}


class AssetPlayer(private val context: Context) {

  private val chordMap = mapOf(
    "1" to listOf("C4.mp3", "E4.mp3", "G4.mp3"),
    "4" to listOf("F4.mp3", "A4.mp3", "C5.mp3"),
    "5" to listOf("G4.mp3", "B4.mp3", "D5.mp3")
  )

  suspend fun playChord(chord: String, instrument: String) {
    val notes = chordMap[chord] ?: return

    notes.map { noteFile ->
      MediaPlayer().apply {
        val afd = context.assets.openFd("$instrument/$noteFile")
        Log.d("AUDIO", "Opened asset $instrument/$noteFile")
        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        afd.close()
        prepare()
      }
    }.forEach { mp ->
      mp.start()
      mp.setOnCompletionListener { it.release() }
    }

    delay(1000) // chord duration
  }
}
