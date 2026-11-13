package com.example

import android.app.Application
import com.example.data.local.PlaybackPreferences
import com.example.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AvaPlayerApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var musicRepository: MusicRepository
        private set

    lateinit var playbackPreferences: PlaybackPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        musicRepository = MusicRepository(this)
        playbackPreferences = PlaybackPreferences(this)
        applicationScope.launch {
            musicRepository.ensureSeeded()
        }
    }
}
