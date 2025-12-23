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

//@Composable
//fun MaestroApp(viewModel: MaestroViewModel = viewModel()) {
//  val transcriptionResult by viewModel.transcriptionResult.collectAsState()
//  var isRecording by remember { mutableStateOf(false) }
//
//  Column {
//    // Existing button to toggle voice recording
//    Button(onClick = {
//      isRecording = !isRecording
//      viewModel.toggleVoiceRecording(isRecording)
//    }) {
//      Text(if (isRecording) "Stop Recording" else "Start Recording")
//    }
//
//    // New UI Component: Display the result of the transcription
//    Text(
//      text = transcriptionResult,
//      style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
//    )
//
//    // Existing UI flow
//    MaestroScreen(
//      uiState = viewModel.uiState.collectAsState().value,
//      onStartExercise = viewModel::startChordProgressionExercise,
//      onSubmitAnswer = viewModel::checkTextAnswer,
//      onRepeat = viewModel::requestProgressionPlayback,
//      onReset = viewModel::reset
//    )
//  }
//}

//package com.openear.maestro.ui
//
//import androidx.compose.runtime.*
//import androidx.compose.ui.platform.LocalContext
//import androidx.lifecycle.viewmodel.compose.viewModel
//
//@Composable
//fun MaestroApp(viewModel: MaestroViewModel = viewModel()) {
//  var isRecording by remember { mutableStateOf(false) }
//
//  Column {
//    Button(onClick = {
//      isRecording = !isRecording
//      viewModel.toggleVoiceRecording(isRecording)
//    }) {
//      Text(if (isRecording) "Stop Recording" else "Start Recording")
//    }
//
//    // Existing UI
//    MaestroScreen(
//      uiState = viewModel.uiState.collectAsState().value,
//      onStartExercise = viewModel::startChordProgressionExercise,
//      onSubmitAnswer = viewModel::checkTextAnswer,
//      onRepeat = viewModel::requestProgressionPlayback,
//      onReset = viewModel::reset
//    )
//  }
//}

//@Composable
//fun MaestroApp(
//  viewModel: MaestroViewModel = viewModel()
//) {
//  val context = LocalContext.current
//
//  LaunchedEffect(Unit) {
//    viewModel.initialize(context)
//  }
//
//  MaestroScreen(
//    uiState = viewModel.uiState.collectAsState().value,
//    onStartExercise = viewModel::startChordProgressionExercise,
//    onSubmitAnswer = viewModel::checkTextAnswer,
//    onRepeat = viewModel::requestProgressionPlayback,
//    onReset = viewModel::reset
//  )
//}
