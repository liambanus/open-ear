package com.openear.maestro

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.openear.maestro.service.VoiceControlService
import com.openear.maestro.ui.MaestroApp
import com.openear.maestro.ui.MaestroViewModel
import com.openear.maestro.ui.theme.MaestroTheme

class MainActivity : ComponentActivity() {

  private val viewModel: MaestroViewModel by viewModels()
  private var bound = false

  private val requestAudioPermission =
    registerForActivityResult(
      androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
      if (granted) {
        startAndBindVoiceService()
      }
    }


  private val connection = object : ServiceConnection {

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
      val binder = service as VoiceControlService.VoiceBinder
      viewModel.setVoiceControlPort(binder.port())
      bound = true
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      bound = false
    }
  }
    private fun startAndBindVoiceService() {
      Intent(this, VoiceControlService::class.java).also {
        startForegroundService(it)
        bindService(it, connection, Context.BIND_AUTO_CREATE)
      }
    }


  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.RECORD_AUDIO
      ) == PackageManager.PERMISSION_GRANTED
    ) {
      startAndBindVoiceService()
    } else {
      requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    setContent {
      MaestroTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          MaestroApp(viewModel = viewModel)
        }
      }
    }
  }

//  override fun onRequestPermissionsResult(
//    requestCode: Int,
//    permissions: Array<out String>,
//    grantResults: IntArray
//  ) {
//    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//
//    if (requestCode == 1001 &&
//      grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
//    ) {
//      startAndBindVoiceService()
//    }
//  }


  override fun onDestroy() {
    if (bound) unbindService(connection)
    super.onDestroy()
  }
}
