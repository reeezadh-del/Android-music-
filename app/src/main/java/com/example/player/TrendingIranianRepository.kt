package com.example.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class IranianMusicSite(
    val id: String,
    val nameFa: String,
    val baseUrl: String,
    val instaTagPaths: List<String> = emptyList()
)

class TrendingIranianRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(9, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {

    suspend fun fetchTrendingTracks(
        preset: TrendingPreset,
        category: TrendingCategory,
        customQuery: String = "",
        favoriteIds: Set<String> = emptySet()
    ): Result<List<MusicTrack>> = withContext(Dispatchers.IO) {
        runCatching {
            val queries = buildInstagramSearchQueries(preset, category, customQuery.trim())

            val collected = coroutineScope {
                IRANIAN_SITES.map { site ->
                    async {
                        fetchFromIranianSite(
                            site = site,
                            queries = queries,
                            preset = preset,
                            category = category,
                            favoriteIds = favoriteIds
                        )
                    }
                }.awaitAll().flatten()
            }

            if (collected.isEmpty()) {
                throw IllegalStateException("خطا در دریافت لیست از سایت‌های ایرانی")
            }

            deduplicateAndRank(collected, category)
        }
    }

    private fun buildInstagramSearchQueries(
        preset: TrendingPreset,
        category: TrendingCategory,
        customQuery: String
    ): List<String> {
        if (customQuery.isNotBlank()) {
            val base = customQuery
            val queries = mutableListOf(base)
            if (category == TrendingCategory.REMIXES &&
                !base.contains("ریمیکس") &&
                !base.contains("remix", ignoreCase = true)
            ) {
                queries.add(0, "$base ریمیکس")
            }
            if (!base.contains("اینستا")) {
                queries.add("$base اینستاگرام")
            }
            return queries.distinct()
        }

        return when (preset) {
            TrendingPreset.HOT -> when (category) {
                TrendingCategory.ALL -> listOf("ترند اینستاگرام", "ریمیکس اینستاگرام", "آهنگ اینستاگرام")
                TrendingCategory.SONGS -> listOf("آهنگ ترند اینستاگرام", "آهنگ اینستاگرام")
                TrendingCategory.REMIXES -> listOf("ریمیکس ترند اینستاگرام", "ریمیکس اینستاگرام")
            }
            TrendingPreset.REELS_CHALLENGE -> listOf("چالش اینستاگرام", "ریلز اینستاگرام", "ترند اینستاگرام")
            TrendingPreset.CLUB_REMIX -> listOf("ریمیکس اینستاگرام", "ریمیکس بیس دار", "ریمیکس شاد")
            TrendingPreset.SAD_SLOW -> listOf("اسلو ریمیکس", "ریمیکس غمگین اینستاگرام", "آهنگ غمگین اینستاگرام")
            TrendingPreset.RAP_GANG -> listOf("رپ اینستاگرام", "ریمیکس رپی", "آهنگ گنگ اینستاگرام")
        }
    }

    private suspend fun fetchFromIranianSite(
        site: IranianMusicSite,
        queries: List<String>,
        preset: TrendingPreset,
        category: TrendingCategory,
        favoriteIds: Set<String>
    ): List<MusicTrack> {
        val wpResults = mutableListOf<MusicTrack>()

        // 1. Try WordPress REST API on Iranian site for clean JSON + embedded MP3s
        for (query in queries.take(2)) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val apiUrl = "${site.baseUrl}/wp-json/wp/v2/posts?search=$encoded&per_page=10&_embed=1"
            val body = executeGet(apiUrl)
            if (!body.isNullOrBlank() && body.trimStart().startsWith("[")) {
                val parsed = parseWordPressPostsJson(
                    rawJson = body,
                    site = site,
                    preset = preset,
                    favoriteIds = favoriteIds
                )
                wpResults.addAll(parsed)
                if (wpResults.size >= 6) break
            }
        }

        if (wpResults.isNotEmpty()) {
            return wpResults
        }

        // 2. Fallback: scrape HTML search / Instagram trend pages directly from the Iranian site
        val htmlCandidatePages = buildList {
            val primaryQuery = queries.firstOrNull() ?: "اینستاگرام"
            val encoded = URLEncoder.encode(primaryQuery, "UTF-8")
            add("${site.baseUrl}/?s=$encoded")
            if (preset == TrendingPreset.HOT) {
                site.instaTagPaths.forEach { path -> add("${site.baseUrl}$path") }
            }
        }

        val scrapedTracks = mutableListOf<MusicTrack>()
        for (pageUrl in htmlCandidatePages) {
            val html = executeGet(pageUrl) ?: continue
            val directFromArchive = extractTracksDirectlyFromHtml(html, site, preset, favoriteIds)
            if (directFromArchive.isNotEmpty()) {
                scrapedTracks.addAll(directFromArchive)
            } else {
                val postUrls = extractPostUrlsFromArchiveHtml(html, site.baseUrl).take(5)
                val postTracks = coroutineScope {
                    postUrls.map { postUrl ->
                        async {
                            val postHtml = executeGet(postUrl) ?: return@async null
                            parseSinglePostHtml(postHtml, postUrl, site, preset, favoriteIds)
                        }
                    }.awaitAll().filterNotNull()
                }
                scrapedTracks.addAll(postTracks)
            }
            if (scrapedTracks.size >= 5) break
        }

        return scrapedTracks
    }

    private fun executeGet(url: String): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json, text/html;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "fa-IR,fa;q=0.9,en;q=0.7")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
            )
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string()
        }
    }.getOrNull()

    companion object {

        val IRANIAN_SITES: List<IranianMusicSite> = listOf(
            IranianMusicSite(
                id = "musicsweb",
                nameFa = "موزیک وب",
                baseUrl = "https://musicsweb.ir",
                instaTagPaths = listOf("/tag/آهنگ-ترند-اینستاگرام/", "/category/remix/")
            ),
            IranianMusicSite(
                id = "remixgram",
                nameFa = "ریمیکس‌گرام",
                baseUrl = "https://remixgram.com",
                instaTagPaths = listOf("/category/instagram-remix/", "/category/trend/")
            ),
            IranianMusicSite(
                id = "upmusics",
                nameFa = "آپ موزیک",
                baseUrl = "https://upmusics.com",
                instaTagPaths = listOf("/category/remix/")
            ),
            IranianMusicSite(
                id = "rozmusic",
                nameFa = "رز موزیک",
                baseUrl = "https://rozmusic.com",
                instaTagPaths = listOf("/category/remix/")
            ),
            IranianMusicSite(
                id = "musicsfa",
                nameFa = "موزیکفا",
                baseUrl = "https://musics-fa.com",
                instaTagPaths = listOf("/category/remix/")
            ),
            IranianMusicSite(
                id = "musicdel",
                nameFa = "موزیکدل",
                baseUrl = "https://musicdel.ir",
                instaTagPaths = listOf("/category/remix/")
            ),
            IranianMusicSite(
                id = "nicmusic",
                nameFa = "نیک موزیک",
                baseUrl = "https://nicmusic.net",
                instaTagPaths = listOf("/category/remix/")
            )
        )

        private val jsonParser = Json { ignoreUnknownKeys = true }

        private val MP3_URL_REGEX = Regex(
            """https?://[^\s"'<>\\]+\.(?:mp3|m4a)(?:\?[^\s"'<>\\]*)?""",
            RegexOption.IGNORE_CASE
        )

        private val IMAGE_URL_REGEX = Regex(
            """https?://[^\s"'<>\\]+\.(?:jpg|jpeg|png|webp)(?:\?[^\s"'<>\\]*)?""",
            RegexOption.IGNORE_CASE
        )

        private val OG_IMAGE_REGEX = Regex(
            """<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )

        private val OG_TITLE_REGEX = Regex(
            """<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )

        private val H1_REGEX = Regex(
            """<h1[^>]*>(.*?)</h1>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        private val PUBLISHED_TIME_REGEX = Regex(
            """<meta[^>]+property=["']article:published_time["'][^>]+content=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )

        private val ARTICLE_BLOCK_REGEX = Regex(
            """<article\b[^>]*>(.*?)</article>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        private val ANCHOR_HREF_REGEX = Regex(
            """<a[^>]+href=["'](https?://[^"'#]+)["'][^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        private val HEADING_LINK_REGEX = Regex(
            """<(?:h1|h2|h3)[^>]*>.*?<a[^>]+href=["'](https?://[^"'#]+)["'][^>]*>(.*?)</a>.*?</(?:h1|h2|h3)>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        fun decodeHtmlEntities(input: String): String {
            return input
                .replace(Regex("<[^>]+>"), " ")
                .replace("&amp;", "&")
                .replace("&#038;", "&")
                .replace("&#8211;", "–")
                .replace("&#8212;", "—")
                .replace("&ndash;", "–")
                .replace("&mdash;", "—")
                .replace("&quot;", "\"")
                .replace("&#8220;", "\"")
                .replace("&#8221;", "\"")
                .replace("&#039;", "'")
                .replace("&nbsp;", " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        fun detectIsRemix(rawText: String): Boolean {
            val lower = rawText.lowercase()
            return lower.contains("ریمیکس") ||
                lower.contains("میکس") ||
                lower.contains("اسلو") ||
                lower.contains("بیس") ||
                lower.contains("remix") ||
                lower.contains("rmx") ||
                lower.contains("mashup") ||
                lower.contains("slowed") ||
                lower.contains("dj ")
        }

        fun detectIsInstaTrend(rawText: String): Boolean {
            val lower = rawText.lowercase()
            return lower.contains("اینستا") ||
                lower.contains("ریلز") ||
                lower.contains("ترند") ||
                lower.contains("چالش") ||
                lower.contains("استوری") ||
                lower.contains("وایرال") ||
                lower.contains("instagram") ||
                lower.contains("reels") ||
                lower.contains("viral") ||
                lower.contains("trend")
        }

        data class ParsedTitleArtist(
            val title: String,
            val artist: String
        )

        fun parsePersianPostTitle(rawTitle: String, fallbackSiteName: String): ParsedTitleArtist {
            var cleaned = decodeHtmlEntities(rawTitle)
            val prefixes = listOf(
                "دانلود آهنگ جدید",
                "دانلود ریمیکس جدید",
                "دانلود ریمیکس ترند اینستاگرام",
                "دانلود آهنگ ترند اینستاگرام",
                "دانلود ریمیکس اینستاگرام",
                "دانلود آهنگ اینستاگرام",
                "دانلود ریمیکس",
                "دانلود آهنگ",
                "آهنگ جدید",
                "ریمیکس جدید",
                "پخش آنلاین"
            )
            for (prefix in prefixes) {
                if (cleaned.startsWith(prefix)) {
                    cleaned = cleaned.removePrefix(prefix).trim()
                }
            }
            cleaned = cleaned
                .replace("ترند اینستاگرام", "")
                .replace("ترند اینستا", "")
                .replace("اینستاگرام", "")
                .replace("با کیفیت 320 و 128", "")
                .replace("با کیفیت ۳۲۰", "")
                .replace("+ متن", "")
                .replace("+ پخش آنلاین", "")
                .replace(Regex("\\s+"), " ")
                .trim(' ', '-', '–', '|', '•', ':')

            // Split on " بنام " or " به نام "
            val benamRegex = Regex("""^(.+?)\s+(?:به\s+نام|بنام)\s+(.+)$""")
            benamRegex.find(cleaned)?.let { m ->
                val artist = m.groupValues[1].trim(' ', '-', '–')
                val title = m.groupValues[2].trim(' ', '-', '–')
                if (artist.isNotBlank() && title.isNotBlank()) {
                    return ParsedTitleArtist(title = title, artist = artist)
                }
            }

            // Split on dash " – " or " - "
            val dashParts = cleaned.split(Regex("""\s+[–—-]\s+"""), limit = 2)
            if (dashParts.size == 2 && dashParts[0].isNotBlank() && dashParts[1].isNotBlank()) {
                return ParsedTitleArtist(
                    title = dashParts[1].trim(),
                    artist = dashParts[0].trim()
                )
            }

            return ParsedTitleArtist(
                title = cleaned.ifBlank { "آهنگ ترند اینستا" },
                artist = fallbackSiteName
            )
        }

        fun pickBestMp3Url(html: String): String? {
            val candidates = MP3_URL_REGEX.findAll(html)
                .map { it.value.trimEnd('.', ',', ';', ')') }
                .filter { url ->
                    val lower = url.lowercase()
                    !lower.contains("teaser") && !lower.contains("demo")
                }
                .distinct()
                .toList()
            if (candidates.isEmpty()) return null
            return candidates.firstOrNull { it.contains("320") }
                ?: candidates.firstOrNull { !it.contains("128") }
                ?: candidates.first()
        }

        fun pickBestArtworkUrl(html: String): String {
            OG_IMAGE_REGEX.find(html)?.groupValues?.getOrNull(1)?.let { og ->
                if (isLikelyAlbumArt(og)) return og
            }
            return IMAGE_URL_REGEX.findAll(html)
                .map { it.value }
                .firstOrNull { isLikelyAlbumArt(it) }
                .orEmpty()
        }

        private fun isLikelyAlbumArt(url: String): Boolean {
            val lower = url.lowercase()
            return !lower.contains("logo") &&
                !lower.contains("favicon") &&
                !lower.contains("emoji") &&
                !lower.contains("avatar") &&
                !lower.contains("icon") &&
                !lower.endsWith(".svg")
        }

        fun computeInstagramTrendScore(
            rawText: String,
            isRemix: Boolean,
            preset: TrendingPreset,
            dateIso: String? = null
        ): Long {
            val lower = rawText.lowercase()
            var score = 10_000L

            if (lower.contains("ترند اینستا") || lower.contains("ترند اینستاگرام")) score += 26_000L
            if (lower.contains("اینستاگرام") || lower.contains("اینستا") || lower.contains("instagram")) score += 16_000L
            if (lower.contains("ریلز") || lower.contains("reels") || lower.contains("چالش") || lower.contains("وایرال")) score += 12_000L
            if (lower.contains("ترند") || lower.contains("استوری") || lower.contains("جدید")) score += 6_000L

            if (isRemix) {
                score += if (preset == TrendingPreset.CLUB_REMIX || preset == TrendingPreset.SAD_SLOW) {
                    14_000L
                } else {
                    8_000L
                }
            }

            if (!dateIso.isNullOrBlank()) {
                val recencyBonus = computeRecencyBonus(dateIso)
                score += recencyBonus
            }

            return score
        }

        private fun computeRecencyBonus(dateIso: String): Long {
            val patterns = listOf(
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd"
            )
            for (pattern in patterns) {
                val parsedMs = runCatching {
                    SimpleDateFormat(pattern, Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.parse(dateIso.take(19))?.time
                }.getOrNull()
                if (parsedMs != null) {
                    val ageDays = ((System.currentTimeMillis() - parsedMs) / 86_400_000L).coerceAtLeast(0L)
                    return when {
                        ageDays <= 7L -> 15_000L
                        ageDays <= 30L -> 11_000L
                        ageDays <= 90L -> 7_000L
                        ageDays <= 365L -> 3_000L
                        else -> 500L
                    }
                }
            }
            return 0L
        }

        fun parseWordPressPostsJson(
            rawJson: String,
            site: IranianMusicSite,
            preset: TrendingPreset,
            favoriteIds: Set<String> = emptySet()
        ): List<MusicTrack> = runCatching {
            val array = jsonParser.parseToJsonElement(rawJson).jsonArray
            array.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val postId = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val dateIso = obj["date"]?.jsonPrimitive?.contentOrNull
                val titleHtml = (obj["title"] as? JsonObject)?.get("rendered")?.jsonPrimitive?.contentOrNull.orEmpty()
                val contentHtml = (obj["content"] as? JsonObject)?.get("rendered")?.jsonPrimitive?.contentOrNull.orEmpty()
                val excerptHtml = (obj["excerpt"] as? JsonObject)?.get("rendered")?.jsonPrimitive?.contentOrNull.orEmpty()

                val mp3Url = pickBestMp3Url(contentHtml) ?: return@mapNotNull null
                val embedded = obj["_embedded"] as? JsonObject
                val mediaArr = embedded?.get("wp:featuredmedia") as? JsonArray
                val featuredImg = (mediaArr?.firstOrNull() as? JsonObject)
                    ?.get("source_url")?.jsonPrimitive?.contentOrNull.orEmpty()
                val artworkUrl = featuredImg.takeIf { it.isNotBlank() && isLikelyAlbumArt(it) }
                    ?: pickBestArtworkUrl(contentHtml)

                val combinedText = "$titleHtml ${decodeHtmlEntities(excerptHtml)}"
                val isRemix = detectIsRemix(combinedText)
                val isInstaTrend = detectIsInstaTrend(combinedText)
                val parsed = parsePersianPostTitle(titleHtml, site.nameFa)
                val trackId = "ir_${site.id}_$postId"

                MusicTrack(
                    id = trackId,
                    title = parsed.title,
                    artist = parsed.artist,
                    album = site.nameFa,
                    durationMs = 0L,
                    mediaUri = mp3Url,
                    artworkUri = artworkUrl,
                    isFavorite = trackId in favoriteIds,
                    isImported = false,
                    isOnline = true,
                    isRemix = isRemix,
                    isInstaTrend = isInstaTrend,
                    isFullTrack = true,
                    sourceLabel = site.nameFa,
                    playCount = 0L,
                    trendScore = computeInstagramTrendScore(combinedText, isRemix, preset, dateIso)
                )
            }
        }.getOrDefault(emptyList())

        fun extractTracksDirectlyFromHtml(
            html: String,
            site: IranianMusicSite,
            preset: TrendingPreset,
            favoriteIds: Set<String> = emptySet()
        ): List<MusicTrack> {
            val results = mutableListOf<MusicTrack>()
            val blocks = ARTICLE_BLOCK_REGEX.findAll(html).map { it.groupValues[1] }.toList()
            for (block in blocks) {
                val mp3Url = pickBestMp3Url(block) ?: continue
                val titleMatch = HEADING_LINK_REGEX.find(block)
                val rawTitle = titleMatch?.groupValues?.getOrNull(2).orEmpty()
                if (rawTitle.isBlank()) continue
                val artwork = pickBestArtworkUrl(block)
                val plainBlock = decodeHtmlEntities(block)
                val isRemix = detectIsRemix("$rawTitle $plainBlock")
                val isInstaTrend = detectIsInstaTrend("$rawTitle $plainBlock")
                val parsed = parsePersianPostTitle(rawTitle, site.nameFa)
                val trackId = "ir_${site.id}_${mp3Url.hashCode().toUInt()}"
                results.add(
                    MusicTrack(
                        id = trackId,
                        title = parsed.title,
                        artist = parsed.artist,
                        album = site.nameFa,
                        durationMs = 0L,
                        mediaUri = mp3Url,
                        artworkUri = artwork,
                        isFavorite = trackId in favoriteIds,
                        isImported = false,
                        isOnline = true,
                        isRemix = isRemix,
                        isInstaTrend = isInstaTrend,
                        isFullTrack = true,
                        sourceLabel = site.nameFa,
                        playCount = 0L,
                        trendScore = computeInstagramTrendScore("$rawTitle $plainBlock", isRemix, preset)
                    )
                )
            }
            return results
        }

        fun extractPostUrlsFromArchiveHtml(html: String, baseUrl: String): List<String> {
            val cleanBase = baseUrl.trimEnd('/')
            val urls = LinkedHashSet<String>()
            HEADING_LINK_REGEX.findAll(html).forEach { match ->
                val url = match.groupValues[1]
                if (isCandidatePostUrl(url, cleanBase)) urls.add(url)
            }
            if (urls.isEmpty()) {
                ANCHOR_HREF_REGEX.findAll(html).forEach { match ->
                    val url = match.groupValues[1]
                    val text = decodeHtmlEntities(match.groupValues[2])
                    if (isCandidatePostUrl(url, cleanBase) &&
                        (text.contains("دانلود") || text.contains("آهنگ") || text.contains("ریمیکس"))
                    ) {
                        urls.add(url)
                    }
                }
            }
            return urls.toList()
        }

        private fun isCandidatePostUrl(url: String, cleanBase: String): Boolean {
            if (!url.startsWith(cleanBase)) return false
            val path = url.removePrefix(cleanBase)
            if (path.length < 4 || path == "/") return false
            val lower = path.lowercase()
            return !lower.contains("/category/") &&
                !lower.contains("/tag/") &&
                !lower.contains("/page/") &&
                !lower.contains("/wp-content/") &&
                !lower.contains("?s=") &&
                !lower.endsWith(".mp3") &&
                !lower.endsWith(".jpg") &&
                !lower.endsWith(".png")
        }

        fun parseSinglePostHtml(
            postHtml: String,
            postUrl: String,
            site: IranianMusicSite,
            preset: TrendingPreset,
            favoriteIds: Set<String> = emptySet()
        ): MusicTrack? {
            val mp3Url = pickBestMp3Url(postHtml) ?: return null
            val rawTitle = H1_REGEX.find(postHtml)?.groupValues?.getOrNull(1)
                ?: OG_TITLE_REGEX.find(postHtml)?.groupValues?.getOrNull(1)
                ?: return null
            val artworkUrl = pickBestArtworkUrl(postHtml)
            val dateIso = PUBLISHED_TIME_REGEX.find(postHtml)?.groupValues?.getOrNull(1)
            val decodedTitle = decodeHtmlEntities(rawTitle)
            val isRemix = detectIsRemix(decodedTitle)
            val isInstaTrend = detectIsInstaTrend(decodedTitle) || postUrl.contains("instagram", ignoreCase = true)
            val parsed = parsePersianPostTitle(decodedTitle, site.nameFa)
            val trackId = "ir_${site.id}_${mp3Url.hashCode().toUInt()}"

            return MusicTrack(
                id = trackId,
                title = parsed.title,
                artist = parsed.artist,
                album = site.nameFa,
                durationMs = 0L,
                mediaUri = mp3Url,
                artworkUri = artworkUrl,
                isFavorite = trackId in favoriteIds,
                isImported = false,
                isOnline = true,
                isRemix = isRemix,
                isInstaTrend = isInstaTrend,
                isFullTrack = true,
                sourceLabel = site.nameFa,
                playCount = 0L,
                trendScore = computeInstagramTrendScore(decodedTitle, isRemix, preset, dateIso)
            )
        }

        fun deduplicateAndRank(
            rawTracks: List<MusicTrack>,
            category: TrendingCategory
        ): List<MusicTrack> {
            val filtered = when (category) {
                TrendingCategory.ALL -> rawTracks
                TrendingCategory.SONGS -> rawTracks.filter { !it.isRemix }
                TrendingCategory.REMIXES -> rawTracks.filter { it.isRemix }
            }.ifEmpty { rawTracks }

            // Multi-site consensus bonus: tracks trending across multiple Iranian music sites
            // get an Instagram viral consensus boost.
            val siteCounts = mutableMapOf<String, MutableSet<String>>()
            for (track in filtered) {
                val key = track.title.lowercase().trim()
                siteCounts.getOrPut(key) { mutableSetOf() }.add(track.sourceLabel)
            }

            val uniqueByKey = LinkedHashMap<String, MusicTrack>()
            for (track in filtered.sortedByDescending { it.trendScore }) {
                val normKey = "${track.title.lowercase().trim()}|${track.artist.lowercase().trim()}"
                val extraSites = ((siteCounts[track.title.lowercase().trim()]?.size ?: 1) - 1).coerceAtLeast(0)
                val boosted = track.copy(trendScore = track.trendScore + (extraSites * 20_000L))
                val existing = uniqueByKey[normKey]
                if (existing == null || boosted.trendScore > existing.trendScore) {
                    uniqueByKey[normKey] = boosted
                }
            }

            return uniqueByKey.values
                .sortedWith(
                    compareByDescending<MusicTrack> { it.isInstaTrend }
                        .thenByDescending { it.trendScore }
                )
                .take(50)
        }
    }
}
