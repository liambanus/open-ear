package com.openear.maestro.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import androidx.compose.material3.MaterialTheme


@Composable
fun MaestroApp(viewModel: MaestroViewModel = viewModel()) {
  val transcriptionResult by viewModel.transcriptionResult.collectAsState()
  var isRecording by remember { mutableStateOf(false) }
  val context = LocalContext.current

  // 🔥 AUTO-TRIGGER PLAYBACK ON APP START
  LaunchedEffect(Unit) {
    viewModel.initialize(context)
    delay(1_000)
    viewModel.requestProgressionPlayback()
  }

  Column {
    Button(onClick = {
      isRecording = !isRecording
      viewModel.toggleVoiceRecording(isRecording)
    }) {
      Text(if (isRecording) "Stop Recording" else "Start Recording")
    }

    Text(
      text = transcriptionResult,
      style = MaterialTheme.typography.bodyLarge
    )

    MaestroScreen(
      uiState = viewModel.uiState.collectAsState().value,
      onStartExercise = viewModel::startChordProgressionExercise,
      onSubmitAnswer = viewModel::checkTextAnswer,
      onRepeat = viewModel::requestProgressionPlayback,
      onReset = viewModel::reset
    )
  }
}
