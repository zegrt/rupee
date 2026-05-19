package com.zegrt.rupee.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// Aviate is generous-radius. Even tiny chips get 12dp. Big cards go 24dp.
private val AviShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val Light = lightColorScheme(
    primary = AviSky,
    onPrimary = AviPaper,
    primaryContainer = AviCloud,
    onPrimaryContainer = AviInk,
    secondary = AviPeachInk,
    onSecondary = AviPaper,
    secondaryContainer = AviPeach,
    onSecondaryContainer = AviPeachInk,
    tertiary = AviMossInk,
    onTertiary = AviPaper,
    tertiaryContainer = AviMoss,
    onTertiaryContainer = AviMossInk,
    background = AviPaper,
    onBackground = AviInk,
    surface = AviPaper,
    onSurface = AviInk,
    surfaceVariant = AviCloud,
    onSurfaceVariant = AviMuted,
    outline = AviHairline,
    outlineVariant = AviHairline,
    error = AviPeachInk,
    onError = AviPaper,
    errorContainer = AviPeach,
    onErrorContainer = AviPeachInk,
)

private val Dark = darkColorScheme(
    primary = AviSky,
    onPrimary = AviPaper,
    primaryContainer = AviSurfaceDarkRaised,
    onPrimaryContainer = AviWarmGreyDark,
    secondary = AviRose,
    onSecondary = AviInkDark,
    secondaryContainer = AviSurfaceDarkRaised,
    onSecondaryContainer = AviWarmGreyDark,
    tertiary = AviMoss,
    onTertiary = AviInkDark,
    tertiaryContainer = AviSurfaceDarkRaised,
    onTertiaryContainer = AviMoss,
    background = AviInkDark,
    onBackground = AviWarmGreyDark,
    surface = AviSurfaceDark,
    onSurface = AviWarmGreyDark,
    surfaceVariant = AviSurfaceDarkRaised,
    onSurfaceVariant = AviSkyDim,
    outline = AviHairlineDark,
    outlineVariant = AviHairlineDark,
)

@Composable
fun RupeeTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) Dark else Light
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        shapes = AviShapes,
        content = content,
    )
}
