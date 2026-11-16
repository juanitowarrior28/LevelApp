package com.levelapp.app.service

import android.content.Context
import android.content.SharedPreferences
import com.levelapp.app.viewmodel.SessionUiState
import com.levelapp.core.dosimetry.DoseCalculator
import com.levelapp.core.dosimetry.RegulatoryProfile
import com.levelapp.data.database.MeasurementPointEntity
import com.levelapp.data.database.SessionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.log10
import kotlin.math.pow

/**
 * Representa un intervalo de tiempo [startEpochMs, startEpochMs + durationSeconds]
 * con integración energética exacta (10^(L/10)*dt) para que al promediar o compactar
 * rangos en sesiones largas el cálculo de LAeq y SEL/SZL sea 100% estadísticamente exacto.
 */
data class AcousticTimeSlice(
    val startEpochMs: Long,
    val elapsedStartSeconds: Double,
    val durationSeconds: Double,
    val energySumSeconds: Double,
    val minDbA: Double,
    val maxDbA: Double,
    val doseNioshInc: Double,
    val doseOshaInc: Double,
    val doseIsoInc: Double,
) {
    fun mergeWith(next: AcousticTimeSlice): AcousticTimeSlice = AcousticTimeSlice(
        startEpochMs = startEpochMs,
        elapsedStartSeconds = elapsedStartSeconds,
        durationSeconds = durationSeconds + next.durationSeconds,
        energySumSeconds = energySumSeconds + next.energySumSeconds,
        minDbA = minOf(minDbA, next.minDbA),
        maxDbA = maxOf(maxDbA, next.maxDbA),
        doseNioshInc = doseNioshInc + next.doseNioshInc,
        doseOshaInc = doseOshaInc + next.doseOshaInc,
        doseIsoInc = doseIsoInc + next.doseIsoInc,
    )
}

object AudioDataStream {
    const val SAMPLE_RATE = 48000

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState

    private val _history = MutableStateFlow<List<Float>>(emptyList())
    val history: StateFlow<List<Float>> = _history

    private const val HISTORY_SIZE = 400 // 400 bloques x 100 ms = 40 s en la gráfica
    private const val MAX_TIME_SLICES = 1800 // Máximo 1800 filas; compacta x2 automáticamente si supera este límite

    private var prefs: SharedPreferences? = null

    @Volatile
    var serviceAlive: Boolean = false

    @Volatile
    var startTimeMs: Long = 0L
        private set

    @Volatile
    var isRecording: Boolean = false
        private set

    private var measuredSeconds = 0.0
    private var sumEnergySeconds = 0.0 // ∑ 10^(L/10) · Δt
    private var minSessionDbA = Double.POSITIVE_INFINITY
    private val accumulatedDose = HashMap<String, Double>()
    private var lastPersistedMs = 0L
    private val historyBuffer = ArrayDeque<Float>(HISTORY_SIZE + 1)

    // Histograma de 0.1 dB entre 30.0 y 129.9 dBA (1000 bins = 8 KB de RAM fijos para 8h+)
    private val histogramSeconds = DoubleArray(1000)

