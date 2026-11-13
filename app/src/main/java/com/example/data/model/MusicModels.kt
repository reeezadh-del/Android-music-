package com.example.data.model

import androidx.annotation.DrawableRes

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val mediaUri: String,
    val artworkUri: String,
    val isFavorite: Boolean = false,
    val isImported: Boolean = false,
    val genre: String = "الکترونیک",
    val accentColorHex: Long = 0xFFFF6B47L,
    val addedAt: Long = 0L
)

data class AlbumSummary(
    val name: String,
    val artist: String,
    val artworkUri: String,
    val trackCount: Int,
    val totalDurationMs: Long,
    val tracks: List<Track>
)

data class Playlist(
    val id: String,
    val name: String,
    val description: String,
    val artworkUri: String,
    val trackIds: List<String>,
    val isBuiltIn: Boolean = false
)

enum class RepeatModeOption {
    OFF,
    ALL,
    ONE
}

enum class EqualizerPreset(val titleFa: String, val bandOffsetsMb: List<Int>) {
    CUSTOM("سفارشی", listOf(0, 0, 0, 0, 0)),
    FLAT("طبیعی (Flat)", listOf(0, 0, 0, 0, 0)),
    ROAD_TRIP("حالت جاده و خودرو", listOf(450, 200, 50, 300, 400)),
    BASS_BOOST("تقویت بیس", listOf(750, 450, 50, -50, 150)),
    VOCAL_CLARITY("وضوح صدا و پادکست", listOf(-250, 100, 550, 450, 150)),
    ROCK("راک و انرژی", listOf(500, 250, -150, 350, 550)),
    NIGHT_CHILL("آرامش شبانه", listOf(300, 150, -150, 200, 350))
}

data class EqualizerBandUi(
    val bandIndex: Int,
    val frequencyHz: Int,
    val frequencyLabel: String,
    val levelMb: Int,
    val minLevelMb: Int,
    val maxLevelMb: Int
)

data class EqualizerUiState(
    val isSupported: Boolean = true,
    val isEnabled: Boolean = false,
    val selectedPreset: EqualizerPreset = EqualizerPreset.ROAD_TRIP,
    val bands: List<EqualizerBandUi> = emptyList(),
    val preampDb: Float = 0f,
    val bassBoostPercent: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val skipSilenceEnabled: Boolean = false,
    val audioSessionId: Int = 0,
    val sampleRateHz: Int = 44100
)

data class AutoBrowseNodePreview(
    val id: String,
    val title: String,
    val subtitle: String,
    val itemCount: Int,
    @DrawableRes val iconRes: Int
)
