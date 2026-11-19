package com.levelapp.app.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.levelapp.app.R
import com.levelapp.core.dosimetry.RegulatoryProfile
import com.levelapp.data.database.SessionEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

class PdfReportExporter(private val context: Context) {

    // Paleta Tema Blanco (ideal para impresión y firmas) con acentos Periwinkle de LevelApp
    private val colorBg = Color.parseColor("#FFFFFF")
    private val colorCard = Color.parseColor("#F8FAFC")
    private val colorCardAlt = Color.parseColor("#FFFFFF")
    private val colorBorder = Color.parseColor("#CBD5E1")
    private val colorTextPrimary = Color.parseColor("#0F172A")
    private val colorTextSecondary = Color.parseColor("#475569")
    private val colorPeriwinkle = Color.parseColor("#5B60F6")
    private val colorPeriwinkleBg = Color.parseColor("#EEF0FF")
    private val colorAmber = Color.parseColor("#D97706")
    private val colorRed = Color.parseColor("#DC2626")

    fun generateSessionPdf(session: SessionEntity): File {
        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val baseTypeface = try {
            ResourcesCompat.getFont(context, R.font.bricolage_grotesque) ?: Typeface.SANS_SERIF
        } catch (_: Exception) {
            Typeface.SANS_SERIF
        }
        val regularFont = Typeface.create(baseTypeface, Typeface.NORMAL)
        val boldFont = Typeface.create(baseTypeface, Typeface.BOLD)

        val margin = 30f
        val contentWidth = pageWidth - margin * 2

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorBg
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorCard
            style = Paint.Style.FILL
        }
        val cardAltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorCardAlt
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorBorder
            style = Paint.Style.STROKE
            strokeWidth = 1.1f
        }

        val profile = RegulatoryProfile.byId(session.profileId)
        val profileLabel = when (profile.id) {
            "osha" -> "OSHA PEL (90 dBA / 8h • Tasa 5 dB • Umbral 90 dBA)"
            "osha_hc" -> "OSHA HC (90 dBA / 8h • Tasa 5 dB • Umbral 80 dBA)"
            "iso9612" -> "ISO 9612 (85 dBA / 8h • Tasa 3 dB • Sin umbral)"
            "col_res1792" -> "Res. 1792/1990 Colombia (85 dBA / 8h • Tasa 5 dB • Umbral 80 dBA)"
            else -> "NIOSH REL (85 dBA / 8h • Tasa 3 dB • Umbral 80 dBA)"
        }
        val profileShort = profile.name

        val startMs = session.startedAtEpochMs
        val endMs = session.endedAtEpochMs ?: System.currentTimeMillis()
        val durationSec = if (session.durationSeconds > 0.0) {
            session.durationSeconds
        } else {
            ((endMs - startMs).coerceAtLeast(1000L)) / 1000.0
        }
        val durationMs = (durationSec * 1000.0).toLong()
        val durationFormatted = formatDurationHms(durationMs)
        val durationHoursDec = durationSec / 3600.0

        val selValue = if (session.sel > 0.0) {
            session.sel
        } else if (session.laeq > 0.0 && durationSec > 0.0) {
            session.laeq + 10.0 * log10(durationSec)
        } else 0.0
        val lasMinValue = if (session.lasMin > 0.0) session.lasMin else session.laeq
        val l10Value = if (session.l10 > 0.0) session.l10 else session.lasMax
        val l50Value = if (session.l50 > 0.0) session.l50 else session.laeq
        val l90Value = if (session.l90 > 0.0) session.l90 else lasMinValue
        val remainingDose = (100.0 - session.dosePercent).coerceAtLeast(0.0)
        val projectedDose8h = if (durationHoursDec > 1e-6) session.dosePercent * (8.0 / durationHoursDec) else 0.0
        val soundExposurePa2h = 4e-10 * durationHoursDec * 10.0.pow(session.laeq / 10.0)
        val twaString = if (session.twa.isFinite() && session.twa > 0.0) "${"%.1f".format(session.twa)} dBA" else "0.0 dBA"

        var y = 26f

        // 2. HEADER
        val headerRect = RectF(margin, y, margin + contentWidth, y + 58f)
        canvas.drawRoundRect(headerRect, 14f, 14f, cardPaint)
        canvas.drawRoundRect(headerRect, 14f, 14f, borderPaint)

        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldFont
            color = colorTextPrimary
            textSize = 19f
            letterSpacing = 0.12f
        }
        canvas.drawText("LEVELAPP", margin + 18f, y + 27f, brandPaint)

        val subBrandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularFont
            color = colorTextSecondary
            textSize = 9f
        }
        canvas.drawText("REPORTE DE DOSIMETRÍA ACÚSTICA OCUPACIONAL", margin + 18f, y + 43f, subBrandPaint)

        val badgeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldFont
            color = Color.WHITE
            textSize = 10f
        }
        val badgeText = "CRITERIO: $profileShort"
        val badgeW = badgeTextPaint.measureText(badgeText) + 26f
        val badgeRect = RectF(headerRect.right - badgeW - 14f, y + 14f, headerRect.right - 14f, y + 44f)
        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorPeriwinkle
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(badgeRect, 14f, 14f, badgeBgPaint)
        canvas.drawText(badgeText, badgeRect.left + 13f, badgeRect.centerY() + 3.5f, badgeTextPaint)

        y = headerRect.bottom + 12f

        // 3. TARJETA PRINCIPAL DE LA MEDICIÓN (Título, Dosis Acumulada, SEL (LAE) y Resumen)
        val heroRect = RectF(margin, y, margin + contentWidth, y + 134f)
        canvas.drawRoundRect(heroRect, 14f, 14f, cardPaint)
        canvas.drawRoundRect(heroRect, 14f, 14f, borderPaint)

        val labelSmallPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldFont
            color = colorPeriwinkle
            textSize = 8.5f
            letterSpacing = 0.08f
        }
        canvas.drawText("NOMBRE DE LA MEDICIÓN", margin + 18f, y + 20f, labelSmallPaint)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldFont
            color = colorTextPrimary
            textSize = 15.5f
        }
        val displayTitle = session.title.ifBlank { "Sesión #${session.id}" }
        canvas.drawText(ellipsize(displayTitle, titlePaint, contentWidth * 0.56f), margin + 18f, y + 40f, titlePaint)

        val normDetailPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularFont
            color = colorTextSecondary
            textSize = 9.5f
        }
        canvas.drawText("Criterio: $profileLabel", margin + 18f, y + 56f, normDetailPaint)
        canvas.drawText(
            "Exposición restante permitida: ${"%.2f".format(remainingDose)}%  •  Proyección 8h: ${"%.2f".format(projectedDose8h)}%",
            margin + 18f,
            y + 70f,
            normDetailPaint
        )

        val doseColor = when {
            session.dosePercent >= 100.0 -> colorRed
            session.dosePercent >= 50.0 -> colorAmber
            else -> colorPeriwinkle
        }
        val statusLabel = when {
            session.dosePercent >= 100.0 -> "RIESGO ALTO (≥ 100%)"
            session.dosePercent >= 50.0 -> "PRECAUCIÓN (≥ 50%)"
            else -> "EXPOSICIÓN DENTRO DEL LÍMITE"
        }

        val doseRightLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldFont
            color = colorTextSecondary
            textSize = 9f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("DOSIS ACUMULADA ($profileShort)", heroRect.right - 18f, y + 20f, doseRightLabelPaint)

        val doseValuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldFont
            color = doseColor
            textSize = 25f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("${"%.2f".format(session.dosePercent)}%", heroRect.right - 18f, y + 47f, doseValuePaint)

        val statusPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldFont
            color = doseColor
            textSize = 8.5f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(statusLabel, heroRect.right - 18f, y + 61f, statusPaint)

        // Barra de progreso de dosis
        val barTop = y + 80f
        val barRect = RectF(margin + 18f, barTop, heroRect.right - 18f, barTop + 9f)
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(barRect, 4.5f, 4.5f, trackPaint)

        val progressFraction = (session.dosePercent / 100.0).coerceIn(0.0, 1.0).toFloat()
        if (progressFraction > 0f) {
            val fillWidth = (barRect.width() * progressFraction).coerceAtLeast(9f)
            val fillRect = RectF(barRect.left, barRect.top, barRect.left + fillWidth, barRect.bottom)
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = doseColor
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(fillRect, 4.5f, 4.5f, fillPaint)
        }

        // Indicadores rápidos con SEL (LAE)
        val miniLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularFont
            color = colorTextSecondary
            textSize = 9f
        }
        val miniValPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldFont
            color = colorTextPrimary
            textSize = 9.5f
        }
        val colW = (contentWidth - 36f) / 4f
        val row1Y = y + 106f
        val row2Y = y + 122f

        drawInlineMetric(canvas, margin + 18f + colW * 0, row1Y, "LAeq: ", "${"%.1f".format(session.laeq)} dBA", miniLabelPaint, miniValPaint)
        drawInlineMetric(canvas, margin + 18f + colW * 1, row1Y, "SEL (LAE): ", "${"%.1f".format(selValue)} dBA", miniLabelPaint, miniValPaint)
        drawInlineMetric(canvas, margin + 18f + colW * 2, row1Y, "LEX,8h/TWA: ", twaString, miniLabelPaint, miniValPaint)
        drawInlineMetric(canvas, margin + 18f + colW * 3, row1Y, "Tiempo: ", durationFormatted, miniLabelPaint, miniValPaint)

        drawInlineMetric(canvas, margin + 18f + colW * 0, row2Y, "LASmax: ", "${"%.1f".format(session.lasMax)} dBA", miniLabelPaint, miniValPaint)
        drawInlineMetric(canvas, margin + 18f + colW * 1, row2Y, "LASmin: ", "${"%.1f".format(lasMinValue)} dBA", miniLabelPaint, miniValPaint)
        drawInlineMetric(canvas, margin + 18f + colW * 2, row2Y, "L10/L50/L90: ", "${"%.0f".format(l10Value)}/${"%.0f".format(l50Value)}/${"%.0f".format(l90Value)} dB", miniLabelPaint, miniValPaint)
        drawInlineMetric(canvas, margin + 18f + colW * 3, row2Y, "EA: ", "${"%.3f".format(soundExposurePa2h)} Pa²h", miniLabelPaint, miniValPaint)

        y = heroRect.bottom + 14f

        // 4. TABLA DE REGISTRO OFICIAL
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val dateStr = dateFormat.format(Date(startMs))
        val startTimeStr = timeFormat.format(Date(startMs))
        val endTimeStr = timeFormat.format(Date(endMs))

        val areaValue = session.area.trim().ifEmpty { "No especificada" }
        val workerNameValue = session.workerName.trim().ifEmpty { "No especificado" }
        val workerRoleValue = session.workerRole.trim().ifEmpty { "No especificado" }
        val workerAndRole = "$workerNameValue / $workerRoleValue"
        val scheduleValue = "Inicio: $startTimeStr  —  Fin: $endTimeStr  —  Duración: ${"%.2f".format(durationHoursDec)} h ($durationFormatted)"

        val nioshPct = if (session.doseNiosh > 0.0 || session.profileId != "niosh") session.doseNiosh else session.dosePercent
        val oshaPelPct = if (session.doseOsha > 0.0 || session.profileId != "osha") session.doseOsha else session.dosePercent
        val oshaHcPct = if (session.doseOshaHc > 0.0 || session.profileId != "osha_hc") session.doseOshaHc else session.dosePercent
        val isoPct = if (session.doseIso > 0.0 || session.profileId != "iso9612") session.doseIso else session.dosePercent
        val colPct = if (session.doseColRes1792 > 0.0 || session.profileId != "col_res1792") session.doseColRes1792 else session.dosePercent

        val equipmentResultsValue =
            "LAeq: ${"%.1f".format(session.laeq)} dBA  |  SEL (LAE): ${"%.1f".format(selValue)} dBA  |  Dosis: ${"%.2f".format(session.dosePercent)}% (Restante: ${"%.2f".format(remainingDose)}%)  |  LEX,8h/TWA: $twaString  |  LASmax: ${"%.1f".format(session.lasMax)} dBA"
        val statisticalParamsValue =
            "LASmin: ${"%.1f".format(lasMinValue)} dBA  |  L10: ${"%.1f".format(l10Value)} dBA  |  L50: ${"%.1f".format(l50Value)} dBA  |  L90 (Fondo): ${"%.1f".format(l90Value)} dBA  |  EA: ${"%.3f".format(soundExposurePa2h)} Pa²·h\n" +
                "NIOSH: ${"%.2f".format(nioshPct)}% • OSHA PEL: ${"%.2f".format(oshaPelPct)}% • OSHA HC: ${"%.2f".format(oshaHcPct)}% • ISO 9612: ${"%.2f".format(isoPct)}% • Res. 1792 (Col): ${"%.2f".format(colPct)}%"

        val notesValue = session.notes.trim().ifEmpty { "Sin descripción u observaciones adicionales registradas." }
        val evaluatorValue = session.evaluatorName.trim().ifEmpty { "Evaluador SST" }

        val tableLeft = margin
        val tableRight = margin + contentWidth
        val colSplitX = tableLeft + 150f

        val tableHeaderH = 32f
        val headerTableRect = RectF(tableLeft, y, tableRight, y + tableHeaderH)
        val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorPeriwinkleBg
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(headerTableRect, 10f, 10f, headerBgPaint)
        canvas.drawRoundRect(headerTableRect, 10f, 10f, borderPaint)

        val tableHeaderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldFont
            color = colorPeriwinkle
            textSize = 10.5f
        }
        canvas.drawText("Campo", tableLeft + 14f, y + 20f, tableHeaderPaint)
        canvas.drawText("Registro / Dato a diligenciar", colSplitX + 14f, y + 20f, tableHeaderPaint)
        canvas.drawLine(colSplitX, y, colSplitX, y + tableHeaderH, borderPaint)

        y += tableHeaderH + 4f

        val fieldTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldFont
            color = colorTextPrimary
            textSize = 10f
        }
        val fieldValuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularFont
            color = colorTextPrimary
            textSize = 9.8f
        }
        val fieldValueHighlightPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldFont
            color = colorPeriwinkle
            textSize = 9.8f
        }

        val rows = listOf(
            Triple("Área", areaValue, false),
            Triple("Fecha de medición", dateStr, false),
            Triple("Trabajador / Cargo", workerAndRole, false),
            Triple("Horario de medición", scheduleValue, false),
            Triple("Resultados del equipo", equipmentResultsValue, true),
            Triple("Estadística y Perfiles", statisticalParamsValue, false)
        )

        val valueColWidth = (tableRight - colSplitX - 26f).toInt()

        rows.forEachIndexed { index, (fieldLabel, fieldVal, highlight) ->
            val vPaint = if (highlight) fieldValueHighlightPaint else fieldValuePaint
            val valLayout = buildStaticLayout(fieldVal, vPaint, valueColWidth)
            val rowHeight = maxOf(34f, valLayout.height + 16f)
            val rowRect = RectF(tableLeft, y, tableRight, y + rowHeight)

            canvas.drawRoundRect(rowRect, 8f, 8f, if (index % 2 == 0) cardPaint else cardAltPaint)
            canvas.drawRoundRect(rowRect, 8f, 8f, borderPaint)
            canvas.drawLine(colSplitX, y, colSplitX, y + rowHeight, borderPaint)

            canvas.drawText(fieldLabel, tableLeft + 12f, y + rowHeight / 2f + 3.5f, fieldTitlePaint)

            canvas.save()
            canvas.translate(colSplitX + 12f, y + (rowHeight - valLayout.height) / 2f)
            valLayout.draw(canvas)
            canvas.restore()

            y += rowHeight + 4f
        }

        // 5. FILA ESPECIAL: Observaciones y firmas
        val notesLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldFont
            color = colorTextSecondary
            textSize = 9f
        }
        val notesTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularFont
            color = colorTextPrimary
            textSize = 9.5f
        }
        val notesLayout = buildStaticLayout(notesValue, notesTextPaint, valueColWidth)
        val signatureBoxHeight = 72f
        val obsRowHeight = maxOf(130f, notesLayout.height + signatureBoxHeight + 40f)
        val obsRect = RectF(tableLeft, y, tableRight, y + obsRowHeight)

        canvas.drawRoundRect(obsRect, 10f, 10f, cardPaint)
        canvas.drawRoundRect(obsRect, 10f, 10f, borderPaint)
        canvas.drawLine(colSplitX, y, colSplitX, y + obsRowHeight, borderPaint)

        val obsTitleLayout = buildStaticLayout("Observaciones y firmas", fieldTitlePaint, (colSplitX - tableLeft - 20f).toInt())
        canvas.save()
        canvas.translate(tableLeft + 12f, y + 16f)
        obsTitleLayout.draw(canvas)
        canvas.restore()

        var innerY = y + 12f
        canvas.drawText("Descripción / Observaciones:", colSplitX + 12f, innerY + 9f, notesLabelPaint)
        innerY += 13f
        canvas.save()
        canvas.translate(colSplitX + 12f, innerY)
        notesLayout.draw(canvas)
        canvas.restore()

        val sigAreaWidth = tableRight - colSplitX - 24f
        val sigBoxW = (sigAreaWidth - 12f) / 2f
        val sigBoxTop = obsRect.bottom - signatureBoxHeight - 10f
        val sigBox1 = RectF(colSplitX + 12f, sigBoxTop, colSplitX + 12f + sigBoxW, sigBoxTop + signatureBoxHeight)
        val sigBox2 = RectF(sigBox1.right + 12f, sigBoxTop, sigBox1.right + 12f + sigBoxW, sigBoxTop + signatureBoxHeight)

        drawSignatureBox(
            canvas = canvas,
            rect = sigBox1,
            title = "Firma del trabajador",
            subtitle = "$workerNameValue ($workerRoleValue)",
            cardAltPaint = cardAltPaint,
            borderPaint = borderPaint,
            boldFont = boldFont,
            regularFont = regularFont
        )
        drawSignatureBox(
            canvas = canvas,
            rect = sigBox2,
            title = "Firma y Licencia SST del evaluador",
            subtitle = evaluatorValue,
            cardAltPaint = cardAltPaint,
            borderPaint = borderPaint,
            boldFont = boldFont,
            regularFont = regularFont
        )

        // 6. PIE DE PÁGINA
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularFont
            color = colorTextSecondary
            textSize = 8f
        }
        val footerY = pageHeight - 18f
        canvas.drawText(
            "LevelApp • Sesión #${session.id} • Reporte técnico de dosimetría acústica (LAeq, SEL/LAE, LEX,8h/TWA, Percentiles L10-L90). Rango: 35–110 dB(A).",
            margin,
            footerY,
            footerPaint
        )

        pdfDocument.finishPage(page)

        val exportDir = File(context.cacheDir, "LevelApp_Exportaciones").apply { mkdirs() }
        val safeTitle = session.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "Reporte_Sesion_${session.id}" }
        val outputFile = File(exportDir, "${safeTitle}_${session.id}.pdf")

        FileOutputStream(outputFile).use { stream ->
            pdfDocument.writeTo(stream)
        }
        pdfDocument.close()
        return outputFile
    }

    private fun drawInlineMetric(
        canvas: Canvas,
        x: Float,
        y: Float,
        label: String,
        value: String,
        labelPaint: TextPaint,
        valPaint: TextPaint
    ) {
        canvas.drawText(label, x, y, labelPaint)
        val offset = labelPaint.measureText(label)
        canvas.drawText(value, x + offset, y, valPaint)
    }

    private fun drawSignatureBox(
        canvas: Canvas,
        rect: RectF,
        title: String,
        subtitle: String,
        cardAltPaint: Paint,
        borderPaint: Paint,
        boldFont: Typeface,
        regularFont: Typeface
    ) {
        canvas.drawRoundRect(rect, 8f, 8f, cardAltPaint)
        canvas.drawRoundRect(rect, 8f, 8f, borderPaint)

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorTextSecondary
            strokeWidth = 1f
        }
        val lineY = rect.top + 38f
        canvas.drawLine(rect.left + 10f, lineY, rect.right - 10f, lineY, linePaint)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldFont
            color = colorTextPrimary
            textSize = 8.5f
        }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularFont
            color = colorTextSecondary
            textSize = 7.8f
        }
        val maxTextW = rect.width() - 18f
        canvas.drawText(ellipsize(title, titlePaint, maxTextW), rect.left + 9f, lineY + 13f, titlePaint)
        canvas.drawText(ellipsize(subtitle, subPaint, maxTextW), rect.left + 9f, lineY + 25f, subPaint)
    }

    private fun buildStaticLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(40))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(2f, 1.05f)
            .setIncludePad(false)
            .build()
    }

    private fun ellipsize(text: String, paint: TextPaint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "...") > maxWidth) {
            end--
        }
        return text.substring(0, end) + "..."
    }

    private fun formatDurationHms(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }
}
