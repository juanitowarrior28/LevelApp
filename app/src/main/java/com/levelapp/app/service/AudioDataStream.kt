package com.levelapp.app.service

import android.content.Context
import com.levelapp.app.viewmodel.SessionUiState
import com.levelapp.core.dosimetry.DosimetrySessionEngine
import com.levelapp.core.dosimetry.SessionSnapshot
import com.levelapp.data.database.MeasurementPointEntity
import com.levelapp.data.database.SessionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Puerta de enlace singleton a nivel de proceso hacia `DosimetrySessionEngine`.
 * Persiste todo el estado completo (incluidos histograma y timeSlices) en un archivo
 * binario atómico dentro de `filesDir` para garantizar recuperación fiel tras un cierre
 * del sistema en jornadas de 8+ horas.
 */
object AudioDataStream {
    const val SAMPLE_RATE = 48000
    private const val HISTORY_SIZE = 400

    private val engine = DosimetrySessionEngine()
    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState

    private val _history = MutableStateFlow<List<Float>>(emptyList())
    val history: StateFlow<List<Float>> = _history

    private var checkpointFile: File? = null
    private var lastPersistedMs = 0L
    private val historyBuffer = ArrayDeque<Float>(HISTORY_SIZE + 1)

    @Volatile
    var serviceAlive: Boolean = false

    val startTimeMs: Long
        get() = engine.startTimeMs

    val isRecording: Boolean
        get() = engine.status == "Running"

    @Synchronized
    fun init(context: Context) {
        if (checkpointFile != null) return
        val file = File(context.applicationContext.filesDir, "active_session_checkpoint.bin")
        checkpointFile = file
        val clockHHmm = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        if (engine.restoreCheckpoint(file, clockHHmm)) {
            publishSnapshot(savedSessionId = null, savedSession = null, savedPoints = emptyList())
            if (engine.status == "Running") {
                persistState(force = true)
            }
        } else {
            publishSnapshot(savedSessionId = null, savedSession = null, savedPoints = emptyList())
        }
    }

    @Synchronized
    fun currentSnapshot(): SessionSnapshot = engine.snapshot()

    private fun publishSnapshot(
        savedSessionId: Long? = _uiState.value.savedSessionId,
        savedSession: SessionEntity? = _uiState.value.lastSavedSession,
        savedPoints: List<MeasurementPointEntity> = _uiState.value.lastSavedPoints,
    ) {
        val snap = engine.snapshot()
        _uiState.value = SessionUiState(
            liveDbA = snap.liveDbA,
            displayDbA = snap.displayDbA,
            laeq = snap.laeq,
            lex8h = snap.lex8h,
            sel = snap.sel,
            soundExposurePa2h = snap.soundExposurePa2h,
            lasMin = snap.lasMin,
            lasMax = snap.lasMax,
            l10 = snap.l10,
            l50 = snap.l50,
            l90 = snap.l90,
            twa = snap.twa,
            dose = snap.dose,
            doseNiosh = snap.doseNiosh,
            doseOsha = snap.doseOsha,
            doseOshaHc = snap.doseOshaHc,
            doseIso = snap.doseIso,
            doseColRes1792 = snap.doseColRes1792,
            projectedDose8h = snap.projectedDose8h,
            durationMinutes = snap.measuredSeconds / 60.0,
            measuredSeconds = snap.measuredSeconds,
            belowFloorSeconds = snap.belowFloorSeconds,
            saturationSeconds = snap.saturationSeconds,
            pausedSeconds = snap.pausedSeconds,
            hasPauses = snap.hasPauses,
            resumedNote = snap.resumedNote,
            status = snap.status,
            savedSessionId = savedSessionId,
            lastSavedSession = savedSession,
            lastSavedPoints = savedPoints,
            profileId = snap.profileId,
            calibrationOffset = snap.calibrationOffset,
        )
    }

    private fun persistState(force: Boolean) {
        val file = checkpointFile ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastPersistedMs < 5000L) return
        lastPersistedMs = now
        try {
            engine.writeCheckpoint(file)
        } catch (_: Exception) { }
    }

    @Synchronized
    fun startSession() {
        engine.startSession(System.currentTimeMillis())
        historyBuffer.clear()
        _history.value = emptyList()
        publishSnapshot(savedSessionId = null, savedSession = null, savedPoints = emptyList())
        persistState(force = true)
    }

    @Synchronized
    fun stopRecording() {
        engine.stopRecording(System.currentTimeMillis())
        publishSnapshot()
        persistState(force = true)
    }

    @Synchronized
    fun pauseSession() {
        engine.pauseSession(System.currentTimeMillis())
        publishSnapshot()
        persistState(force = true)
    }

    @Synchronized
    fun resumeSession() {
        engine.resumeSession(System.currentTimeMillis())
        publishSnapshot()
        persistState(force = true)
    }

    @Synchronized
    fun markSessionCompleted(
        savedSessionId: Long?,
        savedSession: SessionEntity? = null,
        savedPoints: List<MeasurementPointEntity> = emptyList()
    ) {
        engine.markSessionCompleted()
        publishSnapshot(
            savedSessionId = savedSessionId,
            savedSession = savedSession,
            savedPoints = savedPoints
        )
        persistState(force = true)
    }

    @Synchronized
    fun updateCriterion(profileId: String) {
        engine.updateCriterion(profileId)
        publishSnapshot()
        persistState(force = true)
    }

    @Synchronized
    fun setCalibrationOffset(offset: Double) {
        engine.setCalibrationOffset(offset)
        publishSnapshot()
        persistState(force = true)
    }

    @Synchronized
    fun onAudioBlock(
        rawDbA: Double,
        blockSeconds: Double,
        slowMinDbA: Double? = null,
        slowMaxDbA: Double? = null,
    ) {
        if (!isRecording || !rawDbA.isFinite() || blockSeconds <= 0.0) return
        engine.onAudioBlock(
            rawDbA = rawDbA,
            blockSeconds = blockSeconds,
            nowMs = System.currentTimeMillis(),
            slowMinDbA = slowMinDbA,
            slowMaxDbA = slowMaxDbA,
        )
        val snap = engine.snapshot()
        // El recorte 35-110 dB(A) aplica exclusivamente a la representación visual en gráfica
        historyBuffer.addLast(snap.displayDbA.toFloat())
        while (historyBuffer.size > HISTORY_SIZE) historyBuffer.removeFirst()
        _history.value = historyBuffer.toList()

        publishSnapshot()
        persistState(force = false)
    }

    @Synchronized
    fun buildMeasurementPoints(sessionId: Long, profileId: String): List<MeasurementPointEntity> {
        return engine.buildCsvPoints(profileId).map { pt ->
            MeasurementPointEntity(
                sessionId = sessionId,
                timestampEpochMs = pt.timestampEpochMs,
                elapsedSeconds = pt.elapsedSeconds,
                durationSeconds = pt.durationSeconds,
                dbA = pt.sliceLaeq,
                lasMin = pt.sliceLasMin,
                lasMax = pt.sliceLasMax,
                selSlice = pt.sliceSel,
                laeq = pt.cumLaeq,
                lex8h = pt.cumLex8h,
                selCumulative = pt.cumSel,
                twa = pt.cumTwa,
                dosePercent = pt.cumDoseActive,
                doseNiosh = pt.cumDoseNiosh,
                doseOsha = pt.cumDoseOshaPel,
                doseOshaHc = pt.cumDoseOshaHc,
                doseIso = pt.cumDoseIso,
                doseColRes1792 = pt.cumDoseColRes1792,
            )
        }
    }

    @Synchronized
    fun flush() = persistState(force = true)
}
