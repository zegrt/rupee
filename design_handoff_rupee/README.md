# Handoff: Rupee — Android Personal Finance App

## Overview

**Rupee** is a single-user, India-first personal-finance Android app. It reads bank-app notifications (and eventually SMS) to build a clean ledger with minimum manual entry. The intended stack is **Kotlin + Jetpack Compose + Material 3 + Room**, fully offline (no accounts, no cloud sync).

This handoff contains the complete design system, 40 screens / states across 12 feature areas, plus the data shapes and helpers that map cleanly to Kotlin / Compose / Room.

## About the Design Files

The files in this bundle are **design references created in HTML/React** — prototypes showing intended look and behavior, not production code to copy directly. The task is to **recreate these designs in Kotlin + Jetpack Compose + Material 3**, mapping each HTML component to a Composable, the CSS tokens to a `RupeeTheme {}` wrapper, and the sample data shapes to Room entities + ViewModels.

## Fidelity

**High-fidelity.** Pixel-perfect mockups with final colors (oklch), typography (Space Grotesk + JetBrains Mono), spacing, and interactions. Recreate the UI as faithfully as possible in Compose.

Where the design uses a CSS-only trick (e.g. `mix-blend-mode: multiply` on overlapping shapes, `color-mix()` for tinted backgrounds, `backdrop-filter: blur`), prefer the closest Compose-native equivalent — `Modifier.alpha()` with deliberate color picks, `Color.copy(alpha=...)`, `androidx.compose.ui.graphics.BlurEffect`. Don't ship a WebView.

---

## Design System

### Color Tokens

The design uses **OKLCH** throughout for perceptually-uniform color. Compose 1.6+ supports `Color.hsl()` and now `Color()` with arbitrary color spaces, but for maximum compatibility convert to sRGB hex when defining your `ColorScheme`.

**Dark theme (primary):**

| Token | OKLCH | Approx. hex | Compose role |
|---|---|---|---|
| `bg` | oklch(0.135 0.005 80) | `#1F1D1A` | `background` |
| `bg-deep` | oklch(0.10 0.004 80) | `#171614` | scrim, sheets dim |
| `surface` | oklch(0.175 0.006 80) | `#28251F` | `surface` |
| `surface-2` | oklch(0.225 0.007 80) | `#332F28` | `surfaceVariant`, cards |
| `surface-3` | oklch(0.275 0.008 80) | `#403B33` | elevated cards, toggle rail-off |
| `divider` | oklch(0.28 0.006 80) | `#423D36` | `outlineVariant` |
| `hairline` | oklch(0.35 0.006 80) | `#544E45` | borders |
| `text` | oklch(0.96 0.004 80) | `#F4EFE8` | `onSurface` |
| `muted` | oklch(0.70 0.008 80) | `#A29A8E` | `onSurfaceVariant` |
| `dim` | oklch(0.50 0.008 80) | `#72685C` | tertiary text |
| `faint` | oklch(0.38 0.008 80) | `#5A5249` | quaternary, eyebrows |
| `accent` | oklch(0.74 0.175 55) | `#D78A47` | `primary` (saffron) |
| `accent-bright` | oklch(0.82 0.19 60) | `#F1A05A` | `primaryContainer` highlights |
| `accent-deep` | oklch(0.55 0.16 50) | `#8E5B2C` | hover/pressed |
| `accent-on` | oklch(0.15 0.02 55) | `#1F1812` | `onPrimary` |
| `good` | oklch(0.80 0.13 165) | `#7AC8A6` | confirmed / success |
| `good-dim` | oklch(0.55 0.09 165) | `#3F8369` | dim variant |
| `warn` | oklch(0.82 0.17 80) | `#D9B05A` | duplicate-suspected |
| `bad` | oklch(0.68 0.20 25) | `#D26142` | over-budget / danger |

**Light theme** (samples in §11): inverted surfaces with the same accent. See `styles.css` `.rupee-light` block for the full set.

### Type

