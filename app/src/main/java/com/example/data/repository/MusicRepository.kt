package com.example.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.R
import com.example.data.local.AvaDatabase
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistTrackCrossRef
import com.example.data.local.PlaylistWithTracks
import com.example.data.local.TrackEntity
import com.example.data.model.AlbumSummary
import com.example.data.model.Playlist
import com.example.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MusicRepository(private val context: Context) {

    private val dao = AvaDatabase.getInstance(context).avaDao()

    val tracksFlow: Flow<List<Track>> = dao.observeAllTracks().map { list ->
        list.map { it.toDomain() }
    }

    val favoriteTracksFlow: Flow<List<Track>> = dao.observeFavoriteTracks().map { list ->
        list.map { it.toDomain() }
    }

    val recentTracksFlow: Flow<List<Track>> = dao.observeRecentTracks().map { list ->
        list.map { it.toDomain() }
    }

    val playlistsFlow: Flow<List<Playlist>> = dao.observePlaylistsWithTracks().map { list ->
        list.map { it.toDomain() }
    }

    val albumsFlow: Flow<List<AlbumSummary>> = tracksFlow.map { tracks ->
        buildAlbumSummaries(tracks)
    }

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        if (dao.getTrackCount() == 0) {
            val seedTracks = listOf(
                TrackEntity(
                    id = "seed_night_drive",
                    title = "بزرگراه نیمه‌شب (Night Drive)",
                    artist = "آوا استودیو",
                    album = "سینت‌ویو جاده",
                    durationMs = 24_000L,
                    mediaUri = rawUri(R.raw.track_night_drive),
                    artworkUri = drawableUri(R.drawable.img_album_night_drive_1794549204569),
                    isFavorite = true,
                    isImported = false,
                    genre = "سینت‌ویو",
                    accentColorHex = 0xFFFF5E3AL,
                    addedAt = 1L
                ),
                TrackEntity(
                    id = "seed_persian_horizon",
                    title = "افق کویر (Persian Horizon)",
                    artist = "نوای پارس",
                    album = "سفرهای شرقی",
                    durationMs = 26_000L,
                    mediaUri = rawUri(R.raw.track_persian_horizon),
                    artworkUri = drawableUri(R.drawable.img_album_persian_horizon_1794549205476),
                    isFavorite = true,
                    isImported = false,
                    genre = "الکترونیک ایرانی",
                    accentColorHex = 0xFF3CE0D0L,
                    addedAt = 2L
                ),
                TrackEntity(
                    id = "seed_obsidian_lofi",
                    title = "واینل بارانی (Obsidian Lo-Fi)",
                    artist = "رنگ شب",
                    album = "اتاق بارانی",
                    durationMs = 22_000L,
                    mediaUri = rawUri(R.raw.track_obsidian_lofi),
                    artworkUri = drawableUri(R.drawable.img_album_obsidian_lofi_1794549206613),
                    isFavorite = false,
                    isImported = false,
                    genre = "لو-فای",
                    accentColorHex = 0xFFFFB84DL,
                    addedAt = 3L
                ),
                TrackEntity(
                    id = "seed_velvet_echoes",
                    title = "امواج مخملی (Velvet Echoes)",
                    artist = "سپهر آوا",
                    album = "پژواک آبسیدین",
                    durationMs = 24_000L,
                    mediaUri = rawUri(R.raw.track_velvet_echoes),
                    artworkUri = drawableUri(R.drawable.img_album_velvet_echoes_1794549206117),
                    isFavorite = false,
                    isImported = false,
                    genre = "امبینت",
                    accentColorHex = 0xFFE8609AL,
                    addedAt = 4L
                )
            )
            dao.insertTracksIfAbsent(seedTracks)

            val drivingPlaylist = PlaylistEntity(
                id = PLAYLIST_DRIVING_MIX_ID,
                name = "میکس رانندگی شبانه",
                description = "گزیده آهنگ‌های ریتمیک برای رانندگی با اندروید اتو",
                artworkUri = drawableUri(R.drawable.img_album_night_drive_1794549204569),
                isBuiltIn = true,
                createdAt = 1L
            )
            val chillPlaylist = PlaylistEntity(
                id = PLAYLIST_DESERT_CHILL_ID,
                name = "آرامش کویر و باران",
                description = "قطعات ملایم برای تمرکز و سفرهای آرام",
                artworkUri = drawableUri(R.drawable.img_album_persian_horizon_1794549205476),
                isBuiltIn = true,
                createdAt = 2L
            )
            dao.insertPlaylistIfAbsent(drivingPlaylist)
            dao.insertPlaylistIfAbsent(chillPlaylist)

            dao.insertPlaylistTrackCrossRef(PlaylistTrackCrossRef(PLAYLIST_DRIVING_MIX_ID, "seed_night_drive", 0))
            dao.insertPlaylistTrackCrossRef(PlaylistTrackCrossRef(PLAYLIST_DRIVING_MIX_ID, "seed_velvet_echoes", 1))
            dao.insertPlaylistTrackCrossRef(PlaylistTrackCrossRef(PLAYLIST_DRIVING_MIX_ID, "seed_persian_horizon", 2))

            dao.insertPlaylistTrackCrossRef(PlaylistTrackCrossRef(PLAYLIST_DESERT_CHILL_ID, "seed_persian_horizon", 0))
            dao.insertPlaylistTrackCrossRef(PlaylistTrackCrossRef(PLAYLIST_DESERT_CHILL_ID, "seed_obsidian_lofi", 1))
        }
    }

    suspend fun getAllTracksOnce(): List<Track> = withContext(Dispatchers.IO) {
        ensureSeeded()
        dao.getAllTracksOnce().map { it.toDomain() }
    }

    suspend fun getFavoriteTracksOnce(): List<Track> = withContext(Dispatchers.IO) {
        ensureSeeded()
        dao.getFavoriteTracksOnce().map { it.toDomain() }
    }

    suspend fun getPlaylistsOnce(): List<Playlist> = withContext(Dispatchers.IO) {
        ensureSeeded()
        dao.getPlaylistsWithTracksOnce().map { it.toDomain() }
    }

    suspend fun getTrackById(id: String): Track? = withContext(Dispatchers.IO) {
        ensureSeeded()
        dao.getTrackById(id)?.toDomain()
    }

    suspend fun toggleFavorite(trackId: String): Boolean = withContext(Dispatchers.IO) {
        val current = dao.getTrackById(trackId) ?: return@withContext false
        val updated = !current.isFavorite
        dao.setFavorite(trackId, updated)
        updated
    }

    suspend fun markPlayed(trackId: String) = withContext(Dispatchers.IO) {
        dao.updateLastPlayed(trackId, System.currentTimeMillis())
    }

    suspend fun createPlaylist(name: String, description: String = "پلی‌لیست شخصی"): String =
        withContext(Dispatchers.IO) {
            val id = "pl_${UUID.randomUUID().toString().take(8)}"
            dao.insertPlaylist(
                PlaylistEntity(
                    id = id,
                    name = name.trim(),
                    description = description,
                    artworkUri = drawableUri(R.drawable.img_album_velvet_echoes_1794549206117),
                    isBuiltIn = false,
                    createdAt = System.currentTimeMillis()
                )
            )
            id
        }

    suspend fun addTrackToPlaylist(playlistId: String, trackId: String) = withContext(Dispatchers.IO) {
        dao.insertPlaylistTrackCrossRef(
            PlaylistTrackCrossRef(
                playlistId = playlistId,
                trackId = trackId,
                sortOrder = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
            )
        )
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) = withContext(Dispatchers.IO) {
        dao.removeTrackFromPlaylist(playlistId, trackId)
    }

    suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        dao.clearPlaylistTracks(playlistId)
        dao.deleteCustomPlaylist(playlistId)
    }

    suspend fun deleteImportedTrack(trackId: String) = withContext(Dispatchers.IO) {
        dao.deleteImportedTrack(trackId)
    }

    suspend fun importAudioUris(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext 0
        val newEntities = mutableListOf<TrackEntity>()
        val fallbackCovers = listOf(
            R.drawable.img_album_night_drive_1794549204569,
            R.drawable.img_album_obsidian_lofi_1794549206613,
            R.drawable.img_album_persian_horizon_1794549205476,
            R.drawable.img_album_velvet_echoes_1794549206117
        )
        val accentColors = listOf(0xFFFF6B47L, 0xFFFFB84DL, 0xFF3CE0D0L, 0xFFE8609AL)

        uris.forEachIndexed { index, uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Temporary permission still allows playback during session
            }

            val id = "imp_${uri.toString().hashCode().toUInt()}"
            val retriever = MediaMetadataRetriever()
            var title: String? = null
            var artist: String? = null
            var album: String? = null
            var durationMs = 0L
            var genre: String? = null
            var artworkUriString: String? = null

            try {
                retriever.setDataSource(context, uri)
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L

                val embeddedPicture = retriever.embeddedPicture
                if (embeddedPicture != null && embeddedPicture.isNotEmpty()) {
                    artworkUriString = saveEmbeddedArtAndGetContentUri(id, embeddedPicture)
                }
            } catch (_: Exception) {
                // Fallback to ContentResolver filename
            } finally {
                runCatching { retriever.release() }
            }

            val fileName = queryDisplayName(uri)
                ?.substringBeforeLast('.')
                ?.takeIf { it.isNotBlank() }

            val finalTitle = title?.takeIf { it.isNotBlank() } ?: fileName ?: "آهنگ دستگاه"
            val finalArtist = artist?.takeIf { it.isNotBlank() } ?: "هنرمند ناشناس"
            val finalAlbum = album?.takeIf { it.isNotBlank() } ?: "موسیقی دستگاه"
            val finalArt = artworkUriString ?: drawableUri(fallbackCovers[index % fallbackCovers.size])

            newEntities.add(
                TrackEntity(
                    id = id,
                    title = finalTitle,
                    artist = finalArtist,
                    album = finalAlbum,
                    durationMs = durationMs.coerceAtLeast(1000L),
                    mediaUri = uri.toString(),
                    artworkUri = finalArt,
                    isFavorite = false,
                    isImported = true,
                    genre = genre?.takeIf { it.isNotBlank() } ?: "فایل محلی",
                    accentColorHex = accentColors[index % accentColors.size],
                    addedAt = System.currentTimeMillis() + index
                )
            )
        }

        if (newEntities.isNotEmpty()) {
            dao.insertTracks(newEntities)
        }
        newEntities.size
    }

    private fun saveEmbeddedArtAndGetContentUri(trackId: String, bytes: ByteArray): String? {
        return try {
            val dir = File(context.filesDir, "album_art").apply { mkdirs() }
            val artFile = File(dir, "$trackId.jpg")
            artFile.writeBytes(bytes)
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                artFile
            ).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        } catch (_: Exception) {
            null
        }
    }

    fun drawableUri(@DrawableRes resId: Int): String {
        val resources = context.resources
        return Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(resources.getResourcePackageName(resId))
            .appendPath(resources.getResourceTypeName(resId))
            .appendPath(resources.getResourceEntryName(resId))
            .build()
            .toString()
    }

    private fun rawUri(@RawRes resId: Int): String {
        val resources = context.resources
        return Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(resources.getResourcePackageName(resId))
            .appendPath(resources.getResourceTypeName(resId))
            .appendPath(resources.getResourceEntryName(resId))
            .build()
            .toString()
    }

    fun toMediaItem(track: Track): MediaItem {
        val extras = Bundle().apply {
            putBoolean(EXTRA_IS_FAVORITE, track.isFavorite)
            putLong(EXTRA_DURATION_MS, track.durationMs)
            putLong(EXTRA_ACCENT_COLOR, track.accentColorHex)
            putString(EXTRA_GENRE, track.genre)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setGenre(track.genre)
            .setArtworkUri(Uri.parse(track.artworkUri))
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setExtras(extras)
            .build()

        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(Uri.parse(track.mediaUri))
            .setMediaMetadata(metadata)
            .build()
    }

    private fun TrackEntity.toDomain(): Track = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        mediaUri = mediaUri,
        artworkUri = artworkUri,
        isFavorite = isFavorite,
        isImported = isImported,
        genre = genre,
        accentColorHex = accentColorHex,
        addedAt = addedAt
    )

    private fun PlaylistWithTracks.toDomain(): Playlist = Playlist(
        id = playlist.id,
        name = playlist.name,
        description = playlist.description,
        artworkUri = tracks.firstOrNull()?.artworkUri ?: playlist.artworkUri,
        trackIds = tracks.map { it.id },
        isBuiltIn = playlist.isBuiltIn
    )

    companion object {
        const val PLAYLIST_DRIVING_MIX_ID = "pl_driving_mix"
        const val PLAYLIST_DESERT_CHILL_ID = "pl_desert_chill"
        const val EXTRA_IS_FAVORITE = "extra_is_favorite"
        const val EXTRA_DURATION_MS = "extra_duration_ms"
        const val EXTRA_ACCENT_COLOR = "extra_accent_color"
        const val EXTRA_GENRE = "extra_genre"

        fun buildAlbumSummaries(tracks: List<Track>): List<AlbumSummary> {
            return tracks.groupBy { it.album }
                .map { (albumName, albumTracks) ->
                    AlbumSummary(
                        name = albumName,
                        artist = albumTracks.firstOrNull()?.artist ?: "",
                        artworkUri = albumTracks.firstOrNull()?.artworkUri ?: "",
                        trackCount = albumTracks.size,
                        totalDurationMs = albumTracks.sumOf { it.durationMs },
                        tracks = albumTracks
                    )
                }
        }
    }
}
