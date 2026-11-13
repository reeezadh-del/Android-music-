package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Track
import com.example.data.remote.IranianMusicSiteFilter
import com.example.data.remote.OnlineSourceRegion
import com.example.data.remote.OnlineTrackGenre
import com.example.ui.components.AlbumArtwork
import com.example.ui.components.AnimatedEqualizerBars
import com.example.ui.components.formatDuration
import com.example.ui.theme.AutoTurquoise
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.viewmodel.OnlineFeedUiState

@Composable
fun OnlineMusicScreen(
    feedState: OnlineFeedUiState,
    sourceRegion: OnlineSourceRegion,
    selectedIranianSite: IranianMusicSiteFilter,
    selectedGenre: OnlineTrackGenre,
    searchQuery: String,
    savedTrackIds: Set<String>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onSelectRegion: (OnlineSourceRegion) -> Unit,
    onSelectIranianSite: (IranianMusicSiteFilter) -> Unit,
    onSelectGenre: (OnlineTrackGenre) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onRefresh: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayNext: (Track) -> Unit,
    onSaveToLibrary: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.online_title),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Surface(
                        color = AutoTurquoise.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(50.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Public,
                                contentDescription = null,
                                tint = AutoTurquoise,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (sourceRegion == OnlineSourceRegion.IRANIAN_SITES) {
                                    "سایت‌های ایرانی"
                                } else {
                                    "Web P2P"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = AutoTurquoise
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.online_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("online_refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.online_retry),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Region Filter Switcher (Iranian Sites vs Global Web Music)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OnlineSourceRegion.entries.forEach { region ->
                val selected = region == sourceRegion
                Surface(
                    onClick = { onSelectRegion(region) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("online_region_${region.name}")
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (region == OnlineSourceRegion.IRANIAN_SITES) {
                                Icons.Rounded.Language
                            } else {
                                Icons.Rounded.MusicNote
                            },
                            contentDescription = null,
                            tint = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = region.labelFa,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Web Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("online_search_input"),
            placeholder = { Text(stringResource(R.string.online_search_hint)) },
            leadingIcon = {
                IconButton(onClick = {
                    keyboardController?.hide()
                    onSubmitSearch()
                }) {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                }
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        onSearchQueryChange("")
                        onSubmitSearch()
                    }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_close))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboardController?.hide()
                    onSubmitSearch()
                }
            ),
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Site Filter Chips (Iranian Sites) or Global Genre Chips
        if (sourceRegion == OnlineSourceRegion.IRANIAN_SITES) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(IranianMusicSiteFilter.entries) { site ->
                    FilterChip(
                        selected = site == selectedIranianSite,
                        onClick = { onSelectIranianSite(site) },
                        label = { Text(site.labelFa) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AutoTurquoise.copy(alpha = 0.22f),
                            selectedLabelColor = AutoTurquoise
                        ),
                        modifier = Modifier.testTag("online_ir_site_${site.name}")
                    )
                }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(OnlineTrackGenre.entries) { genre ->
                    FilterChip(
                        selected = genre == selectedGenre && searchQuery.isBlank(),
                        onClick = { onSelectGenre(genre) },
                        label = { Text(genre.labelFa) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("online_genre_${genre.name}")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Content State
        when (feedState) {
            is OnlineFeedUiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.online_loading),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            is OnlineFeedUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.online_error_title),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = feedState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onRefresh,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("online_retry_button")
                    ) {
                        Text(stringResource(R.string.online_retry))
                    }
                }
            }

            is OnlineFeedUiState.Success -> {
                if (feedState.tracks.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.empty_search_title),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "عبارت دیگری جستجو کنید یا فیلتر سایت/سبک را تغییر دهید.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 108.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(feedState.tracks, key = { it.id }) { track ->
                            val isCurrent = track.id == currentTrackId
                            val isSaved = savedTrackIds.contains(track.id)
                            OnlineTrackCard(
                                track = track,
                                isCurrent = isCurrent,
                                isPlaying = isPlaying && isCurrent,
                                isSaved = isSaved,
                                onPlayClick = { onPlayTrack(track, feedState.tracks) },
                                onPlayNext = { onPlayNext(track) },
                                onSaveToLibrary = { onSaveToLibrary(track) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineTrackCard(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isSaved: Boolean,
    onPlayClick: () -> Unit,
    onPlayNext: () -> Unit,
    onSaveToLibrary: () -> Unit
) {
    val containerBrush = if (isCurrent) {
        Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                MaterialTheme.colorScheme.surfaceContainerHigh
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceContainerLow,
                MaterialTheme.colorScheme.surfaceContainerLow
            )
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(containerBrush)
            .border(
                1.dp,
                if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onPlayClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("online_track_${track.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(56.dp)) {
            AlbumArtwork(
                artworkUri = track.artworkUri,
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(12.dp)
            )
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.52f)),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedEqualizerBars(isPlaying = isPlaying)
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (track.durationMs > 0L) {
                    Text(
                        text = "• ${formatDuration(track.durationMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = OutfitFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Surface(
                        color = AutoTurquoise.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "MP3",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = OutfitFontFamily,
                            color = AutoTurquoise,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = onPlayNext,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("online_queue_${track.id}")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = stringResource(R.string.action_play_next),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(
            onClick = onSaveToLibrary,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("online_save_${track.id}")
        ) {
            Icon(
                imageVector = if (isSaved) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                contentDescription = stringResource(R.string.online_save_to_library),
                tint = if (isSaved) AutoTurquoise else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
