package com.levelapp.core.dosimetry

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow

/**
 * Intervalo temporal con integración energética exacta (∑ 10^(L/10) · dt).
 * Permite compactar la pirámide temporal en RAM y agrupar ventanas en CSV sin
 * introducir sesgos de promedio aritmético en dB.
 */
data class AcousticTimeSlice(
    val startEpochMs: Long,
    val elapsedStartSeconds: Double,
    val durationSeconds: Double,
    val energySumSeconds: Double,
    val minDbA: Double,
    val maxDbA: Double,
    val doseNioshInc: Double,
    val doseOshaPelInc: Double,
    val doseOshaHcInc: Double,
    val doseIsoInc: Double,
    val doseColRes1792Inc: Double,
) {
    fun mergeWith(next: AcousticTimeSlice): AcousticTimeSlice = AcousticTimeSlice(
        startEpochMs = startEpochMs,
        elapsedStartSeconds = elapsedStartSeconds,
        durationSeconds = durationSeconds + next.durationSeconds,
        energySumSeconds = energySumSeconds + next.energySumSeconds,
        minDbA = minOf(minDbA, next.minDbA),
        maxDbA = maxOf(maxDbA, next.maxDbA),
        doseNioshInc = doseNioshInc + next.doseNioshInc,
        doseOshaPelInc = doseOshaPelInc + next.doseOshaPelInc,
        doseOshaHcInc = doseOshaHcInc + next.doseOshaHcInc,
        doseIsoInc = doseIsoInc + next.doseIsoInc,
        doseColRes1792Inc = doseColRes1792Inc + next.doseColRes1792Inc,
    )
}

data class SessionSnapshot(
    val liveDbA: Double = 0.0,
    val displayDbA: Double = 0.0,
    val laeq: Double = 0.0,
    val lex8h: Double = Double.NEGATIVE_INFINITY,
    val sel: Double = 0.0,
    val soundExposurePa2h: Double = 0.0,
    val lasMin: Double = 0.0,
    val lasMax: Double = 0.0,
    val l10: Double = 0.0,
    val l50: Double = 0.0,
    val l90: Double = 0.0,
    val twa: Double = Double.NEGATIVE_INFINITY,
    val dose: Double = 0.0,
    val doseNiosh: Double = 0.0,
    val doseOsha: Double = 0.0,
    val doseOshaHc: Double = 0.0,
    val doseIso: Double = 0.0,
    val doseColRes1792: Double = 0.0,
    val projectedDose8h: Double = 0.0,
    val measuredSeconds: Double = 0.0,
    val belowFloorSeconds: Double = 0.0,
    val saturationSeconds: Double = 0.0,
    val pausedSeconds: Double = 0.0,
    val hasPauses: Boolean = false,
    val resumedNote: String = "",
    val status: String = "Idle",
    val profileId: String = "niosh",
    val calibrationOffset: Double = 0.0,
    val startTimeMs: Long = 0L,
    val endedAtMs: Long = 0L,
)

data class CsvSlicePoint(
    val timestampEpochMs: Long,
    val elapsedSeconds: Double,
    val durationSeconds: Double,
    val sliceLaeq: Double,
    val sliceLasMin: Double,
    val sliceLasMax: Double,
    val sliceSel: Double,
    val cumLaeq: Double,
    val cumLex8h: Double,
    val cumSel: Double,
    val cumTwa: Double,
    val cumDoseActive: Double,
    val cumDoseNiosh: Double,
    val cumDoseOshaPel: Double,
    val cumDoseOshaHc: Double,
    val cumDoseIso: Double,
    val cumDoseColRes1792: Double,
)

