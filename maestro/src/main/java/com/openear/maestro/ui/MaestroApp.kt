package com.openear.maestro.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun MaestroApp(
  viewModel: MaestroViewModel = viewModel()
) {
  val context = LocalContext.current

  LaunchedEffect(Unit) {
    viewModel.initialize(context)
  }

  MaestroScreen(
    uiState = viewModel.uiState.collectAsState().value,
    onStartExercise = viewModel::startChordProgressionExercise,
    onSubmitAnswer = viewModel::checkTextAnswer,
    onRepeat = viewModel::requestProgressionPlayback,
    onReset = viewModel::reset
  )
}
