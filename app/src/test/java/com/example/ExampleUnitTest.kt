package com.example

import com.example.player.IranianMusicSite
import com.example.player.MusicTrack
import com.example.player.RepeatModeOption
import com.example.player.TrackFilter
import com.example.player.TrendingCategory
import com.example.player.TrendingIranianRepository
import com.example.player.TrendingPreset
import com.example.ui.components.formatDuration
import com.example.ui.components.formatPlayCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `formatDuration and formatPlayCount format cleanly`() {
        assertEquals("00:00", formatDuration(0L))
        assertEquals("01:05", formatDuration(65_000L))
        assertEquals("03:45", formatDuration(225_000L))
        assertEquals("850", formatPlayCount(850L))
        assertEquals("12.5K", formatPlayCount(12_500L))
        assertEquals("1.4M", formatPlayCount(1_400_000L))
    }

    @Test
    fun `persian title parser extracts artist cleanly and never exposes site names`() {
        val parsed = TrendingIranianRepository.parsePersianPostTitle(
            "دانلود آهنگ جدید ترند اینستاگرام شادمهر عقیلی بنام قلبم با کیفیت 320 و 128 از موزیک وب",
            isRemix = false
        )
        assertEquals("شادمهر عقیلی", parsed.artist)
        assertEquals("قلبم", parsed.title)

        val dashParsed = TrendingIranianRepository.parsePersianPostTitle(
            "دانلود ریمیکس جدید مهراد هیدن – کجا میری ترند اینستاگرام ریمیکس گرام",
            isRemix = true
        )
        assertEquals("مهراد هیدن", dashParsed.artist)
        assertEquals("کجا میری", dashParsed.title)

        val fallback = TrendingIranianRepository.parsePersianPostTitle(
            "دانلود آهنگ شبای تهران آپ موزیک",
            isRemix = false
        )
        assertEquals("شبای تهران", fallback.title)
        assertEquals("هنرمند ایرانی", fallback.artist)
    }

    @Test
    fun `wordpress json and html parsers hide site label and extract mp3`() {
        val siteA = IranianMusicSite("musicsweb", "موزیک وب", "https://musicsweb.ir")
        val siteB = IranianMusicSite("remixgram", "ریمیکس‌گرام", "https://remixgram.com")

        val wpJson = """
            [
              {
                "id": 101,
                "date": "2026-11-10T12:00:00",
                "title": { "rendered": "دانلود ریمیکس ترند اینستاگرام علیرضا طلیسچی بنام شبای تهران" },
                "content": {
                  "rendered": "<p><img src=\"https://musicsweb.ir/uploads/cover1.jpg\" /></p><a href=\"https://dl.musicsweb.ir/music/shabaye-tehran-remix-128.mp3\">128</a><a href=\"https://dl.musicsweb.ir/music/shabaye-tehran-remix-320.mp3\">320</a>"
                },
                "excerpt": { "rendered": "<p>ریمیکس وایرال ریلز اینستاگرام</p>" }
              }
            ]
        """.trimIndent()

        val wpTracks = TrendingIranianRepository.parseWordPressPostsJson(
            rawJson = wpJson,
            site = siteA,
            preset = TrendingPreset.HOT,
            favoriteIds = setOf("ir_musicsweb_101")
        )
        assertEquals(1, wpTracks.size)
        assertTrue(wpTracks[0].isRemix)
        assertTrue(wpTracks[0].isInstaTrend)
        assertTrue(wpTracks[0].isFavorite)
        assertEquals("", wpTracks[0].sourceLabel)
        assertEquals("https://dl.musicsweb.ir/music/shabaye-tehran-remix-320.mp3", wpTracks[0].mediaUri)

        val postHtml = """
            <html>
              <head>
                <meta property="og:image" content="https://remixgram.com/uploads/art2.jpg" />
                <meta property="article:published_time" content="2026-11-12T10:00:00+03:30" />
              </head>
              <body>
                <h1>دانلود آهنگ ترند اینستاگرام رضا بهرام بنام دیوانه</h1>
                <audio controls><source src="https://dl.remixgram.com/tracks/divaneh-320.mp3" type="audio/mpeg" /></audio>
              </body>
            </html>
        """.trimIndent()

        val htmlTrack = TrendingIranianRepository.parseSinglePostHtml(
            postHtml = postHtml,
            postUrl = "https://remixgram.com/divaneh/",
            site = siteB,
            preset = TrendingPreset.HOT
        )
        assertNotNull(htmlTrack)
        assertEquals("دیوانه", htmlTrack!!.title)
        assertEquals("رضا بهرام", htmlTrack.artist)
        assertEquals("", htmlTrack.sourceLabel)
        assertFalse(htmlTrack.isRemix)
        assertTrue(htmlTrack.isInstaTrend)
    }

    @Test
    fun `deduplicateAndRank eliminates duplicate songs across persian character variants`() {
        val v1 = MusicTrack(
            id = "ir_musicsweb_1",
            title = "شبای تهران",
            artist = "علیرضا طلیسچی",
            album = "ترند اینستا",
            durationMs = 0L,
            mediaUri = "https://dl.musicsweb.ir/shabaye-tehran-320.mp3",
            artworkUri = "https://musicsweb.ir/cover.jpg",
            isOnline = true,
            isInstaTrend = true,
            trendScore = 30_000L
        )
        val v2 = MusicTrack(
            id = "ir_upmusics_2",
            title = "شباي تهران (ورژن اصلی)",
            artist = "عليرضا طليسچي",
            album = "ترند اینستا",
            durationMs = 0L,
            mediaUri = "https://dl.upmusics.com/shabaye-tehran-128.mp3",
            artworkUri = "",
            isOnline = true,
            isInstaTrend = true,
            trendScore = 25_000L
        )
        val v3 = MusicTrack(
            id = "ir_rozmusic_3",
            title = "شبای تهران علیرضا طلیسچی",
            artist = "هنرمند ایرانی",
            album = "ترند اینستا",
            durationMs = 0L,
            mediaUri = "https://dl.rozmusic.com/other.mp3",
            artworkUri = "",
            isOnline = true,
            isInstaTrend = true,
            trendScore = 22_000L
        )
        val distinct = MusicTrack(
            id = "ir_remixgram_4",
            title = "کجا میری",
            artist = "مهراد هیدن",
            album = "ریمیکس ترند اینستا",
            durationMs = 0L,
            mediaUri = "https://dl.remixgram.com/koja-miri.mp3",
            artworkUri = "",
            isOnline = true,
            isRemix = true,
            isInstaTrend = true,
            trendScore = 28_000L
        )

        val ranked = TrendingIranianRepository.deduplicateAndRank(
            listOf(v1, v2, v3, distinct),
            TrendingCategory.ALL
        )
        assertEquals(2, ranked.size)
        assertEquals("شبای تهران", ranked[0].title)
        assertEquals("علیرضا طلیسچی", ranked[0].artist)
        assertTrue(ranked[0].trendScore > 30_000L)
        assertEquals("کجا میری", ranked[1].title)
    }

    @Test
    fun `configured trending sources are strictly iranian music sites`() {
        assertTrue(TrendingIranianRepository.IRANIAN_SITES.isNotEmpty())
        assertTrue(
            TrendingIranianRepository.IRANIAN_SITES.none { site ->
                site.baseUrl.contains("audius") ||
                    site.baseUrl.contains("deezer") ||
                    site.baseUrl.contains("apple")
            }
        )
        assertEquals(2, TrackFilter.entries.size)
        assertEquals(3, RepeatModeOption.entries.size)
    }
}
