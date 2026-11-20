package com.example.player

import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class ResolvedAudioStream(
    val url: String,
    val userAgent: String,
    val mimeType: String = "audio/mp4"
)

class YouTubeMusicRepository(
    private val client: OkHttpClient = UnblockedNetworkHelper.createUnblockedOkHttpClient(),
    private val trendingRepository: TrendingIranianRepository? = null
) {

    @Volatile
    private var preferMirrorRouting: Boolean = false

    @Volatile
    private var lastDirectProbeMs: Long = 0L

    private val directProbeClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(3500, TimeUnit.MILLISECONDS)
            .readTimeout(5500, TimeUnit.MILLISECONDS)
            .build()
    }

    private val mirrorFastClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(5000, TimeUnit.MILLISECONDS)
            .readTimeout(8000, TimeUnit.MILLISECONDS)
            .build()
    }

    fun resetDirectReachability() {
        preferMirrorRouting = false
        lastDirectProbeMs = 0L
    }

    private fun shouldTryDirectYoutube(): Boolean {
        if (!preferMirrorRouting) return true
        val elapsed = System.currentTimeMillis() - lastDirectProbeMs
        return elapsed > DIRECT_RECHECK_INTERVAL_MS
    }

    private fun markDirectResult(reachable: Boolean) {
        lastDirectProbeMs = System.currentTimeMillis()
        preferMirrorRouting = !reachable
    }

    suspend fun fetchTracks(
        category: YouTubeMusicCategory,
        customQuery: String = "",
        favoriteIds: Set<String> = emptySet()
    ): Result<List<MusicTrack>> = withContext(Dispatchers.IO) {
        runCatching {
            val query = customQuery.trim().ifBlank { category.searchQuery }

            // 1. Direct YT Music search (skipped fast if already known blocked in Iran)
            var tracks: List<MusicTrack> = emptyList()
            if (shouldTryDirectYoutube()) {
                val songsJson = postWebRemix(
                    endpoint = "search",
                    payload = buildSearchPayload(query = query, songsOnly = true),
                    httpClient = directProbeClient
                )
                tracks = songsJson?.let {
                    parseSearchResponse(rawJson = it, favoriteIds = favoriteIds)
                }.orEmpty()

                if (tracks.isEmpty()) {
                    val generalJson = postWebRemix(
                        endpoint = "search",
                        payload = buildSearchPayload(query = query, songsOnly = false),
                        httpClient = directProbeClient
                    )
                    tracks = generalJson?.let {
                        parseSearchResponse(rawJson = it, favoriteIds = favoriteIds)
                    }.orEmpty()
                }
                markDirectResult(tracks.isNotEmpty())
            }

            // 2. Unblocked Mirror Search (Invidious + Piped in parallel — works in Iran without VPN)
            if (tracks.isEmpty()) {
                tracks = searchViaUnblockedMirrors(query = query, favoriteIds = favoriteIds)
            }

            // 3. Domestic Iranian MP3 mirror fallback if all international mirrors are throttled
            if (tracks.isEmpty() && trendingRepository != null) {
                val fallbackQuery = customQuery.trim().ifBlank { category.labelFa }
                val iranianFallback = trendingRepository.fetchTrendingTracks(
                    preset = TrendingPreset.HOT,
                    category = TrendingCategory.ALL,
                    customQuery = fallbackQuery,
                    favoriteIds = favoriteIds
                ).getOrNull().orEmpty()
                if (iranianFallback.isNotEmpty()) {
                    tracks = iranianFallback.take(15)
                }
            }

            if (tracks.isEmpty()) {
                throw IllegalStateException("خطا در دریافت لیست یوتیوب موزیک؛ اتصال اینترنت را بررسی کنید.")
            }

            tracks.distinctBy { it.youtubeVideoId.ifBlank { it.id } }
        }
    }

    private suspend fun searchViaUnblockedMirrors(
        query: String,
        favoriteIds: Set<String>
    ): List<MusicTrack> = coroutineScope {
        val encoded = URLEncoder.encode(query, "UTF-8")

        val invidiousTasks = INVIDIOUS_FALLBACK_BASES.map { base ->
            async {
                val url = "$base/api/v1/search?q=$encoded&type=video&page=1"
                val raw = executeGet(url, httpClient = mirrorFastClient) ?: return@async emptyList()
                parseInvidiousSearchResponse(
                    rawJson = raw,
                    mirrorBaseUrl = base,
                    favoriteIds = favoriteIds
                )
            }
        }

        val pipedTasks = PIPED_FALLBACK_BASES.map { base ->
            async {
                val musicUrl = "$base/search?q=$encoded&filter=music_songs"
                val musicRaw = executeGet(musicUrl, httpClient = mirrorFastClient)
                val musicTracks = musicRaw?.let {
                    parsePipedSearchResponse(
                        rawJson = it,
                        mirrorBaseUrl = base,
                        favoriteIds = favoriteIds
                    )
                }.orEmpty()
                if (musicTracks.isNotEmpty()) return@async musicTracks

                val generalUrl = "$base/search?q=$encoded&filter=videos"
                val generalRaw = executeGet(generalUrl, httpClient = mirrorFastClient)
                    ?: return@async emptyList()
                parsePipedSearchResponse(
                    rawJson = generalRaw,
                    mirrorBaseUrl = base,
                    favoriteIds = favoriteIds
                )
            }
        }

        val allResults = (invidiousTasks + pipedTasks).awaitAll()
        allResults
            .firstOrNull { it.isNotEmpty() }
            ?: emptyList()
    }

    suspend fun resolveAudioStreamUrl(videoId: String): String? =
        resolveAudioStream(videoId)?.url

    suspend fun resolveAudioStream(videoId: String): ResolvedAudioStream? = withContext(Dispatchers.IO) {
        val cleanId = videoId.trim()
        if (cleanId.isBlank()) return@withContext null

        // 1. Direct InnerTube clients (when direct YouTube is reachable)
        if (shouldTryDirectYoutube()) {
            val directClients = listOf(
                PlayerClientSpec(
                    url = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
                    clientName = "IOS",
                    clientVersion = "19.45.4",
                    userAgent = "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)",
                    deviceMake = "Apple",
                    deviceModel = "iPhone16,2",
                    osName = "iPhone",
                    osVersion = "18.1.0.22B83"
                ),
                PlayerClientSpec(
                    url = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
                    clientName = "ANDROID_VR",
                    clientVersion = "1.60.19",
                    userAgent = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 14; en_US) gzip",
                    androidSdkVersion = 34
                ),
                PlayerClientSpec(
                    url = "https://music.youtube.com/youtubei/v1/player?prettyPrint=false",
                    clientName = "ANDROID_MUSIC",
                    clientVersion = "7.29.52",
                    userAgent = "com.google.android.apps.youtube.music/7.29.52 (Linux; U; Android 14; en_US) gzip",
                    androidSdkVersion = 34
                ),
                PlayerClientSpec(
                    url = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
                    clientName = "ANDROID",
                    clientVersion = "19.44.38",
                    userAgent = "com.google.android.youtube/19.44.38 (Linux; U; Android 14; en_US) gzip",
                    androidSdkVersion = 34
                ),
                PlayerClientSpec(
                    url = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
                    clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
                    clientVersion = "2.0",
                    userAgent = WEB_USER_AGENT,
                    includeEmbedThirdParty = true
                )
            )

            var hadNetworkResponse = false
            for (spec in directClients) {
                val payload = buildJsonObject {
                    putJsonObject("context") {
                        putJsonObject("client") {
                            put("clientName", spec.clientName)
                            put("clientVersion", spec.clientVersion)
                            spec.androidSdkVersion?.let { put("androidSdkVersion", it) }
                            spec.deviceMake?.let { put("deviceMake", it) }
                            spec.deviceModel?.let { put("deviceModel", it) }
                            spec.osName?.let { put("osName", it) }
                            spec.osVersion?.let { put("osVersion", it) }
                            put("hl", "en")
                            put("gl", "US")
                        }
                        if (spec.includeEmbedThirdParty) {
                            putJsonObject("thirdParty") {
                                put("embedUrl", "https://www.youtube.com")
                            }
                        }
                    }
                    put("videoId", cleanId)
                    put("contentCheckOk", true)
                    put("racyCheckOk", true)
                }.toString()

                val body = postJson(
                    url = spec.url,
                    jsonPayload = payload,
                    userAgent = spec.userAgent,
                    httpClient = directProbeClient
                )
                if (body != null) {
                    hadNetworkResponse = true
                    val resolved = extractDirectAudioCandidateFromPlayerResponse(body)
                    if (resolved != null && probeAudioStreamReachable(resolved.third, spec.userAgent)) {
                        markDirectResult(true)
                        return@withContext ResolvedAudioStream(
                            url = resolved.third,
                            userAgent = spec.userAgent,
                            mimeType = resolved.first
                        )
                    }
                } else if (!hadNetworkResponse) {
                    // First direct connection timed out or got reset by filtering -> jump straight to mirrors!
                    break
                }
            }

            if (hadNetworkResponse) {
                val watchHtml = executeGet(
                    url = "https://www.youtube.com/watch?v=$cleanId&bpctr=9999999999&has_verified=1",
                    acceptHeader = "text/html,application/xhtml+xml",
                    httpClient = directProbeClient
                )
                if (!watchHtml.isNullOrBlank()) {
                    val playerJson = extractInitialPlayerResponseJson(watchHtml)
                    val resolved = playerJson?.let { extractDirectAudioCandidateFromPlayerResponse(it) }
                    if (resolved != null && probeAudioStreamReachable(resolved.third, WEB_USER_AGENT)) {
                        markDirectResult(true)
                        return@withContext ResolvedAudioStream(
                            url = resolved.third,
                            userAgent = WEB_USER_AGENT,
                            mimeType = resolved.first
                        )
                    }
                }
            } else {
                markDirectResult(false)
            }
        }

        // 2. Unblocked Mirror Stream Resolution (Piped & Invidious with ?local=true proxy in parallel)
        resolveViaUnblockedMirrors(cleanId)
    }

    private suspend fun resolveViaUnblockedMirrors(videoId: String): ResolvedAudioStream? = coroutineScope {
        val pipedTasks = PIPED_FALLBACK_BASES.map { base ->
            async {
                val body = executeGet("$base/streams/$videoId", httpClient = mirrorFastClient)
                    ?: return@async emptyList()
                extractAudioCandidatesFromPipedJson(body, base)
            }
        }

        val invidiousTasks = INVIDIOUS_FALLBACK_BASES.map { base ->
            async {
                val cleanBase = base.trimEnd('/')
                val body = executeGet("$cleanBase/api/v1/videos/$videoId?local=true", httpClient = mirrorFastClient)
                val parsed = if (!body.isNullOrBlank()) {
                    extractProxiedAudioCandidatesFromInvidiousJson(body, cleanBase)
                } else {
                    emptyList()
                }
                // Always include Invidious built-in local proxy endpoints as fallback candidates
                parsed + listOf(
                    "audio/mp4" to "$cleanBase/latest_version?id=$videoId&itag=140&local=true",
                    "video/mp4" to "$cleanBase/latest_version?id=$videoId&itag=18&local=true"
                )
            }
        }

        val rawCandidates = (pipedTasks + invidiousTasks)
            .awaitAll()
            .flatten()
            .distinctBy { it.second }

        // Prioritize proxy URLs that bypass googlevideo.com completely
        val prioritized = rawCandidates.sortedByDescending { (_, url) ->
            UnblockedNetworkHelper.isProxiedNonGoogleVideoUrl(url)
        }

        for ((mime, streamUrl) in prioritized) {
            if (preferMirrorRouting && !UnblockedNetworkHelper.isProxiedNonGoogleVideoUrl(streamUrl)) {
                continue
            }
            if (probeAudioStreamReachable(streamUrl, WEB_USER_AGENT)) {
                return@coroutineScope ResolvedAudioStream(
                    url = streamUrl,
                    userAgent = WEB_USER_AGENT,
                    mimeType = mime.ifBlank { "audio/mp4" }
                )
            }
        }

        null
    }

    private fun probeAudioStreamReachable(url: String, userAgent: String): Boolean = runCatching {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Range", "bytes=0-1023")
            .header("Accept", "*/*")
            .build()
        mirrorFastClient.newCall(req).execute().use { res ->
            if (!res.isSuccessful && res.code != 206) return@use false
            val ct = res.header("Content-Type").orEmpty().lowercase()
            if (ct.contains("text/html") || ct.contains("application/json")) return@use false
            true
        }
    }.getOrDefault(false)

    private fun buildWebRemixContext(): JsonObject = buildJsonObject {
        putJsonObject("client") {
            put("clientName", "WEB_REMIX")
            put("clientVersion", WEB_REMIX_VERSION)
            put("hl", "en")
            put("gl", "US")
        }
    }

    private fun buildSearchPayload(query: String, songsOnly: Boolean): String {
        return buildJsonObject {
            put("context", buildWebRemixContext())
            put("query", query)
            if (songsOnly) {
                put("params", SONGS_FILTER_PARAMS)
            }
        }.toString()
    }

    private fun postWebRemix(
        endpoint: String,
        payload: String,
        httpClient: OkHttpClient = client
    ): String? {
        return postJson(
            url = "https://music.youtube.com/youtubei/v1/$endpoint?prettyPrint=false",
            jsonPayload = payload,
            userAgent = WEB_USER_AGENT,
            httpClient = httpClient
        )
    }

    private fun postJson(
        url: String,
        jsonPayload: String,
        userAgent: String,
        httpClient: OkHttpClient = client
    ): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            .post(jsonPayload.toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Origin", "https://music.youtube.com")
            .header("User-Agent", userAgent)
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string()
        }
    }.getOrNull()

    private fun executeGet(
        url: String,
        acceptHeader: String = "application/json",
        httpClient: OkHttpClient = client
    ): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("Accept", acceptHeader)
            .header("User-Agent", WEB_USER_AGENT)
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string()
        }
    }.getOrNull()

    private data class PlayerClientSpec(
        val url: String,
        val clientName: String,
        val clientVersion: String,
        val userAgent: String,
        val androidSdkVersion: Int? = null,
        val deviceMake: String? = null,
        val deviceModel: String? = null,
        val osName: String? = null,
        val osVersion: String? = null,
        val includeEmbedThirdParty: Boolean = false
    )

    companion object {
        private const val DIRECT_RECHECK_INTERVAL_MS = 3 * 60 * 1000L
        private const val WEB_REMIX_VERSION = "1.20241118.01.00"
        private const val SONGS_FILTER_PARAMS = "EgWKAQIIAWoMEAMQBBAJEAoQBRAV"
        private const val WEB_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val jsonParser = Json { ignoreUnknownKeys = true }

        val PIPED_FALLBACK_BASES = listOf(
            "https://api.piped.private.coffee",
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.adminforge.de",
            "https://pipedapi.tokhmi.xyz",
            "https://pipedapi.in.projectsegfau.lt",
            "https://pipedapi.r4fo.com",
            "https://api.piped.yt"
        )

        val INVIDIOUS_FALLBACK_BASES = listOf(
            "https://inv.nadeko.net",
            "https://yewtu.be",
            "https://invidious.nerdvpn.de",
            "https://invidious.privacyredirect.com",
            "https://inv.tux.pizza",
            "https://invidious.perennialte.ch",
            "https://iv.datura.network",
            "https://invidious.fdn.fr"
        )

        private val DURATION_REGEX = Regex("""^(?:\d{1,2}:)?\d{1,2}:\d{2}$""")

        fun parseDurationLabelToMs(label: String): Long {
            val trimmed = label.trim()
            if (!DURATION_REGEX.matches(trimmed)) return 0L
            val parts = trimmed.split(':').mapNotNull { it.toLongOrNull() }
            return when (parts.size) {
                2 -> ((parts[0] * 60L) + parts[1]) * 1000L
                3 -> ((parts[0] * 3600L) + (parts[1] * 60L) + parts[2]) * 1000L
                else -> 0L
            }
        }

        fun upgradeThumbnailUrl(rawUrl: String, videoId: String): String {
            val clean = rawUrl.trim()
            if (clean.isBlank()) {
                return if (videoId.isNotBlank()) "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" else ""
            }
            return clean.replace(Regex("""=w\d+-h\d+[^&]*"""), "=w544-h544-l90-rj")
        }

        fun parseInvidiousSearchResponse(
            rawJson: String,
            mirrorBaseUrl: String,
            favoriteIds: Set<String> = emptySet()
        ): List<MusicTrack> = runCatching {
            val arr = jsonParser.parseToJsonElement(rawJson) as? JsonArray ?: return@runCatching emptyList()
            val cleanBase = mirrorBaseUrl.trimEnd('/')
            arr.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val type = obj["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (type.isNotBlank() && type != "video" && type != "song") return@mapNotNull null

                val videoId = obj["videoId"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val rawTitle = obj["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (videoId.isBlank() || rawTitle.isBlank()) return@mapNotNull null

                val author = obj["author"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?.takeIf { it.isNotBlank() } ?: "YouTube Music"
                val lengthSec = obj["lengthSeconds"]?.jsonPrimitive?.longOrNull ?: 0L
                val views = obj["viewCount"]?.jsonPrimitive?.longOrNull ?: 0L
                val trackId = "ytm_$videoId"

                // Use the mirror's own /vi/ proxy so album art loads in Iran without VPN
                val proxiedArt = "$cleanBase/vi/$videoId/mqdefault.jpg"
                val watchUrl = "https://music.youtube.com/watch?v=$videoId"

                MusicTrack(
                    id = trackId,
                    title = rawTitle,
                    artist = author,
                    album = "YouTube Music",
                    durationMs = (lengthSec * 1000L).coerceAtLeast(0L),
                    mediaUri = watchUrl,
                    artworkUri = proxiedArt,
                    isFavorite = trackId in favoriteIds,
                    isImported = false,
                    isOnline = true,
                    isRemix = TrendingIranianRepository.detectIsRemix(rawTitle),
                    isInstaTrend = false,
                    isFullTrack = true,
                    sourceLabel = "YouTube Music",
                    playCount = views,
                    trendScore = views,
                    lyrics = "",
                    pageUrl = watchUrl,
                    youtubeVideoId = videoId
                )
            }
        }.getOrDefault(emptyList())

        fun parsePipedSearchResponse(
            rawJson: String,
            mirrorBaseUrl: String,
            favoriteIds: Set<String> = emptySet()
        ): List<MusicTrack> = runCatching {
            val root = jsonParser.parseToJsonElement(rawJson).jsonObject
            val items = root["items"] as? JsonArray ?: return@runCatching emptyList()
            val cleanBase = mirrorBaseUrl.trimEnd('/')
            items.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val urlField = obj["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val videoId = extractVideoIdFromWatchPath(urlField) ?: return@mapNotNull null
                val title = obj["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (title.isBlank()) return@mapNotNull null

                val artist = obj["uploaderName"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?.takeIf { it.isNotBlank() } ?: "YouTube Music"
                val durationSec = obj["duration"]?.jsonPrimitive?.longOrNull ?: 0L
                val views = obj["views"]?.jsonPrimitive?.longOrNull ?: 0L
                val rawThumb = obj["thumbnail"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
                val artwork = when {
                    rawThumb.startsWith("/") -> "$cleanBase$rawThumb"
                    rawThumb.isNotBlank() -> rawThumb
                    else -> "${INVIDIOUS_FALLBACK_BASES.first()}/vi/$videoId/mqdefault.jpg"
                }

                val trackId = "ytm_$videoId"
                val watchUrl = "https://music.youtube.com/watch?v=$videoId"
                MusicTrack(
                    id = trackId,
                    title = title,
                    artist = artist,
                    album = "YouTube Music",
                    durationMs = (durationSec * 1000L).coerceAtLeast(0L),
                    mediaUri = watchUrl,
                    artworkUri = artwork,
                    isFavorite = trackId in favoriteIds,
                    isImported = false,
                    isOnline = true,
                    isRemix = TrendingIranianRepository.detectIsRemix(title),
                    isInstaTrend = false,
                    isFullTrack = true,
                    sourceLabel = "YouTube Music",
                    playCount = views,
                    trendScore = views,
                    lyrics = "",
                    pageUrl = watchUrl,
                    youtubeVideoId = videoId
                )
            }
        }.getOrDefault(emptyList())

        private fun extractVideoIdFromWatchPath(urlOrPath: String): String? {
            val clean = urlOrPath.trim()
            if (clean.isBlank()) return null
            val vParam = Regex("""[?&]v=([A-Za-z0-9_-]{6,})""").find(clean)?.groupValues?.getOrNull(1)
            if (!vParam.isNullOrBlank()) return vParam
            val shortMatch = Regex("""youtu\.be/([A-Za-z0-9_-]{6,})""").find(clean)?.groupValues?.getOrNull(1)
            return shortMatch
        }

        fun parseSearchResponse(
            rawJson: String,
            favoriteIds: Set<String> = emptySet()
        ): List<MusicTrack> = runCatching {
            val root = jsonParser.parseToJsonElement(rawJson).jsonObject
            val tabs = root["contents"]
                ?.jsonObject?.get("tabbedSearchResultsRenderer")
                ?.jsonObject?.get("tabs")?.jsonArray
                ?: return@runCatching emptyList()

            val sections = (tabs.firstOrNull() as? JsonObject)
                ?.get("tabRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray
                ?: return@runCatching emptyList()

            val results = mutableListOf<MusicTrack>()
            for (secEl in sections) {
                val shelf = (secEl as? JsonObject)?.get("musicShelfRenderer") as? JsonObject ?: continue
                val items = shelf["contents"] as? JsonArray ?: continue
                for (itemEl in items) {
                    val renderer = (itemEl as? JsonObject)?.get("musicResponsiveListItemRenderer") as? JsonObject
                        ?: continue
                    parseResponsiveItem(renderer, favoriteIds)?.let { results.add(it) }
                }
            }
            results
        }.getOrDefault(emptyList())

        private fun parseResponsiveItem(
            renderer: JsonObject,
            favoriteIds: Set<String>
        ): MusicTrack? {
            val flexColumns = renderer["flexColumns"] as? JsonArray ?: return null
            val firstColRuns = (flexColumns.getOrNull(0) as? JsonObject)
                ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                ?.get("text")?.jsonObject
                ?.get("runs") as? JsonArray
                ?: return null

            val title = firstColRuns.mapNotNull {
                (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
            }.joinToString("").trim()
            if (title.isBlank()) return null

            val videoId = extractVideoId(renderer, firstColRuns) ?: return null

            val secondColRuns = (flexColumns.getOrNull(1) as? JsonObject)
                ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                ?.get("text")?.jsonObject
                ?.get("runs") as? JsonArray

            val metaParts = secondColRuns
                ?.mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull?.trim() }
                ?.filter { it.isNotBlank() && it != "•" && it != "·" }
                .orEmpty()

            var durationMs = 0L
            val nonDurationParts = mutableListOf<String>()
            for (part in metaParts) {
                if (DURATION_REGEX.matches(part) && durationMs == 0L) {
                    durationMs = parseDurationLabelToMs(part)
                } else if (!part.equals("Song", ignoreCase = true) &&
                    !part.equals("Video", ignoreCase = true) &&
                    !part.endsWith("plays", ignoreCase = true) &&
                    !part.endsWith("views", ignoreCase = true)
                ) {
                    nonDurationParts.add(part)
                }
            }

            val artist = nonDurationParts.firstOrNull().orEmpty().ifBlank { "YouTube Music" }
            val album = nonDurationParts.getOrNull(1).orEmpty().ifBlank { "YouTube Music" }

            val thumbList = renderer["thumbnail"]?.jsonObject
                ?.get("musicThumbnailRenderer")?.jsonObject
                ?.get("thumbnail")?.jsonObject
                ?.get("thumbnails") as? JsonArray
            val rawThumb = (thumbList?.lastOrNull() as? JsonObject)
                ?.get("url")?.jsonPrimitive?.contentOrNull.orEmpty()
            val artworkUri = upgradeThumbnailUrl(rawThumb, videoId)

            val trackId = "ytm_$videoId"
            val watchUrl = "https://music.youtube.com/watch?v=$videoId"
            val isRemix = TrendingIranianRepository.detectIsRemix(title)

            return MusicTrack(
                id = trackId,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                mediaUri = watchUrl,
                artworkUri = artworkUri,
                isFavorite = trackId in favoriteIds,
                isImported = false,
                isOnline = true,
                isRemix = isRemix,
                isInstaTrend = false,
                isFullTrack = true,
                sourceLabel = "YouTube Music",
                playCount = 0L,
                trendScore = 0L,
                lyrics = "",
                pageUrl = watchUrl,
                youtubeVideoId = videoId
            )
        }

        private fun extractVideoId(renderer: JsonObject, firstColRuns: JsonArray): String? {
            val fromPlaylistData = renderer["playlistItemData"]?.jsonObject
                ?.get("videoId")?.jsonPrimitive?.contentOrNull
            if (!fromPlaylistData.isNullOrBlank()) return fromPlaylistData

            val fromFirstRun = (firstColRuns.firstOrNull() as? JsonObject)
                ?.get("navigationEndpoint")?.jsonObject
                ?.get("watchEndpoint")?.jsonObject
                ?.get("videoId")?.jsonPrimitive?.contentOrNull
            if (!fromFirstRun.isNullOrBlank()) return fromFirstRun

            return renderer["overlay"]?.jsonObject
                ?.get("musicItemThumbnailOverlayRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("musicPlayButtonRenderer")?.jsonObject
                ?.get("playNavigationEndpoint")?.jsonObject
                ?.get("watchEndpoint")?.jsonObject
                ?.get("videoId")?.jsonPrimitive?.contentOrNull
        }

        fun extractDirectAudioUrlFromPlayerResponse(rawJson: String): String? =
            extractDirectAudioCandidateFromPlayerResponse(rawJson)?.third

        fun extractDirectAudioCandidateFromPlayerResponse(rawJson: String): Triple<String, Int, String>? = runCatching {
            val root = jsonParser.parseToJsonElement(rawJson).jsonObject
            val status = root["playabilityStatus"]?.jsonObject
                ?.get("status")?.jsonPrimitive?.contentOrNull
            if (status != "OK") return@runCatching null

            val streamingData = root["streamingData"]?.jsonObject ?: return@runCatching null
            val adaptiveFormats = streamingData["adaptiveFormats"] as? JsonArray
            val audioCandidates = adaptiveFormats?.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val mime = obj["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val url = obj["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val bitrate = obj["bitrate"]?.jsonPrimitive?.intOrNull ?: 0
                if (mime.startsWith("audio/", ignoreCase = true) && url.isNotBlank()) {
                    Triple(mime, bitrate, url)
                } else null
            }.orEmpty()

            if (audioCandidates.isNotEmpty()) {
                return@runCatching audioCandidates
                    .sortedWith(
                        compareByDescending<Triple<String, Int, String>> {
                            it.first.contains("mp4", ignoreCase = true)
                        }.thenByDescending { it.second }
                    )
                    .first()
            }

            // Fallback to progressive muxed mp4 formats when adaptive audio is encrypted/omitted
            val muxedFormats = streamingData["formats"] as? JsonArray ?: return@runCatching null
            muxedFormats.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val mime = obj["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val url = obj["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val bitrate = obj["bitrate"]?.jsonPrimitive?.intOrNull ?: 0
                if (url.isNotBlank() &&
                    (mime.startsWith("audio/", ignoreCase = true) || mime.startsWith("video/mp4", ignoreCase = true))
                ) {
                    Triple(mime, bitrate, url)
                } else null
            }.sortedByDescending { it.second }.firstOrNull()
        }.getOrNull()

        private fun extractInitialPlayerResponseJson(html: String): String? {
            val marker = "var ytInitialPlayerResponse = "
            val altMarker = "ytInitialPlayerResponse = "
            val startMarkerIdx = html.indexOf(marker).takeIf { it >= 0 }
                ?.let { it + marker.length }
                ?: html.indexOf(altMarker).takeIf { it >= 0 }?.let { it + altMarker.length }
                ?: return null
            val openBrace = html.indexOf('{', startMarkerIdx).takeIf { it >= 0 } ?: return null
            var depth = 0
            var inString = false
            var escaped = false
            for (i in openBrace until html.length) {
                val ch = html[i]
                if (escaped) {
                    escaped = false
                    continue
                }
                if (ch == '\\' && inString) {
                    escaped = true
                    continue
                }
                if (ch == '"') {
                    inString = !inString
                    continue
                }
                if (!inString) {
                    if (ch == '{') depth++
                    else if (ch == '}') {
                        depth--
                        if (depth == 0) {
                            return html.substring(openBrace, i + 1)
                        }
                    }
                }
            }
            return null
        }

        fun extractAudioCandidatesFromPipedJson(
            rawJson: String,
            mirrorBaseUrl: String
        ): List<Pair<String, String>> = runCatching {
            val root = jsonParser.parseToJsonElement(rawJson).jsonObject
            val streams = root["audioStreams"] as? JsonArray ?: return@runCatching emptyList()
            val cleanBase = mirrorBaseUrl.trimEnd('/')
            streams.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val rawUrl = obj["url"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
                val resolvedUrl = if (rawUrl.startsWith("/")) "$cleanBase$rawUrl" else rawUrl
                val mime = obj["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val bitrate = obj["bitrate"]?.jsonPrimitive?.intOrNull ?: 0
                if (resolvedUrl.startsWith("http", ignoreCase = true)) {
                    Triple(mime, bitrate, resolvedUrl)
                } else null
            }.sortedWith(
                compareByDescending<Triple<String, Int, String>> {
                    UnblockedNetworkHelper.isProxiedNonGoogleVideoUrl(it.third)
                }.thenByDescending {
                    it.first.contains("mp4", ignoreCase = true)
                }.thenByDescending { it.second }
            ).map { it.first to it.third }
        }.getOrDefault(emptyList())

        fun extractProxiedAudioCandidatesFromInvidiousJson(
            rawJson: String,
            mirrorBaseUrl: String
        ): List<Pair<String, String>> = runCatching {
            val root = jsonParser.parseToJsonElement(rawJson).jsonObject
            val cleanBase = mirrorBaseUrl.trimEnd('/')
            val adaptive = (root["adaptiveFormats"] as? JsonArray).orEmpty()
            val muxed = (root["formatStreams"] as? JsonArray).orEmpty()

            (adaptive + muxed).mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val type = obj["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val rawUrl = obj["url"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
                if (rawUrl.isBlank()) return@mapNotNull null
                val resolvedUrl = if (rawUrl.startsWith("/")) "$cleanBase$rawUrl" else rawUrl
                val bitrate = obj["bitrate"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                val isAudioOrMp4 = type.startsWith("audio/", ignoreCase = true) ||
                    type.startsWith("video/mp4", ignoreCase = true)
                if (isAudioOrMp4 && resolvedUrl.startsWith("http", ignoreCase = true)) {
                    Triple(type, bitrate, resolvedUrl)
                } else null
            }.sortedWith(
                compareByDescending<Triple<String, Int, String>> {
                    UnblockedNetworkHelper.isProxiedNonGoogleVideoUrl(it.third)
                }.thenByDescending {
                    it.first.startsWith("audio/", ignoreCase = true)
                }.thenByDescending {
                    it.first.contains("mp4", ignoreCase = true)
                }.thenByDescending { it.second }
            ).map { it.first to it.third }
        }.getOrDefault(emptyList())
    }
}
