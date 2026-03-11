package com.banner.inject.ui.theme

import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ── Dark palette ──────────────────────────────────────────────────────────────
private val DARK_BG          = Color(0xFF121212)
private val DARK_SURFACE_VAR = Color(0xFF1E1E1E)
private val DARK_ON_SURFACE  = Color(0xFFE0E0E0)

// ── AMOLED palette ────────────────────────────────────────────────────────────
private val AMOLED_BG          = Color(0xFF000000)
private val AMOLED_SURFACE_VAR = Color(0xFF111111)
private val AMOLED_ON_SURFACE  = Color(0xFFEEEEEE) // slightly brighter for pure-black contrast

private fun buildDarkScheme(accent: Color, amoled: Boolean): ColorScheme {
    val bg           = if (amoled) AMOLED_BG          else DARK_BG
    val surfaceVar   = if (amoled) AMOLED_SURFACE_VAR else DARK_SURFACE_VAR
    val onSurface    = if (amoled) AMOLED_ON_SURFACE  else DARK_ON_SURFACE
    val onSurfaceVar = if (amoled) Color(0xFFCCCCCC)  else Color(0xFFAAAAAA)
    val outline      = if (amoled) Color(0xFF333333)   else Color(0xFF444444)

    val container = Color(
        red   = accent.red   * 0.45f,
        green = accent.green * 0.45f,
        blue  = accent.blue  * 0.45f,
        alpha = 1f
    )
    val onContainer = Color(
        red   = (accent.red   + (1f - accent.red)   * 0.35f).coerceAtMost(1f),
        green = (accent.green + (1f - accent.green) * 0.35f).coerceAtMost(1f),
        blue  = (accent.blue  + (1f - accent.blue)  * 0.35f).coerceAtMost(1f),
        alpha = 1f
    )
    val secondary = Color(
        red   = (accent.red   + (1f - accent.red)   * 0.3f).coerceAtMost(1f),
        green = (accent.green + (1f - accent.green) * 0.3f).coerceAtMost(1f),
        blue  = (accent.blue  + (1f - accent.blue)  * 0.3f).coerceAtMost(1f),
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
        tertiary           = Color(0xFF66BB6A),
        onTertiary         = Color.Black,
        background         = bg,
        onBackground       = onSurface,
        surface            = bg,
        onSurface          = onSurface,
        surfaceVariant     = surfaceVar,
        onSurfaceVariant   = onSurfaceVar,
        outline            = outline,
        error              = Color(0xFFFF5252),
        onError            = Color.Black
    )
}

private fun buildLightScheme(accent: Color): ColorScheme {
    val lum = accent.red * 0.299f + accent.green * 0.587f + accent.blue * 0.114f
    // Darken very bright accents so they remain readable on white surfaces
    val adjustedAccent = if (lum > 0.65f) {
        Color(
            red   = accent.red   * 0.65f,
            green = accent.green * 0.65f,
            blue  = accent.blue  * 0.65f,
            alpha = 1f
        )
    } else accent
    val adjLum = adjustedAccent.red * 0.299f + adjustedAccent.green * 0.587f + adjustedAccent.blue * 0.114f
    val onPrimary = if (adjLum > 0.5f) Color.Black else Color.White

    val container = Color(
        red   = adjustedAccent.red   * 0.2f + 0.8f,
        green = adjustedAccent.green * 0.2f + 0.8f,
        blue  = adjustedAccent.blue  * 0.2f + 0.8f,
        alpha = 1f
    )
    val secondary = Color(
        red   = (adjustedAccent.red   * 0.6f + 0.3f).coerceAtMost(1f),
        green = (adjustedAccent.green * 0.6f + 0.3f).coerceAtMost(1f),
        blue  = (adjustedAccent.blue  * 0.6f + 0.3f).coerceAtMost(1f),
        alpha = 1f
    )

    return lightColorScheme(
        primary            = adjustedAccent,
        onPrimary          = onPrimary,
        primaryContainer   = container,
        onPrimaryContainer = adjustedAccent,
        secondary          = secondary,
        onSecondary        = Color.White,
        tertiary           = Color(0xFF388E3C),
        onTertiary         = Color.White,
        background         = Color(0xFFF5F5F5),
        onBackground       = Color(0xFF212121),
        surface            = Color(0xFFFFFFFF),
        onSurface          = Color(0xFF212121),
        surfaceVariant     = Color(0xFFEEEEEE),
        onSurfaceVariant   = Color(0xFF555555),
        outline            = Color(0xFFBBBBBB),
        error              = Color(0xFFB00020),
        onError            = Color.White
    )
}

@Composable
fun BannersComponentInjectorTheme(
    accentColor: Color = ThemePrefs.DEFAULT,
    darkMode: Boolean = true,
    amoled: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = remember(accentColor, darkMode, amoled, dynamicColor) {
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (darkMode) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            darkMode -> buildDarkScheme(accentColor, amoled)
            else -> buildLightScheme(accentColor)
        }
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
