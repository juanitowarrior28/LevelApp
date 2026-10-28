package com.levelapp.core.tests

import com.levelapp.core.dosimetry.DoseCalculator
import com.levelapp.core.dosimetry.RegulatoryProfile
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class NioshDoseCalculatorTest {
    private val profile = RegulatoryProfile.nioshDefault()

    @Test
    fun `niosh 85 dba for 8 hours equals 100 percent dose`() {
        val dose = DoseCalculator.dosePercent(85.0, 8.0, profile)
        assertTrue(abs(dose - 100.0) < 0.5)
    }

    @Test
    fun `niosh 88 dba for 4 hours equals 100 percent dose`() {
        val dose = DoseCalculator.dosePercent(88.0, 4.0, profile)
        assertTrue(abs(dose - 100.0) < 0.5)
    }

    @Test
    fun `niosh 91 dba for 2 hours equals 100 percent dose`() {
        val dose = DoseCalculator.dosePercent(91.0, 2.0, profile)
        assertTrue(abs(dose - 100.0) < 0.5)
    }

    @Test
    fun `twa equals criterion for 100 percent dose`() {
        val twa = DoseCalculator.twa(85.0, 8.0, profile)
        assertTrue(abs(twa - 85.0) < 0.5)
    }
}
