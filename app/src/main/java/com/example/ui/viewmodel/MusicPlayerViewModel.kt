package com.example.ui.viewmodel

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.auth.UserAccount
import com.example.auth.UserAccountRepository
import com.example.diagnostics.CrashReporter
import com.example.drive.DriveDbSnapshot
import com.example.drive.GoogleDriveDatabaseManager
import com.example.player.AppTab
import com.example.player.MusicLibraryRepository
import com.example.player.MusicPlaybackService
import com.example.player.MusicPreferences
import com.example.player.MusicTrack
import com.example.player.RepeatModeOption
import com.example.player.TrackFilter
import com.example.player.TrendingCategory
import com.example.player.TrendingPreset
import com.example.player.TrendingIranianRepository
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlayerUiState(
    val currentTrack: MusicTrack? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatModeOption = RepeatModeOption.OFF
)

class MusicPlayerViewModel(
    private val appContext: Context,
    private val preferences: MusicPreferences,
    private val repository: MusicLibraryRepository,
    private val trendingRepository: TrendingIranianRepository,
    private val userAccountRepository: UserAccountRepository,
    private val driveDbManager: GoogleDriveDatabaseManager,
    val currentUserId: String
) : ViewModel() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressJob: Job? = null
    private var trendingLoadJob: Job? = null
    private var autoDriveSyncJob: Job? = null

    private var activeDriveToken: String? = null
    private var activeAccount: UserAccount? = null
    private var activeEmail: String = ""
    private var hasCompletedInitialDriveRestore = false

    private val _activeTab = MutableStateFlow(AppTab.LIBRARY)
    val activeTab: StateFlow<AppTab> = _activeTab.asStateFlow()

    private val _allTracks = MutableStateFlow<List<MusicTrack>>(emptyList())
    val allTracks: StateFlow<List<MusicTrack>> = _allTracks.asStateFlow()

    private val _favoriteOnlineTracks = MutableStateFlow<List<MusicTrack>>(emptyList())
    private val _favoriteCount = MutableStateFlow(0)
    val favoriteCount: StateFlow<Int> = _favoriteCount.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeFilter = MutableStateFlow(TrackFilter.ALL)
    val activeFilter: StateFlow<TrackFilter> = _activeFilter.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Trending Iranian discovery states
    private val _trendingTracks = MutableStateFlow<List<MusicTrack>>(emptyList())
    val trendingTracks: StateFlow<List<MusicTrack>> = _trendingTracks.asStateFlow()

    private val _trendingCategory = MutableStateFlow(TrendingCategory.ALL)
    val trendingCategory: StateFlow<TrendingCategory> = _trendingCategory.asStateFlow()

    private val _trendingPreset = MutableStateFlow(TrendingPreset.HOT)
    val trendingPreset: StateFlow<TrendingPreset> = _trendingPreset.asStateFlow()

    private val _trendingSearchQuery = MutableStateFlow("")
    val trendingSearchQuery: StateFlow<String> = _trendingSearchQuery.asStateFlow()

    private val _isLoadingTrending = MutableStateFlow(false)
    val isLoadingTrending: StateFlow<Boolean> = _isLoadingTrending.asStateFlow()

    private val _trendingError = MutableStateFlow<String?>(null)
    val trendingError: StateFlow<String?> = _trendingError.asStateFlow()

    private val _isSyncingDrive = MutableStateFlow(false)
    val isSyncingDrive: StateFlow<Boolean> = _isSyncingDrive.asStateFlow()

    private val _driveSyncMessage = MutableStateFlow<String?>(null)
    val driveSyncMessage: StateFlow<String?> = _driveSyncMessage.asStateFlow()

    private var hasLoadedInitialTrending = false
    private var activeQueueTracks: List<MusicTrack> = emptyList()

    private val _playerState = MutableStateFlow(PlayerUiState())
    val playerState: StateFlow<PlayerUiState> = _playerState.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val filteredTracks: StateFlow<List<MusicTrack>> = combine(
        _allTracks,
        _favoriteOnlineTracks,
        _searchQuery,
        _activeFilter
    ) { localTracks, favOnline, query, filter ->
        val byFilter = when (filter) {
            TrackFilter.ALL -> localTracks
            TrackFilter.FAVORITES -> (localTracks.filter { it.isFavorite } + favOnline)
                .distinctBy { it.id }
        }
        val q = query.trim().lowercase()
        if (q.isEmpty()) byFilter
        else byFilter.filter {
            it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        connectToService()
        viewModelScope.launch {
            preferences.prefsFlow.collect { prefs ->
                _favoriteOnlineTracks.value = prefs.favoriteOnlineTracks
                _favoriteCount.value = prefs.favoriteIds.size
                refreshLibrary(prefs.favoriteIds, prefs.importedUris)
                syncTrendingFavoriteFlags(prefs.favoriteIds)
            }
        }
    }

    fun updateDriveSessionContext(
        accessToken: String?,
        account: UserAccount,
        email: String
    ) {
        activeAccount = account
        activeEmail = email
        if (!accessToken.isNullOrBlank()) {
            val shouldRestore = !hasCompletedInitialDriveRestore && activeDriveToken != accessToken
            activeDriveToken = accessToken
            if (shouldRestore) {
                restoreFromGoogleDrive(
                    accessToken = accessToken,
                    account = account,
                    email = email,
                    showToast = true
                )
            }
        }
    }

    fun restoreFromGoogleDrive(
        accessToken: String,
        account: UserAccount,
        email: String,
        showToast: Boolean = true
    ) {
        if (_isSyncingDrive.value) return
        activeDriveToken = accessToken
        activeAccount = account
        activeEmail = email

        viewModelScope.launch {
            _isSyncingDrive.value = true
            val result = driveDbManager.syncFromGoogleDrive(
                accessToken = accessToken,
                knownFileId = account.driveFileId
            )
            result.onSuccess { pair ->
                if (pair != null) {
                    val (fileId, snapshot) = pair
                    preferences.restoreFromDriveSnapshot(
                        favoriteIds = snapshot.favoriteIds,
                        favoriteOnlineTracks = snapshot.favoriteOnlineTracks,
                        shuffleEnabled = snapshot.shuffleEnabled,
                        repeatMode = snapshot.repeatMode,
                        lastTrackId = snapshot.lastTrackId
                    )
                    applyControllerModes(snapshot.shuffleEnabled, snapshot.repeatMode)
                    if (account.driveFileId != fileId) {
                        userAccountRepository.updateDriveFileId(currentUserId, fileId)
                    }
                    hasCompletedInitialDriveRestore = true
                    val msg = "دیتابیس @${account.username} از گوگل درایو بازخوانی شد (${snapshot.favoriteIds.size} علاقه‌مندی)"
                    _driveSyncMessage.value = msg
                    if (showToast) _statusMessage.value = msg
                } else {
                    // No SQLite file on Drive yet: create initial database file on Drive
                    val initialUpload = uploadSnapshotInternal(accessToken, account, email)
                    hasCompletedInitialDriveRestore = true
                    initialUpload.onSuccess {
                        val msg = "دیتابیس اولیه در گوگل درایو ایجاد شد"
                        _driveSyncMessage.value = msg
                        if (showToast) _statusMessage.value = msg
                    }.onFailure { err ->
                        _driveSyncMessage.value = err.localizedMessage ?: "خطا در ایجاد دیتابیس گوگل درایو"
                    }
                }
            }.onFailure { err ->
                _driveSyncMessage.value = err.localizedMessage ?: "بازخوانی از گوگل درایو ناموفق بود"
                if (showToast) {
                    _statusMessage.value = "خطا در بازخوانی از گوگل درایو"
                }
            }
            _isSyncingDrive.value = false
        }
    }

    fun syncCurrentDatabaseToDrive(
        accessToken: String,
        account: UserAccount,
        email: String,
        showToast: Boolean = true
    ) {
        if (_isSyncingDrive.value) return
        activeDriveToken = accessToken
        activeAccount = account
        activeEmail = email

        viewModelScope.launch {
            _isSyncingDrive.value = true
            val uploadResult = uploadSnapshotInternal(accessToken, account, email)
            uploadResult.onSuccess {
                hasCompletedInitialDriveRestore = true
                val msg = "دیتابیس SQLite با موفقیت در گوگل درایو ذخیره شد"
                _driveSyncMessage.value = msg
                if (showToast) _statusMessage.value = msg
            }.onFailure { err ->
                _driveSyncMessage.value = err.localizedMessage ?: "ذخیره در گوگل درایو ناموفق بود"
                if (showToast) _statusMessage.value = "خطا در ذخیره دیتابیس در گوگل درایو"
            }
            _isSyncingDrive.value = false
        }
    }

    private suspend fun uploadSnapshotInternal(
        accessToken: String,
        account: UserAccount,
        email: String
    ): Result<String> {
        val prefs = preferences.prefsFlow.first()
        val snapshot = DriveDbSnapshot(
            uid = currentUserId,
            username = account.username,
            email = email,
            favoriteIds = prefs.favoriteIds,
            favoriteOnlineTracks = prefs.favoriteOnlineTracks,
            shuffleEnabled = prefs.shuffleEnabled,
            repeatMode = prefs.repeatMode,
            lastTrackId = prefs.lastTrackId,
            updatedAtEpochMs = System.currentTimeMillis()
        )
        val result = driveDbManager.uploadToGoogleDrive(
            accessToken = accessToken,
            snapshot = snapshot,
            knownFileId = account.driveFileId
        )
        result.onSuccess { fileId ->
            if (account.driveFileId != fileId) {
                userAccountRepository.updateDriveFileId(currentUserId, fileId)
            }
        }
        return result
    }

    private fun scheduleAutoDriveSync() {
        val token = activeDriveToken ?: return
        val account = activeAccount ?: return
        if (!hasCompletedInitialDriveRestore) return

        autoDriveSyncJob?.cancel()
        autoDriveSyncJob = viewModelScope.launch {
            delay(1_200L)
            uploadSnapshotInternal(token, account, activeEmail).onSuccess {
                _driveSyncMessage.value = "آخرین تغییرات در گوگل درایو ذخیره شد"
            }
        }
    }

    private fun applyControllerModes(shuffleEnabled: Boolean, repeatMode: RepeatModeOption) {
        val controller = mediaController ?: return
        controller.shuffleModeEnabled = shuffleEnabled
        controller.repeatMode = when (repeatMode) {
            RepeatModeOption.OFF -> Player.REPEAT_MODE_OFF
            RepeatModeOption.ALL -> Player.REPEAT_MODE_ALL
            RepeatModeOption.ONE -> Player.REPEAT_MODE_ONE
        }
    }

    private fun syncTrendingFavoriteFlags(favoriteIds: Set<String>) {
        if (_trendingTracks.value.isNotEmpty()) {
            _trendingTracks.value = _trendingTracks.value.map { track ->
                track.copy(isFavorite = track.id in favoriteIds)
            }
        }
        mediaController?.let { syncFromController(it) }
    }

    private fun connectToService() {
        runCatching {
            val token = SessionToken(appContext, ComponentName(appContext, MusicPlaybackService::class.java))
            val future = MediaController.Builder(appContext, token).buildAsync()
            controllerFuture = future
            future.addListener(
                {
                    runCatching { future.get() }.onSuccess { controller ->
                        mediaController = controller
                        viewModelScope.launch {
                            val stored = preferences.prefsFlow.first()
                            applyControllerModes(stored.shuffleEnabled, stored.repeatMode)
                        }
                        controller.addListener(object : Player.Listener {
                            override fun onEvents(player: Player, events: Player.Events) {
                                syncFromController(controller)
                            }

                            override fun onPlayerError(error: PlaybackException) {
                                CrashReporter.recordNonFatal("EXOPLAYER_PLAYBACK", error, context = appContext)
                            }
                        })
                        syncFromController(controller)
                        startProgressTicker()
                    }.onFailure { err ->
                        CrashReporter.recordNonFatal("PLAYER_SERVICE_FUTURE", err, context = appContext)
                    }
                },
                MoreExecutors.directExecutor()
            )
        }.onFailure { err ->
            CrashReporter.recordNonFatal("PLAYER_SERVICE_BIND", err, context = appContext)
        }
    }

    private fun findTrackById(id: String): MusicTrack? {
        return _allTracks.value.firstOrNull { it.id == id }
            ?: _trendingTracks.value.firstOrNull { it.id == id }
            ?: _favoriteOnlineTracks.value.firstOrNull { it.id == id }
            ?: activeQueueTracks.firstOrNull { it.id == id }
    }

    private fun syncFromController(controller: MediaController) {
        val currentId = controller.currentMediaItem?.mediaId
        val matched = currentId?.let { findTrackById(it) }
        val duration = controller.duration.takeIf { it > 0L } ?: matched?.durationMs ?: 0L

        if (currentId != null) {
            viewModelScope.launch { preferences.saveLastTrackId(currentId) }
        }

        _playerState.value = PlayerUiState(
            currentTrack = matched,
            isPlaying = controller.isPlaying,
            positionMs = controller.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            shuffleEnabled = controller.shuffleModeEnabled,
            repeatMode = when (controller.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatModeOption.ALL
                Player.REPEAT_MODE_ONE -> RepeatModeOption.ONE
                else -> RepeatModeOption.OFF
            }
        )
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    val dur = controller.duration.takeIf { it > 0L } ?: _playerState.value.durationMs
                    _playerState.value = _playerState.value.copy(
                        positionMs = controller.currentPosition.coerceAtLeast(0L),
                        durationMs = dur,
                        isPlaying = true
                    )
                }
                delay(350L)
            }
        }
    }

    fun selectTab(tab: AppTab) {
        _activeTab.value = tab
        if (tab == AppTab.TRENDING && !hasLoadedInitialTrending && !_isLoadingTrending.value) {
            loadTrendingTracks(forceRefresh = false)
        }
    }

    fun loadTrendingTracks(forceRefresh: Boolean = true) {
        if (!forceRefresh && hasLoadedInitialTrending && _trendingTracks.value.isNotEmpty()) return
        trendingLoadJob?.cancel()
        trendingLoadJob = viewModelScope.launch {
            _isLoadingTrending.value = true
            _trendingError.value = null
            val favIds = preferences.prefsFlow.first().favoriteIds
            val result = trendingRepository.fetchTrendingTracks(
                preset = _trendingPreset.value,
                category = _trendingCategory.value,
                customQuery = _trendingSearchQuery.value,
                favoriteIds = favIds
            )
            result.onSuccess { list ->
                hasLoadedInitialTrending = true
                _trendingTracks.value = list
                _trendingError.value = null
                mediaController?.let { syncFromController(it) }
            }.onFailure { err ->
                CrashReporter.recordNonFatal("TRENDING_FETCH", err, context = appContext)
                _trendingError.value = "دریافت ترندهای اینستاگرام ممکن نشد. اتصال اینترنت را بررسی کنید."
            }
            _isLoadingTrending.value = false
        }
    }

    fun setTrendingCategory(category: TrendingCategory) {
        if (_trendingCategory.value == category) return
        _trendingCategory.value = category
        loadTrendingTracks(forceRefresh = true)
    }

    fun setTrendingPreset(preset: TrendingPreset) {
        if (_trendingPreset.value == preset && _trendingSearchQuery.value.isBlank()) return
        _trendingPreset.value = preset
        _trendingSearchQuery.value = ""
        loadTrendingTracks(forceRefresh = true)
    }

    fun setTrendingSearchQuery(query: String) {
        _trendingSearchQuery.value = query
    }

    fun submitTrendingSearch() {
        loadTrendingTracks(forceRefresh = true)
    }

    fun clearTrendingSearch() {
        _trendingSearchQuery.value = ""
        loadTrendingTracks(forceRefresh = true)
    }

    fun scanDeviceMusic(showResultMessage: Boolean = true) {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val prefs = preferences.prefsFlow.first()
                val tracks = repository.loadAllTracks(prefs.favoriteIds, prefs.importedUris)
                _allTracks.value = tracks
                mediaController?.let { syncFromController(it) }
                if (showResultMessage) {
                    _statusMessage.value = if (tracks.isEmpty()) {
                        "آهنگی در حافظه پیدا نشد؛ می‌توانید فایل صوتی اضافه کنید"
                    } else {
                        "${tracks.size} آهنگ آماده پخش است"
                    }
                }
            } finally {
                _isScanning.value = false
            }
        }
    }

    private suspend fun refreshLibrary(favoriteIds: Set<String>, importedUris: Set<String>) {
        val tracks = repository.loadAllTracks(favoriteIds, importedUris)
        _allTracks.value = tracks
        mediaController?.let { syncFromController(it) }
    }

    fun importAudioFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val count = repository.persistImportedUris(uris)
            _statusMessage.value = "$count فایل صوتی اضافه شد"
        }
    }

    fun removeImportedTrack(track: MusicTrack) {
        if (!track.isImported) return
        viewModelScope.launch {
            preferences.removeImportedUri(track.mediaUri)
            _statusMessage.value = "آهنگ حذف شد"
        }
    }

    fun playTrack(track: MusicTrack) {
        val controller = mediaController ?: return
        val list = filteredTracks.value.ifEmpty { _allTracks.value }.ifEmpty { listOf(track) }
        playFromQueue(track, list, controller)
    }

    fun playTrendingTrack(track: MusicTrack) {
        val controller = mediaController ?: return
        val list = _trendingTracks.value.ifEmpty { listOf(track) }
        playFromQueue(track, list, controller)
    }

    private fun playFromQueue(
        track: MusicTrack,
        queue: List<MusicTrack>,
        controller: MediaController
    ) {
        activeQueueTracks = queue
        val index = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        controller.setMediaItems(queue.map { repository.toMediaItem(it) }, index, 0L)
        controller.prepare()
        controller.play()
        syncFromController(controller)
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else if (controller.mediaItemCount == 0) {
            val firstTrack = if (_activeTab.value == AppTab.TRENDING) {
                _trendingTracks.value.firstOrNull() ?: _allTracks.value.firstOrNull()
            } else {
                _allTracks.value.firstOrNull() ?: _trendingTracks.value.firstOrNull()
            }
            if (firstTrack != null) {
                if (firstTrack.isOnline) playTrendingTrack(firstTrack) else playTrack(firstTrack)
            }
        } else {
            controller.prepare()
            controller.play()
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
        val safePos = positionMs.coerceAtLeast(0L)
        controller.seekTo(safePos)
        _playerState.value = _playerState.value.copy(positionMs = safePos)
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        val next = !controller.shuffleModeEnabled
        controller.shuffleModeEnabled = next
        viewModelScope.launch {
            preferences.setShuffleEnabled(next)
            scheduleAutoDriveSync()
        }
    }

    fun cycleRepeatMode() {
        val controller = mediaController ?: return
        val nextOption = when (_playerState.value.repeatMode) {
            RepeatModeOption.OFF -> RepeatModeOption.ALL
            RepeatModeOption.ALL -> RepeatModeOption.ONE
            RepeatModeOption.ONE -> RepeatModeOption.OFF
        }
        controller.repeatMode = when (nextOption) {
            RepeatModeOption.OFF -> Player.REPEAT_MODE_OFF
            RepeatModeOption.ALL -> Player.REPEAT_MODE_ALL
            RepeatModeOption.ONE -> Player.REPEAT_MODE_ONE
        }
        viewModelScope.launch {
            preferences.setRepeatMode(nextOption)
            scheduleAutoDriveSync()
        }
    }

    fun toggleFavorite(trackId: String) {
        val snapshot = findTrackById(trackId)
        toggleFavorite(trackId, snapshot)
    }

    fun toggleFavorite(track: MusicTrack) {
        toggleFavorite(track.id, track)
    }

    private fun toggleFavorite(trackId: String, snapshot: MusicTrack?) {
        viewModelScope.launch {
            val isFav = preferences.toggleFavorite(trackId, snapshot)
            _statusMessage.value = if (isFav) "به علاقه‌مندی‌ها اضافه شد" else "از علاقه‌مندی‌ها حذف شد"
            scheduleAutoDriveSync()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: TrackFilter) {
        _activeFilter.value = filter
    }

    fun clearMessage() {
        _statusMessage.value = null
    }

    override fun onCleared() {
        progressJob?.cancel()
        trendingLoadJob?.cancel()
        autoDriveSyncJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        super.onCleared()
    }
}
