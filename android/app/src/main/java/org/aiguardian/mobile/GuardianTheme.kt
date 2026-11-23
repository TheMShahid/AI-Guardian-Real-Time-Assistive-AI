package org.aiguardian.mobile

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal data class GuardianPalette(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val primary: Color,
    val secondary: Color,
    val text: Color,
    val muted: Color,
    val warning: Color
)

private val DarkPalette = GuardianPalette(
    background = Color(0xFF081521),
    surface = Color(0xFF122638),
    surfaceVariant = Color(0xFF1B354A),
    primary = Color(0xFF43D9D0),
    secondary = Color(0xFF00E676),
    text = Color(0xFFFFFFFF),
    muted = Color(0xFFAAC0D0),
    warning = Color(0xFFFFD180)
)

private val LightPalette = GuardianPalette(
    background = Color(0xFFF4F7FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE7EEF4),
    primary = Color(0xFF007F83),
    secondary = Color(0xFF087F50),
    text = Color(0xFF172B3A),
    muted = Color(0xFF526777),
    warning = Color(0xFF945500)
)

internal val LocalGuardianPalette =
    staticCompositionLocalOf { DarkPalette }

@Composable
internal fun GuardianTheme(
    themeMode: String,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()

    val useDarkTheme = when (themeMode) {
        "Light" -> false
        "Dark" -> true
        else -> systemDark
    }

    val palette = if (useDarkTheme) DarkPalette else LightPalette

    val scheme = if (useDarkTheme) {
        darkColorScheme(
            primary = palette.primary,
            secondary = palette.secondary,
            background = palette.background,
            surface = palette.surface,
            surfaceVariant = palette.surfaceVariant,
            onPrimary = palette.background,
            onBackground = palette.text,
            onSurface = palette.text,
            onSurfaceVariant = palette.muted
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            secondary = palette.secondary,
            background = palette.background,
            surface = palette.surface,
            surfaceVariant = palette.surfaceVariant,
            onPrimary = Color.White,
            onBackground = palette.text,
            onSurface = palette.text,
            onSurfaceVariant = palette.muted
        )
    }

    CompositionLocalProvider(
        LocalGuardianPalette provides palette
    ) {
        MaterialTheme(
            colorScheme = scheme,
            content = content
        )
    }
}
