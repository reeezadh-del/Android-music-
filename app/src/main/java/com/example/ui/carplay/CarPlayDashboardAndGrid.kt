package com.example.ui.carplay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Track
import com.example.headunit.AapConnectionStatus
import com.example.headunit.GpsTelemetry
import com.example.headunit.HeadunitSettings
import com.example.headunit.HurConnectionMode
import com.example.headunit.UsbHostUiState
import com.example.ui.components.AlbumArtwork
import com.example.ui.components.formatDuration
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.viewmodel.CarPlaySurfacePage
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CarPlayDashboardView(
    palette: CarPlayPalette,
    settings: HeadunitSettings,
    aapStatus: AapConnectionStatus,
    localIp: String,
    usbState: UsbHostUiState,
    gpsTelemetry: GpsTelemetry,
    currentTrack: Track?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onRequestLocationPermission: () -> Unit,
    onOpenNavigationApp: () -> Unit,
    onStartReceiver: (HurConnectionMode) -> Unit,
    onOpenProjection: () -> Unit,
    onOpenMapsScreen: () -> Unit,
    onOpenMusicScreen: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isSplitWide = maxWidth >= 640.dp

        if (isSplitWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left Major CarPlay Card: Navigation & Live GPS Speedometer
                CarPlayMapDashboardCard(
                    palette = palette,
                    gpsTelemetry = gpsTelemetry,
                    onRequestLocationPermission = onRequestLocationPermission,
                    onOpenMapsScreen = onOpenMapsScreen,
                    onOpenNavigationApp = onOpenNavigationApp,
                    modifier = Modifier
                        .weight(1.08f)
                        .fillMaxHeight()
                )

                // Right Column: Top HUR Android Auto Receiver Card + Bottom CarPlay Now Playing Card
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CarPlayHurReceiverCard(
                        palette = palette,
                        settings = settings,
                        aapStatus = aapStatus,
                        localIp = localIp,
                        usbState = usbState,
                        onStartReceiver = onStartReceiver,
                        onOpenProjection = onOpenProjection,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )

                    CarPlayNowPlayingCard(
                        palette = palette,
                        track = currentTrack,
                        isPlaying = isPlaying,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        onCardClick = {
                            if (currentTrack != null) onOpenNowPlaying() else onOpenMusicScreen()
                        },
                        onTogglePlayPause = onTogglePlayPause,
                        onSkipNext = onSkipNext,
                        onSkipPrevious = onSkipPrevious,
                        modifier = Modifier
                            .weight(0.95f)
                            .fillMaxWidth()
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CarPlayHurReceiverCard(
                    palette = palette,
                    settings = settings,
                    aapStatus = aapStatus,
                    localIp = localIp,
                    usbState = usbState,
                    onStartReceiver = onStartReceiver,
                    onOpenProjection = onOpenProjection,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                )

                CarPlayMapDashboardCard(
                    palette = palette,
                    gpsTelemetry = gpsTelemetry,
                    onRequestLocationPermission = onRequestLocationPermission,
                    onOpenMapsScreen = onOpenMapsScreen,
                    onOpenNavigationApp = onOpenNavigationApp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(235.dp)
                )

                CarPlayNowPlayingCard(
                    palette = palette,
                    track = currentTrack,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onCardClick = {
                        if (currentTrack != null) onOpenNowPlaying() else onOpenMusicScreen()
                    },
                    onTogglePlayPause = onTogglePlayPause,
                    onSkipNext = onSkipNext,
                    onSkipPrevious = onSkipPrevious,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp)
                )
            }
        }
    }
}

