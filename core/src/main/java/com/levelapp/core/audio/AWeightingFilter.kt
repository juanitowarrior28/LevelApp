package com.levelapp.core.audio

class AWeightingFilter(private val sampleRate: Int) {

    private data class Biquad(
        val b0: Double,
        val b1: Double,
        val b2: Double,
        val a1: Double,
        val a2: Double,
    )

    private val sections = listOf(
        Biquad(1.0, -2.0, 1.0, -1.955, 0.956),
        Biquad(1.0, -2.0, 1.0, -1.669, 0.705),
        Biquad(1.0, -2.0, 1.0, -1.278, 0.417),
    )

    fun process(samples: DoubleArray): DoubleArray {
        val output = DoubleArray(samples.size)
        val x = DoubleArray(2)
        val y = DoubleArray(2)
        for (i in samples.indices) {
            var value = samples[i]
            for (section in sections) {
                val x0 = value
                val y0 = section.b0 * x0 + section.b1 * x[0] + section.b2 * x[1] - section.a1 * y[0] - section.a2 * y[1]
                x[1] = x[0]
                x[0] = x0
                y[1] = y[0]
                y[0] = y0
                value = y0
            }
            output[i] = value
        }
        return output
    }

    fun aWeightedDb(samples: DoubleArray): Double {
        val filtered = process(samples)
        val rms = Calibration.digitalRms(filtered)
        return if (rms > 0.0) 20.0 * kotlin.math.log10(rms) else Double.NEGATIVE_INFINITY
    }
}