/**
 * Motor puro de sesión de dosimetría acústica:
 * - Integra energía, dosis, histograma y serie temporal con el nivel REAL sin recortar (sin clamp).
 * - Recorta únicamente `displayDbA` a [35.0, 110.0] dB(A) para visualización.
 * - Contabiliza tiempo por debajo de 35.0 dB(A) y por encima de 110.0 dB(A).
 * - Separa L_EX,8h (energético sin umbral) del TWA (por perfil desde su dosis).
 * - Calcula percentiles L10/L50/L90 sobre rango [20.0, 140.0] dB(A) usando centro de bin.
 * - Serializa todo el estado (incluidos histograma y timeSlices) en checkpoint binario atómico.
 */
class DosimetrySessionEngine {
    companion object {
        const val MIN_RELIABLE_DB = 35.0
        const val MAX_RELIABLE_DB = 110.0
        const val HIST_MIN_DB = 20.0
        const val HIST_MAX_DB = 140.0
        const val HIST_STEP_DB = 0.1
        const val HIST_BINS = 1201 // cubre [20.0, 140.0] dBA en pasos de 0.1 dB
        const val MAX_TIME_SLICES = 1800

        private const val CHECKPOINT_MAGIC = 0x4C564150 // "LVAP"
        private const val CHECKPOINT_VERSION = 2

        val SELECTABLE_PROFILE_IDS = setOf("niosh", "osha", "col_res1792")
    }

    var status: String = "Idle"
        private set
    var profileId: String = "niosh"
        private set
    var calibrationOffset: Double = 0.0
        private set
    var startTimeMs: Long = 0L
        private set
    var endedAtMs: Long = 0L
        private set
    private var lastPausedAtMs: Long = 0L

    var measuredSeconds: Double = 0.0
        private set
    var belowFloorSeconds: Double = 0.0
        private set
    var saturationSeconds: Double = 0.0
        private set
    var pausedSeconds: Double = 0.0
        private set
    var hasPauses: Boolean = false
        private set
    var resumedNote: String = ""
        private set

    var sumEnergySeconds: Double = 0.0
        private set
    private var liveDbA: Double = 0.0
    private var lasMin: Double = Double.POSITIVE_INFINITY
    private var lasMax: Double = Double.NEGATIVE_INFINITY
    private var slowBlockEnergyMean: Double? = null

    private var doseNiosh: Double = 0.0
    private var doseOshaPel: Double = 0.0
    private var doseOshaHc: Double = 0.0
    private var doseIso: Double = 0.0
    private var doseColRes1792: Double = 0.0

    private val histogramSeconds = DoubleArray(HIST_BINS)
    private val timeSlices = ArrayList<AcousticTimeSlice>(MAX_TIME_SLICES + 2)
    private var sliceTargetSeconds: Double = 1.0

    private var currentSliceStartEpochMs: Long = 0L
    private var currentSliceElapsedStart: Double = 0.0
    private var currentSliceDuration: Double = 0.0
    private var currentSliceEnergy: Double = 0.0
    private var currentSliceMin: Double = Double.POSITIVE_INFINITY
    private var currentSliceMax: Double = Double.NEGATIVE_INFINITY
    private var currentSliceDoseNiosh: Double = 0.0
    private var currentSliceDoseOshaPel: Double = 0.0
    private var currentSliceDoseOshaHc: Double = 0.0
    private var currentSliceDoseIso: Double = 0.0
    private var currentSliceDoseCol: Double = 0.0

    @Synchronized
    fun startSession(nowMs: Long = System.currentTimeMillis()) {
        status = "Running"
        startTimeMs = nowMs
        endedAtMs = nowMs
        lastPausedAtMs = 0L
        measuredSeconds = 0.0
        belowFloorSeconds = 0.0
        saturationSeconds = 0.0
        pausedSeconds = 0.0
        hasPauses = false
        resumedNote = ""
        sumEnergySeconds = 0.0
        liveDbA = 0.0
        lasMin = Double.POSITIVE_INFINITY
        lasMax = Double.NEGATIVE_INFINITY
        slowBlockEnergyMean = null
        doseNiosh = 0.0
        doseOshaPel = 0.0
        doseOshaHc = 0.0
        doseIso = 0.0
        doseColRes1792 = 0.0
        histogramSeconds.fill(0.0)
        timeSlices.clear()
        sliceTargetSeconds = 1.0
        resetCurrentSlice(nowMs, 0.0)
    }

