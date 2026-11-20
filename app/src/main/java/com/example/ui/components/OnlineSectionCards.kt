package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.player.MusicTrack
import com.example.player.OnlineSubSection
import com.example.player.TrackDownloadStatus
import com.example.player.TrackReactionSummary
import com.example.player.TrackReactionType
import com.example.ui.theme.OutfitFontFamily

@Composable
fun OnlineStudioHeader(
    subSection: OnlineSubSection,
    tracksCount: Int,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = false,
    onToggleTheme: (() -> Unit)? = null
) {
    EditorialPageHeader(
        title = stringResource(R.string.online_title),
        subtitle = if (tracksCount > 0) "$tracksCount قطعه • ${subSection.labelFa}" else subSection.labelFa,
        modifier = modifier
    ) {
        if (onToggleTheme != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .size(48.dp)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        CircleShape
                    )
            ) {
                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("online_theme_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                        contentDescription = stringResource(
                            if (isDarkTheme) R.string.theme_toggle_to_light else R.string.theme_toggle_to_dark
                        ),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(48.dp)
        ) {
            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("online_refresh_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.trending_refresh),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun OnlineSegmentedSwitcher(
    subSection: OnlineSubSection,
    onSelectSubSection: (OnlineSubSection) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(50)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OnlineSubSection.entries.forEach { tab ->
                val selected = subSection == tab
                val containerColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    animationSpec = tween(durationMillis = 200),
                    label = "subtab_bg_${tab.name}"
                )
                val fgColor = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(50))
                        .background(containerColor)
                        .clickable { onSelectSubSection(tab) }
                        .testTag("online_subtab_${tab.name}"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = when (tab) {
                                OnlineSubSection.TRENDING -> Icons.Rounded.Whatshot
                                OnlineSubSection.DISCOVER -> Icons.Rounded.Explore
                                OnlineSubSection.YOUTUBE_MUSIC -> Icons.Rounded.PlayCircle
                            },
                            contentDescription = null,
                            tint = fgColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = tab.labelFa,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = fgColor,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FeaturedOnlineSpotlightCard(
    track: MusicTrack,
    subSection: OnlineSubSection,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isResolvingStream: Boolean = false,
    socialSummary: TrackReactionSummary,
    onPlayClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleReaction: (TrackReactionType) -> Unit,
    onOpenComments: () -> Unit,
    modifier: Modifier = Modifier,
    downloadStatus: TrackDownloadStatus? = null,
    onDownloadClick: (() -> Unit)? = null
) {
    val ink = MaterialTheme.colorScheme.primary
    val onInk = MaterialTheme.colorScheme.onPrimary
    val crimson = MaterialTheme.colorScheme.secondary
    val shape = RoundedCornerShape(22.dp)
    val rowTagPrefix = when (subSection) {
        OnlineSubSection.TRENDING -> "trending"
        OnlineSubSection.DISCOVER -> "online"
        OnlineSubSection.YOUTUBE_MUSIC -> "yt"
    }

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (isCurrent) 1.5.dp else 1.dp,
                color = if (isCurrent) crimson else MaterialTheme.colorScheme.outlineVariant,
                shape = shape
            )
            .clickable(onClick = onPlayClick)
            .testTag("${rowTagPrefix}_row_${track.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = ink
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = onInk,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = when (subSection) {
                                OnlineSubSection.TRENDING -> "#1 • ${stringResource(R.string.online_featured_trending)}"
                                OnlineSubSection.DISCOVER -> stringResource(R.string.online_featured_discover)
                                OnlineSubSection.YOUTUBE_MUSIC -> "#1 • ${stringResource(R.string.ytm_featured_badge)}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = onInk
                        )
                    }
                }

                if (track.durationMs > 0L) {
                    Text(
                        text = formatDuration(track.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = OutfitFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AlbumCoverImage(
                        artworkUri = track.artworkUri,
                        contentDescription = track.title,
                        modifier = Modifier.size(84.dp),
                        shape = RoundedCornerShape(18.dp)
                    )
                    if (isCurrent && isPlaying) {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedEqualizerBars(barColor = Color.White, maxHeight = 20.dp)
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrent) crimson else MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (onDownloadClick != null) {
                    TrackDownloadButton(
                        status = downloadStatus,
                        isDownloaded = track.isDownloaded,
                        onClick = onDownloadClick,
                        testTag = "${rowTagPrefix}_download_btn_${track.id}"
                    )
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("${rowTagPrefix}_fav_btn_${track.id}")
                ) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = stringResource(R.string.cd_favorite),
                        tint = if (track.isFavorite) crimson else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    onClick = onPlayClick,
                    shape = CircleShape,
                    color = ink,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isResolvingStream) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = onInk
                            )
                        } else {
                            Icon(
                                imageVector = if (isCurrent && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isCurrent && isPlaying) stringResource(R.string.cd_pause) else stringResource(R.string.cd_play),
                                tint = onInk,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            TrackSocialBar(
                trackId = track.id,
                summary = socialSummary,
                onToggleReaction = onToggleReaction,
                onOpenComments = onOpenComments
            )
        }
    }
}

@Composable
fun RedesignedOnlineTrackCard(
    rank: Int?,
    track: MusicTrack,
    subSection: OnlineSubSection,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isResolvingStream: Boolean = false,
    socialSummary: TrackReactionSummary,
    onPlayClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleReaction: (TrackReactionType) -> Unit,
    onOpenComments: () -> Unit,
    modifier: Modifier = Modifier,
    downloadStatus: TrackDownloadStatus? = null,
    onDownloadClick: (() -> Unit)? = null
) {
    val ink = MaterialTheme.colorScheme.primary
    val onInk = MaterialTheme.colorScheme.onPrimary
    val crimson = MaterialTheme.colorScheme.secondary
    val shape = RoundedCornerShape(18.dp)
    val rowTagPrefix = when (subSection) {
        OnlineSubSection.TRENDING -> "trending"
        OnlineSubSection.DISCOVER -> "online"
        OnlineSubSection.YOUTUBE_MUSIC -> "yt"
    }

    val containerColor = if (isCurrent) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.background
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .border(
                width = if (isCurrent) 1.2.dp else 0.8.dp,
                color = if (isCurrent) crimson else MaterialTheme.colorScheme.outlineVariant,
                shape = shape
            )
            .clickable(onClick = onPlayClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("${rowTagPrefix}_row_${track.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (rank != null) {
                Text(
                    text = "$rank",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = if (rank in 1..3) crimson else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(24.dp)
                )
            }

            Box(contentAlignment = Alignment.Center) {
                AlbumCoverImage(
                    artworkUri = track.artworkUri,
                    contentDescription = track.title,
                    modifier = Modifier
                        .size(54.dp)
                        .border(
                            0.8.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(14.dp)
                        ),
                    shape = RoundedCornerShape(14.dp)
                )
                if (isResolvingStream) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    }
                } else if (isCurrent && isPlaying) {
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
                    testTag = "${rowTagPrefix}_download_btn_${track.id}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("${rowTagPrefix}_fav_btn_${track.id}")
            ) {
                Icon(
                    imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = stringResource(R.string.cd_favorite),
                    tint = if (track.isFavorite) crimson else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCurrent) ink else MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCurrent && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isCurrent && isPlaying) stringResource(R.string.cd_pause) else stringResource(R.string.cd_play),
                        tint = if (isCurrent) onInk else ink,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        TrackSocialBar(
            trackId = track.id,
            summary = socialSummary,
            onToggleReaction = onToggleReaction,
            onOpenComments = onOpenComments
        )
    }
}

@Composable
private fun TrackMetadataBadge(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}
