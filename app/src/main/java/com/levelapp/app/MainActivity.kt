package com.levelapp.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.levelapp.app.service.AcousticForegroundService
import com.levelapp.app.service.AudioDataStream
import com.levelapp.app.ui.AcousticSessionScreen
import com.levelapp.app.ui.DosimetroTheme
import com.levelapp.app.ui.HistoryScreen
import com.levelapp.app.viewmodel.AcousticSessionViewModel
import com.levelapp.app.viewmodel.AcousticSessionViewModelFactory

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results[Manifest.permission.RECORD_AUDIO] == true) {
            ensureServiceRunningIfRecording()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AudioDataStream.init(applicationContext)
        requestRuntimePermissionsIfNeeded()

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
                                requestRuntimePermissionsIfNeeded()
                                requestIgnoreBatteryOptimizations()
                                startCaptureService()
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

    override fun onResume() {
        super.onResume()
        // Si Android/MIUI mató el proceso durante una medición, la sesión se restauró
        // desde disco: reanudamos la captura sin perder la dosis acumulada.
        ensureServiceRunningIfRecording()
    }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun ensureServiceRunningIfRecording() {
        if (AudioDataStream.isRecording && !AudioDataStream.serviceAlive && hasAudioPermission()) {
            startCaptureService()
        }
    }

    private fun startCaptureService() {
        if (!hasAudioPermission()) return
        try {
            ContextCompat.startForegroundService(this, Intent(this, AcousticForegroundService::class.java))
        } catch (e: Exception) {
            Log.w("LevelApp", "No se pudo iniciar el servicio de captura", e)
        }
    }

    private fun requestRuntimePermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (!hasAudioPermission()) needed += Manifest.permission.RECORD_AUDIO
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }

    /**
     * Para jornadas de 8 h+ es imprescindible que el sistema no aplique ahorro de
     * batería a la app (en Xiaomi/MIUI este ahorro mata los servicios en segundo plano).
     */
    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() {
        try {
            val pm = getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                )
            }
        } catch (e: Exception) {
            Log.w("LevelApp", "No se pudo solicitar exención de batería", e)
        }
    }
}
