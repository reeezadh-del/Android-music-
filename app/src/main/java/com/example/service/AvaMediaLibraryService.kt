package com.example.service

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.example.MainActivity
import com.example.R
import com.example.audio.EqualizerController
import com.example.data.local.PlaybackPreferences
import com.example.data.model.RepeatModeOption
import com.example.data.model.Track
import com.example.data.repository.MusicRepository
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class AvaMediaLibraryService : MediaLibraryService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var player: ExoPlayer
    private lateinit var mediaLibrarySession: MediaLibrarySession
    private lateinit var repository: MusicRepository
    private lateinit var preferences: PlaybackPreferences

    private var sleepTimerJob: Job? = null
    private var positionPersistJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        repository = MusicRepository(this)
        preferences = PlaybackPreferences(this)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                EqualizerController.attachToAudioSession(audioSessionId)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val mediaId = mediaItem?.mediaId ?: return
                serviceScope.launch {
                    repository.markPlayed(mediaId)
                    preferences.saveLastTrack(mediaId, player.currentPosition)
                    updateSessionMediaButtonPreferences()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    if (player.audioSessionId > 0) {
                        EqualizerController.attachToAudioSession(player.audioSessionId)
                    }
                    startPositionSaver()
                } else {
                    positionPersistJob?.cancel()
                    saveCurrentPlaybackPosition()
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                serviceScope.launch {
                    preferences.setShuffleEnabled(shuffleModeEnabled)
                    updateSessionMediaButtonPreferences()
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                val option = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> RepeatModeOption.ONE
                    Player.REPEAT_MODE_ALL -> RepeatModeOption.ALL
                    else -> RepeatModeOption.OFF
                }
                serviceScope.launch {
                    preferences.setRepeatMode(option)
                }
            }
        })

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, AvaLibraryCallback())
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        serviceScope.launch {
            repository.ensureSeeded()
            val stored = preferences.getSnapshot()
            EqualizerController.setEnabled(stored.equalizerEnabled)
            EqualizerController.applyPreset(stored.equalizerPreset)
            player.shuffleModeEnabled = stored.shuffleEnabled
            player.repeatMode = when (stored.repeatMode) {
                RepeatModeOption.ONE -> Player.REPEAT_MODE_ONE
                RepeatModeOption.ALL -> Player.REPEAT_MODE_ALL
                RepeatModeOption.OFF -> Player.REPEAT_MODE_OFF
            }
            if (player.mediaItemCount == 0) {
                val tracks = repository.getAllTracksOnce()
                if (tracks.isNotEmpty()) {
                    val startIndex = tracks.indexOfFirst { it.id == stored.lastTrackId }
                        .takeIf { it >= 0 } ?: 0
                    val items = tracks.map { repository.toMediaItem(it) }
                    player.setMediaItems(items, startIndex, stored.lastPositionMs)
                    player.prepare()
                }
            }
            updateSessionMediaButtonPreferences()
        }

        // Notify Android Auto / connected browsers when library changes
        serviceScope.launch {
            repository.tracksFlow.drop(1).collect {
                mediaLibrarySession.notifyChildrenChanged(TAB_ALL_SONGS, Int.MAX_VALUE, null)
                mediaLibrarySession.notifyChildrenChanged(TAB_ALBUMS, Int.MAX_VALUE, null)
                mediaLibrarySession.notifyChildrenChanged(TAB_FAVORITES, Int.MAX_VALUE, null)
                updateSessionMediaButtonPreferences()
            }
        }
        serviceScope.launch {
            repository.playlistsFlow.drop(1).collect {
                mediaLibrarySession.notifyChildrenChanged(TAB_PLAYLISTS, Int.MAX_VALUE, null)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveCurrentPlaybackPosition()
        sleepTimerJob?.cancel()
        positionPersistJob?.cancel()
        EqualizerController.release()
        mediaLibrarySession.release()
        player.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startPositionSaver() {
        positionPersistJob?.cancel()
        positionPersistJob = serviceScope.launch {
            while (true) {
                delay(4_000L)
                saveCurrentPlaybackPosition()
            }
        }
    }

    private fun saveCurrentPlaybackPosition() {
        val currentId = player.currentMediaItem?.mediaId ?: return
        val pos = player.currentPosition.coerceAtLeast(0L)
        serviceScope.launch {
            preferences.saveLastTrack(currentId, pos)
        }
    }

    private suspend fun updateSessionMediaButtonPreferences() {
        val currentId = player.currentMediaItem?.mediaId
        val isFavorite = currentId?.let { repository.getTrackById(it)?.isFavorite } ?: false
        val favButton = CommandButton.Builder(
            if (isFavorite) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED
        )
            .setDisplayName(getString(R.string.auto_action_favorite))
            .setSessionCommand(SessionCommand(COMMAND_TOGGLE_FAVORITE, Bundle.EMPTY))
            .build()

        val shuffleButton = CommandButton.Builder(
            if (player.shuffleModeEnabled) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF
        )
            .setDisplayName(getString(R.string.auto_action_shuffle))
            .setSessionCommand(SessionCommand(COMMAND_TOGGLE_SHUFFLE, Bundle.EMPTY))
            .build()

        mediaLibrarySession.setMediaButtonPreferences(listOf(favButton, shuffleButton))
    }

    private fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerRemainingMinutes.value = 0
            return
        }
        _sleepTimerRemainingMinutes.value = minutes
        sleepTimerJob = serviceScope.launch {
            var remaining = minutes
            while (remaining > 0) {
                delay(60_000L)
                remaining -= 1
                _sleepTimerRemainingMinutes.value = remaining
            }
            player.pause()
        }
    }

    private inner class AvaLibraryCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ConnectionResult {
            val sessionCommands = ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand(COMMAND_TOGGLE_FAVORITE, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_TOGGLE_SHUFFLE, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_SET_SLEEP_TIMER, Bundle.EMPTY))
                .build()

            return if (session.isMediaNotificationController(controller) ||
                session.isAutoCompanionController(controller)
            ) {
                ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands)
                    .build()
            } else {
                ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands)
                    .build()
            }
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            return when (customCommand.customAction) {
                COMMAND_TOGGLE_FAVORITE -> serviceScope.future {
                    val targetId = args.getString(EXTRA_TRACK_ID)
                        ?: player.currentMediaItem?.mediaId
                    if (targetId != null) {
                        repository.toggleFavorite(targetId)
                        updateSessionMediaButtonPreferences()
                    }
                    SessionResult(SessionResult.RESULT_SUCCESS)
                }

                COMMAND_TOGGLE_SHUFFLE -> {
                    player.shuffleModeEnabled = !player.shuffleModeEnabled
                    serviceScope.launch { updateSessionMediaButtonPreferences() }
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                COMMAND_SET_SLEEP_TIMER -> {
                    val minutes = args.getInt(EXTRA_SLEEP_MINUTES, 0)
                    startSleepTimer(minutes)
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                else -> super.onCustomCommand(session, controller, customCommand, args)
            }
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootExtras = Bundle().apply {
                putBoolean(CONTENT_STYLE_SUPPORTED, true)
                putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_LIST_ITEM)
                putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST_ITEM)
            }
            val rootItem = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(getString(R.string.app_name))
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .build()
                )
                .build()

            val libraryParams = LibraryParams.Builder()
                .setExtras(rootExtras)
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, libraryParams))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = serviceScope.future {
            val rootLimit = params?.extras?.getInt(ROOT_CHILDREN_LIMIT_KEY, 4) ?: 4
            val items: List<MediaItem> = when {
                parentId == ROOT_ID -> buildRootTabs().take(rootLimit.coerceAtLeast(1))
                parentId == TAB_ALL_SONGS -> repository.getAllTracksOnce().map { repository.toMediaItem(it) }
                parentId == TAB_FAVORITES -> repository.getFavoriteTracksOnce().map { repository.toMediaItem(it) }
                parentId == TAB_ALBUMS -> {
                    val albums = MusicRepository.buildAlbumSummaries(repository.getAllTracksOnce())
                    albums.map { album ->
                        browsableFolderItem(
                            mediaId = "$ALBUM_PREFIX${album.name}",
                            title = album.name,
                            subtitle = "${album.artist} • ${album.trackCount} آهنگ",
                            artworkUri = Uri.parse(album.artworkUri),
                            mediaType = MediaMetadata.MEDIA_TYPE_ALBUM
                        )
                    }
                }
                parentId.startsWith(ALBUM_PREFIX) -> {
                    val albumName = parentId.removePrefix(ALBUM_PREFIX)
                    repository.getAllTracksOnce()
                        .filter { it.album == albumName }
                        .map { repository.toMediaItem(it) }
                }
                parentId == TAB_PLAYLISTS -> {
                    repository.getPlaylistsOnce().map { playlist ->
                        browsableFolderItem(
                            mediaId = "$PLAYLIST_PREFIX${playlist.id}",
                            title = playlist.name,
                            subtitle = "${playlist.trackIds.size} آهنگ",
                            artworkUri = Uri.parse(playlist.artworkUri),
                            mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST
                        )
                    }
                }
                parentId.startsWith(PLAYLIST_PREFIX) -> {
                    val playlistId = parentId.removePrefix(PLAYLIST_PREFIX)
                    val playlist = repository.getPlaylistsOnce().firstOrNull { it.id == playlistId }
                    val trackMap = repository.getAllTracksOnce().associateBy { it.id }
                    playlist?.trackIds?.mapNotNull { trackMap[it] }
                        ?.map { repository.toMediaItem(it) }
                        ?: emptyList()
                }
                else -> emptyList()
            }
            LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> = serviceScope.future {
            val track = repository.getTrackById(mediaId)
            if (track != null) {
                LibraryResult.ofItem(repository.toMediaItem(track), null)
            } else {
                LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            }
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> = serviceScope.future {
            val matches = searchTracks(query)
            session.notifySearchResultChanged(browser, query, matches.size, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = serviceScope.future {
            val matches = searchTracks(query).map { repository.toMediaItem(it) }
            LibraryResult.ofItemList(ImmutableList.copyOf(matches), params)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> = serviceScope.future {
            val resolved = resolveMediaItems(mediaItems)
            resolved.toMutableList()
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaItemsWithStartPosition> = serviceScope.future {
            if (mediaItems.size == 1 && mediaItems[0].localConfiguration == null) {
                val targetId = mediaItems[0].mediaId
                val allTracks = repository.getAllTracksOnce()
                when {
                    targetId.startsWith(ALBUM_PREFIX) -> {
                        val albumName = targetId.removePrefix(ALBUM_PREFIX)
                        val items = allTracks.filter { it.album == albumName }
                            .map { repository.toMediaItem(it) }
                        return@future MediaItemsWithStartPosition(items, 0, 0L)
                    }
                    targetId.startsWith(PLAYLIST_PREFIX) -> {
                        val playlistId = targetId.removePrefix(PLAYLIST_PREFIX)
                        val playlist = repository.getPlaylistsOnce().firstOrNull { it.id == playlistId }
                        val map = allTracks.associateBy { it.id }
                        val items = playlist?.trackIds?.mapNotNull { map[it] }
                            ?.map { repository.toMediaItem(it) } ?: emptyList()
                        return@future MediaItemsWithStartPosition(items, 0, 0L)
                    }
                    else -> {
                        val clickedIndex = allTracks.indexOfFirst { it.id == targetId }
                        if (clickedIndex >= 0) {
                            val items = allTracks.map { repository.toMediaItem(it) }
                            return@future MediaItemsWithStartPosition(items, clickedIndex, 0L)
                        }
                    }
                }
            }
            val resolved = resolveMediaItems(mediaItems)
            MediaItemsWithStartPosition(
                resolved,
                startIndex.coerceIn(0, (resolved.size - 1).coerceAtLeast(0)),
                startPositionMs
            )
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean
        ): ListenableFuture<MediaItemsWithStartPosition> = serviceScope.future {
            val allTracks = repository.getAllTracksOnce()
            val stored = preferences.getSnapshot()
            val items = allTracks.map { repository.toMediaItem(it) }
            val idx = allTracks.indexOfFirst { it.id == stored.lastTrackId }.takeIf { it >= 0 } ?: 0
            MediaItemsWithStartPosition(items, idx, stored.lastPositionMs)
        }

        private suspend fun searchTracks(query: String): List<Track> {
            val q = query.trim().lowercase()
            val all = repository.getAllTracksOnce()
            if (q.isEmpty()) return all
            return all.filter {
                it.title.lowercase().contains(q) ||
                    it.artist.lowercase().contains(q) ||
                    it.album.lowercase().contains(q) ||
                    it.genre.lowercase().contains(q)
            }
        }

        private suspend fun resolveMediaItems(requested: List<MediaItem>): List<MediaItem> {
            val allMap = repository.getAllTracksOnce().associateBy { it.id }
            return requested.map { item ->
                if (item.localConfiguration != null) {
                    item
                } else {
                    allMap[item.mediaId]?.let { repository.toMediaItem(it) } ?: item
                }
            }
        }

        private fun buildRootTabs(): List<MediaItem> {
            return listOf(
                browsableTabItem(
                    mediaId = TAB_ALL_SONGS,
                    title = getString(R.string.auto_tab_all_songs),
                    iconRes = R.drawable.ic_auto_music,
                    playableStyle = CONTENT_STYLE_LIST_ITEM
                ),
                browsableTabItem(
                    mediaId = TAB_ALBUMS,
                    title = getString(R.string.auto_tab_albums),
                    iconRes = R.drawable.ic_auto_album,
                    browsableStyle = CONTENT_STYLE_GRID_ITEM,
                    playableStyle = CONTENT_STYLE_LIST_ITEM
                ),
                browsableTabItem(
                    mediaId = TAB_FAVORITES,
                    title = getString(R.string.auto_tab_favorites),
                    iconRes = R.drawable.ic_auto_favorite,
                    playableStyle = CONTENT_STYLE_LIST_ITEM
                ),
                browsableTabItem(
                    mediaId = TAB_PLAYLISTS,
                    title = getString(R.string.auto_tab_playlists),
                    iconRes = R.drawable.ic_auto_playlist,
                    browsableStyle = CONTENT_STYLE_GRID_ITEM,
                    playableStyle = CONTENT_STYLE_LIST_ITEM
                )
            )
        }

        private fun browsableTabItem(
            mediaId: String,
            title: String,
            iconRes: Int,
            browsableStyle: Int = CONTENT_STYLE_LIST_ITEM,
            playableStyle: Int = CONTENT_STYLE_LIST_ITEM
        ): MediaItem {
            val extras = Bundle().apply {
                putInt(CONTENT_STYLE_BROWSABLE_HINT, browsableStyle)
                putInt(CONTENT_STYLE_PLAYABLE_HINT, playableStyle)
            }
            return MediaItem.Builder()
                .setMediaId(mediaId)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtworkUri(Uri.parse(repository.drawableUri(iconRes)))
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .setExtras(extras)
                        .build()
                )
                .build()
        }

        private fun browsableFolderItem(
            mediaId: String,
            title: String,
            subtitle: String,
            artworkUri: Uri,
            mediaType: Int
        ): MediaItem {
            return MediaItem.Builder()
                .setMediaId(mediaId)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setArtworkUri(artworkUri)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(mediaType)
                        .build()
                )
                .build()
        }
    }

    companion object {
        const val ROOT_ID = "ava_root"
        const val TAB_ALL_SONGS = "ava_tab_all_songs"
        const val TAB_ALBUMS = "ava_tab_albums"
        const val TAB_FAVORITES = "ava_tab_favorites"
        const val TAB_PLAYLISTS = "ava_tab_playlists"
        const val ALBUM_PREFIX = "album:"
        const val PLAYLIST_PREFIX = "playlist:"

        const val COMMAND_TOGGLE_FAVORITE = "com.aistudio.avaplayer.TOGGLE_FAVORITE"
        const val COMMAND_TOGGLE_SHUFFLE = "com.aistudio.avaplayer.TOGGLE_SHUFFLE"
        const val COMMAND_SET_SLEEP_TIMER = "com.aistudio.avaplayer.SET_SLEEP_TIMER"
        const val EXTRA_TRACK_ID = "extra_track_id"
        const val EXTRA_SLEEP_MINUTES = "extra_sleep_minutes"

        private const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
        private const val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        private const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        private const val CONTENT_STYLE_LIST_ITEM = 1
        private const val CONTENT_STYLE_GRID_ITEM = 2
        private const val ROOT_CHILDREN_LIMIT_KEY = "androidx.media.MediaBrowserCompat.Extras.KEY_ROOT_CHILDREN_LIMIT"

        private val _sleepTimerRemainingMinutes = MutableStateFlow(0)
        val sleepTimerRemainingMinutes: StateFlow<Int> = _sleepTimerRemainingMinutes.asStateFlow()
    }
}
