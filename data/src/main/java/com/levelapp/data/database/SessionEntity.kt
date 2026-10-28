package com.levelapp.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val workerName: String,
    val evaluatorName: String,
    val profileId: String,
    val status: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null,
    val laeq: Double = 0.0,
    val lasMax: Double = 0.0,
    val twa: Double = 0.0,
    val dosePercent: Double = 0.0,
    val notes: String = "",
)

@Entity(tableName = "measurement_points")
data class MeasurementPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    val timestampEpochMs: Long,
    val dbA: Double,
    val laeq: Double,
    val lasMax: Double,
    val twa: Double,
    val dosePercent: Double,
    val durationSeconds: Double,
)
