package com.banner.inject.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

private val BG = Color(0xFF121212)
private val SURFACE_VAR = Color(0xFF1E1E1E)
private val ON_SURFACE = Color(0xFFE0E0E0)

private fun buildScheme(accent: Color): ColorScheme {
    val container = Color(
        red = accent.red * 0.45f,
        green = accent.green * 0.45f,
        blue = accent.blue * 0.45f,
        alpha = 1f
    )
    val onContainer = Color(
        red = (accent.red + (1f - accent.red) * 0.35f).coerceAtMost(1f),
        green = (accent.green + (1f - accent.green) * 0.35f).coerceAtMost(1f),
        blue = (accent.blue + (1f - accent.blue) * 0.35f).coerceAtMost(1f),
        alpha = 1f
    )
    val secondary = Color(
        red = (accent.red + (1f - accent.red) * 0.3f).coerceAtMost(1f),
        green = (accent.green + (1f - accent.green) * 0.3f).coerceAtMost(1f),
        blue = (accent.blue + (1f - accent.blue) * 0.3f).coerceAtMost(1f),
        alpha = 1f
    )
    val lum = accent.red * 0.299f + accent.green * 0.587f + accent.blue * 0.114f
    val onPrimary = if (lum > 0.5f) Color.Black else Color.White

    return darkColorScheme(
        primary            = accent,
        onPrimary          = onPrimary,
        primaryContainer   = container,
        onPrimaryContainer = onContainer,
        secondary          = secondary,
        onSecondary        = Color.Black,
        background         = BG,
        onBackground       = ON_SURFACE,
        surface            = BG,
        onSurface          = ON_SURFACE,
        surfaceVariant     = SURFACE_VAR,
        onSurfaceVariant   = Color(0xFFAAAAAA),
        outline            = Color(0xFF444444),
        error              = Color(0xFFFF5252),
        onError            = Color.Black
    )
}

@Composable
fun BannersComponentInjectorTheme(
    accentColor: Color = ThemePrefs.DEFAULT,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = remember(accentColor) { buildScheme(accentColor) },
        content = content
    )
}
