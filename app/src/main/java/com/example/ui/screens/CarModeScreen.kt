package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Track
import com.example.ui.components.AlbumArtwork
import com.example.ui.components.formatDuration
import com.example.ui.theme.AutoTurquoise
import com.example.ui.theme.OutfitFontFamily

@Composable
fun CarModeScreen(
    track: Track?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    onExit: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onExit)

    val safeDuration = durationMs.coerceAtLeast(1L)
    val progress = (currentPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF06070B))
            .systemBarsPadding()
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // High-Contrast Driving Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = AutoTurquoise.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(50.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DirectionsCar,
                            contentDescription = null,
                            tint = AutoTurquoise
                        )
                        Text(
                            text = stringResource(R.string.action_car_mode),
                            style = MaterialTheme.typography.titleMedium,
                            color = AutoTurquoise
                        )
                    }
                }

                Surface(
                    onClick = onExit,
                    shape = CircleShape,
                    color = Color(0xFF1E202B),
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("car_mode_exit_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_close),
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // Track Info Card
            if (track != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF11131C), RoundedCornerShape(26.dp))
                        .border(1.dp, Color(0xFF282B3B), RoundedCornerShape(26.dp))
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AlbumArtwork(
                        artworkUri = track.artworkUri,
                        contentDescription = track.title,
                        modifier = Modifier.size(110.dp),
                        shape = RoundedCornerShape(20.dp)
                    )
                    Spacer(modifier = Modifier.width(18.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFB5B8C9),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = AutoTurquoise,
                            trackColor = Color(0xFF282B3B)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${formatDuration(currentPositionMs)} / ${formatDuration(durationMs)}",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = OutfitFontFamily,
                            color = AutoTurquoise
                        )
                    }
                }
            }

            // Oversized Distraction-Safe Touch Controls (>= 80dp)
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onSkipPrevious,
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF1A1C28),
                        modifier = Modifier
                            .weight(1f)
                            .height(92.dp)
                            .testTag("car_mode_previous")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = stringResource(R.string.cd_previous),
                                tint = Color.White,
                                modifier = Modifier.size(46.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = onTogglePlayPause,
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .weight(1.35f)
                            .height(92.dp)
                            .testTag("car_mode_play_pause")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) stringResource(R.string.cd_pause) else stringResource(R.string.cd_play),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(52.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = onSkipNext,
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF1A1C28),
                        modifier = Modifier
                            .weight(1f)
                            .height(92.dp)
                            .testTag("car_mode_next")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = stringResource(R.string.cd_next),
                                tint = Color.White,
                                modifier = Modifier.size(46.dp)
                            )
                        }
                    }
                }

                if (track != null) {
                    Surface(
                        onClick = { onToggleFavorite(track.id) },
                        shape = RoundedCornerShape(20.dp),
                        color = if (track.isFavorite) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        } else {
                            Color(0xFF1A1C28)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .testTag("car_mode_favorite")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = null,
                                tint = if (track.isFavorite) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (track.isFavorite) "ذخیره شده در علاقه‌مندی‌ها" else "افزودن به علاقه‌مندی‌ها",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
