package com.openear.maestro.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import com.openear.maestro.service.VoiceControlService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import android.util.Log

import android.media.MediaPlayer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume


class MaestroViewModel : ViewModel() {

  private val _uiState = MutableStateFlow(MaestroUiState())
  val uiState: StateFlow<MaestroUiState> = _uiState.asStateFlow()

  private lateinit var assetPlayer: AssetPlayer
  private var voiceControlPort: VoiceControlService.Port? = null
  private val progressions = listOf(listOf("1","4","5","4"), listOf("1","5","1","4"), listOf("5","4","1","4"), listOf("5","4","5","1"))
  private var currentProgressionIndex = 0
  private val random = java.util.Random()
  private var recordingActive = false

  private var exerciseJob: kotlinx.coroutines.Job? = null

  // Added state to store the result of transcription (correct/incorrect feedback)
  private val _transcriptionResult = MutableStateFlow("")
  val transcriptionResult: StateFlow<String> = _transcriptionResult.asStateFlow()

  private var playbackLoopActive = false
  private var playbackLoopJob: kotlinx.coroutines.Job? = null

  private fun evaluateFeedback(expected: List<String>, heard: List<String>): String {
    if (heard.isEmpty()) return "Didn't catch that, try again."
    val matches = expected.zip(heard).count { (e, h) -> e == h }
    return when {
      matches == expected.size -> "Correct!"
      matches == expected.size - 1 -> {
        val wrongPos = expected.zip(heard).indexOfFirst { (e, h) -> e != h }
        val ordinal = listOf("First", "Second", "Third", "Fourth").getOrElse(wrongPos) { "${wrongPos+1}th" }
        "Almost! $ordinal chord wrong."
      }
      else -> "Incorrect. You said: ${heard.joinToString("-").ifEmpty { "nothing" }}"
    }
  }
  fun startPlaybackLoop(progressionIndex: Int) {
    playbackLoopActive = true
    playbackLoopJob = viewModelScope.launch {
      val progression = progressions[progressionIndex]
      _uiState.value = _uiState.value.copy(
//        exerciseState = ExerciseState.PLAYING,
        exerciseState = ExerciseState.LOOPING,
        userMessage = "Looping: ${progression.joinToString("-")}"
      )
      while (playbackLoopActive) {
        playProgression(progression)
        delay(500)
      }
    }
  }

  fun stopPlaybackLoop() {
    playbackLoopActive = false
    playbackLoopJob?.cancel()
    playbackLoopJob = null
    _uiState.value = _uiState.value.copy(
      exerciseState = ExerciseState.IDLE,
      userMessage = ""
    )
  }

   fun initialize(context: Context) {
    assetPlayer = AssetPlayer(context.applicationContext)
  }

  private suspend fun playProgression(progression: List<String>) {
    for (chord in progression) {
      assetPlayer.playChord(chord, "piano")
      delay(500) // gap between chords
    }
  }

  fun setVoiceControlPort(port: VoiceControlService.Port) {
    voiceControlPort = port
  }

  fun startChordProgressionExercise() {
    _uiState.value = _uiState.value.copy(
      exerciseState = ExerciseState.PLAYING,
      userMessage = "Listen to the progression and enter your answer."
    )


    // Trigger playback if needed
    requestProgressionPlayback()
  }

  fun reset() {
    _uiState.value = MaestroUiState(exerciseState = ExerciseState.IDLE)
  }

  fun requestProgressionPlayback() {
    val port = voiceControlPort ?: return

    _uiState.value = _uiState.value.copy(
      exerciseState = ExerciseState.PLAYING,
      userMessage = "Listen to the progression..."
    )

    viewModelScope.launch {
      val progression = listOf("1", "4", "5", "4")

      playProgression(progression)

      delay(1000)

      _uiState.value = _uiState.value.copy(
        exerciseState = ExerciseState.LISTENING,
        userMessage = "Now say the progression"
      )

      port.beginListening(
        expectedProgression = progression,
        onCorrect = {
          _uiState.value = _uiState.value.copy(
            exerciseState = ExerciseState.FEEDBACK,
            userMessage = "Correct!"
          )

        },
        onIncorrect = {
          _uiState.value = _uiState.value.copy(
            exerciseState = ExerciseState.FEEDBACK,
            userMessage = "Incorrect."
          )
        }
      )
    }
  }

