package com.openear.maestro.ui

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MaestroUiState(
    val showMainMenu: Boolean = true,
    val userMessage: String = "Press 'Chord Progression' to start.",
    val isListening: Boolean = false,
    val isFinished: Boolean = false,
)

class MaestroViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MaestroUiState())
    val uiState: StateFlow<MaestroUiState> = _uiState.asStateFlow()

    private var sttModule: Any? = null // Stub for your STT module
    private lateinit var assetPlayer: AssetPlayer

    // Exercise details
    private val correctProgression = listOf("C", "F", "G", "F")
    private val correctAnswer = "1454"

    fun initialize(context: Context) {
        assetPlayer = AssetPlayer(context.applicationContext)
        // TODO: Initialize STT module
        // try {
        //     sttModule = SttModule(context)
        // } catch (e: Exception) {
        //     Log.e("ViewModel", "Failed to initialize STT module", e)
        //     // Update UI to show error
        // }
    }

    fun startChordProgressionExercise() {
        _uiState.update {
            it.copy(
                showMainMenu = false,
                userMessage = "Listen to the progression."
            )
        }
        requestProgressionPlayback()
    }

    fun requestProgressionPlayback() {
        viewModelScope.launch {
            playProgression(correctProgression)
            _uiState.update { it.copy(userMessage = "What progression did you hear?") }
            // Start listening automatically after playback
            // For now, we rely on manual input
        }
    }

    private suspend fun playProgression(progression: List<String>) {
        for (chord in progression) {
            Log.d("ViewModel", "Playing chord: $chord")
            assetPlayer.playChord(chord, "piano")
            delay(1500) // Delay between chords
        }
        Log.d("ViewModel", "Progression finished")
    }

    fun checkTextAnswer(answer: String) {
        if (answer.replace("\\s".toRegex(), "") == correctAnswer) {
            _uiState.update { it.copy(isFinished = true) }
        } else {
            _uiState.update { it.copy(userMessage = "Not quite. Try again.") }
            requestProgressionPlayback()
        }
    }

    // STUB for voice recognition
    fun startListening() {
        _uiState.update { it.copy(isListening = true, userMessage = "Listening...") }
        viewModelScope.launch {
            delay(2000) // Initial delay
            // sttModule.startRecording(...)
            delay(5000) // Record for 5 seconds
            // val transcript = sttModule.stopRecording()
            val transcript = "1 4 5 4" // MOCK TRANSCRIPT
            processTranscript(transcript)
        }
    }

    private fun processTranscript(transcript: String) {
        _uiState.update { it.copy(isListening = false) }
        val sanitized = transcript.lowercase().replace("\\s".toRegex(), "")
        Log.d("ViewModel", "Processing transcript: '$sanitized'")

        if (sanitized.contains("repeat")) {
            _uiState.update { it.copy(userMessage = "Repeating progression.") }
            requestProgressionPlayback()
        } else if (sanitized == correctAnswer) {
            _uiState.update { it.copy(isFinished = true) }
        } else {
            _uiState.update { it.copy(userMessage = "Not quite. Try again.") }
            requestProgressionPlayback()
        }
    }

    fun reset() {
        _uiState.value = MaestroUiState()
    }
}

// STUB for AssetPlayer - This needs to be implemented to play sounds from assets
class AssetPlayer(private val context: Context) {
    private val chordMap = mapOf(
        "C" to listOf("C4.mp3", "E4.mp3", "G4.mp3"),
        "F" to listOf("F4.mp3", "A4.mp3", "C5.mp3"),
        "G" to listOf("G4.mp3", "B4.mp3", "D5.mp3")
    )

    suspend fun playChord(chord: String, instrument: String) {
        val notes = chordMap[chord] ?: return
        notes.map { noteFile ->
            MediaPlayer().apply {
                try {
                    val afd = context.assets.openFd("$instrument/$noteFile")
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                    prepare()
                } catch (e: Exception) {
                    Log.e("AssetPlayer", "Error preparing sound $noteFile", e)
                }
            }
        }.forEach { mediaPlayer ->
            mediaPlayer.start()
            mediaPlayer.setOnCompletionListener { it.release() }
        }
        delay(1000) // Duration of the chord
    }
}
