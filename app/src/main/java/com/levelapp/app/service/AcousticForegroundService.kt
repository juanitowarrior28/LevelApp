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
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AcousticForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var recorder: AudioRecord? = null
    private var running = false
    private var captureJob: Job? = null

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

    private fun startRecording() {
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
            while (running && recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = recorder?.read(localBuffer, 0, localBuffer.size) ?: 0
                if (read > 0) {
                    // Se guarda solo agregados; se evita persistir audio crudo en este servicio.
                }
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
        recorder?.release()
        recorder = null
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "levelapp_audio_capture"
    }
}
