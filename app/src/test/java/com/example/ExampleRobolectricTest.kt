package com.example

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Track
import com.example.data.repository.MusicRepository
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
        assertEquals("melodi", context.getString(R.string.app_name))

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