- **Display / body:** Space Grotesk — weights 300 / 400 / 500 / 600 / 700. Load via Google Fonts or bundle as `.ttf` resources. Use 600 for headlines, 500 for body emphasis, 400 for body.
- **Mono / metadata / tabular figures:** JetBrains Mono — weights 400 / 500 / 600. Used for eyebrows, timestamps, status labels, and all numeric data alongside `font-feature-settings: "tnum"`.

**Type scale:**

| Style | Family | Size | Weight | Line height | Letter spacing |
|---|---|---|---|---|---|
| Display (hero amount) | Space Grotesk | 64px | 600 | 0.95 | -0.04 |
| Headline | Space Grotesk | 26px | 600 | 1.1 | -0.01 |
| Title | Space Grotesk | 18px | 600 | 1.25 | 0 |
| Body | Space Grotesk | 14px | 400 | 1.45 | 0 |
| Body small | Space Grotesk | 13px | 400 | 1.4 | 0 |
| Eyebrow | JetBrains Mono | 10–11px | 500–600 | 1 | 0.12–0.16em |
| Tabular | JetBrains Mono | 10–13px | 400–600 | 1.4 | 0 |

All numeric text uses `fontFeatureSettings = "tnum"` (Compose `TextStyle(fontFeatureSettings = "tnum")`) for column-aligned figures.

### Spacing

Spacing scale: 2 · 4 · 6 · 8 · 10 · 12 · 14 · 16 · 18 · 20 · 24 · 28 · 32 · 36 · 40 · 48 px. Inner card padding typically 12–18px; section padding 20px; module gaps 20–36px depending on hierarchy.

### Radii

- 4px — chips, mono pills, registration marks
- 6–8px — small cards
- 10px — standard card / button
- 14px — large cards (subscription items, etc.)
- 20px — bottom sheets
- 50% / 9999px — circular badges, toggle pills, navigation pill

### Iconography

- Stroke-based 1.2–1.6px geometric icons drawn as `Path`/`Canvas` in Compose, OR Material Symbols (`Outlined`, weight 400, fill 0). All bottom-nav icons are 18×18 stroke SVGs in the prototypes — replace with `Icon(painterResource(...))` or Material Symbols.
- **Category monograms:** single-letter (`F`, `T`, `S`, `B`, `E`, `H`, `V`, `G`) in a tinted square. Implement as a small Composable taking `(category: Category, size: Dp)`.
- **Bank monograms:** colored brand-letter squares (`I`, `H`, `A`, `S`, `K`...). See `BANKS` in `rupee-tokens.jsx`.
- **Mode glyphs:** UPI (→ arrow), CARD (rectangle stripe), CASH (round in rect), BANK (pillars), ATM (square). Inline SVG in prototypes; redraw as Compose `Path`.

### Indian-specific formatting

Indian comma grouping: `1,00,000` not `100,000`. Helper in `rupee-tokens.jsx` (`formatINR`) — port to Kotlin:

```kotlin
fun formatINR(n: Long, withSymbol: Boolean = true): String {
    val abs = abs(n)
    val s = abs.toString()
    val formatted = if (s.length <= 3) s else {
        val last3 = s.takeLast(3)
        val rest = s.dropLast(3)
        rest.reversed().chunked(2).joinToString(",").reversed() + "," + last3
    }
    val sign = if (n < 0) "−" else ""
    return sign + (if (withSymbol) "₹" else "") + formatted
}
```

The `₹` symbol is rendered slightly smaller and dimmer than the digits in hero amounts — see `HeroAmount` and `Amount` helpers in `rupee-tokens.jsx`.

---

## Screen Inventory (40 artboards · 12 sections)

### 00 · Foundations (2 reference cards)
Not user-facing. Reference docs for the design system.

### 01 · Onboarding (4 screens)
Linear: **Welcome → Permissions → Profile → Setup → Home**.
- **Welcome** — 38px headline "Your money, watched.", 4 fact rows
- **Permissions** — Notification access ask; live demonstration card showing a fake HDFC notif → parsed transaction
- **Profile** — Name + monthly budget + month-start picker; suggested-budget callout based on detected income
- **Setup** — Pick banks (grid of pills), card (last-4), and cash wallet

