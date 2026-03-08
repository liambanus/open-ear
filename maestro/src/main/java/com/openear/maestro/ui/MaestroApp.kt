package com.openear.maestro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

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
  val recordingFolder by viewModel.recordingFolder.collectAsState()
  val voicingEnabled by viewModel.voicingEnabled.collectAsState()
  val voicingOverlayPolicy by viewModel.voicingOverlayPolicy.collectAsState()
  val fixedVoicingTone by viewModel.fixedVoicingTone.collectAsState()
  val voicingBackingMode by viewModel.voicingBackingMode.collectAsState()
  val voicingTaskMode by viewModel.voicingTaskMode.collectAsState()

  LaunchedEffect(Unit) {
    viewModel.initialize(context)
  }

  var showRecordingManager by remember { mutableStateOf(false) }
  var playbackLoopExpanded by remember { mutableStateOf(false) }
  var snippetExpanded by remember { mutableStateOf(false) }
  var referenceInstrumentExpanded by remember { mutableStateOf(false) }
  var selectedProgressionIndex by remember { mutableStateOf(0) }
  var recordingSnippetExpanded by remember { mutableStateOf(false) }
  var recordingFolderExpanded by remember { mutableStateOf(false) }
  var snippetNameText by remember { mutableStateOf("") }

  val selectedSnippet = snippetOptions.firstOrNull { it.assetPath == selectedSnippetAssetPath }
  val statusText = listOfNotNull(
    uiState.burstProgressText.takeIf { it.isNotBlank() }?.let { "Progress: $it" },
    uiState.currentInstrument.takeIf { it.isNotBlank() }?.let { "Instrument: $it" },
    uiState.heardTranscription.takeIf { it.isNotBlank() },
    uiState.burstSummaryText.takeIf { it.isNotBlank() },
    uiState.reviewMessage.takeIf { it.isNotBlank() },
    transcriptionResult.takeIf { it.isNotBlank() }
  ).joinToString("\n")

  if (showRecordingManager) {
    Column(modifier = Modifier.padding(12.dp)) {
      Button(onClick = { showRecordingManager = false }) {
        Text("Back To Quiz")
      }

      OutlinedButton(onClick = { recordingSnippetExpanded = true }) {
        Text("Snippet: ${selectedSnippet?.displayName ?: "None"}")
      }
      DropdownMenu(
        expanded = recordingSnippetExpanded,
        onDismissRequest = { recordingSnippetExpanded = false }
      ) {
        snippetOptions.forEach { snippet ->
          DropdownMenuItem(
            text = { Text("${snippet.displayName} (${snippet.progression})") },
            onClick = {
              viewModel.selectSnippet(snippet.assetPath)
              recordingSnippetExpanded = false
            }
          )
        }
      }

      Button(onClick = { viewModel.playSelectedSnippet() }) {
        Text("Play Selected")
      }

      Row {
        Text("Folder ")
        OutlinedButton(onClick = { recordingFolderExpanded = true }) {
          Text(recordingFolder.directoryName)
        }
        DropdownMenu(
          expanded = recordingFolderExpanded,
          onDismissRequest = { recordingFolderExpanded = false }
        ) {
          RecordingFolder.entries.forEach { folder ->
            DropdownMenuItem(
              text = { Text(folder.directoryName) },
              onClick = {
                viewModel.setRecordingFolder(folder)
                recordingFolderExpanded = false
              }
            )
          }
        }
      }

      Button(onClick = { viewModel.toggleSnippetRecording(snippetNameText) }) {
        Text(if (isSnippetRecording) "Stop Recording Snippet" else "Start Recording Snippet")
      }

      TextField(
        value = snippetNameText,
        onValueChange = { snippetNameText = it },
        label = { Text("Snippet Name / Rename") }
      )

      Button(
        onClick = {
          viewModel.renameSelectedSnippetLabel(snippetNameText)
          snippetNameText = ""
        }
      ) {
        Text("Rename Selected")
      }

      Button(onClick = { viewModel.deleteSelectedSnippet() }) {
        Text("Delete Selected")
      }
    }
    return
  }

  Column(modifier = Modifier.padding(12.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
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
            "Start Quiz"
          else
            "Stop Quiz"
        )
      }

      Column {
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
          Text(if (uiState.exerciseState == ExerciseState.LOOPING) "Stop Loop" else "Start Loop")
        }
      }
    }

    Row {
      OutlinedButton(onClick = { viewModel.decreaseBurstSize() }) { Text("-") }
      Text("Burst: $burstSize", modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp))
      OutlinedButton(onClick = { viewModel.increaseBurstSize() }) { Text("+") }
    }

    OutlinedButton(onClick = { viewModel.toggleAutoReviewEnabled() }) {
      Text(if (autoReviewEnabled) "Auto Review: ON" else "Auto Review: OFF")
    }

    Button(onClick = { viewModel.startReviewOfMisidentified() }) {
      Text("Review Mistakes")
    }

    OutlinedButton(onClick = { viewModel.toggleVoicingMode() }) {
      Text(if (voicingEnabled) "Voicing Mode: ON" else "Voicing Mode: OFF")
    }
    Row {
      OutlinedButton(
        onClick = { viewModel.setVoicingTaskMode(VoicingTaskMode.PROGRESSION) }
      ) {
        Text(if (voicingTaskMode == VoicingTaskMode.PROGRESSION) "Progression*" else "Progression")
      }
      OutlinedButton(
        onClick = { viewModel.setVoicingTaskMode(VoicingTaskMode.CHORD_TONE) }
      ) {
        Text(if (voicingTaskMode == VoicingTaskMode.CHORD_TONE) "Chord Tone*" else "Chord Tone")
      }
    }
    Row {
      OutlinedButton(
        onClick = { viewModel.setVoicingBackingMode(VoicingBackingMode.GUITAR_LOW) }
      ) {
        Text(if (voicingBackingMode == VoicingBackingMode.GUITAR_LOW) "Guitar Low*" else "Guitar Low")
      }
      OutlinedButton(
        onClick = { viewModel.setVoicingBackingMode(VoicingBackingMode.PIANO_LOW) }
      ) {
        Text(if (voicingBackingMode == VoicingBackingMode.PIANO_LOW) "Piano Low*" else "Piano Low")
      }
    }
    Row {
      OutlinedButton(
        onClick = { viewModel.setVoicingOverlayPolicy(VoicingOverlayPolicy.FIXED) }
      ) {
        Text(if (voicingOverlayPolicy == VoicingOverlayPolicy.FIXED) "Fixed*" else "Fixed")
      }
      OutlinedButton(
        onClick = { viewModel.setVoicingOverlayPolicy(VoicingOverlayPolicy.RANDOM) }
      ) {
        Text(if (voicingOverlayPolicy == VoicingOverlayPolicy.RANDOM) "Random*" else "Random")
      }
    }
    if (voicingOverlayPolicy == VoicingOverlayPolicy.FIXED) {
      Row {
        OutlinedButton(onClick = { viewModel.setFixedVoicingTone(VoicingTone.ROOT) }) {
          Text(if (fixedVoicingTone == VoicingTone.ROOT) "Root*" else "Root")
        }
        OutlinedButton(onClick = { viewModel.setFixedVoicingTone(VoicingTone.THIRD) }) {
          Text(if (fixedVoicingTone == VoicingTone.THIRD) "Third*" else "Third")
        }
        OutlinedButton(onClick = { viewModel.setFixedVoicingTone(VoicingTone.FIFTH) }) {
          Text(if (fixedVoicingTone == VoicingTone.FIFTH) "Fifth*" else "Fifth")
        }
      }
    }

    Row {
      Text("Reference Instrument ")
      OutlinedButton(onClick = { referenceInstrumentExpanded = true }) {
        Text(if (referenceInstrument == "guitar-acoustic") "Guitar" else "Piano")
      }
      DropdownMenu(
        expanded = referenceInstrumentExpanded,
        onDismissRequest = { referenceInstrumentExpanded = false }
      ) {
        DropdownMenuItem(
          text = { Text("Piano") },
          onClick = {
            viewModel.setReferenceInstrument("piano")
            referenceInstrumentExpanded = false
          }
        )
        DropdownMenuItem(
          text = { Text("Guitar") },
          onClick = {
            viewModel.setReferenceInstrument("guitar-acoustic")
            referenceInstrumentExpanded = false
          }
        )
      }
    }

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

    Row {
      Button(onClick = { viewModel.playSelectedSnippet() }) {
        Text("Play Snippet")
      }
      OutlinedButton(onClick = { showRecordingManager = true }) {
        Text("Manage Recordings")
      }
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 14.dp)
        .background(MaterialTheme.colorScheme.secondaryContainer)
        .padding(12.dp)
    ) {
      Text(
        text = statusText.ifBlank { "Waiting for quiz activity..." },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSecondaryContainer
      )
    }

    MaestroScreen(
      uiState = uiState,
      onStart = viewModel::startExercise,
      onStop = viewModel::stopExercise,
      onSubmitAnswer = viewModel::checkTextAnswer,
      onReset = viewModel::reset
    )

    Row {
      Button(onClick = { viewModel.playReferenceChord("1") }) { Text("Play I") }
      Button(onClick = { viewModel.playReferenceChord("lo4") }) { Text("Play loIV") }
    }
    Row {
      Button(onClick = { viewModel.playReferenceChord("4") }) { Text("Play IV") }
      Button(onClick = { viewModel.playReferenceChord("lo5") }) { Text("Play loV") }
    }
    Row {
      Button(onClick = { viewModel.playReferenceChord("5") }) { Text("Play V") }
      Button(onClick = { viewModel.playReferenceChord("lo6") }) { Text("Play loVI") }
    }
    Row {
      Button(onClick = { viewModel.playReferenceChord("6") }) { Text("Play vi") }
    }
  }
}
