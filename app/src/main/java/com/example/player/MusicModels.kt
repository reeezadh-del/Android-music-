package com.example.player

import kotlinx.serialization.Serializable

enum class RepeatModeOption {
    OFF,
    ALL,
    ONE
}

enum class TrackFilter(val labelFa: String) {
    ALL("همه آهنگ‌ها"),
    FAVORITES("علاقه‌مندی‌ها")
}

enum class AppTab(val labelFa: String) {
    LIBRARY("کتابخانه من"),
    TRENDING("ترند اینستا")
}

enum class TrendingCategory(val labelFa: String) {
    ALL("همه ترندهای اینستا"),
    SONGS("آهنگ‌های ترند"),
    REMIXES("ریمیکس‌های ترند")
}

enum class TrendingPreset(val labelFa: String) {
    HOT("ترند روز اینستا"),
    REELS_CHALLENGE("ریلز و چالش"),
    CLUB_REMIX("ریمیکس کلاب و ماشین"),
    SAD_SLOW("اسلو و دپ اینستا"),
    RAP_GANG("رپ و گنگ اینستا")
}

@Serializable
data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val mediaUri: String,
    val artworkUri: String,
    val isFavorite: Boolean = false,
    val isImported: Boolean = false,
    val isOnline: Boolean = false,
    val isRemix: Boolean = false,
    val isInstaTrend: Boolean = false,
    val isFullTrack: Boolean = true,
    val sourceLabel: String = "",
    val playCount: Long = 0L,
    val trendScore: Long = 0L
)
