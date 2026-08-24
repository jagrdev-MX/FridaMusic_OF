package com.jagr.fridamusic.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.materialkolor.PaletteStyle
import com.materialkolor.ktx.themeColor
import com.materialkolor.rememberDynamicColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    pureBlack: Boolean = false,
    dynamicTheme: Boolean = false,
    artworkUrl: String? = null,
    content: @Composable () -> Unit,
) {
    val dynamicSeed = rememberArtworkSeedColor(
        enabled = dynamicTheme,
        artworkUrl = artworkUrl,
    )
    val dynamicColorScheme = if (dynamicTheme) {
        rememberDynamicColorScheme(
            seedColor = dynamicSeed,
            isDark = darkTheme,
            isAmoled = darkTheme && pureBlack,
            style = PaletteStyle.Content,
        )
    } else {
        null
    }
    val colorScheme = when {
        dynamicColorScheme != null && darkTheme -> dynamicColorScheme.copy(
            onBackground = DarkTextPrimary,
            onSurface = DarkTextPrimary,
        )
        dynamicColorScheme != null -> dynamicColorScheme
        darkTheme && pureBlack -> DarkColorScheme.copy(
            background = Color.Black,
            surface = Color(0xFF0A0A0A),
            surfaceVariant = Color(0xFF111111),
            surfaceContainerHighest = Color(0xFF111111),
        )
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
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
        content = content,
    )
}

@Composable
private fun rememberArtworkSeedColor(
    enabled: Boolean,
    artworkUrl: String?,
): Color {
    val context = LocalContext.current
    var seedColor by remember { mutableStateOf(FridaPink) }

    LaunchedEffect(enabled, artworkUrl) {
        if (!enabled || artworkUrl.isNullOrBlank()) {
            seedColor = FridaPink
            return@LaunchedEffect
        }

        seedColor = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .size(192)
                    .allowHardware(false)
                    .build()
                context.imageLoader.execute(request).image
                    ?.toBitmap()
                    ?.asImageBitmap()
                    ?.themeColor(fallback = FridaPink)
            }.getOrNull()
        } ?: FridaPink
    }

    return seedColor
}
