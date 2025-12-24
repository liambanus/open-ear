package com.openear.maestro.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import androidx.compose.material3.MaterialTheme
import com.openear.maestro.ui.MaestroUiState

@Composable
fun MaestroApp(viewModel: MaestroViewModel = viewModel()) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsState()
  val transcriptionResult by viewModel.transcriptionResult.collectAsState()

  LaunchedEffect(Unit) {
    viewModel.initialize(context)
  }

  Column {
    Button(
      onClick = {
        if (uiState.exerciseState == ExerciseState.IDLE) {
          viewModel.startExercise()
        } else {
          viewModel.stopExercise()
        }
      }
    ) {
      Text(
        if (uiState.exerciseState == ExerciseState.IDLE)
          "Start Recording"
        else
          "Stop Recording"
      )
    }

    Text(
      text = transcriptionResult,
      style = MaterialTheme.typography.bodyLarge
    )

    // 👇 CALL MaestroScreen here
    MaestroScreen(
      uiState = uiState,
      onStart = viewModel::startExercise,
      onStop = viewModel::stopExercise,
      onSubmitAnswer = viewModel::checkTextAnswer,
      onReset = viewModel::reset
    )
  }
}
