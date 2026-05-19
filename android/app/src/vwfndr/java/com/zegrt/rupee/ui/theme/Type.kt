package com.zegrt.rupee.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// vwfndr type system — grotesk for headlines, mono for readouts.
// Everything is sized for technical legibility, not editorial seduction.
// Labels are ALWAYS rendered uppercase by the consuming surface (we set
// big letterSpacing here as a hint).
private val display = FontFamily.SansSerif      // grotesk
private val mono = FontFamily.Monospace         // mono readouts — like an EXIF strip

val Typography = Typography(
    // Huge, mono, all-caps amounts and codes. ISO-8960 / 1/8000s energy.
    displayLarge = TextStyle(
        fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 56.sp,
        lineHeight = 60.sp, letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 40.sp,
        lineHeight = 44.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 28.sp,
        lineHeight = 32.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = display, fontWeight = FontWeight.Black, fontSize = 26.sp,
        lineHeight = 30.sp, letterSpacing = 0.5.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = display, fontWeight = FontWeight.Black, fontSize = 20.sp,
        lineHeight = 24.sp, letterSpacing = 0.5.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 16.sp,
        lineHeight = 20.sp, letterSpacing = 1.0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 18.sp,
        lineHeight = 22.sp, letterSpacing = 1.0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 14.sp,
        lineHeight = 18.sp, letterSpacing = 1.2.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
        lineHeight = 16.sp, letterSpacing = 1.4.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = mono, fontWeight = FontWeight.Normal, fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = mono, fontWeight = FontWeight.Normal, fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = mono, fontWeight = FontWeight.Normal, fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
    // The signature vwfndr move — tiny tracked uppercase labels.
    // PRESET · MODE · FOCUS · SPEED · ISO.
    labelLarge = TextStyle(
        fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 11.sp,
        lineHeight = 14.sp, letterSpacing = 1.8.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 10.sp,
        lineHeight = 12.sp, letterSpacing = 2.0.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 9.sp,
        lineHeight = 12.sp, letterSpacing = 2.2.sp,
    ),
)
