package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val AvaDarkColorScheme = darkColorScheme(
    primary = IvoryInk,
    onPrimary = Color(0xFF14151B),
    primaryContainer = IvoryInkContainer,
    onPrimaryContainer = IvoryOnInkContainer,
    secondary = CrimsonHeartDark,
    onSecondary = Color.White,
    secondaryContainer = CrimsonHeartContainerDark,
    onSecondaryContainer = CrimsonOnContainerDark,
    tertiary = AutoTurquoise,
    onTertiary = Color(0xFF002622),
    tertiaryContainer = AutoTurquoiseContainer,
    onTertiaryContainer = Color(0xFFA2FAF0),
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceMid,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ObsidianSurfaceLow,
    surfaceContainer = ObsidianSurfaceMid,
    surfaceContainerHigh = ObsidianSurfaceHigh,
    surfaceContainerHighest = ObsidianSurfaceHighest,
    inverseSurface = MiniPillNightSurface,
    inverseOnSurface = MiniPillOnSurface,
    inversePrimary = CrimsonHeartDark,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineVariant
)

private val AvaLightColorScheme = lightColorScheme(
    primary = EditorialInk,
    onPrimary = Color.White,
    primaryContainer = EditorialInkContainer,
    onPrimaryContainer = EditorialOnInkContainer,
    secondary = CrimsonHeart,
    onSecondary = Color.White,
    secondaryContainer = CrimsonHeartContainer,
    onSecondaryContainer = CrimsonOnContainer,
    tertiary = SlateTealLight,
    onTertiary = Color.White,
    tertiaryContainer = SlateTealContainerLight,
    onTertiaryContainer = SlateTealOnContainerLight,
    background = PaperBg,
    onBackground = TextPrimaryLight,
    surface = PaperSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = PaperSurfaceMid,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = PaperBg,
    surfaceContainerLow = PaperSurfaceLow,
    surfaceContainer = PaperSurfaceMid,
    surfaceContainerHigh = PaperSurfaceHigh,
    surfaceContainerHighest = PaperSurfaceHighest,
    inverseSurface = MiniPillDarkSurface,
    inverseOnSurface = MiniPillOnSurface,
    inversePrimary = CrimsonHeart,
    outline = PaperOutline,
    outlineVariant = PaperOutlineVariant
)

val AvaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AvaPlayerTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> AvaDarkColorScheme
        else -> AvaLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AvaShapes,
        content = content
    )
}
