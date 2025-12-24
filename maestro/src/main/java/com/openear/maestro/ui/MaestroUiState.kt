package com.openear.maestro.ui

data class MaestroUiState(
  val showMainMenu: Boolean = true,
  val isFinished: Boolean = false,
  val isListening: Boolean = false,
  val userMessage: String = "",
  val correctAnswer: String = "1454"
)
