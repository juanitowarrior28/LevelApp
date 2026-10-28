package com.levelapp.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AcousticSessionScreen(
    onStartSession: () -> Unit = {},
    onPauseSession: () -> Unit = {},
    onFinishSession: () -> Unit = {},
) {
    var isRecording by remember { mutableStateOf(false) }
    val levelDb = 84.6
    val laeq = 82.8
    val lasMax = 92.1
    val twa = 84.9
    val dose = 75.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101418))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = "LevelApp — Dosimetría acústica ocupacional",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text("dB(A): ${"%.1f".format(levelDb)}", color = Color.White)
        Text("LAeq: ${"%.1f".format(laeq)} dB(A)", color = Color.White)
        Text("LASmax: ${"%.1f".format(lasMax)} dB(A)", color = Color.White)
        Text("TWA: ${"%.1f".format(twa)} dB(A)", color = Color.White)
        Text("Dosis: ${"%.1f".format(dose)} %", color = Color.White)
        Text("Perfil: NIOSH REL • 85 dBA / 8h • exchange 3 dB • threshold 80 dBA", color = Color.White)
        Text("Estado: ${if (isRecording) "Grabando" else "En espera"}", color = Color.White)
        Text("Calibración: No calibrada / no validada", color = Color(0xFFB9C6D3))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = {
                isRecording = !isRecording
                if (isRecording) onStartSession() else onPauseSession()
            }) {
                Text(if (isRecording) "Pausar" else "Iniciar")
            }
            Button(onClick = {
                isRecording = false
                onFinishSession()
            }) {
                Text("Finalizar")
            }
        }
    }
}

@Composable
fun LevelAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        content = content,
    )
}
