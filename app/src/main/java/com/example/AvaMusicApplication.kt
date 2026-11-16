package com.example

import android.app.Application
import com.example.diagnostics.CrashReporter
import com.example.drive.GoogleDriveDatabaseManager
import com.example.player.MusicLibraryRepository
import com.example.player.MusicPreferences
import com.example.player.TrendingIranianRepository
import com.google.firebase.FirebaseApp

class AvaMusicApplication : Application() {

    lateinit var preferences: MusicPreferences
        private set

    lateinit var repository: MusicLibraryRepository
        private set

    lateinit var trendingRepository: TrendingIranianRepository
        private set

    lateinit var driveDbManager: GoogleDriveDatabaseManager
        private set

    override fun onCreate() {
        CrashReporter.install(this)
        super.onCreate()
        runCatching {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
        }.onFailure { err ->
            CrashReporter.recordNonFatal("FIREBASE_INIT", err, context = this)
        }
        preferences = MusicPreferences(this)
        repository = MusicLibraryRepository(this, preferences)
        trendingRepository = TrendingIranianRepository()
        driveDbManager = GoogleDriveDatabaseManager(this)
    }
}
