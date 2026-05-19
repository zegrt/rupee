package com.zegrt.rupee.ui.flavor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/**
 * vwfndr-flavor decorative glyphs. Every surface should feel like a
 * piece of equipment — these glyphs are the equipment's chrome.
 *
 * All glyphs draw purely on Canvas (no fonts/icons) so they read
 * crisply at any size.
 */

/**
 * EV-style tick scale. A short horizontal rule of tick marks with a taller
 * center mark and a small needle/indicator. Mirrors vwfndr's EV bar above
 * the viewfinder.
 *
 * In a wallet context, this reads as "spend velocity vs. baseline" — the
 * indicator nudges left/right from zero depending on whether the user is
 * over or under their baseline.
 *
 * [needleFrac] is in -1f..1f; 0 is the center mark.
 */
@Composable
internal fun EvScale(
    modifier: Modifier = Modifier,
    needleFrac: Float = 0f,
    wiggleAmount: Float = 0f,
    activeColor: Color = Color(0xFFCFFF5C),
    inactiveColor: Color = Color(0xFF606060),
) {
    // Wiggle: when >0, the needle jitters ±wiggleAmount around the input
    // position with two unsynced sine waves so it never reads as a clean
    // oscillation. Simulates a noisy live reading.
    val live = if (wiggleAmount > 0f) {
        val tx = rememberInfiniteTransition(label = "ev-wiggle")
        val a by tx.animateFloat(
            initialValue = -1f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1300, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "ev-wiggle-a",
        )
        val b by tx.animateFloat(
            initialValue = -1f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 870, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "ev-wiggle-b",
        )
        needleFrac + wiggleAmount * (a * 0.6f + b * 0.4f)
    } else needleFrac
    Canvas(modifier) {
        val cy = size.height / 2f
        val w = size.width
        val tickCount = 13
        val gap = w / (tickCount - 1)
        for (i in 0 until tickCount) {
            val x = i * gap
            val center = (i == tickCount / 2)
            val tickH = if (center) size.height * 0.8f else size.height * 0.35f
            drawLine(
                color = if (center) activeColor else inactiveColor,
                start = Offset(x, cy - tickH / 2f),
                end = Offset(x, cy + tickH / 2f),
                strokeWidth = if (center) 2f else 1f,
                cap = StrokeCap.Square,
            )
        }
        // Needle — driven by [live] which is [needleFrac] + wiggle.
        val needleX = (w / 2f) + (live.coerceIn(-1f, 1f) * (w / 2f))
        val triH = size.height * 0.5f
        drawLine(
            color = activeColor,
            start = Offset(needleX, cy - triH),
            end = Offset(needleX, cy + triH),
            strokeWidth = 2f,
            cap = StrokeCap.Square,
        )
    }
}

/**
 * Range chip — vwfndr's bottom-right aspect-ratio chip (`1:2`, `2:3`)
 * translated to a wallet's time-range selector. Bordered, mono, lime-tinted
 * when currently active.
 */
@Composable
internal fun RangeChip(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    val tint = if (active) Color(0xFFCFFF5C) else Color(0xFFBFBFBF)
    Box(
        modifier = modifier
            .border(1.dp, tint)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
        )
    }
}

/**
 * Four small `+` registration crosses, one in each corner of the parent.
 * Pure brand chrome — vwfndr scatters these around the content credentials
 * receipt card. They communicate "this is framed equipment output."
 */
@Composable
internal fun RegistrationCrosses(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF606060),
    size: androidx.compose.ui.unit.Dp = 8.dp,
    inset: androidx.compose.ui.unit.Dp = 8.dp,
) {
    Box(modifier) {
        Cross(
            color = color,
            modifier = Modifier
                .size(size)
                .align(Alignment.TopStart)
                .padding(start = inset, top = inset),
        )
        Cross(
            color = color,
            modifier = Modifier
                .size(size)
                .align(Alignment.TopEnd)
                .padding(end = inset, top = inset),
        )
        Cross(
            color = color,
            modifier = Modifier
                .size(size)
                .align(Alignment.BottomStart)
                .padding(start = inset, bottom = inset),
        )
        Cross(
            color = color,
            modifier = Modifier
                .size(size)
                .align(Alignment.BottomEnd)
                .padding(end = inset, bottom = inset),
        )
    }
}

@Composable
private fun Cross(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val len = this.size.minDimension * 0.45f
        drawLine(color, Offset(cx - len, cy), Offset(cx + len, cy), 1.5f, StrokeCap.Square)
        drawLine(color, Offset(cx, cy - len), Offset(cx, cy + len), 1.5f, StrokeCap.Square)
    }
}

/**
 * "cr" provenance monogram — vwfndr's content-credentials badge in the
 * top-left corner of every photo receipt. In a wallet context this reads
 * as "this transaction has been cryptographically signed."
 *
 * Small bordered rounded-square with `cr` inside.
 */
@Composable
internal fun CrMonogram(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFFCFFF5C),
) {
    Box(
        modifier = modifier
            .border(1.5.dp, tint, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = "cr",
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}

/**
 * Lime status pip — small filled circle. Indicates active/signed/recording.
 */
@Composable
internal fun StatusPip(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFCFFF5C),
    size: androidx.compose.ui.unit.Dp = 6.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

/**
 * Compact key/value row used as edge metadata along the side of a
 * viewfinder frame. Renders horizontally — the rotation should be applied
 * at the call site with `Modifier.rotate`.
 */
@Composable
internal fun EdgeMeta(
    key: String,
    value: String,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF606060),
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = key,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
        Text(
            text = "·",
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}