### 02 · Home (3 states)
Five-tab bottom navigation, Home is tab 1.
- **Populated** — Greeting · monthly budget hero (₹28,420 remaining + 30-tick progress + daily rate) · weekly bar chart · category budget bars · upcoming-dues horizontal strip (with shape-differentiation: cards card-shaped, EMIs squared, subs pill) · quick actions (Review N + Add tx) · recent activity list with confidence stripes
- **Over budget** — Same skeleton, hero in `bad` red, "OVER BUDGET BY" eyebrow
- **First-run · empty** — Greeting · empty hero (₹65,000 budget · 0 of) · "watching" panel with saffron pulse dot · "while you wait" action list

### 03 · Inbox (3 states)
Review queue for medium-confidence parses.
- **Queue** — Filter chips (All · New · Duplicates · Low conf) + stacked rows, each with: reason chip (icon + label, tap-to-explain), category badge, merchant, raw notif source, amount, Confirm/Edit/✕ action row
- **Chip tapped** — Same row with the explainer popover open, and (for duplicates) a "merge into existing" mini-card with the matching prior tx
- **All clear** — Big "00" hero in mint-green + "what Rupee did last 24h" stat module (Auto-confirmed N, trusted merchants used, duplicates merged, errors caught)

### 04 · Transactions (3 states)
- **Chronological list** — Day-grouped, each row has: confidence stripe (3px left edge, color-coded), category badge, merchant, mode/bank, optional note, amount, ± sign for income. Confidence legend at top.
- **Searching** — Saffron-outlined search bar with active query + blinking caret, filter chip row (active chips have × to remove, dashed add-more chips), result count + total, filtered list, aggregate-insight callout
- **Pre-data · empty** — "Watching" panel + 3 pulsing skeleton rows + "speed things up" action module (add manually / import CSV / receipt photo)

### 05 · Calendar (4 states)
Month grid with per-day spend heat.
- **Month grid** — May 2026 grid (31 days), heatmap fill based on spend, today outlined in saffron, "Heaviest days" footer module
- **Long-press peek** — One cell scaled up + saffron-glowing, floating peek card hovering above with day total + 2 tx, dashed-circle fingerprint hint, "release to open" footer
- **Day sheet** — Bottom sheet for a tapped day; per-tx mini-bar visualization + list
- **Quiet month** — Mostly-empty grid, celebration block at bottom ("19 no-spend days · quietest in a year")

### 06 · Monthly Recap (8 cards)
Two variants of the end-of-month review.
- **Wrapped story** (7 cards) — Spotify-Wrapped-style swipeable: Cover → Total → Category → Merchant → Day → Mode → Wrap. Progress ticks at top, brand badge + ✕ in upper-right, slide index in footer. Mostly dark backgrounds with one saturated full-bleed (Wrap card uses saffron BG).
- **Minimal** (1 card) — Single-screen alternative: hero total + savings delta, 3-cell mini-stat row, top-3 categories with bars, top-3 merchants list, biggest-day callout, "see the full story →" link to the Wrapped version

### 07 · Settings (6 screens)
- **Index** — Profile hero card (USER · 001 · Aman + ledger size/since/storage stats) + 4 module groups (Watching, Money, Reports, Hazard zone) with numbered rows
- **Trust rules** — 12-merchant list, each with category badge, name, mode/bank/cap metadata, use-count, pill toggle. Stats bar + "+ ADD" CTA.
- **Cards & EMIs** — Full card-face component (saffron-glow when urgent), used-bar, MIN DUE / FULL DUE cells, pay buttons; EMI rows with segmented per-month progress bar
- **Accounts** — Banks · credit lines · cash. Each row: bank monogram, name, last-4, monthly tx count, template recognition %, pill toggle. Parser-health callout at bottom.
- **Budgets editor** — Total monthly cap hero + spillover toggle; per-category slider with TWO knobs (one for current spend indicator, one for adjustable cap)
- **Recurring** — Monthly-burn hero (combined monthly + yearly÷12), next-4-weeks bar strip, 6 subscription rows with detected vs manual chip