    @Synchronized
    fun stopRecording(nowMs: Long = System.currentTimeMillis()) {
        status = "Stopped"
        endedAtMs = maxOf(endedAtMs, nowMs)
        commitCurrentSliceIfNotEmpty(nowMs)
    }

    @Synchronized
    fun pauseSession(nowMs: Long = System.currentTimeMillis()) {
        if (status != "Running") return
        status = "Paused"
        hasPauses = true
        lastPausedAtMs = nowMs
        endedAtMs = maxOf(endedAtMs, nowMs)
        commitCurrentSliceIfNotEmpty(nowMs)
    }

    @Synchronized
    fun resumeSession(nowMs: Long = System.currentTimeMillis()) {
        if (status != "Paused" && status != "Stopped") return
        if (lastPausedAtMs > 0L && nowMs > lastPausedAtMs) {
            pausedSeconds += (nowMs - lastPausedAtMs) / 1000.0
            hasPauses = true
        } else if (endedAtMs > 0L && nowMs > endedAtMs) {
            pausedSeconds += (nowMs - endedAtMs) / 1000.0
            hasPauses = true
        }
        lastPausedAtMs = 0L
        status = "Running"
        slowBlockEnergyMean = null
        resetCurrentSlice(nowMs, measuredSeconds)
    }

    @Synchronized
    fun markSessionCompleted() {
        status = "Completada"
        commitCurrentSliceIfNotEmpty(endedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis())
    }

    @Synchronized
    fun updateCriterion(newProfileId: String) {
        if (newProfileId in SELECTABLE_PROFILE_IDS || RegulatoryProfile.all.any { it.id == newProfileId }) {
            profileId = newProfileId
        }
    }

    @Synchronized
    fun setCalibrationOffset(offset: Double) {
        if (offset.isFinite()) {
            calibrationOffset = offset
        }
    }

