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
import com.levelapp.core.dosimetry.DoseCalculator
import com.levelapp.core.dosimetry.RegulatoryProfile
import com.levelapp.data.database.SessionEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10

class PdfReportExporter(private val context: Context) {

    private val colorBg = Color.parseColor("#FFFFFF")
    private val colorCard = Color.parseColor("#F8FAFC")
    private val colorCardAlt = Color.parseColor("#FFFFFF")
    private val colorBorder = Color.parseColor("#CBD5E1")
    private val colorTextPrimary = Color.parseColor("#0F172A")
    private val colorTextSecondary = Color.parseColor("#475569")
    private val colorPeriwinkle = Color.parseColor("#5B60F6")
    private val colorPeriwinkleBg = Color.parseColor("#EEF0FF")
    private val colorGreen = Color.parseColor("#16A34A")
    private val colorAmber = Color.parseColor("#D97706")
    private val colorAmberBg = Color.parseColor("#FFFBEB")
    private val colorRed = Color.parseColor("#DC2626")

    fun generateSessionPdf(session: SessionEntity): File {
        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 28f
        val contentWidth = pageWidth - margin * 2
        val footerLimitY = pageHeight - 30f

        val baseTypeface = try {
            ResourcesCompat.getFont(context, R.font.bricolage_grotesque) ?: Typeface.SANS_SERIF
        } catch (_: Exception) {
            Typeface.SANS_SERIF
        }
        val regularFont = Typeface.create(baseTypeface, Typeface.NORMAL)
        val boldFont = Typeface.create(baseTypeface, Typeface.BOLD)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorBg; style = Paint.Style.FILL }
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorCard; style = Paint.Style.FILL }
        val cardAltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorCardAlt; style = Paint.Style.FILL }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorBorder
            style = Paint.Style.STROKE
            strokeWidth = 1.0f
        }
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularFont
            color = colorTextSecondary
            textSize = 7.8f
        }

        var pageNum = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = page.canvas
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)

        fun drawFooter() {
            canvas.drawText(
                "LevelApp • Sesión #${session.id} (Pág. $pageNum) • Rango confiable 35–110 dB(A) • Ponderación A (IEC 61672-1) / Slow (1 s).",
                margin,
                pageHeight - 14f,
                footerPaint
            )
        }

        var y = 22f
        fun ensureSpace(neededHeight: Float) {
            if (y + neededHeight > footerLimitY) {
                drawFooter()
                pdfDocument.finishPage(page)
                pageNum++
                page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)
                y = 24f
            }
        }

        val profile = RegulatoryProfile.byId(session.profileId)
        val profileLabel = when (profile.id) {
            "osha" -> "OSHA PEL (90 dBA / 8h • Tasa 5 dB • Umbral 90 dBA)"
            "osha_hc" -> "OSHA HC (90 dBA / 8h • Tasa 5 dB • Umbral 80 dBA)"
            "iso9612" -> "ISO 9612 (85 dBA / 8h • Tasa 3 dB • Sin umbral)"
            "col_res1792" -> "COLOMBIA - Res. 1792/1990 (85 dBA / 8h • Tasa 5 dB • Umbral 80 dBA)"
            else -> "NIOSH REL (85 dBA / 8h • Tasa 3 dB • Umbral 80 dBA)"
        }

        // Duración real medida (measuredSeconds excluye pausas)
        val startMs = session.startedAtEpochMs
        val endMs = session.endedAtEpochMs ?: System.currentTimeMillis()
        val measuredSec = if (session.durationSeconds > 0.0) {
            session.durationSeconds
        } else {
            ((endMs - startMs).coerceAtLeast(1000L)) / 1000.0
        }
        val wallSec = ((endMs - startMs).coerceAtLeast(0L)) / 1000.0
        val measuredFormatted = formatDurationHms((measuredSec * 1000.0).toLong())
        val wallFormatted = formatDurationHms((wallSec * 1000.0).toLong())
        val measuredHours = measuredSec / 3600.0

        val selValue = if (session.sel > 0.0) {
            session.sel
        } else if (session.laeq > 0.0 && measuredSec > 0.0) {
            session.laeq + 10.0 * log10(measuredSec)
        } else 0.0
        val lex8hValue = DoseCalculator.lex8h(session.laeq, measuredSec)
        val lex8hStr = if (lex8hValue.isFinite()) "${"%.1f".format(Locale.US, lex8hValue)} dBA" else "--"
        val eaPa2h = if (session.soundExposurePa2h > 0.0) {
            session.soundExposurePa2h
        } else {
            DoseCalculator.soundExposurePa2hFromLaeq(session.laeq, measuredSec)
        }
        val eaStr = DoseCalculator.formatSoundExposure(eaPa2h)
        val projectedDose8h = DoseCalculator.projectedDose8hFromDose(session.dosePercent, measuredSec, profile)
        val twaStr = DoseCalculator.formatTwa(session.dosePercent, profile)

        val lasMinValue = if (session.lasMin > 0.0) session.lasMin else session.laeq
        val l10Value = if (session.l10 > 0.0) session.l10 else session.lasMax
        val l50Value = if (session.l50 > 0.0) session.l50 else session.laeq
        val l90Value = if (session.l90 > 0.0) session.l90 else lasMinValue

        // 1. HEADER
        val headerRect = RectF(margin, y, margin + contentWidth, y + 48f)
        canvas.drawRoundRect(headerRect, 12f, 12f, cardPaint)
        canvas.drawRoundRect(headerRect, 12f, 12f, borderPaint)

        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldFont; color = colorTextPrimary; textSize = 17f; letterSpacing = 0.12f
        }
        canvas.drawText("LEVELAPP", margin + 16f, y + 22f, brandPaint)

        val subBrandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularFont; color = colorTextSecondary; textSize = 8.5f
        }
        canvas.drawText("REPORTE DE DOSIMETRÍA ACÚSTICA OCUPACIONAL", margin + 16f, y + 36f, subBrandPaint)

        val badgeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldFont; color = Color.WHITE; textSize = 9.5f
        }
        val badgeText = "CRITERIO: ${profile.name}"
        val badgeW = badgeTextPaint.measureText(badgeText) + 22f
        val badgeRect = RectF(headerRect.right - badgeW - 12f, y + 11f, headerRect.right - 12f, y + 37f)
        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorPeriwinkle; style = Paint.Style.FILL }
        canvas.drawRoundRect(badgeRect, 13f, 13f, badgeBgPaint)
        canvas.drawText(badgeText, badgeRect.left + 11f, badgeRect.centerY() + 3.5f, badgeTextPaint)

        y = headerRect.bottom + 8f

        // 2. HERO: Nombre, Dosis Acumulada, Frase Amigable y Barra
        val heroRect = RectF(margin, y, margin + contentWidth, y + 84f)
        canvas.drawRoundRect(heroRect, 12f, 12f, cardPaint)
        canvas.drawRoundRect(heroRect, 12f, 12f, borderPaint)

        val labelSmallPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldFont; color = colorPeriwinkle; textSize = 8f; letterSpacing = 0.08f
        }
        canvas.drawText("NOMBRE DE LA MEDICIÓN", margin + 16f, y + 17f, labelSmallPaint)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldFont; color = colorTextPrimary; textSize = 14f
        }
        val displayTitle = session.title.ifBlank { "Sesión #${session.id}" }
        canvas.drawText(ellipsize(displayTitle, titlePaint, contentWidth * 0.56f), margin + 16f, y + 35f, titlePaint)

        val normDetailPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularFont; color = colorTextSecondary; textSize = 8.8f
        }
        canvas.drawText(profileLabel, margin + 16f, y + 49f, normDetailPaint)

        val doseColor = when {
            session.dosePercent >= 100.0 -> colorRed
            session.dosePercent >= 50.0 -> colorAmber
            else -> colorGreen
        }
        val statusLabel = when {
            session.dosePercent >= 100.0 -> "Límite superado: usa protección y reduce la exposición"
            session.dosePercent >= 50.0 -> "Cerca del límite: usa protección auditiva"
            else -> "Exposición aceptable"
        }

        val doseRightLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldFont; color = colorTextSecondary; textSize = 8.5f; textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("DOSIS ACUMULADA (${profile.name})", heroRect.right - 16f, y + 17f, doseRightLabelPaint)

        val doseValuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldFont; color = doseColor; textSize = 22f; textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("${"%.2f".format(Locale.US, session.dosePercent)}%", heroRect.right - 16f, y + 42f, doseValuePaint)

        val statusPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldFont; color = doseColor; textSize = 8.2f; textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(statusLabel, heroRect.right - 16f, y + 55f, statusPaint)

        val barTop = y + 64f
        val barRect = RectF(margin + 16f, barTop, heroRect.right - 16f, barTop + 8f)
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E2E8F0"); style = Paint.Style.FILL }
        canvas.drawRoundRect(barRect, 4f, 4f, trackPaint)
        val progressFraction = (session.dosePercent / 100.0).coerceIn(0.0, 1.0).toFloat()
        if (progressFraction > 0f) {
            val fillWidth = (barRect.width() * progressFraction).coerceAtLeast(8f)
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = doseColor; style = Paint.Style.FILL }
            canvas.drawRoundRect(RectF(barRect.left, barRect.top, barRect.left + fillWidth, barRect.bottom), 4f, 4f, fillPaint)
        }

        y = heroRect.bottom + 8f

        // 3. BLOQUE DE RESULTADOS EN ORDEN EXACTO (Req 8):
        // Criterio elegido -> Dosis -> TWA -> Proyección 8 h -> L_EX,8h -> LAeq -> SEL (LAE) -> LASmax -> L10/L50/L90 -> E_A
        val resCardH = 104f
        val resRect = RectF(margin, y, margin + contentWidth, y + resCardH)
        canvas.drawRoundRect(resRect, 12f, 12f, cardPaint)
        canvas.drawRoundRect(resRect, 12f, 12f, borderPaint)

        canvas.drawText("RESULTADOS DE LA MEDICIÓN", margin + 16f, y + 16f, labelSmallPaint)

        val mLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularFont; color = colorTextSecondary; textSize = 8.6f
        }
        val mValPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldFont; color = colorTextPrimary; textSize = 8.8f
        }
        val leftX = margin + 16f
        val rightX = margin + contentWidth / 2f + 8f
        val leftItems = listOf(
            "1. Criterio elegido: " to profile.name,
            "2. Dosis acumulada: " to "${"%.2f".format(Locale.US, session.dosePercent)} %",
            "3. TWA del criterio: " to twaStr,
            "4. Proyección 8 h: " to "${"%.2f".format(Locale.US, projectedDose8h)} %",
            "5. L_EX,8h (ISO 9612): " to lex8hStr,
        )
        val rightItems = listOf(
            "6. LAeq,T: " to "${"%.1f".format(Locale.US, session.laeq)} dBA",
            "7. SEL (LAE): " to "${"%.1f".format(Locale.US, selValue)} dBA",
            "8. LASmax / LASmin: " to "${"%.1f".format(Locale.US, session.lasMax)} / ${"%.1f".format(Locale.US, lasMinValue)} dBA",
            "9. L10 / L50 / L90: " to "${"%.1f".format(Locale.US, l10Value)} / ${"%.1f".format(Locale.US, l50Value)} / ${"%.1f".format(Locale.US, l90Value)} dBA",
            "10. Exposición E_A: " to eaStr,
        )
        for (i in leftItems.indices) {
            val rowY = y + 32f + i * 14f
            drawInlineMetric(canvas, leftX, rowY, leftItems[i].first, leftItems[i].second, mLabelPaint, mValPaint, contentWidth / 2f - 20f)
            drawInlineMetric(canvas, rightX, rowY, rightItems[i].first, rightItems[i].second, mLabelPaint, mValPaint, contentWidth / 2f - 20f)
        }

        y = resRect.bottom + 8f

        // 4. TABLA COMPARATIVA DE LOS 5 PERFILES (Req 9: Dosis y TWA de cada uno)
        val compHeaderH = 22f
        val compRowH = 17f
        val totalCompH = compHeaderH + 5 * compRowH + 18f
        ensureSpace(totalCompH)

        canvas.drawText("COMPARATIVA DE CRITERIOS (DOSIS Y TWA)", margin + 4f, y + 10f, labelSmallPaint)
        y += 14f

        val colP = margin
        val colC = margin + 125f
        val colD = margin + 320f
        val colT = margin + 405f
        val compRight = margin + contentWidth

        val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorPeriwinkleBg; style = Paint.Style.FILL }
        val compHeaderRect = RectF(colP, y, compRight, y + compHeaderH)
        canvas.drawRoundRect(compHeaderRect, 8f, 8f, headerBgPaint)
        canvas.drawRoundRect(compHeaderRect, 8f, 8f, borderPaint)

        val compHdrPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = boldFont; color = colorPeriwinkle; textSize = 8.8f }
        canvas.drawText("Perfil", colP + 10f, y + 14.5f, compHdrPaint)
        canvas.drawText("Parámetros (Lc / q / Umbral)", colC + 8f, y + 14.5f, compHdrPaint)
        canvas.drawText("Dosis (%)", colD + 8f, y + 14.5f, compHdrPaint)
        canvas.drawText("TWA del perfil", colT + 8f, y + 14.5f, compHdrPaint)
        y += compHeaderH + 2f

        val compRowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = regularFont; color = colorTextPrimary; textSize = 8.4f }
        val compBoldPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = boldFont; color = colorTextPrimary; textSize = 8.4f }

        val fiveProfiles = listOf(
            Triple(RegulatoryProfile.nioshDefault(), resolveDose(session, "niosh"), "85 dBA / q=3 dB / Umbral 80 dBA"),
            Triple(RegulatoryProfile.oshaPelDefault(), resolveDose(session, "osha"), "90 dBA / q=5 dB / Umbral 90 dBA"),
            Triple(RegulatoryProfile.oshaHcDefault(), resolveDose(session, "osha_hc"), "90 dBA / q=5 dB / Umbral 80 dBA"),
            Triple(RegulatoryProfile.iso9612Default(), resolveDose(session, "iso9612"), "85 dBA / q=3 dB / Sin umbral"),
            Triple(RegulatoryProfile.colombiaRes1792Default(), resolveDose(session, "col_res1792"), "85 dBA / q=5 dB / Umbral 80 dBA"),
        )
        fiveProfiles.forEachIndexed { idx, (prof, pct, desc) ->
            val rRect = RectF(colP, y, compRight, y + compRowH)
            canvas.drawRoundRect(rRect, 5f, 5f, if (idx % 2 == 0) cardPaint else cardAltPaint)
            canvas.drawRoundRect(rRect, 5f, 5f, borderPaint)
            val isSelected = prof.id == profile.id
            canvas.drawText(prof.name, colP + 10f, y + 11.8f, if (isSelected) compBoldPaint else compRowPaint)
            canvas.drawText(desc, colC + 8f, y + 11.8f, compRowPaint)
            canvas.drawText("${"%.2f".format(Locale.US, pct)}%", colD + 8f, y + 11.8f, if (isSelected) compBoldPaint else compRowPaint)
            canvas.drawText(ellipsize(DoseCalculator.formatTwa(pct, prof), compRowPaint, compRight - colT - 14f), colT + 8f, y + 11.8f, compRowPaint)
            y += compRowH + 2f
        }

        y += 6f

        // 5. TABLA DE REGISTRO OCUPACIONAL + OBSERVACIONES Y FIRMAS
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val dateStr = dateFormat.format(Date(startMs))
        val startTimeStr = timeFormat.format(Date(startMs))
        val endTimeStr = timeFormat.format(Date(endMs))

        val areaValue = session.area.trim().ifEmpty { "No especificada" }
        val workerNameValue = session.workerName.trim().ifEmpty { "No especificado" }
        val workerRoleValue = session.workerRole.trim().ifEmpty { "No especificado" }
        val workerAndRole = "$workerNameValue / $workerRoleValue"

        val scheduleValue = if (session.hasPauses || (wallSec - measuredSec >= 2.0)) {
            val pausedSec = if (session.pausedSeconds > 0.0) session.pausedSeconds else (wallSec - measuredSec).coerceAtLeast(0.0)
            val pausedFmt = formatDurationHms((pausedSec * 1000.0).toLong())
            "Inicio: $startTimeStr — Fin: $endTimeStr\nTiempo medido efectivo: ${"%.2f".format(Locale.US, measuredHours)} h ($measuredFormatted)  |  Tiempo total reloj: $wallFormatted (Pausas: $pausedFmt)"
        } else {
            "Inicio: $startTimeStr — Fin: $endTimeStr — Duración medida: ${"%.2f".format(Locale.US, measuredHours)} h ($measuredFormatted)"
        }

        val tableLeft = margin
        val tableRight = margin + contentWidth
        val colSplitX = tableLeft + 135f
        val valueColWidth = (tableRight - colSplitX - 22f).toInt()

        val fieldTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = boldFont; color = colorTextPrimary; textSize = 9.2f }
        val fieldValuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = regularFont; color = colorTextPrimary; textSize = 9.0f }

        val metaRows = listOf(
            "Área" to areaValue,
            "Fecha de medición" to dateStr,
            "Trabajador / Cargo" to workerAndRole,
            "Horario de medición" to scheduleValue,
        )

        metaRows.forEachIndexed { idx, (label, valText) ->
            val valLayout = buildStaticLayout(valText, fieldValuePaint, valueColWidth)
            val rowH = maxOf(24f, valLayout.height + 10f)
            ensureSpace(rowH + 4f)
            val rRect = RectF(tableLeft, y, tableRight, y + rowH)
            canvas.drawRoundRect(rRect, 6f, 6f, if (idx % 2 == 0) cardPaint else cardAltPaint)
            canvas.drawRoundRect(rRect, 6f, 6f, borderPaint)
            canvas.drawLine(colSplitX, y, colSplitX, y + rowH, borderPaint)
            canvas.drawText(label, tableLeft + 10f, y + rowH / 2f + 3.2f, fieldTitlePaint)
            canvas.save()
            canvas.translate(colSplitX + 10f, y + (rowH - valLayout.height) / 2f)
            valLayout.draw(canvas)
            canvas.restore()
            y += rowH + 3f
        }

        // Observaciones y firmas
        val notesValue = session.notes.trim().ifEmpty { "Sin descripción u observaciones adicionales registradas." }
        val evaluatorValue = session.evaluatorName.trim().ifEmpty { "Evaluador SST" }
        val notesLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = boldFont; color = colorTextSecondary; textSize = 8.4f }
        val notesTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = regularFont; color = colorTextPrimary; textSize = 8.8f }
        val notesLayout = buildStaticLayout(notesValue, notesTextPaint, valueColWidth)
        val sigBoxH = 58f
        val obsRowH = maxOf(102f, notesLayout.height + sigBoxH + 30f)

        ensureSpace(obsRowH + 4f)
        val obsRect = RectF(tableLeft, y, tableRight, y + obsRowH)
        canvas.drawRoundRect(obsRect, 8f, 8f, cardPaint)
        canvas.drawRoundRect(obsRect, 8f, 8f, borderPaint)
        canvas.drawLine(colSplitX, y, colSplitX, y + obsRowH, borderPaint)

        val obsTitleLayout = buildStaticLayout("Observaciones y firmas", fieldTitlePaint, (colSplitX - tableLeft - 18f).toInt())
        canvas.save()
        canvas.translate(tableLeft + 10f, y + 14f)
        obsTitleLayout.draw(canvas)
        canvas.restore()

        var innerY = y + 9f
        canvas.drawText("Descripción / Observaciones:", colSplitX + 10f, innerY + 8f, notesLabelPaint)
        innerY += 12f
        canvas.save()
        canvas.translate(colSplitX + 10f, innerY)
        notesLayout.draw(canvas)
        canvas.restore()

        val sigAreaW = tableRight - colSplitX - 20f
        val sigBoxW = (sigAreaW - 10f) / 2f
        val sigTop = obsRect.bottom - sigBoxH - 8f
        drawSignatureBox(
            canvas, RectF(colSplitX + 10f, sigTop, colSplitX + 10f + sigBoxW, sigTop + sigBoxH),
            "Firma del trabajador", "$workerNameValue ($workerRoleValue)", cardAltPaint, borderPaint, boldFont, regularFont
        )
        drawSignatureBox(
            canvas, RectF(colSplitX + 20f + sigBoxW, sigTop, colSplitX + 20f + sigBoxW * 2f, sigTop + sigBoxH),
            "Firma y Licencia SST del evaluador", evaluatorValue, cardAltPaint, borderPaint, boldFont, regularFont
        )
        y = obsRect.bottom + 8f

        // 6. AVISOS DE CALIDAD DE LA MEDICIÓN CUANDO APLIQUEN (Req 10 + Req 4)
        val warnings = mutableListOf<String>()
        if (session.belowFloorSeconds > 0.0 && measuredSec > 0.0) {
            val pctBelow = (session.belowFloorSeconds / measuredSec) * 100.0
            if (pctBelow >= 0.1) {
                warnings += "• ${"%.1f".format(Locale.US, pctBelow)} % del tiempo por debajo del piso del micrófono (35 dBA): el LAeq real puede ser menor."
            }
        }
        if (session.saturationSeconds > 0.0) {
            val satMin = session.saturationSeconds / 60.0
            warnings += "• ${"%.2f".format(Locale.US, satMin)} min por encima de 110 dBA: posible saturación del micrófono, la dosis puede estar subestimada."
        }
        if (session.calibrationOffset == 0.0) {
            warnings += "• Sin calibración con sonómetro patrón (Offset = 0.0 dB)."
        }
        if (session.resumedNote.isNotBlank()) {
            warnings += "• ${session.resumedNote}"
        }

        if (warnings.isNotEmpty()) {
            val warnPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = regularFont; color = colorTextPrimary; textSize = 8.4f }
            val warnText = warnings.joinToString("\n")
            val warnLayout = buildStaticLayout(warnText, warnPaint, (contentWidth - 24f).toInt())
            val warnH = warnLayout.height + 24f
            ensureSpace(warnH)

            val warnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorAmberBg; style = Paint.Style.FILL }
            val warnBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorAmber; style = Paint.Style.STROKE; strokeWidth = 1f }
            val warnRect = RectF(margin, y, margin + contentWidth, y + warnH)
            canvas.drawRoundRect(warnRect, 8f, 8f, warnBgPaint)
            canvas.drawRoundRect(warnRect, 8f, 8f, warnBorderPaint)

            val warnHdrPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = boldFont; color = colorAmber; textSize = 8.2f }
            canvas.drawText("AVISOS DE CALIDAD DE LA MEDICIÓN", margin + 12f, y + 14f, warnHdrPaint)
            canvas.save()
            canvas.translate(margin + 12f, y + 19f)
            warnLayout.draw(canvas)
            canvas.restore()
            y = warnRect.bottom + 6f
        }

        drawFooter()
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

    private fun resolveDose(session: SessionEntity, profileId: String): Double = when (profileId) {
        "niosh" -> if (session.doseNiosh > 0.0 || session.profileId != "niosh") session.doseNiosh else session.dosePercent
        "osha" -> if (session.doseOsha > 0.0 || session.profileId != "osha") session.doseOsha else session.dosePercent
        "osha_hc" -> if (session.doseOshaHc > 0.0 || session.profileId != "osha_hc") session.doseOshaHc else session.dosePercent
        "iso9612" -> if (session.doseIso > 0.0 || session.profileId != "iso9612") session.doseIso else session.dosePercent
        "col_res1792" -> if (session.doseColRes1792 > 0.0 || session.profileId != "col_res1792") session.doseColRes1792 else session.dosePercent
        else -> 0.0
    }

    private fun drawInlineMetric(
        canvas: Canvas,
        x: Float,
        y: Float,
        label: String,
        value: String,
        labelPaint: TextPaint,
        valPaint: TextPaint,
        maxWidth: Float
    ) {
        canvas.drawText(label, x, y, labelPaint)
        val offset = labelPaint.measureText(label)
        val clippedVal = ellipsize(value, valPaint, (maxWidth - offset).coerceAtLeast(30f))
        canvas.drawText(clippedVal, x + offset, y, valPaint)
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
        canvas.drawRoundRect(rect, 7f, 7f, cardAltPaint)
        canvas.drawRoundRect(rect, 7f, 7f, borderPaint)

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTextSecondary; strokeWidth = 0.9f }
        val lineY = rect.top + 30f
        canvas.drawLine(rect.left + 8f, lineY, rect.right - 8f, lineY, linePaint)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = boldFont; color = colorTextPrimary; textSize = 8f }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = regularFont; color = colorTextSecondary; textSize = 7.4f }
        val maxTextW = rect.width() - 16f
        canvas.drawText(ellipsize(title, titlePaint, maxTextW), rect.left + 8f, lineY + 11f, titlePaint)
        canvas.drawText(ellipsize(subtitle, subPaint, maxTextW), rect.left + 8f, lineY + 21f, subPaint)
    }

    private fun buildStaticLayout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(40))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(1.5f, 1.05f)
            .setIncludePad(false)
            .build()

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
