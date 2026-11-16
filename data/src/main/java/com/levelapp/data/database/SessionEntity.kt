package com.levelapp.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val workerName: String,
    val workerRole: String = "",
    val area: String = "",
    val evaluatorName: String,
    val profileId: String,
    val status: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null,
    val durationSeconds: Double = 0.0,
    val laeq: Double = 0.0,
    val sel: Double = 0.0,
    val lasMin: Double = 0.0,
    val lasMax: Double = 0.0,
    val l10: Double = 0.0,
    val l50: Double = 0.0,
    val l90: Double = 0.0,
    val twa: Double = 0.0,
    val dosePercent: Double = 0.0,
    val doseNiosh: Double = 0.0,
    val doseOsha: Double = 0.0,
    val doseIso: Double = 0.0,
    val notes: String = "",
)

@Entity(tableName = "measurement_points")
data class MeasurementPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    val timestampEpochMs: Long,
    val elapsedSeconds: Double = 0.0,
    val durationSeconds: Double,
    val dbA: Double,
    val lasMin: Double = 0.0,
    val lasMax: Double,
    val selSlice: Double = 0.0,
    val laeq: Double,
    val selCumulative: Double = 0.0,
    val twa: Double,
    val dosePercent: Double,
    val doseNiosh: Double = 0.0,
    val doseOsha: Double = 0.0,
    val doseIso: Double = 0.0,
)
