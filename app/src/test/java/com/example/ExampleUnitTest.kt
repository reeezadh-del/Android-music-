package com.example

import com.example.player.AppThemeMode
import com.example.player.IranianMusicSite
import com.example.player.MusicTrack
import com.example.player.RepeatModeOption
import com.example.player.TrackFilter
import com.example.player.TrendingCategory
import com.example.player.TrendingIranianRepository
import com.example.player.TrendingPreset
import com.example.player.YouTubeMusicCategory
import com.example.player.YouTubeMusicRepository
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
        assertEquals(3, TrackFilter.entries.size)
        assertTrue(TrackFilter.entries.contains(TrackFilter.DOWNLOADED))
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
        assertEquals(3, com.example.player.OnlineSubSection.entries.size)
        assertTrue(com.example.player.OnlineSubSection.entries.contains(com.example.player.OnlineSubSection.TRENDING))
        assertTrue(com.example.player.OnlineSubSection.entries.contains(com.example.player.OnlineSubSection.DISCOVER))
        assertTrue(com.example.player.OnlineSubSection.entries.contains(com.example.player.OnlineSubSection.YOUTUBE_MUSIC))

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
    fun `app theme mode enum defines night, day and system presets in persian`() {
        assertEquals(listOf(AppThemeMode.DARK, AppThemeMode.LIGHT, AppThemeMode.SYSTEM), AppThemeMode.entries)
        assertEquals("شب", AppThemeMode.DARK.labelFa)
        assertEquals("روز", AppThemeMode.LIGHT.labelFa)
        assertEquals("سیستم", AppThemeMode.SYSTEM.labelFa)
    }

    @Test
    fun `youtube music repository parses search, stream audio urls, duration and thumbnails`() {
        assertEquals(6, YouTubeMusicCategory.entries.size)
        assertEquals(225_000L, YouTubeMusicRepository.parseDurationLabelToMs("3:45"))
        assertEquals(3_661_000L, YouTubeMusicRepository.parseDurationLabelToMs("1:01:01"))
        assertEquals(0L, YouTubeMusicRepository.parseDurationLabelToMs("invalid"))

        assertEquals(
            "https://lh3.googleusercontent.com/art=w544-h544-l90-rj",
            YouTubeMusicRepository.upgradeThumbnailUrl(
                "https://lh3.googleusercontent.com/art=w120-h120-l90-rj",
                "dQw4w9WgXcQ"
            )
        )
        assertEquals(
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
            YouTubeMusicRepository.upgradeThumbnailUrl("", "dQw4w9WgXcQ")
        )

        val searchJson = """
            {
              "contents": {
                "tabbedSearchResultsRenderer": {
                  "tabs": [
                    {
                      "tabRenderer": {
                        "content": {
                          "sectionListRenderer": {
                            "contents": [
                              {
                                "musicShelfRenderer": {
                                  "contents": [
                                    {
                                      "musicResponsiveListItemRenderer": {
                                        "playlistItemData": { "videoId": "abc123XYZ" },
                                        "thumbnail": {
                                          "musicThumbnailRenderer": {
                                            "thumbnail": {
                                              "thumbnails": [
                                                { "url": "https://lh3.googleusercontent.com/cover=w120-h120-l90-rj" }
                                              ]
                                            }
                                          }
                                        },
                                        "flexColumns": [
                                          {
                                            "musicResponsiveListItemFlexColumnRenderer": {
                                              "text": {
                                                "runs": [{ "text": "Taghdir (Remix)" }]
                                              }
                                            }
                                          },
                                          {
                                            "musicResponsiveListItemFlexColumnRenderer": {
                                              "text": {
                                                "runs": [
                                                  { "text": "Song" },
                                                  { "text": " • " },
                                                  { "text": "Shadmehr Aghili" },
                                                  { "text": " • " },
                                                  { "text": "Taghdir Album" },
                                                  { "text": " • " },
                                                  { "text": "4:12" }
                                                ]
                                              }
                                            }
                                          }
                                        ]
                                      }
                                    }
                                  ]
                                }
                              }
                            ]
                          }
                        }
                      }
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val tracks = YouTubeMusicRepository.parseSearchResponse(
            rawJson = searchJson,
            favoriteIds = setOf("ytm_abc123XYZ")
        )
        assertEquals(1, tracks.size)
        val track = tracks.first()
        assertEquals("ytm_abc123XYZ", track.id)
        assertEquals("abc123XYZ", track.youtubeVideoId)
        assertEquals("Taghdir (Remix)", track.title)
        assertEquals("Shadmehr Aghili", track.artist)
        assertEquals("Taghdir Album", track.album)
        assertEquals(252_000L, track.durationMs)
        assertTrue(track.isFavorite)
        assertTrue(track.isRemix)
        assertTrue(track.isOnline)
        assertEquals("https://lh3.googleusercontent.com/cover=w544-h544-l90-rj", track.artworkUri)

        val playerJson = """
            {
              "playabilityStatus": { "status": "OK" },
              "streamingData": {
                "adaptiveFormats": [
                  { "mimeType": "video/mp4; codecs=\"avc1\"", "bitrate": 1000000, "url": "https://cdn.example.com/video.mp4" },
                  { "mimeType": "audio/webm; codecs=\"opus\"", "bitrate": 160000, "url": "https://cdn.example.com/audio.webm" },
                  { "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"", "bitrate": 128000, "url": "https://cdn.example.com/audio.m4a" }
                ]
              }
            }
        """.trimIndent()
        assertEquals(
            "https://cdn.example.com/audio.m4a",
            YouTubeMusicRepository.extractDirectAudioUrlFromPlayerResponse(playerJson)
        )

        val progressiveFallbackJson = """
            {
              "playabilityStatus": { "status": "OK" },
              "streamingData": {
                "adaptiveFormats": [
                  { "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"", "bitrate": 128000, "signatureCipher": "s=abc" }
                ],
                "formats": [
                  { "mimeType": "video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"", "bitrate": 360000, "url": "https://cdn.example.com/muxed.mp4" }
                ]
              }
            }
        """.trimIndent()
        assertEquals(
            "https://cdn.example.com/muxed.mp4",
            YouTubeMusicRepository.extractDirectAudioUrlFromPlayerResponse(progressiveFallbackJson)
        )
    }

    @Test
    fun `track download manager resolves audio extensions and builds safe filenames`() {
        assertEquals(
            "m4a",
            com.example.player.TrackDownloadManager.resolveAudioExtension(
                contentType = "audio/mp4",
                mimeType = "audio/mp4",
                url = "https://cdn.example.com/stream"
            )
        )
        assertEquals(
            "webm",
            com.example.player.TrackDownloadManager.resolveAudioExtension(
                contentType = "audio/webm",
                mimeType = "",
                url = "https://cdn.example.com/stream"
            )
        )
        assertEquals(
            "mp3",
            com.example.player.TrackDownloadManager.resolveAudioExtension(
                contentType = "audio/mpeg",
                mimeType = "audio/mpeg",
                url = "https://cdn.example.com/song.mp3"
            )
        )

        val sample = MusicTrack(
            id = "ytm_abc123",
            title = "Test / Song: Remix?",
            artist = "Shadmehr * Aghili",
            album = "",
            durationMs = 0L,
            mediaUri = "https://example.com/test.mp3",
            artworkUri = ""
        )
        val fileName = com.example.player.TrackDownloadManager.buildSafeFileName(sample)
        assertFalse(fileName.contains("/"))
        assertFalse(fileName.contains(":"))
        assertFalse(fileName.contains("?"))
        assertFalse(fileName.contains("*"))
        assertTrue(fileName.contains("Shadmehr"))
    }

    @Test
    fun `unblocked network helper detects iranian sinkhole ips, doh answers, and proxied urls`() {
        assertTrue(com.example.player.UnblockedNetworkHelper.isSinkholeIp("10.10.34.34"))
        assertTrue(com.example.player.UnblockedNetworkHelper.isSinkholeIp("10.10.34.35"))
        assertTrue(com.example.player.UnblockedNetworkHelper.isSinkholeIp("10.10.35.12"))
        assertFalse(com.example.player.UnblockedNetworkHelper.isSinkholeIp("127.0.0.1"))
        assertFalse(com.example.player.UnblockedNetworkHelper.isSinkholeIp("172.67.180.10"))

        assertTrue(
            com.example.player.UnblockedNetworkHelper.isProxiedNonGoogleVideoUrl(
                "https://inv.nadeko.net/latest_version?id=abc&itag=140&local=true"
            )
        )
        assertFalse(
            com.example.player.UnblockedNetworkHelper.isProxiedNonGoogleVideoUrl(
                "https://rr1---sn-4g5e6nsz.googlevideo.com/videoplayback?expire=123"
            )
        )

        val dohJson = """
            {
              "Status": 0,
              "Answer": [
                { "name": "inv.nadeko.net.", "type": 1, "data": "10.10.34.34" },
                { "name": "inv.nadeko.net.", "type": 1, "data": "188.40.123.45" }
              ]
            }
        """.trimIndent()
        val parsedIps = com.example.player.UnblockedNetworkHelper.parseDohJsonAddresses(dohJson)
        assertEquals(listOf("188.40.123.45"), parsedIps)
    }

    @Test
    fun `youtube repository parses invidious and piped search mirrors and prioritizes proxied streams`() {
        val invidiousSearchJson = """
            [
              {
                "type": "video",
                "title": "Shadmehr Aghili - Taghdir",
                "videoId": "vidIran123",
                "author": "Shadmehr Official",
                "lengthSeconds": 245,
                "viewCount": 1500000
              }
            ]
        """.trimIndent()
        val invTracks = YouTubeMusicRepository.parseInvidiousSearchResponse(
            rawJson = invidiousSearchJson,
            mirrorBaseUrl = "https://inv.nadeko.net",
            favoriteIds = setOf("ytm_vidIran123")
        )
        assertEquals(1, invTracks.size)
        assertEquals("vidIran123", invTracks.first().youtubeVideoId)
        assertEquals("https://inv.nadeko.net/vi/vidIran123/mqdefault.jpg", invTracks.first().artworkUri)
        assertEquals(245_000L, invTracks.first().durationMs)
        assertTrue(invTracks.first().isFavorite)

        val pipedSearchJson = """
            {
              "items": [
                {
                  "url": "/watch?v=piped999xyz",
                  "title": "Mohsen Yeganeh - Behet Ghol Midam (Remix)",
                  "uploaderName": "Mohsen Yeganeh",
                  "duration": 210,
                  "views": 950000,
                  "thumbnail": "/vi_proxy/piped999xyz.jpg"
                }
              ]
            }
        """.trimIndent()
        val pipedTracks = YouTubeMusicRepository.parsePipedSearchResponse(
            rawJson = pipedSearchJson,
            mirrorBaseUrl = "https://api.piped.private.coffee"
        )
        assertEquals(1, pipedTracks.size)
        assertEquals("piped999xyz", pipedTracks.first().youtubeVideoId)
        assertEquals("https://api.piped.private.coffee/vi_proxy/piped999xyz.jpg", pipedTracks.first().artworkUri)
        assertTrue(pipedTracks.first().isRemix)

        val invVideoJson = """
            {
              "adaptiveFormats": [
                { "type": "audio/mp4", "bitrate": "128000", "url": "https://rr3---sn.googlevideo.com/videoplayback?id=1" },
                { "type": "audio/mp4", "bitrate": "128000", "url": "/videoplayback?id=1&local=true" }
              ]
            }
        """.trimIndent()
        val invStreams = YouTubeMusicRepository.extractProxiedAudioCandidatesFromInvidiousJson(
            rawJson = invVideoJson,
            mirrorBaseUrl = "https://inv.nadeko.net"
        )
        assertEquals(2, invStreams.size)
        assertEquals("https://inv.nadeko.net/videoplayback?id=1&local=true", invStreams.first().second)

        val pipedStreamsJson = """
            {
              "audioStreams": [
                { "mimeType": "audio/mp4", "bitrate": 128000, "url": "https://rr2.googlevideo.com/audio" },
                { "mimeType": "audio/mp4", "bitrate": 96000, "url": "https://pipedproxy.example.com/audio" }
              ]
            }
        """.trimIndent()
        val pipedStreams = YouTubeMusicRepository.extractAudioCandidatesFromPipedJson(
            rawJson = pipedStreamsJson,
            mirrorBaseUrl = "https://api.piped.private.coffee"
        )
        assertEquals("https://pipedproxy.example.com/audio", pipedStreams.first().second)
    }
}

