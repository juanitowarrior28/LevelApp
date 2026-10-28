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

    @Query("SELECT * FROM sessions ORDER BY startedAtEpochMs DESC")
    fun observeSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM measurement_points WHERE session_id = :sessionId ORDER BY timestampEpochMs ASC")
    fun observeMeasurementPoints(sessionId: Long): Flow<List<MeasurementPointEntity>>
}
