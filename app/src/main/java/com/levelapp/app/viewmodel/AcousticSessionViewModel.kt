package com.levelapp.app.viewmodel

import androidx.lifecycle.ViewModel
import com.levelapp.core.dosimetry.DoseCalculator
import com.levelapp.core.dosimetry.RegulatoryProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SessionUiState(
    val liveDbA: Double = 0.0,
    val laeq: Double = 0.0,
    val lasMax: Double = 0.0,
    val twa: Double = 0.0,
    val dose: Double = 0.0,
    val durationMinutes: Double = 0.0,
    val status: String = "Idle",
)

class AcousticSessionViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState

    fun updateSnapshot(levelDbA: Double, durationMinutes: Double) {
        val profile = RegulatoryProfile.nioshDefault()
        val dose = DoseCalculator.dosePercent(levelDbA, durationMinutes / 60.0, profile)
        val twa = DoseCalculator.twa(levelDbA, durationMinutes / 60.0, profile)
        _uiState.value = SessionUiState(
            liveDbA = levelDbA,
            laeq = levelDbA,
            lasMax = levelDbA.coerceAtLeast(0.0),
            twa = twa,
            dose = dose,
            durationMinutes = durationMinutes,
            status = if (levelDbA > 0.0) "Running" else "Idle",
        )
    }
}
