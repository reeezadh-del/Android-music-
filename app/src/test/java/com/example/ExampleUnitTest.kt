package com.example

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import com.example.audio.EqualizerController
import com.example.audio.Media3EqualizerAudioProcessor
import com.example.data.model.EqualizerPreset
import com.example.data.remote.OnlineMusicRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ExampleUnitTest {

    @Test
    fun `equalizer controller switches to CUSTOM when frequency band is modified`() {
        EqualizerController.applyPreset(EqualizerPreset.FLAT)
        assertEquals(EqualizerPreset.FLAT, EqualizerController.state.value.selectedPreset)

        EqualizerController.setBandLevel(0, 600) // +6.0 dB at 60Hz
        val state = EqualizerController.state.value
        assertEquals(EqualizerPreset.CUSTOM, state.selectedPreset)
        assertEquals(5, state.bands.size)
        assertEquals(600, state.bands[0].levelMb)
        assertEquals("60Hz", state.bands[0].frequencyLabel)
    }

    @Test
    fun `media3 equalizer audio processor processes 16-bit PCM frames`() {
        val processor = Media3EqualizerAudioProcessor
        val format = AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        val configured = processor.configure(format)
        assertEquals(44100, configured.sampleRate)
        assertEquals(2, configured.channelCount)

        processor.updateParameters(
            isEnabled = true,
            bandLevelsMb = listOf(800, 0, 0, 0, 400),
            preampDb = 2.0f,
            bassBoostPercent = 50
        )
        processor.flush()

        val input = ByteBuffer.allocateDirect(128).order(ByteOrder.nativeOrder())
        for (i in 0 until 64) {
            input.putShort(((i % 16) * 500).toShort())
        }
        input.flip()

        processor.queueInput(input)
        val output = processor.output
        assertEquals(128, output.remaining())
        assertTrue(output.order() == ByteOrder.nativeOrder())
    }

    @Test
    fun `online music repository parses Audius trending tracks and filters non-streamable entries`() {
        val repository = OnlineMusicRepository()
        val sampleJson = """
            {
              "data": [
                {
                  "id": "track123",
                  "title": "Midnight Pulse",
                  "genre": "Electronic",
                  "duration": 215,
                  "is_streamable": true,
                  "artwork": {
                    "150x150": "https://img.example/150.jpg",
                    "480x480": "https://img.example/480.jpg"
                  },
                  "user": {
                    "name": "Aria Synth",
                    "handle": "ariasynth"
                  },
                  "extra_ignored_field": 42
                },
                {
                  "id": "track999",
                  "title": "Blocked Track",
                  "duration": 100,
                  "is_streamable": false
                }
              ]
            }
        """.trimIndent()

        val tracks = repository.parseAudiusTrendingJson("https://api.audius.co", sampleJson)
        assertEquals(1, tracks.size)
        val track = tracks.first()
        assertEquals("online_track123", track.id)
        assertEquals("Midnight Pulse", track.title)
        assertEquals("Aria Synth", track.artist)
        assertEquals(215_000L, track.durationMs)
        assertEquals("https://api.audius.co/v1/tracks/track123/stream?app_name=AvaPlayer", track.mediaUri)
        assertEquals("https://img.example/480.jpg", track.artworkUri)
    }

    @Test
    fun `online music repository parses Iranian WordPress posts and RSS feeds`() {
        val repository = OnlineMusicRepository()
        val wpJson = """
            [
              {
                "id": 4512,
                "link": "https://music-fa.com/post-4512",
                "title": { "rendered": "دانلود آهنگ جدید محسن چاوشی به نام شب بارانی" },
                "content": {
                  "rendered": "<p><img src=\"https://music-fa.com/wp-content/uploads/cover.jpg\" /><a href=\"https://dls.music-fa.com/song-320.mp3\">320</a><a href=\"https://dls.music-fa.com/song-128.mp3\">128</a></p>"
                }
              },
              {
                "id": 4513,
                "link": "https://music-fa.com/post-no-mp3",
                "title": { "rendered": "پست بدون فایل صوتی" },
                "content": { "rendered": "<p>متن خبر</p>" }
              }
            ]
        """.trimIndent()

        val wpTracks = repository.parseWpPostsJson("موزیک‌فا", wpJson)
        assertEquals(1, wpTracks.size)
        val first = wpTracks.first()
        assertEquals("شب بارانی", first.title)
        assertTrue(first.artist.contains("محسن چاوشی"))
        assertEquals("https://dls.music-fa.com/song-128.mp3", first.mediaUri)
        assertEquals("https://music-fa.com/wp-content/uploads/cover.jpg", first.artworkUri)

        val rssXml = """
            <rss><channel>
              <item>
                <title><![CDATA[دانلود آهنگ رضا بهرام – گل عشق]]></title>
                <description><![CDATA[<img src="https://rozmusic.com/cover.webp"/><a href="https://dl.rozmusic.com/gol-320.mp3">Download</a>]]></description>
              </item>
            </channel></rss>
        """.trimIndent()

        val rssTracks = repository.parseIranianRssFeed("رزموزیک", rssXml)
        assertEquals(1, rssTracks.size)
        assertEquals("گل عشق", rssTracks.first().title)
        assertTrue(rssTracks.first().artist.contains("رضا بهرام"))
        assertEquals("https://dl.rozmusic.com/gol-320.mp3", rssTracks.first().mediaUri)
    }
}