### 08 · Sheets (2 modals)
- **Manual entry** — Bottom sheet · Expense/Income segmented · recent-merchant quick-fill chips · big amount input · merchant field · mode chips with icons · category chips with mini-monograms · optional note · Save CTA
- **Transaction detail** — Bottom sheet for an existing tx · hero amount + merchant + meta · confidence-confirmation card · editable fields (merchant/category/mode/note) · "Always trust" pill toggle · raw notif source

### 09 · Notifications (2 system mocks)
- **Lock screen** — Wallpaper bg, lock-screen clock, heads-up Rupee notification with inline Confirm / Not a tx buttons for review case, plus a silent auto-confirmed below
- **Notification shade** — Pulled-down system shade with quick-settings tiles + 3 stacked Rupee notifications (one review, two silent)

### 10 · Widgets (1 mock)
Pixel home-screen with three Rupee widgets: 4×2 hero (remaining + ticks), 2×2 inbox-count (saffron), 2×2 today's spend, 4×1 dues strip. Plus mock app grid + dock with Rupee icon highlighted.

### 11 · Light samples (2 frames)
Same system inverted: Home and Recap category card in light mode to prove the system works.

---

## Component Vocabulary (map to Composables)

| HTML/React | Compose equivalent | Notes |
|---|---|---|
| `<RupeeFrame>` | `RupeeShell { content() }` | Status bar + content + bottom nav + gesture pill. Top-level scaffold. |
| `<StatusBar />` | `StatusBarMock` | Mocked for design canvas; in the real app this is the system status bar |
| `<NavBar active="home">` | `RupeeBottomBar(active)` | 5-tab `NavigationBar` with custom labels (mono caps) and saffron dot indicator above icon |
| `<Module num title hint action>` | `Section(num, title, hint, action)` | TE-style numbered section: hairline rule on top, "03 / CATEGORIES · 6/6 · EDIT" header |
| `<HeroAmount value>` | `HeroAmount(value: Long)` | 64px display weight, `₹` smaller + dimmer prefix, tabular numerals |
| `<Amount value size sign>` | `Amount(value, size, sign)` | Inline tabular amount with mini `₹` prefix |
| `<CatBadge id size>` | `CategoryBadge(category, size)` | Tinted square with letter monogram |
| `<BankBadge id size>` | `BankBadge(bank, size)` | Brand-colored square with letter |
| `<ModeIcon mode>` | `ModeIcon(mode)` | UPI / CARD / CASH / BANK / ATM glyph |
| `<Toggle on>` | iOS-style `Switch` (44×26 rail, 22×22 dark knob, saffron when on, grayscale when off) | Used in Settings & Trust rules |
| `<TxRow tx>` | `TransactionRow(tx)` | Confidence stripe (3px left edge) + category badge + merchant + mode meta + amount |
| `<InboxRow item>` | `InboxCard(item)` | Rounded card with reason chip, content, action row |
| `<DueCard due>` | `DueCard(due)` | Shape-differentiated per type: card-aspect for credit cards, square for EMIs, pill for subs |
| Reason chip | `ReasonChip(reason, mode)` | Three style variants — icon+tint / tint only / outline — toggled via `chipMode` |
| Confidence stripe / dot / tint / chip | `ConfidenceSignal(level, mode)` | Four style variants — see `styles.css` `.r-conf-mode-*` classes |
| `<Field label value>` | `Field(label, value)` | Onboarding/sheet form field with mono eyebrow + value |
| Skeleton (`.r-skel`) | `Modifier.skeleton()` | Pulsing surface-variant — implement with `animateColor` between 0.5 and 0.85 alpha over 1.6s |

### Custom Composables to build