@Composable
private fun CarPlayMapDashboardCard(
    palette: CarPlayPalette,
    gpsTelemetry: GpsTelemetry,
    onRequestLocationPermission: () -> Unit,
    onOpenMapsScreen: () -> Unit,
    onOpenNavigationApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    CarPlayGlassCard(
        palette = palette,
        onClick = onOpenMapsScreen,
        modifier = modifier.testTag("carplay_dashboard_map_card")
    ) {
        // Subtle stylized CarPlay route grid backdrop
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridColor = if (palette.isDark) {
                Color(0xFF1D2B44).copy(alpha = 0.45f)
            } else {
                Color(0xFFC5D5EA).copy(alpha = 0.55f)
            }
            val step = 42.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }

            // Stylized glowing highway curve
            val roadStart = Offset(size.width * 0.16f, size.height * 0.88f)
            val roadMid = Offset(size.width * 0.46f, size.height * 0.52f)
            val roadEnd = Offset(size.width * 0.78f, size.height * 0.2f)
            drawLine(
                color = palette.iosBlue.copy(alpha = 0.25f),
                start = roadStart,
                end = roadMid,
                strokeWidth = 18.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = palette.iosBlue,
                start = roadStart,
                end = roadMid,
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = palette.iosTeal,
                start = roadMid,
                end = roadEnd,
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f), 0f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.cardSurfaceElevated)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Navigation,
                        contentDescription = null,
                        tint = palette.iosBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "مسیریابی و سرعت‌سنج",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                }

                // Live Compass Badge
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(palette.cardSurfaceElevated)
                        .border(1.dp, palette.cardBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val rad = Math.toRadians((gpsTelemetry.bearingDegrees - 90f).toDouble())
                    Canvas(modifier = Modifier.size(28.dp)) {
                        drawCircle(
                            color = palette.iosBlue.copy(alpha = 0.2f),
                            style = Stroke(width = 2.dp.toPx())
                        )
                        val tip = Offset(
                            center.x + (size.minDimension * 0.36f * cos(rad)).toFloat(),
                            center.y + (size.minDimension * 0.36f * sin(rad)).toFloat()
                        )
                        drawLine(
                            color = palette.iosRed,
                            start = center,
                            end = tip,
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(palette.cardSurfaceElevated)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    if (gpsTelemetry.hasPermission) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${gpsTelemetry.speedKmh}",
                                style = MaterialTheme.typography.displaySmall,
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "km/h",
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = OutfitFontFamily,
                                color = palette.iosBlue,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        val coordText = if (gpsTelemetry.latitude != null && gpsTelemetry.longitude != null) {
                            String.format(
                                "%.4f, %.4f",
                                gpsTelemetry.latitude,
                                gpsTelemetry.longitude
                            )
                        } else {
                            "در انتظار قفل ماهواره ${gpsTelemetry.providerName}…"
                        }
                        Text(
                            text = coordText,
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.textSecondary
                        )
                    } else {
                        Text(
                            text = "سرعت‌سنج واقعی GPS",
                            style = MaterialTheme.typography.titleSmall,
                            color = palette.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = onRequestLocationPermission,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = palette.iosBlue),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GpsFixed,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("فعال‌سازی GPS", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                Button(
                    onClick = onOpenNavigationApp,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.iosBlue,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("carplay_open_maps_intent")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Explore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("شروع مسیریاب")
                }
            }
        }
    }
}

