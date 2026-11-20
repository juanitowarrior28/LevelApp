package com.levelapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.levelapp.app.service.AudioDataStream
import com.levelapp.data.database.MeasurementPointEntity
import com.levelapp.data.database.SessionEntity
import com.levelapp.data.repository.SessionRepositoryImpl
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SessionUiState(
    val liveDbA: Double = 0.0,
    val displayDbA: Double = 0.0,
    val laeq: Double = 0.0,
    val lex8h: Double = Double.NEGATIVE_INFINITY,
    val sel: Double = 0.0,
    val soundExposurePa2h: Double = 0.0,
    val lasMin: Double = 0.0,
    val lasMax: Double = 0.0,
    val l10: Double = 0.0,
    val l50: Double = 0.0,
    val l90: Double = 0.0,
    val twa: Double = Double.NEGATIVE_INFINITY,
    val dose: Double = 0.0,
    val doseNiosh: Double = 0.0,
    val doseOsha: Double = 0.0,
    val doseOshaHc: Double = 0.0,
    val doseIso: Double = 0.0,
    val doseColRes1792: Double = 0.0,
    val projectedDose8h: Double = 0.0,
    val durationMinutes: Double = 0.0,
    val measuredSeconds: Double = 0.0,
    val belowFloorSeconds: Double = 0.0,
    val saturationSeconds: Double = 0.0,
    val pausedSeconds: Double = 0.0,
    val hasPauses: Boolean = false,
    val resumedNote: String = "",
    val status: String = "Idle",
    val savedSessionId: Long? = null,
    val lastSavedSession: SessionEntity? = null,
    val lastSavedPoints: List<MeasurementPointEntity> = emptyList(),
    val profileId: String = "niosh",
    val calibrationOffset: Double = 0.0
)

class AcousticSessionViewModel(
    private val repository: SessionRepositoryImpl
) : ViewModel() {
    val uiState: StateFlow<SessionUiState> = AudioDataStream.uiState
    val history: StateFlow<List<Float>> = AudioDataStream.history

    val allSessions: StateFlow<List<SessionEntity>> = repository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateCriterion(profileId: String) {
        AudioDataStream.updateCriterion(profileId)
    }

    fun setCalibrationOffset(offset: Double) {
        AudioDataStream.setCalibrationOffset(offset)
    }

    fun startSession() {
        AudioDataStream.startSession()
    }

    fun stopRecording() {
        AudioDataStream.stopRecording()
    }

    fun pauseSession() {
        AudioDataStream.pauseSession()
    }

    fun resumeSession() {
        AudioDataStream.resumeSession()
    }

    fun finishAndSaveSession(
        workerName: String,
        workerRole: String = "",
        area: String = "",
        evaluator: String = "",
        notes: String = "",
        customTitle: String? = null
    ) {
        val snap = AudioDataStream.currentSnapshot()
        val startedAt = snap.startTimeMs.let { if (it > 0L) it else System.currentTimeMillis() }
        val endedAt = snap.endedAtMs.let { if (it >= startedAt && it > 0L) it else System.currentTimeMillis() }
        val rawPoints = AudioDataStream.buildMeasurementPoints(0L, snap.profileId)

        viewModelScope.launch {
            val entity = SessionEntity(
                title = customTitle?.takeIf { it.isNotBlank() } ?: "Sesión ${System.currentTimeMillis()}",
                workerName = workerName.trim().ifEmpty { "Sin especificar" },
                workerRole = workerRole.trim().ifEmpty { "Sin especificar" },
                area = area.trim().ifEmpty { "Sin especificar" },
                evaluatorName = evaluator.trim().ifEmpty { "Evaluador SST" },
                profileId = snap.profileId,
                status = "Completada",
                startedAtEpochMs = startedAt,
                endedAtEpochMs = endedAt,
                durationSeconds = snap.measuredSeconds,
                laeq = snap.laeq,
                lex8h = snap.lex8h,
                sel = snap.sel,
                soundExposurePa2h = snap.soundExposurePa2h,
                lasMin = snap.lasMin,
                lasMax = snap.lasMax,
                l10 = snap.l10,
                l50 = snap.l50,
                l90 = snap.l90,
                twa = if (snap.twa.isFinite()) snap.twa else 0.0,
                dosePercent = snap.dose,
                doseNiosh = snap.doseNiosh,
                doseOsha = snap.doseOsha,
                doseOshaHc = snap.doseOshaHc,
                doseIso = snap.doseIso,
                doseColRes1792 = snap.doseColRes1792,
                belowFloorSeconds = snap.belowFloorSeconds,
                saturationSeconds = snap.saturationSeconds,
                pausedSeconds = snap.pausedSeconds,
                hasPauses = snap.hasPauses,
                resumedNote = snap.resumedNote,
                calibrationOffset = snap.calibrationOffset,
                notes = notes.trim()
            )
            val sessionId = repository.saveSession(entity)
            val pointsWithId = rawPoints.map { it.copy(sessionId = sessionId) }
            if (pointsWithId.isNotEmpty()) {
                repository.saveMeasurements(pointsWithId)
            }
            AudioDataStream.markSessionCompleted(
                savedSessionId = sessionId,
                savedSession = entity.copy(id = sessionId),
                savedPoints = pointsWithId
            )
        }
    }

    suspend fun getSessionPoints(sessionId: Long): List<MeasurementPointEntity> {
        return repository.getMeasurementPoints(sessionId)
    }

    fun renameSession(sessionId: Long, newTitle: String) {
        viewModelScope.launch {
            repository.renameSession(sessionId, newTitle)
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
        }
    }

    fun deleteAllSessions() {
        viewModelScope.launch {
            repository.deleteAllSessions()
        }
    }
}

class AcousticSessionViewModelFactory(
    private val repository: SessionRepositoryImpl
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AcousticSessionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AcousticSessionViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
