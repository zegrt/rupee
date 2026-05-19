# Design research — Aviate vs vwfndr

Two parallel UI/UX prototypes of the Rupee wallet, each a self-contained
clickable Compose app shipped as its own APK. Both install side by side
(distinct `applicationId`).

```
design-research/apks/
  rupee-0.14.2-aviate-debug.apk   →  com.zegrt.rupee.aviate
  rupee-0.14.2-vwfndr-debug.apk   →  com.zegrt.rupee.vwfndr
```

The wallet's real engine (ingestion pipeline, Room, ViewModels, parsers) is
**unchanged and unwired**. The flavor `MainActivity` ignores it and renders
a fully mocked end-to-end UI in the relevant design language. This is design
research, not a refactor — the goal is to feel each direction in the hand.

---

## What's actually built

### Aviate flavor (`com.zegrt.rupee.aviate`)
- **Home** — top bar with month + share, hero "Total spent" card with decorative
  route arc, primary "Live Spending" action card, 2-up budget/subscriptions
  tiles, 3-up cards-and-inbox tiles, photo-style recent transaction list
- **Insights** — narrative headline + pastel category tiles + an editorial-voice
  callout ("You ate out 38 times — that's 6 more than April")
- **Recap** — Wrapped-style portrait card with hero "23 categories" stat,
  merchant chips, save-image / save-to-gallery / share-public-link affordances
- **Passport** — identity-coded profile: name + Pro pill + member-since,
  era chips, Wrapped teaser banner, hero "MONEY THIS ERA" card, Most Visited
  + Overspend tile pair, savings ratio card, stamps footer
- **Transaction detail** — boarding-pass ticket with perforated divider,
  status pill, oversized merchant code (`ZMA`), parsed-from-notification
  quote, timeline open-row

### vwfndr flavor (`com.zegrt.rupee.vwfndr`)
- **Home (Viewfinder)** — `RPE™ · LEDGER 0.14 · ● LIVE` chrome strip,
  giant mono balance readout, 2×2 exposed-grid stat cells (SPND / RECV / SAVE
  / DUES) with hairline dividers, EXIF-strip transaction rows showing
  `ZMTO · UPI · 14:22:08 · ICICI · 4421 · F7A3 21 9D`, signed/unsigned
  badge per row
- **Ledger** — full-list version of the EXIF rows with section headers
  (`TODAY · 20 MAY · 4 ENTRIES`), bordered filter boxes
- **Receipt (txn detail)** — `TXN 0478` mono title, exposed-grid metadata
  table with `CONTENT CREDENTIALS · SIGNED` (lime), raw signal block,
  EDIT / EXPORT / DESTROY action row, `KEEP TRACKING ↑` footer
- **Config (settings)** — left-rail uppercase labels (`COPYRIGHT`,
  `CAPTURE FORMAT`, `LEDGER`, `VOLUME UP`, `UI COLOUR`, `INGESTION HEALTH`),
  `DESTROY ALL ENTRIES` in red, T3 ingestion funnel rendered as readouts
- **Bottom control strip** — `MODE · [+] · LEDGER · CFG` cells with the
  crosshair shutter as the lime "add transaction" action

---

## What I'd change behind the screen to ship each style for real

Both flavors render mock data today. For each design to become production,
the actual data layer and ingestion pipeline would need to surface
information differently. Concrete changes per direction:

### Aviate ships → needs a "narrative" layer

Aviate's voice depends on **derived stories, not raw rows**. Today the
wallet stores `CanonicalTransactionEntity` rows; everything else is a UI-side
aggregation. To support this design properly:

1. **Period-aggregate cache.** A precomputed `MonthSummary` table — total
   spend, top categories, top merchants, week-over-week delta, sentiment
   tags ("highest dining month", "savings streak: 3 months"). Today these
   would be recomputed every screen open; for an editorial UI that's too
   slow, especially because the Passport screen mixes lifetime + month
   aggregates.
2. **Wrapped generator.** Wrapped needs a periodic job that materializes a
   `WrappedCard` per quarter (image-renderable JSON: hero stat, top
   merchants, narrative line). Today there's no scheduling layer for that —
   `BudgetAlertManager`/`DuesAlertManager` are the closest things, but they
   fire on events. Wrapped is calendar-bound.
3. **Merchant identity enrichment.** Aviate shows "Zomato" with a logo-like
   tile, "ZMTO" code, and a category emoji. Right now we have
   `merchantName: String` and that's it. Need a `MerchantBook` mapping:
   raw name → canonical display name → tile color → category default →
   "feels good / feels bad" weight. The current `MerchantNameUtils` only
   normalizes whitespace.
4. **Narrative copywriter.** Strings like "You ate out 38 times — that's 6
   more than April" need a small templating layer keyed on detected patterns
   (delta thresholds, anomalies). Could live in `home/` next to the
   ViewModel. Trivial to build, but the design *depends* on it — without
   it Aviate's voice collapses into "Food: ₹12,840."
5. **Sharing surface.** Save-image / share-public-link needs (a) a server
   to host LDF-style snapshots or (b) a fully offline render-to-PNG path. The
   wallet today has neither. The simplest first cut is on-device PNG export
   of the Wrapped card composable.
6. **Sentiment scoring.** Most Visited / Overspend / Carbon Footprint
   analogue all encode *judgment* on the user's behavior. That requires a
   "what does healthy look like for this user" baseline — either explicit
   (the user sets income/savings goals during onboarding) or learned
   (rolling 90-day median). Onboarding today asks for accounts but not goals.

