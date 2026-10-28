package com.levelapp.core.audio

import kotlin.math.exp
import kotlin.math.log10

class SlowIntegrator(
    private val sampleRate: Int,
    private val timeConstantSeconds: Double = 1.0,
) {
    private val alpha = 1.0 - exp(-1.0 / (sampleRate * timeConstantSeconds))
    private var previous: Double = 0.0
    private var energySum: Double = 0.0
    private var sampleCount: Long = 0

    fun update(linearValue: Double): Double {
        val withDecay = previous + alpha * (linearValue - previous)
        previous = withDecay
        energySum += linearValue * linearValue
        sampleCount += 1
        return withDecay
    }

    fun computeLeq(durationSeconds: Double): Double {
        if (durationSeconds <= 0.0 || sampleCount == 0L) return Double.NEGATIVE_INFINITY
        val rms = kotlin.math.sqrt(energySum / sampleCount.toDouble())
        return 20.0 * log10(rms.coerceAtLeast(1e-12))
    }
}
