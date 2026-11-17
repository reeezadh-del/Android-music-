package com.example.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaConstants

object AutoMediaLibraryTree {

    const val ROOT_ID = "ava_root"
    const val NODE_ALL_SONGS = "ava_all_songs"
    const val NODE_FAVORITES = "ava_favorites"
    const val NODE_TRENDING = "ava_trending"

    fun buildRootItem(): MediaItem {
        val extras = Bundle().apply {
            putInt(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
            )
            putInt(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
            )
        }
        return MediaItem.Builder()
            .setMediaId(ROOT_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("پخش‌کننده آوا")
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    fun buildCategoryItems(): List<MediaItem> = listOf(
        browsableFolder(
            id = NODE_ALL_SONGS,
            title = "همه آهنگ‌ها",
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
        ),
        browsableFolder(
            id = NODE_FAVORITES,
            title = "علاقه‌مندی‌ها",
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
        ),
        browsableFolder(
            id = NODE_TRENDING,
            title = "ترند اینستا",
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
        )
    )

    private fun browsableFolder(
        id: String,
        title: String,
        mediaType: Int
    ): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(mediaType)
                    .build()
            )
            .build()
    }

    fun toPlayableMediaItem(track: MusicTrack): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album.ifBlank { "آوا موزیک" })
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        if (track.artworkUri.isNotBlank()) {
            metadataBuilder.setArtworkUri(Uri.parse(track.artworkUri))
        }
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(Uri.parse(track.mediaUri))
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    fun filterTracks(tracks: List<MusicTrack>, query: String): List<MusicTrack> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return tracks
        return tracks.filter { track ->
            track.title.lowercase().contains(q) ||
                track.artist.lowercase().contains(q) ||
                track.album.lowercase().contains(q)
        }
    }

    fun <T> paginate(items: List<T>, page: Int, pageSize: Int): List<T> {
        if (items.isEmpty() || page < 0 || pageSize <= 0) return emptyList()
        val fromIndex = page * pageSize
        if (fromIndex >= items.size) return emptyList()
        val toIndex = (fromIndex + pageSize).coerceAtMost(items.size)
        return items.subList(fromIndex, toIndex)
    }
}
