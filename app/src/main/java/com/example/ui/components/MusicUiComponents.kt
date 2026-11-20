package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.example.R
import com.example.player.MusicTrack
import com.example.player.RepeatModeOption
import com.example.player.TrackDownloadStatus
import com.example.player.TrackReactionSummary
import com.example.player.TrackReactionType
import com.example.ui.theme.OutfitFontFamily
import java.util.Locale

fun formatDuration(durationMs: Long): String {
    val totalSec = (durationMs / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}

fun formatPlayCount(count: Long): String {
    return when {
        count >= 1_000_000L -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000L -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

@Composable
fun AnimatedEqualizerBars(
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    barWidth: Dp = 3.dp,
    maxHeight: Dp = 18.dp
) {
    val transition = rememberInfiniteTransition(label = "eq_bars")
    val barA by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq_a"
    )
    val barB by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(160)
        ),
        label = "eq_b"
    )
    val barC by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 390, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(280)
        ),
        label = "eq_c"
    )

    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(barA, barB, barC).forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(maxHeight * fraction)
                    .clip(RoundedCornerShape(50))
                    .background(barColor)
            )
        }
    }
}

@Composable
fun StudioHeroBanner(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                shape = shape
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
fun AvaSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholderText: String,
    voiceContentDescription: String,
    onVoiceSearchClick: () -> Unit,
    inputTestTag: String,
    voiceTestTag: String,
    modifier: Modifier = Modifier,
    onSubmitSearch: (() -> Unit)? = null,
    onClearSearch: (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholderText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            if (onSubmitSearch != null) {
                IconButton(
                    onClick = onSubmitSearch,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "جستجو",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingIcon = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                if (value.isNotEmpty()) {
                    IconButton(
                        onClick = { onClearSearch?.invoke() ?: onValueChange("") },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "پاک کردن",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onVoiceSearchClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag(voiceTestTag)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = voiceContentDescription,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        singleLine = true,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = RoundedCornerShape(20.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
            focusedLeadingIconColor = MaterialTheme.colorScheme.primary
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag(inputTestTag)
    )
}

@Composable
fun AlbumCoverImage(
    artworkUri: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp)
) {
    val fallback = @Composable {
        Image(
            painter = painterResource(id = R.drawable.img_default_album_art_1794792985728),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }

    Box(modifier = modifier.clip(shape)) {
        if (artworkUri.isBlank()) {
            fallback()
        } else {
            SubcomposeAsyncImage(
                model = artworkUri,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { fallback() },
                error = { fallback() }
            )
        }
    }
}

@Composable
fun TrackDownloadButton(
    status: TrackDownloadStatus?,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val downloadingDesc = stringResource(R.string.cd_downloading)
    val downloadedDesc = stringResource(R.string.cd_downloaded)
    val downloadDesc = stringResource(R.string.cd_download)
    val alreadyDownloaded = isDownloaded || status is TrackDownloadStatus.Downloaded

    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .semantics {
                contentDescription = when {
                    status is TrackDownloadStatus.Downloading -> downloadingDesc
                    alreadyDownloaded -> downloadedDesc
                    else -> downloadDesc
                }
            }
            .testTag(testTag)
    ) {
        when (status) {
            is TrackDownloadStatus.Downloading -> {
                if (status.progress in 0f..1f) {
                    CircularProgressIndicator(
                        progress = { status.progress },
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            is TrackDownloadStatus.Downloaded -> {
                Icon(
                    imageVector = Icons.Rounded.DownloadDone,
                    contentDescription = downloadedDesc,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(21.dp)
                )
            }
            else -> {
                if (isDownloaded) {
                    Icon(
                        imageVector = Icons.Rounded.DownloadDone,
                        contentDescription = downloadedDesc,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(21.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = downloadDesc,
                        tint = if (status is TrackDownloadStatus.Error) {
                            MaterialTheme.colorScheme.error
                        } else {
                            tint
                        },
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TrackRowItem(
    track: MusicTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRemoveImported: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onRemoveDownloaded: (() -> Unit)? = null
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
            .testTag("track_row_${track.id}"),
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
                        color = if (isCurrent) {
                            crimson.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                        },
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
                if (track.isDownloaded) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = stringResource(R.string.library_badge_downloaded),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier
                .size(48.dp)
                .testTag("fav_btn_${track.id}")
        ) {
            Icon(
                imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = stringResource(R.string.cd_favorite),
                tint = if (track.isFavorite) crimson else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier.size(20.dp)
            )
        }

        val removeHandler = onRemoveDownloaded ?: onRemoveImported
        if (removeHandler != null) {
            IconButton(
                onClick = removeHandler,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("remove_track_btn_${track.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = if (onRemoveDownloaded != null) {
                        stringResource(R.string.cd_delete_download)
                    } else {
                        "حذف"
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        IconButton(
            onClick = onClick,
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

@Composable
fun TrendingTrackRowItem(
    rank: Int,
    track: MusicTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    socialSummary: TrackReactionSummary? = null,
    onToggleReaction: ((TrackReactionType) -> Unit)? = null,
    onOpenComments: (() -> Unit)? = null
) {
    val isTopThree = rank in 1..3
    val shape = RoundedCornerShape(20.dp)
    val borderBrush = when {
        isCurrent -> Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
            )
        )
        isTopThree -> Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f),
                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            )
        )
        else -> Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        )
    }
    val containerColor = if (isCurrent) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .border(if (isCurrent || isTopThree) 1.2.dp else 1.dp, borderBrush, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("trending_row_${track.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isTopThree) {
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
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = if (isTopThree) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Box(contentAlignment = Alignment.Center) {
                AlbumCoverImage(
                    artworkUri = track.artworkUri,
                    contentDescription = track.title,
                    modifier = Modifier
                        .size(56.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(14.dp)
                        ),
                    shape = RoundedCornerShape(14.dp)
                )
                if (isCurrent && isPlaying) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.62f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedEqualizerBars(barColor = MaterialTheme.colorScheme.primary)
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
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = stringResource(R.string.badge_insta_trend),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (track.isRemix) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = stringResource(R.string.badge_remix),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("trending_fav_btn_${track.id}")
            ) {
                Icon(
                    imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = stringResource(R.string.cd_favorite),
                    tint = if (track.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (onToggleReaction != null && onOpenComments != null) {
            TrackSocialBar(
                trackId = track.id,
                summary = socialSummary ?: TrackReactionSummary(),
                onToggleReaction = onToggleReaction,
                onOpenComments = onOpenComments
            )
        }
    }
}

@Composable
fun OnlineTrackRowItem(
    track: MusicTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    socialSummary: TrackReactionSummary,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleReaction: (TrackReactionType) -> Unit,
    onOpenComments: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    val borderBrush = if (isCurrent) {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f),
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        )
    }
    val containerColor = if (isCurrent) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .border(if (isCurrent) 1.2.dp else 1.dp, borderBrush, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("online_row_${track.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                AlbumCoverImage(
                    artworkUri = track.artworkUri,
                    contentDescription = track.title,
                    modifier = Modifier
                        .size(56.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(14.dp)
                        ),
                    shape = RoundedCornerShape(14.dp)
                )
                if (isCurrent && isPlaying) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.62f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedEqualizerBars(barColor = MaterialTheme.colorScheme.tertiary)
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
                    color = if (isCurrent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
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
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = "پخش آنلاین",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    if (track.isRemix) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = stringResource(R.string.badge_remix),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("online_fav_btn_${track.id}")
            ) {
                Icon(
                    imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = stringResource(R.string.cd_favorite),
                    tint = if (track.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
fun MiniPlayerBar(
    track: MusicTrack,
    isPlaying: Boolean,
    progress: Float,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier,
    onSkipPrevious: (() -> Unit)? = null,
    applyNavigationBarPadding: Boolean = false
) {
    val navModifier = if (applyNavigationBarPadding) {
        Modifier.navigationBarsPadding()
    } else {
        Modifier
    }
    val shape = RoundedCornerShape(28.dp)
    val pillBg = MaterialTheme.colorScheme.inverseSurface
    val pillFg = MaterialTheme.colorScheme.inverseOnSurface
    val crimson = MaterialTheme.colorScheme.inversePrimary

    Surface(
        shape = shape,
        color = pillBg,
        contentColor = pillFg,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
        modifier = modifier
            .fillMaxWidth()
            .then(navModifier)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(shape)
            .clickable(onClick = onExpand)
            .testTag("mini_player_bar")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isPlaying) {
                    AnimatedEqualizerBars(
                        barColor = crimson,
                        barWidth = 2.5.dp,
                        maxHeight = 14.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(width = 8.dp, height = 8.dp)
                            .clip(CircleShape)
                            .background(crimson.copy(alpha = 0.85f))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }

                AlbumCoverImage(
                    artworkUri = track.artworkUri,
                    contentDescription = track.title,
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.width(11.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = pillFg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = pillFg.copy(alpha = 0.68f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (onSkipPrevious != null) {
                    IconButton(
                        onClick = onSkipPrevious,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = stringResource(R.string.cd_previous),
                            tint = pillFg,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("mini_play_pause")
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(pillFg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) stringResource(R.string.cd_pause) else stringResource(R.string.cd_play),
                            tint = pillBg,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("mini_next")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = stringResource(R.string.cd_next),
                        tint = pillFg,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = crimson,
                trackColor = pillFg.copy(alpha = 0.14f)
            )
        }
    }
}

@Composable
fun FullNowPlayingSheet(
    track: MusicTrack,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    shuffleEnabled: Boolean,
    repeatMode: RepeatModeOption,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    isAutoSpeedActive: Boolean = false,
    onOpenAutoSpeedVolume: (() -> Unit)? = null,
    onOpenDrivingMode: (() -> Unit)? = null,
    socialSummary: TrackReactionSummary = TrackReactionSummary(),
    onToggleReaction: ((TrackReactionType) -> Unit)? = null,
    onOpenComments: (() -> Unit)? = null,
    downloadStatus: TrackDownloadStatus? = null,
    onDownloadClick: (() -> Unit)? = null
) {
    BackHandler(onBack = onCollapse)

    val safeDuration = durationMs.coerceAtLeast(1L)
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember(track.id) { mutableFloatStateOf(0f) }
    val currentFraction = if (isDragging) dragFraction else (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val displayPosMs = if (isDragging) (dragFraction * safeDuration).toLong() else positionMs

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 540.dp)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Minimalist Editorial "Play" Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = stringResource(R.string.editorial_play_heading),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    EditorialDashDotIndicator()
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (onOpenDrivingMode != null) {
                        IconButton(
                            onClick = onOpenDrivingMode,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("now_playing_driving_mode_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = stringResource(R.string.driving_mode_title),
                                tint = if (isAutoSpeedActive) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                    if (onOpenAutoSpeedVolume != null) {
                        IconButton(
                            onClick = onOpenAutoSpeedVolume,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("now_playing_speed_volume_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DirectionsCar,
                                contentDescription = stringResource(R.string.auto_speed_volume_title),
                                tint = if (isAutoSpeedActive) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                    IconButton(
                        onClick = onCollapse,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("now_playing_collapse")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // Centerpiece Vinyl Disc Peeking Behind Square Artwork
            EditorialVinylArtworkShowcase(
                artworkUri = track.artworkUri,
                title = track.title,
                isPlaying = isPlaying
            )

            // Editorial Title + Artist + Crimson Heart Row + Compact Social Strip
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when {
                                track.isDownloaded -> "${track.artist} • ${stringResource(R.string.library_badge_downloaded)}"
                                track.isOnline -> "${track.artist} • ${if (track.isRemix) "ریمیکس" else "آنلاین"}"
                                else -> track.artist
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (onDownloadClick != null && (track.isOnline || track.isDownloaded)) {
                        TrackDownloadButton(
                            status = downloadStatus,
                            isDownloaded = track.isDownloaded,
                            onClick = onDownloadClick,
                            testTag = "now_playing_download"
                        )
                    }

                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("now_playing_favorite")
                    ) {
                        Icon(
                            imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = stringResource(R.string.cd_favorite),
                            tint = if (track.isFavorite) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                if (onToggleReaction != null && onOpenComments != null) {
                    TrackSocialBar(
                        trackId = "now_${track.id}",
                        summary = socialSummary,
                        onToggleReaction = onToggleReaction,
                        onOpenComments = onOpenComments,
                        centered = false
                    )
                }
            }

            // Signature Sinusoidal Wavy Seekbar + Timestamps
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                EditorialWavySeekBar(
                    fraction = currentFraction,
                    isPlaying = isPlaying,
                    isDragging = isDragging,
                    onSeekDrag = { next ->
                        isDragging = true
                        dragFraction = next
                    },
                    onSeekCommit = { finalFraction ->
                        onSeekTo((finalFraction * safeDuration).toLong())
                        isDragging = false
                    },
                    activeColor = MaterialTheme.colorScheme.primary,
                    inactiveColor = MaterialTheme.colorScheme.outline
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(displayPosMs),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = OutfitFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatDuration(durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = OutfitFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Minimalist 5-Button Editorial Transport Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCycleRepeat,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (repeatMode == RepeatModeOption.ONE) {
                            Icons.Rounded.RepeatOne
                        } else {
                            Icons.Rounded.Repeat
                        },
                        contentDescription = stringResource(R.string.cd_repeat),
                        tint = if (repeatMode != RepeatModeOption.OFF) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("now_playing_prev")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = stringResource(R.string.cd_previous),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Surface(
                    onClick = onTogglePlayPause,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .size(66.dp)
                        .testTag("now_playing_play_pause")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) stringResource(R.string.cd_pause) else stringResource(R.string.cd_play),
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("now_playing_next")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = stringResource(R.string.cd_next),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = stringResource(R.string.cd_shuffle),
                        tint = if (shuffleEnabled) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}
