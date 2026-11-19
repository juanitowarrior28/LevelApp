package com.levelapp.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.levelapp.app.MainActivity
import com.levelapp.core.audio.AWeightingFilter
import com.levelapp.core.dosimetry.RegulatoryProfile

/**
 * Servicio en primer plano que captura el micrófono durante toda la jornada.
 * Diseñado para funcionar 8 h+ continuas:
 *  - Hilo de audio dedicado con prioridad de audio.
 *  - Si el micrófono falla (llamada, otra app, error del driver) se recrea el AudioRecord
 *    en lugar de terminar la captura.
 *  - WakeLock parcial sin expiración corta (se libera en onDestroy).
 *  - Ningún error de captura puede tumbar la app (todo protegido con try/catch).
 */
class AcousticForegroundService : Service() {
    @Volatile private var running = false
    private var captureThread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AudioDataStream.init(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        } catch (e: Exception) {
            // Android no permite iniciar el micrófono desde segundo plano (p. ej. reinicio
            // automático tras matar el proceso). La sesión queda guardada y se reanuda
            // cuando el usuario abre la app.
            Log.w(TAG, "No se pudo iniciar en primer plano", e)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!AudioDataStream.isRecording) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!running) {
            running = true
            AudioDataStream.serviceAlive = true
            acquireWakeLock()
            captureThread = Thread({ captureLoop() }, "LevelApp-Audio").also { it.start() }
        }
        return START_STICKY
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LevelApp::AcousticWakeLock").apply {
                setReferenceCounted(false)
                acquire(24 * 60 * 60 * 1000L) // 24 h: cubre de sobra jornadas de 8-12 h
            }
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock no disponible", e)
        }
    }

    @SuppressWarnings("MissingPermission")
    private fun createRecorder(): AudioRecord? {
        val channel = AudioFormat.CHANNEL_IN_MONO
        val format = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, channel, format)
        if (minBuf <= 0) return null
        val bufferBytes = maxOf(minBuf * 4, BLOCK_SAMPLES * 2 * 4)

        val sources = mutableListOf<Int>()
        val am = getSystemService(AudioManager::class.java)
        if (am?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true") {
            sources += MediaRecorder.AudioSource.UNPROCESSED
        }
        sources += MediaRecorder.AudioSource.VOICE_RECOGNITION // sin AGC en la mayoría de equipos
        sources += MediaRecorder.AudioSource.MIC

        for (source in sources) {
            try {
                val rec = AudioRecord(source, SAMPLE_RATE, channel, format, bufferBytes)
                if (rec.state == AudioRecord.STATE_INITIALIZED) {
                    rec.startRecording()
                    if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) return rec
                }
                rec.release()
            } catch (e: Exception) {
                Log.w(TAG, "Fuente de audio $source no disponible", e)
            }
        }
        return null
    }

    private fun captureLoop() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        } catch (_: Exception) { }

        val buffer = ShortArray(BLOCK_SAMPLES)
        var lastNotificationMs = 0L

        while (running) {
            val recorder = createRecorder()
            if (recorder == null) {
                sleepQuietly(1000)
                continue
            }
            // Filtro nuevo por cada (re)inicio del micrófono + descarte del arranque:
            // los primeros ~500 ms del micrófono traen transitorios (pop / DC) que
            // generaban el pico falso al iniciar la medición.
            val filter = AWeightingFilter(SAMPLE_RATE)
            var samplesToDiscard = WARMUP_SAMPLES
            var filled = 0
            var sumSquare = 0.0

            try {
                while (running) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read < 0) {
                        Log.w(TAG, "AudioRecord.read error $read, recreando micrófono")
                        break
                    }
                    if (read == 0) continue

                    for (i in 0 until read) {
                        val y = filter.processSample(buffer[i].toDouble())
                        if (samplesToDiscard > 0) {
                            samplesToDiscard--
                            continue
                        }
                        sumSquare += y * y
                        filled++
                        if (filled == BLOCK_SAMPLES) {
                            val rms = Math.sqrt(sumSquare / filled) / 32768.0
                            val db = if (rms > 1e-10) 20.0 * Math.log10(rms) + 100.0 else MIN_DB
                            AudioDataStream.onAudioBlock(
                                db.coerceIn(MIN_DB, MAX_DB),
                                filled.toDouble() / SAMPLE_RATE,
                            )
                            filled = 0
                            sumSquare = 0.0
                        }
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastNotificationMs >= 1000L) {
                        lastNotificationMs = now
                        updateNotification()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en la captura, reintentando", e)
            } finally {
                try { recorder.stop() } catch (_: Exception) { }
                try { recorder.release() } catch (_: Exception) { }
            }
            if (running) sleepQuietly(500)
        }
    }

    private fun sleepQuietly(ms: Long) {
        try { Thread.sleep(ms) } catch (_: InterruptedException) { }
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun updateNotification() {
        try {
            val state = AudioDataStream.uiState.value
            val totalSeconds = (state.durationMinutes * 60).toLong()
            val time = String.format("%02d:%02d:%02d", totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60)
            val profile = RegulatoryProfile.byId(state.profileId).name
            val text = "Tiempo: $time · Dosis $profile: ${"%.2f".format(state.dose)}%"
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, baseBuilder().setContentTitle("LevelApp - Midiendo ruido").setContentText(text).build())
        } catch (_: Exception) { }
    }

    private fun baseBuilder(): NotificationCompat.Builder =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_speakerphone)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())

    private fun buildNotification(): Notification =
        baseBuilder()
            .setContentTitle("LevelApp")
            .setContentText("Medición acústica en curso")
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Grabación acústica", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        running = false
        AudioDataStream.serviceAlive = false
        try { captureThread?.join(1500) } catch (_: Exception) { }
        captureThread = null
        AudioDataStream.flush()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) { }
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LevelAppService"
        const val CHANNEL_ID = "levelapp_audio_capture"
        private const val NOTIFICATION_ID = 1001
        private const val SAMPLE_RATE = AudioDataStream.SAMPLE_RATE
        private const val BLOCK_SAMPLES = SAMPLE_RATE / 10 // bloques de 100 ms
        private const val WARMUP_SAMPLES = SAMPLE_RATE / 2  // descarta 500 ms al arrancar el micrófono
        private const val MIN_DB = 35.0
        private const val MAX_DB = 110.0
    }
}
