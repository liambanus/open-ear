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

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
@Composable
fun MaestroApp(viewModel: MaestroViewModel = viewModel()) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsState()
  val transcriptionResult by viewModel.transcriptionResult.collectAsState()

  LaunchedEffect(Unit) {
    viewModel.initialize(context)
  }

  Column {
    var playbackLoopExpanded by remember { mutableStateOf(false) }
    var selectedProgressionIndex by remember { mutableStateOf(0) }
    val progressionLabels = listOf("1-4-5-4", "1-5-1-4", "5-4-1-4", "5-4-5-1")

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

      // Playback loop controls
      OutlinedButton(onClick = { playbackLoopExpanded = true }) {
        Text("Loop: ${progressionLabels[selectedProgressionIndex]}")
      }
      DropdownMenu(
        expanded = playbackLoopExpanded,
        onDismissRequest = { playbackLoopExpanded = false }
      ) {
        progressionLabels.forEachIndexed { index, label ->
          DropdownMenuItem(
            text = { Text(label) },
            onClick = {
              selectedProgressionIndex = index
              playbackLoopExpanded = false
            }
          )
        }
      }

      Button(
        onClick = {
          if (uiState.exerciseState == ExerciseState.LOOPING) {
            viewModel.stopPlaybackLoop()
          } else {
            viewModel.startPlaybackLoop(selectedProgressionIndex)
          }
        }
      ) {
        Text(
          if (uiState.exerciseState == ExerciseState.LOOPING)
            "Stop Loop"
          else
            "Start Loop"
        )
      }

      Text(
        text = transcriptionResult,
        style = MaterialTheme.typography.bodyLarge
      )

      MaestroScreen(
        uiState = uiState,
        onStart = viewModel::startExercise,
        onStop = viewModel::stopExercise,
        onSubmitAnswer = viewModel::checkTextAnswer,
        onReset = viewModel::reset
      )
    }
  }
}
