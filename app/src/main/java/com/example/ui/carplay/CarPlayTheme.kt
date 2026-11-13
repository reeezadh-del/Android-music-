package com.example.ui.carplay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.headunit.CarPlayThemeMode

data class CarPlayPalette(
    val isDark: Boolean,
    val dockBackground: Color,
    val dockBorder: Color,
    val cardSurface: Color,
    val cardSurfaceElevated: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val iosBlue: Color = Color(0xFF0A84FF),
    val iosGreen: Color = Color(0xFF30D158),
    val iosOrange: Color = Color(0xFFFF9F0A),
    val iosRed: Color = Color(0xFFFF453A),
    val iosPink: Color = Color(0xFFFF375F),
    val iosTeal: Color = Color(0xFF40C8E0),
    val iosIndigo: Color = Color(0xFF5E5CE6)
)

@Composable
fun rememberCarPlayPalette(themeMode: CarPlayThemeMode): CarPlayPalette {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        CarPlayThemeMode.NIGHT_GLASS -> true
        CarPlayThemeMode.DAY_FROST -> false
        CarPlayThemeMode.AUTO -> systemDark
    }
    return if (isDark) {
        CarPlayPalette(
            isDark = true,
            dockBackground = Color(0xCC111622),
            dockBorder = Color(0x33FFFFFF),
            cardSurface = Color(0xB8141A28),
            cardSurfaceElevated = Color(0xD91E2638),
            cardBorder = Color(0x2BFFFFFF),
            textPrimary = Color(0xFFF5F7FA),
            textSecondary = Color(0xFFAAB4C8)
        )
    } else {
        CarPlayPalette(
            isDark = false,
            dockBackground = Color(0xD9F0F4FA),
            dockBorder = Color(0x26000000),
            cardSurface = Color(0xCCF7F9FD),
            cardSurfaceElevated = Color(0xEBFFFFFF),
            cardBorder = Color(0x1F000000),
            textPrimary = Color(0xFF101521),
            textSecondary = Color(0xFF525E74),
            iosBlue = Color(0xFF007AFF),
            iosGreen = Color(0xFF28CD41),
            iosOrange = Color(0xFFFF9500),
            iosRed = Color(0xFFFF3B30),
            iosPink = Color(0xFFFF2D55),
            iosTeal = Color(0xFF32ADE6),
            iosIndigo = Color(0xFF5856D6)
        )
    }
}

@Composable
fun CarPlayWallpaperBackdrop(
    palette: CarPlayPalette,
    modifier: Modifier = Modifier
) {
    val drawableRes = if (palette.isDark) {
        R.drawable.img_carplay_night_bg_1794554521316
    } else {
        R.drawable.img_carplay_day_bg_1794554521048
    }
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (palette.isDark) Color(0x4D050811) else Color(0x26FFFFFF)
                )
        )
    }
}

@Composable
fun CarPlayGlassCard(
    palette: CarPlayPalette,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val baseModifier = modifier
        .clip(shape)
        .background(palette.cardSurface)
        .border(1.dp, palette.cardBorder, shape)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }

    Box(modifier = baseModifier, content = content)
}

@Composable
fun CarPlaySquircleAppTile(
    label: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    palette: CarPlayPalette,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 66.dp,
    showLabel: Boolean = true,
    badgeText: String? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = gradientColors.first().copy(alpha = 0.45f),
                        spotColor = gradientColors.last().copy(alpha = 0.45f)
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(gradientColors))
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.35f),
                        RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(iconSize * 0.52f)
                )
            }
            if (badgeText != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.iosRed)
                        .border(1.5.dp, Color.White, RoundedCornerShape(10.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        if (showLabel) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = palette.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
