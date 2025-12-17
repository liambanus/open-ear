package com.openear.maestro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun MaestroApp(
    viewModel: MaestroViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (uiState.isFinished) {
            Text("Congratulations! You identified the progression correctly.", fontSize = 20.sp)
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = { viewModel.reset() }) {
                Text("Play Again")
            }
            return
        }

        if (uiState.showMainMenu) {
            Text("Main Menu", fontSize = 24.sp, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { viewModel.startChordProgressionExercise() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Chord Progression Exercise")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { /* Stub */ }, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text("Playback Mode (Not Implemented)")
            }
        } else {
            // Exercise Screen
            Text(uiState.userMessage, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(20.dp))

            if (uiState.isListening) {
                CircularProgressIndicator()
                Text("Listening...", modifier = Modifier.padding(top = 8.dp))
            } else {
                var textAnswer by remember { mutableStateOf(TextFieldValue("")) }
                OutlinedTextField(
                    value = textAnswer,
                    onValueChange = { textAnswer = it },
                    label = { Text("Enter progression (e.g., 1454)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    viewModel.checkTextAnswer(textAnswer.text)
                }) {
                    Text("Submit Text Answer")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = { viewModel.requestProgressionPlayback() }) {
                Text("Repeat Progression")
            }
        }
    }
}
