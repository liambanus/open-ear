package com.openear.maestro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MaestroScreen(
  uiState: MaestroUiState,
  onStartExercise: () -> Unit,
  onSubmitAnswer: (String) -> Unit,
  onRepeat: () -> Unit,
  onReset: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {

    when {
      uiState.isFinished -> {
        BasicText(
          "Congratulations! You identified the progression correctly.",
          style = TextStyle(fontSize = 20.sp)
        )
        Spacer(Modifier.height(20.dp))
        BasicText(
          "Restart the app to play again.",
          style = TextStyle(fontSize = 14.sp)
        )
      }

      uiState.showMainMenu -> {
        BasicText("Main Menu", style = TextStyle(fontSize = 24.sp))
        Spacer(Modifier.height(16.dp))
        BasicText(
          "Exercise will start automatically.",
          style = TextStyle(fontSize = 14.sp)
        )

        // Minimal, explicit side effect
        LaunchedEffect(Unit) {
          onStartExercise()
        }
      }

      else -> {
        // Exercise screen
        BasicText(uiState.userMessage, style = TextStyle(fontSize = 18.sp))
        Spacer(Modifier.height(20.dp))

        var textAnswer by remember { mutableStateOf(TextFieldValue("")) }

        BasicTextField(
          value = textAnswer,
          onValueChange = {
            textAnswer = it

            // Optional: submit automatically when input is non-empty and stable
            if (it.text.isNotBlank()) {
              onSubmitAnswer(it.text)
            }
          },
          textStyle = TextStyle(fontSize = 16.sp),
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
        BasicText(
          "Listening or playback repeats automatically.",
          style = TextStyle(fontSize = 14.sp)
        )
      }
    }
  }
}

//buttons complaining

//package com.openear.maestro.ui
//
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.text.BasicText
//import androidx.compose.foundation.text.BasicTextField
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.text.TextStyle
//import androidx.compose.ui.text.input.TextFieldValue
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//
//@Composable
//fun MaestroScreen(
//  uiState: MaestroUiState,
//  onStartExercise: () -> Unit,
//  onSubmitAnswer: (String) -> Unit,
//  onRepeat: () -> Unit,
//  onReset: () -> Unit
//) {
//  Column(
//    modifier = Modifier
//      .fillMaxSize()
//      .padding(16.dp),
//    horizontalAlignment = Alignment.CenterHorizontally,
//    verticalArrangement = Arrangement.Center
//  ) {
//
//    if (uiState.isFinished) {
//      BasicText(
//        "Congratulations! You identified the progression correctly.",
//        style = TextStyle(fontSize = 20.sp)
//      )
//      Spacer(Modifier.height(20.dp))
//      SimpleButton("Play Again", onReset)
//      return
//    }
//
//    if (uiState.showMainMenu) {
//      BasicText("Main Menu", style = TextStyle(fontSize = 24.sp))
//      Spacer(Modifier.height(32.dp))
//      SimpleButton("Chord Progression Exercise", onStartExercise)
//      return
//    }
//
//    // Exercise screen
//    BasicText(uiState.userMessage, style = TextStyle(fontSize = 18.sp))
//    Spacer(Modifier.height(20.dp))
//
//    var textAnswer by remember { mutableStateOf(TextFieldValue("")) }
//
//    BasicTextField(
//      value = textAnswer,
//      onValueChange = { textAnswer = it },
//      textStyle = TextStyle(fontSize = 16.sp),
//      modifier = Modifier.fillMaxWidth()
//    )
//
//    Spacer(Modifier.height(8.dp))
//    SimpleButton("Submit Text Answer") {
//      onSubmitAnswer(textAnswer.text)
//    }
//
//    Spacer(Modifier.height(20.dp))
//    SimpleButton("Repeat Progression", onRepeat)
//  }
//}
