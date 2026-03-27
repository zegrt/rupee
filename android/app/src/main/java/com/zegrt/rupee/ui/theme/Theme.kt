package com.zegrt.rupee.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Clay,
    secondary = Sage,
    background = Paper,
    surface = Paper,
    onPrimary = Paper,
    onSecondary = Paper,
    onBackground = Ink,
    onSurface = Ink,
)

private val DarkColors = darkColorScheme(
    primary = Clay,
    secondary = Sage,
    background = Ink,
    surface = Ink,
    onPrimary = Paper,
    onSecondary = Paper,
    onBackground = Paper,
    onSurface = Paper,
)

@Composable
fun RupeeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content,
    )
}

