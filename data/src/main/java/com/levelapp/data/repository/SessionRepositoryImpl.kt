package com.levelapp.data.repository

import com.levelapp.data.database.AcousticSessionDao
import com.levelapp.data.database.MeasurementPointEntity
import com.levelapp.data.database.SessionEntity
import kotlinx.coroutines.flow.Flow

class SessionRepositoryImpl(
    private val dao: AcousticSessionDao,
) {
    suspend fun saveSession(session: SessionEntity): Long = dao.insertSession(session)
    suspend fun saveMeasurement(point: MeasurementPointEntity): Long = dao.insertMeasurementPoint(point)
    fun observeSessions(): Flow<List<SessionEntity>> = dao.observeSessions()
    fun observeMeasurementPoints(sessionId: Long): Flow<List<MeasurementPointEntity>> = dao.observeMeasurementPoints(sessionId)
}
