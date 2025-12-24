package com.openear.maestro.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MaestroScreen(
  uiState: MaestroUiState,
  onStart: () -> Unit,
  onStop: () -> Unit,
  onSubmitAnswer: (String) -> Unit, // kept for future text input
  onReset: () -> Unit
) {
  Column {
    when (uiState.exerciseState) {

      ExerciseState.IDLE -> {
        Button(onClick = onStart) {
          Text("Start")
        }
      }

      ExerciseState.PLAYING,
      ExerciseState.LISTENING -> {
        Text(uiState.userMessage)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onStop) {
          Text("Stop")
        }
      }

      ExerciseState.FEEDBACK -> {
        Text(uiState.userMessage)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onReset) {
          Text("Reset")
        }
      }

      ExerciseState.EVALUATING -> {
        Text("Evaluating…")
      }
    }
  }
}
