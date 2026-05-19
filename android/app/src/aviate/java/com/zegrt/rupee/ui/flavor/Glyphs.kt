package com.zegrt.rupee.ui.flavor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Aviate-flavor decorative glyphs. Used to make every hero surface feel
 * identity-coded — orbit sparkles around hero stats, a dual-sparkle for
 * the Wrapped banner, page-dot paginator for shareables.
 *
 * All glyphs render purely with Canvas (no font/icon dependency) so they
 * stay sharp at any size.
 */

/**
 * Concentric orbital arcs with scattered pip-glyphs. Sits behind big numbers
 * on hero cards. Reads as "your year in orbit" — Aviate's recap card has
 * the same gesture for the giant "17 airports" stat.
 *
 * Designed to be drawn into a corner — anchor it with a fixed [Modifier.size].
 */
@Composable
internal fun OrbitalSparkles(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    density: Float = 1f,
) {
    Canvas(modifier) {
        val cx = size.width * 0.7f
        val cy = size.height * 0.35f
        val radii = listOf(
            size.minDimension * 0.18f,
            size.minDimension * 0.32f,
            size.minDimension * 0.50f,
        )
        // Faint concentric arcs (only the upper-left arcs to feel airy)
        radii.forEachIndexed { i, r ->
            drawArc(
                color = tint.copy(alpha = 0.10f + i * 0.04f),
                startAngle = 110f, sweepAngle = 200f, useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = 1.2f),
            )
        }
        // Pips: 4–6 small circles scattered along the rings
        data class Pip(val angleDeg: Float, val ring: Int, val radius: Float)
        val pips = listOf(
            Pip(190f, 0, 2.5f),
            Pip(240f, 1, 3.5f),
            Pip(160f, 2, 4f),
            Pip(290f, 2, 2.5f),
            Pip(120f, 1, 2f),
            Pip(220f, 0, 2f),
        ).take((6 * density).toInt().coerceAtLeast(3))
        pips.forEach { p ->
            val r = radii[p.ring]
            val theta = (p.angleDeg * PI / 180f).toFloat()
            val x = cx + r * cos(theta)
            val y = cy + r * sin(theta)
            drawCircle(
                color = tint.copy(alpha = 0.85f),
                radius = p.radius,
                center = Offset(x, y),
            )
        }
        // A single "spark" — bigger pip with a soft halo
        val sparkX = cx + radii[2] * cos((150f * PI / 180f).toFloat())
        val sparkY = cy + radii[2] * sin((150f * PI / 180f).toFloat())
        drawCircle(
            color = tint.copy(alpha = 0.18f),
            radius = 8f,
            center = Offset(sparkX, sparkY),
        )
        drawCircle(
            color = tint,
            radius = 3f,
            center = Offset(sparkX, sparkY),
        )
    }
}

/**
 * Aviate-style dual-sparkle glyph — two diamond-shaped 4-point stars,
 * one bigger one smaller, offset diagonally. Replaces the "magic wand"
 * we'd otherwise grab from a stock icon set.
 *
 * Used on the Wrapped promo banner and any "AI / magic" affordance.
 */
@Composable
internal fun DualSparkle(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    Canvas(modifier) {
        fun sparkle(cx: Float, cy: Float, radius: Float, alpha: Float = 1f) {
            // 4-point star drawn as a thin diamond + a perpendicular thinner diamond
            val ax = listOf(
                Offset(cx, cy - radius),
                Offset(cx + radius * 0.32f, cy - radius * 0.32f),
                Offset(cx + radius, cy),
                Offset(cx + radius * 0.32f, cy + radius * 0.32f),
                Offset(cx, cy + radius),
                Offset(cx - radius * 0.32f, cy + radius * 0.32f),
                Offset(cx - radius, cy),
                Offset(cx - radius * 0.32f, cy - radius * 0.32f),
            )
            // Draw as a polygon by filling triangles from center
            val color = tint.copy(alpha = alpha)
            for (i in ax.indices) {
                val a = ax[i]
                val b = ax[(i + 1) % ax.size]
                drawPath(
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(cx, cy)
                        lineTo(a.x, a.y)
                        lineTo(b.x, b.y)
                        close()
                    },
                    color = color,
                )
            }
        }
        val big = size.minDimension * 0.34f
        val small = size.minDimension * 0.20f
        sparkle(cx = size.width * 0.40f, cy = size.height * 0.46f, radius = big)
        sparkle(cx = size.width * 0.74f, cy = size.height * 0.72f, radius = small, alpha = 0.85f)
    }
}

/**
 * Page-dot paginator. Three dots, one widened into a pill — the
 * Spotify-Wrapped / Aviate-recap signature for "this is card N of M."
 *
 * Pass [count] and [selected]; height is fixed at 8dp.
 */
@Composable
internal fun PageDots(
    count: Int,
    selected: Int,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    Row(
        modifier = modifier.height(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(count) { i ->
            val active = i == selected
            Box(
                Modifier
                    .width(if (active) 22.dp else 8.dp)
                    .size(width = if (active) 22.dp else 8.dp, height = 8.dp)
                    .clip(if (active) RoundedCornerShape(50) else CircleShape)
                    .background(if (active) tint else tint.copy(alpha = 0.30f)),
            )
        }
    }
}
