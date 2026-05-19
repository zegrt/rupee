package com.zegrt.rupee.ui.flavor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Aviate-style prototype of the wallet. Self-contained, no real data.
 * The whole point is to *feel* the design vocabulary applied to money:
 *   - Hero numbers as monuments, not as labels
 *   - Pastel surfaces used semantically (peach = negative, moss = positive)
 *   - Identity framing ("MEMBER SINCE", "RUPEE ERA") not utility framing
 *   - Narrative copy, not descriptive copy
 *   - A Wrapped-style shareable card as a first-class destination
 */
@Composable
fun FlavorApp() {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var openTxn by remember { mutableStateOf<MockTxn?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { AviateBottomNav(tab) { tab = it } },
        contentWindowInsets = WindowInsets.statusBars,
    ) { insets ->
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.padding(insets),
            label = "tab",
        ) { current ->
            when (current) {
                Tab.HOME -> HomeScreen(onOpenTxn = { openTxn = it })
                Tab.INSIGHTS -> InsightsScreen()
                Tab.RECAP -> RecapWrappedScreen()
                Tab.PASSPORT -> PassportScreen()
            }
        }
    }

    openTxn?.let {
        TxnDetailSheet(it) { openTxn = null }
    }
}

// ---------------------------------------------------------------------------
// Bottom nav
// ---------------------------------------------------------------------------

private enum class Tab(val label: String) {
    HOME("Home"), INSIGHTS("Insights"), RECAP("Recap"), PASSPORT("Passport")
}

@Composable
private fun AviateBottomNav(current: Tab, onSelect: (Tab) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(WindowInsets.navigationBars.asPaddingValues())) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Tab.values().forEach { t ->
                    val active = t == current
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onSelect(t) }
                            .background(
                                if (active) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent,
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            t.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// HOME
// ---------------------------------------------------------------------------

@Composable
private fun HomeScreen(onOpenTxn: (MockTxn) -> Unit) {
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        TopBar(title = "May, 2026", actionLabel = "Share")
        Spacer(Modifier.height(20.dp))

        SpendHeroCard()
        Spacer(Modifier.height(16.dp))

        PrimaryActionCard(
            label = "Live Spending",
            sub = "₹1,284 in the last 24h · 6 transactions",
        )
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigTile(
                modifier = Modifier.weight(1f),
                label = "Budgets",
                title = "₹38,420 / ₹50,000",
                sub = "76% of monthly cap",
                tone = TileTone.MOSS,
            )
            BigTile(
                modifier = Modifier.weight(1f),
                label = "Subscriptions",
                title = "₹3,840",
                sub = "Netflix, Spotify, +3",
                tone = TileTone.ROSE,
            )
        }
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallTile(modifier = Modifier.weight(1f), label = "Cards", value = "4")
            SmallTile(modifier = Modifier.weight(1f), label = "Inbox", value = "12")
            SmallTile(modifier = Modifier.weight(1f), label = "Dues", value = "Mar 28")
        }
        Spacer(Modifier.height(28.dp))

        SectionLabel("Recent Transactions")
        Spacer(Modifier.height(8.dp))
        MockTxn.sample.take(5).forEach { txn ->
            TxnRowCard(txn) { onOpenTxn(txn) }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun TopBar(title: String, actionLabel: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        Text(
            actionLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable { }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun SpendHeroCard() {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            // Decorative arc — analogous to the route arc in Aviate trip-detail.
            Canvas(Modifier.fillMaxWidth().height(200.dp).padding(top = 16.dp)) {
                val brush = Brush.linearGradient(
                    listOf(
                        Color(0xFF2E5BFF).copy(alpha = 0.18f),
                        Color(0xFF2E5BFF).copy(alpha = 0f),
                    ),
                )
                drawArc(
                    brush = brush,
                    startAngle = 200f, sweepAngle = 140f, useCenter = false,
                    topLeft = Offset(-80f, 40f),
                    size = Size(size.width + 160f, size.height * 1.8f),
                    style = Stroke(width = 6f),
                )
                drawCircle(
                    color = Color(0xFF2E5BFF),
                    radius = 14f,
                    center = Offset(size.width * 0.18f, size.height * 0.78f),
                )
                drawCircle(
                    color = Color(0xFF2E5BFF),
                    radius = 14f,
                    center = Offset(size.width * 0.82f, size.height * 0.42f),
                )
            }

            Column(Modifier.padding(20.dp)) {
                Text(
                    "TOTAL SPENT",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "₹38,420",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "in May · ₹11,580 under last month",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(160.dp))
            }
        }
    }
}

@Composable
private fun PrimaryActionCard(label: String, sub: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().clickable { },
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary),
                contentAlignment = Alignment.Center,
            ) {
                Text("●", color = MaterialTheme.colorScheme.onSecondary, fontSize = 18.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    sub,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                )
            }
            Text(
                "›",
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontSize = 28.sp,
            )
        }
    }
}