### vwfndr ships → needs honest provenance + raw-data exposure

vwfndr's promise is "what you see is signed." That's not just typography —
it's a different relationship with data. To make this honest:

1. **Real content-credentials for transactions.** Each
   `CanonicalTransactionEntity` should carry a `signature` field — a hash of
   `(rawCaptureEventId + parserKey + amountMinor + occurredAt + accountId)`
   signed with a per-install key. The receipt UI's "✓ SIGNED" badge would
   then reflect actual integrity, not flavor decoration. The schema has
   `dedupeFingerprint` (a hash) and `confidenceTier` — both close, neither
   is a signature.
2. **Surface the raw capture, always.** Today's `RawCaptureEventEntity` is
   stored but the user only sees it on the Debug screen. vwfndr's receipt
   shows the source notification verbatim inline — that needs the raw row
   joined into every transaction read, not a separate debug query.
3. **Expose the parser path.** Each transaction should record *which*
   parser ran (`GPayNotificationParser`, `IciciNotificationParser`,
   `GenericNotificationParser`) and surface it as `CAPTURED FROM GPAY`
   on the receipt. The codebase has the data (`parserKey` on
   `ParsedSignalEntity`) but it's not propagated to the canonical row.
4. **Failure visibility.** vwfndr's UI flatters the user as an operator —
   that means showing them when the pipeline failed, not hiding it. The
   T3 ingestion health funnel needs to be a first-class screen, not buried
   in Debug. The Settings screen in this prototype includes a `INGESTION
   HEALTH` block exactly for this reason.
5. **DESTROY actually destroys.** vwfndr's copy ("DESTROY ALL ENTRIES") is
   honest brutalism only if the action is genuinely unrecoverable. Today
   `LocalFinanceRepository` has soft-deletes via `syncStatus`. For this
   design, a real `purge()` that drops rows and the corresponding
   raw-capture rows is needed, plus a confirmation dialog with the same
   technical voice ("THIS WILL DESTROY 478 ENTRIES. CONFIRM ↑").
6. **No friendly fallbacks.** vwfndr should never say "We couldn't read
   that notification." It should say `PARSE FAIL · 0x21 · SIG MISMATCH`
   with an option to ship the raw text. That means the parsing layer needs
   to retain typed error codes (it currently throws `NotificationParseResult.Failed`
   with a string reason — close, but the strings are mostly user-friendly
   prose today, not error codes).
7. **No light mode.** This is a design assertion, but it has a code
   consequence — the wallet's theme machinery should refuse `isSystemInDarkTheme()`
   for this flavor. (Already done in the prototype's `Theme.kt`.)

---

## Mapping flavor screens to existing wallet surfaces

This shows where each prototype screen would *replace* something the
current Rupee app already has, so the refactor cost is legible.

| Wallet today | Aviate replaces it with | vwfndr replaces it with |
|---|---|---|
| `HomeSummaryTab` (`MainActivity.kt:1171`) | Aviate `HomeScreen` | vwfndr `HomeViewfinder` |
| `TransactionsTab` (`MainActivity.kt:838`) | Photo-row Recent + dedicated full screen | `LedgerScreen` with EXIF rows |
| Transaction edit modal | Boarding-pass `TxnDetailSheet` | Receipt with signed provenance |
| `ReviewTab` (inbox) | Folded into Home as "Live Spending" CTA | First-class `INBOX · GATED 12` cell |
| `CalendarScreen` heat map | Becomes a "Year in Review" map metaphor | Becomes a sparkline strip across the top |
| `SettingsScreen` | Profile-coded "Passport" identity screen | `CFG` instrument panel |
| `RecapScreen` | Wrapped-style shareable cards | Quarterly readout: just numbers + a SHARE pill |
| `DebugScreen` ingestion-health card | Becomes a developer-only flag (consumers never see it) | First-class screen — "INGESTION HEALTH" is *the* product story |

---

## Build, install, hand-back

```
./gradlew :android:app:assembleAviateDebug
./gradlew :android:app:assembleVwfndrDebug

adb install -r design-research/apks/rupee-0.14.2-aviate-debug.apk
adb install -r design-research/apks/rupee-0.14.2-vwfndr-debug.apk
```

Both apps appear in the launcher with distinct names (`Rupee · Calm` and
`RPE™`) and distinct launcher-icon palettes (pale-blue / navy and pure-black /
electric-lime). Tap each to navigate the full prototype.

---

## My honest read after building both

**Aviate** is the right direction if the wallet wants to be a **lifestyle
identity product** — "look at the life I've built with my money." It's
warm, shareable, slightly indulgent. The Wrapped surface is a real growth
lever (free distribution). Risk: the design *demands* a narrative layer,
and without it the screens look like generic fintech.

**vwfndr** is the right direction if the wallet's pitch is **"this is the
honest, signed ledger of your real money."** It's an instrument, not a
lifestyle. It reads as serious, anti-AI, anti-slop — which is increasingly
valuable. Risk: it's deliberately user-unfriendly, and the audience that
wants this is narrower than the audience that wants Aviate.

The deepest fit-for-purpose answer is probably: **vwfndr's brand
seriousness + Aviate's surface care**. A wallet that's honest about
provenance underneath but treats your money with editorial warmth on top.
But that's a third path, not a synthesis — you'd have to design it.
