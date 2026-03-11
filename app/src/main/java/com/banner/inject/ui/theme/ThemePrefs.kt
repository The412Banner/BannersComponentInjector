package com.banner.inject.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

object ThemePrefs {
    private const val PREFS_NAME = "bci_settings"
    private const val KEY_ACCENT = "accent_color"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_AMOLED = "amoled_mode"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"

    val DEFAULT = Color(0xFFFF6D00)

    val PRESETS = listOf(
        "Orange" to Color(0xFFFF6D00),
        "Blue"   to Color(0xFF2196F3),
        "Purple" to Color(0xFF9C27B0),
        "Green"  to Color(0xFF4CAF50),
        "Red"    to Color(0xFFF44336),
        "Teal"   to Color(0xFF00BCD4),
        "Pink"   to Color(0xFFE91E63),
        "Amber"  to Color(0xFFFFC107),
    )

    fun load(context: Context): Color {
        val argb = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_ACCENT, DEFAULT.toArgb())
        return Color(argb)
    }

    fun save(context: Context, color: Color) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_ACCENT, color.toArgb()).apply()
    }

    fun loadDarkMode(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK_MODE, true)

    fun saveDarkMode(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DARK_MODE, value).apply()

    fun loadAmoled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AMOLED, false)

    fun saveAmoled(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AMOLED, value).apply()

    fun loadDynamicColor(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DYNAMIC_COLOR, false)

    fun saveDynamicColor(context: Context, value: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()
}
