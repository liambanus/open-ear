package com.openear.maestro.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.arm.stt.Whisper
import com.arm.stt.WhisperConfig

import com.openear.maestro.data.CommandParser
import com.openear.maestro.data.ProgressionAnswerEvaluator
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import java.io.File
import kotlin.math.sqrt
import java.util.concurrent.Executors

private const val TAG = "VoiceControl"

private const val VOICE_CHANNEL_ID = "maestro_voice_channel"
private const val VOICE_NOTIFICATION_ID = 101
private const val LISTEN_DELAY_MS = 500L
private const val LISTEN_WINDOW_MS = 5000L
private const val MODEL_PATH = "/data/local/tmp/model.bin"

private const val SAMPLE_RATE = 16000
private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

class VoiceControlService : Service() {



  interface Port {
    fun beginListening(
      expectedProgression: List<String>,
      onTranscription: (String) -> Unit,
      onParseDebug: (String) -> Unit,
      onRepeat: () -> Unit,
      onCorrect: () -> Unit,
      onIncorrect: (List<String>) -> Unit
    )
    fun stopListening()
  }


  inner class VoiceBinder : Binder() {
    fun port(): Port = portImpl
  }

  private val binder = VoiceBinder()
  private val scope = CoroutineScope(Dispatchers.IO)
  private val whisperDispatcher =
    Executors.newSingleThreadExecutor { runnable ->
      Thread(runnable, "WhisperThread").apply {
        isDaemon = true
      }
    }.asCoroutineDispatcher()

  private lateinit var commandParser: CommandParser
  private val evaluator = ProgressionAnswerEvaluator()
  private var wakeLock: PowerManager.WakeLock? = null
  private var listenJob: Job? = null

  private lateinit var whisper: Whisper
  private var whisperContext: Long = 0L
  private val whisperReady = CompletableDeferred<Unit>()
  @Volatile private var modelStatusMessage: String = "Initializing voice model..."

  private val whisperMutex = Mutex()

  private val portImpl = object : Port {

    override fun beginListening(
      expectedProgression: List<String>,
      onTranscription: (String) -> Unit,
      onParseDebug: (String) -> Unit,
      onRepeat: () -> Unit,
      onCorrect: () -> Unit,
      onIncorrect: (List<String>) -> Unit
    )
    {
      listenJob?.cancel()

      listenJob = scope.launch {
        try {
          delay(LISTEN_DELAY_MS)
          val result = collectAndProcessOnce(expectedProgression)
          when (result) {
            is ListenResult.Correct -> {
              onTranscription(result.transcription)
              onParseDebug(
                formatParseDebugLine(
                  normalized = result.normalized,
                  usedFallback = result.usedFallback
                )
              )
              onCorrect()
            }
            is ListenResult.Incorrect -> {
              onTranscription(result.transcription)
              onParseDebug(
                formatParseDebugLine(
                  normalized = result.normalized,
                  usedFallback = result.usedFallback
                )
              )
              onIncorrect(result.transcribed)
            }
            is ListenResult.Repeat -> {
              onTranscription(result.transcription)
              onParseDebug(
                formatParseDebugLine(
                  normalized = result.normalized,
                  usedFallback = result.usedFallback
                )
              )
              onRepeat()
            }
          }
        } catch (t: Throwable) {
          val msg = "Voice unavailable: ${t.message ?: "unknown error"}"
          Log.e(TAG, msg, t)
          onTranscription(msg)
          onRepeat()
        }
      }
    }

    override fun stopListening() {
      listenJob?.cancel()
      listenJob = null
    }
  }

  override fun onCreate() {
    super.onCreate()

    commandParser = CommandParser(this)

    val modelFile = File(MODEL_PATH)
    Log.i(TAG, "Model path=$MODEL_PATH exists=${modelFile.exists()} size=${modelFile.length()}")
    if (!modelFile.exists()) {
      modelStatusMessage = "Model missing at $MODEL_PATH"
    }

    scope.launch(whisperDispatcher) {
      try {
        whisper = Whisper()
        whisperContext = whisper.initContext(MODEL_PATH)

        // REQUIRED: init params before first inference
        whisper.initParameters(
          WhisperConfig(
            false,  // printRealTime
            false,  // printProgress
            false,  // timeStamps
            false,  // printSpecial
            false,  // translate
            "en",   // language
            2,      // numThreads (keep small for POC)
            0,      // offsetMs
            false,  // noContext
            true    // singleSegment
          )
        )

        Log.i(
          TAG,
          "Whisper context initialized: ctx=$whisperContext thread=${Thread.currentThread().id}"
        )
        modelStatusMessage = "Voice model ready"
        whisperReady.complete(Unit)
      } catch (t: Throwable) {
        modelStatusMessage = "Voice model init failed: ${t.message ?: "unknown error"}"
        whisperReady.completeExceptionally(t)
        Log.e(TAG, "Failed to initialize Whisper context", t)
      }
    }



    ensureChannel()
    acquireWakeLock()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    startForeground(
      VOICE_NOTIFICATION_ID,
      buildNotification(modelStatusMessage)
    )
    return START_STICKY
  }

