package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GpsOff
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.player.AutoSpeedVolumeState
import com.example.player.MusicTrack
import com.example.player.SpeedVolumeSensitivity
import com.example.ui.theme.OutfitFontFamily

@Composable
fun DrivingModeOverlay(
    speedState: AutoSpeedVolumeState,
    currentTrack: MusicTrack?,
    isPlaying: Boolean,
    progress: Float,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleSpeedAuto: (Boolean) -> Unit,
    onSelectSensitivity: (SpeedVolumeSensitivity) -> Unit,
    onRequestLocationPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onClose)

    val bgColor = MaterialTheme.colorScheme.background
    val primaryGlow = MaterialTheme.colorScheme.primary
    val tertiaryGlow = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryGlow.copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.22f, size.height * 0.25f),
                        radius = size.maxDimension * 0.55f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            tertiaryGlow.copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.80f, size.height * 0.30f),
                        radius = size.maxDimension * 0.50f
                    )
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("driving_mode_overlay")
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            val isWideLandscape = maxWidth >= 600.dp
            val isCompactHeight = maxHeight < 600.dp
            val sectionSpacing = if (isCompactHeight) 8.dp else 14.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(sectionSpacing)
            ) {
                DrivingModeTopBar(
                    speedState = speedState,
                    onRequestLocationPermission = onRequestLocationPermission,
                    onOpenSettings = onOpenSettings,
                    onClose = onClose
                )

                if (!isCompactHeight && speedState.isEnabled && !speedState.hasLocationPermission) {
                    DrivingPermissionBanner(
                        onRequestLocationPermission = onRequestLocationPermission
                    )
                }

                if (isWideLandscape) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1.1f),
                            verticalArrangement = Arrangement.spacedBy(sectionSpacing)
                        ) {
                            DrivingTelemetryRow(
                                speedState = speedState,
                                isCompactHeight = isCompactHeight,
                                onToggleSpeedAuto = onToggleSpeedAuto,
                                onSelectSensitivity = onSelectSensitivity
                            )
                        }
                        Column(
                            modifier = Modifier.weight(0.9f),
                            verticalArrangement = Arrangement.spacedBy(sectionSpacing)
                        ) {
                            DrivingMediaCard(
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                progress = progress,
                                isCompactHeight = isCompactHeight,
                                onTogglePlayPause = onTogglePlayPause,
                                onSkipNext = onSkipNext,
                                onSkipPrevious = onSkipPrevious
                            )
                        }
                    }
                } else {
                    DrivingTelemetryRow(
                        speedState = speedState,
                        isCompactHeight = isCompactHeight,
                        onToggleSpeedAuto = onToggleSpeedAuto,
                        onSelectSensitivity = onSelectSensitivity
                    )

                    DrivingMediaCard(
                        currentTrack = currentTrack,
                        isPlaying = isPlaying,
                        progress = progress,
                        isCompactHeight = isCompactHeight,
                        onTogglePlayPause = onTogglePlayPause,
                        onSkipNext = onSkipNext,
                        onSkipPrevious = onSkipPrevious
                    )
                }
            }
        }
    }
}

