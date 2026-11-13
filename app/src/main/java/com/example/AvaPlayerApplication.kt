package com.example

import android.app.Application
import com.example.data.local.PlaybackPreferences
import com.example.data.repository.MusicRepository
import com.example.headunit.AapProtocolClient
import com.example.headunit.AoapUsbManager
import com.example.headunit.DeviceCarDataProvider
import com.example.headunit.HeadunitPreferences
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

    lateinit var headunitPreferences: HeadunitPreferences
        private set

    lateinit var aoapUsbManager: AoapUsbManager
        private set

    lateinit var aapProtocolClient: AapProtocolClient
        private set

    lateinit var deviceCarDataProvider: DeviceCarDataProvider
        private set

    override fun onCreate() {
        super.onCreate()
        musicRepository = MusicRepository(this)
        playbackPreferences = PlaybackPreferences(this)
        headunitPreferences = HeadunitPreferences(this)
        aoapUsbManager = AoapUsbManager(this)
        aapProtocolClient = AapProtocolClient(aoapUsbManager)
        deviceCarDataProvider = DeviceCarDataProvider(this)

        aoapUsbManager.register()
        applicationScope.launch {
            musicRepository.ensureSeeded()
        }
    }

    override fun onTerminate() {
        aoapUsbManager.unregister()
        aapProtocolClient.release()
        deviceCarDataProvider.stopLocationUpdates()
        super.onTerminate()
    }
}
