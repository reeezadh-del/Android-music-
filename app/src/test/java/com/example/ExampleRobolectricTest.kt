package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.test.core.app.ApplicationProvider
import com.example.ai.AiVoiceSearchRepository
import com.example.player.MusicPreferences
import com.example.player.MusicTrack
import com.example.player.RepeatModeOption
import com.example.player.TrendingPreset
import com.example.player.VoiceSearchHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app identity, permissions and media playback service are configured`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Ava Music", context.getString(R.string.app_name))

        val appInfo = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        )
        assertEquals(AvaMusicApplication::class.java.name, appInfo.className)

        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val perms = pkgInfo.requestedPermissions?.toSet().orEmpty()
        assertTrue(perms.contains(Manifest.permission.INTERNET))

        val sessionIntent = Intent("androidx.media3.session.MediaSessionService").apply {
            `package` = context.packageName
        }
        val services = context.packageManager.queryIntentServices(sessionIntent, 0)
        assertTrue(services.isNotEmpty())
    }

    @Test
    fun `music preferences persist favorites including iranian insta trend tracks`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = MusicPreferences(context)

        val onlineTrack = MusicTrack(
            id = "ir_musicsweb_101",
            title = "شبای تهران",
            artist = "علیرضا طلیسچی",
            album = "موزیک وب",
            durationMs = 0L,
            mediaUri = "https://dl.musicsweb.ir/music/test-320.mp3",
            artworkUri = "",
            isOnline = true,
            isRemix = true,
            isInstaTrend = true,
            isFullTrack = true,
            sourceLabel = ""
        )

        val added = prefs.toggleFavorite(onlineTrack.id, onlineTrack)
        assertTrue(added)
        prefs.setRepeatMode(RepeatModeOption.ONE)
        prefs.setShuffleEnabled(true)

        val loaded = prefs.prefsFlow.first()
        assertTrue(loaded.favoriteIds.contains("ir_musicsweb_101"))
        assertEquals(1, loaded.favoriteOnlineTracks.size)
        assertEquals("شبای تهران", loaded.favoriteOnlineTracks.first().title)
        assertEquals(RepeatModeOption.ONE, loaded.repeatMode)
        assertTrue(loaded.shuffleEnabled)

        val removed = prefs.toggleFavorite(onlineTrack.id, onlineTrack)
        assertFalse(removed)
        val reloaded = prefs.prefsFlow.first()
        assertTrue(reloaded.favoriteOnlineTracks.isEmpty())
    }

    @Test
    fun `google drive sqlite database writes and restores profile, favorites and settings`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = com.example.drive.GoogleDriveDatabaseManager(context)
        manager.deleteLocalDatabaseFile()

        val onlineTrack = MusicTrack(
            id = "ir_remix_77",
            title = "ریلز ترند",
            artist = "دی جی آوا",
            album = "",
            durationMs = 0L,
            mediaUri = "https://dl.example.com/reel-320.mp3",
            artworkUri = "",
            isOnline = true,
            isRemix = true,
            isInstaTrend = true,
            isFullTrack = true,
            sourceLabel = ""
        )
        val snapshot = com.example.drive.DriveDbSnapshot(
            uid = "uid_123",
            username = "Ali1",
            email = "ali@example.com",
            favoriteIds = setOf("ir_remix_77", "local_1"),
            favoriteOnlineTracks = listOf(onlineTrack),
            shuffleEnabled = true,
            repeatMode = RepeatModeOption.ALL,
            lastTrackId = "ir_remix_77",
            updatedAtEpochMs = 1700000000000L
        )

        val dbFile = manager.writeSnapshotToSqlite(snapshot)
        assertTrue(dbFile.exists())
        assertTrue(dbFile.length() > 0L)

        val restored = manager.readSnapshotFromSqlite()
        assertEquals("uid_123", restored?.uid)
        assertEquals("Ali1", restored?.username)
        assertEquals("ali@example.com", restored?.email)
        assertEquals(setOf("ir_remix_77", "local_1"), restored?.favoriteIds)
        assertEquals(1, restored?.favoriteOnlineTracks?.size)
        assertEquals("ریلز ترند", restored?.favoriteOnlineTracks?.first()?.title)
        assertTrue(restored?.shuffleEnabled == true)
        assertEquals(RepeatModeOption.ALL, restored?.repeatMode)
        assertEquals("ir_remix_77", restored?.lastTrackId)
    }

    @Test
    fun `username validator enforces maximum 4 characters`() {
        assertEquals(null, com.example.auth.UserAccountRepository.validateUsername("A"))
        assertEquals(null, com.example.auth.UserAccountRepository.validateUsername("Ali1"))
        assertEquals(null, com.example.auth.UserAccountRepository.validateUsername("ava_"))
        assertTrue(com.example.auth.UserAccountRepository.validateUsername("") != null)
        assertTrue(com.example.auth.UserAccountRepository.validateUsername("12345") != null)
        assertTrue(com.example.auth.UserAccountRepository.validateUsername("ab c") != null)
    }

    @Test
    fun `guest mode preference toggles and clears cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = MusicPreferences(context)
        prefs.setGuestMode(true)
        assertTrue(prefs.prefsFlow.first().isGuestMode)
        prefs.clearUserSessionData()
        assertFalse(prefs.prefsFlow.first().isGuestMode)
    }

    @Test
    fun `voice search helper builds speech intent and normalizes persian queries`() {
        val intent = VoiceSearchHelper.buildSpeechIntent("نام آهنگ را بگویید")
        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, intent.action)
        assertEquals("fa-IR", intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertEquals("نام آهنگ را بگویید", intent.getStringExtra(RecognizerIntent.EXTRA_PROMPT))

        assertEquals("شادمهر عقیلی", VoiceSearchHelper.normalizeSpokenSongQuery("پخش کن شادمهر عقیلي؟"))
        assertEquals("محسن یگانه", VoiceSearchHelper.normalizeSpokenSongQuery("محسن یگانه رو پخش کن"))
        assertEquals("آهنگ جدید", VoiceSearchHelper.normalizeSpokenSongQuery("آهنگ جدید"))

        val resultIntent = Intent().putStringArrayListExtra(
            RecognizerIntent.EXTRA_RESULTS,
            arrayListOf("   ", "جستجو کن ریمیکس شاد!")
        )
        assertEquals("ریمیکس شاد", VoiceSearchHelper.extractBestQuery(resultIntent))
        assertNull(VoiceSearchHelper.extractBestQuery(null))
    }

    @Test
    fun `ai voice search repository parses structured gemini json response cleanly`() {
        val rawJson = """
            {
              "heardText": "یه ریمیکس شاد برای ماشین از شادمهر بذار",
              "searchQuery": "شادمهر عقیلی",
              "matchedTrackIds": ["local_12", "local_45"],
              "suggestedMoodPreset": "CLUB_REMIX",
              "aiNote": "ریمیکس‌های شاد شادمهر عقیلی برای شما جستجو شد."
            }
        """.trimIndent()

        val parsed = AiVoiceSearchRepository.parseStructuredResponse(rawJson)
        assertEquals("یه ریمیکس شاد برای ماشین از شادمهر بذار", parsed.heardText)
        assertEquals("شادمهر عقیلی", parsed.searchQuery)
        assertEquals(listOf("local_12", "local_45"), parsed.matchedTrackIds)
        assertEquals(TrendingPreset.CLUB_REMIX, parsed.suggestedMoodPreset)
        assertEquals("ریمیکس‌های شاد شادمهر عقیلی برای شما جستجو شد.", parsed.aiNote)
    }

    @Test
    fun `android auto manifest metadata, media library service filters and browse tree are configured`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appInfo = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        )
        val carDescRes = appInfo.metaData?.getInt("com.google.android.gms.car.application") ?: 0
        assertEquals(R.xml.automotive_app_desc, carDescRes)

        listOf(
            "androidx.media3.session.MediaLibraryService",
            "androidx.media3.session.MediaSessionService",
            "android.media.browse.MediaBrowserService"
        ).forEach { action ->
            val intent = Intent(action).apply { `package` = context.packageName }
            val resolved = context.packageManager.queryIntentServices(intent, 0)
            assertTrue("Service action missing in manifest: $action", resolved.isNotEmpty())
        }

        val root = com.example.player.AutoMediaLibraryTree.buildRootItem()
        assertEquals(com.example.player.AutoMediaLibraryTree.ROOT_ID, root.mediaId)
        assertEquals(true, root.mediaMetadata.isBrowsable)
        assertEquals(false, root.mediaMetadata.isPlayable)

        val categories = com.example.player.AutoMediaLibraryTree.buildCategoryItems()
        assertEquals(3, categories.size)
        assertTrue(categories.all { it.mediaMetadata.isBrowsable == true && it.mediaMetadata.isPlayable == false })

        val sampleTrack = MusicTrack(
            id = "ms_42",
            title = "جاده شمال",
            artist = "بهنام بانی",
            album = "آلبوم تست",
            durationMs = 210_000L,
            mediaUri = "content://media/external/audio/media/42",
            artworkUri = "",
            isFavorite = true
        )
        val playable = com.example.player.AutoMediaLibraryTree.toPlayableMediaItem(sampleTrack)
        assertEquals("ms_42", playable.mediaId)
        assertEquals(false, playable.mediaMetadata.isBrowsable)
        assertEquals(true, playable.mediaMetadata.isPlayable)
        assertEquals("جاده شمال", playable.mediaMetadata.title?.toString())
        assertEquals("بهنام بانی", playable.mediaMetadata.artist?.toString())

        val filtered = com.example.player.AutoMediaLibraryTree.filterTracks(listOf(sampleTrack), "بهنام")
        assertEquals(1, filtered.size)
        assertEquals(listOf("ms_42"), com.example.player.AutoMediaLibraryTree.paginate(listOf("ms_42", "ms_99"), 0, 1))
    }

    @Test
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun `playback service suppresses absent car connection provider query and restores background executor`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertFalse(com.example.player.MusicPlaybackService.hasCarConnectionProvider(context))

        val originalExecutor = androidx.media3.common.util.BackgroundExecutor.get()
        val controller = org.robolectric.Robolectric.buildService(com.example.player.MusicPlaybackService::class.java)
        controller.create()
        try {
            assertTrue(androidx.media3.common.util.BackgroundExecutor.get() === originalExecutor)
            val service = controller.get()
            val logs = org.robolectric.shadows.ShadowLog.getLogsForTag("ActivityThread")
            assertTrue(logs.none { it.msg.contains("androidx.car.app.connection") })
            val observerRunnable = object : Runnable {
                override fun run() = Unit
            }
            assertFalse(com.example.player.MusicPlaybackService.isCarConnectionObserverTask(observerRunnable))
            service.onTaskRemoved(null)
        } finally {
            controller.destroy()
        }

        val providerInfo = android.content.pm.ProviderInfo().apply {
            authority = com.example.player.MusicPlaybackService.CAR_CONNECTION_AUTHORITY
            packageName = "com.google.android.projection.gearhead"
            name = "com.google.android.projection.gearhead.CarConnectionProvider"
        }
        org.robolectric.Shadows.shadowOf(context.packageManager).addOrUpdateProvider(providerInfo)
        assertTrue(com.example.player.MusicPlaybackService.hasCarConnectionProvider(context))
    }

    @Test
    fun `auto speed volume manager boosts STREAM_MUSIC with speed while connected and resets baseline on disconnect`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = context.applicationContext as android.app.Application
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 10, 0)

        org.robolectric.Shadows.shadowOf(app).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val prefs = MusicPreferences(context)
        prefs.setSpeedVolumeEnabled(true)
        prefs.setOnlyWhenAndroidAutoConnected(true)
        prefs.setSpeedVolumeSensitivity(com.example.player.SpeedVolumeSensitivity.STRONG)

        val stored = prefs.prefsFlow.first()
        assertTrue(stored.speedVolumeEnabled)
        assertTrue(stored.onlyWhenAndroidAutoConnected)
        assertEquals(com.example.player.SpeedVolumeSensitivity.STRONG, stored.speedVolumeSensitivity)

        val manager = com.example.player.AutoSpeedVolumeManager(context, prefs)
        manager.refreshPermissionState()
        manager.setCarConnectionOverrideForTest(true)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        manager.onSpeedSampleForTest(0f)
        val lowSpeedVol = manager.state.value.appliedVolumeFraction
        val lowStreamVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)

        manager.onSpeedSampleForTest(65f)
        manager.onSpeedSampleForTest(85f)
        manager.onSpeedSampleForTest(105f)
        val highSpeedVol = manager.state.value.appliedVolumeFraction
        val highStreamVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)

        assertTrue(manager.state.value.isAndroidAutoConnected)
        assertTrue(manager.state.value.hasLocationPermission)
        assertTrue("Expected highSpeedVol ($highSpeedVol) > lowSpeedVol ($lowSpeedVol)", highSpeedVol > lowSpeedVol)
        assertTrue("Expected STREAM_MUSIC at high speed ($highStreamVol) > low speed ($lowStreamVol)", highStreamVol > lowStreamVol)

        // Slowing back down to stationary must reduce STREAM_MUSIC smoothly as well.
        repeat(8) { manager.onSpeedSampleForTest(0f) }
        val slowedStreamVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        assertTrue("Expected STREAM_MUSIC after slowing down ($slowedStreamVol) < high speed ($highStreamVol)", slowedStreamVol < highStreamVol)

        // Disconnecting from Android Auto must restore the user's baseline STREAM_MUSIC (10)
        manager.setCarConnectionOverrideForTest(false)
        assertFalse(manager.state.value.isAndroidAutoConnected)
        assertEquals(1.0f, manager.state.value.appliedVolumeFraction, 0.0001f)
        assertEquals(0f, manager.state.value.currentSpeedKmh, 0.0001f)
        assertEquals(10, audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC))
    }

    @Test
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun `speed volume background service uses LocationManager and MediaSession to boost phone STREAM_MUSIC on speed increase`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = context.applicationContext as AvaMusicApplication
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 9, 0)

        org.robolectric.Shadows.shadowOf(app).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        // Verify manifest registration for SpeedVolumeService
        val serviceComponent = android.content.ComponentName(context, com.example.player.SpeedVolumeService::class.java)
        val serviceInfo = context.packageManager.getServiceInfo(serviceComponent, 0)
        assertFalse(serviceInfo.exported)
        assertEquals(
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            serviceInfo.foregroundServiceType
        )

        app.preferences.setSpeedVolumeEnabled(true)
        app.preferences.setOnlyWhenAndroidAutoConnected(false)
        app.preferences.setSpeedVolumeSensitivity(com.example.player.SpeedVolumeSensitivity.STRONG)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        val playbackController = org.robolectric.Robolectric.buildService(com.example.player.MusicPlaybackService::class.java)
        playbackController.create()
        val playbackBinder = playbackController.get().onBind(
            Intent("androidx.media3.session.MediaLibraryService")
        ) ?: android.os.Binder()
        org.robolectric.Shadows.shadowOf(app).setComponentNameAndServiceForBindService(
            android.content.ComponentName(context, com.example.player.MusicPlaybackService::class.java),
            playbackBinder
        )

        val speedController = org.robolectric.Robolectric.buildService(
            com.example.player.SpeedVolumeService::class.java,
            Intent(context, com.example.player.SpeedVolumeService::class.java)
                .setAction(com.example.player.SpeedVolumeService.ACTION_START_MONITORING)
        )
        speedController.create().startCommand(0, 0)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        try {
            val service = speedController.get()
            assertTrue(com.example.player.SpeedVolumeService.isServiceRunning)

            val stationaryLoc = android.location.Location(android.location.LocationManager.GPS_PROVIDER).apply {
                latitude = 35.6892
                longitude = 51.3890
                speed = 0f
                time = 1_000_000L
            }
            service.handleLocationSample(stationaryLoc)
            val lowVolume = service.currentComputedVolume()
            val lowStreamVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)

            val fastLoc1 = android.location.Location(android.location.LocationManager.GPS_PROVIDER).apply {
                latitude = 35.6910
                longitude = 51.3910
                speed = 25f // 90 km/h
                time = 1_002_000L
            }
            val fastLoc2 = android.location.Location(android.location.LocationManager.GPS_PROVIDER).apply {
                latitude = 35.6930
                longitude = 51.3930
                speed = 30f // 108 km/h
                time = 1_004_000L
            }
            service.handleLocationSample(fastLoc1)
            service.handleLocationSample(fastLoc2)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

            val highVolume = service.currentComputedVolume()
            val highStreamVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            assertTrue("Expected smoothed speed > 50 km/h, was ${service.currentComputedSpeed()}", service.currentComputedSpeed() > 50f)
            assertTrue("Expected highVolume ($highVolume) > lowVolume ($lowVolume)", highVolume > lowVolume)
            assertTrue("Expected STREAM_MUSIC at high speed ($highStreamVol) > low speed ($lowStreamVol)", highStreamVol > lowStreamVol)
            assertEquals(highVolume, app.autoSpeedVolumeManager.state.value.appliedVolumeFraction, 0.001f)
            assertEquals(highStreamVol, app.autoSpeedVolumeManager.state.value.streamVolumeIndex)
        } finally {
            speedController.destroy()
            playbackController.destroy()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        }

        assertFalse(com.example.player.SpeedVolumeService.isServiceRunning)
        assertEquals(9, audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC))
    }

    @Test
    fun `google session helper detects sha1 mismatch and formats cancellation diagnostics`() {
        val expectedSha1 = "711537411f7ce2dd0750814c33c4bf703ecfcb32"
        val mismatchSha1 = "aabbccddeeff00112233445566778899aabbccdd"

        assertEquals(
            "71:15:37:41:1F:7C:E2:DD:07:50:81:4C:33:C4:BF:70:3E:CF:CB:32",
            com.example.auth.GoogleSessionHelper.formatSha1WithColons(expectedSha1)
        )
        assertTrue(com.example.auth.GoogleSessionHelper.isExpectedDebugSha1(expectedSha1))
        assertFalse(com.example.auth.GoogleSessionHelper.isExpectedDebugSha1(mismatchSha1))

        val mismatchMessage = com.example.auth.GoogleSessionHelper.buildCancellationErrorMessage(
            rawErrorMessage = "Activity is cancelled by the user.",
            runtimeSha1Hex = mismatchSha1
        )
        assertTrue(mismatchMessage.contains("SHA-1"))
        assertTrue(mismatchMessage.contains("AA:BB:CC:DD"))

        val validSha1GmsDetail = com.example.auth.GoogleSessionHelper.buildCancellationErrorMessage(
            rawErrorMessage = "16: Cannot find a matching credential",
            runtimeSha1Hex = expectedSha1
        )
        assertTrue(validSha1GmsDetail.contains("16: Cannot find a matching credential"))
        assertTrue(validSha1GmsDetail.contains("روش جایگزین"))

        val validSha1GenericCancel = com.example.auth.GoogleSessionHelper.buildCancellationErrorMessage(
            rawErrorMessage = "Activity is cancelled by the user.",
            runtimeSha1Hex = expectedSha1
        )
        assertTrue(validSha1GenericCancel.contains("روش جایگزین"))
    }
}
