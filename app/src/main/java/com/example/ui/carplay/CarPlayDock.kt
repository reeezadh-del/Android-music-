package com.example.ui.carplay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.headunit.AapConnectionStatus
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.viewmodel.CarPlaySurfacePage

@Composable
fun CarPlayVerticalDock(
    clockText: String,
    batteryPercent: Int,
    aapStatus: AapConnectionStatus,
    currentPage: CarPlaySurfacePage,
    palette: CarPlayPalette,
    onSelectPage: (CarPlaySurfacePage) -> Unit,
    onToggleDashboardGrid: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusDotColor = when (aapStatus) {
        is AapConnectionStatus.Streaming -> palette.iosGreen
        is AapConnectionStatus.Listening,
        is AapConnectionStatus.Connecting,
        is AapConnectionStatus.Handshake -> palette.iosOrange
        is AapConnectionStatus.Error -> palette.iosRed
        AapConnectionStatus.Idle -> palette.textSecondary.copy(alpha = 0.5f)
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(86.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(palette.dockBackground)
            .border(1.dp, palette.dockBorder, RoundedCornerShape(28.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // CarPlay Status Cluster (Clock + Signal + Battery + HUR Indicator)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = clockText,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SignalCellularAlt,
                    contentDescription = null,
                    tint = palette.textPrimary,
                    modifier = Modifier.size(13.dp)
                )
                Icon(
                    imageVector = Icons.Rounded.Wifi,
                    contentDescription = null,
                    tint = palette.iosBlue,
                    modifier = Modifier.size(13.dp)
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(statusDotColor)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.BatteryStd,
                    contentDescription = null,
                    tint = palette.iosGreen,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "$batteryPercent%",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = OutfitFontFamily,
                    color = palette.textSecondary
                )
            }
        }

        // 3 Recent App Squircle Shortcuts
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DockAppIcon(
                icon = Icons.Rounded.DirectionsCar,
                contentDescription = "Android Auto HUR",
                colors = listOf(Color(0xFF0A84FF), Color(0xFF30D158)),
                isActive = currentPage == CarPlaySurfacePage.AA_PROJECTION,
                palette = palette,
                testTag = "dock_aa_projection",
                onClick = { onSelectPage(CarPlaySurfacePage.AA_PROJECTION) }
            )
            DockAppIcon(
                icon = Icons.Rounded.Navigation,
                contentDescription = "Maps & GPS",
                colors = listOf(Color(0xFF30D158), Color(0xFF0A84FF)),
                isActive = currentPage == CarPlaySurfacePage.MAPS_GPS,
                palette = palette,
                testTag = "dock_maps",
                onClick = { onSelectPage(CarPlaySurfacePage.MAPS_GPS) }
            )
            DockAppIcon(
                icon = Icons.Rounded.MusicNote,
                contentDescription = "Music",
                colors = listOf(Color(0xFFFF375F), Color(0xFFFF6B47)),
                isActive = currentPage == CarPlaySurfacePage.MUSIC,
                palette = palette,
                testTag = "dock_music",
                onClick = { onSelectPage(CarPlaySurfacePage.MUSIC) }
            )
        }

        // Signature CarPlay Home / Split-Screen Switcher
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(palette.cardSurfaceElevated)
                    .border(1.dp, palette.dockBorder, CircleShape)
                    .clickable { onSelectPage(CarPlaySurfacePage.SETTINGS) }
                    .testTag("dock_settings"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "تنظیمات HUR",
                    tint = palette.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (currentPage == CarPlaySurfacePage.DASHBOARD || currentPage == CarPlaySurfacePage.APP_GRID) {
                            palette.iosBlue.copy(alpha = 0.24f)
                        } else {
                            palette.cardSurfaceElevated
                        }
                    )
                    .border(1.dp, palette.dockBorder, RoundedCornerShape(16.dp))
                    .clickable(onClick = onToggleDashboardGrid)
                    .testTag("dock_home_switcher"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (currentPage == CarPlaySurfacePage.DASHBOARD) {
                        Icons.Rounded.Apps
                    } else {
                        Icons.Rounded.Dashboard
                    },
                    contentDescription = "تغییر نمای داشبورد و اپلیکیشن‌ها",
                    tint = palette.textPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun CarPlayCompactStatusBar(
    clockText: String,
    batteryPercent: Int,
    aapStatus: AapConnectionStatus,
    palette: CarPlayPalette,
    modifier: Modifier = Modifier
) {
    val (statusLabel, dotColor) = when (aapStatus) {
        is AapConnectionStatus.Streaming -> "Android Auto Active" to palette.iosGreen
        is AapConnectionStatus.Listening -> "Listening :${aapStatus.port}" to palette.iosOrange
        is AapConnectionStatus.Connecting -> "Connecting…" to palette.iosOrange
        is AapConnectionStatus.Handshake -> "Handshake…" to palette.iosTeal
        is AapConnectionStatus.Error -> "Standby" to palette.iosRed
        AapConnectionStatus.Idle -> "CarPlay HUR" to palette.iosBlue
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(palette.dockBackground)
            .border(1.dp, palette.dockBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = clockText,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = OutfitFontFamily,
                color = palette.textSecondary
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Wifi,
                contentDescription = null,
                tint = palette.iosBlue,
                modifier = Modifier.size(16.dp)
            )
            Icon(
                imageVector = Icons.Rounded.BatteryStd,
                contentDescription = null,
                tint = palette.iosGreen,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "$batteryPercent%",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = OutfitFontFamily,
                color = palette.textPrimary
            )
        }
    }
}

@Composable
fun CarPlayBottomDock(
    currentPage: CarPlaySurfacePage,
    palette: CarPlayPalette,
    onSelectPage: (CarPlaySurfacePage) -> Unit,
    onToggleDashboardGrid: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(palette.dockBackground)
            .border(1.dp, palette.dockBorder, RoundedCornerShape(26.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DockAppIcon(
            icon = Icons.Rounded.DirectionsCar,
            contentDescription = "Android Auto HUR",
            colors = listOf(Color(0xFF0A84FF), Color(0xFF30D158)),
            isActive = currentPage == CarPlaySurfacePage.AA_PROJECTION,
            palette = palette,
            testTag = "bottom_dock_aa",
            onClick = { onSelectPage(CarPlaySurfacePage.AA_PROJECTION) }
        )
        DockAppIcon(
            icon = Icons.Rounded.Navigation,
            contentDescription = "Maps",
            colors = listOf(Color(0xFF30D158), Color(0xFF0A84FF)),
            isActive = currentPage == CarPlaySurfacePage.MAPS_GPS,
            palette = palette,
            testTag = "bottom_dock_maps",
            onClick = { onSelectPage(CarPlaySurfacePage.MAPS_GPS) }
        )
        DockAppIcon(
            icon = Icons.Rounded.MusicNote,
            contentDescription = "Music",
            colors = listOf(Color(0xFFFF375F), Color(0xFFFF6B47)),
            isActive = currentPage == CarPlaySurfacePage.MUSIC,
            palette = palette,
            testTag = "bottom_dock_music",
            onClick = { onSelectPage(CarPlaySurfacePage.MUSIC) }
        )
        DockAppIcon(
            icon = Icons.Rounded.Settings,
            contentDescription = "Settings",
            colors = listOf(Color(0xFF636366), Color(0xFF3A3A3C)),
            isActive = currentPage == CarPlaySurfacePage.SETTINGS,
            palette = palette,
            testTag = "bottom_dock_settings",
            onClick = { onSelectPage(CarPlaySurfacePage.SETTINGS) }
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(palette.cardSurfaceElevated)
                .border(1.dp, palette.dockBorder, RoundedCornerShape(14.dp))
                .clickable(onClick = onToggleDashboardGrid)
                .testTag("bottom_dock_switcher"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (currentPage == CarPlaySurfacePage.DASHBOARD) {
                    Icons.Rounded.Apps
                } else {
                    Icons.Rounded.Dashboard
                },
                contentDescription = "تغییر نمای کارپلی",
                tint = palette.textPrimary
            )
        }
    }
}

@Composable
private fun DockAppIcon(
    icon: ImageVector,
    contentDescription: String,
    colors: List<Color>,
    isActive: Boolean,
    palette: CarPlayPalette,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(2.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(colors))
                .border(
                    width = if (isActive) 2.dp else 1.dp,
                    color = if (isActive) Color.White else Color.White.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(25.dp)
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .width(if (isActive) 16.dp else 0.dp)
                .height(3.dp)
                .clip(CircleShape)
                .background(palette.textPrimary)
        )
    }
}