1. **`RupeeShell`** — Outermost scaffold with status bar, content slot, navigation bar, gesture pill
2. **`Section`** — Numbered TE-style section header
3. **`HeroAmount` / `Amount`** — Mixed-size currency display
4. **`CategoryBadge` / `BankBadge` / `ModeIcon`** — Identity glyphs
5. **`ConfidenceStripe`** — 3px left edge per row, tweakable via system setting
6. **`ReasonChip`** — Inbox-row context chip with popover
7. **`TransactionRow`** / **`InboxCard`** — Repeated list items
8. **`Toggle`** — Custom pill-style switch (not standard `Switch`)
9. **`BarChart`** / **`Heatmap`** — Used in weekly strip and calendar
10. **`StoryCard`** — Recap shell with progress ticks + brand row + content slot
11. **`Widget*`** — Home-screen widget components (use Glance for the real Android widgets)

---

## Interactions & Behavior

### Bottom navigation
5 tabs: HOME · INBOX · TX · CAL · SET. Active tab has saffron color + dot above icon. Inbox tab shows a small saffron pill badge with pending count when N > 0.

### Confidence visual modes
Four ways to signal auto-confirmed vs verified vs needs-review, tweakable globally:
- **Stripe** (default): 3px left edge on row (dim / good / saffron)
- **Dot**: small inline dot next to amount
- **Tint**: row background tinted faintly
- **Chip**: small uppercase "AUTO" / "VERIFIED" / "REVIEW" pill

The user picks one in Settings → Display → Confidence signal. Honor it everywhere transactions are listed (Home recents, Tx list, Calendar day sheet).

### Inbox reason chip popover
Tap the reason chip → expand a popover beneath it with the human-readable explanation. For `DUPLICATE` reason, include a "merge into existing" mini-card with the matching prior transaction. Animation: 200ms ease-out fade + scale 0.95 → 1.

### Calendar long-press
500ms long-press on a day cell → cell scales 1.06× with saffron glow, floating peek card appears above with day total + top 2 tx + "release to open · drag to another day". Releasing taps into the day; dragging to a different cell moves the peek.

### Recap navigation
Swipe horizontally between the 7 Wrapped cards. Tap left edge = previous, right edge = next. ✕ top-right closes the recap. Progress ticks at the top show position.

### "Watching" status
On Home (empty + populated) and Notifications, a small saffron dot with a soft pulsing halo indicates the notification listener is active. Implement as `infiniteRepeatable` animation on a `Modifier.drawBehind` shadow, 1.6s cycle, 0.5–0.85 alpha.

### Tweakable system (Tweaks panel in the prototype)
Four user-controllable knobs:
- **Theme** — dark / light (system-driven by default, override via Settings)
- **Accent color** — saffron (default) / indigo / teal / magenta
- **Inbox chip style** — icon+tint / tint only / outline
- **Confidence signal style** — stripe / dot / tint / chip

Persist as `DataStore` preferences. Apply via `CompositionLocalProvider(LocalRupeeAccent provides accent)`.

---

## State Management

### ViewModels (recommended)
- `HomeViewModel` — emits month budget, weekly bars, category budgets, upcoming dues, recent tx, inbox count
- `InboxViewModel` — emits pending items, handles confirm/dismiss/edit/merge actions
- `TransactionsViewModel` — paginated list, search query, filter chips
- `CalendarViewModel` — month grid + per-day totals + day-sheet data
- `RecapViewModel` — current month stats + month-over-month deltas
- `SettingsViewModel` — settings sections + sub-screen state (trust rules, cards, etc.)

### Notification parser (the heart of the app)
A `NotificationListenerService` that:
1. Receives raw `StatusBarNotification`s from registered packages (HDFC, ICICI, Axis, SBI, Kotak, etc.)
2. Runs through a template-matching pipeline (regex + ML eventually) to extract amount / merchant / mode / bank
3. Emits one of: **auto-confirmed** (high confidence + matches trust rule) → Room directly; **needs-review** (medium confidence) → Inbox queue; **rejected** (low confidence / not a tx) → dropped
4. Posts a system notification: silent for auto, heads-up for review (with inline Confirm / Not a tx `Notification.Action`s using `RemoteInput` for direct response)

### Sample data shapes (Room-ready)

See `rupee-tokens.jsx` for live data:

