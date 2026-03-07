package com.banner.inject.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Orange = Color(0xFFFF6D00)
private val OrangeLight = Color(0xFFFF9E40)
private val Surface = Color(0xFF121212)
private val SurfaceVariant = Color(0xFF1E1E1E)
private val OnSurface = Color(0xFFE0E0E0)

private val DarkColors = darkColorScheme(
    primary = Orange,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF7A3200),
    onPrimaryContainer = OrangeLight,
    secondary = OrangeLight,
    onSecondary = Color.Black,
    background = Surface,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = Color(0xFFAAAAAA),
    outline = Color(0xFF444444),
    error = Color(0xFFFF5252),
    onError = Color.Black
)

@Composable
fun BannersComponentInjectorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
