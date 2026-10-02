package dev.airhealth.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The app's colours.
 *
 * Dark by default rather than following the system, because that is what was asked
 * for. [AirHealthTheme] takes a flag, so switching to `isSystemInDarkTheme()` is a
 * one-line change at the call site if that is wanted later.
 *
 * The neutrals carry a slight blue bias rather than being pure grey, and the accent is
 * the same blue the data uses, so a chart or a value never introduces a hue the rest of
 * the screen does not already have.
 */
private val Accent = Color(0xFF3987E5)
private val AccentLight = Color(0xFF2A78D6)

private val Dark: ColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1C4C85),
    onPrimaryContainer = Color(0xFFD6E6FB),
    secondary = Color(0xFF8E99A8),
    background = Color(0xFF101318),
    onBackground = Color(0xFFF3F5F8),
    surface = Color(0xFF181C22),
    onSurface = Color(0xFFF3F5F8),
    surfaceVariant = Color(0xFF232931),
    onSurfaceVariant = Color(0xFFADB6C2),
    outline = Color(0xFF3C444F),
    outlineVariant = Color(0xFF2B313A),
    error = Color(0xFFE66767),
    onError = Color(0xFF2A0A0A),
)

private val Light: ColorScheme = lightColorScheme(
    primary = AccentLight,
    onPrimary = Color.White,
    background = Color(0xFFF4F5F7),
    onBackground = Color(0xFF11151B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF11151B),
    surfaceVariant = Color(0xFFECEEF1),
    onSurfaceVariant = Color(0xFF4D5663),
    outline = Color(0xFFB9BFC9),
    outlineVariant = Color(0xFFD5D9E0),
    error = Color(0xFFE34948),
)

@Composable
fun AirHealthTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
