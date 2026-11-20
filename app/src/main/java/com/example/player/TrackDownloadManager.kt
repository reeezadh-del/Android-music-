package com.example.player

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface TrackDownloadStatus {
    data object Idle : TrackDownloadStatus
    data class Downloading(val progress: Float) : TrackDownloadStatus
    data class Downloaded(val localUri: String, val fileName: String) : TrackDownloadStatus
    data class Error(val message: String) : TrackDownloadStatus
}

class TrackDownloadManager(
    private val context: Context,
    private val preferences: MusicPreferences,
    private val youTubeMusicRepository: YouTubeMusicRepository,
    private val trendingRepository: TrendingIranianRepository? = null,
    private val client: OkHttpClient = UnblockedNetworkHelper.createUnblockedOkHttpClient(
        connectTimeoutSec = 10L,
        readTimeoutSec = 45L
    )
) {
    private val _downloadStates = MutableStateFlow<Map<String, TrackDownloadStatus>>(emptyMap())
    val downloadStates: StateFlow<Map<String, TrackDownloadStatus>> = _downloadStates.asStateFlow()

    private val downloadsDir: File
        get() = File(context.filesDir, "downloads").apply { mkdirs() }

    fun syncPersistedDownloads(downloadedTracks: List<MusicTrack>) {
        if (downloadedTracks.isEmpty()) return
        _downloadStates.update { current ->
            val next = current.toMutableMap()
            for (track in downloadedTracks) {
                val existingState = next[track.id]
                if (existingState is TrackDownloadStatus.Downloading) continue
                val uri = Uri.parse(track.mediaUri)
                val file = uri.path?.let { File(it) }
                if (file != null && file.exists() && file.length() > 0L) {
                    next[track.id] = TrackDownloadStatus.Downloaded(
                        localUri = track.mediaUri,
                        fileName = file.name
                    )
                }
            }
            next
        }
    }

    suspend fun downloadTrack(track: MusicTrack): Result<MusicTrack> = withContext(Dispatchers.IO) {
        val currentState = _downloadStates.value[track.id]
        if (currentState is TrackDownloadStatus.Downloading) {
            return@withContext Result.failure(IllegalStateException("در حال دانلود است"))
        }

        if (currentState is TrackDownloadStatus.Downloaded) {
            val existingFile = Uri.parse(currentState.localUri).path?.let { File(it) }
            if (existingFile != null && existingFile.exists() && existingFile.length() > 0L) {
                return@withContext Result.success(
                    track.copy(
                        mediaUri = currentState.localUri,
                        isOnline = false,
                        isDownloaded = true,
                        sourceLabel = "دانلود شده"
                    )
                )
            }
        }

        _downloadStates.update { it + (track.id to TrackDownloadStatus.Downloading(-1f)) }

        val result = runCatching {
            val isYoutube = track.youtubeVideoId.isNotBlank() ||
                track.id.startsWith("ytm_") ||
                track.mediaUri.contains("music.youtube.com/watch")

            val resolvedStream = if (isYoutube) {
                val videoId = track.youtubeVideoId.ifBlank {
                    track.id.removePrefix("ytm_")
                }
                youTubeMusicRepository.resolveAudioStream(videoId)
                    ?: resolveIranianFallbackDownloadStream(track)
                    ?: throw IllegalStateException("دریافت لینک دانلود مستقیم ممکن نشد؛ اتصال اینترنت را بررسی کنید.")
            } else {
                val directUrl = track.mediaUri.trim()
                if (!directUrl.startsWith("http", ignoreCase = true)) {
                    throw IllegalStateException("لینک دانلود معتبر برای این آهنگ موجود نیست.")
                }
                ResolvedAudioStream(
                    url = directUrl,
                    userAgent = DEFAULT_USER_AGENT,
                    mimeType = if (directUrl.contains(".m4a", ignoreCase = true)) "audio/mp4" else "audio/mpeg"
                )
            }

            val requestBuilder = Request.Builder()
                .url(resolvedStream.url)
                .header("User-Agent", resolvedStream.userAgent)
                .header("Accept", "audio/*,*/*;q=0.8")
            if (track.pageUrl.startsWith("http", ignoreCase = true)) {
                requestBuilder.header("Referer", track.pageUrl)
            }

            val response = client.newCall(requestBuilder.build()).execute()
            response.use { res ->
                if (!res.isSuccessful) {
                    throw IllegalStateException("خطای سرور هنگام دانلود (${res.code})")
                }
                val body = res.body ?: throw IllegalStateException("فایل صوتی خالی دریافت شد")
                val contentType = res.header("Content-Type").orEmpty().lowercase()
                if (contentType.contains("text/html")) {
                    throw IllegalStateException("لینک مستقیم فایل صوتی در دسترس نبود")
                }

                val extension = resolveAudioExtension(contentType, resolvedStream.mimeType, resolvedStream.url)
                val baseName = buildSafeFileName(track)
                val finalFile = File(downloadsDir, "$baseName.$extension")
                val partFile = File(downloadsDir, "$baseName.$extension.part")
                partFile.delete()

                val totalBytes = body.contentLength()
                var downloadedBytes = 0L
                val buffer = ByteArray(16 * 1024)

                body.byteStream().use { input ->
                    partFile.outputStream().use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            val progress = if (totalBytes > 0L) {
                                (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            } else {
                                -1f
                            }
                            _downloadStates.update {
                                it + (track.id to TrackDownloadStatus.Downloading(progress))
                            }
                        }
                        output.flush()
                    }
                }

                if (partFile.length() < 8_192L) {
                    partFile.delete()
                    throw IllegalStateException("فایل دانلودشده ناقص یا نامعتبر است")
                }

                if (finalFile.exists()) {
                    finalFile.delete()
                }
                if (!partFile.renameTo(finalFile)) {
                    partFile.copyTo(finalFile, overwrite = true)
                    partFile.delete()
                }

                exportToDeviceMusicFolder(finalFile, track, extension)

                val localUri = Uri.fromFile(finalFile).toString()
                val offlineTrack = track.copy(
                    mediaUri = localUri,
                    isOnline = false,
                    isDownloaded = true,
                    sourceLabel = "دانلود شده"
                )
                preferences.addDownloadedTrack(offlineTrack)
                _downloadStates.update {
                    it + (track.id to TrackDownloadStatus.Downloaded(localUri, finalFile.name))
                }
                offlineTrack
            }
        }

        result.onFailure { err ->
            _downloadStates.update {
                it + (track.id to TrackDownloadStatus.Error(err.localizedMessage ?: "دانلود ناموفق بود"))
            }
        }

        result
    }

    suspend fun removeDownload(track: MusicTrack) = withContext(Dispatchers.IO) {
        runCatching {
            val state = _downloadStates.value[track.id]
            val targetPath = when (state) {
                is TrackDownloadStatus.Downloaded -> Uri.parse(state.localUri).path
                else -> Uri.parse(track.mediaUri).path
            }
            if (!targetPath.isNullOrBlank()) {
                val file = File(targetPath)
                if (file.exists() && file.parentFile?.absolutePath == downloadsDir.absolutePath) {
                    file.delete()
                }
            }
            preferences.removeDownloadedTrack(track.id)
            _downloadStates.update { it - track.id }
        }
    }

    private suspend fun resolveIranianFallbackDownloadStream(track: MusicTrack): ResolvedAudioStream? {
        val repo = trendingRepository ?: return null
        val queries = listOfNotNull(
            "${track.artist} ${track.title}".trim().takeIf { it.isNotBlank() },
            track.title.trim().takeIf { it.isNotBlank() }
        ).distinct()
        for (query in queries) {
            val match = runCatching {
                repo.fetchTrendingTracks(
                    preset = TrendingPreset.HOT,
                    category = TrendingCategory.ALL,
                    customQuery = query,
                    favoriteIds = emptySet()
                ).getOrNull()?.firstOrNull()
            }.getOrNull()
            if (match != null && match.mediaUri.startsWith("http", ignoreCase = true)) {
                return ResolvedAudioStream(
                    url = match.mediaUri,
                    userAgent = DEFAULT_USER_AGENT,
                    mimeType = if (match.mediaUri.contains(".m4a", ignoreCase = true)) "audio/mp4" else "audio/mpeg"
                )
            }
        }
        return null
    }

    private fun exportToDeviceMusicFolder(file: File, track: MusicTrack, extension: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            val mimeType = when (extension) {
                "m4a" -> "audio/mp4"
                "webm" -> "audio/webm"
                "ogg" -> "audio/ogg"
                else -> "audio/mpeg"
            }
            val displayName = "${buildSafeFileName(track)}.$extension"
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.TITLE, track.title)
                put(MediaStore.Audio.Media.ARTIST, track.artist)
                put(MediaStore.Audio.Media.ALBUM, track.album.ifBlank { "Ava Music" })
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/AvaMusic")
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input ->
                    input.copyTo(out)
                }
            }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    }

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        fun resolveAudioExtension(contentType: String, mimeType: String, url: String): String {
            val combined = "$contentType $mimeType $url".lowercase()
            return when {
                combined.contains("mp4") || combined.contains("m4a") || combined.contains("aac") -> "m4a"
                combined.contains("webm") -> "webm"
                combined.contains("ogg") || combined.contains("opus") -> "ogg"
                else -> "mp3"
            }
        }

        fun buildSafeFileName(track: MusicTrack): String {
            val raw = "${track.artist}_${track.title}".trim()
            val sanitized = raw
                .replace(Regex("""[\\/:*?"<>|\s]+"""), "_")
                .trim('_')
                .take(48)
                .ifBlank { "ava_track" }
            val hash = track.id.hashCode().toUInt().toString(16)
            return "${sanitized}_$hash"
        }
    }
}
