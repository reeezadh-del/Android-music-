package com.example.player

import kotlinx.serialization.Serializable

enum class AppThemeMode(val labelFa: String) {
    DARK("شب"),
    LIGHT("روز"),
    SYSTEM("سیستم")
}

enum class RepeatModeOption {
    OFF,
    ALL,
    ONE
}

enum class TrackFilter(val labelFa: String) {
    ALL("همه آهنگ‌ها"),
    FAVORITES("علاقه‌مندی‌ها"),
    DOWNLOADED("دانلود شده")
}

enum class AppTab(val labelFa: String) {
    LIBRARY("کتابخانه من"),
    ONLINE("آنلاین"),
    FAVORITES("علاقه‌مندی‌ها")
}

enum class OnlineSubSection(val labelFa: String) {
    TRENDING("ترند اینستا"),
    DISCOVER("تازه‌های آنلاین"),
    YOUTUBE_MUSIC("یوتیوب موزیک")
}

enum class YouTubeMusicCategory(val labelFa: String, val searchQuery: String) {
    IRANIAN_TOP("برترین‌های ایران", "Persian Top Hits New Song"),
    GLOBAL_HITS("هیت‌های جهانی", "Top Global Songs Official Audio"),
    PERSIAN_POP("پاپ فارسی", "پاپ فارسی آهنگ جدید"),
    PERSIAN_RAP("رپ و هیپ‌هاپ", "Persian Rap Hip Hop Official Audio"),
    CHILL_LOFI("آرام و شبانه", "Chill Acoustic LoFi Music"),
    WORKOUT_CLUB("ریمیکس و کلاب", "Club Dance Remix Bass Boosted")
}

enum class OnlineDiscoverStyle(val labelFa: String, val searchKeywords: List<String>) {
    NEW_HITS("جدیدترین‌ها", listOf("آهنگ جدید", "دانلود آهنگ جدید")),
    POP("پاپ ایرانی", listOf("آهنگ پاپ جدید", "پاپ ایرانی")),
    HAPPY_DANCE("شاد و ریتمیک", listOf("آهنگ شاد جدید", "آهنگ رقصی")),
    CHILL_EMOTIONAL("احساسی و آرام", listOf("آهنگ غمگین جدید", "آهنگ احساسی")),
    PERSIAN_RAP("رپ و هیپ‌هاپ", listOf("آهنگ رپ فارسی", "هیپ هاپ"))
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

enum class TrackReactionType(val emoji: String, val labelFa: String) {
    FIRE("🔥", "آتیش"),
    LOVE("❤️", "عشق"),
    VIBE("😍", "خفن"),
    CLAP("👏", "عالی"),
    SAD("😢", "احساسی");

    companion object {
        fun fromRawOrNull(raw: String?): TrackReactionType? {
            if (raw.isNullOrBlank()) return null
            return entries.firstOrNull { it.name == raw.trim().uppercase() }
        }
    }
}

data class TrackReactionSummary(
    val trackKey: String = "",
    val counts: Map<TrackReactionType, Int> = emptyMap(),
    val userReaction: TrackReactionType? = null,
    val commentCount: Int = 0
) {
    val totalReactions: Int
        get() = counts.values.sum()
}

data class TrackCommentUi(
    val commentId: String,
    val trackKey: String,
    val userId: String,
    val authorUsername: String,
    val text: String,
    val createdAtMs: Long,
    val isOwnComment: Boolean
)

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
    val trendScore: Long = 0L,
    val lyrics: String = "",
    val pageUrl: String = "",
    val youtubeVideoId: String = "",
    val isDownloaded: Boolean = false
)
