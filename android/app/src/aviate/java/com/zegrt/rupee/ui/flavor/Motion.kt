package com.zegrt.rupee.ui.flavor

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle

/**
 * Aviate-flavor motion helpers. Sentimental + generous easing — Spotify
 * Wrapped / Strava-recap school. Numbers count up, cards slam in.
 */

/**
 * Animated rupee count-up. On first composition, animates from 0 → [target]
 * over 800ms with easeOutCubic — the signature Wrapped gesture.
 *
 * Formats with Indian lakh-style grouping: 12,34,567.
 */
@Composable
internal fun AnimatedRupees(
    target: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displayLarge,
    color: Color = Color.Unspecified,
    durationMs: Int = 800,
    prefix: String = "₹",
) {
    var animated by remember { mutableIntStateOf(0) }
    val current by animateIntAsState(
        targetValue = animated,
        animationSpec = tween(durationMillis = durationMs, easing = EaseOutCubic),
        label = "rupees",
    )
    LaunchedEffect(target) {
        animated = target
    }
    Text(
        text = "$prefix${formatInr(current)}",
        modifier = modifier,
        style = style,
        color = color,
    )
}

/**
 * Stamp slam-in modifier. On first composition, the stamp drops in from
 * scale 0 with a slight overshoot. Spring-physics easing — feels like a
 * stamp being pressed onto paper, then bouncing back.
 *
 * Usage: `Modifier.slamIn()` on the outermost element of a stamp box.
 */
@Composable
internal fun Modifier.slamIn(): Modifier {
    var entered by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.6f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "slam-scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 240, easing = EaseOutCubic),
        label = "slam-alpha",
    )
    LaunchedEffect(Unit) { entered = true }
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}

/**
 * Staggered fade-in. Apply to each child of a reveal sequence (Wrapped
 * card body, hero stack). Each call gets a unique [index]; the modifier
 * delays its fade-in by `index * stepMs` so children appear in order.
 *
 * Fades 14dp up and 0→1 alpha over 240ms easeOutCubic. Subtle, generous,
 * unmistakably the Spotify Wrapped reveal moment.
 */
@Composable
internal fun Modifier.staggerFadeIn(index: Int, stepMs: Long = 150L): Modifier {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 240, easing = EaseOutCubic),
        label = "stagger-alpha-$index",
    )
    val translateY by animateFloatAsState(
        targetValue = if (visible) 0f else 14f,
        animationSpec = tween(durationMillis = 240, easing = EaseOutCubic),
        label = "stagger-y-$index",
    )
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * stepMs)
        visible = true
    }
    return this.graphicsLayer {
        this.alpha = alpha
        this.translationY = translateY
    }
}

private fun formatInr(value: Int): String {
    if (value < 1_000) return value.toString()
    // Lakh/crore grouping: last 3 digits, then groups of 2
    val s = value.toString()
    val last3 = s.takeLast(3)
    val rest = s.dropLast(3)
    val restGrouped = rest.reversed().chunked(2).joinToString(",").reversed()
    return "$restGrouped,$last3"
}
