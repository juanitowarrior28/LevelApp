package com.levelapp.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AcousticForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var recorder: AudioRecord? = null
    private var running = false
    private var captureJob: Job? = null
    private var timerJob: Job? = null
    private var startTimeMs = 0L
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1001, buildNotification())
        if (!running) {
            running = true
            startRecording()
        }
        return START_STICKY
    }

    @SuppressWarnings("MissingPermission")
    private fun startRecording() {
        // Acquire WakeLock to keep CPU running with screen off
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LevelApp::AcousticWakeLock")
        wakeLock?.acquire(8 * 60 * 60 * 1000L /* 8 hours max */)

        startTimeMs = System.currentTimeMillis()
        startNotificationTimer()

        val sampleRate = 48000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val format = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, format)
        if (bufferSize <= 0) {
            stopSelf()
            return
        }

        val source = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder.AudioSource.UNPROCESSED
        } else {
            MediaRecorder.AudioSource.MIC
        }

        recorder = AudioRecord(
            source,
            sampleRate,
            channelConfig,
            format,
            bufferSize * 2,
        )
        recorder?.startRecording()

        captureJob = serviceScope.launch {
            val localBuffer = ShortArray(bufferSize)

            // Variables del Filtro de Ponderación A (IIR Direct Form II) extraídas del repositorio original (AudioMeter.kt)
            var z1 = 0.0
            var z2 = 0.0
            val b0 = 0.16999495
            val b1 = 0.28039060
            val b2 = -0.11204747
            val a1 = -2.12979410
            val a2 = 0.42996125

            while (running && recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = recorder?.read(localBuffer, 0, localBuffer.size) ?: 0
                if (read > 0) {
                    var sumSquare = 0.0

                    for (i in 0 until read) {
                        val x = localBuffer[i].toDouble()

                        // Aplicación del filtro de ponderación A (Direct Form II)
                        val out = b0 * x + z1
                        z1 = b1 * x - a1 * out + z2
                        z2 = b2 * x - a2 * out
                        
                        // Si el filtro produce un error matemático, usamos el valor original
                        val finalSample = if (out.isNaN() || out.isInfinite()) x else out
                        sumSquare += finalSample * finalSample
                    }

                    // Cálculo del RMS (Raíz Cuadrática Media)
                    val rms = Math.sqrt(sumSquare / read)
                    val normalizedRMS = rms / 32768.0 // Normalización a escala 16-bit
                    
                    // Conversión a decibelios (dB) con referencia de 100 dB SPL (de AudioMeter.kt)
                    val db = if (normalizedRMS > 1e-10) {
                        20.0 * Math.log10(normalizedRMS) + 100.0
                    } else {
                        35.0
                    }

                    // Calibration offset (por ahora fijo, luego lo pasaremos del ViewModel)
                    val calibrationOffset = 0.0
                    val finalDb = (db + calibrationOffset).coerceIn(35.0, 110.0)
                    
                    AudioDataStream.emitDb(finalDb)
                }
            }
        }
    }

    private fun startNotificationTimer() {
        timerJob = serviceScope.launch {
            val notificationManager = getSystemService(NotificationManager::class.java)
            while (running) {
                delay(1000)
                val elapsedSeconds = (System.currentTimeMillis() - startTimeMs) / 1000
                val hours = elapsedSeconds / 3600
                val minutes = (elapsedSeconds % 3600) / 60
                val seconds = elapsedSeconds % 60
                
                val timeString = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                
                val builder = NotificationCompat.Builder(this@AcousticForegroundService, CHANNEL_ID)
                    .setContentTitle("LevelApp - Midiendo Ruido")
                    .setContentText("Tiempo transcurrido: $timeString")
                    .setSmallIcon(android.R.drawable.stat_sys_speakerphone)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOnlyAlertOnce(true)
                
                notificationManager.notify(1001, builder.build())
            }
        }
    }

    private fun buildNotification(): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LevelApp")
            .setContentText("Grabación acústica en ejecución")
            .setSmallIcon(android.R.drawable.stat_sys_speakerphone)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Grabación acústica",
                NotificationManager.IMPORTANCE_LOW,
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        running = false
        captureJob?.cancel()
        timerJob?.cancel()
        
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null

        recorder?.release()
        recorder = null
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "levelapp_audio_capture"
    }
}
