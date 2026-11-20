package com.levelapp.app

import android.app.Application
import androidx.room.Room
import com.levelapp.app.service.AudioDataStream
import com.levelapp.data.database.AcousticDatabase
import com.levelapp.data.repository.SessionRepositoryImpl

class LevelAppApplication : Application() {
    lateinit var database: AcousticDatabase
        private set
    lateinit var sessionRepository: SessionRepositoryImpl
        private set

    override fun onCreate() {
        super.onCreate()
        AudioDataStream.init(this)
        database = Room.databaseBuilder(
            this,
            AcousticDatabase::class.java,
            "acoustic_db"
        )
            .addMigrations(
                AcousticDatabase.MIGRATION_1_2,
                AcousticDatabase.MIGRATION_2_3,
                AcousticDatabase.MIGRATION_3_4,
                AcousticDatabase.MIGRATION_4_5
            )
            .fallbackToDestructiveMigration()
            .build()
        sessionRepository = SessionRepositoryImpl(database.acousticSessionDao())
    }
}