```kotlin
data class Transaction(
    val id: String,
    val merchant: String?,
    val category: Category,
    val mode: Mode,                  // UPI, CARD, CASH, BANK_TRANSFER, ATM, WALLET
    val amount: Long,                // in paise; display divides by 100
    val confidence: Confidence,      // AUTO, USER_CONFIRMED, SUGGESTED
    val timestamp: Instant,
    val bank: Bank?,
    val note: String? = null,
    val isIncome: Boolean = false,
    val rawSource: String? = null,   // original notification text
)

data class InboxItem(
    val id: String,
    val merchant: String?,
    val category: Category?,
    val mode: Mode?,
    val amount: Long,
    val reason: InboxReason,         // AMOUNT_ONLY, NO_MERCHANT, DUPLICATE, MERCHANT_UNCLEAR, LOW_CONFIDENCE, NEW_MERCHANT, FOREIGN
    val rawSource: String,
    val bank: Bank,
    val receivedAt: Instant,
    val duplicateOf: String? = null,
)

data class Category(val id: String, val label: String, val mono: String, val color: Long)

data class Bank(val id: String, val mono: String, val color: Long)

data class TrustRule(
    val id: String,
    val merchant: String,
    val category: Category,
    val mode: Mode?,
    val bank: Bank?,
    val amountLimit: Long? = null,
    val useCount: Int = 0,
    val enabled: Boolean = true,
)

data class Due(
    val id: String,
    val type: DueType,               // CARD, EMI, SUBSCRIPTION
    val title: String,
    val subtitle: String,
    val amount: Long,
    val dueDate: LocalDate,
    val urgent: Boolean = false,
    val bank: Bank? = null,
)

data class Budget(
    val categoryId: String,
    val cap: Long,
    val spent: Long,
    val default: Long,               // suggested cap
)

data class Subscription(
    val id: String,
    val name: String,
    val frequency: Frequency,        // MONTHLY, YEARLY
    val amount: Long,
    val nextChargeDate: LocalDate,
    val detected: Boolean,           // false = manually added
    val mode: Mode,
)
```

The `CATEGORIES`, `BANKS`, `SAMPLE_TX`, `SAMPLE_INBOX`, `SAMPLE_DUES`, `SAMPLE_BUDGETS`, `TRUST_RULES`, `SAMPLE_SUBS`, `SAMPLE_ACCOUNTS` constants in the JSX files contain realistic seed data — use them to build a `DemoDataModule` for dev builds.

---

## Navigation

12 top-level destinations, but only 5 are bottom-nav tabs:

```
Tab 1  HOME    →  / (Home)
Tab 2  INBOX   →  /inbox
Tab 3  TX      →  /transactions  →  /transactions/search
Tab 4  CAL     →  /calendar  →  bottom-sheet: /calendar/day/{date}
Tab 5  SET     →  /settings  →  /settings/trust
                              →  /settings/cards
                              →  /settings/accounts
                              →  /settings/budgets
                              →  /settings/recurring

Modal sheets:
  /add-tx          (manual entry, opened from FAB or Home quick action)
  /tx/{id}         (transaction detail, opened from any tx row)

Onboarding (first-run, no bottom nav):
  /onboard/welcome  →  /onboard/permissions  →  /onboard/profile  →  /onboard/setup  →  /

Recap (full-screen modal):
  /recap            (Wrapped 7 cards — pager)
  /recap/minimal    (single-screen alt, tap-through from /recap)
```

Use `NavHost` with `dialog<>` destinations for sheets and `navigation { … }` for the onboarding nested graph.

---

## Files in This Bundle

