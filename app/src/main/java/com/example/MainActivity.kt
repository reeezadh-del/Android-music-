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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.player.AppTab
import com.example.player.TrackFilter
import com.example.ui.components.FullNowPlayingSheet
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.TrackRowItem
import com.example.ui.screens.TrendingIranianScreen
import com.example.ui.theme.AvaPlayerTheme
import com.example.ui.viewmodel.MusicPlayerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AvaPlayerTheme(darkTheme = true) {
                SimpleMusicPlayerScreen()
            }
        }
    }
}

@Composable
fun SimpleMusicPlayerScreen(
    viewModel: MusicPlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val tracks by viewModel.filteredTracks.collectAsStateWithLifecycle()
    val allTracks by viewModel.allTracks.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val activeFilter by viewModel.activeFilter.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    val trendingTracks by viewModel.trendingTracks.collectAsStateWithLifecycle()
    val trendingCategory by viewModel.trendingCategory.collectAsStateWithLifecycle()
    val trendingPreset by viewModel.trendingPreset.collectAsStateWithLifecycle()
    val trendingSearchQuery by viewModel.trendingSearchQuery.collectAsStateWithLifecycle()
    val isLoadingTrending by viewModel.isLoadingTrending.collectAsStateWithLifecycle()
    val trendingError by viewModel.trendingError.collectAsStateWithLifecycle()

    var isNowPlayingOpen by rememberSaveable { mutableStateOf(false) }

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
                viewModel.scanDeviceMusic(showResultMessage = true)
            }
        }
    )

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                viewModel.importAudioFiles(uris)
            }
        }
    )

    val triggerScan: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            audioPermission
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.scanDeviceMusic(showResultMessage = true)
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
            viewModel.scanDeviceMusic(showResultMessage = false)
        } else {
            val toRequest = buildList {
                add(audioPermission)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val notifGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!notifGranted) add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            permissionsLauncher.launch(toRequest.toTypedArray())
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    val activeTrack = playerState.currentTrack
    val progress = if (playerState.durationMs > 0L) {
        (playerState.positionMs.toFloat() / playerState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (activeTrack != null) {
                        MiniPlayerBar(
                            track = activeTrack,
                            isPlaying = playerState.isPlaying,
                            progress = progress,
                            onExpand = { isNowPlayingOpen = true },
                            onTogglePlayPause = viewModel::togglePlayPause,
                            onSkipNext = viewModel::skipNext,
                            applyNavigationBarPadding = false
                        )
                    }
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        NavigationBarItem(
                            selected = activeTab == AppTab.LIBRARY,
                            onClick = { viewModel.selectTab(AppTab.LIBRARY) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.LibraryMusic,
                                    contentDescription = stringResource(R.string.tab_library)
                                )
                            },
                            label = { Text(stringResource(R.string.tab_library)) },
                            modifier = Modifier.testTag("nav_tab_library")
                        )
                        NavigationBarItem(
                            selected = activeTab == AppTab.TRENDING,
                            onClick = { viewModel.selectTab(AppTab.TRENDING) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.Whatshot,
                                    contentDescription = stringResource(R.string.tab_trending)
                                )
                            },
                            label = { Text(stringResource(R.string.tab_trending)) },
                            modifier = Modifier.testTag("nav_tab_trending")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp)
                ) {
                    when (activeTab) {
                        AppTab.LIBRARY -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Header
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.MusicNote,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = stringResource(R.string.music_player_title),
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${allTracks.size} آهنگ",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = triggerScan,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("scan_music_button")
                                        ) {
                                            if (isScanning) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Rounded.Refresh,
                                                    contentDescription = stringResource(R.string.action_scan_music)
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("add_audio_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Add,
                                                contentDescription = stringResource(R.string.action_add_files)
                                            )
                                        }
                                    }
                                }

                                // Search Bar
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = viewModel::setSearchQuery,
                                    placeholder = { Text(stringResource(R.string.search_hint)) },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Search, contentDescription = null)
                                    },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                                Icon(Icons.Rounded.Close, contentDescription = "پاک کردن")
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("search_music_input")
                                )

                                // Filter Chips
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TrackFilter.entries.forEach { filter ->
                                        FilterChip(
                                            selected = activeFilter == filter,
                                            onClick = { viewModel.setFilter(filter) },
                                            label = { Text(filter.labelFa) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (filter == TrackFilter.FAVORITES) {
                                                        Icons.Rounded.Favorite
                                                    } else {
                                                        Icons.Rounded.LibraryMusic
                                                    },
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            modifier = Modifier.testTag("filter_${filter.name}")
                                        )
                                    }
                                }

                                // Song List or Empty State
                                if (tracks.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(24.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 16.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(24.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (activeFilter == TrackFilter.FAVORITES) {
                                                        Icons.Rounded.Favorite
                                                    } else {
                                                        Icons.Rounded.MusicNote
                                                    },
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(48.dp)
                                                )
                                                Text(
                                                    text = if (activeFilter == TrackFilter.FAVORITES) {
                                                        stringResource(R.string.empty_favorites_title)
                                                    } else {
                                                        stringResource(R.string.empty_title)
                                                    },
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center
                                                )
                                                Text(
                                                    text = if (activeFilter == TrackFilter.FAVORITES) {
                                                        stringResource(R.string.empty_favorites_subtitle)
                                                    } else {
                                                        stringResource(R.string.empty_subtitle)
                                                    },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textAlign = TextAlign.Center
                                                )

                                                if (activeFilter == TrackFilter.ALL) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Button(
                                                        onClick = triggerScan,
                                                        shape = RoundedCornerShape(14.dp),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(48.dp)
                                                            .testTag("empty_scan_button")
                                                    ) {
                                                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(stringResource(R.string.action_scan_music))
                                                    }
                                                    OutlinedButton(
                                                        onClick = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                                                        shape = RoundedCornerShape(14.dp),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(48.dp)
                                                            .testTag("empty_pick_file_button")
                                                    ) {
                                                        Icon(Icons.Rounded.Add, contentDescription = null)
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(stringResource(R.string.action_add_files))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(bottom = 24.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(tracks, key = { it.id }) { track ->
                                            TrackRowItem(
                                                track = track,
                                                isCurrent = activeTrack?.id == track.id,
                                                isPlaying = playerState.isPlaying,
                                                onClick = { viewModel.playTrack(track) },
                                                onToggleFavorite = { viewModel.toggleFavorite(track) },
                                                onRemoveImported = if (track.isImported) {
                                                    { viewModel.removeImportedTrack(track) }
                                                } else null
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        AppTab.TRENDING -> {
                            TrendingIranianScreen(
                                tracks = trendingTracks,
                                selectedCategory = trendingCategory,
                                selectedPreset = trendingPreset,
                                searchQuery = trendingSearchQuery,
                                isLoading = isLoadingTrending,
                                errorMessage = trendingError,
                                currentTrackId = activeTrack?.id,
                                isPlaying = playerState.isPlaying,
                                onSelectCategory = viewModel::setTrendingCategory,
                                onSelectPreset = viewModel::setTrendingPreset,
                                onSearchQueryChange = viewModel::setTrendingSearchQuery,
                                onSubmitSearch = viewModel::submitTrendingSearch,
                                onClearSearch = viewModel::clearTrendingSearch,
                                onRefresh = { viewModel.loadTrendingTracks(forceRefresh = true) },
                                onPlayTrack = viewModel::playTrendingTrack,
                                onToggleFavorite = viewModel::toggleFavorite
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isNowPlayingOpen && activeTrack != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            if (activeTrack != null) {
                FullNowPlayingSheet(
                    track = activeTrack,
                    isPlaying = playerState.isPlaying,
                    positionMs = playerState.positionMs,
                    durationMs = playerState.durationMs,
                    shuffleEnabled = playerState.shuffleEnabled,
                    repeatMode = playerState.repeatMode,
                    onCollapse = { isNowPlayingOpen = false },
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onSkipNext = viewModel::skipNext,
                    onSkipPrevious = viewModel::skipPrevious,
                    onSeekTo = viewModel::seekTo,
                    onToggleShuffle = viewModel::toggleShuffle,
                    onCycleRepeat = viewModel::cycleRepeatMode,
                    onToggleFavorite = { viewModel.toggleFavorite(activeTrack) }
                )
            }
        }
    }
}