private enum class TileTone { CLOUD, MOSS, ROSE, PEACH }

@Composable
private fun BigTile(
    modifier: Modifier = Modifier,
    label: String,
    title: String,
    sub: String,
    tone: TileTone,
) {
    val (bg, fg) = when (tone) {
        TileTone.CLOUD -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        TileTone.MOSS -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        TileTone.ROSE -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        TileTone.PEACH -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        modifier = modifier.heightIn(min = 130.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.7f))
            Spacer(Modifier.height(12.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = fg,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(sub, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun SmallTile(modifier: Modifier = Modifier, label: String, value: String) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun TxnRowCard(txn: MockTxn, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (txn.isIncome)
                MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    txn.merchant.take(1),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    txn.merchant,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${txn.timeLabel} · ${txn.mode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                (if (txn.isIncome) "+" else "-") + "₹${txn.amount}",
                style = MaterialTheme.typography.titleLarge,
                color = if (txn.isIncome) MaterialTheme.colorScheme.onTertiaryContainer
                        else MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// INSIGHTS — three pastel category callouts + a "this month" headline
// ---------------------------------------------------------------------------

@Composable
private fun InsightsScreen() {
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
    ) {
        TopBar(title = "Insights", actionLabel = "Filter")
        Spacer(Modifier.height(20.dp))

        Text(
            "Where your money went",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "May 2026 · 142 transactions across 8 categories",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        InsightHero("FOOD & DINING", "₹12,840", "33% of spend · ↑ 22% from April", TileTone.PEACH)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigTile(Modifier.weight(1f), "TRANSPORT", "₹4,210", "Uber, Rapido, fuel", TileTone.CLOUD)
            BigTile(Modifier.weight(1f), "SHOPPING", "₹6,920", "Amazon, Decathlon", TileTone.ROSE)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigTile(Modifier.weight(1f), "GROCERIES", "₹5,180", "Zepto, Blinkit", TileTone.MOSS)
            BigTile(Modifier.weight(1f), "ENTERTAINMENT", "₹2,400", "BookMyShow, Spotify", TileTone.CLOUD)
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "The headline",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "You ate out 38 times — that's 6 more than April and the highest of the year.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "It's not necessarily bad — but worth noticing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun InsightHero(label: String, value: String, sub: String, tone: TileTone) {
    val (bg, fg) = when (tone) {
        TileTone.CLOUD -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        TileTone.MOSS -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        TileTone.ROSE -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        TileTone.PEACH -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = fg.copy(alpha = 0.7f))
            Spacer(Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.displayMedium,
                color = fg,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(6.dp))
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.85f))
        }
    }
}

// ---------------------------------------------------------------------------
// RECAP — Wrapped-style narrative card with paginator + save/share affordances
// ---------------------------------------------------------------------------

@Composable
private fun RecapWrappedScreen() {
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        TopBar(title = "Q2 2026 in Rupee", actionLabel = "History")
        Spacer(Modifier.height(24.dp))

        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "RUPEE · WRAPPED",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            "✓ YOU  /  Q2",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                Spacer(Modifier.height(60.dp))

                Text(
                    "YOU SPENT IN",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "23",
                    fontSize = 156.sp,
                    lineHeight = 156.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "categories",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(28.dp))

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    ),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChipMerchant("ZMTO")
                            ChipMerchant("UBER")
                            ChipMerchant("AMZN")
                            ChipMerchant("ZPTO")
                        }
                        Spacer(Modifier.height(8.dp))
                        Row {
                            ChipMerchant("CRED")
                            Spacer(Modifier.width(10.dp))
                            Box(
                                Modifier
                                    .size(width = 70.dp, height = 50.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "+18",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    "→ rupee.app",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CtaButton("Save image", primary = true, modifier = Modifier.weight(1f))
            CtaButton("Save to gallery", primary = false, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "🔗 Share public link",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ChipMerchant(code: String) {
    Box(
        Modifier
            .size(width = 60.dp, height = 50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.BottomStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                code,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.background,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CtaButton(label: String, primary: Boolean, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(50),
        colors = CardDefaults.cardColors(
            containerColor = if (primary) MaterialTheme.colorScheme.primary
                              else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = modifier.height(52.dp).clickable { },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = if (primary) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// PASSPORT — identity stats
// ---------------------------------------------------------------------------

@Composable
private fun PassportScreen() {
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        TopBar(title = "Passport", actionLabel = "Edit")
        Spacer(Modifier.height(20.dp))

        Text(
            "BITTY",
            fontSize = 40.sp,
            lineHeight = 40.sp,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    "✨ RUPEE PRO",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "Member since October 2025",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EraChip("All time", true)
            EraChip("2026", false)
            EraChip("2025", false)
            EraChip("2024", false)
        }
        Spacer(Modifier.height(20.dp))

        // Wrapped teaser banner
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth().clickable { },
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center,
                ) { Text("✨", fontSize = 18.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "WRAPPED IS READY",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Your Q2 2026 in Rupee",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Conscious spender · 142 txns",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 28.sp)
            }
        }
        Spacer(Modifier.height(16.dp))

        // Hero stats card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "MONEY THIS ERA",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "₹4.2L",
                        fontSize = 64.sp,
                        lineHeight = 64.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "spent",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    HeroSubStat("478", "TXNS")
                    HeroSubStat("₹1.8L", "INCOME")
                    HeroSubStat("11", "MERCHANTS")
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // Most visited + Hours lost equivalent
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.weight(1f).heightIn(min = 170.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "MOST VISITED",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "ZMTO",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "Zomato",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            "38× orders",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.weight(1f).heightIn(min = 170.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "OVERSPEND",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "₹3.2k",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Black,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "above your dining budget",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // Footprint card (Aviate "carbon footprint" analogue)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Text("◐", color = MaterialTheme.colorScheme.onTertiaryContainer, fontSize = 18.sp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "SAVINGS RATIO",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "28%",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "  of income",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "Top 18% of users your age",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "STAMPS",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "5 earned · 4 in progress",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun HeroSubStat(value: String, label: String) {
    Column {
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Black,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun EraChip(label: String, active: Boolean) {
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable { }
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (active) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ---------------------------------------------------------------------------
// TXN DETAIL — boarding-pass style ticket
// ---------------------------------------------------------------------------

@Composable
private fun TxnDetailSheet(txn: MockTxn, onClose: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onClose),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "‹  back",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.clickable(onClick = onClose),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "Transaction",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text("⤴", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(Modifier.height(20.dp))

            // Upper boarding-pass card
            Card(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 0.dp, bottomStart = 0.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "RUPEE · CONFIRMED",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            )
                            Text(
                                "${txn.mode.uppercase()} · ${txn.dayLabel}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF1F8742))
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Text(
                                "POSTED",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        txn.merchant.take(3).uppercase(),
                        fontSize = 84.sp,
                        lineHeight = 84.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "₹${txn.amount}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        txn.merchant,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(28.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        DetailKv("MODE", txn.mode.uppercase())
                        DetailKv("ACCOUNT", "ICICI · 4421")
                        DetailKv("CATEGORY", "Food")
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        DetailKv("TIME", txn.timeLabel)
                        DetailKv("STATUS", "Auto-confirmed")
                        DetailKv("CONFIDENCE", "98%")
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            // Perforation
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                    drawLine(
                        color = Color.Black.copy(alpha = 0.18f),
                        start = Offset(20f, size.height / 2),
                        end = Offset(size.width - 20f, size.height / 2),
                        strokeWidth = 3f,
                        pathEffect = dash,
                    )
                }
                // notches
                Box(
                    Modifier
                        .size(20.dp)
                        .align(Alignment.CenterStart)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background),
                )
                Box(
                    Modifier
                        .size(20.dp)
                        .align(Alignment.CenterEnd)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background),
                )
            }
            Card(
                shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomEnd = 24.dp, bottomStart = 24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✎ ", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            "PARSED FROM ${txn.source.uppercase()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "“${txn.notificationText}”",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChipMerchantMini("ALWAYS TRUST · ZOMATO")
                        ChipMerchantMini("REPORT ISSUE")
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth().clickable { },
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) { Text("⌚", fontSize = 18.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Timeline",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Posted · auto-confirmed · live",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "Open  ›",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun DetailKv(k: String, v: String) {
    Column {
        Text(
            k,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            v,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ChipMerchantMini(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)),
                RoundedCornerShape(50),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

// ---------------------------------------------------------------------------
// Mock data
// ---------------------------------------------------------------------------

private data class MockTxn(
    val merchant: String,
    val amount: String,
    val mode: String,
    val timeLabel: String,
    val dayLabel: String,
    val isIncome: Boolean,
    val source: String,
    val notificationText: String,
) {
    companion object {
        val sample = listOf(
            MockTxn("Zomato", "428", "UPI", "Today · 14:22", "Today · 20 May", false,
                "GPay",
                "You paid ₹428 to Zomato. Available balance ₹14,228.10"),
            MockTxn("Uber", "186", "UPI", "Today · 09:14", "Today · 20 May", false,
                "GPay", "₹186 sent to Uber India."),
            MockTxn("Salary · TechCo", "1,80,000", "BANK", "Yesterday", "19 May", true,
                "ICICI", "INR 1,80,000.00 credited to A/c xxx4421."),
            MockTxn("Decathlon", "4,210", "CARD", "18 May · 19:42", "18 May", false,
                "CRED", "Spent ₹4,210 on your Axis card ending 9023."),
            MockTxn("Spotify", "199", "CARD", "18 May · 06:00", "18 May", false,
                "Spotify", "Renewed Premium · ₹199."),
            MockTxn("Zepto", "1,124", "UPI", "17 May · 21:08", "17 May", false,
                "PhonePe", "₹1,124 paid to Zepto."),
        )
    }
}
