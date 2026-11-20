package com.levelapp.core.audio

import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Integrador exponencial con constante de tiempo Slow (IEC 61672-1: tau = 1.0 s).
 *
 * Integra la energía cuadrática media de la señal filtrada:
 *   p_S^2[n] = p_S^2[n-1] + alpha * (x^2[n] - p_S^2[n-1])
 * con alpha = 1 - exp(-1 / (sampleRate * timeConstantSeconds)).
 *
 * También acumula energía lineal total para calcular el L_eq exacto del tramo.
 */
class SlowIntegrator(
    private val sampleRate: Int,
    private val timeConstantSeconds: Double = 1.0,
) {
    init {
        require(sampleRate > 0) { "sampleRate must be > 0" }
        require(timeConstantSeconds > 0.0) { "timeConstantSeconds must be > 0" }
    }

    private val alpha = 1.0 - exp(-1.0 / (sampleRate * timeConstantSeconds))
    private var meanSquare: Double = 0.0
    private var energySum: Double = 0.0
    private var sampleCount: Long = 0

    /**
     * Pre-carga la energía media inicial tras el descarte de arranque del hardware
     * para evitar que el filtro Slow arranque desde 0 y genere un mínimo falso.
     */
    fun seedMeanSquare(initialMeanSquare: Double) {
        if (initialMeanSquare.isFinite() && initialMeanSquare >= 0.0) {
            meanSquare = initialMeanSquare
        }
    }

    fun reset() {
        meanSquare = 0.0
        energySum = 0.0
        sampleCount = 0
    }

    /**
     * Procesa una muestra filtrada y devuelve el valor eficaz Slow (RMS lineal).
     */
    fun update(linearValue: Double): Double {
        val sq = linearValue * linearValue
        meanSquare += alpha * (sq - meanSquare)
        energySum += sq
        sampleCount += 1
        return sqrt(meanSquare.coerceAtLeast(0.0))
    }

    fun currentRms(): Double = sqrt(meanSquare.coerceAtLeast(0.0))

    fun currentDb(referenceRms: Double = 32768.0, referenceDb: Double = 100.0): Double {
        val refSq = referenceRms * referenceRms
        val ratio = (meanSquare / refSq).coerceAtLeast(1e-20)
        return 10.0 * log10(ratio) + referenceDb
    }

    /**
     * Calcula el Leq energético exacto sobre todas las muestras acumuladas.
     */
    fun computeLeq(referenceRms: Double = 1.0, referenceDb: Double = 0.0): Double {
        if (sampleCount == 0L || referenceRms <= 0.0) return Double.NEGATIVE_INFINITY
        val meanSq = energySum / sampleCount.toDouble()
        val refSq = referenceRms * referenceRms
        val ratio = (meanSq / refSq).coerceAtLeast(1e-24)
        return 10.0 * log10(ratio) + referenceDb
    }

    /**
     * Sobrecarga compatible que exige una duración positiva y calcula el Leq
     * respecto a referenceRms (por defecto escala unitaria 1.0).
     */
    fun computeLeq(durationSeconds: Double, referenceRms: Double = 1.0, referenceDb: Double = 0.0): Double {
        if (durationSeconds <= 0.0 || sampleCount == 0L || referenceRms <= 0.0) return Double.NEGATIVE_INFINITY
        val effectiveSamples = (durationSeconds * sampleRate).coerceAtLeast(1.0)
        val meanSq = energySum / effectiveSamples
        val refSq = referenceRms * referenceRms
        val ratio = (meanSq / refSq).coerceAtLeast(1e-24)
        return 10.0 * log10(ratio) + referenceDb
    }
}
