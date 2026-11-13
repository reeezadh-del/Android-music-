package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.Track
import com.example.headunit.CarPlayDockSide
import com.example.ui.carplay.CarPlayAndroidAutoProjectionScreen
import com.example.ui.carplay.CarPlayAppGridView
import com.example.ui.carplay.CarPlayBottomDock
import com.example.ui.carplay.CarPlayCompactStatusBar
import com.example.ui.carplay.CarPlayDashboardView
import com.example.ui.carplay.CarPlayGlassCard
import com.example.ui.carplay.CarPlayInstalledAppsScreen
import com.example.ui.carplay.CarPlayMapsGpsScreen
import com.example.ui.carplay.CarPlayMusicSuiteScreen
import com.example.ui.carplay.CarPlayPhoneScreen
import com.example.ui.carplay.CarPlaySettingsScreen
import com.example.ui.carplay.CarPlayVerticalDock
import com.example.ui.carplay.CarPlayWallpaperBackdrop
import com.example.ui.carplay.rememberCarPlayPalette
import com.example.ui.components.AddToPlaylistSheet
import com.example.ui.screens.CarModeScreen
import com.example.ui.screens.CreatePlaylistDialog
import com.example.ui.screens.EqualizerScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.theme.AvaPlayerTheme
import com.example.ui.viewmodel.AvaPlayerViewModel
import com.example.ui.viewmodel.CarPlaySurfacePage
import com.example.ui.viewmodel.HeadunitViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AvaPlayerTheme(darkTheme = true) {
                CarPlayHeadunitApp(activity = this)
            }
        }
    }
}

