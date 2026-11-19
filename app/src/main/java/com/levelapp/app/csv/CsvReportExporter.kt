package com.levelapp.app.csv

import android.content.Context
import com.levelapp.core.dosimetry.DoseCalculator
import com.levelapp.core.dosimetry.RegulatoryProfile
import com.levelapp.data.database.MeasurementPointEntity
import com.levelapp.data.database.SessionEntity
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow

class CsvReportExporter(private val context: Context) {

    fun generateSessionCsv(
        session: SessionEntity,
        points: List<MeasurementPointEntity> = emptyList()
    ): File {
        val exportDir = File(context.cacheDir, "LevelApp_Exportaciones").apply { mkdirs() }
        val safeTitle = session.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "Sesion_${session.id}" }
        val outputFile = File(exportDir, "${safeTitle}_${session.id}.csv")

        val startMs = session.startedAtEpochMs
        val endMs = session.endedAtEpochMs ?: System.currentTimeMillis()
        val durationSec = if (session.durationSeconds > 0.0) {
            session.durationSeconds
        } else {
            ((endMs - startMs).coerceAtLeast(1000L)) / 1000.0
        }
        val durationHours = durationSec / 3600.0

        val selValue = if (session.sel > 0.0) {
            session.sel
        } else if (session.laeq > 0.0 && durationSec > 0.0) {
            session.laeq + 10.0 * log10(durationSec)
        } else 0.0

        val lasMinValue = if (session.lasMin > 0.0) session.lasMin else session.laeq
        val remainingDose = (100.0 - session.dosePercent).coerceAtLeast(0.0)
        val projectedDose8h = if (durationHours > 1e-6) session.dosePercent * (8.0 / durationHours) else 0.0
        val soundExposurePa2h = 4e-10 * durationHours * 10.0.pow(session.laeq / 10.0)

        val profile = RegulatoryProfile.byId(session.profileId)
        val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        val effectivePoints = if (points.isNotEmpty()) {
            points
        } else {
            buildFallbackProgressionPoints(session, durationSec)
        }

        val avgIntervalSec = if (effectivePoints.isNotEmpty()) {
            effectivePoints.map { it.durationSeconds }.average()
        } else {
            1.0
        }
        val resolutionDescription = if (avgIntervalSec <= 1.5) {
            "Muestra por muestra cada 1 s (${effectivePoints.size} filas)"
        } else {
            "Rangos de ${"%.0f".format(Locale.US, avgIntervalSec)} s con promedio energetico logaritmico (${effectivePoints.size} filas)"
        }