    /**
     * Registra un bloque de audio medido con nivel real sin recortar.
     * Si el servicio provee `slowMinDbA` y `slowMaxDbA` reales (integrador Slow a 48 kHz),
     * los utiliza directamente; de lo contrario aplica integración Slow exponencial (tau = 1 s)
     * sobre la energía del bloque.
     */
    @Synchronized
    fun onAudioBlock(
        rawDbA: Double,
        blockSeconds: Double,
        nowMs: Long = System.currentTimeMillis(),
        slowMinDbA: Double? = null,
        slowMaxDbA: Double? = null,
    ) {
        if (status != "Running" || !rawDbA.isFinite() || blockSeconds <= 0.0) return

        val levelDbA = rawDbA + calibrationOffset
        val blockHours = blockSeconds / 3600.0
        val linearBlockEnergy = 10.0.pow(levelDbA / 10.0)
        val blockEnergySeconds = linearBlockEnergy * blockSeconds

        if (currentSliceDuration == 0.0) {
            currentSliceStartEpochMs = nowMs
            currentSliceElapsedStart = measuredSeconds
        }

        liveDbA = levelDbA
        endedAtMs = maxOf(endedAtMs, nowMs)
        measuredSeconds += blockSeconds
        if (levelDbA < MIN_RELIABLE_DB) belowFloorSeconds += blockSeconds
        if (levelDbA > MAX_RELIABLE_DB) saturationSeconds += blockSeconds

        sumEnergySeconds += blockEnergySeconds

        // Ponderación Slow (tau = 1 s)
        val (blockSlowMin, blockSlowMax) = if (slowMinDbA != null && slowMaxDbA != null &&
            slowMinDbA.isFinite() && slowMaxDbA.isFinite()
        ) {
            (slowMinDbA + calibrationOffset) to (slowMaxDbA + calibrationOffset)
        } else {
            val alpha = 1.0 - exp(-blockSeconds / 1.0)
            val prev = slowBlockEnergyMean
            val next = if (prev == null) linearBlockEnergy else prev + alpha * (linearBlockEnergy - prev)
            slowBlockEnergyMean = next
            val slowDb = 10.0 * log10(next.coerceAtLeast(1e-20))
            slowDb to slowDb
        }

        lasMin = minOf(lasMin, blockSlowMin)
        lasMax = maxOf(lasMax, blockSlowMax)

        // Histograma en [20.0, 140.0] dB(A) sin recorte en 35-110
        val binIndex = ((levelDbA - HIST_MIN_DB) / HIST_STEP_DB).toInt().coerceIn(0, histogramSeconds.lastIndex)
        histogramSeconds[binIndex] += blockSeconds

        val nioshInc = DoseCalculator.dosePercent(levelDbA, blockHours, RegulatoryProfile.nioshDefault())
        val oshaPelInc = DoseCalculator.dosePercent(levelDbA, blockHours, RegulatoryProfile.oshaPelDefault())
        val oshaHcInc = DoseCalculator.dosePercent(levelDbA, blockHours, RegulatoryProfile.oshaHcDefault())
        val isoInc = DoseCalculator.dosePercent(levelDbA, blockHours, RegulatoryProfile.iso9612Default())
        val colInc = DoseCalculator.dosePercent(levelDbA, blockHours, RegulatoryProfile.colombiaRes1792Default())

        doseNiosh += nioshInc
        doseOshaPel += oshaPelInc
        doseOshaHc += oshaHcInc
        doseIso += isoInc
        doseColRes1792 += colInc

        currentSliceDuration += blockSeconds
        currentSliceEnergy += blockEnergySeconds
        currentSliceMin = minOf(currentSliceMin, blockSlowMin)
        currentSliceMax = maxOf(currentSliceMax, blockSlowMax)
        currentSliceDoseNiosh += nioshInc
        currentSliceDoseOshaPel += oshaPelInc
        currentSliceDoseOshaHc += oshaHcInc
        currentSliceDoseIso += isoInc
        currentSliceDoseCol += colInc

        if (currentSliceDuration + 1e-6 >= sliceTargetSeconds) {
            commitCurrentSliceIfNotEmpty(nowMs)
        }
    }

    @Synchronized
    fun snapshot(): SessionSnapshot {
        val laeq = if (measuredSeconds > 0.0 && sumEnergySeconds > 0.0) {
            10.0 * log10((sumEnergySeconds / measuredSeconds).coerceAtLeast(1e-20))
        } else 0.0
        val sel = if (measuredSeconds > 0.0 && sumEnergySeconds > 0.0) {
            10.0 * log10(sumEnergySeconds.coerceAtLeast(1e-20))
        } else 0.0
        val lex8h = DoseCalculator.lex8h(laeq, measuredSeconds)
        val ea = DoseCalculator.soundExposurePa2h(sumEnergySeconds)
        val activeDose = doseFor(profileId)
        val activeProfile = RegulatoryProfile.byId(profileId)
        val activeTwa = if (activeDose >= DoseCalculator.MIN_VALID_TWA_DOSE_PERCENT) {
            DoseCalculator.twaFromDose(activeDose, activeProfile)
        } else {
            Double.NEGATIVE_INFINITY
        }
        val projected8h = DoseCalculator.projectedDose8hFromDose(activeDose, measuredSeconds, activeProfile)
        val displayDb = if (status == "Running" && measuredSeconds > 0.0) {
            liveDbA.coerceIn(MIN_RELIABLE_DB, MAX_RELIABLE_DB)
        } else if (measuredSeconds > 0.0) {
            liveDbA.coerceIn(MIN_RELIABLE_DB, MAX_RELIABLE_DB)
        } else {
            0.0
        }

        val wallSeconds = if (startTimeMs > 0L && endedAtMs >= startTimeMs) {
            (endedAtMs - startTimeMs) / 1000.0
        } else measuredSeconds
        val effectivePaused = if (pausedSeconds > 0.0) pausedSeconds else (wallSeconds - measuredSeconds).coerceAtLeast(0.0)

        return SessionSnapshot(
            liveDbA = liveDbA,
            displayDbA = displayDb,
            laeq = laeq,
            lex8h = lex8h,
            sel = sel,
            soundExposurePa2h = ea,
            lasMin = if (lasMin.isFinite()) lasMin else 0.0,
            lasMax = if (lasMax.isFinite()) lasMax else 0.0,
            l10 = computePercentileExceeded(10.0),
            l50 = computePercentileExceeded(50.0),
            l90 = computePercentileExceeded(90.0),
            twa = activeTwa,
            dose = activeDose,
            doseNiosh = doseNiosh,
            doseOsha = doseOshaPel,
            doseOshaHc = doseOshaHc,
            doseIso = doseIso,
            doseColRes1792 = doseColRes1792,
            projectedDose8h = projected8h,
            measuredSeconds = measuredSeconds,
            belowFloorSeconds = belowFloorSeconds,
            saturationSeconds = saturationSeconds,
            pausedSeconds = effectivePaused,
            hasPauses = hasPauses || (wallSeconds - measuredSeconds >= 2.0),
            resumedNote = resumedNote,
            status = status,
            profileId = profileId,
            calibrationOffset = calibrationOffset,
            startTimeMs = startTimeMs,
            endedAtMs = endedAtMs,
        )
    }

