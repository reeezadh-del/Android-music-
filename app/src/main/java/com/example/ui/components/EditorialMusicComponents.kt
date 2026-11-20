package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.example.R
import com.example.player.MusicTrack
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun EditorialDashDotIndicator(
    modifier: Modifier = Modifier,
    dashColor: Color = MaterialTheme.colorScheme.primary,
    dotColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(3.5.dp)
                .clip(RoundedCornerShape(50))
                .background(dashColor)
        )
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
    }
}

@Composable
fun EditorialPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onSearchClick: (() -> Unit)? = null,
    searchTestTag: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                EditorialDashDotIndicator()
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            actions()
            if (onSearchClick != null) {
                IconButton(
                    onClick = onSearchClick,
                    modifier = Modifier
                        .size(48.dp)
                        .then(
                            if (!searchTestTag.isNullOrBlank()) {
                                Modifier.testTag(searchTestTag)
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.editorial_search_toggle),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EditorialHeroSpotlightBanner(
    featuredTrack: MusicTrack?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    eyebrowText: String = stringResource(R.string.editorial_featured_label),
    fallbackTitle: String = stringResource(R.string.music_player_title),
    fallbackSubtitle: String = stringResource(R.string.empty_subtitle)
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(148.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), shape)
            .clickable(onClick = onClick)
    ) {
        val artwork = featuredTrack?.artworkUri.orEmpty()
        if (artwork.isNotBlank()) {
            SubcomposeAsyncImage(
                model = artwork,
                contentDescription = featuredTrack?.title ?: fallbackTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Image(
                        painter = painterResource(id = R.drawable.img_studio_hero_backdrop_1795019180500),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                },
                error = {
                    Image(
                        painter = painterResource(id = R.drawable.img_studio_hero_backdrop_1795019180500),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.img_studio_hero_backdrop_1795019180500),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Clean editorial scrim for crisp readability in both Day and Night modes
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.14f),
                            Color.Black.copy(alpha = 0.64f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.45f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary)
                    )
                    Text(
                        text = eyebrowText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = featuredTrack?.title ?: fallbackTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = featuredTrack?.artist ?: fallbackSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCurrent && isPlaying) {
                        AnimatedEqualizerBars(
                            barColor = MaterialTheme.colorScheme.secondary,
                            maxHeight = 14.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (isCurrent && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.cd_play),
                            tint = Color(0xFF18191F),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EditorialSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    actionTestTag: String? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (trailingContent != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                content = trailingContent
            )
        } else if (!actionLabel.isNullOrBlank() && onActionClick != null) {
            Row(
                modifier = Modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 6.dp)
                    .then(
                        if (!actionTestTag.isNullOrBlank()) {
                            Modifier.testTag(actionTestTag)
                        } else {
                            Modifier
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun EditorialAlbumsCarousel(
    tracks: List<MusicTrack>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onTrackClick: (MusicTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(tracks.take(10), key = { "album_tile_${it.id}" }) { track ->
            val isCurrent = currentTrackId == track.id
            Column(
                modifier = Modifier
                    .width(108.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onTrackClick(track) },
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AlbumCoverImage(
                        artworkUri = track.artworkUri,
                        contentDescription = track.title,
                        modifier = Modifier
                            .size(108.dp)
                            .border(
                                width = if (isCurrent) 1.5.dp else 0.8.dp,
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                },
                                shape = RoundedCornerShape(18.dp)
                            ),
                        shape = RoundedCornerShape(18.dp)
                    )
                    if (isCurrent && isPlaying) {
                        Box(
                            modifier = Modifier
                                .size(108.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.Black.copy(alpha = 0.42f)),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedEqualizerBars(
                                barColor = Color.White,
                                maxHeight = 20.dp
                            )
                        }
                    }
                }
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun EditorialVinylArtworkShowcase(
    artworkUri: String,
    title: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val effectiveAngle = if (isPlaying) {
        val rotationTransition = rememberInfiniteTransition(label = "vinyl_spin")
        val spinAngle by rotationTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 12_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "vinyl_rotation"
        )
        spinAngle
    } else {
        0f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(252.dp)
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        // Subtle peeking next cover card on the far trailing edge
        Box(
            modifier = Modifier
                .offset(x = 186.dp)
                .size(width = 76.dp, height = 200.dp)
                .alpha(0.32f)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )

        // Peeking vinyl disc behind the album cover
        Box(
            modifier = Modifier
                .offset(x = 34.dp)
                .size(214.dp)
                .graphicsLayer { rotationZ = effectiveAngle }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF282A34),
                            Color(0xFF101116),
                            Color(0xFF1E2029),
                            Color(0xFF0A0B0F)
                        )
                    ),
                    radius = radius
                )
                // Fine concentric vinyl grooves
                for (factor in listOf(0.88f, 0.78f, 0.68f, 0.56f)) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.08f),
                        radius = radius * factor,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                // Center spindle ring
                drawCircle(
                    color = Color(0xFF3A3D4B),
                    radius = radius * 0.24f
                )
                drawCircle(
                    color = Color(0xFF0C0D12),
                    radius = radius * 0.07f
                )
            }
        }

        // Main Square Editorial Album Cover
        AlbumCoverImage(
            artworkUri = artworkUri,
            contentDescription = title,
            modifier = Modifier
                .size(228.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(22.dp),
                    ambientColor = Color.Black.copy(alpha = 0.18f),
                    spotColor = Color.Black.copy(alpha = 0.22f)
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(22.dp)
                ),
            shape = RoundedCornerShape(22.dp)
        )
    }
}

@Composable
fun EditorialWavySeekBar(
    fraction: Float,
    isPlaying: Boolean,
    isDragging: Boolean,
    onSeekDrag: (Float) -> Unit,
    onSeekCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.outlineVariant
) {
    val effectivePhase = if (isPlaying) {
        val waveTransition = rememberInfiniteTransition(label = "wave_seek")
        val wavePhase by waveTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wave_phase"
        )
        wavePhase
    } else {
        0.6f
    }
    var latestDragFraction by remember { mutableFloatStateOf(fraction) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Canvas(
            modifier = modifier
                .fillMaxWidth()
                .height(36.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val next = (offset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                        latestDragFraction = next
                        onSeekDrag(next)
                        onSeekCommit(next)
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            val next = (offset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            latestDragFraction = next
                            onSeekDrag(next)
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val next = (change.position.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            latestDragFraction = next
                            onSeekDrag(next)
                        },
                        onDragEnd = {
                            onSeekCommit(latestDragFraction)
                        },
                        onDragCancel = {
                            onSeekCommit(latestDragFraction)
                        }
                    )
                }
        ) {
            val clamped = fraction.coerceIn(0f, 1f)
            val centerY = size.height / 2f
            val horizontalPad = 8.dp.toPx()
            val trackWidth = (size.width - horizontalPad * 2f).coerceAtLeast(1f)
            val thumbX = horizontalPad + trackWidth * clamped
            val endX = horizontalPad + trackWidth

            // Unplayed subtle horizontal track line
            drawLine(
                color = inactiveColor,
                start = Offset(thumbX, centerY),
                end = Offset(endX, centerY),
                strokeWidth = 1.6.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Active sinusoidal wavy progress path
            val activeWidth = (thumbX - horizontalPad).coerceAtLeast(0f)
            val waveAmplitude = 5.5.dp.toPx()
            val waveFrequency = 2.6f * (2f * PI.toFloat()) / trackWidth.coerceAtLeast(1f)
            val wavePath = Path()
            if (activeWidth > 1f) {
                val steps = 64
                for (i in 0..steps) {
                    val t = i / steps.toFloat()
                    val x = horizontalPad + activeWidth * t
                    // Taper wave smoothly to 0 right at the thumb for clean connection
                    val envelope = sin(t * PI.toFloat()).coerceAtLeast(0f)
                    val y = centerY + sin((x - horizontalPad) * waveFrequency + effectivePhase) *
                        waveAmplitude * (0.35f + 0.65f * envelope) * (1f - t * 0.25f)
                    if (i == 0) {
                        wavePath.moveTo(x, y)
                    } else {
                        wavePath.lineTo(x, y)
                    }
                }
                drawPath(
                    path = wavePath,
                    color = activeColor,
                    style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Crisp Thumb Knob
            if (isDragging) {
                drawCircle(
                    color = activeColor.copy(alpha = 0.22f),
                    radius = 12.dp.toPx(),
                    center = Offset(thumbX, centerY)
                )
            }
            drawCircle(
                color = activeColor,
                radius = if (isDragging) 6.dp.toPx() else 4.5.dp.toPx(),
                center = Offset(thumbX, centerY)
            )
        }
    }
}
