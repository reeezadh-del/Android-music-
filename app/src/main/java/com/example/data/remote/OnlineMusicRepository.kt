package com.example.data.remote

import com.example.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

enum class OnlineSourceMode(val labelFa: String) {
    TRACKS("آهنگ‌های جدید وب"),
    LIVE_RADIO("رادیو آنلاین زنده")
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

enum class OnlineRadioCategory(val labelFa: String, val language: String?, val tag: String?) {
    PERSIAN("رادیوهای فارسی", "persian", null),
    POP("پاپ جهانی", null, "pop"),
    ELECTRONIC("الکترونیک", null, "electronic"),
    CHILLOUT("آرامش و لو-فای", null, "chillout")
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
data class RadioStationDto(
    val stationuuid: String = "",
    val name: String = "",
    @SerialName("url_resolved") val urlResolved: String = "",
    val url: String = "",
    val favicon: String = "",
    val tags: String = "",
    val country: String = "",
    val language: String = "",
    val bitrate: Int = 0
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

    private val radioHosts = listOf(
        "https://de1.api.radio-browser.info",
        "https://nl1.api.radio-browser.info",
        "https://all.api.radio-browser.info"
    )

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

    suspend fun fetchLiveRadioStations(
        category: OnlineRadioCategory,
        query: String = ""
    ): Result<List<Track>> = withContext(Dispatchers.IO) {
        runCatching {
            val params = buildList {
                add("hidebroken=true")
                add("order=clickcount")
                add("reverse=true")
                add("limit=25")
                if (query.isNotBlank()) {
                    add("name=${URLEncoder.encode(query.trim(), "UTF-8")}")
                } else {
                    category.language?.let { add("language=${URLEncoder.encode(it, "UTF-8")}") }
                    category.tag?.let { add("tag=${URLEncoder.encode(it, "UTF-8")}") }
                }
            }.joinToString("&")
            val path = "/json/stations/search?$params"
            val (_, body) = fetchFirstAvailable(radioHosts, path)
            parseRadioStationsJson(category, body)
        }
    }

    fun parseAudiusTrendingJson(host: String, rawJson: String): List<Track> {
        val parsed = json.decodeFromString<AudiusTrendingResponse>(rawJson)
        return parsed.data
            .filter { it.id.isNotBlank() && it.title.isNotBlank() && it.isStreamable }
            .mapIndexed { idx, dto -> dto.toTrack(host, idx) }
    }

    fun parseRadioStationsJson(category: OnlineRadioCategory, rawJson: String): List<Track> {
        val stations = json.decodeFromString<List<RadioStationDto>>(rawJson)
        val accentPalette = listOf(0xFF3CE0D0L, 0xFFFF6B47L, 0xFFFFB84DL, 0xFFE8609AL)
        return stations
            .filter { (it.urlResolved.isNotBlank() || it.url.isNotBlank()) && it.name.isNotBlank() }
            .distinctBy { it.urlResolved.ifBlank { it.url } }
            .mapIndexed { idx, st ->
                val streamUrl = st.urlResolved.ifBlank { st.url }
                val subtitle = listOfNotNull(
                    st.country.takeIf { it.isNotBlank() },
                    st.tags.split(",").firstOrNull()?.trim()?.takeIf { it.isNotBlank() },
                    if (st.bitrate > 0) "${st.bitrate}kbps" else null
                ).joinToString(" • ").ifBlank { "پخش زنده اینترنتی" }

                Track(
                    id = "radio_${st.stationuuid.ifBlank { streamUrl.hashCode().toUInt().toString() }}",
                    title = st.name.trim(),
                    artist = subtitle,
                    album = category.labelFa,
                    durationMs = 0L,
                    mediaUri = streamUrl,
                    artworkUri = st.favicon,
                    isFavorite = false,
                    isImported = false,
                    genre = "رادیو زنده",
                    accentColorHex = accentPalette[idx % accentPalette.size],
                    addedAt = System.currentTimeMillis() + idx
                )
            }
    }

    private fun fetchFirstAvailable(hosts: List<String>, path: String): Pair<String, String> {
        var lastError: Throwable? = null
        for (host in hosts) {
            try {
                val request = Request.Builder()
                    .url("$host$path")
                    .header("User-Agent", "AvaPlayer/1.0 (Android; Media3)")
                    .header("Accept", "application/json")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            return host to body
                        }
                    } else {
                        lastError = IllegalStateException("HTTP ${response.code}")
                    }
                }
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
