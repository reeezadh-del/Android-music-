package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.diagnostics.CrashReporter
import com.example.drive.GoogleDriveDatabaseManager
import com.example.player.AutoSpeedVolumeManager
import com.example.player.MusicLibraryRepository
import com.example.player.MusicPreferences
import com.example.player.TrendingIranianRepository
import com.example.player.UnblockedNetworkHelper
import com.google.firebase.FirebaseApp

class AvaMusicApplication : Application(), ImageLoaderFactory {

    lateinit var preferences: MusicPreferences
        private set

    lateinit var repository: MusicLibraryRepository
        private set

    lateinit var trendingRepository: TrendingIranianRepository
        private set

    lateinit var youTubeMusicRepository: com.example.player.YouTubeMusicRepository
        private set

    lateinit var trackDownloadManager: com.example.player.TrackDownloadManager
        private set

    lateinit var driveDbManager: GoogleDriveDatabaseManager
        private set

    lateinit var autoSpeedVolumeManager: AutoSpeedVolumeManager
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
        youTubeMusicRepository = com.example.player.YouTubeMusicRepository(
            trendingRepository = trendingRepository
        )
        trackDownloadManager = com.example.player.TrackDownloadManager(
            context = this,
            preferences = preferences,
            youTubeMusicRepository = youTubeMusicRepository,
            trendingRepository = trendingRepository
        )
        driveDbManager = GoogleDriveDatabaseManager(this)
        autoSpeedVolumeManager = AutoSpeedVolumeManager(this, preferences)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient { UnblockedNetworkHelper.createUnblockedOkHttpClient() }
            .crossfade(true)
            .build()
    }
}
