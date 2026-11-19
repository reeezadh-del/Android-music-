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

    @Test
    fun `speed volume calculator scales volume monotonically only when connected to android auto and enabled`() {
        val balanced = com.example.player.SpeedVolumeSensitivity.BALANCED

        // When not connected or disabled, always return full 1.0f volume regardless of speed
        assertEquals(
            1.0f,
            com.example.player.SpeedVolumeCalculator.computeTargetVolume(
                speedKmh = 0f,
                sensitivity = balanced,
                isAndroidAutoConnected = false,
                isEnabled = true
            ),
            0.0001f
        )
        assertEquals(
            1.0f,
            com.example.player.SpeedVolumeCalculator.computeTargetVolume(
                speedKmh = 30f,
                sensitivity = balanced,
                isAndroidAutoConnected = true,
                isEnabled = false
            ),
            0.0001f
        )

        // When connected & enabled, volume increases cleanly with speed
        val vLow = com.example.player.SpeedVolumeCalculator.computeTargetVolume(
            speedKmh = 5f,
            sensitivity = balanced,
            isAndroidAutoConnected = true,
            isEnabled = true
        )
        val vCity = com.example.player.SpeedVolumeCalculator.computeTargetVolume(
            speedKmh = 45f,
            sensitivity = balanced,
            isAndroidAutoConnected = true,
            isEnabled = true
        )
        val vHighway = com.example.player.SpeedVolumeCalculator.computeTargetVolume(
            speedKmh = 85f,
            sensitivity = balanced,
            isAndroidAutoConnected = true,
            isEnabled = true
        )
        val vMax = com.example.player.SpeedVolumeCalculator.computeTargetVolume(
            speedKmh = 120f,
            sensitivity = balanced,
            isAndroidAutoConnected = true,
            isEnabled = true
        )

        assertEquals(balanced.minVolume, vLow, 0.0001f)
        assertTrue(vCity > vLow)
        assertTrue(vHighway > vCity)
        assertTrue(vMax > vHighway)
        assertEquals(1.0f, vMax, 0.0001f)

        // When onlyWhenAndroidAutoConnected is false, background service scales volume even without Android Auto
        val standaloneCity = com.example.player.SpeedVolumeCalculator.computeTargetVolume(
            speedKmh = 60f,
            sensitivity = balanced,
            isAndroidAutoConnected = false,
            isEnabled = true,
            onlyWhenAndroidAutoConnected = false
        )
        val standaloneHighway = com.example.player.SpeedVolumeCalculator.computeTargetVolume(
            speedKmh = 100f,
            sensitivity = balanced,
            isAndroidAutoConnected = false,
            isEnabled = true,
            onlyWhenAndroidAutoConnected = false
        )
        assertTrue(standaloneCity > balanced.minVolume)
        assertTrue(standaloneHighway > standaloneCity)
    }

    @Test
    fun `favorite tracks repository sanitizes document ids and roundtrips track metadata`() {
        assertEquals(3, com.example.player.AppTab.entries.size)
        assertTrue(com.example.player.AppTab.entries.contains(com.example.player.AppTab.ONLINE))
        assertTrue(com.example.player.AppTab.entries.contains(com.example.player.AppTab.FAVORITES))
        assertEquals(2, com.example.player.OnlineSubSection.entries.size)
        assertTrue(com.example.player.OnlineSubSection.entries.contains(com.example.player.OnlineSubSection.TRENDING))
        assertTrue(com.example.player.OnlineSubSection.entries.contains(com.example.player.OnlineSubSection.DISCOVER))

        assertEquals(
            "ir_musicsweb_101",
            com.example.player.FavoriteTracksRepository.toFirestoreDocId("ir_musicsweb_101")
        )
        assertEquals(
            "ms_42",
            com.example.player.FavoriteTracksRepository.toFirestoreDocId("ms_42")
        )

        val dirtyId = "content://media/external:123/track?name=test"
        val sanitizedA = com.example.player.FavoriteTracksRepository.toFirestoreDocId(dirtyId)
        val sanitizedB = com.example.player.FavoriteTracksRepository.toFirestoreDocId(dirtyId)
        assertEquals(sanitizedA, sanitizedB)
        assertTrue(sanitizedA.matches(Regex("^[a-zA-Z0-9_\\-]+$")))
        assertTrue(sanitizedA.length in 1..128)

        val longId = "x".repeat(200)
        val sanitizedLong = com.example.player.FavoriteTracksRepository.toFirestoreDocId(longId)
        assertTrue(sanitizedLong.matches(Regex("^[a-zA-Z0-9_\\-]+$")))
        assertTrue(sanitizedLong.length <= 128)

        val sampleTrack = MusicTrack(
            id = "ir_remix_99",
            title = "شبای تهران",
            artist = "علیرضا طلیسچی",
            album = "ترند اینستا",
            durationMs = 212_000L,
            mediaUri = "https://dl.example.com/music/test.mp3",
            artworkUri = "https://dl.example.com/art.jpg",
            isFavorite = true,
            isImported = false,
            isOnline = true,
            isRemix = true,
            isInstaTrend = true,
            isFullTrack = true
        )
        val createMap = com.example.player.FavoriteTracksRepository.buildCreatePayload(
            userId = "uid_alice",
            favoriteId = "ir_remix_99",
            track = sampleTrack,
            addedAtMs = 1700000000000L
        )
        assertEquals("uid_alice", createMap["userId"])
        assertEquals("ir_remix_99", createMap["favoriteId"])
        assertEquals("ir_remix_99", createMap["trackId"])
        assertFalse(createMap.containsKey("id"))
        assertEquals(true, createMap["onlineTrack"])
        assertEquals(true, createMap["remixTrack"])

        val firestoreModel = com.example.player.FirestoreFavoriteTrack(
            id = "ir_remix_99",
            userId = "uid_alice",
            favoriteId = "ir_remix_99",
            trackId = sampleTrack.id,
            title = sampleTrack.title,
            artist = sampleTrack.artist,
            album = sampleTrack.album,
            durationMs = sampleTrack.durationMs,
            mediaUri = sampleTrack.mediaUri,
            artworkUri = sampleTrack.artworkUri,
            importedTrack = sampleTrack.isImported,
            onlineTrack = sampleTrack.isOnline,
            remixTrack = sampleTrack.isRemix,
            instaTrend = sampleTrack.isInstaTrend,
            fullTrack = sampleTrack.isFullTrack,
            addedAtMs = 1700000000000L
        )
        val roundTripped = firestoreModel.toMusicTrack()
        assertEquals(sampleTrack.id, roundTripped.id)
        assertEquals(sampleTrack.title, roundTripped.title)
        assertEquals(sampleTrack.artist, roundTripped.artist)
        assertEquals(sampleTrack.mediaUri, roundTripped.mediaUri)
        assertTrue(roundTripped.isFavorite)
        assertTrue(roundTripped.isOnline)
        assertTrue(roundTripped.isRemix)
        assertTrue(roundTripped.isInstaTrend)
    }

    @Test
    fun `track social repository aggregates reactions and builds rules-compliant payloads without id`() {
        val track = MusicTrack(
            id = "ir_musicsweb_555",
            title = "شبای تهران",
            artist = "علیرضا طلیسچی",
            album = "ترند اینستا",
            durationMs = 200_000L,
            mediaUri = "https://dl.example.com/music/555.mp3",
            artworkUri = "",
            isOnline = true
        )
        val trackKey = com.example.player.TrackSocialRepository.toTrackKey(track)
        assertEquals("ir_musicsweb_555", trackKey)

        val reactions = listOf(
            com.example.player.FirestoreTrackReaction(
                id = "uid_alice",
                userId = "uid_alice",
                trackKey = trackKey,
                trackTitle = track.title,
                emojiType = "FIRE",
                authorUsername = "Ali1"
            ),
            com.example.player.FirestoreTrackReaction(
                id = "uid_bob",
                userId = "uid_bob",
                trackKey = trackKey,
                trackTitle = track.title,
                emojiType = "FIRE",
                authorUsername = "Bob2"
            ),
            com.example.player.FirestoreTrackReaction(
                id = "uid_sara",
                userId = "uid_sara",
                trackKey = trackKey,
                trackTitle = track.title,
                emojiType = "LOVE",
                authorUsername = "Sara"
            )
        )
        val summary = com.example.player.TrackSocialRepository.aggregateReactions(
            trackKey = trackKey,
            reactions = reactions,
            currentUid = "uid_alice",
            commentCount = 4
        )
        assertEquals(2, summary.counts[com.example.player.TrackReactionType.FIRE])
        assertEquals(1, summary.counts[com.example.player.TrackReactionType.LOVE])
        assertEquals(3, summary.totalReactions)
        assertEquals(com.example.player.TrackReactionType.FIRE, summary.userReaction)
        assertEquals(4, summary.commentCount)

        val reactCreate = com.example.player.TrackSocialRepository.buildReactionCreatePayload(
            userId = "uid_alice",
            trackKey = trackKey,
            trackTitle = track.title,
            reaction = com.example.player.TrackReactionType.VIBE,
            authorUsername = "Ali1"
        )
        assertFalse(reactCreate.containsKey("id"))
        assertEquals("uid_alice", reactCreate["userId"])
        assertEquals(trackKey, reactCreate["trackKey"])
        assertEquals("VIBE", reactCreate["emojiType"])
        assertTrue(reactCreate.containsKey("createdAt"))
        assertTrue(reactCreate.containsKey("updatedAt"))

        val reactUpdate = com.example.player.TrackSocialRepository.buildReactionUpdatePayload(
            reaction = com.example.player.TrackReactionType.CLAP,
            trackTitle = track.title,
            authorUsername = "Ali1"
        )
        assertFalse(reactUpdate.containsKey("id"))
        assertFalse(reactUpdate.containsKey("userId"))
        assertEquals("CLAP", reactUpdate["emojiType"])
        assertTrue(reactUpdate.containsKey("updatedAt"))

        val commentCreate = com.example.player.TrackSocialRepository.buildCommentCreatePayload(
            commentId = "cmt_abc123",
            trackKey = trackKey,
            trackTitle = track.title,
            userId = "uid_alice",
            authorUsername = "Ali1",
            text = "  خیلی آهنگ خفنی بود!  "
        )
        assertFalse(commentCreate.containsKey("id"))
        assertEquals("cmt_abc123", commentCreate["commentId"])
        assertEquals(trackKey, commentCreate["trackKey"])
        assertEquals("uid_alice", commentCreate["userId"])
        assertEquals("Ali1", commentCreate["authorUsername"])
        assertEquals("خیلی آهنگ خفنی بود!", commentCreate["text"])
        assertTrue(commentCreate.containsKey("createdAt"))
        assertTrue(commentCreate.containsKey("updatedAt"))

        assertEquals(null, com.example.player.TrackSocialRepository.sanitizeCommentText("   "))
        assertEquals(
            com.example.player.TrackSocialRepository.MAX_COMMENT_LENGTH,
            com.example.player.TrackSocialRepository.sanitizeCommentText("آ".repeat(700))?.length
        )
    }

    @Test
    fun `extractLyricsFromHtml extracts clean persian lyrics and ignores download footers`() {
        val htmlWithLyrics = """
            <p>دانلود آهنگ جدید رضا بهرام بنام شب</p>
            <h3>متن آهنگ شب رضا بهرام</h3>
            <p>───┤ ♩♬♫♪♭ ├───</p>
            <p>امشب دلم هوای تو را کرده است<br/>
            باران دوباره یاد تو را زنده کرده است<br/>
            ای کاش بیایی و ببینی حالم را</p>
            <p>───┤ ♩♬♫♪♭ ├───</p>
            <p>دانلود آهنگ با کیفیت 320</p>
            <a href="https://cdn.example.com/song-320.mp3">لینک مستقیم</a>
        """.trimIndent()

        val extracted = TrendingIranianRepository.extractLyricsFromHtml(htmlWithLyrics)
        assertTrue(extracted.contains("امشب دلم هوای تو را کرده است"))
        assertTrue(extracted.contains("باران دوباره یاد تو را زنده کرده است"))
        assertFalse(extracted.contains("دانلود آهنگ با کیفیت 320"))
        assertFalse(extracted.contains("───┤"))

        val htmlWithoutLyrics = """
            <p>دانلود آهنگ جدید با کیفیت عالی</p>
            <a href="https://cdn.example.com/track.mp3">دانلود MP3</a>
        """.trimIndent()
        assertEquals("", TrendingIranianRepository.extractLyricsFromHtml(htmlWithoutLyrics))
    }

    @Test
    fun `deduplicateAndRank preserves lyrics when merging duplicate tracks`() {
        val noLyricsTrack = MusicTrack(
            id = "ir_upmusics_1",
            title = "شب",
            artist = "رضا بهرام",
            album = "ترند اینستا",
            durationMs = 0L,
            mediaUri = "https://cdn.a.ir/shab-320.mp3",
            artworkUri = "https://cdn.a.ir/cover.jpg",
            isOnline = true,
            isInstaTrend = true,
            trendScore = 50_000L,
            lyrics = ""
        )
        val withLyricsTrack = noLyricsTrack.copy(
            id = "ir_musicsweb_2",
            mediaUri = "https://cdn.b.ir/shab-128.mp3",
            artworkUri = "",
            trendScore = 40_000L,
            lyrics = "امشب دلم هوای تو را کرده است\nباران دوباره یاد تو را زنده کرده است"
        )

        val merged = TrendingIranianRepository.deduplicateAndRank(
            listOf(noLyricsTrack, withLyricsTrack),
            TrendingCategory.ALL
        )
        assertEquals(1, merged.size)
        assertTrue(merged.first().lyrics.contains("امشب دلم هوای تو را کرده است"))
        assertEquals("https://cdn.a.ir/cover.jpg", merged.first().artworkUri)
    }
}