    /**
     * Percentil L_N (nivel superado el N% del tiempo) calculado con el CENTRO del bin.
     */
    @Synchronized
    fun computePercentileExceeded(exceedPercent: Double): Double {
        if (measuredSeconds <= 0.0) return 0.0
        val targetSecondsFromTop = measuredSeconds * (exceedPercent / 100.0)
        var cumulativeFromTop = 0.0
        for (bin in histogramSeconds.indices.reversed()) {
            cumulativeFromTop += histogramSeconds[bin]
            if (cumulativeFromTop >= targetSecondsFromTop) {
                return HIST_MIN_DB + (bin + 0.5) * HIST_STEP_DB
            }
        }
        return HIST_MIN_DB + 0.5 * HIST_STEP_DB
    }

    /**
     * Construye la serie temporal para almacenar/exportar a CSV sin mutar el estado
     * en curso si aún hay un intervalo abierto.
     */
    @Synchronized
    fun buildCsvPoints(targetProfileId: String = profileId): List<CsvSlicePoint> {
        val allSlices = ArrayList<AcousticTimeSlice>(timeSlices.size + 1)
        allSlices.addAll(timeSlices)
        if (currentSliceDuration > 0.0) {
            allSlices.add(
                AcousticTimeSlice(
                    startEpochMs = currentSliceStartEpochMs,
                    elapsedStartSeconds = currentSliceElapsedStart,
                    durationSeconds = currentSliceDuration,
                    energySumSeconds = currentSliceEnergy,
                    minDbA = if (currentSliceMin.isFinite()) currentSliceMin else currentSliceMax,
                    maxDbA = if (currentSliceMax.isFinite()) currentSliceMax else 0.0,
                    doseNioshInc = currentSliceDoseNiosh,
                    doseOshaPelInc = currentSliceDoseOshaPel,
                    doseOshaHcInc = currentSliceDoseOshaHc,
                    doseIsoInc = currentSliceDoseIso,
                    doseColRes1792Inc = currentSliceDoseCol,
                )
            )
        }
        if (allSlices.isEmpty()) return emptyList()

        val totalSec = measuredSeconds
        val desiredWindowSec = when {
            totalSec <= 3 * 60 -> 1.0
            totalSec <= 15 * 60 -> 5.0
            totalSec <= 60 * 60 -> 15.0
            totalSec <= 4 * 3600 -> 30.0
            else -> 60.0
        }

        val groupedSlices = ArrayList<AcousticTimeSlice>()
        var acc: AcousticTimeSlice? = null
        for (slice in allSlices) {
            acc = if (acc == null) slice else acc.mergeWith(slice)
            if (acc.durationSeconds + 1e-6 >= desiredWindowSec) {
                groupedSlices.add(acc)
                acc = null
            }
        }
        if (acc != null) groupedSlices.add(acc)

        val activeProfile = RegulatoryProfile.byId(targetProfileId)
        val result = ArrayList<CsvSlicePoint>(groupedSlices.size)
        var cumDuration = 0.0
        var cumEnergy = 0.0
        var cumNiosh = 0.0
        var cumOshaPel = 0.0
        var cumOshaHc = 0.0
        var cumIso = 0.0
        var cumCol = 0.0

        for (s in groupedSlices) {
            cumDuration += s.durationSeconds
            cumEnergy += s.energySumSeconds
            cumNiosh += s.doseNioshInc
            cumOshaPel += s.doseOshaPelInc
            cumOshaHc += s.doseOshaHcInc
            cumIso += s.doseIsoInc
            cumCol += s.doseColRes1792Inc

            val sliceLaeq = if (s.durationSeconds > 0.0 && s.energySumSeconds > 0.0) {
                10.0 * log10((s.energySumSeconds / s.durationSeconds).coerceAtLeast(1e-20))
            } else 0.0
            val sliceSel = if (s.energySumSeconds > 0.0) 10.0 * log10(s.energySumSeconds.coerceAtLeast(1e-20)) else 0.0
            val cumLaeq = if (cumDuration > 0.0 && cumEnergy > 0.0) {
                10.0 * log10((cumEnergy / cumDuration).coerceAtLeast(1e-20))
            } else 0.0
            val cumSel = if (cumEnergy > 0.0) 10.0 * log10(cumEnergy.coerceAtLeast(1e-20)) else 0.0
            val cumLex8h = DoseCalculator.lex8h(cumLaeq, cumDuration)

            val activeCumDose = when (targetProfileId) {
                "osha", "osha_pel" -> cumOshaPel
                "osha_hc" -> cumOshaHc
                "iso9612" -> cumIso
                "col_res1792" -> cumCol
                else -> cumNiosh
            }
            val cumTwa = if (activeCumDose >= DoseCalculator.MIN_VALID_TWA_DOSE_PERCENT) {
                DoseCalculator.twaFromDose(activeCumDose, activeProfile)
            } else {
                Double.NEGATIVE_INFINITY
            }

            result.add(
                CsvSlicePoint(
                    timestampEpochMs = s.startEpochMs,
                    elapsedSeconds = cumDuration,
                    durationSeconds = s.durationSeconds,
                    sliceLaeq = sliceLaeq,
                    sliceLasMin = s.minDbA,
                    sliceLasMax = s.maxDbA,
                    sliceSel = sliceSel,
                    cumLaeq = cumLaeq,
                    cumLex8h = cumLex8h,
                    cumSel = cumSel,
                    cumTwa = cumTwa,
                    cumDoseActive = activeCumDose,
                    cumDoseNiosh = cumNiosh,
                    cumDoseOshaPel = cumOshaPel,
                    cumDoseOshaHc = cumOshaHc,
                    cumDoseIso = cumIso,
                    cumDoseColRes1792 = cumCol,
                )
            )
        }
        return result
    }

