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
    primary = CoralPrimary,
    onPrimary = Color(0xFF1F0803),
    primaryContainer = CoralContainer,
    onPrimaryContainer = Color(0xFFFFDAD1),
    secondary = AmberSecondary,
    onSecondary = Color(0xFF281800),
    secondaryContainer = AmberContainer,
    onSecondaryContainer = Color(0xFFFFDFB0),
    tertiary = AutoTurquoise,
    onTertiary = Color(0xFF002522),
    tertiaryContainer = AutoTurquoiseContainer,
    onTertiaryContainer = Color(0xFF9DF7EE),
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceHigh,
    surfaceContainerHigh = ObsidianSurfaceElevated,
    outline = ObsidianBorder,
    outlineVariant = Color(0xFF232333)
)

private val AvaLightColorScheme = lightColorScheme(
    primary = CoralPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = CoralContainerLight,
    onPrimaryContainer = Color(0xFF3E0B00),
    secondary = AmberSecondaryLight,
    onSecondary = Color.White,
    tertiary = Color(0xFF007B72),
    onTertiary = Color.White,
    background = IvoryBg,
    onBackground = TextPrimaryLight,
    surface = IvorySurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = IvorySurfaceHigh,
    onSurfaceVariant = TextSecondaryLight
)

val AvaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun AvaPlayerTheme(
    darkTheme: Boolean = true,
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
