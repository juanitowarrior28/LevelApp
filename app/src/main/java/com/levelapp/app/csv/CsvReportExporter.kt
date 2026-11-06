package com.levelapp.app.csv

import android.content.Context
import android.os.Environment
import com.levelapp.data.database.MeasurementPointEntity
import com.levelapp.data.database.SessionEntity
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CsvReportExporter(private val context: Context) {
    fun generateSessionCsv(session: SessionEntity, points: List<MeasurementPointEntity>): File {
        val outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val levelAppDir = File(outputDir, "LevelApp_Exportaciones")
        levelAppDir.mkdirs()

        val safeTitle = session.title.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val outputFile = File(levelAppDir, "$safeTitle.csv")

        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(session.startedAtEpochMs))

        FileWriter(outputFile).use { writer ->
            writer.append("Reporte de dosimetría acústica\n")
            writer.append("Titulo,${session.title}\n")
            writer.append("Trabajador,${session.workerName}\n")
            writer.append("Evaluador,${session.evaluatorName}\n")
            writer.append("Perfil,${session.profileId.uppercase()}\n")
            writer.append("Estado,${session.status}\n")
            writer.append("Fecha,${dateStr}\n")
            writer.append("LAeq dB(A),${"%.2f".format(session.laeq)}\n")
            writer.append("LASmax dB(A),${"%.2f".format(session.lasMax)}\n")
            writer.append("TWA dB(A),${"%.2f".format(session.twa)}\n")
            writer.append("Dosis %,${"%.2f".format(session.dosePercent)}\n\n")
            writer.append("timestamp_epoch_ms,dbA,laeq,las_max,twa,dose_percent,duration_seconds\n")

            if (points.isEmpty()) {
                writer.append("${session.startedAtEpochMs},${"%.2f".format(session.laeq)},${"%.2f".format(session.laeq)},${"%.2f".format(session.lasMax)},${"%.2f".format(session.twa)},${"%.2f".format(session.dosePercent)},0\n")
            } else {
                points.forEach { point ->
                    writer.append("${point.timestampEpochMs},${"%.2f".format(point.dbA)},${"%.2f".format(point.laeq)},${"%.2f".format(point.lasMax)},${"%.2f".format(point.twa)},${"%.2f".format(point.dosePercent)},${"%.2f".format(point.durationSeconds)}\n")
                }
            }
        }
        return outputFile
    }
}
