package com.example.player

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicLibraryRepository(
    private val context: Context,
    private val preferences: MusicPreferences
) {

    fun hasAudioPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun loadAllTracks(
        favoriteIds: Set<String>,
        importedUris: Set<String>
    ): List<MusicTrack> = withContext(Dispatchers.IO) {
        val deviceTracks = if (hasAudioPermission()) scanMediaStoreTracks(favoriteIds) else emptyList()
        val existingUris = deviceTracks.map { it.mediaUri }.toSet()

        val importedTracks = importedUris
            .asSequence()
            .filter { it !in existingUris }
            .mapNotNull { uriStr -> readImportedTrack(Uri.parse(uriStr), favoriteIds) }
            .toList()

        (importedTracks + deviceTracks).distinctBy { it.mediaUri }
    }

    suspend fun persistImportedUris(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        val valid = mutableListOf<String>()
        for (uri in uris) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            valid.add(uri.toString())
        }
        preferences.addImportedUris(valid)
        valid.size
    }

    private fun scanMediaStoreTracks(favoriteIds: Set<String>): List<MusicTrack> {
        val tracks = mutableListOf<MusicTrack>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
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
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val durationMs = cursor.getLong(durCol).coerceAtLeast(0L)
                    if (durationMs < 3_000L) continue

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        mediaId
                    )
                    val albumId = cursor.getLong(albumIdCol)
                    val artworkUri = if (albumId > 0L) {
                        ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"),
                            albumId
                        ).toString()
                    } else ""

                    val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "آهنگ بدون نام"
                    val rawArtist = cursor.getString(artistCol)?.trim().orEmpty()
                    val artist = if (rawArtist.isBlank() || rawArtist == "<unknown>") "هنرمند ناشناس" else rawArtist
                    val album = cursor.getString(albumCol)?.takeIf { it.isNotBlank() } ?: ""
                    val trackId = "ms_$mediaId"

                    tracks.add(
                        MusicTrack(
                            id = trackId,
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = durationMs,
                            mediaUri = contentUri.toString(),
                            artworkUri = artworkUri,
                            isFavorite = trackId in favoriteIds,
                            isImported = false
                        )
                    )
                }
            }
        }
        return tracks
    }

    private fun readImportedTrack(uri: Uri, favoriteIds: Set<String>): MusicTrack? {
        val uriStr = uri.toString()
        val id = "imp_${uriStr.hashCode().toUInt()}"
        var title = ""
        var artist = "هنرمند ناشناس"
        var album = ""
        var durationMs = 0L

        runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty()
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() } ?: artist
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            retriever.release()
        }

        if (title.isBlank()) {
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c ->
                        if (c.moveToFirst()) {
                            val name = c.getString(0).orEmpty()
                            title = name.substringBeforeLast('.')
                        }
                    }
            }
        }

        return MusicTrack(
            id = id,
            title = title.ifBlank { "فایل صوتی" },
            artist = artist,
            album = album,
            durationMs = durationMs,
            mediaUri = uriStr,
            artworkUri = "",
            isFavorite = id in favoriteIds,
            isImported = true
        )
    }

    fun toMediaItem(track: MusicTrack): MediaItem {
        return AutoMediaLibraryTree.toPlayableMediaItem(track)
    }
}
