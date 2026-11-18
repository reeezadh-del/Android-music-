package com.example

import android.content.ComponentName
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Binder
import android.util.DisplayMetrics
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.diagnostics.CrashReporter
import com.example.player.MusicPlaybackService
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivityStartupTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `main activity launches and displays auth gate without crashing`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("google_sign_in_button").assertIsDisplayed()
    }

    @Test
    fun `drawable bitmaps do not exceed safe texture size on xxxhdpi screen`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val res = context.resources

        val opts = BitmapFactory.Options().apply {
            inTargetDensity = DisplayMetrics.DENSITY_XXXHIGH
        }
        val fgBitmap = BitmapFactory.decodeResource(res, R.drawable.ic_ava_music_fg_1794792983705, opts)
        val artBitmap = BitmapFactory.decodeResource(res, R.drawable.img_default_album_art_1794792985728, opts)

        assertTrue(
            "Foreground logo bitmap too large at xxxhdpi: ${fgBitmap.width}x${fgBitmap.height}",
            fgBitmap.width <= 1024 && fgBitmap.height <= 1024
        )
        assertTrue(
            "Default album art bitmap too large at xxxhdpi: ${artBitmap.width}x${artBitmap.height}",
            artBitmap.width <= 1024 && artBitmap.height <= 1024
        )
    }

    @Test
    fun `firebase app initializes or is handled cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(FirebaseApp.getApps(context).isNotEmpty())
    }

    @Test
    fun `main activity recovers cleanly even if FirebaseApp was cleared before launch`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val existingApps = FirebaseApp.getApps(context).toList()
        try {
            FirebaseApp.clearInstancesForTest()
            val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
            assertFalse(controller.get().isFinishing)
            controller.pause().stop().destroy()
        } finally {
            if (existingApps.isNotEmpty() && FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
        }
    }

    @Test
    fun `crash reporter persists fatal crash and shows recovery dialog with copy and share`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        try {
            CrashReporter.clearAllReports(context)
            val saved = CrashReporter.recordFatalCrash(
                context = context,
                throwable = IllegalStateException("Boom on real phone"),
                threadName = "main"
            )

            val pending = CrashReporter.getPendingFatalCrash(context)
            assertNotNull(pending)
            assertEquals(saved.reportId, pending?.reportId)

            val formatted = CrashReporter.formatSingleReport(saved)
            assertTrue(formatted.contains("IllegalStateException"))
            assertTrue(formatted.contains("Boom on real phone"))

            composeRule.waitForIdle()
            composeRule.onNodeWithTag("crash_copy_button").assertIsDisplayed()
            composeRule.onNodeWithTag("crash_share_button").assertIsDisplayed()
            composeRule.onNodeWithTag("crash_dismiss_button").performClick()
            composeRule.waitForIdle()

            assertNull(CrashReporter.getPendingFatalCrash(context))
        } finally {
            CrashReporter.clearAllReports(context)
        }
    }

    @Test
    fun `guest sign in enters player screen without firebase auth and allows exiting`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = context.applicationContext as AvaMusicApplication
        Shadows.shadowOf(app).setComponentNameAndServiceForBindService(
            ComponentName(app, MusicPlaybackService::class.java),
            Binder()
        )
        try {
            kotlinx.coroutines.runBlocking { app.preferences.setGuestMode(false) }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("guest_sign_in_button").assertIsDisplayed().performClick()
            composeRule.waitUntil(3_000) {
                composeRule.onAllNodesWithTag("account_profile_button").fetchSemanticsNodes().isNotEmpty()
            }

            composeRule.onNodeWithTag("account_profile_button").assertIsDisplayed()
            composeRule.onNodeWithTag("nav_tab_library").assertIsDisplayed()
            composeRule.onNodeWithTag("voice_search_library_button").assertIsDisplayed().performClick()
            composeRule.waitForIdle()
            if (composeRule.onAllNodesWithTag("ai_voice_dismiss_button").fetchSemanticsNodes().isNotEmpty()) {
                composeRule.onNodeWithTag("ai_voice_system_fallback_button").assertIsDisplayed()
                composeRule.onNodeWithTag("ai_voice_dismiss_button").performClick()
                composeRule.waitForIdle()
            } else if (composeRule.onAllNodesWithTag("ai_voice_cancel_button").fetchSemanticsNodes().isNotEmpty()) {
                composeRule.onNodeWithTag("ai_voice_cancel_button").performClick()
                composeRule.waitForIdle()
            }

            composeRule.onNodeWithTag("nav_tab_online").performClick()
            composeRule.waitUntil(3_000) {
                composeRule.onAllNodesWithTag("online_screen").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("online_screen").assertIsDisplayed()
            composeRule.onNodeWithTag("online_subtab_TRENDING").assertIsDisplayed()
            composeRule.onNodeWithTag("online_subtab_DISCOVER").assertIsDisplayed()
            composeRule.onNodeWithTag("voice_search_trending_button").assertIsDisplayed()

            composeRule.onNodeWithTag("online_subtab_DISCOVER").performClick()
            composeRule.waitUntil(3_000) {
                composeRule.onAllNodesWithTag("discover_search_input").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("discover_search_input").assertIsDisplayed()
            composeRule.onNodeWithTag("online_style_NEW_HITS").assertIsDisplayed()

            composeRule.onNodeWithTag("online_subtab_TRENDING").performClick()
            composeRule.waitUntil(3_000) {
                composeRule.onAllNodesWithTag("voice_search_trending_button").fetchSemanticsNodes().isNotEmpty()
            }

            composeRule.onNodeWithTag("nav_tab_favorites").performClick()
            composeRule.waitUntil(3_000) {
                composeRule.onAllNodesWithTag("favorites_screen").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("favorites_screen").assertIsDisplayed()
            composeRule.onNodeWithTag("favorites_empty_card").assertIsDisplayed()
            composeRule.onNodeWithTag("favorites_upgrade_google_button").assertIsDisplayed()

            val favTrack = com.example.player.MusicTrack(
                id = "fav_test_1",
                title = "بارون",
                artist = "محسن یگانه",
                album = "تک‌آهنگ",
                durationMs = 195_000L,
                mediaUri = "https://example.com/baroon.mp3",
                artworkUri = "",
                isOnline = true
            )
            kotlinx.coroutines.runBlocking {
                app.preferences.toggleFavorite(favTrack.id, favTrack)
            }
            composeRule.waitUntil(3_000) {
                composeRule.onAllNodesWithTag("favorite_row_fav_test_1").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("favorite_row_fav_test_1").assertIsDisplayed()
            composeRule.onNodeWithTag("favorites_play_all_button").assertIsDisplayed()
            composeRule.onNodeWithTag("favorites_shuffle_all_button").assertIsDisplayed()

            composeRule.onNodeWithTag("favorites_remove_btn_fav_test_1").performClick()
            composeRule.waitUntil(3_000) {
                composeRule.onAllNodesWithTag("favorites_empty_card").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("favorites_empty_card").assertIsDisplayed()

            composeRule.onNodeWithTag("nav_tab_library").performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("auto_speed_volume_button").assertIsDisplayed().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("speed_volume_switch").assertIsDisplayed()
            composeRule.onNodeWithTag("speed_volume_sensitivity_STRONG").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("speed_volume_close_button").performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("driving_mode_button").assertIsDisplayed().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("driving_mode_overlay").assertIsDisplayed()
            composeRule.onNodeWithTag("driving_mode_speed_value").assertIsDisplayed()
            composeRule.onNodeWithTag("driving_mode_volume_value").assertIsDisplayed()
            composeRule.onNodeWithTag("driving_mode_play_pause").assertIsDisplayed()
            composeRule.onNodeWithTag("driving_mode_sensitivity_BALANCED").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("driving_mode_exit_button").performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("account_profile_button").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("dialog_guest_google_login_button").assertIsDisplayed()
            composeRule.onNodeWithTag("dialog_sign_out_button").performClick()
            composeRule.waitUntil(3_000) {
                composeRule.onAllNodesWithTag("google_sign_in_button").fetchSemanticsNodes().isNotEmpty()
            }

            composeRule.onNodeWithTag("google_sign_in_button").assertIsDisplayed()
            composeRule.onNodeWithTag("guest_sign_in_button").assertIsDisplayed()
        } finally {
            kotlinx.coroutines.runBlocking { app.preferences.setGuestMode(false) }
        }
    }
}
