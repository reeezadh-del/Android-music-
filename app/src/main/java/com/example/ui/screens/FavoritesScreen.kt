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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.player.MusicTrack
import com.example.player.TrackDownloadStatus
import com.example.ui.components.AlbumCoverImage
import com.example.ui.components.AnimatedEqualizerBars
import com.example.ui.components.AvaSearchField
import com.example.ui.components.EditorialPageHeader
import com.example.ui.components.TrackDownloadButton
import com.example.ui.components.formatDuration
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.viewmodel.FavoritesUiState

@Composable
fun FavoritesScreen(
    uiState: FavoritesUiState,
    searchQuery: String,
    currentTrackId: String?,
    isPlaying: Boolean,
    isGuestMode: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onVoiceSearchClick: () -> Unit,
    onPlayTrack: (MusicTrack) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onRemoveFavorite: (MusicTrack) -> Unit,
    onRetryCloud: () -> Unit,
    onUpgradeToGoogle: () -> Unit,
    modifier: Modifier = Modifier,
    downloadStates: Map<String, TrackDownloadStatus> = emptyMap(),
    onDownloadTrack: ((MusicTrack) -> Unit)? = null
) {
    val tracks: List<MusicTrack>
    val totalCount: Int
    val isCloudBacked: Boolean
    val isSyncing: Boolean
    val errorMessage: String?

    when (uiState) {
        is FavoritesUiState.Loading -> {
            tracks = emptyList()
            totalCount = 0
            isCloudBacked = !isGuestMode
            isSyncing = true
            errorMessage = null
        }
        is FavoritesUiState.Success -> {
            tracks = uiState.tracks
            totalCount = uiState.totalCount
            isCloudBacked = uiState.isCloudBacked
            isSyncing = uiState.isSyncing
            errorMessage = null
        }
        is FavoritesUiState.Error -> {
            tracks = uiState.fallbackTracks
            totalCount = uiState.fallbackTracks.size
            isCloudBacked = uiState.isCloudBacked
            isSyncing = false
            errorMessage = uiState.message
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("favorites_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        EditorialPageHeader(
            title = stringResource(R.string.favorites_screen_title),
            subtitle = stringResource(R.string.favorites_count_format, totalCount),
            modifier = Modifier.padding(top = 10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(50)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (isCloudBacked) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                            contentDescription = null,
                            tint = if (isCloudBacked) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = when {
                            isSyncing && isCloudBacked -> stringResource(R.string.favorites_cloud_syncing)
                            isCloudBacked -> stringResource(R.string.favorites_cloud_synced)
                            else -> stringResource(R.string.favorites_guest_local_badge)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (tracks.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPlayAll,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("favorites_play_all_button")
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.favorites_play_all))
                }
                OutlinedButton(
                    onClick = onShuffleAll,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("favorites_shuffle_all_button")
                ) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.favorites_shuffle_all))
                }
            }
        }

        // Guest Mode cloud upgrade hint banner
        if (isGuestMode) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.favorites_guest_cloud_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = onUpgradeToGoogle,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("favorites_upgrade_google_button")
                    ) {
                        Text("ورود با گوگل", maxLines = 1)
                    }
                }
            }
        }

        // Cloud error banner with retry (without hiding cached favorites)
        if (errorMessage != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("favorites_error_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = onRetryCloud,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("favorites_retry_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.favorites_retry))
                    }
                }
            }
        }

        // Pill Search Bar
        AvaSearchField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholderText = stringResource(R.string.favorites_search_hint),
            voiceContentDescription = stringResource(R.string.favorites_voice_search_cd),
            onVoiceSearchClick = onVoiceSearchClick,
            inputTestTag = "favorites_search_input",
            voiceTestTag = "voice_search_favorites_button"
        )

        // Content List / Loading / Empty
        when {
            uiState is FavoritesUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.favorites_cloud_syncing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            tracks.isEmpty() -> {
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
                            .testTag("favorites_empty_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Favorite,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = if (searchQuery.isNotBlank()) {
                                    "موردی با این عنوان در علاقه‌مندی‌ها پیدا نشد"
                                } else {
                                    stringResource(R.string.empty_favorites_title)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = if (searchQuery.isNotBlank()) {
                                    "عبارت جستجو را تغییر دهید یا فیلتر را پاک کنید."
                                } else {
                                    stringResource(R.string.empty_favorites_subtitle)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tracks, key = { it.id }) { track ->
                        FavoriteTrackRowItem(
                            track = track,
                            isCurrent = currentTrackId == track.id,
                            isPlaying = isPlaying,
                            onClick = { onPlayTrack(track) },
                            onRemoveFavorite = { onRemoveFavorite(track) },
                            downloadStatus = downloadStates[track.id],
                            onDownloadClick = if (onDownloadTrack != null && (track.isOnline || track.isDownloaded)) {
                                { onDownloadTrack(track) }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteTrackRowItem(
    track: MusicTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onRemoveFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    downloadStatus: TrackDownloadStatus? = null,
    onDownloadClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(18.dp)
    val ink = MaterialTheme.colorScheme.primary
    val crimson = MaterialTheme.colorScheme.secondary
    val containerColor = if (isCurrent) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.background
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .then(
                if (isCurrent) {
                    Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                        shape
                    )
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp)
            .testTag("favorite_row_${track.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            AlbumCoverImage(
                artworkUri = track.artworkUri,
                contentDescription = track.title,
                modifier = Modifier
                    .size(54.dp)
                    .border(
                        width = if (isCurrent) 1.4.dp else 0.8.dp,
                        color = if (isCurrent) crimson.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(14.dp)
                    ),
                shape = RoundedCornerShape(14.dp)
            )
            if (isCurrent && isPlaying) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedEqualizerBars(barColor = Color.White, maxHeight = 16.dp)
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isCurrent) crimson else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (track.durationMs > 0L) {
                    Text(
                        text = "• ${formatDuration(track.durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = OutfitFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1
                    )
                }
            }
        }

        if (onDownloadClick != null) {
            TrackDownloadButton(
                status = downloadStatus,
                isDownloaded = track.isDownloaded,
                onClick = onDownloadClick,
                testTag = "favorites_download_btn_${track.id}"
            )
        }

        IconButton(
            onClick = onRemoveFavorite,
            modifier = Modifier
                .size(48.dp)
                .testTag("favorites_remove_btn_${track.id}")
        ) {
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = stringResource(R.string.favorites_remove_cd),
                tint = crimson,
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isCurrent) ink else MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isCurrent && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isCurrent && isPlaying) {
                        stringResource(R.string.cd_pause)
                    } else {
                        stringResource(R.string.cd_play)
                    },
                    tint = if (isCurrent) MaterialTheme.colorScheme.onPrimary else ink,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}
