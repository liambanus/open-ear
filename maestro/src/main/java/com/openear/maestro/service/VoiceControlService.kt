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
import android.media.AudioRecord
import android.media.MediaRecorder
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

//  interface Port {
//    fun beginListening(
//      expectedProgression: List<String>,
//      onRepeat: () -> Unit,
//      onCorrect: () -> Unit,
//      onUnknown: () -> Unit
//    )
//  }

  interface Port {
    fun beginListening(
      expectedProgression: List<String>,
      onCorrect: () -> Unit
    )
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

  private val whisperMutex = Mutex()

  private val portImpl = object : Port {

    override fun beginListening(
      expectedProgression: List<String>,
      onCorrect: () -> Unit
    ) {
      listenJob?.cancel()
      listenJob = scope.launch {
        delay(LISTEN_DELAY_MS)

        while (isActive) {
          val result = collectAndProcessOnce(expectedProgression)
          when (result) {
            ListenResult.CORRECT -> {
              onCorrect()
              return@launch
            }
            ListenResult.REPEAT,
            ListenResult.INCORRECT -> {
              delay(300) // retry
            }
          }
        }
      }
    }
  }
    override fun onCreate() {
    super.onCreate()

    commandParser = CommandParser(this)

    val modelFile = File(MODEL_PATH)
    Log.i(TAG, "Model path=$MODEL_PATH exists=${modelFile.exists()} size=${modelFile.length()}")

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
        whisperReady.complete(Unit)
      } catch (t: Throwable) {
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
      buildNotification("Listening for your answer…")
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

private enum class ListenResult {
  CORRECT, INCORRECT, REPEAT
}

  private suspend fun collectAndProcessOnce(
    expectedProgression: List<String>
  ): ListenResult {

    // Ensure whisper is ready before recording/transcribing
    whisperReady.await()

    val audioData = recordAudioSample()

    if (whisperContext == 0L) {
      logRepeat("whisperContext=0")
      return ListenResult.REPEAT
    }

    val transcription = withContext(whisperDispatcher) {
      whisperMutex.withLock {
        whisper.fullTranscribe(whisperContext, audioData)
      }
    }

    if (transcription.isBlank()) {
      logRepeat("blank transcription")
      return ListenResult.REPEAT
    }

    Log.i(TAG, "Transcription='$transcription'")

    val parsed = commandParser.parse(transcription)
    if (parsed !is CommandParser.Result.Answer) {
      logIncorrect(transcription, emptyList())
      return ListenResult.INCORRECT
    }

    val normalized = parsed.tokens.map { normalizeToken(it) }

    return if (evaluator.isCorrect(expectedProgression, normalized)) {
      Log.i(TAG, "ListenResult=CORRECT normalized=$normalized")
      ListenResult.CORRECT
    } else {
      logIncorrect(transcription, normalized)
      ListenResult.INCORRECT
    }
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
      else -> cleaned
    }
  }




  private suspend fun recordAudioSample(): FloatArray {
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
