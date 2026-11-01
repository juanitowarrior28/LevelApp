package com.levelapp.app.csv

import android.content.Context
import android.os.Environment
import com.levelapp.data.database.SessionEntity
import java.io.File
import java.io.FileWriter

class CsvReportExporter(private val context: Context) {
    fun generateSessionCsv(session: SessionEntity): File {
        val outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val levelAppDir = File(outputDir, "LevelApp_Exportaciones")
        levelAppDir.mkdirs()
        
        val safeTitle = session.title.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val outputFile = File(levelAppDir, "$safeTitle.csv")
        
        FileWriter(outputFile).use { writer ->
            writer.append("ID,Titulo,Trabajador,Evaluador,Perfil,Status,Inicio(ms),Fin(ms),LAeq,LASmax,TWA,Dosis(%)\n")
            writer.append("${session.id},")
            writer.append("${session.title},")
            writer.append("${session.workerName},")
            writer.append("${session.evaluatorName},")
            writer.append("${session.profileId},")
            writer.append("${session.status},")
            writer.append("${session.startedAtEpochMs},")
            writer.append("${session.endedAtEpochMs ?: ""},")
            writer.append("${"%.2f".format(session.laeq)},")
            writer.append("${"%.2f".format(session.lasMax)},")
            writer.append("${"%.2f".format(session.twa)},")
            writer.append("${"%.2f".format(session.dosePercent)}\n")
        }
        return outputFile
    }
}
