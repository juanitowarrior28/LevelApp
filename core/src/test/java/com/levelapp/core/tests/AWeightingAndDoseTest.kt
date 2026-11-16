package com.levelapp.core.tests

import com.levelapp.core.audio.AWeightingFilter
import com.levelapp.core.dosimetry.DoseCalculator
import com.levelapp.core.dosimetry.RegulatoryProfile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
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
            if (i >= 48000) { // descartar transitorio
                sumIn += x * x
                sumOut += y * y
            }
        }
        return 20 * log10(sqrt(sumOut) / sqrt(sumIn))
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
    fun `iso 9612 85 dBA for 8 hours equals 100 percent`() {
        val dose = DoseCalculator.dosePercent(85.0, 8.0, RegulatoryProfile.iso9612Default())
        assertTrue(abs(dose - 100.0) < 0.01)
    }

    @Test
    fun `iso 9612 has no threshold so quiet periods still count but tiny`() {
        val dose = DoseCalculator.dosePercent(55.0, 1.0, RegulatoryProfile.iso9612Default())
        assertTrue(dose > 0.0 && dose < 0.02)
    }

    @Test
    fun `8 hours of incremental 100 ms blocks never decreases and matches closed form`() {
        val profiles = RegulatoryProfile.all
        val acc = DoubleArray(profiles.size)
        val blockHours = 0.1 / 3600.0
        val totalBlocks = 8 * 3600 * 10
        for (i in 0 until totalBlocks) {
            // primera media hora ruidosa (92 dB) y luego silencio (55 dB)
            val level = if (i < 30 * 60 * 10) 92.0 else 55.0
            profiles.forEachIndexed { idx, p ->
                val before = acc[idx]
                acc[idx] += DoseCalculator.dosePercent(level, blockHours, p)
                assertTrue(acc[idx] >= before)
            }
        }
        val nioshExpected = DoseCalculator.dosePercent(92.0, 0.5, RegulatoryProfile.nioshDefault())
        assertTrue(abs(acc[0] - nioshExpected) < 0.01, "NIOSH ${acc[0]} vs $nioshExpected")
        assertTrue(acc[2] >= acc[0])
    }
}
