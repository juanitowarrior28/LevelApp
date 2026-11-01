package com.levelapp.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.levelapp.app.csv.CsvReportExporter
import com.levelapp.app.pdf.PdfReportExporter
import com.levelapp.app.utils.ShareUtils
import com.levelapp.app.viewmodel.AcousticSessionViewModel
import com.levelapp.data.database.SessionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: AcousticSessionViewModel,
    onBack: () -> Unit
) {
    val allSessions by viewModel.allSessions.collectAsState()
    val context = LocalContext.current
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Eliminar todo", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("¿Estás seguro de que deseas eliminar todo el historial? Esto no se puede deshacer.", color = TextSecondary) },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteAllSessions()
                    showDeleteAllDialog = false
                    Toast.makeText(context, "Historial eliminado", Toast.LENGTH_SHORT).show()
                }, colors = ButtonDefaults.buttonColors(containerColor = RedAlert)) {
                    Text("Eliminar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) { Text("Cancelar", color = PeriwinklePrimary) }
            },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(24.dp)
        )
    }

    DosimetroTheme {
        Box(Modifier.fillMaxSize().background(DarkBg).systemBarsPadding().padding(16.dp)) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp).background(SurfaceCard, RoundedCornerShape(12.dp))
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = TextSecondary) }
                    
                    Text("HISTORIAL", color = TextPrimary, fontSize = 20.sp, fontFamily = BricolageGrotesque, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)

                    IconButton(
                        onClick = { if(allSessions.isNotEmpty()) showDeleteAllDialog = true },
                        modifier = Modifier.size(48.dp).background(SurfaceCard, RoundedCornerShape(12.dp))
                    ) { Icon(Icons.Default.Delete, "Borrar Todo", tint = if (allSessions.isNotEmpty()) RedAlert else TextSecondary) }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (allSessions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No hay sesiones guardadas.", color = TextSecondary)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(allSessions) { session ->
                            SessionCard(
                                session = session,
                                onDelete = { viewModel.deleteSession(session.id) },
                                onRename = { id, title -> viewModel.renameSession(id, title) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SessionCard(session: SessionEntity, onDelete: () -> Unit, onRename: (Long, String) -> Unit) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf(session.title) }
    
    val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(session.startedAtEpochMs))
    
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Renombrar sesión", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PeriwinklePrimary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newTitle.isNotBlank()) {
                        onRename(session.id, newTitle)
                        showRenameDialog = false
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = PeriwinklePrimary)) {
                    Text("Guardar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancelar", color = PeriwinklePrimary) }
            },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(24.dp)
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text(session.title, color = TextPrimary, fontSize = 16.sp, fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold)
                    Text(dateStr, color = TextSecondary, fontSize = 12.sp)
                }
                
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opciones", tint = TextSecondary)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(SurfaceCard)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Renombrar", color = TextPrimary) },
                            onClick = {
                                expanded = false
                                showRenameDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Compartir CSV", color = TextPrimary) },
                            onClick = {
                                expanded = false
                                val exporter = CsvReportExporter(context)
                                val file = exporter.generateSessionCsv(session)
                                ShareUtils.shareFile(context, file, "text/csv")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Compartir PDF", color = TextPrimary) },
                            onClick = {
                                expanded = false
                                val exporter = PdfReportExporter(context)
                                val file = exporter.generateSessionPdf(session.id.toString(), session.title)
                                ShareUtils.shareFile(context, file, "application/pdf")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Eliminar", color = RedAlert) },
                            onClick = {
                                expanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Perfil", color = TextSecondary, fontSize = 12.sp)
                    Text(session.profileId.uppercase(), color = PeriwinklePrimary, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("LAeq", color = TextSecondary, fontSize = 12.sp)
                    Text("${"%.1f".format(session.laeq)} dB", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Dosis", color = TextSecondary, fontSize = 12.sp)
                    val doseColor = if (session.dosePercent >= 100.0) RedAlert else if (session.dosePercent >= 50.0) AmberAlert else TextPrimary
                    Text("${"%.1f".format(session.dosePercent)}%", color = doseColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
