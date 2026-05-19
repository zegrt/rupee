package com.zegrt.rupee.ui.theme

import androidx.compose.ui.graphics.Color

// vwfndr flavor palette — instrument, not application.
// Pure black, pure white, one electric lime that means "active state."
// No tints. No gradients. No warmth. Color is *function*, not decoration.

val VwBlack = Color(0xFF000000)            // background — pure, not warm
val VwInk = Color(0xFF0A0A0A)              // raised surface (cells, cards)
val VwLine = Color(0xFF1C1C1C)             // exposed grid lines
val VwDim = Color(0xFF2A2A2A)              // sub-surface
val VwMuted = Color(0xFF606060)            // inactive labels
val VwBody = Color(0xFFBFBFBF)             // body text
val VwWhite = Color(0xFFF5F5F5)            // primary text
val VwLime = Color(0xFFCFFF5C)             // THE accent — state, active, alert
val VwLimeInk = Color(0xFF0A0A0A)          // on-lime text (the shutter "+")
val VwRed = Color(0xFFFF3B2F)              // DESTROY / failure — used sparingly
val VwAmber = Color(0xFFFFC847)            // warning state

// Light mode in vwfndr is unusual — we keep it dark anyway. The whole product
// is anti-soft. Even if the user is in a daylight setting, the viewfinder
// stays a viewfinder. So both schemes use the same dark palette.