    /**
     * Guarda de forma atómica todo el estado de la sesión (incluidos histograma completo
     * y todos los timeSlices) en un archivo binario.
     */
    @Synchronized
    fun writeCheckpoint(file: File) {
        file.parentFile?.mkdirs()
        val tmpFile = File(file.parentFile, "${file.name}.tmp")
        DataOutputStream(BufferedOutputStream(FileOutputStream(tmpFile))).use { out ->
            out.writeInt(CHECKPOINT_MAGIC)
            out.writeInt(CHECKPOINT_VERSION)
            out.writeUTF(status)
            out.writeUTF(profileId)
            out.writeDouble(calibrationOffset)
            out.writeLong(startTimeMs)
            out.writeLong(endedAtMs)
            out.writeLong(lastPausedAtMs)

            out.writeDouble(measuredSeconds)
            out.writeDouble(belowFloorSeconds)
            out.writeDouble(saturationSeconds)
            out.writeDouble(pausedSeconds)
            out.writeBoolean(hasPauses)
            out.writeUTF(resumedNote)

            out.writeDouble(sumEnergySeconds)
            out.writeDouble(liveDbA)
            out.writeDouble(lasMin)
            out.writeDouble(lasMax)
            val sb = slowBlockEnergyMean
            out.writeBoolean(sb != null)
            out.writeDouble(sb ?: 0.0)

            out.writeDouble(doseNiosh)
            out.writeDouble(doseOshaPel)
            out.writeDouble(doseOshaHc)
            out.writeDouble(doseIso)
            out.writeDouble(doseColRes1792)

            out.writeInt(histogramSeconds.size)
            for (v in histogramSeconds) out.writeDouble(v)

            out.writeDouble(sliceTargetSeconds)
            out.writeLong(currentSliceStartEpochMs)
            out.writeDouble(currentSliceElapsedStart)
            out.writeDouble(currentSliceDuration)
            out.writeDouble(currentSliceEnergy)
            out.writeDouble(currentSliceMin)
            out.writeDouble(currentSliceMax)
            out.writeDouble(currentSliceDoseNiosh)
            out.writeDouble(currentSliceDoseOshaPel)
            out.writeDouble(currentSliceDoseOshaHc)
            out.writeDouble(currentSliceDoseIso)
            out.writeDouble(currentSliceDoseCol)

            out.writeInt(timeSlices.size)
            for (s in timeSlices) {
                out.writeLong(s.startEpochMs)
                out.writeDouble(s.elapsedStartSeconds)
                out.writeDouble(s.durationSeconds)
                out.writeDouble(s.energySumSeconds)
                out.writeDouble(s.minDbA)
                out.writeDouble(s.maxDbA)
                out.writeDouble(s.doseNioshInc)
                out.writeDouble(s.doseOshaPelInc)
                out.writeDouble(s.doseOshaHcInc)
                out.writeDouble(s.doseIsoInc)
                out.writeDouble(s.doseColRes1792Inc)
            }
            out.flush()
        }
        if (file.exists() && !tmpFile.renameTo(file)) {
            file.delete()
            tmpFile.renameTo(file)
        } else {
            tmpFile.renameTo(file)
        }
    }

