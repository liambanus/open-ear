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
import androidx.core.app.NotificationCompat
import com.openear.maestro.data.CommandParser
import com.openear.maestro.data.ProgressionAnswerEvaluator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.arm.stt.Whisper

private const val VOICE_CHANNEL_ID = "maestro_voice_channel"
private const val VOICE_NOTIFICATION_ID = 101
private const val LISTEN_DELAY_MS = 500L
private const val LISTEN_WINDOW_MS = 5000L

class VoiceControlService : Service() {

  interface Port {
    fun beginListening(
      expectedProgression: List<String>,
      onRepeat: () -> Unit,
      onCorrect: () -> Unit,
      onUnknown: () -> Unit
    )
  }

  inner class VoiceBinder : Binder() {
    fun port(): Port = portImpl
  }

  private val binder = VoiceBinder()
  private val scope = CoroutineScope(Dispatchers.IO)
  private lateinit var commandParser: CommandParser
  private val evaluator = ProgressionAnswerEvaluator()
  private var wakeLock: PowerManager.WakeLock? = null
  private var listenJob: Job? = null

  // TODO: replace stub with JNI-backed client from external STT module
  private val sttClient: SttClient = SttClientStub()

  private val portImpl = object : Port {
    override fun beginListening(
      expectedProgression: List<String>,
      onRepeat: () -> Unit,
      onCorrect: () -> Unit,
      onUnknown: () -> Unit
    ) {
      listenJob?.cancel()
      listenJob = scope.launch {
        delay(LISTEN_DELAY_MS)
        collectAndProcess(expectedProgression, onRepeat, onCorrect, onUnknown)
      }
    }
  }

  override fun onCreate() {
    super.onCreate()
    try {
      val w = Whisper()
      // Use a dummy path to confirm the JNI symbol is found; it will likely throw if file missing.
      // Swap with a real model path when ready.
      val ctx = w.initContext("/data/local/tmp/model.bin")
      android.util.Log.d("JNI_TEST", "initContext returned $ctx")
    } catch (t: Throwable) {
      android.util.Log.e("JNI_TEST", "load/call failed", t)
    }
    commandParser = CommandParser(this)
    ensureChannel()
//    startForeground(VOICE_NOTIFICATION_ID, buildNotification("Listening for your answer…"))
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
    sttClient.stop()
    releaseWakeLock()
    scope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder = binder

  private suspend fun collectAndProcess(
    expectedProgression: List<String>,
    onRepeat: () -> Unit,
    onCorrect: () -> Unit,
    onUnknown: () -> Unit
  ) {
    val finals = mutableListOf<String>()
    sttClient.start(onPartial = { partial ->
      android.util.Log.d("STT_PARTIAL", partial)
    }, onFinal = { final ->
      finals.add(final)
      android.util.Log.d("STT_FINAL", final)
    })

    withTimeoutOrNull(LISTEN_WINDOW_MS) {
      while (finals.isEmpty()) delay(500) // Sliding window delay
    }
    sttClient.stop()

    val combined = finals.joinToString(" ").trim()
    if (combined.isEmpty()) {
      onRepeat()
      return
    }

    val numbers = listOf("1", "4", "5", "one", "four", "five")
    val result = numbers.any { it.equals(combined, ignoreCase = true) }

    if (result) onCorrect() else onUnknown()
  }

//  private suspend fun collectAndProcess(
//    expectedProgression: List<String>,
//    onRepeat: () -> Unit,
//    onCorrect: () -> Unit,
//    onUnknown: () -> Unit
//  ) {
//    val finals = mutableListOf<String>()
//    sttClient.start(onPartial = {}, onFinal = { finals.add(it) })
//
//    withTimeoutOrNull(LISTEN_WINDOW_MS) {
//      while (finals.isEmpty()) delay(100)
//    }
//    sttClient.stop()
//
//    val combined = finals.joinToString(" ").trim()
//    if (combined.isEmpty()) {
//      onRepeat(); return
//    }
//    when (val parsed = commandParser.parse(combined)) {
//      is CommandParser.Result.Command -> if (parsed.keyword == "repeat") onRepeat() else onUnknown()
//      is CommandParser.Result.Answer -> {
//        val ok = evaluator.isCorrect(expectedProgression, parsed.tokens)
//        if (ok) onCorrect() else onUnknown()
//      }
//      CommandParser.Result.None -> onUnknown()
//    }
//  }

  private fun ensureChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val ch = NotificationChannel(VOICE_CHANNEL_ID, "Maestro Voice", NotificationManager.IMPORTANCE_LOW)
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
    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Maestro:VoiceControl").apply {
      setReferenceCounted(false); acquire()
    }
  }
  private fun releaseWakeLock() { wakeLock?.let { if (it.isHeld) it.release() } }

  interface SttClient {
    fun start(onPartial: (String) -> Unit, onFinal: (String) -> Unit)
    fun stop()
  }
  private class SttClientStub : SttClient {
    override fun start(onPartial: (String) -> Unit, onFinal: (String) -> Unit) { /* hook JNI here */ }
    override fun stop() { /* hook JNI here */ }
  }
}