| File | Purpose |
|---|---|
| `Rupee.html` | Main entry — loads all the JSX |
| `styles.css` | **The design tokens** — every color, font, helper class. Read first. |
| `app.jsx` | Canvas composition — shows what screens go where |
| `rupee-frame.jsx` | Phone shell (status bar + nav + gesture pill) |
| `rupee-tokens.jsx` | **Shared helpers + sample data** — `formatINR`, `Amount`, `CatBadge`, `BankBadge`, `ModeIcon`, all sample arrays. Read second. |
| `rupee-onboarding.jsx` | 4 onboarding screens |
| `rupee-home.jsx` | Home + states + recent activity list |
| `rupee-inbox.jsx` | Inbox queue + chip-tapped popover |
| `rupee-transactions.jsx` | Transaction list + confidence variants |
| `rupee-calendar.jsx` | Month grid + day sheet |
| `rupee-settings.jsx` | Settings index |
| `rupee-sheets.jsx` | Manual entry, tx detail, **Toggle component** |
| `rupee-recap.jsx` | 7-card Wrapped story |
| `rupee-extras.jsx` | Notifications, minimal recap, trust rules, widgets, empty states |
| `rupee-more.jsx` | Cards/EMIs, accounts, budgets editor, recurring, tx search, calendar peek |
| `design-canvas.jsx` | Canvas wrapper (not part of the app) |
| `tweaks-panel.jsx` | Tweaks UI (not part of the app — for prototype only) |

**Recommended reading order:** `styles.css` (tokens) → `rupee-tokens.jsx` (helpers + data shapes) → `rupee-home.jsx` (most complex screen) → the rest as needed.

## Generating Screenshots

The live HTML (`Rupee.html`) is the canonical visual reference — open it in any browser to inspect every screen interactively, including pan/zoom and focus mode.

If you want a flat PNG of any artboard:
1. Open `Rupee.html` in a browser.
2. Hover the artboard's title bar and click the **⋯** kebab menu → **Download PNG**.
3. The exporter inlines fonts and computed styles, producing a 3× resolution PNG of just that screen.

For batch export, open every artboard's menu in sequence — or run the page's `dcExport` helper in DevTools to script it.

---

## Implementation Hints

1. **Build the theme first.** Convert the oklch tokens to a `RupeeColorScheme` data class and provide it via `CompositionLocalProvider`. Map to Material 3 `ColorScheme` slots where it makes sense; create extension properties for the Rupee-specific tokens (`good`, `accent-bright`, `faint`, etc.).
2. **Build the primitives next.** `HeroAmount`, `Amount`, `CategoryBadge`, `BankBadge`, `Section`, `Toggle`, `ConfidenceStripe`, `ReasonChip`. These are tiny composables reused everywhere.
3. **Build a single screen end-to-end** (suggestion: Inbox queue). It exercises most primitives: reason chip with popover, category badge, action buttons, list scaffolding.
4. **Wire the notification listener** with a dummy parser that just dumps every notification to a debug log. Real parsing comes later.
5. **Seed Room with the sample data** from `rupee-tokens.jsx` for dev builds. Lets you build Home / Tx list / Calendar without needing the parser working.
6. **The Wrapped recap (§06) is the showpiece** — design it as a single `HorizontalPager` with one composable per card. Most cards are statically-sized graphic compositions; the `Day` and `Merchant` cards have data-driven elements.
7. **Widgets** require Jetpack Glance. The HTML mockup is just a reference — actually building widgets is a separate path. Save for v0.5+.

## Assets

No image assets — all graphics are inline SVG or drawn with CSS. In Compose, redraw the simple shapes (logo squares, decorative circles in onboarding, etc.) with `Canvas` or `Path` Composables. No bitmaps needed.

The Rupee logo is currently a saffron square with a mono "₹" — replace with a real wordmark if the brand evolves.

## Open Questions for the Implementer

1. **Notification parsing strategy** — regex templates per bank vs. a single ML model? The brief implies template-first with ML as a stretch.
2. **SMS access** — alpha-flagged. Permission ask + parser logic still TBD.
3. **The Aviate vs Wrapped recap call** — both versions exist; product decision pending. Default route to `/recap` (Wrapped); ship `/recap/minimal` behind a flag.
4. **Widget framework** — Glance (recommended) or RemoteViews? Glance unlocks Compose-style widget code.
5. **Receipt OCR** (alpha) — mentioned in the empty-state Transactions screen as a placeholder. Off-scope for v1.

---

*Generated from the Rupee design canvas — 40 artboards across 12 feature areas.*
