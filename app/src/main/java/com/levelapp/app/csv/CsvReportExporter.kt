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
        // Duración real medida (excluye pausas)
        val measuredSec = if (session.durationSeconds > 0.0) {
            session.durationSeconds
        } else {
            ((endMs - startMs).coerceAtLeast(1000L)) / 1000.0
        }
        val measuredHours = measuredSec / 3600.0
        val wallSec = ((endMs - startMs).coerceAtLeast(0L)) / 1000.0

        val selValue = if (session.sel > 0.0) {
            session.sel
        } else if (session.laeq > 0.0 && measuredSec > 0.0) {
            session.laeq + 10.0 * log10(measuredSec)
        } else 0.0
        val lex8hValue = DoseCalculator.lex8h(session.laeq, measuredSec)
        val eaPa2h = if (session.soundExposurePa2h > 0.0) {
            session.soundExposurePa2h
        } else {
            DoseCalculator.soundExposurePa2hFromLaeq(session.laeq, measuredSec)
        }
        val eaFormatted = DoseCalculator.formatSoundExposure(eaPa2h)

        val profile = RegulatoryProfile.byId(session.profileId)
        val lasMinValue = if (session.lasMin > 0.0) session.lasMin else session.laeq
        val l10Value = if (session.l10 > 0) session.l10 else session.lasMax
        val l50Value = if (session.l50 > 0) session.l50 else session.laeq
        val l90Value = if (session.l90 > 0) session.l90 else lasMinValue
        val projectedDose8h = DoseCalculator.projectedDose8hFromDose(session.dosePercent, measuredSec, profile)
        val twaStr = DoseCalculator.formatTwa(session.dosePercent, profile)

        val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        val effectivePoints = if (points.isNotEmpty()) {
            points
        } else {
            buildFallbackProgressionPoints(session, measuredSec)
        }

        val avgIntervalSec = if (effectivePoints.isNotEmpty()) {
            effectivePoints.map { it.durationSeconds }.average()
        } else 1.0
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
            writer.write("Duracion medida efectiva,${formatHmsFull(measuredSec)},${"%.2f".format(Locale.US, measuredSec)} s (${"%.4f".format(Locale.US, measuredHours)} h)\n")
            if (session.hasPauses || (wallSec - measuredSec >= 2.0)) {
                val pausedSec = if (session.pausedSeconds > 0.0) session.pausedSeconds else (wallSec - measuredSec).coerceAtLeast(0.0)
                writer.write("Tiempo total transcurrido (reloj),${formatHmsFull(wallSec)},Incluye pausas/interrupciones (${formatHmsFull(pausedSec)})\n")
            }
            writer.write("Calibracion Offset,${fmt(session.calibrationOffset)},dB\n")
            writer.write("Rango confiable,35.0 - 110.0,dB(A) (se integra todo el rango sin recorte)\n")

            // Bloque de resultados en el orden exacto (Req 8)
            writer.write("Criterio elegido,${profile.name},Criterio ${profile.criterionDbA} dBA / 8h / Tasa ${profile.exchangeRateDb} dB / Umbral ${profile.thresholdDbA} dBA\n")
            writer.write("Dosis Acumulada,${fmt(session.dosePercent)},% de la jornada de 8h\n")
            writer.write("TWA del criterio,${csvEscape(twaStr)},Calculado desde la dosis del criterio elegido\n")
            writer.write("Proyeccion 8 h,${fmt(projectedDose8h)},% estimado manteniendo el ritmo medido\n")
            writer.write("L_EX_8h (ISO 9612),${if (lex8hValue.isFinite()) fmt(lex8hValue) else "0.00"},dBA (por energia sin umbral)\n")
            writer.write("LAeq_T,${fmt(session.laeq)},dBA (Promedio energetico)\n")
            writer.write("SEL (LAE),${fmt(selValue)},dBA (Nivel de Exposicion Sonora integrado a 1 s)\n")
            writer.write("LASmax,${fmt(session.lasMax)},dBA (Ponderacion Slow tau=1 s)\n")
            writer.write("LASmin,${fmt(lasMinValue)},dBA (Ponderacion Slow tau=1 s)\n")
            writer.write("Percentil L10,${fmt(l10Value)},dBA (Superado el 10% del tiempo)\n")
            writer.write("Percentil L50,${fmt(l50Value)},dBA (Mediana - superado el 50% del tiempo)\n")
            writer.write("Percentil L90,${fmt(l90Value)},dBA (Ruido de fondo - superado el 90% del tiempo)\n")
            writer.write("Exposicion sonora E_A,${csvEscape(eaFormatted)},Pa^2*h (1.01 Pa^2*h = 100% a 85 dBA)\n")
            writer.write("Resolucion de la tabla temporal,${csvEscape(resolutionDescription)},Agrupacion adaptativa segun duracion\n")
            writer.write("Observaciones,${csvEscape(session.notes.ifBlank { "Sin observaciones" })},Notas del evaluador\n")
            writer.write("\n")

            // Tabla comparativa de los 5 perfiles con Dosis y TWA (Req 9)
            writer.write("COMPARATIVA DE CRITERIOS (5 PERFILES)\n")
            writer.write("Perfil,Criterio_dBA,Tasa_Intercambio_dB,Umbral_dBA,Dosis_%,TWA\n")
            val fiveProfiles = listOf(
                RegulatoryProfile.nioshDefault() to resolveDose(session, "niosh"),
                RegulatoryProfile.oshaPelDefault() to resolveDose(session, "osha"),
                RegulatoryProfile.oshaHcDefault() to resolveDose(session, "osha_hc"),
                RegulatoryProfile.iso9612Default() to resolveDose(session, "iso9612"),
                RegulatoryProfile.colombiaRes1792Default() to resolveDose(session, "col_res1792"),
            )
            for ((prof, pct) in fiveProfiles) {
                writer.write(
                    "${csvEscape(prof.name)},${fmt(prof.criterionDbA)},${fmt(prof.exchangeRateDb)},${fmt(prof.thresholdDbA)},${fmt(pct)},${csvEscape(DoseCalculator.formatTwa(pct, prof))}\n"
                )
            }
            writer.write("\n")

            // Avisos de calidad cuando apliquen (Req 10 + Req 4)
            val warnings = mutableListOf<String>()
            if (session.belowFloorSeconds > 0.0 && measuredSec > 0.0) {
                val pctBelow = (session.belowFloorSeconds / measuredSec) * 100.0
                if (pctBelow >= 0.1) {
                    warnings += "${"%.1f".format(Locale.US, pctBelow)} % del tiempo por debajo del piso del microfono (35 dBA): el LAeq real puede ser menor."
                }
            }
            if (session.saturationSeconds > 0.0) {
                val satMin = session.saturationSeconds / 60.0
                warnings += "${"%.2f".format(Locale.US, satMin)} min por encima de 110 dBA: posible saturacion del microfono, la dosis puede estar subestimada."
            }
            if (session.calibrationOffset == 0.0) {
                warnings += "Sin calibracion con sonometro patron"
            }
            if (session.resumedNote.isNotBlank()) {
                warnings += session.resumedNote
            }
            if (warnings.isNotEmpty()) {
                writer.write("AVISOS DE CALIDAD DE LA MEDICION\n")
                warnings.forEach { w -> writer.write("${csvEscape(w)}\n") }
                writer.write("\n")
            }

            writer.write("PROGRESION TEMPORAL PASO A PASO (FILAS HACIA ABAJO SEGUN DURACION)\n")
            writer.write(
                "Muestra_N,Rango_Tiempo,Hora,Nivel_dBA,LAeq_Acum_dBA,L_EX8h_Acum_dBA,SEL_LAE_Acum_dBA," +
                    "Dosis_Acum_%,TWA_Acum,Min_Rango_dBA,Max_Rango_dBA,SEL_LAE_Rango_dBA," +
                    "Dosis_NIOSH_%,Dosis_OSHA_PEL_%,Dosis_OSHA_HC_%,Dosis_ISO9612_%,Dosis_Res1792_Col_%\n"
            )

            val useHoursInRange = measuredSec >= 3600.0
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
                val cumLex = if (pt.lex8h.isFinite()) pt.lex8h else DoseCalculator.lex8h(pt.laeq, endElapsed)
                val rowTwaStr = DoseCalculator.formatTwa(pt.dosePercent, profile)

                writer.write(
                    "${idx + 1}," +
                        "${csvEscape(rangeLabel)}," +
                        "$clockStr," +
                        "${fmt(pt.dbA)}," +
                        "${fmt(pt.laeq)}," +
                        "${if (cumLex.isFinite()) fmt(cumLex) else "0.00"}," +
                        "${fmt(cumSel)}," +
                        "${fmt(pt.dosePercent)}," +
                        "${csvEscape(rowTwaStr)}," +
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

    private fun resolveDose(session: SessionEntity, profileId: String): Double = when (profileId) {
        "niosh" -> if (session.doseNiosh > 0.0 || session.profileId != "niosh") session.doseNiosh else session.dosePercent
        "osha" -> if (session.doseOsha > 0.0 || session.profileId != "osha") session.doseOsha else session.dosePercent
        "osha_hc" -> if (session.doseOshaHc > 0.0 || session.profileId != "osha_hc") session.doseOshaHc else session.dosePercent
        "iso9612" -> if (session.doseIso > 0.0 || session.profileId != "iso9612") session.doseIso else session.dosePercent
        "col_res1792" -> if (session.doseColRes1792 > 0.0 || session.profileId != "col_res1792") session.doseColRes1792 else session.dosePercent
        else -> 0.0
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

        val totalNiosh = resolveDose(session, "niosh")
        val totalOshaPel = resolveDose(session, "osha")
        val totalOshaHc = resolveDose(session, "osha_hc")
        val totalIso = resolveDose(session, "iso9612")
        val totalCol = resolveDose(session, "col_res1792")
        val minVal = if (session.lasMin > 0.0) session.lasMin else session.laeq

        for (i in 0 until numSteps) {
            val startS = i * stepSec
            val endS = min(totalSec, (i + 1) * stepSec)
            val dt = (endS - startS).coerceAtLeast(0.1)
            val fraction = endS / totalSec
            val cumDose = session.dosePercent * fraction
            val cumTwa = if (cumDose >= DoseCalculator.MIN_VALID_TWA_DOSE_PERCENT) {
                DoseCalculator.twaFromDose(cumDose, profile)
            } else Double.NEGATIVE_INFINITY
            val sliceSel = if (session.laeq > 0.0) session.laeq + 10.0 * log10(dt) else 0.0
            val cumSel = if (session.laeq > 0.0) session.laeq + 10.0 * log10(endS) else 0.0
            val cumLex = DoseCalculator.lex8h(session.laeq, endS)

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
                    lex8h = cumLex,
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
