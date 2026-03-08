package com.openear.maestro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
  val voicingOverlayPolicy by viewModel.voicingOverlayPolicy.collectAsState()
  val fixedVoicingTone by viewModel.fixedVoicingTone.collectAsState()
  val voicingBackingMode by viewModel.voicingBackingMode.collectAsState()
  val voicingTaskMode by viewModel.voicingTaskMode.collectAsState()
  val voicingAdvancedMix by viewModel.voicingAdvancedMix.collectAsState()

  LaunchedEffect(Unit) {
    viewModel.initialize(context)
  }

  var showRecordingManager by remember { mutableStateOf(false) }
  var playbackLoopExpanded by remember { mutableStateOf(false) }
  var snippetExpanded by remember { mutableStateOf(false) }
  var selectedProgressionIndex by remember { mutableStateOf(0) }
  var recordingSnippetExpanded by remember { mutableStateOf(false) }
  var recordingFolderExpanded by remember { mutableStateOf(false) }
  var snippetNameText by remember { mutableStateOf("") }

  val selectedSnippet = snippetOptions.firstOrNull { it.assetPath == selectedSnippetAssetPath }
  val selectedSnippetShort = selectedSnippet?.displayName?.takeLast(6) ?: "None"
  val statusText = listOfNotNull(
    uiState.userMessage.takeIf { it.isNotBlank() },
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

  Column(
    modifier = Modifier.fillMaxSize().padding(10.dp),
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(0.49f).padding(end = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          Button(
            onClick = {
              if (uiState.exerciseState == ExerciseState.IDLE) viewModel.startExercise()
              else viewModel.stopExercise()
            }
          ) {
            Text(if (uiState.exerciseState == ExerciseState.IDLE) "Start Quiz" else "Stop Quiz")
          }
          OutlinedButton(onClick = { viewModel.startReviewOfMisidentified() }) {
            Text("Rev")
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          OutlinedButton(onClick = { viewModel.decreaseBurstSize() }) { Text("-") }
          Text("Burst $burstSize", modifier = Modifier.padding(vertical = 12.dp))
          OutlinedButton(onClick = { viewModel.increaseBurstSize() }) { Text("+") }
        }
        OutlinedButton(onClick = { viewModel.toggleAutoReviewEnabled() }) {
          Text(if (autoReviewEnabled) "Auto Review ON" else "Auto Review OFF")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          OutlinedButton(
            onClick = {
              viewModel.setVoicingTaskMode(
                if (voicingTaskMode == VoicingTaskMode.PROGRESSION) {
                  VoicingTaskMode.CHORD_TONE
                } else {
                  VoicingTaskMode.PROGRESSION
                }
              )
            }
          ) {
            Text(if (voicingTaskMode == VoicingTaskMode.CHORD_TONE) "Mode: Tone" else "Mode: Prog")
          }
          OutlinedButton(onClick = { viewModel.toggleVoicingAdvancedMix() }) {
            Text(if (voicingAdvancedMix) "Adv*" else "Adv")
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          OutlinedButton(onClick = { viewModel.setVoicingBackingMode(VoicingBackingMode.PIANO_LOW) }) {
            Text(if (voicingBackingMode == VoicingBackingMode.PIANO_LOW) "PnoLo*" else "PnoLo")
          }
          OutlinedButton(onClick = { viewModel.setVoicingBackingMode(VoicingBackingMode.GUITAR_LOW) }) {
            Text(if (voicingBackingMode == VoicingBackingMode.GUITAR_LOW) "GtrLo*" else "GtrLo")
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          OutlinedButton(onClick = { viewModel.setVoicingOverlayPolicy(VoicingOverlayPolicy.FIXED) }) {
            Text(if (voicingOverlayPolicy == VoicingOverlayPolicy.FIXED) "Fixed*" else "Fixed")
          }
          OutlinedButton(onClick = { viewModel.setVoicingOverlayPolicy(VoicingOverlayPolicy.RANDOM) }) {
            Text(if (voicingOverlayPolicy == VoicingOverlayPolicy.RANDOM) "Random*" else "Random")
          }
        }
        if (voicingOverlayPolicy == VoicingOverlayPolicy.FIXED) {
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { viewModel.setFixedVoicingTone(VoicingTone.ROOT) }) {
              Text(if (fixedVoicingTone == VoicingTone.ROOT) "Rt*" else "Rt")
            }
            OutlinedButton(onClick = { viewModel.setFixedVoicingTone(VoicingTone.THIRD) }) {
              Text(if (fixedVoicingTone == VoicingTone.THIRD) "3rd*" else "3rd")
            }
            OutlinedButton(onClick = { viewModel.setFixedVoicingTone(VoicingTone.FIFTH) }) {
              Text(if (fixedVoicingTone == VoicingTone.FIFTH) "5th*" else "5th")
            }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { viewModel.setFixedVoicingTone(VoicingTone.FOURTH) }) {
              Text(if (fixedVoicingTone == VoicingTone.FOURTH) "4th*" else "4th")
            }
            OutlinedButton(onClick = { viewModel.setFixedVoicingTone(VoicingTone.SIXTH) }) {
              Text(if (fixedVoicingTone == VoicingTone.SIXTH) "6th*" else "6th")
            }
            OutlinedButton(onClick = { viewModel.setFixedVoicingTone(VoicingTone.SEVENTH) }) {
              Text(if (fixedVoicingTone == VoicingTone.SEVENTH) "7th*" else "7th")
            }
          }
        }
      }

      Column(
        modifier = Modifier.fillMaxWidth(0.49f).padding(start = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        OutlinedButton(onClick = { playbackLoopExpanded = true }) {
          Text("Loop ${loopProgressionLabels.getOrElse(selectedProgressionIndex) { "N/A" }}")
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
        OutlinedButton(
          onClick = {
            if (uiState.exerciseState == ExerciseState.LOOPING) viewModel.stopPlaybackLoop()
            else viewModel.startPlaybackLoop(selectedProgressionIndex)
          }
        ) {
          Text(if (uiState.exerciseState == ExerciseState.LOOPING) "Stop Loop" else "Start Loop")
        }

        OutlinedButton(onClick = { snippetExpanded = true }) {
          Text("Snippet: $selectedSnippetShort")
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

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          OutlinedButton(onClick = { viewModel.playSelectedSnippet() }) { Text("Play $selectedSnippetShort") }
          OutlinedButton(onClick = { showRecordingManager = true }) { Text("Recordings") }
        }
      }
    }

    MaestroScreen(
      uiState = uiState,
      onStart = viewModel::startExercise,
      onStop = viewModel::stopExercise,
      onSubmitAnswer = viewModel::checkTextAnswer,
      onReset = viewModel::reset
    )

    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
      ) {
        Button(onClick = { viewModel.playReferenceChord("1") }) { Text("I") }
        Button(onClick = { viewModel.playReferenceChord("4") }) { Text("IV") }
        Button(onClick = { viewModel.playReferenceChord("5") }) { Text("V") }
        Button(onClick = { viewModel.playReferenceChord("6") }) { Text("vi") }
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
      ) {
        Button(onClick = { viewModel.playReferenceChord("lo4") }) { Text("loIV") }
        Button(onClick = { viewModel.playReferenceChord("lo5") }) { Text("loV") }
        Button(onClick = { viewModel.playReferenceChord("lo6") }) { Text("loVI") }
        OutlinedButton(
          onClick = {
            viewModel.setReferenceInstrument(
              if (referenceInstrument == "guitar-acoustic") "piano" else "guitar-acoustic"
            )
          }
        ) {
          Text(if (referenceInstrument == "guitar-acoustic") "Gtr" else "Pno")
        }
      }
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(140.dp)
        .padding(top = 6.dp)
        .background(MaterialTheme.colorScheme.secondaryContainer)
        .padding(12.dp)
    ) {
      Text(
        text = statusText.ifBlank { "Waiting for quiz activity..." },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSecondaryContainer
      )
    }
  }
}
