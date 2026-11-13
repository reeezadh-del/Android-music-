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
    FLAT("طبیعی (Flat)", listOf(0, 0, 0, 0, 0)),
    BASS_BOOST("تقویت بیس", listOf(500, 350, 50, -50, 100)),
    ROAD_TRIP("حالت جاده و خودرو", listOf(400, 150, 100, 250, 350)),
    VOCAL_CLARITY("وضوح صدا و پادکست", listOf(-200, 100, 450, 350, 100)),
    NIGHT_CHILL("آرامش شبانه", listOf(250, 100, -100, 150, 300))
}

data class EqualizerBandUi(
    val bandIndex: Int,
    val frequencyLabel: String,
    val levelMb: Int,
    val minLevelMb: Int,
    val maxLevelMb: Int
)

data class EqualizerUiState(
    val isSupported: Boolean = true,
    val isEnabled: Boolean = false,
    val selectedPreset: EqualizerPreset = EqualizerPreset.ROAD_TRIP,
    val bands: List<EqualizerBandUi> = emptyList()
)

data class AutoBrowseNodePreview(
    val id: String,
    val title: String,
    val subtitle: String,
    val itemCount: Int,
    @DrawableRes val iconRes: Int
)
