package com.levelapp.app

import android.app.Application
import androidx.room.Room
import com.levelapp.data.database.AcousticDatabase
import com.levelapp.data.repository.SessionRepositoryImpl

class LevelAppApplication : Application() {
    lateinit var database: AcousticDatabase
        private set
    lateinit var sessionRepository: SessionRepositoryImpl
        private set

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            this,
            AcousticDatabase::class.java,
            "acoustic_db"
        ).build()
        sessionRepository = SessionRepositoryImpl(database.acousticSessionDao())
    }
}
