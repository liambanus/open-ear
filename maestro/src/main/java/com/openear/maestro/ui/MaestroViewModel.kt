package com.openear.maestro.ui

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openear.maestro.service.VoiceControlService
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.random.Random
import java.io.File

class MaestroViewModel : ViewModel() {

  private val _uiState = MutableStateFlow(MaestroUiState())
  val uiState: StateFlow<MaestroUiState> = _uiState.asStateFlow()

  private val _transcriptionResult = MutableStateFlow("")
  val transcriptionResult: StateFlow<String> = _transcriptionResult.asStateFlow()

  private val _burstSize = MutableStateFlow(DEFAULT_BURST_SIZE)
  val burstSize: StateFlow<Int> = _burstSize.asStateFlow()

  private val _autoReviewEnabled = MutableStateFlow(true)
  val autoReviewEnabled: StateFlow<Boolean> = _autoReviewEnabled.asStateFlow()

  private val _referenceInstrument = MutableStateFlow("piano")
  val referenceInstrument: StateFlow<String> = _referenceInstrument.asStateFlow()

  private val _snippetOptions = MutableStateFlow(defaultSnippetOptions())
  val snippetOptions: StateFlow<List<ProgressionSnippet>> = _snippetOptions.asStateFlow()

  private val _selectedSnippetAssetPath =
    MutableStateFlow(_snippetOptions.value.firstOrNull()?.assetPath.orEmpty())
  val selectedSnippetAssetPath: StateFlow<String> = _selectedSnippetAssetPath.asStateFlow()

  private val _loopProgressionLabels =
    MutableStateFlow(progressionsWithLowDegrees().map { it.joinToString("-") })
  val loopProgressionLabels: StateFlow<List<String>> = _loopProgressionLabels.asStateFlow()

  private val _isSnippetRecording = MutableStateFlow(false)
  val isSnippetRecording: StateFlow<Boolean> = _isSnippetRecording.asStateFlow()

  private val _recordingFolder = MutableStateFlow(RecordingFolder.SONG_SAMPLES)
  val recordingFolder: StateFlow<RecordingFolder> = _recordingFolder.asStateFlow()

  private val _voicingEnabled = MutableStateFlow(false)
  val voicingEnabled: StateFlow<Boolean> = _voicingEnabled.asStateFlow()

  private val _voicingOverlayPolicy = MutableStateFlow(VoicingOverlayPolicy.FIXED)
  val voicingOverlayPolicy: StateFlow<VoicingOverlayPolicy> = _voicingOverlayPolicy.asStateFlow()

  private val _fixedVoicingTone = MutableStateFlow(VoicingTone.ROOT)
  val fixedVoicingTone: StateFlow<VoicingTone> = _fixedVoicingTone.asStateFlow()

  private val _voicingBackingMode = MutableStateFlow(VoicingBackingMode.GUITAR_LOW)
  val voicingBackingMode: StateFlow<VoicingBackingMode> = _voicingBackingMode.asStateFlow()

  private val _voicingTaskMode = MutableStateFlow(VoicingTaskMode.PROGRESSION)
  val voicingTaskMode: StateFlow<VoicingTaskMode> = _voicingTaskMode.asStateFlow()

  private val _voicingAdvancedMix = MutableStateFlow(false)
  val voicingAdvancedMix: StateFlow<Boolean> = _voicingAdvancedMix.asStateFlow()

  private lateinit var assetPlayer: AssetPlayer
  private var voiceControlPort: VoiceControlService.Port? = null

  private var recordingActive = false
  private var playbackLoopActive = false

  private var exerciseJob: Job? = null
  private var playbackLoopJob: Job? = null
  private var reviewJob: Job? = null
  private var chordToneCycleJob: Job? = null

  private val random = Random(System.currentTimeMillis())

  private val progressions = progressionsWithLowDegrees()

  private var burstPlan: List<List<String>> = emptyList()
  private var performanceRecords: List<PerformanceRecord> = emptyList()
  private var misidentifiedProgressions: List<List<String>> = emptyList()
  private var snippetRecorder: MediaRecorder? = null
  private var activeSnippetRecordPath: String? = null
  private var quizPausedUntilMs: Long = 0L
  private var activeListenDeferred: CompletableDeferred<ListenAttemptResult>? = null

  fun initialize(context: Context) {
    assetPlayer = AssetPlayer(context.applicationContext)
    _snippetOptions.value = defaultSnippetOptions() + loadRecordedSnippets(context)
    _selectedSnippetAssetPath.value = _snippetOptions.value.firstOrNull()?.assetPath.orEmpty()
  }

  fun setVoiceControlPort(port: VoiceControlService.Port) {
    voiceControlPort = port
  }

  fun setBurstSize(value: Int) {
    _burstSize.value = value.coerceIn(MIN_BURST_SIZE, MAX_BURST_SIZE)
  }

  fun increaseBurstSize() {
    setBurstSize(_burstSize.value + 1)
  }

  fun decreaseBurstSize() {
    setBurstSize(_burstSize.value - 1)
  }

  fun toggleAutoReviewEnabled() {
    _autoReviewEnabled.value = !_autoReviewEnabled.value
  }

  fun setReferenceInstrument(instrument: String) {
    _referenceInstrument.value = if (instrument == "guitar-acoustic") {
      "guitar-acoustic"
    } else {
      "piano"
    }
  }

  fun toggleVoicingMode() {
    _voicingEnabled.value = !_voicingEnabled.value
  }

  fun setVoicingOverlayPolicy(policy: VoicingOverlayPolicy) {
    _voicingOverlayPolicy.value = policy
  }

  fun setFixedVoicingTone(tone: VoicingTone) {
    _fixedVoicingTone.value = tone
  }

  fun setVoicingBackingMode(mode: VoicingBackingMode) {
    _voicingBackingMode.value = mode
  }

  fun setVoicingTaskMode(mode: VoicingTaskMode) {
    _voicingTaskMode.value = mode
  }

  fun toggleVoicingAdvancedMix() {
    _voicingAdvancedMix.value = !_voicingAdvancedMix.value
  }

