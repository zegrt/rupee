package com.zegrt.rupee.ui.theme

import androidx.compose.ui.graphics.Color

// Light-mode base
val Ink = Color(0xFF111111)            // primary text on Paper
val Paper = Color(0xFFF7F1E8)          // background / surface
val Clay = Color(0xFFCC7A3B)           // primary accent (works in both modes)
val Sage = Color(0xFF5E7A61)           // secondary accent (works in both modes)
val Mist = Color(0xFFE8DFD1)           // light surface variant (cards, dividers)

// Dark-mode-specific surfaces, tuned to feel like the Paper palette inverted —
// warm near-black backgrounds, soft warm-grey text. Avoids the pure-black look
// that makes Compose cards float weirdly against true OLED black.
val InkSurface = Color(0xFF1A1714)       // slightly warmer than pure ink, used for surface
val InkSurfaceVariant = Color(0xFF2A2521) // raised cards / outline tone in dark
val WarmGrey = Color(0xFFEFE6D8)         // body text on dark
val DimGrey = Color(0xFFB3A99A)          // muted text / outlineVariant in dark
val Dust = Color(0xFFD8CFC0)             // light surface variant in light (outlineVariant)

