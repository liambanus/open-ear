package com.openear.maestro.ui

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openear.maestro.service.VoiceControlService
import kotlinx.coroutines.Job
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
    MutableStateFlow(progressionsWithFlats().map { it.joinToString("-") })
  val loopProgressionLabels: StateFlow<List<String>> = _loopProgressionLabels.asStateFlow()

  private val _isSnippetRecording = MutableStateFlow(false)
  val isSnippetRecording: StateFlow<Boolean> = _isSnippetRecording.asStateFlow()

  private lateinit var assetPlayer: AssetPlayer
  private var voiceControlPort: VoiceControlService.Port? = null

  private var recordingActive = false
  private var playbackLoopActive = false

  private var exerciseJob: Job? = null
  private var playbackLoopJob: Job? = null
  private var reviewJob: Job? = null

  private val random = Random(System.currentTimeMillis())

  private val progressions = progressionsWithFlats()

  private var burstPlan: List<List<String>> = emptyList()
  private var performanceRecords: List<PerformanceRecord> = emptyList()
  private var misidentifiedProgressions: List<List<String>> = emptyList()
  private var snippetRecorder: MediaRecorder? = null
  private var activeSnippetRecordPath: String? = null

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

  fun playReferenceChord(chord: String) {
    viewModelScope.launch {
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
    val targetFile = createUniqueSnippetFile(assetPlayer.getAppContext(), safeBase)
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

    val renamed = createUniqueSnippetFile(assetPlayer.getAppContext(), sanitizeSnippetBaseName(newLabel.trim()))
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
    startBurstExercise()
  }

  fun stopExercise() {
    recordingActive = false
    exerciseJob?.cancel()
    exerciseJob = null
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

    val questionInstrument = nextPlaybackInstrument()
    while (recordingActive && !solved) {
      playProgression(progression, questionInstrument)

      _uiState.value = _uiState.value.copy(
        exerciseState = ExerciseState.LISTENING,
        userMessage = "Say the progression now."
      )

      when (val listenResult = listenForSingleAttempt(port, progression)) {
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

          _uiState.value = _uiState.value.copy(
            exerciseState = ExerciseState.PLAYING,
            userMessage = evaluateFeedback(progression, listenResult.heard),
            heardTranscription =
              if (listenResult.heard.isEmpty()) "" else "I heard: ${listenResult.heard.joinToString("-")}"
          )
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
      port.beginListening(
        expectedProgression = expectedProgression,
        onTranscription = { transcription ->
          updateTranscriptionResult(
            "Transcribed: ${transcription.ifBlank { "<empty>" }}"
          )
        },
        onRepeat = {
          if (cont.isActive) cont.resume(ListenAttemptResult.Repeat)
        },
        onCorrect = {
          if (cont.isActive) cont.resume(ListenAttemptResult.Correct)
        },
        onIncorrect = { heard ->
          if (cont.isActive) cont.resume(ListenAttemptResult.Incorrect(heard))
        }
      )

      cont.invokeOnCancellation {
        port.stopListening()
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

  private suspend fun playProgression(progression: List<String>) {
    val instrument = nextPlaybackInstrument()
    playProgression(progression, instrument)
  }

  private suspend fun playProgression(
    progression: List<String>,
    instrument: String
  ) {

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

  private fun updateTranscriptionResult(message: String) {
    _transcriptionResult.value = message
  }

  private sealed class ListenAttemptResult {
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

    private fun progressionsWithFlats(): List<List<String>> = listOf(
      listOf("1", "4", "5", "4"),
      listOf("1", "5", "1", "4"),
      listOf("5", "4", "1", "4"),
      listOf("5", "4", "5", "1"),
      listOf("1", "b4"),
      listOf("1", "b5"),
      listOf("1", "b4", "b5", "1")
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
        val progression = inferProgressionFromFileName(file.nameWithoutExtension)
        val snippet = ProgressionSnippet(
          assetPath = file.absolutePath,
          displayName = file.nameWithoutExtension,
          progression = progression,
          source = SnippetSource.FILE
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

  private fun recordedSnippetsDir(context: Context): File =
    File(context.filesDir, "progression-snippets-recorded")

  private fun createUniqueSnippetFile(context: Context, baseName: String): File {
    val dir = recordedSnippetsDir(context)
    dir.mkdirs()
    var suffix = 1
    var candidate = File(dir, "$baseName.m4a")
    while (candidate.exists()) {
      suffix += 1
      candidate = File(dir, "${baseName}_$suffix.m4a")
    }
    return candidate
  }

  private fun loadRecordedSnippets(context: Context): List<ProgressionSnippet> {
    val dir = recordedSnippetsDir(context)
    if (!dir.exists()) return emptyList()
    return dir.listFiles()
      ?.filter { it.isFile && it.extension.equals("m4a", ignoreCase = true) }
      ?.sortedBy { it.name.lowercase() }
      ?.map { file ->
        ProgressionSnippet(
          assetPath = file.absolutePath,
          displayName = file.nameWithoutExtension,
          progression = inferProgressionFromFileName(file.nameWithoutExtension),
          source = SnippetSource.FILE
        )
      }
      ?: emptyList()
  }

  private fun inferProgressionFromFileName(name: String): String {
    val p = Regex("(\\d+b?\\d*|b\\d)(?:[_-](\\d+b?\\d*|b\\d)){1,4}").find(name)
    return p?.value?.replace("_", "-") ?: "custom"
  }
}

data class ProgressionSnippet(
  val assetPath: String,
  val displayName: String,
  val progression: String,
  val source: SnippetSource
)

enum class SnippetSource {
  ASSET,
  FILE
}

class AssetPlayer(private val context: Context) {

  private val chordMapByInstrument = mapOf(
    "piano" to mapOf(
      "1" to listOf("C4.mp3", "E4.mp3", "G4.mp3"),
      "4" to listOf("F4.mp3", "A4.mp3", "C5.mp3"),
      "5" to listOf("G4.mp3", "B4.mp3", "D5.mp3"),
      // raised one octave from prior baseline
      "6" to listOf("A4.mp3", "C5.mp3", "E5.mp3"),
      "b4" to listOf("F3.mp3", "A3.mp3", "C4.mp3"),
      "b5" to listOf("G3.mp3", "B3.mp3", "D4.mp3"),
      // raised one octave from prior baseline
      "b6" to listOf("A3.mp3", "C4.mp3", "E4.mp3")
    ),
    "guitar-acoustic" to mapOf(
      "1" to listOf("C4.mp3", "E4.mp3", "G4.mp3"),
      "4" to listOf("F4.mp3", "A4.mp3", "C5.mp3"),
      "5" to listOf("G4.mp3", "B4.mp3", "D5.mp3"),
      // guitar set has no E5 sample, so use highest available E4
      "6" to listOf("A4.mp3", "C5.mp3", "E4.mp3"),
      "b4" to listOf("F3.mp3", "A3.mp3", "C4.mp3"),
      "b5" to listOf("G3.mp3", "B3.mp3", "D4.mp3"),
      "b6" to listOf("A3.mp3", "C4.mp3", "E4.mp3")
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

  fun getAppContext(): Context = context
}
