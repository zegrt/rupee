package com.zegrt.rupee.ui.flavor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * vwfndr-style prototype of the wallet — instrument, not application.
 *
 * Black ground, exposed grid, one electric-lime accent that means "active." Numbers
 * are technical readouts (mono, all caps). Copy is verbs and nouns. Every transaction
 * comes with a signed receipt — provenance is the actual product.
 */
@Composable
fun FlavorApp() {
    var route by remember { mutableStateOf<Route>(Route.Home) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { VwBottomBar(route) { route = it } },
        contentWindowInsets = WindowInsets.statusBars,
    ) { insets ->
        AnimatedContent(
            targetState = route,
            // Instant cut, no fade. vwfndr is a viewfinder — views snap.
            transitionSpec = {
                androidx.compose.animation.EnterTransition.None togetherWith
                    androidx.compose.animation.ExitTransition.None
            },
            modifier = Modifier.padding(insets),
            label = "route",
        ) { current ->
            when (current) {
                is Route.Home -> HomeViewfinder { route = Route.Detail(it) }
                is Route.Ledger -> LedgerScreen { route = Route.Detail(it) }
                is Route.Settings -> SettingsScreen()
                is Route.Detail -> TxnReceiptScreen(current.txn) { route = Route.Home }
            }
        }
    }
}

private sealed class Route {
    data object Home : Route()
    data object Ledger : Route()
    data object Settings : Route()
    data class Detail(val txn: VTxn) : Route()
}

// ---------------------------------------------------------------------------
// Brand chrome — corner brackets, KEEP TRACKING marker
// ---------------------------------------------------------------------------

@Composable
private fun CornerBrackets(content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        content()
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val arm = 28f
            val w = 3f
            val c = Color(0xFFF5F5F5)
            // top-left
            drawLine(c, Offset(0f, 0f), Offset(arm, 0f), w)
            drawLine(c, Offset(0f, 0f), Offset(0f, arm), w)
            // top-right
            drawLine(c, Offset(size.width - arm, 0f), Offset(size.width, 0f), w)
            drawLine(c, Offset(size.width, 0f), Offset(size.width, arm), w)
            // bottom-left
            drawLine(c, Offset(0f, size.height - arm), Offset(0f, size.height), w)
            drawLine(c, Offset(0f, size.height), Offset(arm, size.height), w)
            // bottom-right
            drawLine(c, Offset(size.width, size.height - arm), Offset(size.width, size.height), w)
            drawLine(c, Offset(size.width - arm, size.height), Offset(size.width, size.height), w)
        }
    }
}

// ---------------------------------------------------------------------------
// Bottom bar — the technical control strip
// ---------------------------------------------------------------------------

@Composable
private fun VwBottomBar(route: Route, onSelect: (Route) -> Unit) {
    Surface(color = Color.Transparent) {
        Column(Modifier.padding(WindowInsets.navigationBars.asPaddingValues())) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .height(96.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Cells double as nav + status readouts — vwfndr-grammar:
                // the label is the surface code, the value is the readout
                // that surface is reporting *right now* (pipeline activity,
                // ledger row count, system status).
                ControlCell(
                    label = "HOM",
                    value = "LIVE",
                    active = route is Route.Home,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(Route.Home) },
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outline)

                // The lime shutter (crosshair +). Primary action — "add txn."
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "+",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }

                VerticalDivider(color = MaterialTheme.colorScheme.outline)
                ControlCell(
                    label = "LDG",
                    value = "412",
                    active = route is Route.Ledger,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(Route.Ledger) },
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outline)
                ControlCell(
                    label = "CFG",
                    value = "OK",
                    active = route is Route.Settings,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(Route.Settings) },
                )
            }
        }
    }
}

@Composable
private fun ControlCell(
    label: String,
    value: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .fillMaxSize()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = accent.copy(alpha = 0.85f))
        Spacer(Modifier.height(8.dp))
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Black,
        )
    }
}

// ---------------------------------------------------------------------------
// HOME — exposed grid of cells; readouts not stories
// ---------------------------------------------------------------------------

