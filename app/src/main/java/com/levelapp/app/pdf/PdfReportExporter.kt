package com.levelapp.app.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import java.io.File
import java.io.FileOutputStream

class PdfReportExporter(private val context: Context) {
    fun generateSessionPdf(sessionId: String, title: String): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint().apply { color = Color.BLACK; textSize = 18f }
        canvas.drawText(title, 60f, 80f, paint)
        canvas.drawText("Sesión ID: $sessionId", 60f, 130f, paint)
        canvas.drawText("Perfil: NIOSH REL • 85 dBA/8h • 3 dB • threshold 80 dBA", 60f, 170f, paint)
        canvas.drawText("Nota: Documento generado localmente. No sustituye validación metrológica.", 60f, 210f, paint)
        pdfDocument.finishPage(page)

        val outputDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "levelapp")
        outputDir.mkdirs()
        val outputFile = File(outputDir, "session_$sessionId.pdf")
        FileOutputStream(outputFile).use { stream ->
            pdfDocument.writeTo(stream)
        }
        pdfDocument.close()
        return outputFile
    }
}
