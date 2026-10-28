package com.levelapp.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.levelapp.app.service.AcousticForegroundService
import com.levelapp.app.ui.AcousticSessionScreen
import com.levelapp.app.ui.LevelAppTheme

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startForegroundService(Intent(this, AcousticForegroundService::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestAudioPermissionIfNeeded()
        setContent {
            LevelAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AcousticSessionScreen(
                        onStartSession = {
                            requestAudioPermissionIfNeeded()
                            startForegroundService(Intent(this, AcousticForegroundService::class.java))
                        },
                        onPauseSession = { },
                        onFinishSession = { stopService(Intent(this, AcousticForegroundService::class.java)) },
                    )
                }
            }
        }
    }

    private fun requestAudioPermissionIfNeeded() {
        val permission = Manifest.permission.RECORD_AUDIO
        val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            permissionLauncher.launch(permission)
        }
    }
}
