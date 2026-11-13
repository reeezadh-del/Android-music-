package com.example.data.repository

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
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
        // Remove any legacy bundled/default seed tracks
        dao.deleteDefaultSeedPlaylistRefs()
        dao.deleteDefaultSeedTracks()
        dao.clearPlaylistTracks(PLAYLIST_DESERT_CHILL_ID)
        dao.deletePlaylistById(PLAYLIST_DESERT_CHILL_ID)

        val devicePlaylist = PlaylistEntity(
            id = PLAYLIST_DRIVING_MIX_ID,
            name = "موسیقی‌های دستگاه",
            description = "تمامی آهنگ‌های حافظه گوشی برای پخش و اندروید اتو",
            artworkUri = drawableUri(R.drawable.img_album_night_drive_1794549204569),
            isBuiltIn = true,
            createdAt = 1L
        )
        dao.insertPlaylist(devicePlaylist)
    }

    fun hasAudioScanPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    suspend fun scanDeviceAudio(): Int = withContext(Dispatchers.IO) {
        ensureSeeded()
        if (!hasAudioScanPermission()) return@withContext -1

        val existingMap = dao.getAllTracksOnce().associateBy { it.id }
        val fallbackCovers = listOf(
            R.drawable.img_album_night_drive_1794549204569,
            R.drawable.img_album_obsidian_lofi_1794549206613,
            R.drawable.img_album_persian_horizon_1794549205476,
            R.drawable.img_album_velvet_echoes_1794549206117
        )
        val accentColors = listOf(0xFFFF6B47L, 0xFF3CE0D0L, 0xFFFFB84DL, 0xFFE8609AL)

        val scanned = mutableListOf<TrackEntity>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 3000"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val dateCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)

                var index = 0
                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val trackId = "dev_$mediaId"
                    val rawTitle = if (titleCol >= 0) cursor.getString(titleCol) else null
                    val rawArtist = if (artistCol >= 0) cursor.getString(artistCol) else null
                    val rawAlbum = if (albumCol >= 0) cursor.getString(albumCol) else null
                    val albumId = if (albumIdCol >= 0) cursor.getLong(albumIdCol) else -1L
                    val durationMs = if (durationCol >= 0) cursor.getLong(durationCol) else 0L
                    val dateAddedSec = if (dateCol >= 0) cursor.getLong(dateCol) else 0L

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        mediaId
                    ).toString()

                    val artUri = if (albumId > 0L) {
                        ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"),
                            albumId
                        ).toString()
                    } else {
                        drawableUri(fallbackCovers[index % fallbackCovers.size])
                    }

                    val existing = existingMap[trackId]
                    val title = rawTitle?.takeIf { it.isNotBlank() } ?: "آهنگ دستگاه"
                    val artist = rawArtist
                        ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                        ?: "هنرمند ناشناس"
                    val album = rawAlbum
                        ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                        ?: "موسیقی دستگاه"

                    scanned.add(
                        TrackEntity(
                            id = trackId,
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = durationMs.coerceAtLeast(1000L),
                            mediaUri = contentUri,
                            artworkUri = artUri,
                            isFavorite = existing?.isFavorite ?: false,
                            isImported = true,
                            genre = "موسیقی دستگاه",
                            accentColorHex = existing?.accentColorHex
                                ?: accentColors[index % accentColors.size],
                            addedAt = if (dateAddedSec > 0L) dateAddedSec * 1000L else System.currentTimeMillis() + index,
                            lastPlayedAt = existing?.lastPlayedAt ?: 0L
                        )
                    )
                    index++
                }
            }
        }

        // Remove stale scanned device tracks that no longer exist on storage
        val scannedIds = scanned.map { it.id }.toSet()
        existingMap.keys
            .filter { it.startsWith("dev_") && it !in scannedIds }
            .forEach { staleId ->
                dao.deleteTrackFromAllPlaylists(staleId)
                dao.deleteTrackById(staleId)
            }

        if (scanned.isNotEmpty()) {
            dao.insertTracks(scanned)
            scanned.forEachIndexed { idx, entity ->
                dao.insertPlaylistTrackCrossRef(
                    PlaylistTrackCrossRef(
                        playlistId = PLAYLIST_DRIVING_MIX_ID,
                        trackId = entity.id,
                        sortOrder = idx
                    )
                )
            }
        }

        scanned.size
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
        dao.deleteTrackFromAllPlaylists(trackId)
        dao.deleteImportedTrack(trackId)
    }

    suspend fun saveOnlineTrack(track: Track) = withContext(Dispatchers.IO) {
        dao.insertTracks(
            listOf(
                TrackEntity(
                    id = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationMs = track.durationMs,
                    mediaUri = track.mediaUri,
                    artworkUri = track.artworkUri,
                    isFavorite = true,
                    isImported = true,
                    genre = track.genre,
                    accentColorHex = track.accentColorHex,
                    addedAt = System.currentTimeMillis()
                )
            )
        )
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
            newEntities.forEachIndexed { idx, entity ->
                dao.insertPlaylistTrackCrossRef(
                    PlaylistTrackCrossRef(
                        playlistId = PLAYLIST_DRIVING_MIX_ID,
                        trackId = entity.id,
                        sortOrder = idx
                    )
                )
            }
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
