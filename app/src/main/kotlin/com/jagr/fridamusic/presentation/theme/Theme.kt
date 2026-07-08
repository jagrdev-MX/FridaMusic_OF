package com.jagr.fridamusic.presentation.theme

import android.app.Activity
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

private val DarkColorScheme = darkColorScheme(
    primary = FridaPink,
    onPrimary = DarkTextInverse,
    primaryContainer = EchoLavender,
    onPrimaryContainer = DarkBgPrimary,
    secondary = FridaPurple,
    onSecondary = DarkTextPrimary,
    secondaryContainer = DarkBgElevation2,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = FridaBlue,

    background = DarkBgPrimary,
    onBackground = DarkTextPrimary,
    surface = DarkBgSecondary,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkBgElevation1,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainerHighest = DarkBgElevation1,

    outline = DarkTextDisabled,
    error = StateError,
    onError = DarkTextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = ModernPrimary,
    onPrimary = ModernOnPrimary,
    primaryContainer = ModernPrimaryContainer,
    onPrimaryContainer = ModernOnPrimaryContainer,
    secondary = ModernSecondary,
    onSecondary = ModernOnSecondary,
    secondaryContainer = ModernSecondaryContainer,
    onSecondaryContainer = ModernOnSecondaryContainer,
    tertiary = ModernTertiary,
    onTertiary = ModernOnTertiary,
    background = ModernBackground,
    onBackground = ModernOnBackground,
    surface = ModernSurface,
    onSurface = ModernOnSurface,
    surfaceVariant = ModernSurfaceVariant,
    onSurfaceVariant = ModernOnSurfaceVariant,
    outline = ModernOutline,
    surfaceContainerLowest = ModernSurfaceContainerLowest,
    surfaceContainerLow = ModernSurfaceContainerLow,
    surfaceContainer = ModernSurfaceContainer,
    surfaceContainerHigh = ModernSurfaceContainerHigh,
    surfaceContainerHighest = ModernSurfaceContainerHighest,
    surfaceBright = ModernSurfaceBright,
    surfaceDim = ModernSurfaceDim,
    error = StateError,
    onError = LightTextInverse
)

@Composable
fun FridaMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LiquidTypography,
        content = content
    )
}