package com.levelapp.core.dosimetry

import kotlin.math.log10
import kotlin.math.pow

data class RegulatoryProfile(
    val id: String,
    val name: String,
    val criterionDbA: Double,
    val exchangeRateDb: Double,
    val thresholdDbA: Double,
    val referenceHours: Double,
    val weighting: String = "A",
    val averaging: String = "Slow",
) {
    companion object {
        fun nioshDefault(): RegulatoryProfile = RegulatoryProfile(
            id = "niosh",
            name = "NIOSH REL",
            criterionDbA = 85.0,
            exchangeRateDb = 3.0,
            thresholdDbA = 80.0,
            referenceHours = 8.0,
            weighting = "A",
            averaging = "Slow",
        )

        fun oshaDefault(): RegulatoryProfile = RegulatoryProfile(
            id = "osha",
            name = "OSHA PEL",
            criterionDbA = 90.0,
            exchangeRateDb = 5.0,
            thresholdDbA = 80.0,
            referenceHours = 8.0,
            weighting = "A",
            averaging = "Slow",
        )

        /**
         * ISO 9612 (adoptada en Colombia como NTC-ISO 9612): principio de igual energía
         * (tasa de intercambio 3 dB), nivel de referencia 85 dB(A) para 8 h y SIN umbral
         * de integración (todo el sonido medido cuenta). La dosis % equivale a
         * 100 · 10^((L_EX,8h − 85)/10).
         */
        fun iso9612Default(): RegulatoryProfile = RegulatoryProfile(
            id = "iso9612",
            name = "ISO 9612",
            criterionDbA = 85.0,
            exchangeRateDb = 3.0,
            thresholdDbA = 0.0,
            referenceHours = 8.0,
            weighting = "A",
            averaging = "Slow",
        )

        val all: List<RegulatoryProfile> by lazy { listOf(nioshDefault(), oshaDefault(), iso9612Default()) }

        fun byId(id: String): RegulatoryProfile = all.firstOrNull { it.id == id } ?: nioshDefault()
    }
}

object DoseCalculator {
    fun dosePercent(levelDbA: Double, durationHours: Double, profile: RegulatoryProfile): Double {
        require(durationHours >= 0.0) { "durationHours must be >= 0" }
        if (durationHours == 0.0 || levelDbA < profile.thresholdDbA) return 0.0

        val exchangeRate = profile.exchangeRateDb
        val delta = (levelDbA - profile.criterionDbA) / exchangeRate
        val dose = (durationHours / profile.referenceHours) * 2.0.pow(delta)
        return dose * 100.0
    }

    fun twaFromDose(dosePercent: Double, profile: RegulatoryProfile): Double {
        val ratio = dosePercent / 100.0
        if (ratio <= 0.0) return Double.NEGATIVE_INFINITY
        val exchangeFactor = if (profile.exchangeRateDb == 3.0) 10.0 else profile.exchangeRateDb / log10(2.0)
        return profile.criterionDbA + exchangeFactor * log10(ratio)
    }

    fun twa(levelDbA: Double, durationHours: Double, profile: RegulatoryProfile): Double {
        if (durationHours <= 0.0) return Double.NEGATIVE_INFINITY
        val dose = dosePercent(levelDbA, durationHours, profile)
        return twaFromDose(dose, profile)
    }

    fun projectedDose8h(levelDbA: Double, durationHours: Double, profile: RegulatoryProfile): Double {
        val dose = dosePercent(levelDbA, durationHours, profile)
        return if (durationHours <= 0.0) 0.0 else dose * (profile.referenceHours / durationHours.coerceAtLeast(1e-9))
    }
}