    /**
     * Restaura todo el estado desde el checkpoint binario.
     * Si la sesión estaba en estado "Running" al morir el proceso, marca
     * `resumedNote` con la hora `HH:MM` de reanudación y `hasPauses = true`.
     */
    @Synchronized
    fun restoreCheckpoint(file: File, resumeClockHHmm: String): Boolean {
        if (!file.exists()) return false
        return try {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
                if (input.readInt() != CHECKPOINT_MAGIC) return false
                if (input.readInt() != CHECKPOINT_VERSION) return false

                val savedStatus = input.readUTF()
                val rawProfileId = input.readUTF()
                profileId = if (rawProfileId in SELECTABLE_PROFILE_IDS) rawProfileId else "niosh"
                calibrationOffset = input.readDouble()
                startTimeMs = input.readLong()
                endedAtMs = input.readLong()
                lastPausedAtMs = input.readLong()

                measuredSeconds = input.readDouble()
                belowFloorSeconds = input.readDouble()
                saturationSeconds = input.readDouble()
                pausedSeconds = input.readDouble()
                hasPauses = input.readBoolean()
                resumedNote = input.readUTF()

                sumEnergySeconds = input.readDouble()
                liveDbA = input.readDouble()
                lasMin = input.readDouble()
                lasMax = input.readDouble()
                val hasSlow = input.readBoolean()
                val slowVal = input.readDouble()
                slowBlockEnergyMean = if (hasSlow) slowVal else null

                doseNiosh = input.readDouble()
                doseOshaPel = input.readDouble()
                doseOshaHc = input.readDouble()
                doseIso = input.readDouble()
                doseColRes1792 = input.readDouble()

                val bins = input.readInt()
                if (bins != histogramSeconds.size) return false
                for (i in 0 until bins) histogramSeconds[i] = input.readDouble()

                sliceTargetSeconds = input.readDouble()
                currentSliceStartEpochMs = input.readLong()
                currentSliceElapsedStart = input.readDouble()
                currentSliceDuration = input.readDouble()
                currentSliceEnergy = input.readDouble()
                currentSliceMin = input.readDouble()
                currentSliceMax = input.readDouble()
                currentSliceDoseNiosh = input.readDouble()
                currentSliceDoseOshaPel = input.readDouble()
                currentSliceDoseOshaHc = input.readDouble()
                currentSliceDoseIso = input.readDouble()
                currentSliceDoseCol = input.readDouble()

                val sliceCount = input.readInt()
                timeSlices.clear()
                for (i in 0 until sliceCount) {
                    timeSlices.add(
                        AcousticTimeSlice(
                            startEpochMs = input.readLong(),
                            elapsedStartSeconds = input.readDouble(),
                            durationSeconds = input.readDouble(),
                            energySumSeconds = input.readDouble(),
                            minDbA = input.readDouble(),
                            maxDbA = input.readDouble(),
                            doseNioshInc = input.readDouble(),
                            doseOshaPelInc = input.readDouble(),
                            doseOshaHcInc = input.readDouble(),
                            doseIsoInc = input.readDouble(),
                            doseColRes1792Inc = input.readDouble(),
                        )
                    )
                }

                status = savedStatus
                if (savedStatus == "Running") {
                    val event = "Sesión reanudada tras cierre del sistema a las $resumeClockHHmm"
                    resumedNote = if (resumedNote.isBlank()) event else "$resumedNote; $event"
                    hasPauses = true
                }
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun doseFor(id: String): Double = when (id) {
        "osha", "osha_pel" -> doseOshaPel
        "osha_hc" -> doseOshaHc
        "iso9612" -> doseIso
        "col_res1792" -> doseColRes1792
        else -> doseNiosh
    }

    private fun resetCurrentSlice(nowMs: Long, elapsedStart: Double) {
        currentSliceStartEpochMs = nowMs
        currentSliceElapsedStart = elapsedStart
        currentSliceDuration = 0.0
        currentSliceEnergy = 0.0
        currentSliceMin = Double.POSITIVE_INFINITY
        currentSliceMax = Double.NEGATIVE_INFINITY
        currentSliceDoseNiosh = 0.0
        currentSliceDoseOshaPel = 0.0
        currentSliceDoseOshaHc = 0.0
        currentSliceDoseIso = 0.0
        currentSliceDoseCol = 0.0
    }

    private fun commitCurrentSliceIfNotEmpty(nowMs: Long) {
        if (currentSliceDuration <= 0.0) return
        timeSlices.add(
            AcousticTimeSlice(
                startEpochMs = currentSliceStartEpochMs,
                elapsedStartSeconds = currentSliceElapsedStart,
                durationSeconds = currentSliceDuration,
                energySumSeconds = currentSliceEnergy,
                minDbA = if (currentSliceMin.isFinite()) currentSliceMin else currentSliceMax,
                maxDbA = if (currentSliceMax.isFinite()) currentSliceMax else 0.0,
                doseNioshInc = currentSliceDoseNiosh,
                doseOshaPelInc = currentSliceDoseOshaPel,
                doseOshaHcInc = currentSliceDoseOshaHc,
                doseIsoInc = currentSliceDoseIso,
                doseColRes1792Inc = currentSliceDoseCol,
            )
        )
        if (timeSlices.size >= MAX_TIME_SLICES) {
            compactTimeSlices()
        }
        resetCurrentSlice(nowMs = nowMs, elapsedStart = measuredSeconds)
    }

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
}
