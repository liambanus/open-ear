package com.openear.maestro.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
@Composable
fun MaestroApp(viewModel: MaestroViewModel = viewModel()) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsState()
  val transcriptionResult by viewModel.transcriptionResult.collectAsState()
  val burstSize by viewModel.burstSize.collectAsState()
  val autoReviewEnabled by viewModel.autoReviewEnabled.collectAsState()
  val referenceInstrument by viewModel.referenceInstrument.collectAsState()
  val snippetOptions by viewModel.snippetOptions.collectAsState()
  val selectedSnippetAssetPath by viewModel.selectedSnippetAssetPath.collectAsState()
  val loopProgressionLabels by viewModel.loopProgressionLabels.collectAsState()
  val isSnippetRecording by viewModel.isSnippetRecording.collectAsState()

  LaunchedEffect(Unit) {
    viewModel.initialize(context)
  }

  Column {
    var playbackLoopExpanded by remember { mutableStateOf(false) }
    var snippetExpanded by remember { mutableStateOf(false) }
    var selectedProgressionIndex by remember { mutableStateOf(0) }
    var renameSnippetText by remember { mutableStateOf("") }
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

      Row {
        OutlinedButton(onClick = { viewModel.decreaseBurstSize() }) {
          Text("-")
        }
        Text("Burst: $burstSize")
        OutlinedButton(onClick = { viewModel.increaseBurstSize() }) {
          Text("+")
        }
      }

      OutlinedButton(onClick = { viewModel.toggleAutoReviewEnabled() }) {
        Text(
          if (autoReviewEnabled) {
            "Auto Review: ON"
          } else {
            "Auto Review: OFF"
          }
        )
      }

      Button(onClick = { viewModel.startReviewOfMisidentified() }) {
        Text("Review Mistakes")
      }

      Text("Reference Instrument: $referenceInstrument")
      Row {
        OutlinedButton(onClick = { viewModel.setReferenceInstrument("piano") }) {
          Text("Piano")
        }
        OutlinedButton(onClick = { viewModel.setReferenceInstrument("guitar-acoustic") }) {
          Text("Guitar")
        }
      }
      Row {
        Button(onClick = { viewModel.playReferenceChord("1") }) { Text("Play I") }
        Button(onClick = { viewModel.playReferenceChord("b4") }) { Text("Play bIV") }
      }
      Row {
        Button(onClick = { viewModel.playReferenceChord("4") }) { Text("Play IV") }
        Button(onClick = { viewModel.playReferenceChord("b5") }) { Text("Play bV") }
      }
      Row {
        Button(onClick = { viewModel.playReferenceChord("5") }) { Text("Play V") }
        Button(onClick = { viewModel.playReferenceChord("b6") }) { Text("Play bVI") }
      }
      Row {
        Button(onClick = { viewModel.playReferenceChord("6") }) { Text("Play vi") }
      }

      val selectedSnippet = snippetOptions.firstOrNull { it.assetPath == selectedSnippetAssetPath }
      OutlinedButton(onClick = { snippetExpanded = true }) {
        Text("Snippet: ${selectedSnippet?.displayName ?: "None"}")
      }
      DropdownMenu(
        expanded = snippetExpanded,
        onDismissRequest = { snippetExpanded = false }
      ) {
        snippetOptions.forEach { snippet ->
          DropdownMenuItem(
            text = { Text("${snippet.displayName} (${snippet.progression})") },
            onClick = {
              viewModel.selectSnippet(snippet.assetPath)
              snippetExpanded = false
            }
          )
        }
      }
      Button(onClick = { viewModel.playSelectedSnippet() }) {
        Text("Play Snippet")
      }

      Button(onClick = { viewModel.toggleSnippetRecording(renameSnippetText) }) {
        Text(if (isSnippetRecording) "Stop Recording Snippet" else "Start Recording Snippet")
      }
      TextField(
        value = renameSnippetText,
        onValueChange = { renameSnippetText = it },
        label = { Text("Snippet Name / Rename") }
      )
      Button(
        onClick = {
          viewModel.renameSelectedSnippetLabel(renameSnippetText)
          renameSnippetText = ""
        }
      ) {
        Text("Rename Selected Snippet")
      }
      Button(onClick = { viewModel.deleteSelectedSnippet() }) {
        Text("Delete Selected Snippet")
      }

      // Playback loop controls
      OutlinedButton(onClick = { playbackLoopExpanded = true }) {
        Text("Loop: ${loopProgressionLabels.getOrElse(selectedProgressionIndex) { "N/A" }}")
      }
      DropdownMenu(
        expanded = playbackLoopExpanded,
        onDismissRequest = { playbackLoopExpanded = false }
      ) {
        loopProgressionLabels.forEachIndexed { index, label ->
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
        text = listOfNotNull(
          uiState.burstProgressText.takeIf { it.isNotBlank() }?.let { "Progress: $it" },
          uiState.currentInstrument.takeIf { it.isNotBlank() }?.let { "Instrument: $it" },
          uiState.heardTranscription.takeIf { it.isNotBlank() },
          uiState.burstSummaryText.takeIf { it.isNotBlank() },
          uiState.reviewMessage.takeIf { it.isNotBlank() },
          transcriptionResult.takeIf { it.isNotBlank() }
        ).joinToString("\n"),
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
