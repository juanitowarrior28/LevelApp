package com.levelapp.core.audio

import kotlin.math.log10
import kotlin.math.pow

object Calibration {
    fun digitalRms(samples: DoubleArray): Double {
        require(samples.isNotEmpty()) { "Samples must not be empty" }
        val meanSquare = samples.map { it * it }.average()
        return kotlin.math.sqrt(meanSquare)
    }

    fun splFromDigital(samples: DoubleArray, referenceRms: Double = 1.0, offsetDb: Double = 0.0): Double {
        require(referenceRms > 0.0) { "referenceRms must be > 0" }
        val rms = digitalRms(samples)
        return 20.0 * log10(rms / referenceRms) + offsetDb
    }

    fun applyCalibration(samples: DoubleArray, gainLinear: Double = 1.0, offsetDb: Double = 0.0): DoubleArray {
        val gain = gainLinear.coerceAtLeast(1e-12)
        return samples.map { sample ->
            val linear = sample * gain
            val calibrated = linear * (10.0.pow(offsetDb / 20.0))
            calibrated
        }.toDoubleArray()
    }

    fun validateCalibrationStatus(iscalibrated: Boolean, metrologyValid: Boolean): CalibrationStatus =
        when {
            !iscalibrated -> CalibrationStatus.NOT_CALIBRATED
            !metrologyValid -> CalibrationStatus.CALIBRATED_UNVALIDATED
            else -> CalibrationStatus.VALIDATED
        }
}

enum class CalibrationStatus {
    NOT_CALIBRATED,
    CALIBRATED_UNVALIDATED,
    VALIDATED
}
