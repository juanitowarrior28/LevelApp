package com.levelapp.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SessionEntity::class, MeasurementPointEntity::class],
    version = 5,
    exportSchema = true,
)
abstract class AcousticDatabase : RoomDatabase() {
    abstract fun acousticSessionDao(): AcousticSessionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN workerRole TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE sessions ADD COLUMN area TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN durationSeconds REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN sel REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN lasMin REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN l10 REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN l50 REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN l90 REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN doseNiosh REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN doseOsha REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN doseIso REAL NOT NULL DEFAULT 0.0")

                db.execSQL("ALTER TABLE measurement_points ADD COLUMN elapsedSeconds REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE measurement_points ADD COLUMN lasMin REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE measurement_points ADD COLUMN selSlice REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE measurement_points ADD COLUMN selCumulative REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE measurement_points ADD COLUMN doseNiosh REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE measurement_points ADD COLUMN doseOsha REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE measurement_points ADD COLUMN doseIso REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN doseOshaHc REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN doseColRes1792 REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE measurement_points ADD COLUMN doseOshaHc REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE measurement_points ADD COLUMN doseColRes1792 REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN lex8h REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN soundExposurePa2h REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN belowFloorSeconds REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN saturationSeconds REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN pausedSeconds REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN hasPauses INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN resumedNote TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE sessions ADD COLUMN calibrationOffset REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE measurement_points ADD COLUMN lex8h REAL NOT NULL DEFAULT 0.0")
            }
        }
    }
}
