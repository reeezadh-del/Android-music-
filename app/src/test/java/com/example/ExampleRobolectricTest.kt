package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.example.player.MusicPreferences
import com.example.player.MusicTrack
import com.example.player.RepeatModeOption
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