        OutputStreamWriter(FileOutputStream(outputFile), StandardCharsets.UTF_8).use { writer ->
            writer.write("REPORTE Y SERIE TEMPORAL DE DOSIMETRIA ACUSTICA - LEVELAPP\n")
            writer.write("Parametro,Valor,Unidad / Detalle\n")
            writer.write("ID Sesion,${session.id},Identificador unico\n")
            writer.write("Nombre de la medicion,${csvEscape(session.title)},Titulo del registro\n")
            writer.write("Area / Lugar,${csvEscape(session.area)},Ubicacion evaluada\n")
            writer.write("Trabajador,${csvEscape(session.workerName)},Persona evaluada\n")
            writer.write("Cargo / GES,${csvEscape(session.workerRole)},Puesto o Grupo de Exposicion Similar\n")
            writer.write("Evaluador / Licencia SST,${csvEscape(session.evaluatorName)},Responsable de la medicion\n")
            writer.write("Fecha de medicion,${dateFmt.format(Date(startMs))},DD/MM/AAAA\n")
            writer.write("Hora de inicio,${timeFmt.format(Date(startMs))},HH:MM:SS\n")
            writer.write("Hora de finalizacion,${timeFmt.format(Date(endMs))},HH:MM:SS\n")
            writer.write("Duracion efectiva,${formatHmsFull(durationSec)},${"%.2f".format(Locale.US, durationSec)} s (${"%.4f".format(Locale.US, durationHours)} h)\n")
            writer.write("Rango dinamico del sistema,35.0 - 110.0,dB(A) (Ponderacion A IEC 61672)\n")
            writer.write("Criterio principal,${profile.name},Criterio ${profile.criterionDbA} dBA / 8h / Tasa ${profile.exchangeRateDb} dB / Umbral ${profile.thresholdDbA} dBA\n")
            writer.write("LAeq_T (Promedio energetico),${fmt(session.laeq)},dBA\n")
            writer.write("SEL (LAE - Nivel de Exposicion Sonora),${fmt(selValue)},dBA (Energia ponderada A integrada a 1 s)\n")
            writer.write("LEX_8h / TWA (Nivel diario equivalente 8h),${fmt(session.twa)},dBA\n")
            writer.write("Exposicion sonora (EA - ISO 9612),${"%.4f".format(Locale.US, soundExposurePa2h)},Pa^2*h (1.01 Pa^2*h = 100% a 85 dBA)\n")
            writer.write("LASmax,${fmt(session.lasMax)},dBA (Maximo registrado)\n")
            writer.write("LASmin,${fmt(lasMinValue)},dBA (Minimo registrado)\n")
            writer.write("Percentil L10 (Picos frecuentes),${fmt(if (session.l10 > 0) session.l10 else session.lasMax)},dBA (Superado el 10% del tiempo)\n")
            writer.write("Percentil L50 (Mediana acustica),${fmt(if (session.l50 > 0) session.l50 else session.laeq)},dBA (Superado el 50% del tiempo)\n")
            writer.write("Percentil L90 (Ruido de fondo),${fmt(if (session.l90 > 0) session.l90 else lasMinValue)},dBA (Superado el 90% del tiempo)\n")
            writer.write("Dosis Acumulada (${profile.name}),${fmt(session.dosePercent)},% de la jornada de 8h\n")
            writer.write("Porcentaje Restante de Exposicion Permitida,${fmt(remainingDose)},% disponible antes del limite del 100%\n")
            writer.write("Dosis Proyectada a 8 horas,${fmt(projectedDose8h)},% estimado manteniendo el ritmo actual\n")
            writer.write("Dosis bajo NIOSH REL (85 dBA / 3 dB / umbral 80 dBA),${fmt(if (session.doseNiosh > 0 || session.profileId != "niosh") session.doseNiosh else session.dosePercent)},%\n")
            writer.write("Dosis bajo OSHA PEL (90 dBA / 5 dB / umbral 90 dBA),${fmt(if (session.doseOsha > 0 || session.profileId != "osha") session.doseOsha else session.dosePercent)},%\n")
            writer.write("Dosis bajo OSHA HC (90 dBA / 5 dB / umbral 80 dBA),${fmt(if (session.doseOshaHc > 0 || session.profileId != "osha_hc") session.doseOshaHc else session.dosePercent)},%\n")
            writer.write("Dosis bajo ISO 9612 (85 dBA / 3 dB / sin umbral),${fmt(if (session.doseIso > 0 || session.profileId != "iso9612") session.doseIso else session.dosePercent)},%\n")
            writer.write("Dosis bajo Res. 1792/1990 Colombia (85 dBA / 5 dB / umbral 80 dBA),${fmt(if (session.doseColRes1792 > 0 || session.profileId != "col_res1792") session.doseColRes1792 else session.dosePercent)},%\n")
            writer.write("Resolucion de la tabla temporal,${csvEscape(resolutionDescription)},Agrupacion adaptativa segun duracion\n")
            writer.write("Observaciones,${csvEscape(session.notes.ifBlank { "Sin observaciones" })},Notas del evaluador\n")
            writer.write("\n")

            writer.write("PROGRESION TEMPORAL PASO A PASO (FILAS HACIA ABAJO SEGUN DURACION)\n")
            writer.write(
                "Muestra_N,Rango_Tiempo,Hora,Nivel_dBA,LAeq_Acum_dBA,SEL_LAE_Acum_dBA," +
                    "Dosis_Acum_%,Restante_%,TWA_Acum_dBA,Min_Rango_dBA,Max_Rango_dBA,SEL_LAE_Rango_dBA," +
                    "Dosis_NIOSH_%,Dosis_OSHA_PEL_%,Dosis_OSHA_HC_%,Dosis_ISO9612_%,Dosis_Res1792_Col_%\n"
            )

            val useHoursInRange = durationSec >= 3600.0
            effectivePoints.forEachIndexed { idx, pt ->
                val endElapsed = if (pt.elapsedSeconds > 0.0) pt.elapsedSeconds else (idx + 1) * pt.durationSeconds
                val startElapsed = (endElapsed - pt.durationSeconds).coerceAtLeast(0.0)
                val rangeLabel = "${formatCompactTime(startElapsed, useHoursInRange)} a ${formatCompactTime(endElapsed, useHoursInRange)}"
                val clockStr = timeFmt.format(Date(pt.timestampEpochMs))
                val sliceMin = if (pt.lasMin > 0.0) pt.lasMin else pt.dbA
                val sliceSel = if (pt.selSlice > 0.0) {
                    pt.selSlice
                } else if (pt.dbA > 0.0 && pt.durationSeconds > 0.0) {
                    pt.dbA + 10.0 * log10(pt.durationSeconds)
                } else 0.0
                val cumSel = if (pt.selCumulative > 0.0) {
                    pt.selCumulative
                } else if (pt.laeq > 0.0 && endElapsed > 0.0) {
                    pt.laeq + 10.0 * log10(endElapsed)
                } else 0.0
                val remPct = (100.0 - pt.dosePercent).coerceAtLeast(0.0)

                writer.write(
                    "${idx + 1}," +
                        "${csvEscape(rangeLabel)}," +
                        "$clockStr," +
                        "${fmt(pt.dbA)}," +
                        "${fmt(pt.laeq)}," +
                        "${fmt(cumSel)}," +
                        "${fmt(pt.dosePercent)}," +
                        "${fmt(remPct)}," +
                        "${fmt(pt.twa)}," +
                        "${fmt(sliceMin)}," +
                        "${fmt(pt.lasMax)}," +
                        "${fmt(sliceSel)}," +
                        "${fmt(pt.doseNiosh)}," +
                        "${fmt(pt.doseOsha)}," +
                        "${fmt(pt.doseOshaHc)}," +
                        "${fmt(pt.doseIso)}," +
                        "${fmt(pt.doseColRes1792)}\n"
                )
            }
        }
        return outputFile
    }

    private fun buildFallbackProgressionPoints(
        session: SessionEntity,
        totalDurationSec: Double
    ): List<MeasurementPointEntity> {
        val totalSec = totalDurationSec.coerceAtLeast(1.0)
        val stepSec = when {
            totalSec <= 3 * 60 -> 1.0
            totalSec <= 15 * 60 -> 5.0
            totalSec <= 60 * 60 -> 15.0
            totalSec <= 4 * 3600 -> 30.0
            else -> 60.0
        }
        val numSteps = ceil(totalSec / stepSec).toInt().coerceAtLeast(1)
        val profile = RegulatoryProfile.byId(session.profileId)
        val list = ArrayList<MeasurementPointEntity>(numSteps)

        val totalNiosh = if (session.doseNiosh > 0.0 || session.profileId != "niosh") session.doseNiosh else session.dosePercent
        val totalOshaPel = if (session.doseOsha > 0.0 || session.profileId != "osha") session.doseOsha else session.dosePercent
        val totalOshaHc = if (session.doseOshaHc > 0.0 || session.profileId != "osha_hc") session.doseOshaHc else session.dosePercent
        val totalIso = if (session.doseIso > 0.0 || session.profileId != "iso9612") session.doseIso else session.dosePercent
        val totalCol = if (session.doseColRes1792 > 0.0 || session.profileId != "col_res1792") session.doseColRes1792 else session.dosePercent
        val minVal = if (session.lasMin > 0.0) session.lasMin else session.laeq

        for (i in 0 until numSteps) {
            val startS = i * stepSec
            val endS = min(totalSec, (i + 1) * stepSec)
            val dt = (endS - startS).coerceAtLeast(0.1)
            val fraction = endS / totalSec
            val cumDose = session.dosePercent * fraction
            val cumTwa = DoseCalculator.twaFromDose(cumDose, profile).let { if (it.isFinite()) it else 0.0 }
            val sliceSel = if (session.laeq > 0.0) session.laeq + 10.0 * log10(dt) else 0.0
            val cumSel = if (session.laeq > 0.0) session.laeq + 10.0 * log10(endS) else 0.0

            list.add(
                MeasurementPointEntity(
                    sessionId = session.id,
                    timestampEpochMs = session.startedAtEpochMs + (startS * 1000.0).toLong(),
                    elapsedSeconds = endS,
                    durationSeconds = dt,
                    dbA = session.laeq,
                    lasMin = minVal,
                    lasMax = session.lasMax,
                    selSlice = sliceSel,
                    laeq = session.laeq,
                    selCumulative = cumSel,
                    twa = cumTwa,
                    dosePercent = cumDose,
                    doseNiosh = totalNiosh * fraction,
                    doseOsha = totalOshaPel * fraction,
                    doseOshaHc = totalOshaHc * fraction,
                    doseIso = totalIso * fraction,
                    doseColRes1792 = totalCol * fraction,
                )
            )
        }
        return list
    }

    private fun fmt(value: Double): String {
        if (!value.isFinite()) return "0.00"
        return String.format(Locale.US, "%.2f", value)
    }

    private fun csvEscape(text: String): String {
        val clean = text.replace("\r", " ").replace("\n", " ")
        return "\"${clean.replace("\"", "\"\"")}\""
    }

    private fun formatCompactTime(seconds: Double, includeHours: Boolean): String {
        val total = ceil(seconds - 1e-4).toLong().coerceAtLeast(0L)
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        return if (includeHours || h > 0L) {
            String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }

    private fun formatHmsFull(seconds: Double): String {
        val total = seconds.toLong().coerceAtLeast(0L)
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }
}
