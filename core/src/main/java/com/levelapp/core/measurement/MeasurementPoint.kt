package com.levelapp.core.measurement

import kotlin.math.round

data class MeasurementPoint(
    val timestampEpochMs: Long,
    val dbA: Double,
    val laeq: Double,
    val lasMax: Double,
    val twa: Double,
    val dosePercent: Double,
    val durationSeconds: Double,
)

fun MeasurementPoint.formattedInstant(): String = "${dbA.roundTo1Decimal()} dB(A)"

private fun Double.roundTo1Decimal(): Double = round(this * 10.0) / 10.0
