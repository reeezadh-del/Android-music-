package com.example.ui.viewmodel

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.ai.AiVoiceSearchRepository
import com.example.ai.AiVoiceSearchResult
import com.example.auth.UserAccount
import com.example.auth.UserAccountRepository
import com.example.diagnostics.CrashReporter
import com.example.drive.DriveDbSnapshot
import com.example.drive.GoogleDriveDatabaseManager
import com.example.player.AppTab
import com.example.player.AudioVoiceRecorder
import com.example.player.AutoSpeedVolumeManager
import com.example.player.AutoSpeedVolumeState
import com.example.player.FavoriteTracksRepository
import com.example.player.MusicLibraryRepository
import com.example.player.MusicPlaybackService
import com.example.player.MusicPreferences
import com.example.player.MusicTrack
import com.example.player.OnlineDiscoverStyle
import com.example.player.OnlineSubSection
import com.example.player.RepeatModeOption
import com.example.player.SpeedVolumeSensitivity
import com.example.player.TrackCommentUi
import com.example.player.TrackFilter
import com.example.player.TrackReactionSummary
import com.example.player.TrackReactionType
import com.example.player.TrackSocialRepository
import com.example.player.TrendingCategory
import com.example.player.TrendingPreset
import com.example.player.TrendingIranianRepository
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayerUiState(
    val currentTrack: MusicTrack? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatModeOption = RepeatModeOption.OFF
)

sealed interface FavoritesUiState {
    data object Loading : FavoritesUiState
    data class Success(
        val tracks: List<MusicTrack>,
        val totalCount: Int,
        val isCloudBacked: Boolean,
        val isSyncing: Boolean
    ) : FavoritesUiState

    data class Error(
        val message: String,
        val fallbackTracks: List<MusicTrack>,
        val isCloudBacked: Boolean
    ) : FavoritesUiState
}

sealed interface AiVoiceSearchUiState {
    data object Idle : AiVoiceSearchUiState
    data object PermissionRequired : AiVoiceSearchUiState
    data class Recording(val elapsedSeconds: Int = 0, val amplitude: Float = 0f) : AiVoiceSearchUiState
    data object Analyzing : AiVoiceSearchUiState
    data class Success(val result: AiVoiceSearchResult) : AiVoiceSearchUiState
    data class Error(val message: String) : AiVoiceSearchUiState
}

