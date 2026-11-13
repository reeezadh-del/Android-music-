package com.example

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import com.example.audio.EqualizerController
import com.example.audio.Media3EqualizerAudioProcessor
import com.example.data.model.EqualizerPreset
import com.example.data.remote.OnlineMusicRepository
import com.example.data.remote.OnlineRadioCategory
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
    fun `online music repository parses live radio stations and deduplicates stream URLs`() {
        val repository = OnlineMusicRepository()
        val sampleJson = """
            [
              {
                "stationuuid": "st-1",
                "name": "Tehran Chill FM",
                "url_resolved": "https://stream.example/chill.mp3",
                "url": "http://fallback.example/chill.mp3",
                "favicon": "https://img.example/radio.png",
                "tags": "chillout,persian",
                "country": "Iran",
                "bitrate": 128
              },
              {
                "stationuuid": "st-dup",
                "name": "Duplicate Chill FM",
                "url_resolved": "https://stream.example/chill.mp3",
                "bitrate": 128
              }
            ]
        """.trimIndent()

        val stations = repository.parseRadioStationsJson(OnlineRadioCategory.PERSIAN, sampleJson)
        assertEquals(1, stations.size)
        val station = stations.first()
        assertEquals("radio_st-1", station.id)
        assertEquals("Tehran Chill FM", station.title)
        assertEquals(0L, station.durationMs)
        assertEquals("https://stream.example/chill.mp3", station.mediaUri)
        assertTrue(station.artist.contains("128kbps"))
    }
}