@Composable
fun CarPlayHeadunitApp(
    activity: ComponentActivity,
    playerViewModel: AvaPlayerViewModel = viewModel(),
    headunitViewModel: HeadunitViewModel = viewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Player & Music Library States
    val allTracks by playerViewModel.allTracks.collectAsStateWithLifecycle()
    val filteredTracks by playerViewModel.filteredTracks.collectAsStateWithLifecycle()
    val recentTracks by playerViewModel.recentTracks.collectAsStateWithLifecycle()
    val albums by playerViewModel.albums.collectAsStateWithLifecycle()
    val playlists by playerViewModel.playlists.collectAsStateWithLifecycle()
    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()
    val searchQuery by playerViewModel.searchQuery.collectAsStateWithLifecycle()
    val libraryFilter by playerViewModel.libraryFilter.collectAsStateWithLifecycle()
    val equalizerState by playerViewModel.equalizerState.collectAsStateWithLifecycle()
    val statusBanner by playerViewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val onlineFeedState by playerViewModel.onlineFeedState.collectAsStateWithLifecycle()
    val onlineRegion by playerViewModel.onlineRegion.collectAsStateWithLifecycle()
    val iranianSiteFilter by playerViewModel.iranianSiteFilter.collectAsStateWithLifecycle()
    val onlineGenre by playerViewModel.onlineGenre.collectAsStateWithLifecycle()
    val onlineSearchQuery by playerViewModel.onlineSearchQuery.collectAsStateWithLifecycle()
    val isScanningDeviceMusic by playerViewModel.isScanningDeviceMusic.collectAsStateWithLifecycle()

    // Headunit Reloaded & CarPlay Shell States
    val hurSettings by headunitViewModel.settings.collectAsStateWithLifecycle()
    val aapStatus by headunitViewModel.aapStatus.collectAsStateWithLifecycle()
    val localIp by headunitViewModel.localIpAddress.collectAsStateWithLifecycle()
    val usbState by headunitViewModel.usbState.collectAsStateWithLifecycle()
    val gpsTelemetry by headunitViewModel.gpsTelemetry.collectAsStateWithLifecycle()
    val currentPage by headunitViewModel.currentPage.collectAsStateWithLifecycle()
    val installedApps by headunitViewModel.installedApps.collectAsStateWithLifecycle()
    val clockText by headunitViewModel.clockText.collectAsStateWithLifecycle()
    val batteryPercent by headunitViewModel.batteryPercent.collectAsStateWithLifecycle()
    val headunitMessage by headunitViewModel.headunitMessage.collectAsStateWithLifecycle()

    var isNowPlayingOpen by rememberSaveable { mutableStateOf(false) }
    var isCarModeOpen by rememberSaveable { mutableStateOf(false) }
    var trackForPlaylistSheet by remember { mutableStateOf<Track?>(null) }
    var showCreatePlaylistFromSheet by remember { mutableStateOf(false) }

    val palette = rememberCarPlayPalette(hurSettings.carPlayTheme)

    // Keep Screen On while driving
    DisposableEffect(hurSettings.keepScreenOn) {
        if (hurSettings.keepScreenOn) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val audioPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { results ->
            if (results[audioPermission] == true) {
                playerViewModel.scanDeviceMusic(showBannerOnResult = false)
            }
            if (results[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                results[Manifest.permission.ACCESS_FINE_LOCATION] == true
            ) {
                headunitViewModel.refreshLocationPermissionState()
            }
        }
    )

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = {
            headunitViewModel.refreshLocationPermissionState()
        }
    )

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            headunitViewModel.updateMicPassthrough(granted)
        }
    )

    val triggerDeviceMusicScan: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            audioPermission
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            playerViewModel.scanDeviceMusic(showBannerOnResult = true)
        } else {
            permissionsLauncher.launch(arrayOf(audioPermission))
        }
    }

    val requestLocationAccess: () -> Unit = {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                playerViewModel.importAudioUris(uris)
            }
        }
    )

    LaunchedEffect(Unit) {
        val audioGranted = ContextCompat.checkSelfPermission(
            context,
            audioPermission
        ) == PackageManager.PERMISSION_GRANTED

        if (audioGranted) {
            playerViewModel.scanDeviceMusic(showBannerOnResult = false)
        }
        val toRequest = buildList {
            if (!audioGranted) add(audioPermission)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val notifGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!notifGranted) add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (toRequest.isNotEmpty()) {
            permissionsLauncher.launch(toRequest.toTypedArray())
        }
    }

    LaunchedEffect(statusBanner) {
        statusBanner?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            playerViewModel.clearBanner()
        }
    }

    LaunchedEffect(headunitMessage) {
        headunitMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            headunitViewModel.clearMessage()
        }
    }

    val activeTrack = playbackState.currentTrack

    Box(modifier = Modifier.fillMaxSize()) {
        CarPlayWallpaperBackdrop(palette = palette)

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val isLandscapeHeadunit = maxWidth >= 600.dp

                if (isLandscapeHeadunit) {
                    val dockContent: @Composable () -> Unit = {
                        CarPlayVerticalDock(
                            clockText = clockText,
                            batteryPercent = batteryPercent,
                            aapStatus = aapStatus,
                            currentPage = currentPage,
                            palette = palette,
                            onSelectPage = headunitViewModel::navigateTo,
                            onToggleDashboardGrid = headunitViewModel::toggleDashboardAndGrid
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (hurSettings.dockSide == CarPlayDockSide.LEFT) {
                            dockContent()
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            CarPlayPageHost(
                                currentPage = currentPage,
                                palette = palette,
                                playerViewModel = playerViewModel,
                                headunitViewModel = headunitViewModel,
                                allTracks = allTracks,
                                filteredTracks = filteredTracks,
                                recentTracks = recentTracks,
                                albums = albums,
                                playlists = playlists,
                                activeTrack = activeTrack,
                                isPlaying = playbackState.isPlaying,
                                positionMs = playbackState.currentPositionMs,
                                durationMs = playbackState.durationMs,
                                isScanningDeviceMusic = isScanningDeviceMusic,
                                searchQuery = searchQuery,
                                libraryFilter = libraryFilter,
                                equalizerState = equalizerState,
                                onlineFeedState = onlineFeedState,
                                onlineRegion = onlineRegion,
                                iranianSiteFilter = iranianSiteFilter,
                                onlineGenre = onlineGenre,
                                onlineSearchQuery = onlineSearchQuery,
                                hurSettings = hurSettings,
                                aapStatus = aapStatus,
                                localIp = localIp,
                                usbState = usbState,
                                gpsTelemetry = gpsTelemetry,
                                installedApps = installedApps,
                                onRequestLocation = requestLocationAccess,
                                onRequestMic = { enabled ->
                                    if (enabled) {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) headunitViewModel.updateMicPassthrough(true)
                                        else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        headunitViewModel.updateMicPassthrough(false)
                                    }
                                },
                                onScanDeviceMusic = triggerDeviceMusicScan,
                                onImportAudio = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                                onOpenNowPlaying = { isNowPlayingOpen = true },
                                onOpenCarMode = { isCarModeOpen = true },
                                onAddToPlaylist = { trackForPlaylistSheet = it }
                            )
                        }

                        if (hurSettings.dockSide == CarPlayDockSide.RIGHT) {
                            dockContent()
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        CarPlayCompactStatusBar(
                            clockText = clockText,
                            batteryPercent = batteryPercent,
                            aapStatus = aapStatus,
                            palette = palette
                        )

                        Box(modifier = Modifier.weight(1f)) {
                            CarPlayPageHost(
                                currentPage = currentPage,
                                palette = palette,
                                playerViewModel = playerViewModel,
                                headunitViewModel = headunitViewModel,
                                allTracks = allTracks,
                                filteredTracks = filteredTracks,
                                recentTracks = recentTracks,
                                albums = albums,
                                playlists = playlists,
                                activeTrack = activeTrack,
                                isPlaying = playbackState.isPlaying,
                                positionMs = playbackState.currentPositionMs,
                                durationMs = playbackState.durationMs,
                                isScanningDeviceMusic = isScanningDeviceMusic,
                                searchQuery = searchQuery,
                                libraryFilter = libraryFilter,
                                equalizerState = equalizerState,
                                onlineFeedState = onlineFeedState,
                                onlineRegion = onlineRegion,
                                iranianSiteFilter = iranianSiteFilter,
                                onlineGenre = onlineGenre,
                                onlineSearchQuery = onlineSearchQuery,
                                hurSettings = hurSettings,
                                aapStatus = aapStatus,
                                localIp = localIp,
                                usbState = usbState,
                                gpsTelemetry = gpsTelemetry,
                                installedApps = installedApps,
                                onRequestLocation = requestLocationAccess,
                                onRequestMic = { enabled ->
                                    if (enabled) {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) headunitViewModel.updateMicPassthrough(true)
                                        else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        headunitViewModel.updateMicPassthrough(false)
                                    }
                                },
                                onScanDeviceMusic = triggerDeviceMusicScan,
                                onImportAudio = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                                onOpenNowPlaying = { isNowPlayingOpen = true },
                                onOpenCarMode = { isCarModeOpen = true },
                                onAddToPlaylist = { trackForPlaylistSheet = it }
                            )
                        }

                        CarPlayBottomDock(
                            currentPage = currentPage,
                            palette = palette,
                            onSelectPage = headunitViewModel::navigateTo,
                            onToggleDashboardGrid = headunitViewModel::toggleDashboardAndGrid
                        )
                    }
                }
            }
        }

        // Full-Screen Now Playing Overlay
        AnimatedVisibility(
            visible = isNowPlayingOpen && activeTrack != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            if (activeTrack != null) {
                NowPlayingScreen(
                    track = activeTrack,
                    isPlaying = playbackState.isPlaying,
                    currentPositionMs = playbackState.currentPositionMs,
                    durationMs = playbackState.durationMs,
                    shuffleEnabled = playbackState.shuffleEnabled,
                    repeatMode = playbackState.repeatMode,
                    queueTracks = playbackState.queueTracks,
                    currentQueueIndex = playbackState.currentQueueIndex,
                    onCollapse = { isNowPlayingOpen = false },
                    onTogglePlayPause = playerViewModel::togglePlayPause,
                    onSkipNext = playerViewModel::skipNext,
                    onSkipPrevious = playerViewModel::skipPrevious,
                    onSeekTo = playerViewModel::seekTo,
                    onToggleShuffle = playerViewModel::toggleShuffle,
                    onCycleRepeat = playerViewModel::cycleRepeatMode,
                    onToggleFavorite = { playerViewModel.toggleFavorite(activeTrack.id) },
                    onSelectQueueItem = playerViewModel::playQueueItemAt,
                    onOpenCarMode = {
                        isNowPlayingOpen = false
                        isCarModeOpen = true
                    },
                    onOpenEqualizer = {
                        isNowPlayingOpen = false
                        headunitViewModel.navigateTo(CarPlaySurfacePage.EQUALIZER)
                    }
                )
            }
        }

        // Distraction-Safe Driving / Car Mode Overlay
        AnimatedVisibility(
            visible = isCarModeOpen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            CarModeScreen(
                track = activeTrack,
                isPlaying = playbackState.isPlaying,
                currentPositionMs = playbackState.currentPositionMs,
                durationMs = playbackState.durationMs,
                onExit = { isCarModeOpen = false },
                onTogglePlayPause = playerViewModel::togglePlayPause,
                onSkipNext = playerViewModel::skipNext,
                onSkipPrevious = playerViewModel::skipPrevious,
                onToggleFavorite = playerViewModel::toggleFavorite
            )
        }
    }

    val pendingTrack = trackForPlaylistSheet
    if (pendingTrack != null) {
        AddToPlaylistSheet(
            track = pendingTrack,
            playlists = playlists,
            onSelectPlaylist = { playlist ->
                playerViewModel.addTrackToPlaylist(playlist.id, pendingTrack.id, playlist.name)
                trackForPlaylistSheet = null
            },
            onCreateNewPlaylistClick = {
                showCreatePlaylistFromSheet = true
            },
            onDismiss = { trackForPlaylistSheet = null }
        )
    }

    if (showCreatePlaylistFromSheet) {
        CreatePlaylistDialog(
            onConfirm = { name ->
                playerViewModel.createPlaylist(name)
                showCreatePlaylistFromSheet = false
            },
            onDismiss = { showCreatePlaylistFromSheet = false }
        )
    }
}

