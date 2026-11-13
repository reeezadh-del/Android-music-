package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.model.Track
import com.example.ui.components.AddToPlaylistSheet
import com.example.ui.components.MiniPlayerBar
import com.example.ui.screens.AutoHubScreen
import com.example.ui.screens.CarModeScreen
import com.example.ui.screens.CreatePlaylistDialog
import com.example.ui.screens.EqualizerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.OnlineMusicScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.theme.AvaPlayerTheme
import com.example.ui.viewmodel.AvaPlayerViewModel
import kotlinx.serialization.Serializable

@Serializable
sealed interface AvaRoute {
    @Serializable
    data object Home : AvaRoute

    @Serializable
    data object Online : AvaRoute

    @Serializable
    data object Library : AvaRoute

    @Serializable
    data object Equalizer : AvaRoute

    @Serializable
    data object Playlists : AvaRoute

    @Serializable
    data object AutoHub : AvaRoute
}

private data class TopLevelDestination(
    val route: AvaRoute,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AvaPlayerTheme(darkTheme = true) {
                AvaPlayerApp()
            }
        }
    }
}

@Composable
fun AvaPlayerApp(
    viewModel: AvaPlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val allTracks by viewModel.allTracks.collectAsStateWithLifecycle()
    val filteredTracks by viewModel.filteredTracks.collectAsStateWithLifecycle()
    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val recentTracks by viewModel.recentTracks.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val libraryFilter by viewModel.libraryFilter.collectAsStateWithLifecycle()
    val autoBrowseNodes by viewModel.autoBrowseTreePreview.collectAsStateWithLifecycle()
    val equalizerState by viewModel.equalizerState.collectAsStateWithLifecycle()
    val sleepTimerMinutes by viewModel.sleepTimerRemainingMinutes.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val onlineFeedState by viewModel.onlineFeedState.collectAsStateWithLifecycle()
    val onlineRegion by viewModel.onlineRegion.collectAsStateWithLifecycle()
    val iranianSiteFilter by viewModel.iranianSiteFilter.collectAsStateWithLifecycle()
    val onlineGenre by viewModel.onlineGenre.collectAsStateWithLifecycle()
    val onlineSearchQuery by viewModel.onlineSearchQuery.collectAsStateWithLifecycle()
    val isScanningDeviceMusic by viewModel.isScanningDeviceMusic.collectAsStateWithLifecycle()

    var isNowPlayingOpen by rememberSaveable { mutableStateOf(false) }
    var isCarModeOpen by rememberSaveable { mutableStateOf(false) }
    var trackForPlaylistSheet by remember { mutableStateOf<Track?>(null) }
    var showCreatePlaylistFromSheet by remember { mutableStateOf(false) }

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
                viewModel.scanDeviceMusic(showBannerOnResult = true)
            }
        }
    )

    val triggerDeviceMusicScan: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            audioPermission
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.scanDeviceMusic(showBannerOnResult = true)
        } else {
            permissionsLauncher.launch(arrayOf(audioPermission))
        }
    }

    LaunchedEffect(Unit) {
        val audioGranted = ContextCompat.checkSelfPermission(
            context,
            audioPermission
        ) == PackageManager.PERMISSION_GRANTED

        if (audioGranted) {
            viewModel.scanDeviceMusic(showBannerOnResult = false)
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

    // Zero-permission SAF Audio Picker
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                viewModel.importAudioUris(uris)
            }
        }
    )

    LaunchedEffect(statusBanner) {
        statusBanner?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    val destinations = remember {
        listOf(
            TopLevelDestination(
                route = AvaRoute.Home,
                labelRes = R.string.nav_home,
                selectedIcon = Icons.Rounded.Home,
                unselectedIcon = Icons.Outlined.Home,
                testTag = "nav_home"
            ),
            TopLevelDestination(
                route = AvaRoute.Online,
                labelRes = R.string.nav_online,
                selectedIcon = Icons.Rounded.Public,
                unselectedIcon = Icons.Outlined.Public,
                testTag = "nav_online"
            ),
            TopLevelDestination(
                route = AvaRoute.Library,
                labelRes = R.string.nav_library,
                selectedIcon = Icons.Rounded.LibraryMusic,
                unselectedIcon = Icons.Outlined.LibraryMusic,
                testTag = "nav_library"
            ),
            TopLevelDestination(
                route = AvaRoute.Equalizer,
                labelRes = R.string.nav_equalizer,
                selectedIcon = Icons.Rounded.Equalizer,
                unselectedIcon = Icons.Outlined.Equalizer,
                testTag = "nav_equalizer"
            ),
            TopLevelDestination(
                route = AvaRoute.AutoHub,
                labelRes = R.string.nav_auto,
                selectedIcon = Icons.Rounded.DirectionsCar,
                unselectedIcon = Icons.Outlined.DirectionsCar,
                testTag = "nav_auto"
            )
        )
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val activeTrack = playbackState.currentTrack
    val progress = if (playbackState.durationMs > 0L) {
        playbackState.currentPositionMs.toFloat() / playbackState.durationMs.toFloat()
    } else 0f

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 700.dp

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useNavRail) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.navigationBars)
                        ) {
                            if (activeTrack != null) {
                                MiniPlayerBar(
                                    track = activeTrack,
                                    isPlaying = playbackState.isPlaying,
                                    progress = progress,
                                    onExpandClick = { isNowPlayingOpen = true },
                                    onTogglePlayPause = viewModel::togglePlayPause,
                                    onSkipNext = viewModel::skipNext,
                                    onToggleFavorite = { viewModel.toggleFavorite(activeTrack.id) }
                                )
                            }
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                windowInsets = WindowInsets(0, 0, 0, 0)
                            ) {
                                destinations.forEach { dest ->
                                    val selected = currentDestination?.hasRoute(dest.route::class) == true
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            navController.navigate(dest.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = stringResource(dest.labelRes)
                                            )
                                        },
                                        label = { Text(stringResource(dest.labelRes)) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                        ),
                                        modifier = Modifier.testTag(dest.testTag)
                                    )
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            destinations.forEach { dest ->
                                val selected = currentDestination?.hasRoute(dest.route::class) == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(dest.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        NavHost(
                            navController = navController,
                            startDestination = AvaRoute.Home,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            composable<AvaRoute.Home> {
                                HomeScreen(
                                    allTracks = allTracks,
                                    recentTracks = recentTracks,
                                    albums = albums,
                                    currentTrackId = activeTrack?.id,
                                    isPlaying = playbackState.isPlaying,
                                    isScanningDeviceMusic = isScanningDeviceMusic,
                                    onPlayDrivingMix = viewModel::playDrivingMix,
                                    onOpenCarMode = { isCarModeOpen = true },
                                    onScanDeviceMusic = triggerDeviceMusicScan,
                                    onImportAudio = {
                                        audioPickerLauncher.launch(arrayOf("audio/*"))
                                    },
                                    onPlayAlbum = viewModel::playAlbum,
                                    onPlayTrack = viewModel::playTrackInList,
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onPlayNext = viewModel::addTrackToQueueNext,
                                    onAddToPlaylist = { track -> trackForPlaylistSheet = track },
                                    onDeleteImported = viewModel::deleteImportedTrack
                                )
                            }

                            composable<AvaRoute.Online> {
                                val savedIds = remember(allTracks) {
                                    allTracks.map { it.id }.toSet()
                                }
                                OnlineMusicScreen(
                                    feedState = onlineFeedState,
                                    sourceRegion = onlineRegion,
                                    selectedIranianSite = iranianSiteFilter,
                                    selectedGenre = onlineGenre,
                                    searchQuery = onlineSearchQuery,
                                    savedTrackIds = savedIds,
                                    currentTrackId = activeTrack?.id,
                                    isPlaying = playbackState.isPlaying,
                                    onSelectRegion = viewModel::selectOnlineRegion,
                                    onSelectIranianSite = viewModel::selectIranianSiteFilter,
                                    onSelectGenre = viewModel::selectOnlineGenre,
                                    onSearchQueryChange = viewModel::setOnlineSearchQuery,
                                    onSubmitSearch = viewModel::loadOnlineFeed,
                                    onRefresh = viewModel::loadOnlineFeed,
                                    onPlayTrack = viewModel::playTrackInList,
                                    onPlayNext = viewModel::addTrackToQueueNext,
                                    onSaveToLibrary = viewModel::saveOnlineTrackToLibrary
                                )
                            }

                            composable<AvaRoute.Library> {
                                LibraryScreen(
                                    tracks = filteredTracks,
                                    searchQuery = searchQuery,
                                    activeFilter = libraryFilter,
                                    currentTrackId = activeTrack?.id,
                                    isPlaying = playbackState.isPlaying,
                                    onSearchQueryChange = viewModel::setSearchQuery,
                                    onFilterSelect = viewModel::setLibraryFilter,
                                    onScanDeviceMusic = triggerDeviceMusicScan,
                                    onImportAudio = {
                                        audioPickerLauncher.launch(arrayOf("audio/*"))
                                    },
                                    onOpenPlaylists = {
                                        navController.navigate(AvaRoute.Playlists) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onPlayTrack = viewModel::playTrackInList,
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onPlayNext = viewModel::addTrackToQueueNext,
                                    onAddToPlaylist = { track -> trackForPlaylistSheet = track },
                                    onDeleteImported = viewModel::deleteImportedTrack
                                )
                            }

                            composable<AvaRoute.Equalizer> {
                                EqualizerScreen(
                                    equalizerState = equalizerState,
                                    onSetEnabled = viewModel::setEqualizerEnabled,
                                    onSelectPreset = viewModel::selectEqualizerPreset,
                                    onSetBandLevel = viewModel::setEqualizerBand,
                                    onSetPreampDb = viewModel::setPreampDb,
                                    onSetBassBoostPercent = viewModel::setBassBoostPercent,
                                    onSetPlaybackSpeed = viewModel::setMedia3PlaybackSpeed,
                                    onSetSkipSilence = viewModel::setMedia3SkipSilence,
                                    onReset = viewModel::resetEqualizer
                                )
                            }

                            composable<AvaRoute.Playlists> {
                                PlaylistsScreen(
                                    playlists = playlists,
                                    allTracks = allTracks,
                                    onCreatePlaylist = viewModel::createPlaylist,
                                    onPlayPlaylist = viewModel::playPlaylist,
                                    onPlayTrackInPlaylist = viewModel::playTrackInList,
                                    onRemoveTrackFromPlaylist = viewModel::removeTrackFromPlaylist,
                                    onDeletePlaylist = viewModel::deletePlaylist,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<AvaRoute.AutoHub> {
                                AutoHubScreen(
                                    isServiceConnected = playbackState.isServiceConnected,
                                    browseNodes = autoBrowseNodes,
                                    allTracks = allTracks,
                                    favoriteTracks = favoriteTracks,
                                    albums = albums,
                                    playlists = playlists,
                                    equalizerState = equalizerState,
                                    sleepTimerRemainingMinutes = sleepTimerMinutes,
                                    onOpenCarMode = { isCarModeOpen = true },
                                    onOpenEqualizer = {
                                        navController.navigate(AvaRoute.Equalizer) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onPlayTrack = viewModel::playTrackInList,
                                    onPlayAlbum = viewModel::playAlbum,
                                    onPlayPlaylist = viewModel::playPlaylist,
                                    onSetSleepTimer = viewModel::setSleepTimer
                                )
                            }
                        }

                        if (useNavRail && activeTrack != null) {
                            MiniPlayerBar(
                                track = activeTrack,
                                isPlaying = playbackState.isPlaying,
                                progress = progress,
                                onExpandClick = { isNowPlayingOpen = true },
                                onTogglePlayPause = viewModel::togglePlayPause,
                                onSkipNext = viewModel::skipNext,
                                onToggleFavorite = { viewModel.toggleFavorite(activeTrack.id) },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
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
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onSkipNext = viewModel::skipNext,
                    onSkipPrevious = viewModel::skipPrevious,
                    onSeekTo = viewModel::seekTo,
                    onToggleShuffle = viewModel::toggleShuffle,
                    onCycleRepeat = viewModel::cycleRepeatMode,
                    onToggleFavorite = { viewModel.toggleFavorite(activeTrack.id) },
                    onSelectQueueItem = viewModel::playQueueItemAt,
                    onOpenCarMode = {
                        isNowPlayingOpen = false
                        isCarModeOpen = true
                    },
                    onOpenEqualizer = {
                        isNowPlayingOpen = false
                        navController.navigate(AvaRoute.Equalizer) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
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
                onTogglePlayPause = viewModel::togglePlayPause,
                onSkipNext = viewModel::skipNext,
                onSkipPrevious = viewModel::skipPrevious,
                onToggleFavorite = viewModel::toggleFavorite
            )
        }
    }

    val pendingTrack = trackForPlaylistSheet
    if (pendingTrack != null) {
        AddToPlaylistSheet(
            track = pendingTrack,
            playlists = playlists,
            onSelectPlaylist = { playlist ->
                viewModel.addTrackToPlaylist(playlist.id, pendingTrack.id, playlist.name)
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
                viewModel.createPlaylist(name)
                showCreatePlaylistFromSheet = false
            },
            onDismiss = { showCreatePlaylistFromSheet = false }
        )
    }
}
