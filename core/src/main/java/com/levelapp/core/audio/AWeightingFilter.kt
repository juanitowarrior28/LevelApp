package com.levelapp.core.audio

/**
 * Filtro de ponderación A (IEC 61672) de 6º orden para 48 kHz, obtenido por
 * transformación bilineal del filtro analógico normalizado (0 dB a 1 kHz).
 *
 * Es un filtro con estado (streaming): se debe crear UNA instancia por sesión de
 * captura y alimentarla bloque a bloque. Si por cualquier motivo el estado interno
 * se vuelve NaN/Infinito, se reinicia automáticamente para no contaminar la medición.
 *
 * Nota: el filtro anterior (biquad con polo en z≈1.9) era INESTABLE: su salida
 * crecía exponencialmente hasta desbordar, lo que producía el pico de ~110 dB al
 * iniciar cada medición.
 */
class AWeightingFilter(sampleRate: Int = 48000) {

    init {
        require(sampleRate == 48000) { "AWeightingFilter solo está diseñado para 48 kHz" }
    }

    private val b = doubleArrayOf(
        0.234301792299513, -0.468603584599026, -0.234301792299513,
        0.937207169198054, -0.234301792299515, -0.468603584599025,
        0.234301792299513,
    )
    private val a = doubleArrayOf(
        1.000000000000000, -4.113043408775871, 6.553121752655047,
        -4.990849294163381, 1.785737302937573, -0.246190595319487,
        0.011224250033231,
    )

    // Estado de la forma directa II transpuesta
    private val z = DoubleArray(6)

    fun reset() {
        z.fill(0.0)
    }

    /** Filtra una muestra y devuelve la muestra ponderada A. */
    fun processSample(x: Double): Double {
        val y = b[0] * x + z[0]
        z[0] = b[1] * x - a[1] * y + z[1]
        z[1] = b[2] * x - a[2] * y + z[2]
        z[2] = b[3] * x - a[3] * y + z[3]
        z[3] = b[4] * x - a[4] * y + z[4]
        z[4] = b[5] * x - a[5] * y + z[5]
        z[5] = b[6] * x - a[6] * y
        if (y.isNaN() || y.isInfinite()) {
            reset()
            return 0.0
        }
        return y
    }

    /** Filtra un bloque (mantiene el estado entre llamadas). */
    fun process(samples: DoubleArray): DoubleArray {
        val output = DoubleArray(samples.size)
        for (i in samples.indices) output[i] = processSample(samples[i])
        return output
    }

    fun aWeightedDb(samples: DoubleArray): Double {
        val filtered = process(samples)
        val rms = Calibration.digitalRms(filtered)
        return if (rms > 0.0) 20.0 * kotlin.math.log10(rms) else Double.NEGATIVE_INFINITY
    }
}
