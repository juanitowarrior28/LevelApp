package com.levelapp.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.levelapp.app.service.AcousticForegroundService
import com.levelapp.app.ui.DosimetroTheme
import com.levelapp.app.ui.AcousticSessionScreen
import com.levelapp.app.ui.HistoryScreen
import com.levelapp.app.viewmodel.AcousticSessionViewModel
import com.levelapp.app.viewmodel.AcousticSessionViewModelFactory

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
        
        val app = application as LevelAppApplication
        val repository = app.sessionRepository
        
        setContent {
            DosimetroTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val viewModel: AcousticSessionViewModel = viewModel(
                        factory = AcousticSessionViewModelFactory(repository)
                    )
                    
                    var currentScreen by remember { mutableStateOf("Main") }
                    
                    if (currentScreen == "Main") {
                        AcousticSessionScreen(
                            viewModel = viewModel,
                            onStartService = {
                                requestAudioPermissionIfNeeded()
                                startForegroundService(Intent(this, AcousticForegroundService::class.java))
                            },
                            onStopService = { stopService(Intent(this, AcousticForegroundService::class.java)) },
                            onOpenHistory = { currentScreen = "History" }
                        )
                    } else {
                        HistoryScreen(
                            viewModel = viewModel,
                            onBack = { currentScreen = "Main" }
                        )
                    }
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
