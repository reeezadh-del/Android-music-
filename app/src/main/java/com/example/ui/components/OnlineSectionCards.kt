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
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
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
import com.example.player.TrackReactionSummary
import com.example.player.TrackReactionType
import com.example.ui.theme.OutfitFontFamily

@Composable
fun OnlineStudioHeader(
    subSection: OnlineSubSection,
    tracksCount: Int,
    lyricsAvailableCount: Int,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (subSection == OnlineSubSection.TRENDING) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    val secondaryAccent = MaterialTheme.colorScheme.secondary

    val pulseTransition = rememberInfiniteTransition(label = "online_live_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        accent.copy(alpha = 0.14f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        secondaryAccent.copy(alpha = 0.35f)
                    )
                ),
                shape = shape
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = accent.copy(alpha = 0.16f),
                        modifier = Modifier.border(
                            0.8.dp,
                            accent.copy(alpha = 0.4f),
                            RoundedCornerShape(50)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .alpha(pulseAlpha)
                                    .clip(CircleShape)
                                    .background(accent)
                            )
                            Text(
                                text = stringResource(R.string.online_live_eyebrow),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )
                        }
                    }

                    if (tracksCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.78f)
                        ) {
                            Text(
                                text = if (lyricsAvailableCount > 0) {
                                    "$tracksCount آهنگ • $lyricsAvailableCount با متن"
                                } else {
                                    "$tracksCount آهنگ آنلاین"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.online_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = stringResource(R.string.online_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                modifier = Modifier
                    .size(48.dp)
                    .border(
                        1.dp,
                        accent.copy(alpha = 0.4f),
                        CircleShape
                    )
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
                            color = accent
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.trending_refresh),
                            tint = accent
                        )
                    }
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
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                RoundedCornerShape(20.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OnlineSubSection.entries.forEach { tab ->
                val selected = subSection == tab
                val targetContainer = if (selected) {
                    if (tab == OnlineSubSection.TRENDING) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    }
                } else {
                    Color.Transparent
                }
                val containerColor by animateColorAsState(
                    targetValue = targetContainer,
                    animationSpec = tween(durationMillis = 220),
                    label = "subtab_bg_${tab.name}"
                )
                val iconTint = if (selected) {
                    if (tab == OnlineSubSection.TRENDING) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    }
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                val labelColor = if (selected) {
                    if (tab == OnlineSubSection.TRENDING) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    }
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(containerColor)
                        .then(
                            if (selected) {
                                Modifier.border(
                                    1.dp,
                                    iconTint.copy(alpha = 0.45f),
                                    RoundedCornerShape(16.dp)
                                )
                            } else Modifier
                        )
                        .clickable { onSelectSubSection(tab) }
                        .testTag("online_subtab_${tab.name}"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = when (tab) {
                                OnlineSubSection.TRENDING -> Icons.Rounded.Whatshot
                                OnlineSubSection.DISCOVER -> Icons.Rounded.Explore
                            },
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tab.labelFa,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = labelColor,
                            maxLines = 1,
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
    isTrending: Boolean,
    isCurrent: Boolean,
    isPlaying: Boolean,
    socialSummary: TrackReactionSummary,
    onPlayClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenLyrics: () -> Unit,
    onToggleReaction: (TrackReactionType) -> Unit,
    onOpenComments: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (isTrending) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    val secondary = MaterialTheme.colorScheme.secondary
    val shape = RoundedCornerShape(26.dp)
    val lyricsPreview = extractLyricsPreviewLines(track.lyrics)
    val rowTagPrefix = if (isTrending) "trending" else "online"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = if (isCurrent) 1.6.dp else 1.2.dp,
                brush = Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = 0.8f),
                        secondary.copy(alpha = 0.45f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
                    )
                ),
                shape = shape
            )
            .clickable(onClick = onPlayClick)
            .testTag("${rowTagPrefix}_row_${track.id}")
    ) {
        if (track.artworkUri.isNotBlank()) {
            AsyncImage(
                model = track.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(0.18f)
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.80f),
                            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.94f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Spotlight Badge Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = accent.copy(alpha = 0.2f),
                    modifier = Modifier.border(
                        0.8.dp,
                        accent.copy(alpha = 0.5f),
                        RoundedCornerShape(50)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isTrending) {
                                "#1 • ${stringResource(R.string.online_featured_trending)}"
                            } else {
                                stringResource(R.string.online_featured_discover)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (track.isRemix) {
                        TrackMetadataBadge(
                            text = stringResource(R.string.badge_remix),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    if (track.lyrics.isNotBlank()) {
                        LyricsPillButton(
                            trackId = track.id,
                            onClick = onOpenLyrics
                        )
                    }
                }
            }

            // Cover + Title + Play CTA + Favorite
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AlbumCoverImage(
                        artworkUri = track.artworkUri,
                        contentDescription = track.title,
                        modifier = Modifier
                            .size(84.dp)
                            .border(
                                1.2.dp,
                                accent.copy(alpha = 0.55f),
                                RoundedCornerShape(20.dp)
                            ),
                        shape = RoundedCornerShape(20.dp)
                    )
                    if (isCurrent && isPlaying) {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.56f)),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedEqualizerBars(barColor = accent, maxHeight = 22.dp)
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
                        color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (track.durationMs > 0L) {
                            "${track.artist} • ${formatDuration(track.durationMs)}"
                        } else {
                            track.artist
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                        tint = if (track.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    onClick = onPlayClick,
                    shape = CircleShape,
                    color = accent,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isCurrent && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isCurrent && isPlaying) stringResource(R.string.cd_pause) else stringResource(R.string.cd_play),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // Conditional Inline Lyrics Snippet (ONLY if lyrics exist!)
            if (lyricsPreview.isNotBlank()) {
                Surface(
                    onClick = onOpenLyrics,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            0.8.dp,
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f),
                            RoundedCornerShape(16.dp)
                        )
                        .testTag("lyrics_preview_card_${track.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = lyricsPreview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
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
}

@Composable
fun RedesignedOnlineTrackCard(
    rank: Int?,
    track: MusicTrack,
    isTrending: Boolean,
    isCurrent: Boolean,
    isPlaying: Boolean,
    socialSummary: TrackReactionSummary,
    onPlayClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenLyrics: () -> Unit,
    onToggleReaction: (TrackReactionType) -> Unit,
    onOpenComments: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (isTrending) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    val isTopMedal = rank != null && rank in 1..3
    val shape = RoundedCornerShape(22.dp)
    val rowTagPrefix = if (isTrending) "trending" else "online"

    val borderBrush = when {
        isCurrent -> Brush.linearGradient(
            listOf(accent.copy(alpha = 0.85f), MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f))
        )
        isTopMedal -> Brush.linearGradient(
            listOf(MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f), accent.copy(alpha = 0.3f))
        )
        else -> Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f),
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        )
    }

    val containerColor = if (isCurrent) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    val lyricsLine = extractLyricsPreviewLines(track.lyrics, maxLines = 1)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .border(if (isCurrent || isTopMedal) 1.2.dp else 1.dp, borderBrush, shape)
            .clickable(onClick = onPlayClick)
            .padding(horizontal = 12.dp, vertical = 11.dp)
            .testTag("${rowTagPrefix}_row_${track.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (rank != null) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isTopMedal) {
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                            } else {
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceContainerHighest,
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                )
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$rank",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = if (isTopMedal) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
            }

            Box(contentAlignment = Alignment.Center) {
                AlbumCoverImage(
                    artworkUri = track.artworkUri,
                    contentDescription = track.title,
                    modifier = Modifier
                        .size(60.dp)
                        .border(
                            1.dp,
                            if (isCurrent) accent.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            RoundedCornerShape(15.dp)
                        ),
                    shape = RoundedCornerShape(15.dp)
                )
                if (isCurrent && isPlaying) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedEqualizerBars(barColor = accent)
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = if (track.durationMs > 0L) {
                        "${track.artist} • ${formatDuration(track.durationMs)}"
                    } else {
                        track.artist
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (track.isInstaTrend) {
                        TrackMetadataBadge(
                            text = stringResource(R.string.badge_insta_trend),
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    } else if (!isTrending) {
                        TrackMetadataBadge(
                            text = "پخش آنلاین",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }

                    if (track.isRemix) {
                        TrackMetadataBadge(
                            text = stringResource(R.string.badge_remix),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    if (track.lyrics.isNotBlank()) {
                        LyricsPillButton(
                            trackId = track.id,
                            onClick = onOpenLyrics
                        )
                    }
                }
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
                    tint = if (track.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (lyricsLine.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.52f))
                    .clickable(onClick = onOpenLyrics)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("lyrics_inline_snippet_${track.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = lyricsLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
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
private fun LyricsPillButton(
    trackId: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f),
        modifier = Modifier
            .border(
                0.7.dp,
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f),
                RoundedCornerShape(8.dp)
            )
            .testTag("track_lyrics_btn_$trackId")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = stringResource(R.string.online_lyrics_badge),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
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

private fun extractLyricsPreviewLines(lyrics: String, maxLines: Int = 2): String {
    if (lyrics.isBlank()) return ""
    return lyrics.lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .take(maxLines)
        .joinToString(" / ")
}