    // Serie temporal auto-compactable para el CSV
    private val timeSlices = ArrayList<AcousticTimeSlice>(MAX_TIME_SLICES + 2)
    private var sliceTargetSeconds = 1.0 // Inicia muestra a muestra cada 1.0 s; se duplica si pasa de 1800 intervalos
    private var currentSliceStartEpochMs = 0L
    private var currentSliceElapsedStart = 0.0
    private var currentSliceDuration = 0.0
    private var currentSliceEnergy = 0.0
    private var currentSliceMin = Double.POSITIVE_INFINITY
    private var currentSliceMax = 0.0
    private var currentSliceDoseNiosh = 0.0
    private var currentSliceDoseOsha = 0.0
    private var currentSliceDoseIso = 0.0

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences("levelapp_active_session", Context.MODE_PRIVATE)
        prefs = sp
        try {
            restoreFromPrefs(sp)
        } catch (_: Exception) {
            sp.edit().clear().apply()
            _uiState.value = SessionUiState()
        }
    }

    private fun SharedPreferences.getDouble(key: String): Double =
        Double.fromBits(getLong(key, 0.0.toRawBits())).let { if (it.isFinite()) it else 0.0 }

    private fun SharedPreferences.Editor.putDouble(key: String, value: Double) =
        putLong(key, value.toRawBits())

    private fun restoreFromPrefs(sp: SharedPreferences) {
        val savedStatus = sp.getString("status", "Idle") ?: "Idle"
        val profileId = sp.getString("profileId", "niosh") ?: "niosh"
        val calibrationOffset = sp.getDouble("calibrationOffset")

        if (savedStatus == "Running" || savedStatus == "Stopped" || savedStatus == "Paused") {
            startTimeMs = sp.getLong("startTimeMs", System.currentTimeMillis())
            isRecording = savedStatus == "Running"
            measuredSeconds = sp.getDouble("measuredSeconds")
            sumEnergySeconds = sp.getDouble("sumEnergySeconds")
            val restoredMin = sp.getDouble("lasMin")
            minSessionDbA = if (restoredMin > 0.0) restoredMin else Double.POSITIVE_INFINITY
            accumulatedDose.clear()
            RegulatoryProfile.all.forEach { accumulatedDose[it.id] = sp.getDouble("dose_${it.id}") }
            val laeq = if (measuredSeconds > 0.0 && sumEnergySeconds > 0.0) {
                10.0 * log10(sumEnergySeconds / measuredSeconds)
            } else 0.0
            val sel = if (sumEnergySeconds > 0.0) 10.0 * log10(sumEnergySeconds) else 0.0
            _uiState.value = SessionUiState(
                liveDbA = 0.0,
                laeq = laeq,
                sel = sel,
                lasMin = if (minSessionDbA.isFinite()) minSessionDbA else 0.0,
                lasMax = sp.getDouble("lasMax"),
                l10 = sp.getDouble("l10"),
                l50 = sp.getDouble("l50"),
                l90 = sp.getDouble("l90"),
                twa = twaFor(profileId),
                dose = accumulatedDose[profileId] ?: 0.0,
                doseNiosh = accumulatedDose["niosh"] ?: 0.0,
                doseOsha = accumulatedDose["osha"] ?: 0.0,
                doseIso = accumulatedDose["iso9612"] ?: 0.0,
                durationMinutes = measuredSeconds / 60.0,
                status = savedStatus,
                savedSessionId = null,
                profileId = profileId,
                calibrationOffset = calibrationOffset,
            )
        } else {
            _uiState.value = SessionUiState(profileId = profileId, calibrationOffset = calibrationOffset)
        }
    }

    private fun persistState(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPersistedMs < 5000L) return
        lastPersistedMs = now
        val state = _uiState.value
        val editor = prefs?.edit() ?: return
        editor.putString("status", state.status)
        editor.putString("profileId", state.profileId)
        editor.putDouble("calibrationOffset", state.calibrationOffset)
        editor.putLong("startTimeMs", startTimeMs)
        editor.putDouble("measuredSeconds", measuredSeconds)
        editor.putDouble("sumEnergySeconds", sumEnergySeconds)
        editor.putDouble("lasMin", state.lasMin)
        editor.putDouble("lasMax", state.lasMax)
        editor.putDouble("l10", state.l10)
        editor.putDouble("l50", state.l50)
        editor.putDouble("l90", state.l90)
        RegulatoryProfile.all.forEach { editor.putDouble("dose_${it.id}", accumulatedDose[it.id] ?: 0.0) }
        editor.apply()
    }

    private fun twaFor(profileId: String): Double {
        val dose = accumulatedDose[profileId] ?: 0.0
        return DoseCalculator.twaFromDose(dose, RegulatoryProfile.byId(profileId)).let { if (it.isFinite()) it else 0.0 }
    }

    /**
     * Calcula el percentil L_N (nivel superado el N% del tiempo, ej. L10, L50, L90)
     * a partir del histograma temporal de 0.1 dB.
     */
    private fun computePercentileExceeded(exceedPercent: Double): Double {
        if (measuredSeconds <= 0.0) return 0.0
        val targetSecondsFromTop = measuredSeconds * (exceedPercent / 100.0)
        var cumulativeFromTop = 0.0
        for (bin in histogramSeconds.indices.reversed()) {
            cumulativeFromTop += histogramSeconds[bin]
            if (cumulativeFromTop >= targetSecondsFromTop) {
                return 30.0 + bin * 0.1
            }
        }
        return 30.0
    }

    private fun resetCurrentSlice(nowMs: Long, elapsedStart: Double) {
        currentSliceStartEpochMs = nowMs
        currentSliceElapsedStart = elapsedStart
        currentSliceDuration = 0.0
        currentSliceEnergy = 0.0
        currentSliceMin = Double.POSITIVE_INFINITY
        currentSliceMax = 0.0
        currentSliceDoseNiosh = 0.0
        currentSliceDoseOsha = 0.0
        currentSliceDoseIso = 0.0
    }

    private fun commitCurrentSliceIfNotEmpty() {
        if (currentSliceDuration <= 0.0) return
        timeSlices.add(
            AcousticTimeSlice(
                startEpochMs = currentSliceStartEpochMs,
                elapsedStartSeconds = currentSliceElapsedStart,
                durationSeconds = currentSliceDuration,
                energySumSeconds = currentSliceEnergy,
                minDbA = if (currentSliceMin.isFinite()) currentSliceMin else currentSliceMax,
                maxDbA = currentSliceMax,
                doseNioshInc = currentSliceDoseNiosh,
                doseOshaInc = currentSliceDoseOsha,
                doseIsoInc = currentSliceDoseIso,
            )
        )
        if (timeSlices.size >= MAX_TIME_SLICES) {
            compactTimeSlices()
        }
        resetCurrentSlice(
            nowMs = System.currentTimeMillis(),
            elapsedStart = measuredSeconds
        )
    }

    /**
     * Compacta pares contiguos de intervalos (2 -> 1) conservando de forma
     * exacta la energía acústica, los extremos y la dosis de cada sub-rango.
     */
    private fun compactTimeSlices() {
        val compacted = ArrayList<AcousticTimeSlice>(timeSlices.size / 2 + 2)
        var i = 0
        while (i < timeSlices.size) {
            if (i + 1 < timeSlices.size) {
                compacted.add(timeSlices[i].mergeWith(timeSlices[i + 1]))
                i += 2
            } else {
                compacted.add(timeSlices[i])
                i++
            }
        }
        timeSlices.clear()
        timeSlices.addAll(compacted)
        sliceTargetSeconds *= 2.0
    }

    @Synchronized
    fun startSession() {
        val now = System.currentTimeMillis()
        isRecording = true
        startTimeMs = now
        measuredSeconds = 0.0
        sumEnergySeconds = 0.0
        minSessionDbA = Double.POSITIVE_INFINITY
        accumulatedDose.clear()
        histogramSeconds.fill(0.0)
        timeSlices.clear()
        sliceTargetSeconds = 1.0
        resetCurrentSlice(now, 0.0)
        historyBuffer.clear()
        _history.value = emptyList()
        _uiState.value = _uiState.value.copy(
            liveDbA = 0.0,
            laeq = 0.0,
            sel = 0.0,
            lasMin = 0.0,
            lasMax = 0.0,
            l10 = 0.0,
            l50 = 0.0,
            l90 = 0.0,
            twa = 0.0,
            dose = 0.0,
            doseNiosh = 0.0,
            doseOsha = 0.0,
            doseIso = 0.0,
            durationMinutes = 0.0,
            status = "Running",
            savedSessionId = null,
            lastSavedSession = null,
            lastSavedPoints = emptyList(),
        )
        persistState(force = true)
    }

    @Synchronized
    fun stopRecording() {
        isRecording = false
        commitCurrentSliceIfNotEmpty()
        _uiState.value = _uiState.value.copy(status = "Stopped")
        persistState(force = true)
    }

    @Synchronized
    fun pauseSession() {
        isRecording = false
        commitCurrentSliceIfNotEmpty()
        _uiState.value = _uiState.value.copy(status = "Paused")
        persistState(force = true)
    }

    @Synchronized
    fun markSessionCompleted(
        savedSessionId: Long?,
        savedSession: SessionEntity? = null,
        savedPoints: List<MeasurementPointEntity> = emptyList()
    ) {
        isRecording = false
        commitCurrentSliceIfNotEmpty()
        _uiState.value = _uiState.value.copy(
            status = "Completada",
            savedSessionId = savedSessionId,
            lastSavedSession = savedSession,
            lastSavedPoints = savedPoints
        )
        persistState(force = true)
    }

    @Synchronized
    fun updateCriterion(profileId: String) {
        _uiState.value = _uiState.value.copy(
            profileId = profileId,
            dose = accumulatedDose[profileId] ?: 0.0,
            twa = twaFor(profileId),
        )
        persistState(force = true)
    }

    @Synchronized
    fun setCalibrationOffset(offset: Double) {
        _uiState.value = _uiState.value.copy(calibrationOffset = offset)
        persistState(force = true)
    }

    @Synchronized
    fun onAudioBlock(rawDbA: Double, blockSeconds: Double) {
        if (!isRecording || !rawDbA.isFinite() || blockSeconds <= 0.0) return
        val currentState = _uiState.value
        val levelDbA = rawDbA + currentState.calibrationOffset
        val blockHours = blockSeconds / 3600.0
        val blockEnergySeconds = 10.0.pow(levelDbA / 10.0) * blockSeconds

        if (currentSliceDuration == 0.0) {
            currentSliceStartEpochMs = System.currentTimeMillis()
            currentSliceElapsedStart = measuredSeconds
        }

        measuredSeconds += blockSeconds
        sumEnergySeconds += blockEnergySeconds
        minSessionDbA = minOf(minSessionDbA, levelDbA)
        val newLasMax = maxOf(currentState.lasMax, levelDbA)

        val laeq = 10.0 * log10(sumEnergySeconds / measuredSeconds)
        val sel = 10.0 * log10(sumEnergySeconds)

        // Actualizar histograma para percentiles L10, L50, L90
        val binIndex = ((levelDbA - 30.0) * 10.0).toInt().coerceIn(0, histogramSeconds.lastIndex)
        histogramSeconds[binIndex] += blockSeconds

        val nioshInc = DoseCalculator.dosePercent(levelDbA, blockHours, RegulatoryProfile.nioshDefault())
        val oshaInc = DoseCalculator.dosePercent(levelDbA, blockHours, RegulatoryProfile.oshaDefault())
        val isoInc = DoseCalculator.dosePercent(levelDbA, blockHours, RegulatoryProfile.iso9612Default())

        accumulatedDose["niosh"] = (accumulatedDose["niosh"] ?: 0.0) + nioshInc
        accumulatedDose["osha"] = (accumulatedDose["osha"] ?: 0.0) + oshaInc
        accumulatedDose["iso9612"] = (accumulatedDose["iso9612"] ?: 0.0) + isoInc

        // Acumular en el intervalo temporal actual para el CSV
        currentSliceDuration += blockSeconds
        currentSliceEnergy += blockEnergySeconds
        currentSliceMin = minOf(currentSliceMin, levelDbA)
        currentSliceMax = maxOf(currentSliceMax, levelDbA)
        currentSliceDoseNiosh += nioshInc
        currentSliceDoseOsha += oshaInc
        currentSliceDoseIso += isoInc

        if (currentSliceDuration + 1e-6 >= sliceTargetSeconds) {
            commitCurrentSliceIfNotEmpty()
        }

        historyBuffer.addLast(levelDbA.toFloat())
        while (historyBuffer.size > HISTORY_SIZE) historyBuffer.removeFirst()
        _history.value = historyBuffer.toList()

        val l10 = computePercentileExceeded(10.0)
        val l50 = computePercentileExceeded(50.0)
        val l90 = computePercentileExceeded(90.0)

        _uiState.value = currentState.copy(
            liveDbA = levelDbA,
            laeq = laeq,
            sel = sel,
            lasMin = if (minSessionDbA.isFinite()) minSessionDbA else levelDbA,
            lasMax = newLasMax,
            l10 = l10,
            l50 = l50,
            l90 = l90,
            twa = twaFor(currentState.profileId),
            dose = accumulatedDose[currentState.profileId] ?: 0.0,
            doseNiosh = accumulatedDose["niosh"] ?: 0.0,
            doseOsha = accumulatedDose["osha"] ?: 0.0,
            doseIso = accumulatedDose["iso9612"] ?: 0.0,
            durationMinutes = measuredSeconds / 60.0,
            status = "Running",
        )
        persistState(force = false)
    }

    /**
     * Construye la serie temporal completa de la sesión para guardar en Room y exportar a CSV.
     * Si la medición tiene muchos puntos, aplica agrupación adaptativa según la duración total
     * promediando la energía acústica de forma logarítmica exacta.
     */
    @Synchronized
    fun buildMeasurementPoints(sessionId: Long, profileId: String): List<MeasurementPointEntity> {
        commitCurrentSliceIfNotEmpty()
        if (timeSlices.isEmpty()) return emptyList()

        val totalSec = measuredSeconds
        // Definir tamaño de ventana estadístico óptimo según la duración total:
        // - Hasta 3 min: muestra por muestra cada 1 s (ej. 1 min = 60 filas)
        // - 3 a 15 min: rangos de 5 s (36 a 180 filas)
        // - 15 a 60 min: rangos de 15 s (60 a 240 filas)
        // - 1 a 4 horas: rangos de 30 s (120 a 480 filas)
        // - Más de 4 horas (ej. 8 h): rangos de 60 s / 1 min (480 filas en 8 h)
        val desiredWindowSec = when {
            totalSec <= 3 * 60 -> 1.0
            totalSec <= 15 * 60 -> 5.0
            totalSec <= 60 * 60 -> 15.0
            totalSec <= 4 * 3600 -> 30.0
            else -> 60.0
        }

        val groupedSlices = ArrayList<AcousticTimeSlice>()
        var acc: AcousticTimeSlice? = null
        for (slice in timeSlices) {
            acc = if (acc == null) slice else acc.mergeWith(slice)
            if (acc.durationSeconds + 1e-6 >= desiredWindowSec) {
                groupedSlices.add(acc)
                acc = null
            }
        }
        if (acc != null) groupedSlices.add(acc)

        val activeProfile = RegulatoryProfile.byId(profileId)
        val result = ArrayList<MeasurementPointEntity>(groupedSlices.size)
        var cumDuration = 0.0
        var cumEnergy = 0.0
        var cumNiosh = 0.0
        var cumOsha = 0.0
        var cumIso = 0.0

        for (s in groupedSlices) {
            cumDuration += s.durationSeconds
            cumEnergy += s.energySumSeconds
            cumNiosh += s.doseNioshInc
            cumOsha += s.doseOshaInc
            cumIso += s.doseIsoInc

            val sliceLaeq = if (s.durationSeconds > 0.0 && s.energySumSeconds > 0.0) {
                10.0 * log10(s.energySumSeconds / s.durationSeconds)
            } else 0.0
            val sliceSel = if (s.energySumSeconds > 0.0) 10.0 * log10(s.energySumSeconds) else 0.0
            val cumLaeq = if (cumDuration > 0.0 && cumEnergy > 0.0) {
                10.0 * log10(cumEnergy / cumDuration)
            } else 0.0
            val cumSel = if (cumEnergy > 0.0) 10.0 * log10(cumEnergy) else 0.0

            val activeCumDose = when (profileId) {
                "osha" -> cumOsha
                "iso9612" -> cumIso
                else -> cumNiosh
            }
            val cumTwa = DoseCalculator.twaFromDose(activeCumDose, activeProfile).let {
                if (it.isFinite()) it else 0.0
            }

            result.add(
                MeasurementPointEntity(
                    sessionId = sessionId,
                    timestampEpochMs = s.startEpochMs,
                    elapsedSeconds = cumDuration,
                    durationSeconds = s.durationSeconds,
                    dbA = sliceLaeq,
                    lasMin = s.minDbA,
                    lasMax = s.maxDbA,
                    selSlice = sliceSel,
                    laeq = cumLaeq,
                    selCumulative = cumSel,
                    twa = cumTwa,
                    dosePercent = activeCumDose,
                    doseNiosh = cumNiosh,
                    doseOsha = cumOsha,
                    doseIso = cumIso,
                )
            )
        }
        return result
    }

    @Synchronized
    fun flush() = persistState(force = true)
}