@Composable
private fun DrivingModeTopBar(
    speedState: AutoSpeedVolumeState,
    onRequestLocationPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit
) {
    val isLive = speedState.isActiveForDriving &&
        (speedState.isAndroidAutoConnected || speedState.hasLocationPermission)
    val needsPermission = speedState.isEnabled && !speedState.hasLocationPermission
    val statusText = when {
        !speedState.isEnabled -> stringResource(R.string.driving_mode_status_off)
        needsPermission -> stringResource(R.string.driving_mode_status_paused)
        speedState.isAndroidAutoConnected -> stringResource(R.string.driving_mode_status_auto)
        speedState.onlyWhenAndroidAutoConnected -> stringResource(R.string.driving_mode_status_waiting_auto)
        else -> stringResource(R.string.driving_mode_status_gps)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = { if (needsPermission) onRequestLocationPermission() },
            enabled = needsPermission,
            shape = RoundedCornerShape(16.dp),
            color = when {
                isLive -> MaterialTheme.colorScheme.tertiaryContainer
                needsPermission -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            },
            modifier = Modifier
                .height(48.dp)
                .weight(1f, fill = false)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = when {
                        speedState.isAndroidAutoConnected -> Icons.Rounded.DirectionsCar
                        isLive -> Icons.Rounded.GpsFixed
                        else -> Icons.Rounded.GpsOff
                    },
                    contentDescription = null,
                    tint = when {
                        isLive -> MaterialTheme.colorScheme.onTertiaryContainer
                        needsPermission -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isLive -> MaterialTheme.colorScheme.onTertiaryContainer
                        needsPermission -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("driving_mode_status_badge")
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("driving_mode_settings_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = stringResource(R.string.driving_mode_settings),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Surface(
                onClick = onClose,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("driving_mode_exit_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.driving_mode_exit),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.driving_mode_exit),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun DrivingPermissionBanner(onRequestLocationPermission: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.GpsOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = stringResource(R.string.auto_speed_permission_needed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onRequestLocationPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("driving_mode_permission_button")
            ) {
                Text(
                    text = stringResource(R.string.auto_speed_grant_permission),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun DrivingTelemetryRow(
    speedState: AutoSpeedVolumeState,
    isCompactHeight: Boolean,
    onToggleSpeedAuto: (Boolean) -> Unit,
    onSelectSensitivity: (SpeedVolumeSensitivity) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 8.dp else 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CockpitGaugeCard(
                title = stringResource(R.string.driving_mode_speed_label),
                valueText = "${speedState.speedRoundedKmh}",
                unitText = stringResource(R.string.driving_mode_speed_unit),
                fraction = (speedState.currentSpeedKmh / 140f).coerceIn(0f, 1f),
                accentColor = MaterialTheme.colorScheme.primary,
                icon = Icons.Rounded.Speed,
                valueTestTag = "driving_mode_speed_value",
                isCompactHeight = isCompactHeight,
                modifier = Modifier.weight(1f)
            )

            CockpitGaugeCard(
                title = stringResource(R.string.driving_mode_volume_label),
                valueText = "${speedState.appliedVolumePercent}",
                unitText = "%",
                fraction = speedState.appliedVolumeFraction.coerceIn(0f, 1f),
                accentColor = MaterialTheme.colorScheme.tertiary,
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                valueTestTag = "driving_mode_volume_value",
                isCompactHeight = isCompactHeight,
                modifier = Modifier.weight(1f)
            )
        }

        if (isCompactHeight) {
            // Single streamlined 48dp row for compact heights so media transport stays above the fold
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = { onToggleSpeedAuto(!speedState.isEnabled) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (speedState.isEnabled) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("driving_mode_auto_toggle")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (speedState.isEnabled) "خودکار: روشن" else "خودکار: خاموش",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (speedState.isEnabled) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                SpeedVolumeSensitivity.entries.forEach { option ->
                    val selected = speedState.sensitivity == option
                    Surface(
                        onClick = { onSelectSensitivity(option) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .border(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = RoundedCornerShape(14.dp)
                            )
                            .testTag("driving_mode_sensitivity_${option.name}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = option.labelFa,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        RoundedCornerShape(20.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.auto_speed_toggle_label),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (speedState.isActiveForDriving) {
                                    stringResource(R.string.driving_mode_auto_badge_on)
                                } else {
                                    stringResource(R.string.driving_mode_auto_badge_off)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (speedState.isActiveForDriving) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        Switch(
                            checked = speedState.isEnabled,
                            onCheckedChange = onToggleSpeedAuto,
                            modifier = Modifier.testTag("driving_mode_auto_toggle")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpeedVolumeSensitivity.entries.forEach { option ->
                            val selected = speedState.sensitivity == option
                            Surface(
                                onClick = { onSelectSensitivity(option) },
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .border(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .testTag("driving_mode_sensitivity_${option.name}")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = option.labelFa,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CockpitGaugeCard(
    title: String,
    valueText: String,
    unitText: String,
    fraction: Float,
    accentColor: Color,
    icon: ImageVector,
    valueTestTag: String,
    isCompactHeight: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "gauge_sweep"
    )
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val gaugeSize = if (isCompactHeight) 82.dp else 136.dp
    val strokeDp = if (isCompactHeight) 8.dp else 13.dp
    val valueFontSize = if (isCompactHeight) 28.sp else 44.sp

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 4.dp,
        modifier = modifier
            .border(
                1.dp,
                accentColor.copy(alpha = 0.35f),
                RoundedCornerShape(24.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = if (isCompactHeight) 8.dp else 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 4.dp else 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier.size(gaugeSize),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = strokeDp.toPx()
                    val inset = strokeWidth / 2f
                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                    val topLeft = Offset(inset, inset)

                    drawArc(
                        color = trackColor,
                        startAngle = 145f,
                        sweepAngle = 250f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    if (animatedFraction > 0.005f) {
                        drawArc(
                            color = accentColor,
                            startAngle = 145f,
                            sweepAngle = 250f * animatedFraction,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = valueText,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = valueFontSize,
                        lineHeight = valueFontSize,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag(valueTestTag)
                    )
                    Text(
                        text = unitText,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isCompactHeight) 11.sp else 14.sp,
                        color = accentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun DrivingMediaCard(
    currentTrack: MusicTrack?,
    isPlaying: Boolean,
    progress: Float,
    isCompactHeight: Boolean,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit
) {
    val coverSize = if (isCompactHeight) 44.dp else 72.dp
    val sideBtnSize = if (isCompactHeight) 52.dp else 72.dp
    val playBtnSize = if (isCompactHeight) 62.dp else 88.dp

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                RoundedCornerShape(26.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isCompactHeight) 10.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 8.dp else 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (currentTrack != null) {
                    AlbumCoverImage(
                        artworkUri = currentTrack.artworkUri,
                        contentDescription = currentTrack.title,
                        modifier = Modifier.size(coverSize),
                        shape = RoundedCornerShape(14.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(coverSize)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(if (isCompactHeight) 22.dp else 34.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTrack?.title ?: stringResource(R.string.driving_mode_no_track),
                        style = if (isCompactHeight) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("driving_mode_track_title")
                    )
                    Text(
                        text = currentTrack?.artist ?: stringResource(R.string.driving_mode_tap_play),
                        style = if (isCompactHeight) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onSkipPrevious,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .size(sideBtnSize)
                        .testTag("driving_mode_prev")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = stringResource(R.string.cd_previous),
                            modifier = Modifier.size(if (isCompactHeight) 28.dp else 40.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(playBtnSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onTogglePlayPause)
                        .testTag("driving_mode_play_pause"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) stringResource(R.string.cd_pause) else stringResource(R.string.cd_play),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(if (isCompactHeight) 34.dp else 48.dp)
                    )
                }

                Surface(
                    onClick = onSkipNext,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .size(sideBtnSize)
                        .testTag("driving_mode_next")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = stringResource(R.string.cd_next),
                            modifier = Modifier.size(if (isCompactHeight) 28.dp else 40.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
