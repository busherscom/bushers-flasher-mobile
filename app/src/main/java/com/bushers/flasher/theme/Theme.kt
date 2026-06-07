package com.bushers.flasher.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB4AB), // Pastel Red
    onPrimary = Color(0xFF690005),
    primaryContainer = Color(0xFF93000A),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFACC7FF), // Pastel Blue
    onSecondary = Color(0xFF002F67),
    secondaryContainer = Color(0xFF08428F),
    onSecondaryContainer = Color(0xFFD7E2FF),
    tertiary = Color(0xFFE4C600), // Pastel Yellow
    onTertiary = Color(0xFF393000),
    tertiaryContainer = Color(0xFF534600),
    onTertiaryContainer = Color(0xFFFFE24F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111318), // True dark background
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474E), // Better contrast for variant surfaces
    onSurfaceVariant = Color(0xFFC4C6D0), // Readable variant text
    outline = Color(0xFF8E9099)
)

private val LightColorScheme = lightColorScheme(
    primary = BushersPrimary,
    onPrimary = BushersOnPrimary,
    primaryContainer = BushersPrimaryContainer,
    onPrimaryContainer = BushersOnPrimaryContainer,
    secondary = BushersSecondary,
    onSecondary = BushersOnSecondary,
    secondaryContainer = BushersSecondaryContainer,
    onSecondaryContainer = BushersOnSecondaryContainer,
    tertiary = BushersTertiary,
    onTertiary = BushersOnTertiary,
    tertiaryContainer = BushersTertiaryContainer,
    onTertiaryContainer = BushersOnTertiaryContainer,
    error = BushersError,
    onError = BushersOnError,
    errorContainer = BushersErrorContainer,
    onErrorContainer = BushersOnErrorContainer,
    background = BushersBackground,
    onBackground = BushersOnBackground,
    surface = BushersSurface,
    onSurface = BushersOnSurface,
    surfaceVariant = BushersSurfaceVariant,
    onSurfaceVariant = BushersOnSurfaceVariant,
    outline = BushersOutline
)

@Composable
fun BushersFlasherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Disable dynamic color for brand consistency
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
