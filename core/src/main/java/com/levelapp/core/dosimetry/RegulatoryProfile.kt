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
        /**
         * NIOSH REL (1998): Criterio 85 dB(A) para 8 h, tasa de intercambio 3 dB,
         * umbral de integración 80 dB(A).
         */
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

        /**
         * OSHA PEL (29 CFR 1910.95 Table G-16): Límite legal permisible con criterio 90 dB(A)
         * para 8 h, tasa de intercambio 5 dB y umbral de integración de 90 dB(A).
         */
        fun oshaPelDefault(): RegulatoryProfile = RegulatoryProfile(
            id = "osha",
            name = "OSHA PEL",
            criterionDbA = 90.0,
            exchangeRateDb = 5.0,
            thresholdDbA = 90.0,
            referenceHours = 8.0,
            weighting = "A",
            averaging = "Slow",
        )

        fun oshaDefault(): RegulatoryProfile = oshaPelDefault()

        /**
         * OSHA HC (29 CFR 1910.95 Hearing Conservation Amendment / Action Level):
         * Criterio de 100% dosis en 90 dB(A) (50% dosis = 85 dB(A) Action Level),
         * tasa de intercambio 5 dB y umbral de integración de 80 dB(A).
         */
        fun oshaHcDefault(): RegulatoryProfile = RegulatoryProfile(
            id = "osha_hc",
            name = "OSHA HC",
            criterionDbA = 90.0,
            exchangeRateDb = 5.0,
            thresholdDbA = 80.0,
            referenceHours = 8.0,
            weighting = "A",
            averaging = "Slow",
        )

        /**
         * ISO 9612 (Método internacional de ingeniería para determinación de L_EX,8h):
         * Principio de igual energía (tasa de intercambio 3 dB), nivel de referencia 85 dB(A)
         * para 8 h y sin umbral de corte (threshold 0 dB(A): integra toda la energía acústica).
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

        /**
         * Resolución 1792 de 1990 (Colombia - Ministerios de Trabajo y Salud):
         * Límite permisible de 85 dB(A) para 8 h con tasa de intercambio de 5 dB
         * (85 dBA -> 8h, 90 dBA -> 4h, 95 dBA -> 2h, 100 dBA -> 1h) y umbral de 80 dB(A).
         */
        fun colombiaRes1792Default(): RegulatoryProfile = RegulatoryProfile(
            id = "col_res1792",
            name = "Res. 1792 (Col)",
            criterionDbA = 85.0,
            exchangeRateDb = 5.0,
            thresholdDbA = 80.0,
            referenceHours = 8.0,
            weighting = "A",
            averaging = "Slow",
        )

        val all: List<RegulatoryProfile> by lazy {
            listOf(
                nioshDefault(),
                oshaPelDefault(),
                oshaHcDefault(),
                iso9612Default(),
                colombiaRes1792Default()
            )
        }

        fun byId(id: String): RegulatoryProfile = when (id) {
            "osha_pel", "osha" -> oshaPelDefault()
            "osha_hc" -> oshaHcDefault()
            "iso9612" -> iso9612Default()
            "col_res1792" -> colombiaRes1792Default()
            else -> all.firstOrNull { it.id == id } ?: nioshDefault()
        }
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
