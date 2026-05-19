package com.zegrt.rupee.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// No rounded corners. The grid is exposed. The shutter button (the only
// pill in the system) is set elsewhere — at this layer everything is square.
private val VwShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(2.dp),
)

private val Scheme = darkColorScheme(
    primary = VwLime,
    onPrimary = VwLimeInk,
    primaryContainer = VwInk,
    onPrimaryContainer = VwLime,
    secondary = VwWhite,
    onSecondary = VwBlack,
    secondaryContainer = VwInk,
    onSecondaryContainer = VwWhite,
    tertiary = VwAmber,
    onTertiary = VwBlack,
    tertiaryContainer = VwInk,
    onTertiaryContainer = VwAmber,
    background = VwBlack,
    onBackground = VwWhite,
    surface = VwBlack,
    onSurface = VwWhite,
    surfaceVariant = VwInk,
    onSurfaceVariant = VwBody,
    outline = VwLine,
    outlineVariant = VwLine,
    error = VwRed,
    onError = VwWhite,
    errorContainer = VwInk,
    onErrorContainer = VwRed,
)

// vwfndr refuses light mode — even in daylight, you're operating an
// instrument. The viewfinder is always dark.
@Composable
fun RupeeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = Typography,
        shapes = VwShapes,
        content = content,
    )
}
