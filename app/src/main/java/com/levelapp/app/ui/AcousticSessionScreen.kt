package com.levelapp.app.ui

import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.levelapp.app.utils.ShareUtils
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.mutableStateMapOf
import com.levelapp.app.R
import com.levelapp.app.csv.CsvReportExporter
import com.levelapp.app.pdf.PdfReportExporter
import com.levelapp.app.viewmodel.AcousticSessionViewModel
import com.levelapp.app.viewmodel.SessionUiState
import com.levelapp.core.dosimetry.DoseCalculator
import com.levelapp.core.dosimetry.RegulatoryProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// DARK THEME COLORS
val DarkBg = Color(0xFF101418)
val SurfaceCard = Color(0xFF1E232B)
val BorderSoft = Color(0xFF2C323C)
val TextPrimary = Color(0xFFE2E8F0)
val TextSecondary = Color(0xFF94A3B8)
val PeriwinklePrimary = Color(0xFF7A7EFF)
val PeriwinkleLight = Color(0xFF2D315E)
val GreenSafe = Color(0xFF22C55E)
val AmberAlert = Color(0xFFF59E0B)
val RedAlert = Color(0xFFF44336)

val BricolageGrotesque = FontFamily(
    Font(R.font.bricolage_grotesque, FontWeight.Normal),
    Font(R.font.bricolage_grotesque, FontWeight.Medium),
    Font(R.font.bricolage_grotesque, FontWeight.Bold),
    Font(R.font.bricolage_grotesque, FontWeight.ExtraBold)
)

@Composable
fun DosimetroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = PeriwinklePrimary,
            background = DarkBg,
            surface = SurfaceCard,
            onPrimary = Color.White,
            onBackground = TextPrimary,
            onSurface = TextPrimary
        ),
        // typography = AppTypography, // Using default to keep it simple, but we map font where needed
        content = content
    )
}

