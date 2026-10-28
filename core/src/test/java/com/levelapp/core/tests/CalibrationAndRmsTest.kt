package com.levelapp.core.tests

import com.levelapp.core.audio.Calibration
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class CalibrationAndRmsTest {
    @Test
    fun `rms of 1 0 1 is sqrt of mean square`() {
        val samples = doubleArrayOf(1.0, 0.0, -1.0)
        val rms = Calibration.digitalRms(samples)
        assertTrue(abs(rms - 0.81649658) < 1e-6)
    }

    @Test
    fun `gain calibration adjusts level predictably`() {
        val samples = doubleArrayOf(0.5, 0.5, 0.5)
        val calibrated = Calibration.applyCalibration(samples, gainLinear = 2.0, offsetDb = 6.0)
        val rms = Calibration.digitalRms(calibrated)
        assertTrue(rms > 1.0)
    }
}
