package com.openear.maestro.ui

data class MaestroUiState(
  val exerciseState: ExerciseState = ExerciseState.IDLE,
  val userMessage: String = "",
  val correctAnswer: String = "",
  val heardTranscription: String = "",
  val currentInstrument: String = "",
  val burstProgressText: String = "",
  val burstSummaryText: String = "",
  val reviewMessage: String = ""
) {
  val showMainMenu: Boolean
    get() = exerciseState == ExerciseState.IDLE

  val isListening: Boolean
    get() = exerciseState == ExerciseState.LISTENING

  val isFinished: Boolean
    get() = exerciseState == ExerciseState.FEEDBACK
}

enum class ExerciseState {
  IDLE,
  PLAYING,
  LISTENING,
  EVALUATING,
  FEEDBACK,
  LOOPING,
  REVIEWING
}
