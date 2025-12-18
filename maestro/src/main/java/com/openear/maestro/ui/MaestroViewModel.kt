package com.openear.maestro.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import com.openear.maestro.service.VoiceControlService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MaestroViewModel : ViewModel() {

  private val _uiState = MutableStateFlow(MaestroUiState())
  val uiState: StateFlow<MaestroUiState> = _uiState.asStateFlow()

  private var voiceControlPort: VoiceControlService.Port? = null

  /* -----------------------------
   * Initialization
   * ----------------------------- */

  fun initialize(context: Context) {
    // Perform one-time setup here if needed
    // (audio engine, assets, etc.)
  }

  fun setVoiceControlPort(port: VoiceControlService.Port) {
    voiceControlPort = port
  }

  /* -----------------------------
   * Navigation / Flow
   * ----------------------------- */

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

  /* -----------------------------
   * Exercise Logic
   * ----------------------------- */

//  fun requestProgressionPlayback() {
//    // Stub: talk to audio engine / service later
//    // voiceControlPort can be used here when implemented
//  }

  fun requestProgressionPlayback() {
    android.util.Log.d("VOICE", "requestProgressionPlayback called, port=$voiceControlPort")
    val port = voiceControlPort ?: return

    // TEMP STUB: start voice listening immediately
    port.beginListening(
      expectedProgression = listOf("I", "V", "vi", "IV"),
      onRepeat = {
        android.util.Log.d("VOICE", "repeat")
      },
      onCorrect = {
        android.util.Log.d("VOICE", "correct")
      },
      onUnknown = {
        android.util.Log.d("VOICE", "unknown")
      }
    )
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
}
