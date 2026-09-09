package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val YTDarkColorScheme = darkColorScheme(
    primary = YouTubeRed,
    onPrimary = Color.White,
    primaryContainer = YTDarkSurfaceVariant,
    onPrimaryContainer = YTDarkTextPrimary,
    secondary = YTBlueVerified,
    onSecondary = Color.White,
    background = YTDarkBackground,
    onBackground = YTDarkTextPrimary,
    surface = YTDarkBackground,
    onSurface = YTDarkTextPrimary,
    surfaceContainer = YTDarkSurfaceContainer,
    surfaceContainerLow = YTDarkSurface,
    surfaceContainerHigh = YTDarkSurfaceVariant,
    surfaceContainerHighest = YTDarkSurfaceVariant,
    surfaceVariant = YTDarkSurfaceVariant,
    onSurfaceVariant = YTDarkTextSecondary,
    outline = YTDarkDivider,
    outlineVariant = YTDarkCardBorder
)

private val YTLightColorScheme = lightColorScheme(
    primary = YouTubeRed,
    onPrimary = Color.White,
    primaryContainer = YTLightSurfaceVariant,
    onPrimaryContainer = YTLightTextPrimary,
    secondary = YTBlueVerified,
    onSecondary = Color.White,
    background = YTLightBackground,
    onBackground = YTLightTextPrimary,
    surface = YTLightBackground,
    onSurface = YTLightTextPrimary,
    surfaceContainer = YTLightSurfaceContainer,
    surfaceContainerLow = YTLightSurface,
    surfaceContainerHigh = YTLightSurfaceVariant,
    surfaceContainerHighest = YTLightSurfaceVariant,
    surfaceVariant = YTLightSurfaceVariant,
    onSurfaceVariant = YTLightTextSecondary,
    outline = YTLightDivider,
    outlineVariant = YTLightCardBorder
)

@Composable
fun YouTubeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) YTDarkColorScheme else YTLightColorScheme
    val view = LocalView.current
    // SideEffect reads darkTheme + scheme; window writes are guarded so
    // unrelated recompositions don't re-hit the window (was every recompose).
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.let {
                val bg = colorScheme.background.toArgb()
                if (it.statusBarColor != bg) it.statusBarColor = bg
                if (it.navigationBarColor != bg) it.navigationBarColor = bg
                val controller = WindowCompat.getInsetsController(it, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Alias for compatibility
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    YouTubeTheme(darkTheme = darkTheme, content = content)
}