@Composable
fun AcousticSessionScreen(
    viewModel: AcousticSessionViewModel,
    onStartService: () -> Unit = {},
    onStopService: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val history by viewModel.history.collectAsState()
    val allSessions by viewModel.allSessions.collectAsState()
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator

    var showStopDialog by remember { mutableStateOf(false) }
    var sessionName by remember { mutableStateOf("") }
    var workerName by remember { mutableStateOf("") }
    var workerRole by remember { mutableStateOf("") }
    var areaName by remember { mutableStateOf("") }
    var evaluatorName by remember { mutableStateOf("") }
    var sessionNotes by remember { mutableStateOf("") }
    var showCalibration by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showInterpretationDialog by remember { mutableStateOf(false) }
    var showCalibrationReminder by remember { mutableStateOf(false) }
    var hasVibrated by remember { mutableStateOf(false) }
    
    var tutorialStep by remember { mutableIntStateOf(0) }
    val targetRects = remember { mutableStateMapOf<Int, Rect>() }

    val prefs = context.getSharedPreferences("levelapp_prefs", android.content.Context.MODE_PRIVATE)
    LaunchedEffect(Unit) {
        if (!prefs.getBoolean("tutorial_shown_v2", false)) {
            tutorialStep = 1
            prefs.edit().putBoolean("tutorial_shown_v2", true).apply()
        }
        sessionName = "Medición_${SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())}"
    }

    LaunchedEffect(showCalibrationReminder) {
        if (showCalibrationReminder) {
            delay(8000L)
            showCalibrationReminder = false
        }
    }

    LaunchedEffect(uiState.calibrationOffset) {
        if (uiState.calibrationOffset != 0.0) {
            showCalibrationReminder = false
        }
    }

    val isRunning = uiState.status == "Running"
    val isStopped = uiState.status == "Stopped"

    // Vibration Trigger
    LaunchedEffect(uiState.dose) {
        if (uiState.dose >= 100.0 && !hasVibrated) {
            hasVibrated = true
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(1000)
            }
        }
        if (uiState.dose < 100.0) {
            hasVibrated = false
        }
    }

    if (showStopDialog) {
        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PeriwinklePrimary,
            unfocusedBorderColor = BorderSoft,
            focusedLabelColor = PeriwinklePrimary,
            unfocusedLabelColor = TextSecondary,
            unfocusedTextColor = TextPrimary,
            focusedTextColor = TextPrimary
        )
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = {
                Text(
                    "Guardar sesión y datos del reporte",
                    color = TextPrimary,
                    fontFamily = BricolageGrotesque,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Dosis acumulada: ${"%.2f".format(uiState.dose)}% • LAeq: ${"%.1f".format(uiState.laeq)} dBA",
                        color = PeriwinklePrimary,
                        fontFamily = BricolageGrotesque,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = sessionName,
                        onValueChange = { sessionName = it },
                        label = { Text("Nombre de la medición", fontFamily = BricolageGrotesque) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors
                    )
                    OutlinedTextField(
                        value = workerName,
                        onValueChange = { workerName = it },
                        label = { Text("Nombre completo de la persona", fontFamily = BricolageGrotesque) },
                        placeholder = { Text("Ej. Juan Carlos Pérez", color = TextSecondary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors
                    )
                    OutlinedTextField(
                        value = workerRole,
                        onValueChange = { workerRole = it },
                        label = { Text("Cargo / GES", fontFamily = BricolageGrotesque) },
                        placeholder = { Text("Ej. Operario de Planta", color = TextSecondary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors
                    )
                    OutlinedTextField(
                        value = areaName,
                        onValueChange = { areaName = it },
                        label = { Text("Área / Lugar de la medición", fontFamily = BricolageGrotesque) },
                        placeholder = { Text("Ej. Taller de Metalmecánica", color = TextSecondary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors
                    )
                    OutlinedTextField(
                        value = evaluatorName,
                        onValueChange = { evaluatorName = it },
                        label = { Text("Evaluador / Licencia SST (opcional)", fontFamily = BricolageGrotesque) },
                        placeholder = { Text("Ej. Ing. SST - Lic. 12345", color = TextSecondary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors
                    )
                    OutlinedTextField(
                        value = sessionNotes,
                        onValueChange = { sessionNotes = it },
                        label = { Text("Descripción / Observaciones (opcional)", fontFamily = BricolageGrotesque) },
                        placeholder = { Text("Condiciones, fuentes de ruido, EPP usado...", color = TextSecondary) },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showStopDialog = false
                        viewModel.finishAndSaveSession(
                            workerName = workerName,
                            workerRole = workerRole,
                            area = areaName,
                            evaluator = evaluatorName,
                            notes = sessionNotes,
                            customTitle = sessionName
                        )
                        onStopService()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PeriwinklePrimary)
                ) {
                    Text("Guardar", color = Color.White, fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopDialog = false }) {
                    Text("Cancelar", color = PeriwinklePrimary, fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showSettingsMenu) {
        SettingsMenuDialog(
            onDismiss = { showSettingsMenu = false },
            onCalibrationSelect = { 
                showSettingsMenu = false
                showCalibration = true 
            },
            onTutorialSelect = { 
                showSettingsMenu = false
                tutorialStep = 1 
            },
            onInterpretationSelect = {
                showSettingsMenu = false
                showInterpretationDialog = true
            }
        )
    }

    if (showCalibration) {
        CalibrationDialog(
            initialOffset = uiState.calibrationOffset.toFloat(),
            onDismiss = { showCalibration = false },
            onSave = { offset ->
                viewModel.setCalibrationOffset(offset.toDouble())
            }
        )
    }

    if (showInterpretationDialog) {
        InterpretationDialog(onDismiss = { showInterpretationDialog = false })
    }

    DosimetroTheme {
        Box(Modifier.fillMaxSize().background(DarkBg)) {
            // El modificador blur se removió de toda la columna para no afectar los elementos resaltados
            Column(
                Modifier.fillMaxSize()
                    .systemBarsPadding()
                    .padding(16.dp), 
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Header(
                    onSettingsClick = { showSettingsMenu = true },
                    onOpenHistory = onOpenHistory,
                    modifierSettings = Modifier.onGloballyPositioned { targetRects[5] = it.boundsInRoot() },
                    modifierHistory = Modifier.onGloballyPositioned { targetRects[4] = it.boundsInRoot() }
                )
                Spacer(Modifier.height(12.dp))
                Box(Modifier.height(190.dp).onGloballyPositioned { targetRects[1] = it.boundsInRoot() }, contentAlignment = Alignment.Center) {
                    SoundLevelCircle(uiState.displayDbA.toFloat(), isRunning)
                }
                Spacer(Modifier.height(14.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LevelHistoryGraph(Modifier.fillMaxSize(), history, isRunning)
                    Column(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isRunning && uiState.liveDbA > 110.0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF3B1518).copy(alpha = 0.94f))
                                    .border(1.dp, RedAlert, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    "El micrófono puede estar saturado",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontFamily = BricolageGrotesque,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (showCalibrationReminder && uiState.calibrationOffset == 0.0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceCard.copy(alpha = 0.95f))
                                    .border(1.dp, PeriwinklePrimary, RoundedCornerShape(12.dp))
                                    .clickable {
                                        showCalibrationReminder = false
                                        showCalibration = true
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    "Calibra con un sonómetro para mejorar la exactitud",
                                    color = TextPrimary,
                                    fontSize = 11.5.sp,
                                    fontFamily = BricolageGrotesque,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Box(Modifier.onGloballyPositioned { targetRects[2] = it.boundsInRoot() }) {
                    DoseCard(uiState = uiState, isRunning = isRunning)
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.onGloballyPositioned { targetRects[3] = it.boundsInRoot() }) {
                    CriteriaToggleRow(uiState.profileId) {
                        viewModel.updateCriterion(it)
                    }
                }
                Spacer(Modifier.height(12.dp))
                
                if (uiState.status == "Completada" && uiState.savedSessionId != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().onGloballyPositioned { targetRects[6] = it.boundsInRoot() },
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1.2f).height(56.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PeriwinklePrimary),
                            onClick = {
                                val session = uiState.lastSavedSession
                                    ?: allSessions.firstOrNull { it.id == uiState.savedSessionId }
                                if (session != null) {
                                    val exporter = PdfReportExporter(context)
                                    val file = exporter.generateSessionPdf(session)
                                    ShareUtils.shareFile(context, file, "application/pdf")
                                } else {
                                    Toast.makeText(context, "Cargando sesión guardada...", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("Reporte PDF", color = Color.White, fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PeriwinkleLight),
                            onClick = {
                                val session = uiState.lastSavedSession
                                    ?: allSessions.firstOrNull { it.id == uiState.savedSessionId }
                                if (session != null) {
                                    scope.launch {
                                        val pts = uiState.lastSavedPoints.ifEmpty {
                                            viewModel.getSessionPoints(session.id)
                                        }
                                        val exporter = CsvReportExporter(context)
                                        val file = exporter.generateSessionCsv(session, pts)
                                        ShareUtils.shareFile(context, file, "text/csv")
                                    }
                                } else {
                                    Toast.makeText(context, "Cargando sesión guardada...", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("Datos CSV", color = PeriwinklePrimary, fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            modifier = Modifier.height(56.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard),
                            border = BorderStroke(1.dp, BorderSoft),
                            onClick = {
                                sessionName = "Medición_${SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())}"
                                viewModel.startSession()
                                onStartService()
                                if (uiState.calibrationOffset == 0.0) {
                                    showCalibrationReminder = true
                                }
                            }
                        ) {
                            Text("Nueva", color = TextPrimary, fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (isStopped) {
                    Row(
                        modifier = Modifier.fillMaxWidth().onGloballyPositioned { targetRects[6] = it.boundsInRoot() },
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1.4f).height(56.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PeriwinkleLight),
                            onClick = { showStopDialog = true }
                        ) {
                            Text("Guardar sesión como", color = PeriwinklePrimary, fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            modifier = Modifier.weight(0.8f).height(56.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PeriwinklePrimary),
                            onClick = {
                                viewModel.resumeSession()
                                onStartService()
                            }
                        ) {
                            Text("Reanudar", color = Color.White, fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Box(Modifier.onGloballyPositioned { targetRects[6] = it.boundsInRoot() }) {
                        ActionButtons(
                            isRunning = isRunning,
                            onMainClick = {
                                if (isRunning) {
                                    viewModel.stopRecording()
                                    onStopService()
                                } else {
                                    viewModel.startSession()
                                    onStartService()
                                    if (uiState.calibrationOffset == 0.0) {
                                        showCalibrationReminder = true
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // --- Interactive Spotlight Guided Tour ---
            if (tutorialStep in 1..6) {
                // Dibujamos un fondo blur falso usando un canvas separado debajo del recorte
                // pero por encima de la UI. En Android moderno, el efecto blur real a un fondo es costoso
                // y corta los elementos. La capa negra semitransparente es la mejor practica.
                
                val currentRect = targetRects[tutorialStep]
                if (currentRect != null) {
                    val density = LocalDensity.current
                    val screenHeightPx = LocalConfiguration.current.screenHeightDp * density.density
                    val isTopHalf = currentRect.center.y < (screenHeightPx / 2)

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = 0.99f } // Forces offscreen render for BlendMode.Clear to work on the dark layer only
                            .pointerInput(currentRect) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val pos = event.changes.first().position
                                        
                                        // Dynamic inflation based on the element size to prevent cutting off edges
                                        val inflateX = if (currentRect.width < 150f) 40f else 20f
                                        val inflateY = if (currentRect.height < 150f) 40f else 20f
                                        
                                        val isInsideHole = pos.x >= currentRect.left - inflateX && pos.x <= currentRect.right + inflateX &&
                                                           pos.y >= currentRect.top - inflateY && pos.y <= currentRect.bottom + inflateY
                                        
                                        if (!isInsideHole) {
                                            // Consume touches outside the hole so background can't be interacted with
                                            event.changes.forEach { it.consume() }
                                        }
                                        // If inside the hole, the touch passes through to the underlying UI!
                                    }
                                }
                            }
                    ) {
                        // Draw Dark Overlay (Semitransparent dark overlay for the spotlight effect)
                        drawRect(Color.Black.copy(alpha = 0.75f))
                        
                        // Dynamic inflation for drawing the hole
                        val inflateX = if (currentRect.width < 150f) 40f else 20f
                        val inflateY = if (currentRect.height < 150f) 40f else 20f

                        // Punch Hole
                        drawRoundRect(
                            color = Color.Transparent,
                            topLeft = Offset(currentRect.left - inflateX, currentRect.top - inflateY),
                            size = androidx.compose.ui.geometry.Size(currentRect.width + (inflateX * 2), currentRect.height + (inflateY * 2)),
                            cornerRadius = CornerRadius(40f, 40f),
                            blendMode = BlendMode.Clear
                        )
                    }

                    // Tooltip Dialog Card
                    Card(
                        modifier = Modifier
                            .align(if (isTopHalf) Alignment.BottomCenter else Alignment.TopCenter)
                            .padding(horizontal = 16.dp, vertical = 64.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(Modifier.padding(24.dp)) {
                            Text(
                                text = when(tutorialStep) {
                                    1 -> "1. Nivel Sonoro"
                                    2 -> "2. Dosis Acumulada"
                                    3 -> "3. NIOSH, OSHA o COLOMBIA"
                                    4 -> "4. Historial y Reportes"
                                    5 -> "5. Configuración"
                                    else -> "6. ¡Comenzar!"
                                },
                                fontSize = 18.sp, fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold, color = PeriwinklePrimary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = when(tutorialStep) {
                                    1 -> "Mide el Nivel de Presión Sonora en dB(A) simulando la respuesta del oído humano. Se pondrá naranja o rojo si la intensidad del ruido alcanza niveles perjudiciales para la salud."
                                    2 -> "Porcentaje de ruido seguro de tu día que ya has consumido. ¡Vibrará al llegar al 100%!"
                                    3 -> "Elige con qué criterio ver tu dosis diaria (8 h):\n• NIOSH: el más estricto, recomendado para prevención (85 dBA).\n• OSHA: límite legal de EE. UU. (90 dBA).\n• COLOMBIA: límite legal colombiano, Resolución 1792 de 1990 (85 dBA).\nPuedes cambiar en cualquier momento sin perder lo medido; el reporte PDF/CSV incluye además OSHA HC e ISO 9612."
                                    4 -> "Aquí encontrarás tus sesiones pasadas. Tu historial dura para siempre guardado localmente (solo se borra si desinstalas la app). Podrás renombrar o compartir tus sesiones."
                                    5 -> "En este engranaje podrás ajustar la Calibración de Offset con un sonómetro patrón, aprender a interpretar tus resultados, o volver a ver este tutorial cuando lo desees."
                                    else -> "Toca COMENZAR para iniciar la medición. Al detenerla, usa 'Guardar sesión como' para almacenarla permanentemente."
                                },
                                color = TextSecondary, fontFamily = BricolageGrotesque
                            )
                            Spacer(Modifier.height(24.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { tutorialStep = 0 }) {
                                        Text("SALTAR", color = TextSecondary, fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold)
                                    }
                                    if (tutorialStep > 1) {
                                        TextButton(onClick = { tutorialStep-- }) {
                                            Text("ATRÁS", color = PeriwinklePrimary, fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Button(
                                    onClick = { if (tutorialStep < 6) tutorialStep++ else tutorialStep = 0 },
                                    colors = ButtonDefaults.buttonColors(containerColor = PeriwinklePrimary)
                                ) {
                                    Text(if (tutorialStep < 6) "SIGUIENTE" else "ENTENDIDO", color = Color.White, fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Header(
    onSettingsClick: () -> Unit, 
    onOpenHistory: () -> Unit,
    modifierSettings: Modifier = Modifier,
    modifierHistory: Modifier = Modifier
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(
            onClick = onSettingsClick,
            modifier = modifierSettings.size(48.dp).background(SurfaceCard, RoundedCornerShape(12.dp)).border(1.dp, BorderSoft, RoundedCornerShape(12.dp))
        ) { Icon(Icons.Default.Settings, "Configuraciones", tint = TextSecondary) }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("LEVELAPP", color = TextPrimary, fontSize = 20.sp, fontFamily = BricolageGrotesque, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
        }
        
        IconButton(
            onClick = onOpenHistory,
            modifier = modifierHistory.size(48.dp).background(SurfaceCard, RoundedCornerShape(12.dp)).border(1.dp, BorderSoft, RoundedCornerShape(12.dp))
        ) { Icon(Icons.AutoMirrored.Filled.List, "Historial", tint = TextSecondary) }
    }
}

@Composable
fun SoundLevelCircle(db: Float, isRunning: Boolean) {
    val circleBg = if (db > 100) RedAlert else if (db > 85) AmberAlert else SurfaceCard
    val circleText = if (db > 85) Color.White else TextPrimary
    val circleSub = if (db > 85) Color.White.copy(alpha = 0.8f) else TextSecondary
    val borderColor = if (isRunning && db <= 85) PeriwinklePrimary else Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .aspectRatio(1f)
            .shadow(if (db <= 85) 4.dp else 12.dp, CircleShape)
            .background(circleBg, CircleShape)
            .border(4.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("NIVEL SONORO", color = circleSub, fontSize = 12.sp, fontFamily = BricolageGrotesque, fontWeight = FontWeight.SemiBold)
            Text("%.1f".format(db), color = circleText, fontSize = 64.sp, fontFamily = BricolageGrotesque, fontWeight = FontWeight.ExtraBold)
            Text("dB(A)", color = if(db > 85) circleText else PeriwinklePrimary, fontSize = 16.sp, fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun LevelHistoryGraph(modifier: Modifier, levels: List<Float>, isRunning: Boolean) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val rightMargin = 40.dp.toPx()
        val bottomMargin = 20.dp.toPx()
        val graphW = w - rightMargin
        val graphH = h - bottomMargin

        val dbLevels = listOf(20, 40, 60, 80, 100)
        val gridColor = BorderSoft
        val minPlotDb = 20f
        val maxPlotDb = 110f
        val plotSpan = maxPlotDb - minPlotDb
        
        dbLevels.forEach { db ->
            val y = graphH - ((db - minPlotDb) / plotSpan) * graphH
            drawLine(gridColor, Offset(0f, y), Offset(graphW, y), strokeWidth = 1f)
            drawText(
                textMeasurer = textMeasurer,
                text = "$db dB",
                topLeft = Offset(graphW + 4.dp.toPx(), y - 6.dp.toPx()),
                style = androidx.compose.ui.text.TextStyle(color = TextSecondary, fontSize = 10.sp, fontFamily = BricolageGrotesque, fontWeight = FontWeight.Medium)
            )
        }

        val timeLabels = listOf(0, 10, 20, 30, 40)
        timeLabels.forEach { sec ->
            val x = graphW - (sec / 40f) * graphW
            drawLine(gridColor, Offset(x, 0f), Offset(x, graphH), strokeWidth = 1f)
            drawText(
                textMeasurer = textMeasurer,
                text = "-$sec s",
                topLeft = Offset(x - 12.dp.toPx(), graphH + 4.dp.toPx()),
                style = androidx.compose.ui.text.TextStyle(color = TextSecondary, fontSize = 10.sp, fontFamily = BricolageGrotesque, fontWeight = FontWeight.Medium)
            )
        }

        val y85 = graphH - ((85f - minPlotDb) / plotSpan) * graphH
        drawLine(
            color = AmberAlert,
            start = Offset(0f, y85),
            end = Offset(graphW, y85),
            strokeWidth = 2f,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        )
        drawText(
            textMeasurer = textMeasurer,
            text = "85",
            topLeft = Offset(graphW + 4.dp.toPx(), y85 - 6.dp.toPx()),
            style = androidx.compose.ui.text.TextStyle(color = AmberAlert, fontSize = 10.sp, fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold)
        )

        if (isRunning && levels.isNotEmpty()) {
            val maxPoints = 400
            val path = Path()
            val fillPath = Path()
            val step = graphW / (maxPoints - 1).toFloat()
            
            val coords = mutableListOf<Offset>()
            val startIdx = (maxPoints - levels.size).coerceAtLeast(0)
            levels.forEachIndexed { idx, db ->
                val x = (startIdx + idx) * step
                val clampedDb = db.coerceIn(35f, 110f)
                val y = graphH - ((clampedDb - minPlotDb) / plotSpan) * graphH
                coords.add(Offset(x, y))
            }
            
            if (coords.size > 1) {
                path.moveTo(coords[0].x, coords[0].y)
                fillPath.moveTo(coords[0].x, graphH)
                fillPath.lineTo(coords[0].x, coords[0].y)
                
                for (i in 0 until coords.size - 1) {
                    val p0 = coords[i]
                    val p1 = coords[i + 1]
                    val cx = (p0.x + p1.x) / 2f
                    path.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                    fillPath.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                }
                fillPath.lineTo(coords.last().x, graphH)
                fillPath.close()
                
                drawPath(
                    fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(PeriwinklePrimary.copy(alpha = 0.35f), Color.Transparent),
                        startY = 0f,
                        endY = graphH
                    )
                )
                
                drawPath(
                    path,
                    color = PeriwinklePrimary,
                    style = Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
        }
    }
}

@Composable
fun DoseCard(uiState: SessionUiState, isRunning: Boolean) {
    val dose = uiState.dose.toFloat()
    val profile = RegulatoryProfile.byId(uiState.profileId)
    val doseColor = when {
        dose >= 100f -> RedAlert
        dose >= 50f -> AmberAlert
        else -> GreenSafe
    }
    val statusPhrase = when {
        dose >= 100f -> "Límite superado: usa protección y reduce el tiempo de exposición"
        dose >= 50f -> "Cerca del límite: usa protección auditiva"
        else -> "Exposición aceptable"
    }

    val refLevel = if (isRunning && uiState.measuredSeconds > 0.0) uiState.liveDbA else uiState.laeq
    val remText = if (uiState.measuredSeconds <= 0.0 && !isRunning) {
        "--"
    } else {
        DoseCalculator.formatRemainingTimeHoursMinutes(
            DoseCalculator.remainingTimeHours(uiState.dose, refLevel, profile)
        )
    }
    val lexStr = if (uiState.measuredSeconds > 0.0 && uiState.lex8h.isFinite()) {
        String.format(Locale.US, "%.1f dBA", uiState.lex8h)
    } else "--"
    val twaStr = if (uiState.measuredSeconds > 0.0) {
        DoseCalculator.formatTwa(uiState.dose, profile)
    } else "--"

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, BorderSoft)
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Dosis Acumulada", color = TextPrimary, fontSize = 14.sp, fontFamily = BricolageGrotesque, fontWeight = FontWeight.SemiBold)
                Text(profile.name, color = TextSecondary, fontFamily = BricolageGrotesque, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text("%.2f".format(dose), color = PeriwinklePrimary, fontSize = 30.sp, fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold)
                Text("%", color = PeriwinklePrimary, fontSize = 15.sp, fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (dose / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = doseColor,
                trackColor = DarkBg,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = statusPhrase,
                color = doseColor,
                fontSize = 12.5.sp,
                fontFamily = BricolageGrotesque,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Tiempo restante hasta 100%: $remText",
                color = TextSecondary,
                fontSize = 11.5.sp,
                fontFamily = BricolageGrotesque
            )
            Text(
                text = "L_EX,8h: $lexStr  •  TWA: $twaStr",
                color = TextSecondary,
                fontSize = 11.sp,
                fontFamily = BricolageGrotesque
            )
        }
    }
}

@Composable
fun CriteriaToggleRow(selectedProfileId: String, onSelect: (String) -> Unit) {
    // Selector visible simplificado: los 5 perfiles (incl. OSHA HC e ISO 9612) se siguen
    // calculando en paralelo y aparecen en el PDF y el CSV.
    val options = listOf(
        "niosh" to "NIOSH",
        "osha" to "OSHA",
        "col_res1792" to "COLOMBIA"
    )
    Row(Modifier.fillMaxWidth().height(48.dp).background(SurfaceCard, RoundedCornerShape(24.dp)).border(1.dp, BorderSoft, RoundedCornerShape(24.dp)).padding(4.dp)) {
        options.forEach { (id, label) ->
            val selected = selectedProfileId == id
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp))
                    .background(if (selected) PeriwinklePrimary else Color.Transparent)
                    .clickable { onSelect(id) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (selected) Color.White else TextSecondary,
                    fontFamily = BricolageGrotesque,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun ActionButtons(isRunning: Boolean, onMainClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onMainClick,
            modifier = Modifier.weight(1f).height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) PeriwinkleLight else PeriwinklePrimary),
            shape = RoundedCornerShape(20.dp),
            border = if (isRunning) BorderStroke(1.dp, BorderSoft) else null
        ) {
            Text(
                if (isRunning) "DETENER" else "COMENZAR", 
                color = if (isRunning) PeriwinklePrimary else Color.White, 
                fontSize = 15.sp, fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun SettingsMenuDialog(
    onDismiss: () -> Unit, 
    onCalibrationSelect: () -> Unit, 
    onTutorialSelect: () -> Unit,
    onInterpretationSelect: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = SurfaceCard)) {
            Column(Modifier.padding(24.dp).fillMaxWidth()) {
                Text("Configuraciones", fontSize = 20.sp, fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(16.dp))
                TextButton(
                    onClick = onCalibrationSelect,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Text("Calibración Offset", color = TextPrimary, fontSize = 16.sp, fontFamily = BricolageGrotesque)
                    }
                }
                TextButton(
                    onClick = onTutorialSelect,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Text("Cómo usar la app", color = TextPrimary, fontSize = 16.sp, fontFamily = BricolageGrotesque)
                    }
                }
                TextButton(
                    onClick = onInterpretationSelect,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Text("Interpretar Resultados", color = TextPrimary, fontSize = 16.sp, fontFamily = BricolageGrotesque)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onDismiss) { Text("CERRAR", color = PeriwinklePrimary, fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
fun CalibrationDialog(initialOffset: Float, onDismiss: () -> Unit, onSave: (Float) -> Unit) {
    var textValue by remember { mutableStateOf(initialOffset.toString()) }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = SurfaceCard)) {
            Column(Modifier.padding(24.dp)) {
                Text("Calibración Offset (dB)", fontSize = 20.sp, fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = textValue, 
                    onValueChange = { textValue = it }, 
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PeriwinklePrimary, 
                        unfocusedBorderColor = BorderSoft,
                        focusedTextColor = TextPrimary, 
                        unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onDismiss) { Text("CANCELAR", color = TextSecondary, fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold) }
                    Button(onClick = { onSave(textValue.toFloatOrNull() ?: 0f); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = PeriwinklePrimary)) { 
                        Text("GUARDAR", color = Color.White, fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold) 
                    }
                }
            }
        }
    }
}

@Composable
fun InterpretationDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = SurfaceCard)) {
            Column(Modifier.padding(24.dp).height(450.dp)) {
                Text("Cómo interpretar tus resultados", fontSize = 20.sp, fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold, color = PeriwinklePrimary)
                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp).verticalScroll(rememberScrollState())) {
                    Column {
                        Text("• ¿Cómo funciona?", fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                        Text("El micrófono se coloca cerca del oído del trabajador (en el hombro). El aparato acumula la energía acústica recibida a lo largo del tiempo y calcula la Dosis de Ruido recibida en porcentaje.", color = TextSecondary, fontFamily = BricolageGrotesque, fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("• Criterio de evaluación", fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                        Text("Una dosis del 100% equivale a haber estado expuesto al límite máximo permitido para una jornada de 8 horas (que habitualmente es de 85 dBA, según la normativa local). Si el dosímetro marca más del 100%, significa que se ha superado el límite seguro y hay riesgo de hipoacusia (pérdida de audición).", color = TextSecondary, fontFamily = BricolageGrotesque, fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("• L_EX,8h y TWA (métricas separadas)", fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                        Text("• L_EX,8h (ISO 9612): Nivel diario normalizado a 8 h calculado únicamente por energía acústica (sin umbral), igual para todos los criterios.\n• TWA: Promedio ponderado propio del criterio elegido calculado a partir de su dosis. Si la dosis es menor a 0.01 %, se muestra 'Bajo umbral' en lugar de un número.\n• Rango confiable del micrófono: 35–110 dB(A). La app integra el nivel real sin recortar y te avisa si hay valores fuera de este rango.", color = TextSecondary, fontFamily = BricolageGrotesque, fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("• Diferencias de Normativa y Perfiles", fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                        Text("Criterios seleccionables en pantalla:\n• NIOSH (REL): 85 dBA / 8 h, tasa de intercambio 3 dB, umbral 80 dBA. El más protector.\n• OSHA (PEL): 90 dBA / 8 h, tasa de intercambio 5 dB, umbral 90 dBA. Límite legal en EE. UU.\n• COLOMBIA (Res. 1792 de 1990): 85 dBA / 8 h, tasa de intercambio 5 dB, umbral 80 dBA. Límite legal en Colombia.\n\nIncluidos además en el reporte PDF/CSV:\n• OSHA HC: programa de conservación auditiva (Action Level: 50% = 85 dBA), umbral 80 dBA.\n• ISO 9612: método de cálculo del nivel diario L_EX,8h (85 dBA, 3 dB, sin umbral).", color = TextSecondary, fontFamily = BricolageGrotesque, fontSize = 14.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = PeriwinklePrimary)) {
                        Text("ENTENDIDO", color = Color.White, fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun TutorialDialog(onDismiss: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = SurfaceCard)) {
            Column(Modifier.padding(24.dp).height(380.dp)) {
                Text("Cómo usar LevelApp (${page + 1}/4)", fontSize = 20.sp, fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold, color = PeriwinklePrimary)
                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp).verticalScroll(rememberScrollState())) {
                    when (page) {
                        0 -> Column {
                            Text("1. Nivel Sonoro", fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
                            Text("\nEl círculo principal te muestra los decibelios en tiempo real. Esta es tu exposición actual al ruido. El color cambiará a naranja o rojo si el sonido es peligroso.", color = TextSecondary, fontFamily = BricolageGrotesque)
                        }
                        1 -> Column {
                            Text("2. Dosis Acumulada", fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
                            Text("\nLa barra de dosis es lo más importante. Te dice qué porcentaje del ruido seguro de todo tu día ya has consumido. Si llega al 100%, tu teléfono vibrará avisando que estás en riesgo de daño auditivo.", color = TextSecondary, fontFamily = BricolageGrotesque)
                        }
                        2 -> Column {
                            Text("3. NIOSH, OSHA o COLOMBIA", fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
                            Text("\nPuedes elegir el criterio bajo el que quieres evaluarte:\n\n• NIOSH: 85 dBA (recomendado / más estricto)\n• OSHA: 90 dBA (límite legal EE. UU.)\n• COLOMBIA: 85 dBA, Resolución 1792 de 1990 (límite legal colombiano)", color = TextSecondary, fontFamily = BricolageGrotesque)
                        }
                        3 -> Column {
                            Text("4. Guardar y Exportar", fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
                            Text("\nCuando finalices tu medición presionando 'Detener', podrás asignarle un nombre a tu sesión y luego exportar todos los datos a un reporte PDF o CSV desde el Historial.", color = TextSecondary, fontFamily = BricolageGrotesque)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("SALTAR", color = TextSecondary, fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold) }
                    Button(onClick = { if (page < 3) page++ else onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = PeriwinklePrimary)) {
                        Text(if (page < 3) "SIGUIENTE" else "ENTENDIDO", color = Color.White, fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
