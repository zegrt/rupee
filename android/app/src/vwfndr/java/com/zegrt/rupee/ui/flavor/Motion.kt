package com.zegrt.rupee.ui.flavor

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha

/**
 * vwfndr-flavor motion helpers. Mechanical, parameterised. No easing
 * curves — just snap or pulse. Live indicators pulse at a fixed cadence,
 * the way a Geiger counter pulses.
 */

/**
 * Steady pulse on alpha — 1.0 → 0.4 → 1.0 over 1500ms. Apply to a
 * status indicator so the dot reads as "the pipeline is live."
 */
@Composable
internal fun Modifier.livePulse(durationMs: Int = 1500): Modifier {
    val transition = rememberInfiniteTransition(label = "live-pulse")
    val alphaValue by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "live-pulse-alpha",
    )
    return this.alpha(alphaValue)
}