  override fun onDestroy() {
    listenJob?.cancel()
    scope.cancel()
    scope.launch(whisperDispatcher) {
      whisperReady.await()
    }
    releaseWakeLock()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder = binder

//  private enum class ListenResult {
//    CORRECT, INCORRECT, REPEAT
//  }

  private sealed class ListenResult {
    data class Correct(
      val transcription: String,
      val normalized: List<String>,
      val usedFallback: Boolean
    ) : ListenResult()
    data class Repeat(
      val transcription: String,
      val normalized: List<String> = emptyList(),
      val usedFallback: Boolean = false
    ) : ListenResult()
    data class Incorrect(
      val transcribed: List<String>,
      val transcription: String,
      val normalized: List<String>,
      val usedFallback: Boolean
    ) : ListenResult()
  }
  private suspend fun collectAndProcessOnce(
    expectedProgression: List<String>
  ): ListenResult {

    // Ensure whisper is ready before recording/transcribing
    try {
      whisperReady.await()
    } catch (_: Throwable) {
      return ListenResult.Repeat(modelStatusMessage)
    }

    val audioData = recordAudioSample()

    if (whisperContext == 0L) {
      logRepeat("whisperContext=0")
      return ListenResult.Repeat("")
    }

    val transcription = withContext(whisperDispatcher) {
      whisperMutex.withLock {
        whisper.fullTranscribe(whisperContext, audioData)
      }
    }

    if (transcription.isBlank()) {
      logRepeat("blank transcription")
      return ListenResult.Repeat("")
    }

    Log.i(TAG, "Transcription='$transcription'")

    val parsed = commandParser.parse(transcription)
    var usedFallback = false
    val normalized = when (parsed) {
      is CommandParser.Result.Answer -> normalizeAnswerTokens(parsed.tokens)
      else -> {
        val fallback = extractAnswerTokensFallback(transcription)
        if (fallback.isNotEmpty()) {
          usedFallback = true
          Log.d(TAG, "Fallback token extraction used: $fallback")
        } else {
          logRepeat("unparsed transcription='$transcription'")
          return ListenResult.Repeat(transcription)
        }
        fallback
      }
    }

    return if (evaluator.isCorrect(expectedProgression, normalized)) {
      Log.i(TAG, "ListenResult=CORRECT normalized=$normalized")
      ListenResult.Correct(transcription, normalized, usedFallback)
    } else {
      logIncorrect(transcription, normalized)
      ListenResult.Incorrect(
        transcribed = normalized,
        transcription = transcription,
        normalized = normalized,
        usedFallback = usedFallback
      )
    }
  }

  private fun formatParseDebugLine(normalized: List<String>, usedFallback: Boolean): String {
    val tokensText = if (normalized.isEmpty()) "<none>" else normalized.joinToString("-")
    val source = if (usedFallback) "fallback" else "parser"
    return "Parsed: $tokensText ($source)"
  }

  private fun normalizeToken(token: String): String {
    val cleaned = token
      .lowercase()
      .replace(Regex("[^a-z0-9]"), "") // remove commas, periods, spaces, etc.

    return when (cleaned) {
      "one" -> "1"
      "two" -> "2"
      "three" -> "3"
      "four" -> "4"
      "five" -> "5"
      "six" -> "6"
      "seven" -> "7"
      "low" -> "lo"
      "lower" -> "lo"
      "flat" -> "b"
      "sharp" -> "#"
      else -> cleaned
    }
  }

  private fun normalizeAnswerTokens(tokens: List<String>): List<String> {
    val raw = tokens.map { normalizeToken(it) }.filter { it.isNotBlank() }
    return mergeModifierTokens(raw)
  }

  private fun extractAnswerTokensFallback(transcription: String): List<String> {
    val raw = transcription
      .lowercase()
      .replace(Regex("[^a-z0-9#\\s]"), " ")
      .split("\\s+".toRegex())
      .filter { it.isNotBlank() }
      .map { normalizeToken(it) }
      .filter { it.isNotBlank() }

    return mergeModifierTokens(raw)
      .filter { it.matches(Regex("(?:lo|b|#)?[1-7]")) }
  }

  private fun mergeModifierTokens(raw: List<String>): List<String> {
    val output = mutableListOf<String>()
    var i = 0
    while (i < raw.size) {
      val current = raw[i]
      val next = raw.getOrNull(i + 1)
      if ((current == "b" || current == "#" || current == "lo") &&
        next != null &&
        next.matches(Regex("[1-7]"))
      ) {
        output += "$current$next"
        i += 2
        continue
      }
      output += current
      i += 1
    }
    return output
  }


  private suspend fun recordAudioSample(): FloatArray {
    playBeep(ToneGenerator.TONE_PROP_BEEP, 60)

    val totalSamples = (SAMPLE_RATE * (LISTEN_WINDOW_MS / 1000f)).toInt()
    val audioBuffer = ShortArray(totalSamples)

    val minBufferSize = AudioRecord.getMinBufferSize(
      SAMPLE_RATE,
      CHANNEL_CONFIG,
      AUDIO_FORMAT
    )

    Log.d(TAG, "AudioRecord minBufferSize=$minBufferSize totalSamples=$totalSamples")

    val recorder = AudioRecord(
      MediaRecorder.AudioSource.MIC,
      SAMPLE_RATE,
      CHANNEL_CONFIG,
      AUDIO_FORMAT,
      maxOf(minBufferSize, totalSamples * 2)
    )

    try {
      recorder.startRecording()
      Log.d(TAG, "Recording started")

      var samplesRead = 0
      while (samplesRead < totalSamples) {
        val read = recorder.read(
          audioBuffer,
          samplesRead,
          totalSamples - samplesRead
        )
        if (read <= 0) {
          Log.w(TAG, "AudioRecord read=$read at samplesRead=$samplesRead")
          break
        }
        samplesRead += read
      }

      Log.d(TAG, "Recording complete samplesRead=$samplesRead")
    } finally {
      recorder.stop()
      recorder.release()
      playBeep(ToneGenerator.TONE_PROP_ACK, 60)
    }

    val floatBuffer = FloatArray(totalSamples)
    var sumSq = 0.0
    for (i in 0 until totalSamples) {
      val v = audioBuffer[i] / 32768.0f
      floatBuffer[i] = v
      sumSq += v * v
    }

    val rms = sqrt(sumSq / totalSamples)
    Log.i(TAG, "Audio RMS=$rms")

    return floatBuffer
  }

  private fun ensureChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val ch = NotificationChannel(
        VOICE_CHANNEL_ID,
        "Maestro Voice",
        NotificationManager.IMPORTANCE_LOW
      )
      mgr.createNotificationChannel(ch)
    }
  }

  private fun buildNotification(text: String): Notification =
    NotificationCompat.Builder(this, VOICE_CHANNEL_ID)
      .setContentTitle("Maestro Voice Control")
      .setContentText(text)
      .setSmallIcon(android.R.drawable.ic_btn_speak_now)
      .setOngoing(true)
      .build()

  private fun acquireWakeLock() {
    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
    wakeLock = pm.newWakeLock(
      PowerManager.PARTIAL_WAKE_LOCK,
      "Maestro:VoiceControl"
    ).apply {
      setReferenceCounted(false)
      acquire()
    }
  }

  private fun releaseWakeLock() {
    wakeLock?.let { if (it.isHeld) it.release() }
  }

  private fun logRepeat(reason: String) {
    Log.d(TAG, "ListenResult=REPEAT ($reason) — retrying")
  }

  private fun logIncorrect(transcription: String, normalized: List<String>) {
    Log.d(
      TAG,
      "ListenResult=INCORRECT transcription='$transcription' normalized=$normalized — retrying"
    )
  }

  private fun playBeep(tone: Int, durationMs: Int) {
    runCatching {
      val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 25)
      try {
        tg.startTone(tone, durationMs)
      } finally {
        tg.release()
      }
    }
  }
}

//  interface Port {
//    fun beginListening(
//      expectedProgression: List<String>,
//      onRepeat: () -> Unit,
//      onCorrect: () -> Unit,
//      onUnknown: () -> Unit
//    )
//  }