  fun checkTextAnswer(answer: String) {
    val isCorrect = answer.trim() == _uiState.value.correctAnswer

    _uiState.value = if (isCorrect) {
      _uiState.value.copy(
        exerciseState = ExerciseState.FEEDBACK,
        userMessage = "Correct!"
      )
    } else {
      _uiState.value.copy(
        userMessage = "Try again."
      )
    }
  }



  fun startExercise() {
    startExerciseLoop()
  }

  fun stopExercise() {
    stopVoiceListening()
  }

  private fun startExerciseLoop() {
    recordingActive = true
    _uiState.value = _uiState.value.copy(
      exerciseState = ExerciseState.LISTENING,
      userMessage = "Listen to the progression and say it out loud."
    )

    exerciseJob = viewModelScope.launch {
      val port = voiceControlPort ?: return@launch
      while (recordingActive) {
        val progression = progressions[currentProgressionIndex]
        playProgression(progression)            // play chord progression
        // Listen for one attempt (5 seconds recording)
        val (correct, heard) = suspendCancellableCoroutine<Pair<Boolean, List<String>>> { cont ->
          port.beginListening(
            expectedProgression = progression,
            onCorrect = {
              if (cont.isActive) cont.resume(true to emptyList())
            },
            onIncorrect = { heard ->
              if (cont.isActive) cont.resume(false to heard)
            }
          )
          cont.invokeOnCancellation {
            port.stopListening()
          }
        }
        if (!recordingActive) return@launch
        if (correct) {
          _uiState.value = _uiState.value.copy(
            exerciseState = ExerciseState.FEEDBACK,
            userMessage = "Correct!"
          )
          delay(1000)
          currentProgressionIndex = random.nextInt(progressions.size)
          _uiState.value = _uiState.value.copy(
            exerciseState = ExerciseState.PLAYING,
            correctAnswer = progressions[currentProgressionIndex].joinToString(""),
            userMessage = "Listen to the progression and say it out loud."
          )
          // loop continues — remove the break
        }
        else {
          // Incorrect, will play progression again
          _uiState.value = _uiState.value.copy(
            exerciseState = ExerciseState.PLAYING,
            userMessage = evaluateFeedback(progression, heard),
            heardTranscription = if (heard.isEmpty()) "" else "I heard: ${heard.joinToString("-")}"
          )

        }
      }
    }
  }

  private fun stopVoiceListening() {
    recordingActive = false
    exerciseJob?.cancel()
    exerciseJob = null
    voiceControlPort?.stopListening()
    _uiState.value = _uiState.value.copy(
      exerciseState = ExerciseState.IDLE,
      userMessage = ""
    )
  }


  // Helper method to update transcription results
  private fun updateTranscriptionResult(message: String) {
    _transcriptionResult.value = message
  }
}


class AssetPlayer(private val context: Context) {

  private val chordMap = mapOf(
    "1" to listOf("C4.mp3", "E4.mp3", "G4.mp3"),
    "4" to listOf("F4.mp3", "A4.mp3", "C5.mp3"),
    "5" to listOf("G4.mp3", "B4.mp3", "D5.mp3")
  )

  suspend fun playChord(chord: String, instrument: String) {
    val notes = chordMap[chord] ?: return

    notes.map { noteFile ->
      MediaPlayer().apply {
        val afd = context.assets.openFd("$instrument/$noteFile")
        Log.d("AUDIO", "Opened asset $instrument/$noteFile")
        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        afd.close()
        prepare()
      }
    }.forEach { mp ->
      mp.start()
      mp.setOnCompletionListener { it.release() }
    }

    delay(1000) // chord duration
  }
}


//  fun toggleVoiceRecording(isRecording: Boolean) {
//    if (isRecording) {
//      startExerciseLoop()
//    } else {
//      stopVoiceListening()
//    }
//  }
