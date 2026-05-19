package com.zegrt.rupee.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

// Aviate type system — heavy, editorial display weights for hero numbers;
// confident sans for body. Big contrast between display and body — that's
// the entire visual hierarchy. No serifs (consumer app, not magazine).
private val display = FontFamily.SansSerif
private val body = FontFamily.SansSerif

private val tightLine = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = display, fontWeight = FontWeight.Black, fontSize = 64.sp,
        lineHeight = 64.sp, letterSpacing = (-2).sp, lineHeightStyle = tightLine,
    ),
    displayMedium = TextStyle(
        fontFamily = display, fontWeight = FontWeight.Black, fontSize = 48.sp,
        lineHeight = 50.sp, letterSpacing = (-1.5).sp, lineHeightStyle = tightLine,
    ),
    displaySmall = TextStyle(
        fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp,
        lineHeight = 38.sp, letterSpacing = (-0.8).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 28.sp,
        lineHeight = 34.sp, letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    // Labels in Aviate are *uppercase-tracked overlines* — "MOST VISITED",
    // "FLIGHTS THIS ERA". Tiny, spaced, muted. Pure editorial signal.
    labelLarge = TextStyle(
        fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
        lineHeight = 18.sp, letterSpacing = 1.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
        lineHeight = 14.sp, letterSpacing = 1.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 10.sp,
        lineHeight = 14.sp, letterSpacing = 1.6.sp,
    ),
)
