package com.example.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.example.AvaPlayerApplication
import com.example.R
import com.example.audio.EqualizerController
import com.example.data.model.AlbumSummary
import com.example.data.model.AutoBrowseNodePreview
import com.example.data.model.EqualizerPreset
import com.example.data.model.EqualizerUiState
import com.example.data.model.Playlist
import com.example.data.model.RepeatModeOption
import com.example.data.model.Track
import com.example.data.remote.IranianMusicSiteFilter
import com.example.data.remote.OnlineMusicRepository
import com.example.data.remote.OnlineSourceRegion
import com.example.data.remote.OnlineTrackGenre
import com.example.data.repository.MusicRepository
import com.example.service.AvaMediaLibraryService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryFilter(val labelFa: String) {
    ALL("همه آهنگ‌ها"),
    FAVORITES("علاقه‌مندی‌ها"),
    IMPORTED("فایل‌های دستگاه")
}

sealed interface OnlineFeedUiState {
    data object Loading : OnlineFeedUiState
    data class Success(val tracks: List<Track>) : OnlineFeedUiState
    data class Error(val message: String) : OnlineFeedUiState
}

data class PlaybackUiState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatModeOption = RepeatModeOption.ALL,
    val queueTracks: List<Track> = emptyList(),
    val currentQueueIndex: Int = 0,
    val isServiceConnected: Boolean = false
)

class AvaPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AvaPlayerApplication
    private val repository = app.musicRepository
    private val preferences = app.playbackPreferences
    private val onlineRepository = OnlineMusicRepository()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressJob: Job? = null

    val allTracks: StateFlow<List<Track>> = repository.tracksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteTracks: StateFlow<List<Track>> = repository.favoriteTracksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentTracks: StateFlow<List<Track>> = repository.recentTracksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val albums: StateFlow<List<AlbumSummary>> = repository.albumsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists: StateFlow<List<Playlist>> = repository.playlistsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val equalizerState: StateFlow<EqualizerUiState> = EqualizerController.state

    val sleepTimerRemainingMinutes: StateFlow<Int> = AvaMediaLibraryService.sleepTimerRemainingMinutes

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _libraryFilter = MutableStateFlow(LibraryFilter.ALL)
    val libraryFilter: StateFlow<LibraryFilter> = _libraryFilter

    val filteredTracks: StateFlow<List<Track>> = combine(
        allTracks,
        _searchQuery,
        _libraryFilter
    ) { tracks, query, filter ->
        val base = when (filter) {
            LibraryFilter.ALL -> tracks
            LibraryFilter.FAVORITES -> tracks.filter { it.isFavorite }
            LibraryFilter.IMPORTED -> tracks.filter { it.isImported }
        }
        val q = query.trim().lowercase()
        if (q.isEmpty()) base
        else base.filter {
            it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q) ||
                it.genre.lowercase().contains(q)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val autoBrowseTreePreview: StateFlow<List<AutoBrowseNodePreview>> = combine(
        allTracks,
        albums,
        favoriteTracks,
        playlists
    ) { tracks, albumList, favorites, playlistList ->
        listOf(
            AutoBrowseNodePreview(
                id = AvaMediaLibraryService.TAB_ALL_SONGS,
                title = app.getString(R.string.auto_tab_all_songs),
                subtitle = "${tracks.size} قطعه آماده پخش",
                itemCount = tracks.size,
                iconRes = R.drawable.ic_auto_music
            ),
            AutoBrowseNodePreview(
                id = AvaMediaLibraryService.TAB_ALBUMS,
                title = app.getString(R.string.auto_tab_albums),
                subtitle = "${albumList.size} آلبوم (نمایش شبکه‌ای)",
                itemCount = albumList.size,
                iconRes = R.drawable.ic_auto_album
            ),
            AutoBrowseNodePreview(
                id = AvaMediaLibraryService.TAB_FAVORITES,
                title = app.getString(R.string.auto_tab_favorites),
                subtitle = "${favorites.size} آهنگ محبوب",
                itemCount = favorites.size,
                iconRes = R.drawable.ic_auto_favorite
            ),
            AutoBrowseNodePreview(
                id = AvaMediaLibraryService.TAB_PLAYLISTS,
                title = app.getString(R.string.auto_tab_playlists),
                subtitle = "${playlistList.size} پلی‌لیست خودرو",
                itemCount = playlistList.size,
                iconRes = R.drawable.ic_auto_playlist
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _playbackState = MutableStateFlow(PlaybackUiState())
    val playbackState: StateFlow<PlaybackUiState> = _playbackState

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage

    private val _onlineRegion = MutableStateFlow(OnlineSourceRegion.IRANIAN_SITES)
    val onlineRegion: StateFlow<OnlineSourceRegion> = _onlineRegion

    private val _iranianSiteFilter = MutableStateFlow(IranianMusicSiteFilter.ALL)
    val iranianSiteFilter: StateFlow<IranianMusicSiteFilter> = _iranianSiteFilter

    private val _onlineGenre = MutableStateFlow(OnlineTrackGenre.TRENDING)
    val onlineGenre: StateFlow<OnlineTrackGenre> = _onlineGenre

    private val _onlineSearchQuery = MutableStateFlow("")
    val onlineSearchQuery: StateFlow<String> = _onlineSearchQuery

    private val _onlineFeedState = MutableStateFlow<OnlineFeedUiState>(OnlineFeedUiState.Loading)
    val onlineFeedState: StateFlow<OnlineFeedUiState> = _onlineFeedState

    private val _isScanningDeviceMusic = MutableStateFlow(false)
    val isScanningDeviceMusic: StateFlow<Boolean> = _isScanningDeviceMusic

    private var onlineFetchJob: Job? = null

    init {
        connectToMediaService()
        loadOnlineFeed()
        viewModelScope.launch {
            allTracks.collect { tracks ->
                syncPlayerMetadataWithTracks(tracks)
            }
        }
    }

    private fun connectToMediaService() {
        val sessionToken = SessionToken(
            app,
            ComponentName(app, AvaMediaLibraryService::class.java)
        )
        val future = MediaController.Builder(app, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching { future.get() }.onSuccess { controller ->
                    mediaController = controller
                    attachPlayerListener(controller)
                    syncFromController(controller)
                    startProgressTicker()
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun attachPlayerListener(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                syncFromController(controller)
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                syncFromController(controller)
            }
        })
    }

    private fun syncFromController(controller: MediaController) {
        val tracksById = allTracks.value.associateBy { it.id }
        val queue = (0 until controller.mediaItemCount).mapNotNull { idx ->
            val item = controller.getMediaItemAt(idx)
            tracksById[item.mediaId] ?: item.toFallbackTrack()
        }
        val currentIdx = controller.currentMediaItemIndex.coerceAtLeast(0)
        val currentId = controller.currentMediaItem?.mediaId
        val activeTrack = currentId?.let { tracksById[it] }
            ?: controller.currentMediaItem?.toFallbackTrack()

        val duration = controller.duration.takeIf { it > 0L }
            ?: activeTrack?.durationMs ?: 0L

        _playbackState.value = PlaybackUiState(
            currentTrack = activeTrack,
            isPlaying = controller.isPlaying,
            currentPositionMs = controller.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            shuffleEnabled = controller.shuffleModeEnabled,
            repeatMode = when (controller.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatModeOption.ONE
                Player.REPEAT_MODE_ALL -> RepeatModeOption.ALL
                else -> RepeatModeOption.OFF
            },
            queueTracks = queue,
            currentQueueIndex = currentIdx,
            isServiceConnected = true
        )
    }

    private fun syncPlayerMetadataWithTracks(tracks: List<Track>) {
        val controller = mediaController ?: return
        if (tracks.isEmpty()) return
        syncFromController(controller)
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    val dur = controller.duration.takeIf { it > 0L }
                        ?: _playbackState.value.durationMs
                    _playbackState.value = _playbackState.value.copy(
                        currentPositionMs = pos,
                        durationMs = dur,
                        isPlaying = true
                    )
                }
                delay(350L)
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setLibraryFilter(filter: LibraryFilter) {
        _libraryFilter.value = filter
    }

    fun playTrackInList(track: Track, list: List<Track>) {
        val controller = mediaController ?: return
        val items = list.ifEmpty { listOf(track) }.map { repository.toMediaItem(it) }
        val startIndex = list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        controller.setMediaItems(items, startIndex, 0L)
        controller.prepare()
        controller.play()
    }

    fun playQueueItemAt(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until controller.mediaItemCount) {
            controller.seekToDefaultPosition(index)
            controller.play()
        }
    }

    fun playDrivingMix() {
        val drivingPlaylist = playlists.value.firstOrNull {
            it.id == MusicRepository.PLAYLIST_DRIVING_MIX_ID
        }
        val tracksMap = allTracks.value.associateBy { it.id }
        val mixTracks = drivingPlaylist?.trackIds?.mapNotNull { tracksMap[it] }
            ?.takeIf { it.isNotEmpty() }
            ?: allTracks.value
        if (mixTracks.isNotEmpty()) {
            playTrackInList(mixTracks.first(), mixTracks)
        } else {
            showBanner("ابتدا دکمه «اسکن موزیک‌های دستگاه» را بزنید تا آهنگ‌ها اضافه شوند")
        }
    }

    fun playAlbum(album: AlbumSummary) {
        if (album.tracks.isNotEmpty()) {
            playTrackInList(album.tracks.first(), album.tracks)
        }
    }

    fun playPlaylist(playlist: Playlist) {
        val tracksMap = allTracks.value.associateBy { it.id }
        val tracks = playlist.trackIds.mapNotNull { tracksMap[it] }
        if (tracks.isNotEmpty()) {
            playTrackInList(tracks.first(), tracks)
        } else {
            showBanner("این پلی‌لیست هنوز آهنگی ندارد")
        }
    }

    fun addTrackToQueueNext(track: Track) {
        val controller = mediaController ?: return
        val mediaItem = repository.toMediaItem(track)
        val insertIndex = (controller.currentMediaItemIndex + 1)
            .coerceAtMost(controller.mediaItemCount)
        controller.addMediaItem(insertIndex, mediaItem)
        showBanner("«${track.title}» به صف پخش بعدی اضافه شد")
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.mediaItemCount == 0 && allTracks.value.isNotEmpty()) {
                playTrackInList(allTracks.value.first(), allTracks.value)
            } else {
                controller.prepare()
                controller.play()
            }
        }
    }

    fun skipNext() {
        val controller = mediaController ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
            controller.play()
        } else if (controller.mediaItemCount > 0) {
            controller.seekToDefaultPosition(0)
            controller.play()
        }
    }

    fun skipPrevious() {
        val controller = mediaController ?: return
        if (controller.currentPosition > 3_500L) {
            controller.seekTo(0L)
        } else if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
            controller.play()
        } else {
            controller.seekTo(0L)
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        controller.seekTo(positionMs.coerceAtLeast(0L))
        _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
    }

    fun cycleRepeatMode() {
        val controller = mediaController ?: return
        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggleFavorite(trackId: String) {
        viewModelScope.launch {
            val isFav = repository.toggleFavorite(trackId)
            val msg = if (isFav) "به علاقه‌مندی‌ها اضافه شد" else "از علاقه‌مندی‌ها حذف شد"
            showBanner(msg)
        }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createPlaylist(name)
            showBanner("پلی‌لیست «$name» ساخته شد")
        }
    }

    fun addTrackToPlaylist(playlistId: String, trackId: String, playlistName: String) {
        viewModelScope.launch {
            repository.addTrackToPlaylist(playlistId, trackId)
            showBanner("به پلی‌لیست «$playlistName» اضافه شد")
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            showBanner("پلی‌لیست حذف شد")
        }
    }

    fun scanDeviceMusic(showBannerOnResult: Boolean = true) {
        if (_isScanningDeviceMusic.value) return
        viewModelScope.launch {
            _isScanningDeviceMusic.value = true
            try {
                val count = repository.scanDeviceAudio()
                if (count > 0) {
                    val controller = mediaController
                    if (controller != null && controller.mediaItemCount == 0) {
                        val tracks = repository.getAllTracksOnce()
                        if (tracks.isNotEmpty()) {
                            controller.setMediaItems(tracks.map { repository.toMediaItem(it) }, 0, 0L)
                            controller.prepare()
                        }
                    }
                    if (showBannerOnResult) {
                        showBanner("$count آهنگ از حافظه دستگاه به لیست پخش اضافه شد")
                    }
                } else if (count == 0 && showBannerOnResult) {
                    showBanner("آهنگی در حافظه دستگاه پیدا نشد؛ می‌توانید دستی فایل صوتی اضافه کنید")
                }
            } finally {
                _isScanningDeviceMusic.value = false
            }
        }
    }

    fun importAudioUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val count = repository.importAudioUris(uris)
            if (count > 0) {
                val controller = mediaController
                if (controller != null && controller.mediaItemCount == 0) {
                    val tracks = repository.getAllTracksOnce()
                    if (tracks.isNotEmpty()) {
                        controller.setMediaItems(tracks.map { repository.toMediaItem(it) }, 0, 0L)
                        controller.prepare()
                    }
                }
                showBanner("$count آهنگ به لیست پخش و اندروید اتو اضافه شد")
            }
        }
    }

    fun deleteImportedTrack(trackId: String) {
        viewModelScope.launch {
            repository.deleteImportedTrack(trackId)
            showBanner("آهنگ از کتابخانه حذف شد")
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        EqualizerController.setEnabled(enabled)
        persistEqualizerState()
    }

    fun selectEqualizerPreset(preset: EqualizerPreset) {
        EqualizerController.applyPreset(preset)
        persistEqualizerState()
    }

    fun setEqualizerBand(bandIndex: Int, levelMb: Int) {
        EqualizerController.setBandLevel(bandIndex, levelMb)
        persistEqualizerState()
    }

    fun setPreampDb(db: Float) {
        EqualizerController.setPreampDb(db)
        persistEqualizerState()
    }

    fun setBassBoostPercent(percent: Int) {
        EqualizerController.setBassBoostPercent(percent)
        persistEqualizerState()
    }

    fun resetEqualizer() {
        EqualizerController.resetToFlat()
        persistEqualizerState()
        showBanner("اکولایزر به حالت طبیعی بازنشانی شد")
    }

    fun setMedia3PlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.75f, 1.5f)
        mediaController?.setPlaybackSpeed(clamped)
        EqualizerController.updateMedia3Settings(
            speed = clamped,
            skipSilence = equalizerState.value.skipSilenceEnabled
        )
        viewModelScope.launch {
            preferences.setMedia3PlaybackSettings(clamped, equalizerState.value.skipSilenceEnabled)
        }
    }

    fun setMedia3SkipSilence(enabled: Boolean) {
        val controller = mediaController
        if (controller != null) {
            val args = Bundle().apply {
                putBoolean(AvaMediaLibraryService.EXTRA_SKIP_SILENCE, enabled)
            }
            controller.sendCustomCommand(
                SessionCommand(AvaMediaLibraryService.COMMAND_SET_SKIP_SILENCE, Bundle.EMPTY),
                args
            )
        }
        EqualizerController.updateMedia3Settings(
            speed = equalizerState.value.playbackSpeed,
            skipSilence = enabled
        )
        viewModelScope.launch {
            preferences.setMedia3PlaybackSettings(equalizerState.value.playbackSpeed, enabled)
        }
    }

    private fun persistEqualizerState() {
        val snapshot = equalizerState.value
        viewModelScope.launch {
            preferences.setEqualizerState(
                enabled = snapshot.isEnabled,
                preset = snapshot.selectedPreset,
                bandLevelsMb = snapshot.bands.map { it.levelMb },
                preampDb = snapshot.preampDb,
                bassBoostPercent = snapshot.bassBoostPercent
            )
        }
    }

    fun setSleepTimer(minutes: Int) {
        val controller = mediaController ?: return
        val args = Bundle().apply {
            putInt(AvaMediaLibraryService.EXTRA_SLEEP_MINUTES, minutes)
        }
        controller.sendCustomCommand(
            SessionCommand(AvaMediaLibraryService.COMMAND_SET_SLEEP_TIMER, Bundle.EMPTY),
            args
        )
        if (minutes > 0) {
            showBanner("تایمر خواب برای $minutes دقیقه تنظیم شد")
        } else {
            showBanner("تایمر خواب غیرفعال شد")
        }
    }

    fun clearBanner() {
        _statusBannerMessage.value = null
    }

    fun selectOnlineRegion(region: OnlineSourceRegion) {
        if (_onlineRegion.value == region) return
        _onlineRegion.value = region
        loadOnlineFeed()
    }

    fun selectIranianSiteFilter(siteFilter: IranianMusicSiteFilter) {
        _iranianSiteFilter.value = siteFilter
        loadOnlineFeed()
    }

    fun selectOnlineGenre(genre: OnlineTrackGenre) {
        _onlineGenre.value = genre
        _onlineSearchQuery.value = ""
        loadOnlineFeed()
    }

    fun setOnlineSearchQuery(query: String) {
        _onlineSearchQuery.value = query
    }

    fun loadOnlineFeed() {
        onlineFetchJob?.cancel()
        onlineFetchJob = viewModelScope.launch {
            _onlineFeedState.value = OnlineFeedUiState.Loading
            val query = _onlineSearchQuery.value.trim()
            val result = when (_onlineRegion.value) {
                OnlineSourceRegion.IRANIAN_SITES -> {
                    onlineRepository.fetchIranianSiteTracks(_iranianSiteFilter.value, query)
                }
                OnlineSourceRegion.GLOBAL_WEB -> {
                    if (query.isNotEmpty()) {
                        onlineRepository.searchWebTracks(query)
                    } else {
                        onlineRepository.fetchTrendingTracks(_onlineGenre.value)
                    }
                }
            }
            _onlineFeedState.value = result.fold(
                onSuccess = { OnlineFeedUiState.Success(it) },
                onFailure = { err ->
                    OnlineFeedUiState.Error(
                        err.localizedMessage?.takeIf { it.isNotBlank() }
                            ?: "اتصال اینترنت یا دسترسی به سایت‌های موسیقی را بررسی کنید."
                    )
                }
            )
        }
    }

    fun saveOnlineTrackToLibrary(track: Track) {
        viewModelScope.launch {
            repository.saveOnlineTrack(track)
            showBanner("«${track.title}» در کتابخانه و اندروید اتو ذخیره شد")
        }
    }

    private fun showBanner(message: String) {
        _statusBannerMessage.value = message
    }

    private fun MediaItem.toFallbackTrack(): Track {
        val meta = mediaMetadata
        return Track(
            id = mediaId,
            title = meta.title?.toString() ?: "در حال پخش",
            artist = meta.artist?.toString() ?: "melodi",
            album = meta.albumTitle?.toString() ?: "",
            durationMs = meta.extras?.getLong(MusicRepository.EXTRA_DURATION_MS) ?: 0L,
            mediaUri = localConfiguration?.uri?.toString() ?: "",
            artworkUri = meta.artworkUri?.toString() ?: "",
            isFavorite = meta.extras?.getBoolean(MusicRepository.EXTRA_IS_FAVORITE) ?: false
        )
    }

    override fun onCleared() {
        progressJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        super.onCleared()
    }
}
