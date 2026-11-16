package com.example

import android.app.Application
import com.example.player.MusicLibraryRepository
import com.example.player.MusicPreferences
import com.example.player.TrendingIranianRepository

class AvaMusicApplication : Application() {

    lateinit var preferences: MusicPreferences
        private set

    lateinit var repository: MusicLibraryRepository
        private set

    lateinit var trendingRepository: TrendingIranianRepository
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = MusicPreferences(this)
        repository = MusicLibraryRepository(this, preferences)
        trendingRepository = TrendingIranianRepository()
    }
}
