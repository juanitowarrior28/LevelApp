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
    suspend fun saveMeasurements(points: List<MeasurementPointEntity>) = dao.insertMeasurementPoints(points)
    fun observeSessions(): Flow<List<SessionEntity>> = dao.observeSessions()
    fun observeMeasurementPoints(sessionId: Long): Flow<List<MeasurementPointEntity>> = dao.observeMeasurementPoints(sessionId)
    suspend fun getMeasurementPoints(sessionId: Long): List<MeasurementPointEntity> = dao.getMeasurementPoints(sessionId)
    suspend fun deleteSession(sessionId: Long) {
        dao.deleteMeasurementPointsForSession(sessionId)
        dao.deleteSession(sessionId)
    }
    suspend fun deleteAllSessions() {
        dao.deleteAllMeasurementPoints()
        dao.deleteAllSessions()
    }
    suspend fun renameSession(sessionId: Long, newTitle: String) = dao.renameSession(sessionId, newTitle)
}
