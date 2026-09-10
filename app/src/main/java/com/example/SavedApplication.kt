package com.example

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.data.UserPreferences
import com.example.media.AudioPlayerManager

class SavedApplication : Application() {
    lateinit var database: AppDatabase
        private set
    lateinit var repository: AppRepository
        private set
    lateinit var userPreferences: UserPreferences
        private set
    lateinit var audioPlayerManager: AudioPlayerManager
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        repository = AppRepository(this, database)
        userPreferences = UserPreferences(this)
        audioPlayerManager = AudioPlayerManager()
    }
}
