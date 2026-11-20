package com.levelapp.core.tests

import com.levelapp.core.audio.AWeightingFilter
import com.levelapp.core.dosimetry.DoseCalculator
import com.levelapp.core.dosimetry.DosimetrySessionEngine
import com.levelapp.core.dosimetry.RegulatoryProfile
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AWeightingAndDoseTest {

    private fun gainDb(freq: Double): Double {
        val filter = AWeightingFilter(48000)
        val n = 48000 * 2
        var sumIn = 0.0
        var sumOut = 0.0
        for (i in 0 until n) {
            val x = sin(2 * PI * freq * i / 48000.0)
            val y = filter.processSample(x)
            if (i >= 48000) {
                sumIn += x * x
                sumOut += y * y
            }
        }
        return 20 * log10(sqrt(sumOut) / sqrt(sumIn))
    }

    private fun feedConstantLevel(
        engine: DosimetrySessionEngine,
        levelDbA: Double,
        totalSeconds: Double,
        blockSeconds: Double = 0.5,
        startMs: Long = 1_700_000_000_000L
    ) {
        val steps = (totalSeconds / blockSeconds).toInt()
        for (i in 0 until steps) {
            engine.onAudioBlock(levelDbA, blockSeconds, nowMs = startMs + (i * blockSeconds * 1000.0).toLong())
        }
        val remainder = totalSeconds - steps * blockSeconds
        if (remainder > 1e-6) {
            engine.onAudioBlock(levelDbA, remainder, nowMs = startMs + (totalSeconds * 1000.0).toLong())
        }
    }

    @Test
    fun `a weighting matches IEC 61672 reference values`() {
        assertTrue(abs(gainDb(1000.0)) < 0.3, "1 kHz debe ser ~0 dB, fue ${gainDb(1000.0)}")
        assertTrue(abs(gainDb(100.0) - (-19.1)) < 0.6, "100 Hz debe ser ~-19.1 dB, fue ${gainDb(100.0)}")
        assertTrue(abs(gainDb(4000.0) - 1.0) < 0.5, "4 kHz debe ser ~+1.0 dB, fue ${gainDb(4000.0)}")
    }

    @Test
    fun `a weighting filter is stable on silence and impulses (no startup spike)`() {
        val filter = AWeightingFilter(48000)
        var maxAbs = 0.0
        for (i in 0 until 48000 * 10) {
            val x = if (i == 0) 32767.0 else 0.0
            val y = filter.processSample(x)
            assertTrue(y.isFinite())
            if (i > 48000) maxAbs = maxOf(maxAbs, abs(y))
        }
        assertTrue(maxAbs < 1e-3, "La respuesta al impulso debe decaer, max=$maxAbs")
    }

    @Test
    fun `85 dBA during 8h matches all 5 profiles plus LEX8h and EA`() {
        val engine = DosimetrySessionEngine()
        engine.startSession(1_700_000_000_000L)
        feedConstantLevel(engine, 85.0, 8.0 * 3600.0, blockSeconds = 1.0)
        val snap = engine.snapshot()

        assertTrue(abs(snap.doseNiosh - 100.0) < 0.5, "NIOSH=${snap.doseNiosh}")
        assertTrue(abs(snap.doseColRes1792 - 100.0) < 0.5, "COLOMBIA=${snap.doseColRes1792}")
        assertTrue(abs(snap.doseIso - 100.0) < 0.5, "ISO 9612=${snap.doseIso}")
        assertTrue(abs(snap.doseOshaHc - 50.0) < 0.5, "OSHA HC=${snap.doseOshaHc}")
        assertEquals(0.0, snap.doseOsha, 1e-6) // 85 < umbral 90 dBA
        assertTrue(abs(snap.lex8h - 85.0) < 0.1, "L_EX,8h=${snap.lex8h}")
        assertTrue(abs(snap.soundExposurePa2h - 1.01) < 0.02, "E_A=${snap.soundExposurePa2h}")
    }

    @Test
    fun `94 dBA during 1h yields NIOSH 100pct COLOMBIA 43_5pct and LEX8h 85`() {
        val engine = DosimetrySessionEngine()
        engine.startSession(1_700_000_000_000L)
        feedConstantLevel(engine, 94.0, 3600.0, blockSeconds = 0.5)
        val snap = engine.snapshot()

        assertTrue(abs(snap.doseNiosh - 100.0) < 0.5, "NIOSH=${snap.doseNiosh}")
        assertTrue(abs(snap.doseColRes1792 - 43.5) < 0.5, "COLOMBIA=${snap.doseColRes1792}")
        assertTrue(abs(snap.lex8h - 85.0) < 0.1, "L_EX,8h=${snap.lex8h}")
    }

    @Test
    fun `52 dBA during 4_23h yields threshold message for TWA and valid ISO LEX8h and EA`() {
        val engine = DosimetrySessionEngine()
        engine.startSession(1_700_000_000_000L)
        feedConstantLevel(engine, 52.0, 4.23 * 3600.0, blockSeconds = 1.0)
        val snap = engine.snapshot()

        assertEquals(0.0, snap.doseNiosh, 1e-9)
        assertEquals(0.0, snap.doseOsha, 1e-9)
        assertEquals(0.0, snap.doseOshaHc, 1e-9)
        assertEquals(0.0, snap.doseColRes1792, 1e-9)
        assertTrue(DoseCalculator.formatTwa(snap.doseNiosh, RegulatoryProfile.nioshDefault()).contains("Bajo umbral"))
        assertTrue(DoseCalculator.formatTwa(snap.doseOsha, RegulatoryProfile.oshaPelDefault()).contains("Bajo umbral"))
        assertTrue(DoseCalculator.formatTwa(snap.doseColRes1792, RegulatoryProfile.colombiaRes1792Default()).contains("Bajo umbral"))

        assertTrue(abs(snap.doseIso - 0.026) < 0.005, "ISO 9612=${snap.doseIso}")
        assertTrue(abs(snap.lex8h - 49.2) < 0.1, "L_EX,8h=${snap.lex8h}")
        assertTrue(abs(snap.soundExposurePa2h - 2.68e-4) < 0.05e-4, "E_A=${snap.soundExposurePa2h}")
        assertEquals("2.68 × 10⁻⁴ Pa²·h", DoseCalculator.formatSoundExposure(snap.soundExposurePa2h))
    }

    @Test
    fun `120 dBA during 1 min integrates as 120 without 110 clamp and counts 1 min saturation`() {
        val engine = DosimetrySessionEngine()
        engine.startSession(1_700_000_000_000L)
        feedConstantLevel(engine, 120.0, 60.0, blockSeconds = 0.1)
        val snap = engine.snapshot()

        assertTrue(abs(snap.laeq - 120.0) < 0.1, "LAeq=${snap.laeq}")
        assertEquals(110.0, snap.displayDbA, 1e-6) // recorte solo para UI
        val expectedNiosh = DoseCalculator.dosePercent(120.0, 1.0 / 60.0, RegulatoryProfile.nioshDefault())
        assertTrue(abs(snap.doseNiosh - expectedNiosh) < 0.5, "NIOSH=${snap.doseNiosh} vs $expectedNiosh")
        assertTrue(abs(snap.saturationSeconds - 60.0) < 0.2, "saturationSeconds=${snap.saturationSeconds}")
        assertEquals(0.0, snap.belowFloorSeconds, 1e-6)
    }

    @Test
    fun `process kill and checkpoint restore preserves percentiles and CSV row count identically`() {
        val startMs = 1_700_000_000_000L
        val pattern = doubleArrayOf(30.0, 48.5, 62.0, 79.5, 86.2, 93.4, 114.0, 70.0, 55.3, 88.8)

        // Sesión A: sin interrupciones (1200 bloques de 0.1 s = 120 s)
        val uninterrupted = DosimetrySessionEngine()
        uninterrupted.startSession(startMs)
        for (i in 0 until 1200) {
            val lvl = pattern[i % pattern.size]
            uninterrupted.onAudioBlock(lvl, 0.1, nowMs = startMs + i * 100L)
        }
        uninterrupted.stopRecording(startMs + 120_000L)

        // Sesión B -> checkpoint en bloque 573 (a mitad de un intervalo de 1 s) -> restaurar en Sesión C
        val tempCheckpoint = File.createTempFile("levelapp_ckpt_", ".bin")
        try {
            val firstHalf = DosimetrySessionEngine()
            firstHalf.startSession(startMs)
            for (i in 0 until 573) {
                val lvl = pattern[i % pattern.size]
                firstHalf.onAudioBlock(lvl, 0.1, nowMs = startMs + i * 100L)
            }
            firstHalf.writeCheckpoint(tempCheckpoint)

            val restored = DosimetrySessionEngine()
            val ok = restored.restoreCheckpoint(tempCheckpoint, "14:32")
            assertTrue(ok)
            assertTrue(restored.resumedNote.contains("Sesión reanudada tras cierre del sistema a las 14:32"))

            for (i in 573 until 1200) {
                val lvl = pattern[i % pattern.size]
                restored.onAudioBlock(lvl, 0.1, nowMs = startMs + i * 100L)
            }
            restored.stopRecording(startMs + 120_000L)

            val snapA = uninterrupted.snapshot()
            val snapC = restored.snapshot()
            assertEquals(snapA.l10, snapC.l10, 1e-9)
            assertEquals(snapA.l50, snapC.l50, 1e-9)
            assertEquals(snapA.l90, snapC.l90, 1e-9)
            assertEquals(snapA.laeq, snapC.laeq, 1e-9)
            assertEquals(snapA.lex8h, snapC.lex8h, 1e-9)
            assertEquals(snapA.soundExposurePa2h, snapC.soundExposurePa2h, 1e-12)
            assertEquals(snapA.doseNiosh, snapC.doseNiosh, 1e-9)
            assertEquals(snapA.belowFloorSeconds, snapC.belowFloorSeconds, 1e-9)
            assertEquals(snapA.saturationSeconds, snapC.saturationSeconds, 1e-9)

            val csvA = uninterrupted.buildCsvPoints("niosh")
            val csvC = restored.buildCsvPoints("niosh")
            assertEquals(csvA.size, csvC.size)
            assertTrue(csvA.size >= 100)
        } finally {
            tempCheckpoint.delete()
        }
    }

    @Test
    fun `percentile uses bin center across wide 20 to 140 dBA range`() {
        val engine = DosimetrySessionEngine()
        engine.startSession(1_700_000_000_000L)
        feedConstantLevel(engine, 25.0, 10.0, blockSeconds = 0.5)
        val snapLow = engine.snapshot()
        assertTrue(abs(snapLow.l50 - 25.05) < 0.01, "L50 low=${snapLow.l50}")
        assertTrue(abs(snapLow.belowFloorSeconds - 10.0) < 1e-6)
    }

    @Test
    fun `remaining time at 85 and 88 dBA under NIOSH matches expected hours`() {
        val niosh = RegulatoryProfile.nioshDefault()
        val rem85 = DoseCalculator.remainingTimeHours(0.0, 85.0, niosh)
        val rem88 = DoseCalculator.remainingTimeHours(50.0, 88.0, niosh)
        assertEquals(8.0, rem85!!, 1e-6)
        assertEquals(2.0, rem88!!, 1e-6)
        assertEquals("08:00 h", DoseCalculator.formatRemainingTimeHoursMinutes(rem85))
        assertEquals("02:00 h", DoseCalculator.formatRemainingTimeHoursMinutes(rem88))
        assertEquals(null, DoseCalculator.remainingTimeHours(10.0, 75.0, niosh))
    }

    @Test
    fun `8 hours of incremental 100 ms blocks never decreases and matches closed form`() {
        val profiles = RegulatoryProfile.all
        val acc = DoubleArray(profiles.size)
        val blockHours = 0.1 / 3600.0
        val totalBlocks = 8 * 3600 * 10
        for (i in 0 until totalBlocks) {
            val level = if (i < 30 * 60 * 10) 92.0 else 55.0
            profiles.forEachIndexed { idx, p ->
                val before = acc[idx]
                acc[idx] += DoseCalculator.dosePercent(level, blockHours, p)
                assertTrue(acc[idx] >= before)
            }
        }
        val nioshExpected = DoseCalculator.dosePercent(92.0, 0.5, RegulatoryProfile.nioshDefault())
        assertTrue(abs(acc[0] - nioshExpected) < 0.01, "NIOSH ${acc[0]} vs $nioshExpected")
    }
}
