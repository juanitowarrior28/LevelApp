package com.levelapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.levelapp.app.service.AudioDataStream
import com.levelapp.core.dosimetry.DoseCalculator
import com.levelapp.core.dosimetry.RegulatoryProfile
import com.levelapp.data.database.SessionEntity
import com.levelapp.data.repository.SessionRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SessionUiState(
    val liveDbA: Double = 0.0,
    val laeq: Double = 0.0,
    val lasMax: Double = 0.0,
    val twa: Double = 0.0,
    val dose: Double = 0.0,
    val durationMinutes: Double = 0.0,
    val status: String = "Idle",
    val savedSessionId: Long? = null,
    val isNiosh: Boolean = true,
    val calibrationOffset: Double = 0.0
)

class AcousticSessionViewModel(
    private val repository: SessionRepositoryImpl
) : ViewModel() {
    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState
    
    val allSessions: StateFlow<List<SessionEntity>> = repository.observeSessions()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())
    
    private val _history = MutableStateFlow<List<Float>>(emptyList())
    val history: StateFlow<List<Float>> = _history

    private var startTimeMs = 0L
    private var isRecording = false

    private var sumEnergy = 0.0
    private var sampleCount = 0

    init {
        viewModelScope.launch {
            AudioDataStream.liveDbFlow.collect { rawLiveDb ->
                if (isRecording) {
                    val calibratedDb = rawLiveDb + _uiState.value.calibrationOffset
                    processNewAudioSample(calibratedDb)
                }
            }
        }
    }
    
    fun updateCriterion(isNiosh: Boolean) {
        _uiState.value = _uiState.value.copy(isNiosh = isNiosh)
        // If we change criterion on the fly, we ideally should recalculate dose, but for simplicity here we just update state
    }
    
    fun setCalibrationOffset(offset: Double) {
        _uiState.value = _uiState.value.copy(calibrationOffset = offset)
    }

    fun startSession() {
        isRecording = true
        startTimeMs = System.currentTimeMillis()
        sumEnergy = 0.0
        sampleCount = 0
        _history.value = emptyList()
        _uiState.value = _uiState.value.copy(status = "Running", savedSessionId = null)
    }
    
    fun stopRecording() {
        isRecording = false
        _uiState.value = _uiState.value.copy(status = "Stopped")
    }

    fun pauseSession() {
        isRecording = false
        _uiState.value = _uiState.value.copy(status = "Paused")
    }

    private fun processNewAudioSample(levelDbA: Double) {
        val currentState = _uiState.value
        val durationMs = System.currentTimeMillis() - startTimeMs
        val durationMinutes = durationMs / 60000.0

        // Track History
        val currentHistory = _history.value.toMutableList()
        currentHistory.add(levelDbA.toFloat())
        if (currentHistory.size > 400) currentHistory.removeAt(0)
        _history.value = currentHistory

        // Keep track of max level
        val newLasMax = maxOf(currentState.lasMax, levelDbA)

        // Accumulate energy for Leq (simplified)
        sumEnergy += Math.pow(10.0, levelDbA / 10.0)
        sampleCount++
        val laeq = 10.0 * Math.log10(sumEnergy / sampleCount)

        val profile = if (currentState.isNiosh) {
            RegulatoryProfile.nioshDefault()
        } else {
            RegulatoryProfile(id = "osha", name = "OSHA PEL", criterionDbA = 90.0, exchangeRateDb = 5.0, thresholdDbA = 80.0, referenceHours = 8.0)
        }
        
        val dose = DoseCalculator.dosePercent(laeq, durationMinutes / 60.0, profile)
        val twa = DoseCalculator.twa(laeq, durationMinutes / 60.0, profile)

        _uiState.value = currentState.copy(
            liveDbA = levelDbA,
            laeq = laeq,
            lasMax = newLasMax,
            twa = twa,
            dose = dose,
            durationMinutes = durationMinutes,
            status = "Running"
        )
    }

    fun finishAndSaveSession(workerName: String, evaluator: String, customTitle: String? = null) {
        isRecording = false
        val finalState = _uiState.value
        _uiState.value = finalState.copy(status = "Completada")
        
        viewModelScope.launch {
            val entity = SessionEntity(
                title = customTitle ?: "Sesión ${System.currentTimeMillis()}",
                workerName = workerName,
                evaluatorName = evaluator,
                profileId = if (finalState.isNiosh) "niosh" else "osha",
                status = "Completada",
                startedAtEpochMs = startTimeMs,
                endedAtEpochMs = System.currentTimeMillis(),
                laeq = finalState.laeq,
                lasMax = finalState.lasMax,
                twa = finalState.twa,
                dosePercent = finalState.dose
            )
            val sessionId = repository.saveSession(entity)
            _uiState.value = _uiState.value.copy(savedSessionId = sessionId)
        }
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