  fun setRecordingFolder(folder: RecordingFolder) {
    _recordingFolder.value = folder
  }

  fun playReferenceChord(chord: String) {
    viewModelScope.launch {
      if (recordingActive) {
        pauseQuizForManualReference()
      }
      _uiState.value = _uiState.value.copy(
        userMessage = "Reference: $chord on ${_referenceInstrument.value}",
        currentInstrument = _referenceInstrument.value
      )
      assetPlayer.playChord(chord, _referenceInstrument.value)
    }
  }

  fun selectSnippet(assetPath: String) {
    _selectedSnippetAssetPath.value = assetPath
  }

  fun playSelectedSnippet() {
    val selected = _snippetOptions.value.firstOrNull {
      it.assetPath == _selectedSnippetAssetPath.value
    } ?: return

    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(
        userMessage = "Snippet: ${selected.displayName}",
        currentInstrument = ""
      )
      if (selected.source == SnippetSource.ASSET) {
        assetPlayer.playAssetClip(selected.assetPath)
      } else {
        assetPlayer.playFileClip(selected.assetPath)
      }
    }
  }

  fun toggleSnippetRecording(baseName: String) {
    if (_isSnippetRecording.value) {
      stopSnippetRecording()
      return
    }

    if (!::assetPlayer.isInitialized) return
    val safeBase = sanitizeSnippetBaseName(baseName.ifBlank { "snippet_new" })
    val targetFile = createUniqueSnippetFile(
      context = assetPlayer.getAppContext(),
      baseName = safeBase,
      folder = _recordingFolder.value
    )
    targetFile.parentFile?.mkdirs()

    try {
      val recorder = MediaRecorder().apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setAudioSamplingRate(44100)
        setAudioEncodingBitRate(96000)
        setOutputFile(targetFile.absolutePath)
        prepare()
        start()
      }
      snippetRecorder = recorder
      activeSnippetRecordPath = targetFile.absolutePath
      _isSnippetRecording.value = true
      _uiState.value = _uiState.value.copy(
        userMessage = "Recording snippet to ${targetFile.name} (tap again to stop)."
      )
      viewModelScope.launch {
        delay(12_000)
        if (_isSnippetRecording.value) {
          stopSnippetRecording()
        }
      }
    } catch (t: Throwable) {
      _isSnippetRecording.value = false
      activeSnippetRecordPath = null
      _uiState.value = _uiState.value.copy(
        userMessage = "Failed to start snippet recording: ${t.message ?: "unknown error"}"
      )
    }
  }

  fun deleteSelectedSnippet() {
    val selected = _snippetOptions.value.firstOrNull {
      it.assetPath == _selectedSnippetAssetPath.value
    } ?: return

    if (selected.source == SnippetSource.ASSET) {
      _uiState.value = _uiState.value.copy(
        userMessage = "Built-in snippet cannot be deleted."
      )
      return
    }

    val file = File(selected.assetPath)
    val deleted = !file.exists() || file.delete()
    if (!deleted) {
      _uiState.value = _uiState.value.copy(
        userMessage = "Could not delete ${file.name}."
      )
      return
    }

    _snippetOptions.value = _snippetOptions.value.filter { it.assetPath != selected.assetPath }
    _selectedSnippetAssetPath.value = _snippetOptions.value.firstOrNull()?.assetPath.orEmpty()
    _uiState.value = _uiState.value.copy(
      userMessage = "Deleted snippet ${selected.displayName}."
    )
  }

  fun renameSelectedSnippetLabel(newLabel: String) {
    if (newLabel.isBlank()) {
      _uiState.value = _uiState.value.copy(
        userMessage = "Rename placeholder: enter a non-empty label."
      )
      return
    }

    val selected = _snippetOptions.value.firstOrNull {
      it.assetPath == _selectedSnippetAssetPath.value
    } ?: return

    if (selected.source == SnippetSource.ASSET) {
      _snippetOptions.value = _snippetOptions.value.map { snippet ->
        if (snippet.assetPath == selected.assetPath) {
          snippet.copy(displayName = newLabel.trim())
        } else {
          snippet
        }
      }
      _uiState.value = _uiState.value.copy(
        userMessage = "Snippet label updated to \"$newLabel\"."
      )
      return
    }

    val file = File(selected.assetPath)
    if (!file.exists()) {
      _uiState.value = _uiState.value.copy(
        userMessage = "Selected snippet file no longer exists."
      )
      return
    }

    val renamed = createUniqueFileInDir(
      dir = file.parentFile ?: return,
      baseName = sanitizeSnippetBaseName(newLabel.trim()),
      extension = file.extension.ifBlank { "m4a" }
    )
    val success = file.renameTo(renamed)
    if (!success) {
      _uiState.value = _uiState.value.copy(
        userMessage = "Rename failed for ${file.name}."
      )
      return
    }
    _snippetOptions.value = _snippetOptions.value.map { snippet ->
      if (snippet.assetPath == selected.assetPath) {
        snippet.copy(
          assetPath = renamed.absolutePath,
          displayName = renamed.nameWithoutExtension
        )
      } else {
        snippet
      }
    }
    _selectedSnippetAssetPath.value = renamed.absolutePath
    _uiState.value = _uiState.value.copy(
      userMessage = "Snippet renamed to ${renamed.nameWithoutExtension}."
    )
  }

  fun startPlaybackLoop(progressionIndex: Int) {
    stopExercise()
    stopReview()
    stopSnippetRecording()

    playbackLoopActive = true
    playbackLoopJob = viewModelScope.launch {
      val progression = progressions[progressionIndex.coerceIn(0, progressions.lastIndex)]
      _uiState.value = _uiState.value.copy(
        exerciseState = ExerciseState.LOOPING,
        userMessage = "Looping progression ${progression.joinToString("-")} (instrument changes each pass)",
        heardTranscription = "",
        burstSummaryText = "",
        reviewMessage = ""
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

    if (!recordingActive && reviewJob == null) {
      _uiState.value = _uiState.value.copy(
        exerciseState = ExerciseState.IDLE,
        userMessage = "",
        currentInstrument = ""
      )
    }
  }

  fun startExercise() {
    if (_voicingEnabled.value && _voicingTaskMode.value == VoicingTaskMode.CHORD_TONE) {
      startChordToneBackgroundCycle()
    } else {
      startBurstExercise()
    }
  }

  fun stopExercise() {
    recordingActive = false
    exerciseJob?.cancel()
    exerciseJob = null
    chordToneCycleJob?.cancel()
    chordToneCycleJob = null
    voiceControlPort?.stopListening()
    stopPlaybackLoop()
    stopReview()

    _uiState.value = _uiState.value.copy(
      exerciseState = ExerciseState.IDLE,
      userMessage = "",
      heardTranscription = "",
      burstProgressText = "",
      burstSummaryText = "",
      reviewMessage = "",
      currentInstrument = ""
    )
  }

  fun startReviewOfMisidentified() {
    if (misidentifiedProgressions.isEmpty()) {
      _uiState.value = _uiState.value.copy(
        exerciseState = ExerciseState.FEEDBACK,
        userMessage = "No misidentified progressions to review.",
        reviewMessage = ""
      )
      return
    }
    startAutomaticReview()
  }

  fun reset() {
    stopExercise()
    _uiState.value = MaestroUiState(exerciseState = ExerciseState.IDLE)
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

  private fun startBurstExercise() {
    chordToneCycleJob?.cancel()
    chordToneCycleJob = null
    stopPlaybackLoop()
    stopReview()

    recordingActive = true
    performanceRecords = emptyList()
    misidentifiedProgressions = emptyList()

    burstPlan = List(_burstSize.value) {
      progressions[random.nextInt(progressions.size)]
    }

    _uiState.value = _uiState.value.copy(
      exerciseState = ExerciseState.PLAYING,
      userMessage = "Starting burst of ${burstPlan.size} progressions.",
      heardTranscription = "",
      burstProgressText = "0/${burstPlan.size}",
      burstSummaryText = "",
      reviewMessage = "",
      currentInstrument = ""
    )
    updateTranscriptionResult("")

    exerciseJob = viewModelScope.launch {
      val port = voiceControlPort ?: run {
        _uiState.value = _uiState.value.copy(
          exerciseState = ExerciseState.FEEDBACK,
          userMessage = "Voice service not connected.",
          burstSummaryText = "Burst not started."
        )
        recordingActive = false
        return@launch
      }

      val records = mutableListOf<PerformanceRecord>()

      burstPlan.forEachIndexed { index, progression ->
        if (!recordingActive) return@launch

        _uiState.value = _uiState.value.copy(
          exerciseState = ExerciseState.PLAYING,
          burstProgressText = "${index + 1}/${burstPlan.size}",
          userMessage = "Listen to progression ${index + 1}/${burstPlan.size}."
        )
        updateTranscriptionResult("")

        val record = runSingleProgressionQuestion(progression, port)
        records += record

        _uiState.value = _uiState.value.copy(
          exerciseState = ExerciseState.FEEDBACK,
          burstProgressText = "${index + 1}/${burstPlan.size}",
          userMessage = if (record.hadAnyWrongGuess) {
            "Correct now. You previously misidentified ${record.progression.joinToString("-")}."
          } else {
            "Correct."
          }
        )

        delay(650)
      }

      if (!recordingActive) return@launch

      performanceRecords = records
      misidentifiedProgressions = records
        .filter { it.hadAnyWrongGuess }
        .map { it.progression }

      val firstGuessCorrect = records.count { it.firstExplicitGuessCorrect }
      val correctedAfterWrongGuess = records.count { it.hadAnyWrongGuess && it.solvedEventually }
      val summaryText =
        "Burst done: ${records.size}. First-guess correct: $firstGuessCorrect. " +
          "Misidentified at least once: ${misidentifiedProgressions.size}. " +
          "Solved after wrong guess: $correctedAfterWrongGuess."

      _uiState.value = _uiState.value.copy(
        exerciseState = ExerciseState.FEEDBACK,
        userMessage = if (misidentifiedProgressions.isEmpty()) {
          "Burst complete. 100% first-guess correct."
        } else {
          "Burst complete. ${misidentifiedProgressions.size} misidentified progression(s) saved for review."
        },
        burstProgressText = "${records.size}/${records.size}",
        burstSummaryText = summaryText
      )
      updateTranscriptionResult(summaryText)

      if (_autoReviewEnabled.value && misidentifiedProgressions.isNotEmpty()) {
        startAutomaticReview()
      }

      recordingActive = false
    }
  }

  private suspend fun runSingleProgressionQuestion(
    progression: List<String>,
    port: VoiceControlService.Port
  ): PerformanceRecord {
    var explicitGuessCount = 0
    var wrongGuessCount = 0
    var solved = false
    var exhaustedAttempts = false

    val questionInstrument = nextPlaybackInstrument()
    val isChordToneTask = _voicingEnabled.value && _voicingTaskMode.value == VoicingTaskMode.CHORD_TONE
    val questionTone = if (isChordToneTask) {
      when (_voicingOverlayPolicy.value) {
        VoicingOverlayPolicy.FIXED -> _fixedVoicingTone.value
        VoicingOverlayPolicy.RANDOM -> VoicingTone.entries[random.nextInt(VoicingTone.entries.size)]
      }
    } else {
      null
    }
    val expectedAnswer = if (questionTone != null) {
      listOf(questionTone.answerToken)
    } else {
      progression
    }

    while (recordingActive && !solved && !exhaustedAttempts) {
      waitForQuizPauseWindowIfNeeded()
      playProgression(progression, questionInstrument, forcedVoicingTone = questionTone)

      _uiState.value = _uiState.value.copy(
        exerciseState = ExerciseState.LISTENING,
        userMessage = if (questionTone != null) {
          "Progression ${progression.joinToString("-")}. Identify the chord tone: one, three, or five."
        } else {
          "Say the progression now."
        }
      )

      when (val listenResult = listenForSingleAttempt(port, expectedAnswer)) {
        is ListenAttemptResult.ManualPause -> {
          updateTranscriptionResult("Quiz resumes in 2s.")
          _uiState.value = _uiState.value.copy(
            exerciseState = ExerciseState.PLAYING,
            userMessage = "Paused for manual reference. Replaying progression shortly."
          )
          waitForQuizPauseWindowIfNeeded()
        }

        is ListenAttemptResult.Repeat -> {
          updateTranscriptionResult("No parseable guess detected. Replaying.")
          _uiState.value = _uiState.value.copy(
            exerciseState = ExerciseState.PLAYING,
            userMessage = "No parseable guess detected. Replaying the same progression."
          )
        }

        is ListenAttemptResult.Incorrect -> {
          explicitGuessCount += 1
          wrongGuessCount += 1

          val feedbackMessage = if (questionTone != null) {
            evaluateToneFeedback(questionTone, listenResult.heard)
          } else {
            evaluateFeedback(progression, listenResult.heard)
          }

          _uiState.value = _uiState.value.copy(
            exerciseState = ExerciseState.PLAYING,
            userMessage = feedbackMessage,
            heardTranscription =
              if (listenResult.heard.isEmpty()) "" else "I heard: ${listenResult.heard.joinToString("-")}"
          )

          if (wrongGuessCount >= MAX_WRONG_GUESSES_BEFORE_REVEAL) {
            exhaustedAttempts = true
            val answerText = expectedAnswer.joinToString("-")
            _uiState.value = _uiState.value.copy(
              exerciseState = ExerciseState.FEEDBACK,
              userMessage = "Answer: $answerText",
              heardTranscription = "Answer: $answerText"
            )
            updateTranscriptionResult("Answer revealed: $answerText")
            val missingPromptWords = assetPlayer.playSpokenAnswerTokens(expectedAnswer)
            if (missingPromptWords.isNotEmpty()) {
              val missingPromptFiles = missingPromptWords.joinToString(", ") { "${it}.mp3" }
              updateTranscriptionResult(
                "Could not find $missingPromptFiles"
              )
            }
            delay(1200)
          }
        }

        is ListenAttemptResult.Correct -> {
          explicitGuessCount += 1
          updateTranscriptionResult("Correct.")
          solved = true
        }
      }
    }

    return PerformanceRecord(
      progression = progression,
      explicitGuessCount = explicitGuessCount,
      wrongGuessCount = wrongGuessCount,
      solvedEventually = solved,
      firstExplicitGuessCorrect = explicitGuessCount > 0 && wrongGuessCount == 0
    )
  }

  private suspend fun listenForSingleAttempt(
    port: VoiceControlService.Port,
    expectedProgression: List<String>
  ): ListenAttemptResult =
    suspendCancellableCoroutine { cont ->
      val deferred = CompletableDeferred<ListenAttemptResult>()
      activeListenDeferred = deferred
      var latestTranscription = "<empty>"
      var latestParsed = "<none>"

      fun publishListenDebug() {
        updateTranscriptionResult(
          "Transcribed: $latestTranscription\nParsed: $latestParsed"
        )
      }

      port.beginListening(
        expectedProgression = expectedProgression,
        onTranscription = { transcription ->
          latestTranscription = transcription.ifBlank { "<empty>" }
          publishListenDebug()
        },
        onParseDebug = { parseDebug ->
          latestParsed = parseDebug.removePrefix("Parsed: ").ifBlank { "<none>" }
          publishListenDebug()
        },
        onRepeat = {
          deferred.complete(ListenAttemptResult.Repeat)
        },
        onCorrect = {
          deferred.complete(ListenAttemptResult.Correct)
        },
        onIncorrect = { heard ->
          deferred.complete(ListenAttemptResult.Incorrect(heard))
        }
      )

      viewModelScope.launch {
        val result = deferred.await()
        if (cont.isActive) {
          cont.resume(result)
        }
      }

      cont.invokeOnCancellation {
        activeListenDeferred = null
        port.stopListening()
      }
    }

  private fun pauseQuizForManualReference() {
    quizPausedUntilMs = SystemClock.elapsedRealtime() + 2_500L
    voiceControlPort?.stopListening()
    activeListenDeferred?.complete(ListenAttemptResult.ManualPause)
  }

  private suspend fun waitForQuizPauseWindowIfNeeded() {
    while (recordingActive) {
      val remainingMs = quizPausedUntilMs - SystemClock.elapsedRealtime()
      if (remainingMs <= 0) return
      delay(minOf(remainingMs, 100L))
    }
  }

  private fun startAutomaticReview() {
    stopReview()

    reviewJob = viewModelScope.launch {
      misidentifiedProgressions.forEachIndexed { index, progression ->
        _uiState.value = _uiState.value.copy(
          exerciseState = ExerciseState.REVIEWING,
          userMessage = "Review ${index + 1}/${misidentifiedProgressions.size}",
          reviewMessage = "You misidentified this progression: ${progression.joinToString("-")}"
        )

        playProgression(progression)
        delay(900)
      }

      _uiState.value = _uiState.value.copy(
        exerciseState = ExerciseState.FEEDBACK,
        userMessage = "Automatic review complete.",
        reviewMessage = ""
      )

      reviewJob = null
    }
  }

  private fun stopReview() {
    reviewJob?.cancel()
    reviewJob = null
  }

  private fun startChordToneBackgroundCycle() {
    stopPlaybackLoop()
    stopReview()
    voiceControlPort?.stopListening()
    exerciseJob?.cancel()
    exerciseJob = null
    chordToneCycleJob?.cancel()
    chordToneCycleJob = null
    recordingActive = false

    val progressions = listOf(
      listOf("1", "4"),
      listOf("1", "5"),
      listOf("lo4", "1", "lo5")
    )
    val toneCycle = listOf(VoicingTone.ROOT, VoicingTone.THIRD, VoicingTone.FIFTH)
    val setup = currentVoicingPlaybackSetup()

    _uiState.value = _uiState.value.copy(
      exerciseState = ExerciseState.PLAYING,
      userMessage = "Chord tone background mode running.",
      currentInstrument = setup.modeLabel,
      heardTranscription = "",
      burstProgressText = "",
      burstSummaryText = "",
      reviewMessage = ""
    )
    updateTranscriptionResult("")

    chordToneCycleJob = viewModelScope.launch {
      var progressionIndex = 0
      var toneIndex = 0

      while (true) {
        val progression = progressions[progressionIndex]
        val tonesForProgression = if (_voicingAdvancedMix.value) {
          progression.indices.map { idx ->
            toneCycle[(toneIndex + idx) % toneCycle.size]
          }
        } else {
          List(progression.size) { toneCycle[toneIndex % toneCycle.size] }
        }

        _uiState.value = _uiState.value.copy(
          exerciseState = ExerciseState.PLAYING,
          userMessage = "Listen: ${progression.joinToString("-")}",
          reviewMessage = ""
        )

        repeat(4) {
          playProgressionWithToneMap(progression, tonesForProgression, setup)
          delay(900)
        }

        for (i in progression.indices) {
          assetPlayer.playChordArpeggioThenOverlay(
            chord = progression[i],
            chordInstrument = setup.chordInstrument,
            overlayInstrument = setup.overlayInstrument,
            overlayTone = tonesForProgression[i],
            lowerBackingOctave = setup.lowerBackingOctave,
            raiseOverlayOctave = setup.raiseOverlayOctave,
            arpeggioGapMs = 700L
          )
          delay(900)
        }

        val answerTokens = if (_voicingAdvancedMix.value) {
          tonesForProgression.map { it.answerToken }
        } else {
          listOf(tonesForProgression.first().answerToken)
        }
        val answerWords = answerTokens.joinToString("-") { token ->
          when (token) {
            "1" -> "one"
            "3" -> "three"
            "5" -> "five"
            else -> token
          }
        }
        _uiState.value = _uiState.value.copy(
          exerciseState = ExerciseState.FEEDBACK,
          reviewMessage = "Answer: $answerWords"
        )
        updateTranscriptionResult("Answer: $answerWords")
        assetPlayer.playSpokenAnswerTokens(answerTokens)
        delay(900)

        repeat(2) {
          playProgressionWithToneMap(progression, tonesForProgression, setup)
          delay(900)
        }

        progressionIndex = (progressionIndex + 1) % progressions.size
        toneIndex = (toneIndex + 1) % toneCycle.size
      }
    }
  }

  private suspend fun playProgression(progression: List<String>) {
    val instrument = nextPlaybackInstrument()
    playProgression(progression, instrument, forcedVoicingTone = null)
  }

  private suspend fun playProgression(
    progression: List<String>,
    instrument: String,
    forcedVoicingTone: VoicingTone?
  ) {
    if (_voicingEnabled.value) {
      val (chordInstrument, overlayInstrument, lowerBackingOctave, raiseOverlayOctave, modeLabel) =
        currentVoicingPlaybackSetup()
      _uiState.value = _uiState.value.copy(
        currentInstrument = modeLabel,
        userMessage = _uiState.value.userMessage
      )
      for (chord in progression) {
        val tone = forcedVoicingTone ?: when (_voicingOverlayPolicy.value) {
          VoicingOverlayPolicy.FIXED -> _fixedVoicingTone.value
          VoicingOverlayPolicy.RANDOM -> VoicingTone.entries[random.nextInt(VoicingTone.entries.size)]
        }
        assetPlayer.playChordWithOverlay(
          chord = chord,
          chordInstrument = chordInstrument,
          overlayInstrument = overlayInstrument,
          overlayTone = tone,
          lowerBackingOctave = lowerBackingOctave,
          raiseOverlayOctave = raiseOverlayOctave
        )
        delay(500)
      }
      return
    }

    _uiState.value = _uiState.value.copy(
      currentInstrument = instrument,
      userMessage = _uiState.value.userMessage
    )

    for (chord in progression) {
      assetPlayer.playChord(chord, instrument)
      delay(500)
    }
  }

  private fun nextPlaybackInstrument(): String {
    return if (random.nextBoolean()) "piano" else "guitar-acoustic"
  }

  private suspend fun playProgressionWithToneMap(
    progression: List<String>,
    tones: List<VoicingTone>,
    setup: VoicingPlaybackSetup
  ) {
    _uiState.value = _uiState.value.copy(currentInstrument = setup.modeLabel)
    for (i in progression.indices) {
      assetPlayer.playChordWithOverlay(
        chord = progression[i],
        chordInstrument = setup.chordInstrument,
        overlayInstrument = setup.overlayInstrument,
        overlayTone = tones.getOrElse(i) { VoicingTone.ROOT },
        lowerBackingOctave = setup.lowerBackingOctave,
        raiseOverlayOctave = setup.raiseOverlayOctave
      )
      delay(700)
    }
  }

  private fun currentVoicingPlaybackSetup(): VoicingPlaybackSetup {
    return when (_voicingBackingMode.value) {
      VoicingBackingMode.GUITAR_LOW -> {
        VoicingPlaybackSetup("guitar-acoustic", "piano", true, false, "guitar(low) + piano")
      }

      VoicingBackingMode.PIANO_LOW -> {
        VoicingPlaybackSetup("piano", "piano", true, true, "piano(low) + piano(high)")
      }
    }
  }

  private fun evaluateFeedback(expected: List<String>, heard: List<String>): String {
    if (heard.isEmpty()) return "Did not catch a clear guess."

    val matches = expected.zip(heard).count { (e, h) -> e == h }
    return when {
      matches == expected.size -> "Correct!"
      matches == expected.size - 1 -> {
        val wrongPos = expected.zip(heard).indexOfFirst { (e, h) -> e != h }
        val ordinal = listOf("First", "Second", "Third", "Fourth")
          .getOrElse(wrongPos) { "${wrongPos + 1}th" }
        "Almost. $ordinal chord is incorrect."
      }

      else -> "Incorrect guess: ${heard.joinToString("-").ifEmpty { "nothing" }}"
    }
  }

  private fun evaluateToneFeedback(expectedTone: VoicingTone, heard: List<String>): String {
    if (heard.isEmpty()) return "Did not catch a clear guess."
    val token = heard.firstOrNull() ?: return "Did not catch a clear guess."
    return if (token == expectedTone.answerToken) {
      "Correct."
    } else {
      val expectedText = when (expectedTone) {
        VoicingTone.ROOT -> "one"
        VoicingTone.THIRD -> "three"
        VoicingTone.FIFTH -> "five"
      }
      "Incorrect. Expected $expectedText."
    }
  }

  private fun updateTranscriptionResult(message: String) {
    _transcriptionResult.value = message
  }

  private sealed class ListenAttemptResult {
    data object ManualPause : ListenAttemptResult()
    data object Repeat : ListenAttemptResult()
    data object Correct : ListenAttemptResult()
    data class Incorrect(val heard: List<String>) : ListenAttemptResult()
  }

  private data class PerformanceRecord(
    val progression: List<String>,
    val explicitGuessCount: Int,
    val wrongGuessCount: Int,
    val solvedEventually: Boolean,
    val firstExplicitGuessCorrect: Boolean
  ) {
    val hadAnyWrongGuess: Boolean
      get() = wrongGuessCount > 0
  }

  companion object {
    private const val MIN_BURST_SIZE = 1
    private const val MAX_BURST_SIZE = 50
    private const val DEFAULT_BURST_SIZE = 10
    private const val MAX_WRONG_GUESSES_BEFORE_REVEAL = 4

    private fun progressionsWithLowDegrees(): List<List<String>> = listOf(
      listOf("1", "4", "5", "4"),
      listOf("1", "5", "1", "4"),
      listOf("5", "4", "1", "4"),
      listOf("5", "4", "5", "1"),
      listOf("1", "lo4"),
      listOf("1", "lo5"),
      listOf("1", "lo4", "lo5", "1")
    )

    private fun defaultSnippetOptions(): List<ProgressionSnippet> = listOf(
      ProgressionSnippet(
        assetPath = "progression-snippets/song_progression_1454_C_1.mp3",
        displayName = "song_progression_1454_C_1",
        progression = "1-4-5-4",
        source = SnippetSource.ASSET
      ),
      ProgressionSnippet(
        assetPath = "progression-snippets/song_progression_1454_G_2.mp3",
        displayName = "song_progression_1454_G_2",
        progression = "1-4-5-4",
        source = SnippetSource.ASSET
      ),
      ProgressionSnippet(
        assetPath = "progression-snippets/song_progression_1514_C_1.mp3",
        displayName = "song_progression_1514_C_1",
        progression = "1-5-1-4",
        source = SnippetSource.ASSET
      ),
      ProgressionSnippet(
        assetPath = "progression-snippets/song_progression_5414_G_1.mp3",
        displayName = "song_progression_5414_G_1",
        progression = "5-4-1-4",
        source = SnippetSource.ASSET
      ),
      ProgressionSnippet(
        assetPath = "progression-snippets/song_progression_5451_D_1.mp3",
        displayName = "song_progression_5451_D_1",
        progression = "5-4-5-1",
        source = SnippetSource.ASSET
      )
    )
  }

  private fun stopSnippetRecording() {
    val recorder = snippetRecorder ?: return
    val recordedPath = activeSnippetRecordPath
    try {
      recorder.stop()
    } catch (_: Throwable) {
    } finally {
      recorder.release()
      snippetRecorder = null
      _isSnippetRecording.value = false
      activeSnippetRecordPath = null
    }

    if (recordedPath != null) {
      val file = File(recordedPath)
      if (file.exists()) {
        file.setReadable(true, false)
        file.setWritable(true, false)
        val progression = inferProgressionFromFileName(file.nameWithoutExtension)
        val folder = RecordingFolder.fromDirectoryName(file.parentFile?.name)
        val snippet = ProgressionSnippet(
          assetPath = file.absolutePath,
          displayName = "${folder.directoryName}/${file.nameWithoutExtension}",
          progression = progression,
          source = SnippetSource.FILE,
          recordingFolder = folder
        )
        _snippetOptions.value = _snippetOptions.value + snippet
        _selectedSnippetAssetPath.value = snippet.assetPath
        _uiState.value = _uiState.value.copy(
          userMessage = "Saved recording ${file.name}."
        )
      }
    }
  }

  private fun sanitizeSnippetBaseName(raw: String): String {
    return raw.lowercase()
      .replace(Regex("[^a-z0-9_\\-]"), "_")
      .replace(Regex("_+"), "_")
      .trim('_')
      .ifBlank { "snippet_new" }
  }

  private fun userAudioBaseDir(context: Context): File {
    val externalMediaBase = context.externalMediaDirs.firstOrNull()
    return if (externalMediaBase != null) {
      File(externalMediaBase, "maestro-user-audio")
    } else {
      File(context.filesDir, "maestro-user-audio")
    }
  }

  private fun recordedSnippetsDir(context: Context, folder: RecordingFolder): File =
    File(userAudioBaseDir(context), folder.directoryName)

  private fun createUniqueSnippetFile(
    context: Context,
    baseName: String,
    folder: RecordingFolder
  ): File {
    val dir = recordedSnippetsDir(context, folder)
    return createUniqueFileInDir(dir, baseName, "m4a")
  }

  private fun createUniqueFileInDir(
    dir: File,
    baseName: String,
    extension: String
  ): File {
    dir.mkdirs()
    var suffix = 1
    var candidate = File(dir, "$baseName.$extension")
    while (candidate.exists()) {
      suffix += 1
      candidate = File(dir, "${baseName}_$suffix.$extension")
    }
    return candidate
  }

  private fun loadRecordedSnippets(context: Context): List<ProgressionSnippet> {
    return RecordingFolder.entries.flatMap { folder ->
      val dir = recordedSnippetsDir(context, folder)
      if (!dir.exists()) {
        emptyList()
      } else {
        dir.listFiles()
          ?.filter { it.isFile && (it.extension.equals("m4a", ignoreCase = true) || it.extension.equals("mp3", ignoreCase = true)) }
          ?.sortedBy { it.name.lowercase() }
          ?.map { file ->
            ProgressionSnippet(
              assetPath = file.absolutePath,
              displayName = "${folder.directoryName}/${file.nameWithoutExtension}",
              progression = inferProgressionFromFileName(file.nameWithoutExtension),
              source = SnippetSource.FILE,
              recordingFolder = folder
            )
          }
          ?: emptyList()
      }
    }
  }

  private fun inferProgressionFromFileName(name: String): String {
    val p = Regex("((?:lo|b)?\\d)(?:[_-](?:lo|b)?\\d){1,4}").find(name.lowercase())
    return p?.value?.replace("_", "-") ?: "custom"
  }
}

data class ProgressionSnippet(
  val assetPath: String,
  val displayName: String,
  val progression: String,
  val source: SnippetSource,
  val recordingFolder: RecordingFolder? = null
)

enum class SnippetSource {
  ASSET,
  FILE
}

enum class RecordingFolder(val directoryName: String) {
  SONG_SAMPLES("song_samples"),
  SPEECH("speech"),
  VOICE("voice");

  companion object {
    fun fromDirectoryName(name: String?): RecordingFolder {
      return entries.firstOrNull { it.directoryName == name } ?: SONG_SAMPLES
    }
  }
}

enum class VoicingOverlayPolicy {
  FIXED,
  RANDOM
}

enum class VoicingTone {
  ROOT,
  THIRD,
  FIFTH;

  val answerToken: String
    get() = when (this) {
      ROOT -> "1"
      THIRD -> "3"
      FIFTH -> "5"
    }
}

enum class VoicingTaskMode {
  PROGRESSION,
  CHORD_TONE
}

enum class VoicingBackingMode {
  GUITAR_LOW,
  PIANO_LOW
}

private data class VoicingPlaybackSetup(
  val chordInstrument: String,
  val overlayInstrument: String,
  val lowerBackingOctave: Boolean,
  val raiseOverlayOctave: Boolean,
  val modeLabel: String
)

class AssetPlayer(private val context: Context) {

  private val chordMapByInstrument = mapOf(
    "piano" to mapOf(
      "1" to listOf("C4.mp3", "E4.mp3", "G4.mp3"),
      "4" to listOf("F4.mp3", "A4.mp3", "C5.mp3"),
      "5" to listOf("G4.mp3", "B4.mp3", "D5.mp3"),
      // raised one octave from prior baseline
      "6" to listOf("A4.mp3", "C5.mp3", "E5.mp3"),
      "lo4" to listOf("F3.mp3", "A3.mp3", "C4.mp3"),
      "lo5" to listOf("G3.mp3", "B3.mp3", "D4.mp3"),
      // raised one octave from prior baseline
      "lo6" to listOf("A3.mp3", "C4.mp3", "E4.mp3")
    ),
    "guitar-acoustic" to mapOf(
      "1" to listOf("C4.mp3", "E4.mp3", "G4.mp3"),
      "4" to listOf("F4.mp3", "A4.mp3", "C5.mp3"),
      "5" to listOf("G4.mp3", "B4.mp3", "D5.mp3"),
      // guitar set has no E5 sample, so use highest available E4
      "6" to listOf("A4.mp3", "C5.mp3", "E4.mp3"),
      "lo4" to listOf("F3.mp3", "A3.mp3", "C4.mp3"),
      "lo5" to listOf("G3.mp3", "B3.mp3", "D4.mp3"),
      "lo6" to listOf("A3.mp3", "C4.mp3", "E4.mp3")
    )
  )

  suspend fun playChord(chord: String, instrument: String) {
    val fallbackMap = chordMapByInstrument["piano"].orEmpty()
    val notes = chordMapByInstrument[instrument]?.get(chord) ?: fallbackMap[chord] ?: return

    notes.map { noteFile ->
      runCatching {
        MediaPlayer().apply {
          val afd = context.assets.openFd("$instrument/$noteFile")
          Log.d("AUDIO", "Opened asset $instrument/$noteFile")
          setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
          afd.close()
          prepare()
        }
      }.getOrElse {
        Log.w("AUDIO", "Failed to load $instrument/$noteFile: ${it.message}")
        null
      }
    }.filterNotNull().forEach { mp ->
      mp.start()
      mp.setOnCompletionListener { it.release() }
    }

    delay(1000)
  }

  suspend fun playChordWithOverlay(
    chord: String,
    chordInstrument: String,
    overlayInstrument: String,
    overlayTone: VoicingTone,
    lowerBackingOctave: Boolean = false,
    raiseOverlayOctave: Boolean = false
  ) {
    val backingMap = chordMapByInstrument[chordInstrument]
      ?: chordMapByInstrument["guitar-acoustic"]
      ?: return
    val overlayMap = chordMapByInstrument[overlayInstrument]
      ?: chordMapByInstrument["piano"]
      ?: return

    val backingNotes = (backingMap[chord] ?: return).map { note ->
      if (lowerBackingOctave) shiftNoteFileOctave(note, -1, chordInstrument) else note
    }
    val overlayChordNotes = overlayMap[chord] ?: return
    val overlayIndex = when (overlayTone) {
      VoicingTone.ROOT -> 0
      VoicingTone.THIRD -> 1
      VoicingTone.FIFTH -> 2
    }
    val overlayNoteBase = overlayChordNotes.getOrNull(overlayIndex) ?: return
    val overlayNote = if (raiseOverlayOctave) {
      shiftNoteFileOctave(overlayNoteBase, +1, overlayInstrument)
    } else {
      overlayNoteBase
    }

    val players = mutableListOf<MediaPlayer>()
    backingNotes.forEach { noteFile ->
      runCatching {
        MediaPlayer().apply {
          val afd = context.assets.openFd("$chordInstrument/$noteFile")
          setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
          afd.close()
          prepare()
        }
      }.onSuccess { players += it }
        .onFailure { Log.w("AUDIO", "Failed backing note $chordInstrument/$noteFile: ${it.message}") }
    }

    runCatching {
      MediaPlayer().apply {
        val afd = context.assets.openFd("$overlayInstrument/$overlayNote")
        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        afd.close()
        prepare()
      }
    }.onSuccess { players += it }
      .onFailure { Log.w("AUDIO", "Failed overlay note $overlayInstrument/$overlayNote: ${it.message}") }

    players.forEach { mp ->
      mp.start()
      mp.setOnCompletionListener { it.release() }
    }

    delay(1000)
  }

  suspend fun playChordArpeggioThenOverlay(
    chord: String,
    chordInstrument: String,
    overlayInstrument: String,
    overlayTone: VoicingTone,
    lowerBackingOctave: Boolean = false,
    raiseOverlayOctave: Boolean = false,
    arpeggioGapMs: Long = 500L
  ) {
    val backingMap = chordMapByInstrument[chordInstrument]
      ?: chordMapByInstrument["guitar-acoustic"]
      ?: return
    val backingNotes = (backingMap[chord] ?: return).map { note ->
      if (lowerBackingOctave) shiftNoteFileOctave(note, -1, chordInstrument) else note
    }
    for (noteFile in backingNotes) {
      playSingleNote(chordInstrument, noteFile)
      delay(arpeggioGapMs)
    }
    delay(220)
    playChordWithOverlay(
      chord = chord,
      chordInstrument = chordInstrument,
      overlayInstrument = overlayInstrument,
      overlayTone = overlayTone,
      lowerBackingOctave = lowerBackingOctave,
      raiseOverlayOctave = raiseOverlayOctave
    )
  }

  private fun playSingleNote(instrument: String, noteFile: String) {
    runCatching {
      MediaPlayer().apply {
        val afd = context.assets.openFd("$instrument/$noteFile")
        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        afd.close()
        prepare()
        start()
        setOnCompletionListener { it.release() }
      }
    }.onFailure {
      Log.w("AUDIO", "Failed single note $instrument/$noteFile: ${it.message}")
    }
  }

  private fun shiftNoteFileOctave(noteFile: String, delta: Int, instrument: String): String {
    val match = Regex("^([A-G]s?)(\\d)\\.mp3$").find(noteFile) ?: return noteFile
    val noteName = match.groupValues[1]
    val octave = match.groupValues[2].toIntOrNull() ?: return noteFile
    val shiftedOctave = octave + delta
    if (shiftedOctave < 0) return noteFile
    val shifted = "$noteName$shiftedOctave.mp3"
    return if (assetExists("$instrument/$shifted")) shifted else noteFile
  }

  private fun assetExists(assetPath: String): Boolean {
    return try {
      context.assets.openFd(assetPath).close()
      true
    } catch (_: Throwable) {
      false
    }
  }

  suspend fun playAssetClip(assetPath: String) {
    suspendCancellableCoroutine<Unit> { cont ->
      val player = MediaPlayer()
      try {
        val afd = context.assets.openFd(assetPath)
        player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        afd.close()
        player.setOnCompletionListener {
          it.release()
          if (cont.isActive) cont.resume(Unit)
        }
        player.prepare()
        player.start()
        cont.invokeOnCancellation {
          player.release()
        }
      } catch (t: Throwable) {
        player.release()
        if (cont.isActive) cont.resume(Unit)
      }
    }
  }

  suspend fun playFileClip(filePath: String) {
    suspendCancellableCoroutine<Unit> { cont ->
      val player = MediaPlayer()
      try {
        player.setDataSource(filePath)
        player.setOnCompletionListener {
          it.release()
          if (cont.isActive) cont.resume(Unit)
        }
        player.prepare()
        player.start()
        cont.invokeOnCancellation {
          player.release()
        }
      } catch (_: Throwable) {
        player.release()
        if (cont.isActive) cont.resume(Unit)
      }
    }
  }

  suspend fun playSpokenAnswerTokens(tokens: List<String>): List<String> {
    val spokenTokens = tokens.flatMap { token ->
      when {
        token.startsWith("lo") && token.length > 2 -> listOf(token.removePrefix("lo"))
        token.startsWith("b") && token.length > 1 -> listOf(token.removePrefix("b"))
        token.startsWith("#") && token.length > 1 -> listOf("sharp", token.removePrefix("#"))
        else -> listOf(token)
      }
    }.mapNotNull { toSpokenWord(it) }

    val missing = mutableListOf<String>()
    for (word in spokenTokens) {
      val played = playPromptWordIfExists(word)
      if (!played) {
        Log.d("AUDIO", "Prompt word missing for '$word' (skipping)")
        missing += word
      }
      delay(120)
    }
    return missing
  }

  private suspend fun playPromptWordIfExists(word: String): Boolean {
    val assetPaths = listOf(
      "voice-prompts/$word.mp3",
      "speech/$word.mp3",
      "prompts/$word.mp3"
    )
    for (assetPath in assetPaths) {
      if (assetExists(assetPath)) {
        playAssetClip(assetPath)
        return true
      }
    }

    val userAudioBase = userAudioBaseDir()
    val recordedCandidates = listOf(
      File(userAudioBase, "speech/$word.m4a"),
      File(userAudioBase, "speech/$word.mp3"),
      File(userAudioBase, "voice/$word.m4a"),
      File(userAudioBase, "voice/$word.mp3"),
      File(userAudioBase, "song_samples/$word.m4a"),
      File(userAudioBase, "song_samples/$word.mp3")
    )
    recordedCandidates.firstOrNull { it.exists() }?.let { file ->
      playFileClip(file.absolutePath)
      return true
    }
    return false
  }

  private fun toSpokenWord(token: String): String? {
    return when (token.lowercase()) {
      "1" -> "one"
      "2" -> "two"
      "3" -> "three"
      "4" -> "four"
      "5" -> "five"
      "6" -> "six"
      "7" -> "seven"
      "sharp" -> "sharp"
      else -> null
    }
  }

  private fun userAudioBaseDir(): File {
    val externalMediaBase = context.externalMediaDirs.firstOrNull()
    return if (externalMediaBase != null) {
      File(externalMediaBase, "maestro-user-audio")
    } else {
      File(context.filesDir, "maestro-user-audio")
    }
  }

  fun getAppContext(): Context = context
}
