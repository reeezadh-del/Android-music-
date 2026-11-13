package com.example

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Track
import com.example.data.repository.MusicRepository
import com.example.headunit.CarPlayDockSide
import com.example.headunit.CarPlayThemeMode
import com.example.headunit.HeadunitPreferences
import com.example.headunit.HurConnectionMode
import com.example.headunit.HurResolution
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app metadata and Android Auto service are configured`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("CarPlay HUR", context.getString(R.string.app_name))

        val appInfo = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        )
        val carMetaResId = appInfo.metaData?.getInt("com.google.android.gms.car.application") ?: 0
        assertEquals(R.xml.automotive_app_desc, carMetaResId)

        val mediaLibraryIntent = Intent("androidx.media3.session.MediaLibraryService").apply {
            `package` = context.packageName
        }
        val resolvedServices = context.packageManager.queryIntentServices(mediaLibraryIntent, 0)
        assertTrue(resolvedServices.isNotEmpty())
        assertTrue(resolvedServices.first().serviceInfo.exported)
    }

    @Test
    fun `headunit preferences persist connection mode, CarPlay theme, and display settings`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = HeadunitPreferences(context)

        prefs.updateSettings {
            it.copy(
                connectionMode = HurConnectionMode.SELF_MODE,
                resolution = HurResolution.FHD_1080P,
                dpi = 200,
                targetFps = 60,
                carPlayTheme = CarPlayThemeMode.DAY_FROST,
                dockSide = CarPlayDockSide.RIGHT
            )
        }

        val loaded = prefs.settingsFlow.first()
        assertEquals(HurConnectionMode.SELF_MODE, loaded.connectionMode)
        assertEquals(HurResolution.FHD_1080P, loaded.resolution)
        assertEquals(200, loaded.dpi)
        assertEquals(60, loaded.targetFps)
        assertEquals(CarPlayThemeMode.DAY_FROST, loaded.carPlayTheme)
        assertEquals(CarPlayDockSide.RIGHT, loaded.dockSide)
    }

    @Test
    fun `music repository has no default seed tracks and supports saving and toggling favorites`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = MusicRepository(context)
        repo.ensureSeeded()

        val initialTracks = repo.getAllTracksOnce()
        assertTrue(initialTracks.none { it.id.startsWith("seed_") })

        val playlists = repo.getPlaylistsOnce()
        assertTrue(playlists.any { it.id == MusicRepository.PLAYLIST_DRIVING_MIX_ID })

        val deviceTrack = Track(
            id = "dev_101",
            title = "آهنگ نمونه دستگاه",
            artist = "هنرمند دستگاه",
            album = "آلبوم دستگاه",
            durationMs = 180_000L,
            mediaUri = "content://media/external/audio/media/101",
            artworkUri = "",
            isFavorite = false,
            isImported = true
        )
        repo.saveOnlineTrack(deviceTrack)

        val saved = repo.getTrackById("dev_101")
        assertNotNull(saved)
        val initialFav = saved!!.isFavorite
        val newFav = repo.toggleFavorite(saved.id)
        assertEquals(!initialFav, newFav)
        assertFalse(repo.getTrackById(saved.id)!!.isFavorite == initialFav)
    }
}