@Composable
private fun CarPlayHurReceiverCard(
    palette: CarPlayPalette,
    settings: HeadunitSettings,
    aapStatus: AapConnectionStatus,
    localIp: String,
    usbState: UsbHostUiState,
    onStartReceiver: (HurConnectionMode) -> Unit,
    onOpenProjection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (headline, badgeColor) = when (aapStatus) {
        is AapConnectionStatus.Streaming -> "متصل: ${aapStatus.transport}" to palette.iosGreen
        is AapConnectionStatus.Listening -> "آماده اتصال روی $localIp:${aapStatus.port}" to palette.iosOrange
        is AapConnectionStatus.Connecting -> "در حال اتصال به ${aapStatus.target}…" to palette.iosBlue
        is AapConnectionStatus.Handshake -> aapStatus.stage to palette.iosTeal
        is AapConnectionStatus.Error -> aapStatus.title to palette.iosRed
        AapConnectionStatus.Idle -> "گیرنده Headunit Reloaded (ظاهر CarPlay)" to palette.iosBlue
    }

    CarPlayGlassCard(
        palette = palette,
        onClick = onOpenProjection,
        modifier = modifier.testTag("carplay_dashboard_hur_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(badgeColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DirectionsCar,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Android Auto Receiver",
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                        Text(
                            text = headline,
                            style = MaterialTheme.typography.labelMedium,
                            color = badgeColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    onClick = onOpenProjection,
                    shape = RoundedCornerShape(12.dp),
                    color = palette.iosBlue
                ) {
                    Text(
                        text = "نمایش",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Quick HUR connection mode pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickHurPill(
                    label = "Self",
                    icon = Icons.Rounded.DirectionsCar,
                    selected = settings.connectionMode == HurConnectionMode.SELF_MODE,
                    palette = palette,
                    onClick = {
                        onStartReceiver(HurConnectionMode.SELF_MODE)
                        onOpenProjection()
                    },
                    modifier = Modifier.weight(1f)
                )
                QuickHurPill(
                    label = "Wi-Fi",
                    icon = Icons.Rounded.Wifi,
                    selected = settings.connectionMode == HurConnectionMode.WIFI_SERVER ||
                        settings.connectionMode == HurConnectionMode.WIFI_CLIENT,
                    palette = palette,
                    onClick = {
                        onStartReceiver(HurConnectionMode.WIFI_SERVER)
                        onOpenProjection()
                    },
                    modifier = Modifier.weight(1f)
                )
                QuickHurPill(
                    label = if (usbState.isUsbConnected) "USB آماده" else "USB",
                    icon = Icons.Rounded.Usb,
                    selected = settings.connectionMode == HurConnectionMode.USB_AOAP,
                    palette = palette,
                    onClick = {
                        onStartReceiver(HurConnectionMode.USB_AOAP)
                        onOpenProjection()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun QuickHurPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    palette: CarPlayPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) palette.iosBlue.copy(alpha = 0.22f) else palette.cardSurfaceElevated
            )
            .border(
                1.dp,
                if (selected) palette.iosBlue else palette.cardBorder,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) palette.iosBlue else palette.textSecondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = OutfitFontFamily,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CarPlayNowPlayingCard(
    palette: CarPlayPalette,
    track: Track?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onCardClick: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    CarPlayGlassCard(
        palette = palette,
        onClick = onCardClick,
        modifier = modifier.testTag("carplay_dashboard_music_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AlbumArtwork(
                    artworkUri = track?.artworkUri ?: "",
                    contentDescription = track?.title ?: "موزیک",
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(14.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track?.title ?: "موزیک CarPlay آماده پخش",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track?.artist ?: "برای انتخاب آهنگ لمس کنید",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = if (durationMs > 0L) formatDuration(positionMs) else "",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = OutfitFontFamily,
                    color = palette.textSecondary
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
                color = palette.iosPink,
                trackColor = palette.textSecondary.copy(alpha = 0.2f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("carplay_card_prev")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "قبلی",
                        tint = palette.textPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(palette.iosPink)
                        .clickable(onClick = onTogglePlayPause)
                        .testTag("carplay_card_play_pause"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "توقف" else "پخش",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("carplay_card_next")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "بعدی",
                        tint = palette.textPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CarPlayAppGridView(
    palette: CarPlayPalette,
    aapStatus: AapConnectionStatus,
    installedCount: Int,
    onSelectPage: (CarPlaySurfacePage) -> Unit,
    onOpenMessages: () -> Unit,
    modifier: Modifier = Modifier
) {
    val aaBadge = when (aapStatus) {
        is AapConnectionStatus.Streaming -> "LIVE"
        is AapConnectionStatus.Listening -> "5277"
        else -> null
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 600.dp) 4 else 2

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    CarPlaySquircleAppTile(
                        label = "اندروید اتو (HUR)",
                        icon = Icons.Rounded.DirectionsCar,
                        gradientColors = listOf(Color(0xFF0A84FF), Color(0xFF30D158)),
                        palette = palette,
                        badgeText = aaBadge,
                        testTag = "grid_app_aa_hur",
                        onClick = { onSelectPage(CarPlaySurfacePage.AA_PROJECTION) }
                    )
                }
                item {
                    CarPlaySquircleAppTile(
                        label = "موزیک",
                        icon = Icons.Rounded.MusicNote,
                        gradientColors = listOf(Color(0xFFFF375F), Color(0xFFFF6B47)),
                        palette = palette,
                        testTag = "grid_app_music",
                        onClick = { onSelectPage(CarPlaySurfacePage.MUSIC) }
                    )
                }
                item {
                    CarPlaySquircleAppTile(
                        label = "نقشه و GPS",
                        icon = Icons.Rounded.Navigation,
                        gradientColors = listOf(Color(0xFF30D158), Color(0xFF0A84FF)),
                        palette = palette,
                        testTag = "grid_app_maps",
                        onClick = { onSelectPage(CarPlaySurfacePage.MAPS_GPS) }
                    )
                }
                item {
                    CarPlaySquircleAppTile(
                        label = "تلفن",
                        icon = Icons.Rounded.Call,
                        gradientColors = listOf(Color(0xFF34C759), Color(0xFF1E9E4B)),
                        palette = palette,
                        testTag = "grid_app_phone",
                        onClick = { onSelectPage(CarPlaySurfacePage.PHONE) }
                    )
                }
                item {
                    CarPlaySquircleAppTile(
                        label = "پیام‌ها",
                        icon = Icons.AutoMirrored.Rounded.Message,
                        gradientColors = listOf(Color(0xFF30D158), Color(0xFF24A148)),
                        palette = palette,
                        testTag = "grid_app_messages",
                        onClick = onOpenMessages
                    )
                }
                item {
                    CarPlaySquircleAppTile(
                        label = "اکولایزر ۵ بانده",
                        icon = Icons.Rounded.Equalizer,
                        gradientColors = listOf(Color(0xFFFF9F0A), Color(0xFFFF5E3A)),
                        palette = palette,
                        testTag = "grid_app_equalizer",
                        onClick = { onSelectPage(CarPlaySurfacePage.EQUALIZER) }
                    )
                }
                item {
                    CarPlaySquircleAppTile(
                        label = "برنامه‌های گوشی",
                        icon = Icons.Rounded.Apps,
                        gradientColors = listOf(Color(0xFF5E5CE6), Color(0xFFBF5AF2)),
                        palette = palette,
                        badgeText = if (installedCount > 0) "$installedCount" else null,
                        testTag = "grid_app_car_apps",
                        onClick = { onSelectPage(CarPlaySurfacePage.CAR_APPS) }
                    )
                }
                item {
                    CarPlaySquircleAppTile(
                        label = "تنظیمات HUR",
                        icon = Icons.Rounded.Settings,
                        gradientColors = listOf(Color(0xFF636366), Color(0xFF2C2C2E)),
                        palette = palette,
                        testTag = "grid_app_settings",
                        onClick = { onSelectPage(CarPlaySurfacePage.SETTINGS) }
                    )
                }
            }

            // CarPlay Page Indicator Dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(palette.textPrimary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(palette.textSecondary.copy(alpha = 0.4f))
                        .clickable { onSelectPage(CarPlaySurfacePage.CAR_APPS) }
                )
            }
        }
    }
}
