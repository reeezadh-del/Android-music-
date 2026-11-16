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
