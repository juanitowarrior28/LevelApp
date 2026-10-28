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

    fun twa(levelDbA: Double, durationHours: Double, profile: RegulatoryProfile): Double {
        if (durationHours <= 0.0) return Double.NEGATIVE_INFINITY
        val dose = dosePercent(levelDbA, durationHours, profile)
        val ratio = dose / 100.0
        return if (ratio <= 0.0) Double.NEGATIVE_INFINITY else profile.criterionDbA + 10.0 * log10(ratio)
    }

    fun projectedDose8h(levelDbA: Double, durationHours: Double, profile: RegulatoryProfile): Double {
        val dose = dosePercent(levelDbA, durationHours, profile)
        return if (durationHours <= 0.0) 0.0 else dose * (profile.referenceHours / durationHours.coerceAtLeast(1e-9))
    }
}
