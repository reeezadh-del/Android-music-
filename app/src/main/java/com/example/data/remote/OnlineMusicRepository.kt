package com.example.data.remote

import com.example.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

enum class OnlineSourceRegion(val labelFa: String) {
    IRANIAN_SITES("سایت‌های ایرانی"),
    GLOBAL_WEB("موزیک جهانی")
}

enum class IranianMusicSiteFilter(
    val labelFa: String,
    val siteTitleFa: String,
    val baseUrls: List<String>
) {
    ALL(
        labelFa = "همه سایت‌های ایرانی",
        siteTitleFa = "سایت‌های ایرانی",
        baseUrls = emptyList()
    ),
    MUSIC_FA(
        labelFa = "موزیک‌فا",
        siteTitleFa = "موزیک‌فا (Music-Fa)",
        baseUrls = listOf("https://music-fa.com", "https://musics-fa.com")
    ),
    ROZ_MUSIC(
        labelFa = "رزموزیک",
        siteTitleFa = "رزموزیک (RozMusic)",
        baseUrls = listOf("https://rozmusic.com")
    ),
    UP_MUSICS(
        labelFa = "آپ موزیک",
        siteTitleFa = "آپ موزیک (UpMusics)",
        baseUrls = listOf("https://upmusics.com")
    ),
    NEX1_MUSIC(
        labelFa = "نکس‌وان",
        siteTitleFa = "نکس‌وان موزیک (Nex1Music)",
        baseUrls = listOf("https://nex1music.com", "https://nex1music.ir")
    )
}

enum class OnlineTrackGenre(val labelFa: String, val apiGenre: String?) {
    TRENDING("داغ‌ترین‌های هفته", null),
    ELECTRONIC("الکترونیک", "Electronic"),
    POP("پاپ", "Pop"),
    HIP_HOP("هیپ‌هاپ", "Hip-Hop/Rap"),
    LOFI("لو-فای", "Lo-Fi"),
    AMBIENT("امبینت", "Ambient"),
    ROCK("راک", "Rock")
}

@Serializable
data class AudiusTrendingResponse(
    val data: List<AudiusTrackDto> = emptyList()
)

@Serializable
data class AudiusTrackDto(
    val id: String = "",
    val title: String = "",
    val genre: String? = null,
    val duration: Long = 0L,
    val artwork: AudiusArtworkDto? = null,
    val user: AudiusUserDto? = null,
    @SerialName("is_streamable") val isStreamable: Boolean = true
)

@Serializable
data class AudiusArtworkDto(
    @SerialName("150x150") val small: String? = null,
    @SerialName("480x480") val medium: String? = null,
    @SerialName("1000x1000") val large: String? = null
)

@Serializable
data class AudiusUserDto(
    val name: String = "",
    val handle: String = ""
)

@Serializable
data class WpPostDto(
    val id: Long = 0L,
    val link: String = "",
    val title: WpRenderedDto = WpRenderedDto(),
    val content: WpRenderedDto = WpRenderedDto(),
    @SerialName("yoast_head_json") val yoastHead: WpYoastHeadDto? = null,
    @SerialName("_embedded") val embedded: WpEmbeddedDto? = null
)

@Serializable
data class WpRenderedDto(
    val rendered: String = ""
)

@Serializable
data class WpYoastHeadDto(
    @SerialName("og_image") val ogImage: List<WpOgImageDto> = emptyList()
)

@Serializable
data class WpOgImageDto(
    val url: String = ""
)

@Serializable
data class WpEmbeddedDto(
    @SerialName("wp:featuredmedia") val featuredMedia: List<WpFeaturedMediaDto> = emptyList()
)

@Serializable
data class WpFeaturedMediaDto(
    @SerialName("source_url") val sourceUrl: String = ""
)

class OnlineMusicRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val audiusHosts = listOf(
        "https://api.audius.co",
        "https://discoveryprovider.audius.co",
        "https://discoveryprovider2.audius.co"
    )

    suspend fun fetchIranianSiteTracks(
        siteFilter: IranianMusicSiteFilter,
        query: String = ""
    ): Result<List<Track>> = withContext(Dispatchers.IO) {
        runCatching {
            val targets = if (siteFilter == IranianMusicSiteFilter.ALL) {
                IranianMusicSiteFilter.entries.filter { it != IranianMusicSiteFilter.ALL }
            } else {
                listOf(siteFilter)
            }

            val results = coroutineScope {
                targets.map { site ->
                    async {
                        runCatching { fetchSingleIranianSite(site, query) }.getOrNull()
                    }
                }.awaitAll()
            }

            val tracks = results
                .filterNotNull()
                .flatten()
                .distinctBy { it.mediaUri }

            if (tracks.isEmpty() && results.all { it == null }) {
                throw IllegalStateException("دسترسی به سایت‌های موسیقی ایرانی برقرار نشد؛ اتصال اینترنت را بررسی کنید.")
            }
            tracks
        }
    }

    private fun fetchSingleIranianSite(
        site: IranianMusicSiteFilter,
        query: String
    ): List<Track> {
        var lastError: Throwable? = null
        val encodedQuery = if (query.isNotBlank()) {
            "&search=${URLEncoder.encode(query.trim(), "UTF-8")}"
        } else ""

        for (baseUrl in site.baseUrls) {
            // 1. Try WordPress REST API (JSON)
            try {
                val wpUrl = "$baseUrl/wp-json/wp/v2/posts?per_page=18&_embed=1$encodedQuery"
                val wpBody = executeGet(wpUrl, accept = "application/json")
                if (wpBody.trimStart().startsWith("[")) {
                    val parsed = parseWpPostsJson(site.labelFa, wpBody)
                    if (parsed.isNotEmpty() || query.isNotBlank()) {
                        return parsed
                    }
                }
            } catch (t: Throwable) {
                lastError = t
            }

            // 2. Fallback to WordPress RSS Feed (XML)
            try {
                val rssPath = if (query.isNotBlank()) {
                    "/feed/?s=${URLEncoder.encode(query.trim(), "UTF-8")}"
                } else {
                    "/feed/"
                }
                val rssBody = executeGet("$baseUrl$rssPath", accept = "application/rss+xml, application/xml, text/xml")
                val parsedRss = parseIranianRssFeed(site.labelFa, rssBody)
                if (parsedRss.isNotEmpty() || query.isNotBlank()) {
                    return parsedRss
                }
            } catch (t: Throwable) {
                lastError = t
            }
        }

        throw lastError ?: IllegalStateException("خطا در دریافت لیست از ${site.labelFa}")
    }

    suspend fun fetchTrendingTracks(genre: OnlineTrackGenre): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val genreParam = genre.apiGenre?.let {
                    "&genre=${URLEncoder.encode(it, "UTF-8")}"
                } ?: ""
                val path = "/v1/tracks/trending?app_name=AvaPlayer&time=week&limit=25$genreParam"
                val (host, body) = fetchFirstAvailable(audiusHosts, path)
                parseAudiusTrendingJson(host, body)
            }
        }

    suspend fun searchWebTracks(query: String): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = URLEncoder.encode(query.trim(), "UTF-8")
                val path = "/v1/tracks/search?query=$encoded&app_name=AvaPlayer&limit=25"
                val (host, body) = fetchFirstAvailable(audiusHosts, path)
                parseAudiusTrendingJson(host, body)
            }
        }

    fun parseWpPostsJson(siteLabelFa: String, rawJson: String): List<Track> {
        val posts = json.decodeFromString<List<WpPostDto>>(rawJson)
        val accentPalette = listOf(0xFF3CE0D0L, 0xFFFF6B47L, 0xFFFFB84DL, 0xFFE8609AL)
        return posts.mapIndexedNotNull { idx, post ->
            val mp3Url = extractBestMp3Url(post.content.rendered) ?: return@mapIndexedNotNull null
            val rawTitle = decodeHtmlEntities(post.title.rendered)
            val (artist, songTitle) = parseIranianTitleAndArtist(rawTitle, siteLabelFa)
            val artwork = post.embedded?.featuredMedia?.firstOrNull()?.sourceUrl?.takeIf { it.isNotBlank() }
                ?: post.yoastHead?.ogImage?.firstOrNull()?.url?.takeIf { it.isNotBlank() }
                ?: extractFirstImageUrl(post.content.rendered)
                ?: ""

            Track(
                id = "ir_${siteLabelFa.hashCode().toUInt()}_${post.id}_${mp3Url.hashCode().toUInt()}",
                title = songTitle,
                artist = "$artist • $siteLabelFa",
                album = siteLabelFa,
                durationMs = 0L,
                mediaUri = mp3Url,
                artworkUri = artwork,
                isFavorite = false,
                isImported = false,
                genre = "ایرانی",
                accentColorHex = accentPalette[idx % accentPalette.size],
                addedAt = System.currentTimeMillis() + idx
            )
        }
    }

    fun parseIranianRssFeed(siteLabelFa: String, rawXml: String): List<Track> {
        val itemRegex = Regex("""<item>(.*?)</item>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val titleRegex = Regex("""<title>(?:<!\[CDATA\[)?(.*?)(?:\]\]>)?</title>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val enclosureRegex = Regex("""<enclosure[^>]+url=["']([^"']+\.mp3[^"']*)["']""", RegexOption.IGNORE_CASE)
        val accentPalette = listOf(0xFF3CE0D0L, 0xFFFF6B47L, 0xFFFFB84DL, 0xFFE8609AL)

        return itemRegex.findAll(rawXml).mapIndexedNotNull { idx, match ->
            val itemXml = match.groupValues[1]
            val mp3Url = enclosureRegex.find(itemXml)?.groupValues?.getOrNull(1)
                ?.let { decodeHtmlEntities(it) }
                ?: extractBestMp3Url(itemXml)
                ?: return@mapIndexedNotNull null

            val rawTitle = titleRegex.find(itemXml)?.groupValues?.getOrNull(1)
                ?.let { decodeHtmlEntities(it) }
                ?.takeIf { it.isNotBlank() }
                ?: "آهنگ جدید"

            val (artist, songTitle) = parseIranianTitleAndArtist(rawTitle, siteLabelFa)
            val artwork = extractFirstImageUrl(itemXml) ?: ""

            Track(
                id = "ir_rss_${mp3Url.hashCode().toUInt()}",
                title = songTitle,
                artist = "$artist • $siteLabelFa",
                album = siteLabelFa,
                durationMs = 0L,
                mediaUri = mp3Url,
                artworkUri = artwork,
                isFavorite = false,
                isImported = false,
                genre = "ایرانی",
                accentColorHex = accentPalette[idx % accentPalette.size],
                addedAt = System.currentTimeMillis() + idx
            )
        }.toList()
    }

    fun parseAudiusTrendingJson(host: String, rawJson: String): List<Track> {
        val parsed = json.decodeFromString<AudiusTrendingResponse>(rawJson)
        return parsed.data
            .filter { it.id.isNotBlank() && it.title.isNotBlank() && it.isStreamable }
            .mapIndexed { idx, dto -> dto.toTrack(host, idx) }
    }

    internal fun extractBestMp3Url(html: String): String? {
        val mp3Regex = Regex("""https?://[^\s"'<>\\]+?\.mp3(?:\?[^\s"'<>\\]*)?""", RegexOption.IGNORE_CASE)
        val matches = mp3Regex.findAll(html)
            .map { decodeHtmlEntities(it.value).trim() }
            .filter { it.startsWith("http", ignoreCase = true) }
            .distinct()
            .toList()
        if (matches.isEmpty()) return null
        return matches.firstOrNull { it.contains("128") }
            ?: matches.firstOrNull { it.contains("320") }
            ?: matches.first()
    }

    internal fun extractFirstImageUrl(html: String): String? {
        val imgRegex = Regex(
            """https?://[^\s"'<>\\]+?\.(?:jpg|jpeg|png|webp)(?:\?[^\s"'<>\\]*)?""",
            RegexOption.IGNORE_CASE
        )
        return imgRegex.find(html)?.value?.let { decodeHtmlEntities(it) }
    }

    internal fun parseIranianTitleAndArtist(rawTitle: String, fallbackSite: String): Pair<String, String> {
        var cleaned = rawTitle
            .replace(Regex("""<[^>]+>"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val prefixes = listOf(
            "دانلود آهنگ جدید ",
            "دانلود آهنگ ",
            "آهنگ جدید ",
            "آهنگ "
        )
        for (prefix in prefixes) {
            if (cleaned.startsWith(prefix)) {
                cleaned = cleaned.removePrefix(prefix).trim()
                break
            }
        }

        val separators = listOf(" به نام ", " – ", " - ", " | ")
        for (sep in separators) {
            if (cleaned.contains(sep)) {
                val parts = cleaned.split(sep, limit = 2)
                val artist = parts[0].trim().ifBlank { fallbackSite }
                val title = parts[1].trim().ifBlank { cleaned }
                return artist to title
            }
        }
        return fallbackSite to cleaned.ifBlank { "آهنگ جدید ایرانی" }
    }

    internal fun decodeHtmlEntities(input: String): String {
        var result = input
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&ndash;", "–")
            .replace("&mdash;", "—")
            .replace("&laquo;", "«")
            .replace("&raquo;", "»")

        result = Regex("""&#(\d+);""").replace(result) { match ->
            val code = match.groupValues[1].toIntOrNull()
            if (code != null) code.toChar().toString() else match.value
        }
        result = Regex("""&#x([0-9a-fA-F]+);""").replace(result) { match ->
            val code = match.groupValues[1].toIntOrNull(16)
            if (code != null) code.toChar().toString() else match.value
        }
        return result.replace(Regex("""<[^>]+>"""), " ").replace(Regex("""\s+"""), " ").trim()
    }

    private fun executeGet(url: String, accept: String): String {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            )
            .header("Accept", accept)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            val body = response.body?.string()
            if (body.isNullOrBlank()) {
                throw IllegalStateException("Empty response")
            }
            return body
        }
    }

    private fun fetchFirstAvailable(hosts: List<String>, path: String): Pair<String, String> {
        var lastError: Throwable? = null
        for (host in hosts) {
            try {
                val body = executeGet("$host$path", "application/json")
                return host to body
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw lastError ?: IllegalStateException("Unable to reach online music catalog")
    }

    private fun AudiusTrackDto.toTrack(host: String, index: Int): Track {
        val accentPalette = listOf(0xFFFF6B47L, 0xFF3CE0D0L, 0xFFFFB84DL, 0xFFE8609AL)
        val artworkUrl = artwork?.medium
            ?: artwork?.large
            ?: artwork?.small
            ?: ""
        val artistName = user?.name?.takeIf { it.isNotBlank() }
            ?: user?.handle?.takeIf { it.isNotBlank() }
            ?: "هنرمند آنلاین"

        return Track(
            id = "online_$id",
            title = title.trim(),
            artist = artistName,
            album = genre?.takeIf { it.isNotBlank() } ?: "موزیک آنلاین وب",
            durationMs = (duration * 1000L).coerceAtLeast(0L),
            mediaUri = "$host/v1/tracks/$id/stream?app_name=AvaPlayer",
            artworkUri = artworkUrl,
            isFavorite = false,
            isImported = false,
            genre = genre?.takeIf { it.isNotBlank() } ?: "آنلاین",
            accentColorHex = accentPalette[index % accentPalette.size],
            addedAt = System.currentTimeMillis() + index
        )
    }
}