@Composable
private fun CarPlayPageHost(
    currentPage: CarPlaySurfacePage,
    palette: com.example.ui.carplay.CarPlayPalette,
    playerViewModel: AvaPlayerViewModel,
    headunitViewModel: HeadunitViewModel,
    allTracks: List<Track>,
    filteredTracks: List<Track>,
    recentTracks: List<Track>,
    albums: List<com.example.data.model.AlbumSummary>,
    playlists: List<com.example.data.model.Playlist>,
    activeTrack: Track?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    isScanningDeviceMusic: Boolean,
    searchQuery: String,
    libraryFilter: com.example.ui.viewmodel.LibraryFilter,
    equalizerState: com.example.data.model.EqualizerUiState,
    onlineFeedState: com.example.ui.viewmodel.OnlineFeedUiState,
    onlineRegion: com.example.data.remote.OnlineSourceRegion,
    iranianSiteFilter: com.example.data.remote.IranianMusicSiteFilter,
    onlineGenre: com.example.data.remote.OnlineTrackGenre,
    onlineSearchQuery: String,
    hurSettings: com.example.headunit.HeadunitSettings,
    aapStatus: com.example.headunit.AapConnectionStatus,
    localIp: String,
    usbState: com.example.headunit.UsbHostUiState,
    gpsTelemetry: com.example.headunit.GpsTelemetry,
    installedApps: List<com.example.headunit.InstalledCarApp>,
    onRequestLocation: () -> Unit,
    onRequestMic: (Boolean) -> Unit,
    onScanDeviceMusic: () -> Unit,
    onImportAudio: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenCarMode: () -> Unit,
    onAddToPlaylist: (Track) -> Unit
) {
    AnimatedContent(
        targetState = currentPage,
        label = "carplay_page_transition"
    ) { page ->
        when (page) {
            CarPlaySurfacePage.DASHBOARD -> {
                CarPlayDashboardView(
                    palette = palette,
                    settings = hurSettings,
                    aapStatus = aapStatus,
                    localIp = localIp,
                    usbState = usbState,
                    gpsTelemetry = gpsTelemetry,
                    currentTrack = activeTrack,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onRequestLocationPermission = onRequestLocation,
                    onOpenNavigationApp = headunitViewModel::openBestNavigation,
                    onStartReceiver = { mode -> headunitViewModel.startReceiver(mode) },
                    onOpenProjection = { headunitViewModel.navigateTo(CarPlaySurfacePage.AA_PROJECTION) },
                    onOpenMapsScreen = { headunitViewModel.navigateTo(CarPlaySurfacePage.MAPS_GPS) },
                    onOpenMusicScreen = { headunitViewModel.navigateTo(CarPlaySurfacePage.MUSIC) },
                    onOpenNowPlaying = onOpenNowPlaying,
                    onTogglePlayPause = playerViewModel::togglePlayPause,
                    onSkipNext = playerViewModel::skipNext,
                    onSkipPrevious = playerViewModel::skipPrevious
                )
            }

            CarPlaySurfacePage.APP_GRID -> {
                BackHandler {
                    headunitViewModel.navigateTo(CarPlaySurfacePage.DASHBOARD)
                }
                CarPlayAppGridView(
                    palette = palette,
                    aapStatus = aapStatus,
                    installedCount = installedApps.size,
                    onSelectPage = headunitViewModel::navigateTo,
                    onOpenMessages = headunitViewModel::openMessages
                )
            }

            CarPlaySurfacePage.AA_PROJECTION -> {
                CarPlayAndroidAutoProjectionScreen(
                    palette = palette,
                    settings = hurSettings,
                    aapStatus = aapStatus,
                    localIp = localIp,
                    usbState = usbState,
                    onAttachSurface = headunitViewModel::attachVideoSurface,
                    onStartReceiver = { mode -> headunitViewModel.startReceiver(mode) },
                    onStopReceiver = headunitViewModel::stopReceiver,
                    onRefreshUsb = headunitViewModel::refreshUsbDevices,
                    onSendTouch = headunitViewModel::sendProjectionTouch,
                    onSendSteeringKey = headunitViewModel::sendSteeringKey,
                    onOpenSettings = { headunitViewModel.navigateTo(CarPlaySurfacePage.SETTINGS) },
                    onBack = { headunitViewModel.navigateTo(CarPlaySurfacePage.DASHBOARD) }
                )
            }

            CarPlaySurfacePage.MUSIC -> {
                CarPlayMusicSuiteScreen(
                    palette = palette,
                    allTracks = allTracks,
                    filteredTracks = filteredTracks,
                    recentTracks = recentTracks,
                    albums = albums,
                    playlists = playlists,
                    currentTrackId = activeTrack?.id,
                    isPlaying = isPlaying,
                    isScanningDeviceMusic = isScanningDeviceMusic,
                    searchQuery = searchQuery,
                    libraryFilter = libraryFilter,
                    onlineFeedState = onlineFeedState,
                    onlineRegion = onlineRegion,
                    iranianSiteFilter = iranianSiteFilter,
                    onlineGenre = onlineGenre,
                    onlineSearchQuery = onlineSearchQuery,
                    onPlayDrivingMix = playerViewModel::playDrivingMix,
                    onOpenCarMode = onOpenCarMode,
                    onOpenNowPlaying = onOpenNowPlaying,
                    onScanDeviceMusic = onScanDeviceMusic,
                    onImportAudio = onImportAudio,
                    onPlayAlbum = playerViewModel::playAlbum,
                    onPlayTrack = playerViewModel::playTrackInList,
                    onToggleFavorite = playerViewModel::toggleFavorite,
                    onPlayNext = playerViewModel::addTrackToQueueNext,
                    onAddToPlaylist = onAddToPlaylist,
                    onDeleteImported = playerViewModel::deleteImportedTrack,
                    onSearchQueryChange = playerViewModel::setSearchQuery,
                    onFilterSelect = playerViewModel::setLibraryFilter,
                    onSelectRegion = playerViewModel::selectOnlineRegion,
                    onSelectIranianSite = playerViewModel::selectIranianSiteFilter,
                    onSelectGenre = playerViewModel::selectOnlineGenre,
                    onOnlineSearchQueryChange = playerViewModel::setOnlineSearchQuery,
                    onSubmitOnlineSearch = playerViewModel::loadOnlineFeed,
                    onRefreshOnline = playerViewModel::loadOnlineFeed,
                    onSaveOnlineTrack = playerViewModel::saveOnlineTrackToLibrary,
                    onCreatePlaylist = playerViewModel::createPlaylist,
                    onPlayPlaylist = playerViewModel::playPlaylist,
                    onRemoveTrackFromPlaylist = playerViewModel::removeTrackFromPlaylist,
                    onDeletePlaylist = playerViewModel::deletePlaylist,
                    onBack = { headunitViewModel.navigateTo(CarPlaySurfacePage.DASHBOARD) }
                )
            }

            CarPlaySurfacePage.MAPS_GPS -> {
                CarPlayMapsGpsScreen(
                    palette = palette,
                    gpsTelemetry = gpsTelemetry,
                    installedApps = installedApps,
                    onRequestLocationPermission = onRequestLocation,
                    onOpenBestNav = headunitViewModel::openBestNavigation,
                    onLaunchApp = headunitViewModel::launchInstalledApp,
                    onBack = { headunitViewModel.navigateTo(CarPlaySurfacePage.DASHBOARD) }
                )
            }

            CarPlaySurfacePage.PHONE -> {
                CarPlayPhoneScreen(
                    palette = palette,
                    onCallNumber = headunitViewModel::openDialer,
                    onOpenMessages = headunitViewModel::openMessages,
                    onBack = { headunitViewModel.navigateTo(CarPlaySurfacePage.DASHBOARD) }
                )
            }

            CarPlaySurfacePage.EQUALIZER -> {
                BackHandler {
                    headunitViewModel.navigateTo(CarPlaySurfacePage.DASHBOARD)
                }
                CarPlayGlassCard(
                    palette = palette,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                ) {
                    EqualizerScreen(
                        equalizerState = equalizerState,
                        onSetEnabled = playerViewModel::setEqualizerEnabled,
                        onSelectPreset = playerViewModel::selectEqualizerPreset,
                        onSetBandLevel = playerViewModel::setEqualizerBand,
                        onSetPreampDb = playerViewModel::setPreampDb,
                        onSetBassBoostPercent = playerViewModel::setBassBoostPercent,
                        onSetPlaybackSpeed = playerViewModel::setMedia3PlaybackSpeed,
                        onSetSkipSilence = playerViewModel::setMedia3SkipSilence,
                        onReset = playerViewModel::resetEqualizer
                    )
                }
            }

            CarPlaySurfacePage.CAR_APPS -> {
                CarPlayInstalledAppsScreen(
                    palette = palette,
                    installedApps = installedApps,
                    onLaunchApp = headunitViewModel::launchInstalledApp,
                    onBack = { headunitViewModel.navigateTo(CarPlaySurfacePage.APP_GRID) }
                )
            }

            CarPlaySurfacePage.SETTINGS -> {
                CarPlaySettingsScreen(
                    palette = palette,
                    settings = hurSettings,
                    localIp = localIp,
                    usbState = usbState,
                    onUpdateMode = headunitViewModel::updateConnectionMode,
                    onUpdateHostIp = headunitViewModel::updateClientHostIp,
                    onUpdatePort = headunitViewModel::updatePort,
                    onUpdateResolution = headunitViewModel::updateResolution,
                    onUpdateDpi = headunitViewModel::updateDpi,
                    onUpdateFps = headunitViewModel::updateTargetFps,
                    onUpdateCarPlayTheme = headunitViewModel::updateCarPlayTheme,
                    onUpdateDockSide = headunitViewModel::updateDockSide,
                    onUpdateHardwareDecode = headunitViewModel::updateHardwareDecode,
                    onUpdateMicPassthrough = onRequestMic,
                    onUpdateKeepScreenOn = headunitViewModel::updateKeepScreenOn,
                    onUpdateImmersiveMode = headunitViewModel::updateImmersiveMode,
                    onRefreshUsb = headunitViewModel::refreshUsbDevices,
                    onLaunchProjection = {
                        headunitViewModel.startReceiver()
                        headunitViewModel.navigateTo(CarPlaySurfacePage.AA_PROJECTION)
                    },
                    onBack = { headunitViewModel.navigateTo(CarPlaySurfacePage.DASHBOARD) }
                )
            }
        }
    }
}
