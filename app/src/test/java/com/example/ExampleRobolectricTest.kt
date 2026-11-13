package com.example

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
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
        assertEquals("آوا پلیر", context.getString(R.string.app_name))

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
    fun `music repository seeds tracks and toggles favorites`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = MusicRepository(context)
        repo.ensureSeeded()

        val tracks = repo.getAllTracksOnce()
        assertTrue(tracks.size >= 4)

        val playlists = repo.getPlaylistsOnce()
        assertTrue(playlists.any { it.id == MusicRepository.PLAYLIST_DRIVING_MIX_ID })

        val lofi = tracks.firstOrNull { it.id == "seed_obsidian_lofi" }
        assertNotNull(lofi)
        val initialFav = lofi!!.isFavorite
        val newFav = repo.toggleFavorite(lofi.id)
        assertEquals(!initialFav, newFav)
        assertFalse(repo.getTrackById(lofi.id)!!.isFavorite == initialFav)
    }
}