class MusicPlayerViewModel(
    private val appContext: Context,
    private val preferences: MusicPreferences,
    private val repository: MusicLibraryRepository,
    private val trendingRepository: TrendingIranianRepository,
    private val userAccountRepository: UserAccountRepository?,
    private val driveDbManager: GoogleDriveDatabaseManager,
    val currentUserId: String,
    private val autoSpeedVolumeManager: AutoSpeedVolumeManager? = null,
    private val favoriteTracksRepository: FavoriteTracksRepository? = null,
    private val trackSocialRepository: TrackSocialRepository? = null
) : ViewModel() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressJob: Job? = null
    private var trendingLoadJob: Job? = null
    private var discoverLoadJob: Job? = null
    private var autoDriveSyncJob: Job? = null
    private var voiceRecordingJob: Job? = null
    private var aiVoiceJob: Job? = null
    private var cloudFavoritesJob: Job? = null
    private var socialSummariesJob: Job? = null
    private var activeReactionsJob: Job? = null
    private var activeCommentsJob: Job? = null

    private val aiVoiceSearchRepository by lazy { AiVoiceSearchRepository() }
    private val audioVoiceRecorder by lazy { AudioVoiceRecorder(appContext) }

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

    // Online hub states (Trending + Discover)
    private val _onlineSubSection = MutableStateFlow(OnlineSubSection.TRENDING)
    val onlineSubSection: StateFlow<OnlineSubSection> = _onlineSubSection.asStateFlow()

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

    private val _discoverTracks = MutableStateFlow<List<MusicTrack>>(emptyList())
    val discoverTracks: StateFlow<List<MusicTrack>> = _discoverTracks.asStateFlow()

    private val _discoverStyle = MutableStateFlow(OnlineDiscoverStyle.NEW_HITS)
    val discoverStyle: StateFlow<OnlineDiscoverStyle> = _discoverStyle.asStateFlow()

    private val _discoverSearchQuery = MutableStateFlow("")
    val discoverSearchQuery: StateFlow<String> = _discoverSearchQuery.asStateFlow()

    private val _isLoadingDiscover = MutableStateFlow(false)
    val isLoadingDiscover: StateFlow<Boolean> = _isLoadingDiscover.asStateFlow()

    private val _discoverError = MutableStateFlow<String?>(null)
    val discoverError: StateFlow<String?> = _discoverError.asStateFlow()

    // Social reactions & comments states
    private val _trackSocialSummaries = MutableStateFlow<Map<String, TrackReactionSummary>>(emptyMap())
    val trackSocialSummaries: StateFlow<Map<String, TrackReactionSummary>> = _trackSocialSummaries.asStateFlow()

    private val _activeSocialTrack = MutableStateFlow<MusicTrack?>(null)
    val activeSocialTrack: StateFlow<MusicTrack?> = _activeSocialTrack.asStateFlow()

    private val _activeTrackSummary = MutableStateFlow(TrackReactionSummary())
    val activeTrackSummary: StateFlow<TrackReactionSummary> = _activeTrackSummary.asStateFlow()

    private val _activeTrackComments = MutableStateFlow<List<TrackCommentUi>>(emptyList())
    val activeTrackComments: StateFlow<List<TrackCommentUi>> = _activeTrackComments.asStateFlow()

    private val _isLoadingComments = MutableStateFlow(false)
    val isLoadingComments: StateFlow<Boolean> = _isLoadingComments.asStateFlow()

    private val _isSubmittingComment = MutableStateFlow(false)
    val isSubmittingComment: StateFlow<Boolean> = _isSubmittingComment.asStateFlow()

    private val _commentsError = MutableStateFlow<String?>(null)
    val commentsError: StateFlow<String?> = _commentsError.asStateFlow()

    private val _isSyncingDrive = MutableStateFlow(false)
    val isSyncingDrive: StateFlow<Boolean> = _isSyncingDrive.asStateFlow()

    private val _driveSyncMessage = MutableStateFlow<String?>(null)
    val driveSyncMessage: StateFlow<String?> = _driveSyncMessage.asStateFlow()

    private var hasLoadedInitialTrending = false
    private var hasLoadedInitialDiscover = false
    private var activeQueueTracks: List<MusicTrack> = emptyList()

    private val _playerState = MutableStateFlow(PlayerUiState())
    val playerState: StateFlow<PlayerUiState> = _playerState.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _aiVoiceState = MutableStateFlow<AiVoiceSearchUiState>(AiVoiceSearchUiState.Idle)
    val aiVoiceState: StateFlow<AiVoiceSearchUiState> = _aiVoiceState.asStateFlow()

    private val _cloudFavorites = MutableStateFlow<List<MusicTrack>?>(null)
    private val _favoritesSearchQuery = MutableStateFlow("")
    val favoritesSearchQuery: StateFlow<String> = _favoritesSearchQuery.asStateFlow()

    private val _favoritesCloudError = MutableStateFlow<String?>(null)
    private val _isSyncingFavorites = MutableStateFlow(false)
    private var hasCompletedInitialCloudBackfill = false

    val autoSpeedVolumeState: StateFlow<AutoSpeedVolumeState> =
        autoSpeedVolumeManager?.state ?: MutableStateFlow(AutoSpeedVolumeState()).asStateFlow()

    private fun isCloudFavoritesActive(): Boolean {
        if (favoriteTracksRepository == null || currentUserId == "guest") return false
        val signedUid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
        return !signedUid.isNullOrBlank() && signedUid == currentUserId
    }

    private fun isCloudSocialActive(): Boolean {
        if (trackSocialRepository == null || currentUserId == "guest") return false
        val signedUid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
        return !signedUid.isNullOrBlank() && signedUid == currentUserId
    }

    private fun resolveAuthorUsername(): String {
        val accountName = activeAccount?.username?.trim().orEmpty()
        if (accountName.isNotEmpty()) return accountName
        val display = runCatching { Firebase.auth.currentUser?.displayName?.trim() }.getOrNull().orEmpty()
        if (display.isNotEmpty()) return display.take(24)
        return "کاربر"
    }

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

    val favoritesUiState: StateFlow<FavoritesUiState> = combine(
        _allTracks,
        _favoriteOnlineTracks,
        _cloudFavorites,
        _favoritesSearchQuery,
        combine(_favoritesCloudError, _isSyncingFavorites) { err, syncing -> err to syncing }
    ) { localTracks, favOnline, cloudTracks, query, (cloudError, isSyncing) ->
        val cloudBacked = isCloudFavoritesActive()
        val localMerged = (localTracks.filter { it.isFavorite } + favOnline).distinctBy { it.id }

        if (cloudBacked && cloudTracks == null && cloudError == null) {
            FavoritesUiState.Loading
        } else {
            val baseTracks = if (cloudBacked && cloudTracks != null) {
                val localById = localTracks.associateBy { it.id }
                cloudTracks.map { cloudTrack ->
                    localById[cloudTrack.id]?.copy(isFavorite = true) ?: cloudTrack.copy(isFavorite = true)
                }
            } else {
                localMerged
            }
            val q = query.trim().lowercase()
            val filtered = if (q.isEmpty()) {
                baseTracks
            } else {
                baseTracks.filter {
                    it.title.lowercase().contains(q) ||
                        it.artist.lowercase().contains(q) ||
                        it.album.lowercase().contains(q)
                }
            }
            if (cloudError != null && cloudBacked) {
                FavoritesUiState.Error(
                    message = cloudError,
                    fallbackTracks = filtered,
                    isCloudBacked = true
                )
            } else {
                FavoritesUiState.Success(
                    tracks = filtered,
                    totalCount = baseTracks.size,
                    isCloudBacked = cloudBacked,
                    isSyncing = isSyncing
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavoritesUiState.Loading)

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
        startObservingCloudFavorites()
    }

    private fun startObservingCloudFavorites() {
        val repo = favoriteTracksRepository ?: return
        if (!isCloudFavoritesActive()) return

        cloudFavoritesJob?.cancel()
        cloudFavoritesJob = viewModelScope.launch {
            _favoritesCloudError.value = null
            repo.observeFavorites(currentUserId)
                .catch { error ->
                    val rootFirestore = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    _favoritesCloudError.value = when (rootFirestore?.code) {
                        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                            "دسترسی به لیست علاقه‌مندی‌های ابری مجاز نیست."
                        FirebaseFirestoreException.Code.UNAVAILABLE ->
                            "ارتباط با Cloud Firestore برقرار نشد؛ لطفاً اینترنت را بررسی کنید."
                        else ->
                            rootFirestore?.localizedMessage
                                ?: error.localizedMessage
                                ?: "خطا در همگام‌سازی علاقه‌مندی‌ها با Firestore"
                    }
                }
                .collect { cloudList ->
                    _favoritesCloudError.value = null
                    _cloudFavorites.value = cloudList

                    if (!hasCompletedInitialCloudBackfill) {
                        hasCompletedInitialCloudBackfill = true
                        if (cloudList.isEmpty()) {
                            val prefs = preferences.prefsFlow.first()
                            val localTracks = _allTracks.value.filter { it.id in prefs.favoriteIds }
                            val localToUpload = (localTracks + prefs.favoriteOnlineTracks)
                                .distinctBy { it.id }
                            if (localToUpload.isNotEmpty()) {
                                _isSyncingFavorites.value = true
                                repo.syncMissingFavorites(currentUserId, localToUpload)
                                _isSyncingFavorites.value = false
                                return@collect
                            }
                        }
                    }

                    val currentLocalIds = preferences.prefsFlow.first().favoriteIds
                    val cloudIds = cloudList.map { it.id }.toSet()
                    if (currentLocalIds != cloudIds) {
                        preferences.setCloudFavoritesSnapshot(cloudList)
                    }
                }
        }
    }

    fun retryCloudFavorites() {
        _cloudFavorites.value = null
        _favoritesCloudError.value = null
        startObservingCloudFavorites()
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
                        userAccountRepository?.updateDriveFileId(currentUserId, fileId)
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
                userAccountRepository?.updateDriveFileId(currentUserId, fileId)
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
        if (_discoverTracks.value.isNotEmpty()) {
            _discoverTracks.value = _discoverTracks.value.map { track ->
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
            ?: _discoverTracks.value.firstOrNull { it.id == id }
            ?: _favoriteOnlineTracks.value.firstOrNull { it.id == id }
            ?: _cloudFavorites.value?.firstOrNull { it.id == id }
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
        if (tab == AppTab.ONLINE) {
            when (_onlineSubSection.value) {
                OnlineSubSection.TRENDING -> {
                    if (!hasLoadedInitialTrending && !_isLoadingTrending.value) {
                        loadTrendingTracks(forceRefresh = false)
                    }
                }
                OnlineSubSection.DISCOVER -> {
                    if (!hasLoadedInitialDiscover && !_isLoadingDiscover.value) {
                        loadDiscoverTracks(forceRefresh = false)
                    }
                }
            }
        }
    }

    fun selectOnlineSubSection(subSection: OnlineSubSection) {
        _onlineSubSection.value = subSection
        when (subSection) {
            OnlineSubSection.TRENDING -> {
                if (!hasLoadedInitialTrending && !_isLoadingTrending.value) {
                    loadTrendingTracks(forceRefresh = false)
                } else {
                    refreshSocialSummariesForTracks(_trendingTracks.value)
                }
            }
            OnlineSubSection.DISCOVER -> {
                if (!hasLoadedInitialDiscover && !_isLoadingDiscover.value) {
                    loadDiscoverTracks(forceRefresh = false)
                } else {
                    refreshSocialSummariesForTracks(_discoverTracks.value)
                }
            }
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
                refreshSocialSummariesForTracks(list)
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

    fun loadDiscoverTracks(forceRefresh: Boolean = true) {
        if (!forceRefresh && hasLoadedInitialDiscover && _discoverTracks.value.isNotEmpty()) return
        discoverLoadJob?.cancel()
        discoverLoadJob = viewModelScope.launch {
            _isLoadingDiscover.value = true
            _discoverError.value = null
            val favIds = preferences.prefsFlow.first().favoriteIds
            val result = trendingRepository.fetchDiscoverTracks(
                style = _discoverStyle.value,
                customQuery = _discoverSearchQuery.value,
                favoriteIds = favIds
            )
            result.onSuccess { list ->
                hasLoadedInitialDiscover = true
                _discoverTracks.value = list
                _discoverError.value = null
                mediaController?.let { syncFromController(it) }
                refreshSocialSummariesForTracks(list)
            }.onFailure { err ->
                CrashReporter.recordNonFatal("DISCOVER_FETCH", err, context = appContext)
                _discoverError.value = "دریافت آهنگ‌های آنلاین ممکن نشد. اتصال اینترنت را بررسی کنید."
            }
            _isLoadingDiscover.value = false
        }
    }

    fun setDiscoverStyle(style: OnlineDiscoverStyle) {
        if (_discoverStyle.value == style && _discoverSearchQuery.value.isBlank()) return
        _discoverStyle.value = style
        _discoverSearchQuery.value = ""
        loadDiscoverTracks(forceRefresh = true)
    }

    fun setDiscoverSearchQuery(query: String) {
        _discoverSearchQuery.value = query
    }

    fun submitDiscoverSearch() {
        loadDiscoverTracks(forceRefresh = true)
    }

    fun clearDiscoverSearch() {
        _discoverSearchQuery.value = ""
        loadDiscoverTracks(forceRefresh = true)
    }

    fun refreshSocialSummariesForTracks(tracks: List<MusicTrack>) {
        val repo = trackSocialRepository ?: return
        if (!isCloudSocialActive() || tracks.isEmpty()) return
        socialSummariesJob?.cancel()
        socialSummariesJob = viewModelScope.launch {
            val fetched = repo.fetchSummariesForTracks(tracks)
            if (fetched.isNotEmpty()) {
                _trackSocialSummaries.value = _trackSocialSummaries.value + fetched
            }
        }
    }

    fun openTrackSocialSheet(track: MusicTrack) {
        _activeSocialTrack.value = track
        _commentsError.value = null
        val trackKey = TrackSocialRepository.toTrackKey(track)
        val cached = _trackSocialSummaries.value[track.id]
        _activeTrackSummary.value = cached ?: TrackReactionSummary(trackKey = trackKey)

        val repo = trackSocialRepository
        if (repo == null || !isCloudSocialActive()) {
            _activeTrackComments.value = emptyList()
            _isLoadingComments.value = false
            return
        }

        activeReactionsJob?.cancel()
        activeReactionsJob = viewModelScope.launch {
            repo.observeTrackReactionSummary(trackKey)
                .catch { error ->
                    val root = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (root == null && error is CancellationException) throw error
                }
                .collect { summary ->
                    val currentCommentCount = _activeTrackComments.value.size
                        .coerceAtLeast(_trackSocialSummaries.value[track.id]?.commentCount ?: 0)
                    val merged = summary.copy(commentCount = currentCommentCount)
                    _activeTrackSummary.value = merged
                    _trackSocialSummaries.value = _trackSocialSummaries.value + (track.id to merged)
                }
        }

        activeCommentsJob?.cancel()
        _isLoadingComments.value = true
        activeCommentsJob = viewModelScope.launch {
            repo.observeTrackComments(trackKey)
                .catch { error ->
                    val rootFirestore = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (rootFirestore == null && error is CancellationException) throw error
                    _isLoadingComments.value = false
                    _commentsError.value = when (rootFirestore?.code) {
                        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                            "دسترسی به بخش نظرات مجاز نیست."
                        FirebaseFirestoreException.Code.UNAVAILABLE ->
                            "ارتباط با Cloud Firestore برقرار نشد."
                        else ->
                            rootFirestore?.localizedMessage
                                ?: error.localizedMessage
                                ?: "خطا در بارگذاری نظرات"
                    }
                }
                .collect { comments ->
                    _isLoadingComments.value = false
                    _commentsError.value = null
                    _activeTrackComments.value = comments
                    val updatedSummary = _activeTrackSummary.value.copy(commentCount = comments.size)
                    _activeTrackSummary.value = updatedSummary
                    _trackSocialSummaries.value = _trackSocialSummaries.value + (track.id to updatedSummary)
                }
        }
    }

    fun closeTrackSocialSheet() {
        activeReactionsJob?.cancel()
        activeCommentsJob?.cancel()
        _activeSocialTrack.value = null
        _isLoadingComments.value = false
        _commentsError.value = null
    }

    fun toggleTrackReaction(track: MusicTrack, reaction: TrackReactionType) {
        val repo = trackSocialRepository
        if (repo == null || !isCloudSocialActive()) {
            _statusMessage.value = "برای ثبت ری‌اکت لطفاً ابتدا با حساب گوگل وارد شوید"
            return
        }
        val author = resolveAuthorUsername()
        viewModelScope.launch {
            val result = repo.toggleReaction(track, reaction, author)
            result.onSuccess { newSelection ->
                val fresh = repo.fetchTrackSummary(track)
                if (fresh != null) {
                    _trackSocialSummaries.value = _trackSocialSummaries.value + (track.id to fresh)
                    if (_activeSocialTrack.value?.id == track.id) {
                        _activeTrackSummary.value = fresh.copy(
                            commentCount = _activeTrackComments.value.size.coerceAtLeast(fresh.commentCount)
                        )
                    }
                }
                _statusMessage.value = if (newSelection != null) {
                    "واکنش ${newSelection.emoji} ثبت شد"
                } else {
                    "واکنش شما برداشته شد"
                }
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "خطا در ثبت واکنش"
            }
        }
    }

    fun submitTrackComment(track: MusicTrack, rawText: String) {
        val repo = trackSocialRepository
        if (repo == null || !isCloudSocialActive()) {
            _statusMessage.value = "برای ارسال نظر لطفاً ابتدا با حساب گوگل وارد شوید"
            return
        }
        val cleaned = TrackSocialRepository.sanitizeCommentText(rawText)
        if (cleaned == null) {
            _statusMessage.value = "لطفاً متن نظر خود را بنویسید"
            return
        }
        val author = resolveAuthorUsername()
        viewModelScope.launch {
            _isSubmittingComment.value = true
            val result = repo.addComment(track, cleaned, author)
            _isSubmittingComment.value = false
            result.onSuccess {
                _statusMessage.value = "نظر شما ثبت شد"
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "ارسال نظر با خطا مواجه شد"
            }
        }
    }

    fun deleteTrackComment(track: MusicTrack, commentId: String) {
        val repo = trackSocialRepository
        if (repo == null || !isCloudSocialActive()) return
        viewModelScope.launch {
            val result = repo.deleteComment(track, commentId)
            result.onSuccess {
                _statusMessage.value = "نظر حذف شد"
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "حذف نظر با خطا مواجه شد"
            }
        }
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

    fun playDiscoverTrack(track: MusicTrack) {
        val controller = mediaController ?: return
        val list = _discoverTracks.value.ifEmpty { listOf(track) }
        playFromQueue(track, list, controller)
    }

    fun playFavoriteTrack(track: MusicTrack) {
        val controller = mediaController ?: return
        val currentFavorites = when (val state = favoritesUiState.value) {
            is FavoritesUiState.Success -> state.tracks
            is FavoritesUiState.Error -> state.fallbackTracks
            FavoritesUiState.Loading -> emptyList()
        }
        val list = currentFavorites.ifEmpty { listOf(track) }
        playFromQueue(track, list, controller)
    }

    fun playAllFavorites(shuffle: Boolean) {
        val controller = mediaController ?: return
        val currentFavorites = when (val state = favoritesUiState.value) {
            is FavoritesUiState.Success -> state.tracks
            is FavoritesUiState.Error -> state.fallbackTracks
            FavoritesUiState.Loading -> emptyList()
        }
        if (currentFavorites.isEmpty()) return
        controller.shuffleModeEnabled = shuffle
        viewModelScope.launch { preferences.setShuffleEnabled(shuffle) }
        val startTrack = if (shuffle) currentFavorites.random() else currentFavorites.first()
        playFromQueue(startTrack, currentFavorites, controller)
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
            val firstTrack = if (_activeTab.value == AppTab.ONLINE) {
                when (_onlineSubSection.value) {
                    OnlineSubSection.TRENDING -> _trendingTracks.value.firstOrNull() ?: _discoverTracks.value.firstOrNull()
                    OnlineSubSection.DISCOVER -> _discoverTracks.value.firstOrNull() ?: _trendingTracks.value.firstOrNull()
                } ?: _allTracks.value.firstOrNull()
            } else {
                _allTracks.value.firstOrNull() ?: _trendingTracks.value.firstOrNull()
            }
            if (firstTrack != null) {
                if (firstTrack.isOnline) {
                    if (_onlineSubSection.value == OnlineSubSection.DISCOVER && _discoverTracks.value.any { it.id == firstTrack.id }) {
                        playDiscoverTrack(firstTrack)
                    } else {
                        playTrendingTrack(firstTrack)
                    }
                } else {
                    playTrack(firstTrack)
                }
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
        val resolvedSnapshot = snapshot ?: findTrackById(trackId)
        viewModelScope.launch {
            val isFav = preferences.toggleFavorite(trackId, resolvedSnapshot)
            _statusMessage.value = if (isFav) "به علاقه‌مندی‌ها اضافه شد" else "از علاقه‌مندی‌ها حذف شد"

            val repo = favoriteTracksRepository
            if (repo != null && isCloudFavoritesActive()) {
                _isSyncingFavorites.value = true
                val cloudResult = if (isFav && resolvedSnapshot != null) {
                    repo.saveFavorite(currentUserId, resolvedSnapshot.copy(isFavorite = true))
                } else {
                    repo.removeFavorite(currentUserId, trackId)
                }
                _isSyncingFavorites.value = false
                cloudResult.onFailure {
                    _statusMessage.value = "ذخیره محلی انجام شد؛ همگام‌سازی با Firestore خطا داد."
                }
            }

            scheduleAutoDriveSync()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFavoritesSearchQuery(query: String) {
        _favoritesSearchQuery.value = query
    }

    fun applyVoiceSearchToLibrary(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        _activeFilter.value = TrackFilter.ALL
        _searchQuery.value = trimmed
        _statusMessage.value = "جستجوی صوتی: «$trimmed»"
    }

    fun applyVoiceSearchToTrending(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        _trendingSearchQuery.value = trimmed
        _statusMessage.value = "جستجوی صوتی ترند: «$trimmed»"
        loadTrendingTracks(forceRefresh = true)
    }

    fun showStatusMessage(message: String) {
        _statusMessage.value = message
    }

    fun showAiVoicePermissionRequired() {
        voiceRecordingJob?.cancel()
        audioVoiceRecorder.cancelRecording()
        _aiVoiceState.value = AiVoiceSearchUiState.PermissionRequired
    }

    fun startAiVoiceRecording(targetTab: AppTab) {
        voiceRecordingJob?.cancel()
        aiVoiceJob?.cancel()
        val startResult = audioVoiceRecorder.startRecording()
        if (startResult.isFailure) {
            val err = startResult.exceptionOrNull()
            Log.e("AiVoiceVM", "Unable to start microphone recording", err)
            if (err != null) {
                CrashReporter.recordNonFatal("AI_VOICE_MIC_START", err, context = appContext)
            }
            _aiVoiceState.value = AiVoiceSearchUiState.Error(
                err?.localizedMessage ?: "امکان ضبط صدا از میکروفون وجود ندارد."
            )
            return
        }

        _aiVoiceState.value = AiVoiceSearchUiState.Recording(elapsedSeconds = 0, amplitude = 0f)
        voiceRecordingJob = viewModelScope.launch {
            val maxTicks = (AudioVoiceRecorder.MAX_DURATION_MS / 120L).toInt()
            for (tick in 1..maxTicks) {
                delay(120L)
                if (!audioVoiceRecorder.isRecording) return@launch
                val elapsedSec = ((tick * 120L) / 1000L).toInt()
                val amp = audioVoiceRecorder.sampleAmplitudeNormalized()
                _aiVoiceState.value = AiVoiceSearchUiState.Recording(
                    elapsedSeconds = elapsedSec,
                    amplitude = amp
                )
            }
            if (audioVoiceRecorder.isRecording) {
                stopRecordingAndAnalyzeWithAi(targetTab)
            }
        }
    }

    fun stopRecordingAndAnalyzeWithAi(targetTab: AppTab) {
        voiceRecordingJob?.cancel()
        aiVoiceJob?.cancel()
        _aiVoiceState.value = AiVoiceSearchUiState.Analyzing

        aiVoiceJob = viewModelScope.launch {
            try {
                val audioBytes = withContext(Dispatchers.IO) {
                    audioVoiceRecorder.stopAndReadBytes().getOrThrow()
                }
                val result = aiVoiceSearchRepository.analyzeVoiceAudio(
                    audioBytes = audioBytes,
                    mimeType = AudioVoiceRecorder.MIME_TYPE_MP4,
                    libraryTracks = _allTracks.value,
                    targetTab = targetTab
                )
                applyAiSearchResult(result, targetTab, closeDialog = false)
                _aiVoiceState.value = AiVoiceSearchUiState.Success(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AiVoiceVM", "AI voice analysis failed", e)
                CrashReporter.recordNonFatal("AI_VOICE_ANALYZE", e, context = appContext)
                _aiVoiceState.value = AiVoiceSearchUiState.Error(
                    e.localizedMessage ?: "تحلیل صوتی با هوش مصنوعی ناموفق بود."
                )
            }
        }
    }

    fun analyzeSpokenTextWithAi(spokenText: String, targetTab: AppTab) {
        val cleaned = spokenText.trim()
        if (cleaned.isBlank()) {
            _aiVoiceState.value = AiVoiceSearchUiState.Error("عبارت صوتی خالی بود؛ دوباره تلاش کنید.")
            return
        }
        voiceRecordingJob?.cancel()
        audioVoiceRecorder.cancelRecording()
        aiVoiceJob?.cancel()
        _aiVoiceState.value = AiVoiceSearchUiState.Analyzing

        aiVoiceJob = viewModelScope.launch {
            try {
                val result = aiVoiceSearchRepository.analyzeTextQuery(
                    spokenText = cleaned,
                    libraryTracks = _allTracks.value,
                    targetTab = targetTab
                )
                applyAiSearchResult(result, targetTab, closeDialog = false)
                _aiVoiceState.value = AiVoiceSearchUiState.Success(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AiVoiceVM", "AI text search interpretation failed", e)
                CrashReporter.recordNonFatal("AI_VOICE_TEXT_ANALYZE", e, context = appContext)
                _aiVoiceState.value = AiVoiceSearchUiState.Error(
                    e.localizedMessage ?: "پردازش هوشمند عبارت صوتی ناموفق بود."
                )
            }
        }
    }

    fun applyAiSearchResult(
        result: AiVoiceSearchResult,
        targetTab: AppTab,
        closeDialog: Boolean = true
    ) {
        val effectiveQuery = when {
            result.searchQuery.isNotBlank() -> result.searchQuery.trim()
            result.heardText.isNotBlank() -> result.heardText.trim()
            else -> ""
        }
        when (targetTab) {
            AppTab.ONLINE -> {
                _activeTab.value = AppTab.ONLINE
                if (_onlineSubSection.value == OnlineSubSection.DISCOVER && result.suggestedMoodPreset == null) {
                    _discoverSearchQuery.value = effectiveQuery
                    loadDiscoverTracks(forceRefresh = true)
                    if (effectiveQuery.isNotBlank()) {
                        _statusMessage.value = "جستجوی هوشمند آنلاین: «$effectiveQuery»"
                    }
                } else {
                    _onlineSubSection.value = OnlineSubSection.TRENDING
                    result.suggestedMoodPreset?.let { _trendingPreset.value = it }
                    _trendingSearchQuery.value = effectiveQuery
                    loadTrendingTracks(forceRefresh = true)
                    if (effectiveQuery.isNotBlank()) {
                        _statusMessage.value = "جستجوی هوشمند ترند: «$effectiveQuery»"
                    }
                }
            }
            AppTab.FAVORITES -> {
                _activeTab.value = AppTab.FAVORITES
                val matchedTrack = result.matchedTrackIds
                    .firstNotNullOfOrNull { id -> findTrackById(id) }
                val displayQuery = matchedTrack?.title ?: effectiveQuery
                _favoritesSearchQuery.value = displayQuery
                if (displayQuery.isNotBlank()) {
                    _statusMessage.value = "جستجوی هوشمند علاقه‌مندی‌ها: «$displayQuery»"
                }
            }
            AppTab.LIBRARY -> {
                _activeTab.value = AppTab.LIBRARY
                _activeFilter.value = TrackFilter.ALL
                val matchedTrack = result.matchedTrackIds
                    .firstNotNullOfOrNull { id -> _allTracks.value.firstOrNull { it.id == id } }
                val displayQuery = matchedTrack?.title ?: effectiveQuery
                _searchQuery.value = displayQuery
                if (displayQuery.isNotBlank()) {
                    _statusMessage.value = "جستجوی هوشمند: «$displayQuery»"
                }
            }
        }
        if (closeDialog) {
            _aiVoiceState.value = AiVoiceSearchUiState.Idle
        }
    }

    fun cancelAiVoiceRecording() {
        voiceRecordingJob?.cancel()
        audioVoiceRecorder.cancelRecording()
        _aiVoiceState.value = AiVoiceSearchUiState.Idle
    }

    fun dismissAiVoiceSearch() {
        voiceRecordingJob?.cancel()
        aiVoiceJob?.cancel()
        audioVoiceRecorder.cancelRecording()
        _aiVoiceState.value = AiVoiceSearchUiState.Idle
    }

    fun setFilter(filter: TrackFilter) {
        _activeFilter.value = filter
    }

    fun setSpeedVolumeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setSpeedVolumeEnabled(enabled)
            if (enabled) {
                autoSpeedVolumeManager?.syncBackgroundServiceFromForeground()
            }
        }
    }

    fun setSpeedVolumeSensitivity(sensitivity: SpeedVolumeSensitivity) {
        viewModelScope.launch {
            preferences.setSpeedVolumeSensitivity(sensitivity)
        }
    }

    fun setOnlyWhenAndroidAutoConnected(onlyAndroidAuto: Boolean) {
        viewModelScope.launch {
            preferences.setOnlyWhenAndroidAutoConnected(onlyAndroidAuto)
        }
    }

    fun syncSpeedVolumeBackgroundService() {
        autoSpeedVolumeManager?.refreshPermissionState()
        autoSpeedVolumeManager?.syncBackgroundServiceFromForeground()
    }

    fun refreshAutoSpeedPermission() {
        autoSpeedVolumeManager?.refreshPermissionState()
    }

    fun clearMessage() {
        _statusMessage.value = null
    }

    override fun onCleared() {
        progressJob?.cancel()
        trendingLoadJob?.cancel()
        discoverLoadJob?.cancel()
        autoDriveSyncJob?.cancel()
        voiceRecordingJob?.cancel()
        aiVoiceJob?.cancel()
        cloudFavoritesJob?.cancel()
        socialSummariesJob?.cancel()
        activeReactionsJob?.cancel()
        activeCommentsJob?.cancel()
        audioVoiceRecorder.cancelRecording()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        super.onCleared()
    }
}
