package com.zegrt.rupee.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Clay,
    onPrimary = Paper,
    primaryContainer = Mist,
    onPrimaryContainer = Ink,
    secondary = Sage,
    onSecondary = Paper,
    secondaryContainer = Mist,
    onSecondaryContainer = Ink,
    tertiary = Sage,
    onTertiary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Mist,
    onSurfaceVariant = Ink,
    outlineVariant = Dust,
)

private val DarkColors = darkColorScheme(
    primary = Clay,
    onPrimary = Ink,
    primaryContainer = InkSurfaceVariant,
    onPrimaryContainer = WarmGrey,
    secondary = Sage,
    onSecondary = Ink,
    secondaryContainer = InkSurfaceVariant,
    onSecondaryContainer = WarmGrey,
    tertiary = Sage,
    onTertiary = Ink,
    background = Ink,
    onBackground = WarmGrey,
    surface = InkSurface,
    onSurface = WarmGrey,
    surfaceVariant = InkSurfaceVariant,
    onSurfaceVariant = DimGrey,
    outlineVariant = InkSurfaceVariant,
)

/**
 * Follows the system light/dark setting via [isSystemInDarkTheme]. No in-app
 * toggle for now — Android's system preference covers ~95% of users and adding
 * a manual override is a settings + preferences-store lift we can defer until
 * someone actually asks.
 */
@Composable
fun RupeeTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content,
    )
}

