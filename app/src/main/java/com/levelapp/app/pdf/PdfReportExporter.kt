package com.levelapp.app.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.levelapp.data.database.MeasurementPointEntity
import com.levelapp.data.database.SessionEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PdfReportExporter(private val context: Context) {
    fun generateSessionPdf(session: SessionEntity, points: List<MeasurementPointEntity>): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val background = Paint().apply { color = Color.WHITE }
        canvas.drawRect(0f, 0f, 595f, 842f, background)

        val darkPaint = Paint().apply {
            color = Color.rgb(30, 35, 43)
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, 595f, 150f, darkPaint)

        val accentPaint = Paint().apply {
            color = Color.rgb(122, 126, 255)
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, 595f, 10f, accentPaint)

        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 26f
            isFakeBoldText = true
        }
        canvas.drawText("Reporte de dosimetría", 42f, 60f, titlePaint)

        val subtitlePaint = Paint().apply {
            color = Color.argb(180, 255, 255, 255)
            textSize = 12f
        }
        val dateStr = SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.getDefault()).format(Date(session.startedAtEpochMs))
        canvas.drawText(dateStr, 42f, 90f, subtitlePaint)
        canvas.drawText(session.title, 42f, 112f, subtitlePaint)

        val cardPaint = Paint().apply {
            color = Color.rgb(248, 249, 252)
            style = Paint.Style.FILL
        }
        val cardStroke = Paint().apply {
            color = Color.rgb(219, 224, 235)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }

        val metrics = listOf(
            "LAeq" to "${"%.1f".format(session.laeq)} dB(A)",
            "LASmax" to "${"%.1f".format(session.lasMax)} dB(A)",
            "TWA" to "${"%.1f".format(session.twa)} dB(A)",
            "Dosis" to "${"%.1f".format(session.dosePercent)}%"
        )

        val cardWidth = 240f
        val cardHeight = 88f
        val startX = 42f
        val startY = 175f
        val gap = 16f

        metrics.forEachIndexed { index, metric ->
            val x = startX + (index % 2) * (cardWidth + gap)
            val y = startY + (index / 2) * (cardHeight + gap)
            val rect = RectF(x, y, x + cardWidth, y + cardHeight)
            canvas.drawRoundRect(rect, 14f, 14f, cardPaint)
            canvas.drawRoundRect(rect, 14f, 14f, cardStroke)

            val labelPaint = Paint().apply {
                color = Color.rgb(100, 116, 139)
                textSize = 11f
            }
            val valuePaint = Paint().apply {
                color = Color.rgb(15, 23, 42)
                textSize = 22f
                isFakeBoldText = true
            }
            canvas.drawText(metric.first, x + 18f, y + 24f, labelPaint)
            canvas.drawText(metric.second, x + 18f, y + 58f, valuePaint)
        }

        val graphRect = RectF(42f, 430f, 553f, 650f)
        canvas.drawRoundRect(graphRect, 18f, 18f, cardPaint)
        canvas.drawRoundRect(graphRect, 18f, 18f, cardStroke)

        val graphTitle = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 14f
            isFakeBoldText = true
        }
        canvas.drawText("Nivel de ruido en la sesión", 58f, 458f, graphTitle)

        val gridPaint = Paint().apply {
            color = Color.argb(80, 148, 163, 184)
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        val linePaint = Paint().apply {
            color = Color.rgb(122, 126, 255)
            strokeWidth = 3f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        val fillPaint = Paint().apply {
            color = Color.argb(60, 122, 126, 255)
            style = Paint.Style.FILL
        }

        val graphLeft = graphRect.left + 18f
        val graphTop = graphRect.top + 18f
        val graphRight = graphRect.right - 18f
        val graphBottom = graphRect.bottom - 18f
        val graphWidth = graphRight - graphLeft
        val graphHeight = graphBottom - graphTop

        for (i in 0..4) {
            val y = graphTop + i * (graphHeight / 4f)
            canvas.drawLine(graphLeft, y, graphRight, y, gridPaint)
        }
        for (i in 0..4) {
            val x = graphLeft + i * (graphWidth / 4f)
            canvas.drawLine(x, graphTop, x, graphBottom, gridPaint)
        }

        val values = points.map { it.dbA.toFloat() }.takeLast(200)
        if (values.size > 1) {
            val path = Path()
            val fillPath = Path()
            val minDb = 20f
            val maxDb = 100f

            values.forEachIndexed { index, value ->
                val x = graphLeft + (index.toFloat() / (values.size - 1).coerceAtLeast(1).toFloat()) * graphWidth
                val clampedValue = value.coerceIn(minDb, maxDb)
                val y = graphBottom - ((clampedValue - minDb) / (maxDb - minDb).coerceAtLeast(1f)) * graphHeight

                if (index == 0) {
                    path.moveTo(x, y)
                    fillPath.moveTo(x, graphBottom)
                    fillPath.lineTo(x, y)
                } else {
                    path.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo(graphRight, graphBottom)
            fillPath.close()
            canvas.drawPath(fillPath, fillPaint)
            canvas.drawPath(path, linePaint)
        }

        val footerPaint = Paint().apply {
            color = Color.rgb(71, 85, 105)
            textSize = 11f
        }
        canvas.drawText("Perfil: ${session.profileId.uppercase()} • Limite: ${if (session.profileId.uppercase() == "NIOSH") "85 dB(A) / 3 dB" else "90 dB(A) / 5 dB"}", 42f, 705f, footerPaint)
        canvas.drawText("Documento generado localmente. No sustituye validación metrológica ni certificación oficial.", 42f, 726f, footerPaint)
        canvas.drawText("LevelApp • Reporte de exposición acústica", 42f, 789f, footerPaint)

        pdfDocument.finishPage(page)

        val outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val levelAppDir = File(outputDir, "LevelApp_Exportaciones")
        levelAppDir.mkdirs()

        val safeTitle = session.title.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val outputFile = File(levelAppDir, "$safeTitle.pdf")

        FileOutputStream(outputFile).use { stream ->
            pdfDocument.writeTo(stream)
        }
        pdfDocument.close()
        return outputFile
    }
}
