package com.levelapp.core.dosimetry

import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

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
    const val MIN_VALID_TWA_DOSE_PERCENT = 0.01

    fun dosePercent(levelDbA: Double, durationHours: Double, profile: RegulatoryProfile): Double {
        require(durationHours >= 0.0) { "durationHours must be >= 0" }
        if (durationHours == 0.0 || !levelDbA.isFinite() || levelDbA < profile.thresholdDbA) return 0.0

        val exchangeRate = profile.exchangeRateDb
        val delta = (levelDbA - profile.criterionDbA) / exchangeRate
        val dose = (durationHours / profile.referenceHours) * 2.0.pow(delta)
        return dose * 100.0
    }

    /**
     * L_EX,8h según ISO 9612: calculado por energía acústica sin umbral de corte,
     * independiente del perfil regulatorio seleccionado:
     *   L_EX,8h = L_Aeq,T + 10 * log10(T_horas / 8)
     */
    fun lex8h(laeq: Double, measuredSeconds: Double): Double {
        if (measuredSeconds <= 0.0 || !laeq.isFinite()) return Double.NEGATIVE_INFINITY
        val durationHours = measuredSeconds / 3600.0
        return laeq + 10.0 * log10(durationHours.coerceAtLeast(1e-12) / 8.0)
    }

    /**
     * Exposición sonora E_A (Pa²·h) a partir de la integral de energía relativa
     * sumEnergySeconds = ∑ 10^(L/10) * dt, usando p0 = 20 uPa (p0^2 = 4e-10 Pa²).
     */
    fun soundExposurePa2h(sumEnergySeconds: Double): Double {
        if (sumEnergySeconds <= 0.0 || !sumEnergySeconds.isFinite()) return 0.0
        return 4e-10 * (sumEnergySeconds / 3600.0)
    }

    fun soundExposurePa2hFromLaeq(laeq: Double, measuredSeconds: Double): Double {
        if (measuredSeconds <= 0.0 || !laeq.isFinite()) return 0.0
        val durationHours = measuredSeconds / 3600.0
        return 4e-10 * durationHours * 10.0.pow(laeq / 10.0)
    }

    fun twaFromDose(dosePercent: Double, profile: RegulatoryProfile): Double {
        val ratio = dosePercent / 100.0
        if (ratio <= 0.0 || !ratio.isFinite()) return Double.NEGATIVE_INFINITY
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

    fun projectedDose8hFromDose(dosePercent: Double, measuredSeconds: Double, profile: RegulatoryProfile): Double {
        if (measuredSeconds <= 0.0 || !dosePercent.isFinite()) return 0.0
        val durationHours = measuredSeconds / 3600.0
        return dosePercent * (profile.referenceHours / durationHours.coerceAtLeast(1e-9))
    }

    /**
     * Formatea el TWA del criterio elegido. Si D < 0.01 %, no muestra un número
     * espurio sino "Bajo umbral (< XX dBA)".
     */
    fun formatTwa(dosePercent: Double, profile: RegulatoryProfile): String {
        if (!dosePercent.isFinite() || dosePercent < MIN_VALID_TWA_DOSE_PERCENT) {
            return if (profile.thresholdDbA > 0.0) {
                "Bajo umbral (< ${profile.thresholdDbA.toInt()} dBA)"
            } else {
                "Bajo umbral (< 0.01 %)"
            }
        }
        val twaVal = twaFromDose(dosePercent, profile)
        return if (twaVal.isFinite()) {
            String.format(Locale.US, "%.1f dBA", twaVal)
        } else {
            if (profile.thresholdDbA > 0.0) "Bajo umbral (< ${profile.thresholdDbA.toInt()} dBA)"
            else "Bajo umbral (< 0.01 %)"
        }
    }

    /**
     * Tiempo restante (en horas) hasta alcanzar 100 % de dosis si se mantiene el nivel actual.
     * Devuelve 0.0 si la dosis ya es >= 100 %, o null si el nivel actual está bajo el umbral.
     */
    fun remainingTimeHours(
        currentDosePercent: Double,
        currentLevelDbA: Double,
        profile: RegulatoryProfile
    ): Double? {
        if (currentDosePercent >= 100.0) return 0.0
        if (!currentLevelDbA.isFinite() || currentLevelDbA < profile.thresholdDbA) return null
        val ratePerHour = (100.0 / profile.referenceHours) * 2.0.pow((currentLevelDbA - profile.criterionDbA) / profile.exchangeRateDb)
        if (ratePerHour <= 0.0 || !ratePerHour.isFinite()) return null
        return ((100.0 - currentDosePercent) / ratePerHour).coerceAtLeast(0.0)
    }

    fun formatRemainingTimeHoursMinutes(hours: Double?): String {
        if (hours == null) return "Sin límite al nivel actual (bajo umbral)"
        if (hours <= 0.0) return "00:00 h (límite alcanzado)"
        if (hours > 99.0) return "> 99:00 h"
        val totalMinutes = (hours * 60.0).roundToInt().coerceAtLeast(0)
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return String.format(Locale.US, "%02d:%02d h", h, m)
    }

    /**
     * Formatea E_A (Pa²·h): usa notación científica (ej. 2.68 × 10⁻⁴ Pa²·h)
     * cuando es < 0.01 Pa²·h para evitar salidas de 0.000.
     */
    fun formatSoundExposure(eaPa2h: Double, asciiSafe: Boolean = false): String {
        val unit = if (asciiSafe) "Pa^2*h" else "Pa²·h"
        if (!eaPa2h.isFinite() || eaPa2h <= 0.0) return "0.00 $unit"
        if (eaPa2h >= 1.0) return "${String.format(Locale.US, "%.2f", eaPa2h)} $unit"
        if (eaPa2h >= 0.01) return "${String.format(Locale.US, "%.3f", eaPa2h)} $unit"

        var exp = floor(log10(eaPa2h)).toInt()
        var mantissa = eaPa2h / 10.0.pow(exp)
        if (mantissa >= 9.995) {
            mantissa = 1.0
            exp += 1
        }
        return if (asciiSafe) {
            String.format(Locale.US, "%.2f x 10^%d %s", mantissa, exp, unit)
        } else {
            String.format(Locale.US, "%.2f × 10%s %s", mantissa, toSuperscript(exp), unit)
        }
    }

    private fun toSuperscript(number: Int): String {
        val map = mapOf(
            '-' to '⁻', '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³',
            '4' to '⁴', '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹'
        )
        return number.toString().map { map[it] ?: it }.joinToString("")
    }
}