@Composable
private fun HomeViewfinder(onOpenTxn: (VTxn) -> Unit) {
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll),
    ) {
        TopMarker()

        // Hero cell — current balance, mono giant.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "BAL · LIVE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                // Range chip in upper-right — the wallet's analogue of the
                // aspect-ratio badge in vwfndr's viewfinder.
                RangeChip(label = "7D", active = true)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "₹ 1,42,438",
                fontSize = 56.sp,
                lineHeight = 60.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                ".20 INR · UNSIGNED",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            // EV-style spend-velocity scale. Mirrors the EV bar that sits
            // over vwfndr's viewfinder — needle drifts left/right of zero
            // to indicate "under" / "over" baseline.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "EV",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                EvScale(
                    modifier = Modifier
                        .width(180.dp)
                        .height(14.dp),
                    needleFrac = -0.30f,
                    // Subtle live jitter — needle never sits perfectly still,
                    // reads as "sensor reporting, not static."
                    wiggleAmount = 0.06f,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "− 12 %",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // LIVE FEED ticker — auto-marquee through the last few signals.
        // Single-line strip; the pipeline is broadcasting, the user is
        // operating equipment that's listening.
        LiveFeedTicker()
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // 2×2 stat grid — SPND / RECV / SAVE / DUES — exposed cell borders
        Row(Modifier.fillMaxWidth().height(110.dp)) {
            GridCell(modifier = Modifier.weight(1f), label = "SPND · MAY", value = "38,420", unit = "INR")
            VerticalDivider(color = MaterialTheme.colorScheme.outline)
            GridCell(modifier = Modifier.weight(1f), label = "RECV · MAY", value = "1,84,000", unit = "INR")
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(Modifier.fillMaxWidth().height(110.dp)) {
            GridCell(modifier = Modifier.weight(1f), label = "SAVE · RATIO", value = "28", unit = "%", lime = true)
            VerticalDivider(color = MaterialTheme.colorScheme.outline)
            GridCell(modifier = Modifier.weight(1f), label = "DUES · NEXT", value = "MAR 28", unit = "—")
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // EXIF-strip txn list preview
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "RECENT · 06",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "VIEW ALL  ↗",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        VTxn.sample.take(6).forEach { t ->
            TxnExifRow(t) { onOpenTxn(t) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun TopMarker() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "RPEE™",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "· LEDGER 0.14",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .clip(RoundedCornerShape(0.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                // Pulse on a 1.5s loop — the pipeline-is-live indicator.
                .livePulse(),
        ) {
            Text(
                "● LIVE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun GridCell(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    unit: String,
    lime: Boolean = false,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (lime) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.displaySmall,
                color = if (lime) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                unit,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun TxnExifRow(txn: VTxn, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Lime pip next to the merchant — signals "signed."
                // Muted grey for unsigned rows.
                StatusPip(
                    color = if (txn.signed) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    txn.merchantCode,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        txn.mode,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${txn.time} · ${txn.account} · ${txn.fingerprint}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (txn.income) "+" else "-") + txn.amount,
                style = MaterialTheme.typography.titleLarge,
                color = if (txn.income) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (txn.signed) "✓ SIGNED" else "UNSIGNED",
                style = MaterialTheme.typography.labelSmall,
                color = if (txn.signed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// LEDGER — long-form list of EXIF rows + grouping headers
// ---------------------------------------------------------------------------

@Composable
private fun LedgerScreen(onOpenTxn: (VTxn) -> Unit) {
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        // Rotated edge label down the left side — vwfndr's signature
        // "rotated metadata strip" applied to the wallet's ledger context.
        EdgeMeta(
            key = "LDG",
            value = "30D · 412 TX",
            modifier = Modifier
                .align(Alignment.CenterStart)
                .rotate(-90f)
                .padding(horizontal = 4.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll),
    ) {
        TopMarker()
        // section title
        Column(Modifier.padding(20.dp)) {
            Text(
                "LEDGER",
                fontSize = 40.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "478 entries · 11 merchants · 6 categories",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // Filter strip
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterBox("ALL", active = true)
            FilterBox("SPND", active = false)
            FilterBox("RECV", active = false)
            FilterBox("FAILED", active = false)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        LedgerHeader("TODAY · 20 MAY · 4 ENTRIES")
        VTxn.sample.take(4).forEach { t ->
            TxnExifRow(t) { onOpenTxn(t) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
        LedgerHeader("19 MAY · 3 ENTRIES")
        VTxn.sample.drop(4).forEach { t ->
            TxnExifRow(t) { onOpenTxn(t) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
        Spacer(Modifier.height(60.dp))
    }
    } // close outer Box hosting the rotated EdgeMeta overlay
}

@Composable
private fun FilterBox(label: String, active: Boolean) {
    Box(
        Modifier
            .border(1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable { }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LedgerHeader(label: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// TXN RECEIPT — the signed-provenance card
// ---------------------------------------------------------------------------

@Composable
private fun TxnReceiptScreen(txn: VTxn, onBack: () -> Unit) {
    val scroll = rememberScrollState()
    CornerBrackets {
        // Rotated edge label down the right side — receipt provenance
        // strip. Visible chrome that reinforces "this is a signed artifact."
        EdgeMeta(
            key = "SIG",
            value = "0.98 · ${txn.source}",
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .rotate(90f)
                .padding(horizontal = 4.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "↩  BACK",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.clickable(onClick = onBack),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "RPEE™  /  RECEIPT",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))

            // ID badge row — "PHOTO 12" equivalent → "TXN 4421"
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "TXN",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "${txn.serial}",
                    fontSize = 56.sp,
                    lineHeight = 56.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.weight(1f))
                // CR provenance monogram — borrowed from vwfndr's content
                // credentials badge. Sits above the receipt to signal
                // "this row carries a signature."
                CrMonogram(
                    tint = if (txn.signed) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(20.dp))

            // The receipt card — exposed-grid metadata table, with four
            // registration crosses scattered around it for brand chrome.
            Box {
                RegistrationCrosses(
                    modifier = Modifier.matchParentSize(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(Modifier.padding(0.dp)) {
                    Row(Modifier.fillMaxWidth().height(64.dp)) {
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("AMOUNT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "₹ " + txn.amount,
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        VerticalDivider(color = MaterialTheme.colorScheme.outline)
                        Column(
                            Modifier
                                .wrapContentSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("DIR", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (txn.income) "RECV ↓" else "SPND ↑",
                                style = MaterialTheme.typography.headlineMedium,
                                color = if (txn.income) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    ReceiptRow("CONTENT CREDENTIALS", if (txn.signed) "SIGNED" else "UNSIGNED", lime = txn.signed)
                    ReceiptRow("MERCHANT", txn.merchantCode + " · " + txn.merchant)
                    ReceiptRow("MODE", txn.mode)
                    ReceiptRow("ACCOUNT", txn.account)
                    ReceiptRow("CAPTURED FROM", txn.source)
                    ReceiptRow("FINGERPRINT", txn.fingerprint)
                    ReceiptRow("TIMESTAMP", "20 MAY 2026 · " + txn.time)
                    ReceiptRow("CATEGORY", txn.category)
                    ReceiptRow("CONFIDENCE", "98 %", lime = true)
                }
            }
            } // close outer Box that hosts the RegistrationCrosses overlay
            Spacer(Modifier.height(12.dp))

            // Raw signal block — EXIF-style metadata strip with the original
            // notification text rendered like a developer dump.
            Box(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline)
                    .padding(14.dp),
            ) {
                Column {
                    Text(
                        "RAW SIGNAL  ↘",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        txn.notificationText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // Action row — UNDO / DESTROY / EXPORT
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionPill("EDIT", border = true, modifier = Modifier.weight(1f))
                ActionPill("EXPORT", border = true, modifier = Modifier.weight(1f))
                ActionPill("DESTROY", danger = true, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(28.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "KEEP TRACKING  ↑",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ReceiptRow(label: String, value: String, lime: Boolean = false) {
    Row(Modifier.fillMaxWidth().height(48.dp)) {
        Box(
            Modifier
                .width(160.dp)
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        VerticalDivider(color = MaterialTheme.colorScheme.outline)
        Box(
            Modifier
                .weight(1f)
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (lime) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                fontWeight = if (lime) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun ActionPill(
    label: String,
    modifier: Modifier = Modifier,
    border: Boolean = false,
    danger: Boolean = false,
    primary: Boolean = false,
) {
    val borderColor = when {
        danger -> MaterialTheme.colorScheme.error
        primary -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val fg = when {
        danger -> MaterialTheme.colorScheme.error
        primary -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onBackground
    }
    Box(
        modifier
            .height(48.dp)
            .border(1.dp, borderColor)
            .background(if (primary) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (primary) MaterialTheme.colorScheme.onPrimary else fg,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ---------------------------------------------------------------------------
// SETTINGS — vwfndr-style preferences ("FORMAT · GALLERY · UI COLOUR · DESTROY ALL")
// ---------------------------------------------------------------------------

@Composable
private fun SettingsScreen() {
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll),
    ) {
        TopMarker()
        Column(Modifier.padding(20.dp)) {
            Text(
                "CFG",
                fontSize = 56.sp,
                lineHeight = 56.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "SYSTEM PREFERENCES",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        SettingsBlock("COPYRIGHT") {
            Text(
                "RUPEE BY BITTY",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        SettingsBlock("EXPORT FORMAT") {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                FormatOption("LEDGER + RAW", true)
                FormatOption("LEDGER ONLY", false)
                FormatOption("CSV", false)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        SettingsBlock("LEDGER") {
            Text(
                "DESTROY ALL ENTRIES",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        SettingsBlock("SHORTCUTS") {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Text(
                    "ADD TXN",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "SCAN",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "JUMP",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        SettingsBlock("UI COLOUR") {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ColourDot(Color(0xFFF5F5F5))
                ColourDot(Color(0xFFCFFF5C), selected = true)
                ColourDot(Color(0xFFFFD32E))
                ColourDot(Color(0xFFFF3B2F))
                ColourDot(Color(0xFFF59FCB))
                ColourDot(Color(0xFF2E9CFF))
                ColourDot(Color(0xFF7140FF))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        SettingsBlock("INGESTION HEALTH") {
            // T3-inspired exposed metrics. Even the debug surface gets the vwfndr treatment.
            Row(verticalAlignment = Alignment.Bottom) {
                ReadoutColumn("RECV", "1284")
                Spacer(Modifier.width(28.dp))
                ReadoutColumn("PARSED", "1217", lime = true)
                Spacer(Modifier.width(28.dp))
                ReadoutColumn("FAILED", "12", danger = true)
                Spacer(Modifier.width(28.dp))
                ReadoutColumn("GATED", "55")
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "PIPELINE / 24H · GATE REJECT 4.3%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(40.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "KEEP TRACKING  ↑",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SettingsBlock(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .width(140.dp)
                .padding(20.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.weight(1f).padding(vertical = 20.dp, horizontal = 4.dp)) {
            content()
        }
    }
}

@Composable
private fun FormatOption(label: String, selected: Boolean) {
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun ColourDot(c: Color, selected: Boolean = false) {
    Box(
        Modifier
            .size(if (selected) 28.dp else 22.dp)
            .clip(CircleShape)
            .background(c)
            .clickable { },
    )
}

@Composable
private fun ReadoutColumn(label: String, value: String, lime: Boolean = false, danger: Boolean = false) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            color = when {
                danger -> MaterialTheme.colorScheme.error
                lime -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onBackground
            },
            fontWeight = FontWeight.Black,
        )
    }
}

// ---------------------------------------------------------------------------
// LIVE FEED ticker — auto-marquee
// ---------------------------------------------------------------------------

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun LiveFeedTicker() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "● LIVE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.livePulse(),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "ZMTO ₹612  ·  UBER ₹284  ·  HDFC·EMI ₹4,250  ·  BIGBASKET ₹1,624  ·  AMZN ₹899  ·  METRO ₹500  ·  BMS ₹640  ·  SPOTIFY ₹199",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .basicMarquee(),
        )
    }
}

// ---------------------------------------------------------------------------
// Mock data
// ---------------------------------------------------------------------------

private data class VTxn(
    val serial: String,
    val merchantCode: String,
    val merchant: String,
    val amount: String,
    val mode: String,
    val time: String,
    val account: String,
    val fingerprint: String,
    val source: String,
    val category: String,
    val income: Boolean,
    val signed: Boolean,
    val notificationText: String,
) {
    companion object {
        val sample = listOf(
            VTxn("0478", "ZMTO", "Zomato Ltd.", "428", "UPI", "14:22:08", "ICICI · 4421",
                "F7A3 21 9D", "GPAY", "FOOD", false, true,
                "You paid ₹428.00 to Zomato. Available balance ₹14,228.10"),
            VTxn("0477", "UBER", "Uber India", "186", "UPI", "09:14:32", "ICICI · 4421",
                "2B11 04 7E", "GPAY", "TRANSPORT", false, true,
                "₹186 sent to Uber India."),
            VTxn("0476", "ZPTO", "Zepto Marketplace", "1124", "UPI", "08:41:11", "ICICI · 4421",
                "8E91 22 13", "PHPE", "GROCERY", false, true,
                "₹1,124 paid to Zepto."),
            VTxn("0475", "SPOT", "Spotify Premium", "199", "CARD", "06:00:00", "AXIS · 9023",
                "C03A 09 88", "CRED", "SUBSCRIPTION", false, true,
                "Renewed Premium · ₹199 on Axis 9023."),
            VTxn("0474", "TCO", "Salary · TechCo", "1,80,000", "BANK", "11:02:00", "ICICI · 4421",
                "F002 17 EE", "ICICI", "INCOME", true, true,
                "INR 1,80,000.00 credited to A/c xxx4421."),
            VTxn("0473", "DCTH", "Decathlon Sports", "4,210", "CARD", "19:42:17", "AXIS · 9023",
                "9921 0E A1", "CRED", "SHOPPING", false, false,
                "Spent ₹4,210 on your Axis card ending 9023."),
        )
    }
}

// Keep TextAlign import alive in case other surfaces use it later.
@Suppress("unused")
private val _imports = TextAlign.Center
