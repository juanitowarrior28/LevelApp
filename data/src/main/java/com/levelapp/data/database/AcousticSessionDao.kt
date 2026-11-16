package com.levelapp.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AcousticSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeasurementPoint(point: MeasurementPointEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeasurementPoints(points: List<MeasurementPointEntity>)

    @Query("SELECT * FROM sessions ORDER BY startedAtEpochMs DESC")
    fun observeSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM measurement_points WHERE session_id = :sessionId ORDER BY timestampEpochMs ASC")
    fun observeMeasurementPoints(sessionId: Long): Flow<List<MeasurementPointEntity>>

    @Query("SELECT * FROM measurement_points WHERE session_id = :sessionId ORDER BY timestampEpochMs ASC")
    suspend fun getMeasurementPoints(sessionId: Long): List<MeasurementPointEntity>

    @Query("DELETE FROM measurement_points WHERE session_id = :sessionId")
    suspend fun deleteMeasurementPointsForSession(sessionId: Long)

    @Query("DELETE FROM measurement_points")
    suspend fun deleteAllMeasurementPoints()

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()

    @Query("UPDATE sessions SET title = :newTitle WHERE id = :sessionId")
    suspend fun renameSession(sessionId: Long, newTitle: String)
}
