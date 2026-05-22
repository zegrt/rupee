package com.zegrt.rupee.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reusable loading-placeholder primitives. Used on Home/Inbox/Transactions
 * during the brief window between first composition and the dashboard flow's
 * first emission — `HomeUiState.isSeeding == true`. Without these the surfaces
 * flash their "Nothing to review." / empty copy for a beat, which reads as
 * "broken" to first-time users (backlog COLDSTART).
 *
 * Theme-native: pills are `surfaceVariant` with a slow alpha pulse — no
 * gradient sweep, no extra-flavor glyphs. Honest skeleton, not a magic show.
 */

@Composable
private fun shimmerAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "skeleton-shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            // 900ms one-way; reverse gives 1.8s/cycle — slow enough to read
            // as a heartbeat, fast enough that the placeholder doesn't look
            // frozen if the flow hangs.
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton-shimmer-alpha",
    )
    return alpha
}

@Composable
fun SkeletonPill(
    width: Dp,
    height: Dp = 12.dp,
    modifier: Modifier = Modifier,
) {
    val alpha = shimmerAlpha()
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .alpha(alpha)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/**
 * Approximates a row in RecentActivity / Review / Transactions: a thicker
 * "merchant" pill, a thinner "subline" pill, and a right-aligned amount
 * pill. Renders three of these in a Column with dividers and you have a
 * convincing list placeholder.
 */
@Composable
fun SkeletonListRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonPill(width = 160.dp, height = 14.dp)
            SkeletonPill(width = 120.dp, height = 10.dp)
        }
        SkeletonPill(width = 64.dp, height = 14.dp)
    }
}

/**
 * Drop-in vertical stack of [count] skeleton rows separated by small
 * spacers. Suitable for wrapping inside any Card-shaped container.
 */
@Composable
fun SkeletonRowStack(
    count: Int = 3,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) {
            SkeletonListRow()
            if (it < count - 1) {
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}
