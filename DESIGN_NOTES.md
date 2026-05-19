# Design research — aviate vs vwfndr

Two parallel design-language explorations for the wallet. Both ship as side-by-side
installable APKs from this branch via Gradle product flavors. Real data layer is
**not wired** in either flavor — these are mocked UI explorations only.

## How to build

```bash
./gradlew :android:app:assembleAviateDebug
./gradlew :android:app:assembleVwfndrDebug
```

Output:
- `android/app/build/outputs/apk/aviate/debug/rupee-0.14.2-aviate-debug.apk`  — app label **Rupee · Calm**, applicationId `com.zegrt.rupee.aviate`
- `android/app/build/outputs/apk/vwfndr/debug/rupee-0.14.2-vwfndr-debug.apk` — app label **RPE™**, applicationId `com.zegrt.rupee.vwfndr`

Both install side-by-side. Open them and compare. Launcher icon colors are also
flavor-overridden so they're distinguishable in the app drawer.

## How the flavors are wired

- `android/app/build.gradle.kts` declares two product flavors on the `design`
  dimension with distinct `applicationIdSuffix` + `versionNameSuffix`.
- Each flavor has its own source set under `android/app/src/<flavor>/`. Theme
  files (`Color.kt`, `Type.kt`, `Theme.kt`) live in the flavor source set so
  `RupeeTheme {}` resolves to a completely different palette / type / shape
  system per flavor at compile time.
- Each flavor also has its own `ui/flavor/FlavorApp.kt` that fully replaces the
  app UI — bottom nav, every screen, every component. `MainActivity` is now a
  20-line shell that calls `FlavorApp()`.
- Existing ViewModels / Room / ingestion pipeline / parser registry compile but
  are unused. They stay in the tree for the next phase where we wire real data
  back in to whichever direction wins.

---

## Path A — aviate

> "Your money, as a story you can share."

### What it does to the wallet
- **Calm, premium, sentimental.** Every screen has one hero number rendered as a
  monument (60–72sp display, Black weight). Below that, soft pastel surfaces
  arranged in bento grids.
- **Tints are semantic.** Cloud (pale blue) = info / neutral, Peach (warm) =
  negative or "over budget", Moss (mint) = positive / income, Rose (pink) =
  identity / premium / special. Nothing screams red.
- **Narrative copy everywhere.** "Touched ₹2.4L across 142 merchants." "Q2 2026
  in Rupee." "Apparel obsessed." "Member since Oct 2025." Never "Total: ₹47820".
- **Identity is a destination.** The fourth tab is a **Passport** — JANE DOE,
  PRO chip, era chips, hero stats card, stamps row. The wallet sells you a
  version of yourself.
- **Wrapped is a first-class screen.** Pulse tab shows a tall pastel card with
  the quarter's hero stat + a merchant grid with a `+138` pill (mirroring
  Aviate's `+12` city tile).
- **Sharing is everywhere.** Every top bar has a share circle.

### Screens shipped
1. **Home** — "May, 2026" top bar, hero spend card with dashed arc, primary
   action card, 2-up + 3-up bento grids, recent merchant cards with colored
   tints.
2. **Spend / Ledger** — pill rows grouped by day, soft hairline borders, no
   harsh red for expenses — pure typography hierarchy carries the meaning.
3. **Pulse** — Wrapped hero card with `YOU · Q2 2026` segmented pill, most
   visited + over-budget tile pair, savings rate card.
4. **Passport** — JANE DOE, PRO chip, era filter chips, big stats card, "Wrapped
   is ready" promo, stamps row.

### What needs to change behind the screen for this design to work fully

These are the data/logic changes I'd recommend before this design ships, in
priority order:

1. **Quarterly aggregation pipeline.** Wrapped needs Q-bounded rollups: total
   touched, top N merchants by visit count, "you spent X% on category Y", days
   active, merchants discovered, longest no-spend streak. Today the repo does
   monthly. Add a `QuarterRecap` derived view computed in `RecapViewModel` or a
   new `WrappedRollupDao`.
2. **Merchant identity enrichment.** The hero merchant tiles want a logo +
   category color. Right now we store `merchantName` as parsed text. Add a
   `MerchantIdentity` table keyed by normalized name → (logo asset, brand
   color, default category). Bootstrap from a static seed for the top 200 IN
   merchants (Swiggy, Zepto, Blinkit, Ola, Amazon, Flipkart, BLR Metro, ...).
3. **"Era" concept.** The Passport tab implies `era` (calendar year) and "all
   time" lifetime stats. Add a `firstSeenAt` field on `UserEntity` + a lifetime
   rollup query. Era filter chips need O(1) reads — pre-aggregate at month
   close.
4. **Stamp/badge system.** "First ₹1L", "No-buy day", "On streak" require event
   triggers. New `StampEntity` + a `StampEvaluator` that runs on transaction
   confirm. Same pattern as `BudgetAlertManager` already in repo.
5. **Narrative copy generator.** "Apparel obsessed", "Red-eye warrior"
   equivalents — auto-generated from rollups. Small Kotlin function:
   `WrappedNarratorEngine.title(rollup): String` with a deterministic ranking
   over ~30 archetypes (Foodie / Streamer / Saver / Big-Spender / Subscription
   Sage / etc.). Show as the subhead on the Wrapped card.
6. **Shareable artifact rendering.** "Save image" and "Share public link"
   buttons want a JPEG/PNG export of the Wrapped card. Use Compose's
   `GraphicsLayer.toImageBitmap()` or a Canvas-backed view; render at 2x to a
   file in `Pictures/Rupee`. Public link → optional later, requires backend.
7. **Soft-removal of debug/inbox.** Aviate would never show a "review unparsed
   transactions" inbox or an ingestion health funnel to the user. Those become
   *settings-buried diagnostics*, not a main tab. The current `ReviewTab` either
   gets folded into Home as a "Heads up, 3 transactions need a look" soft card,
   or moves to a `Settings > Diagnostics` screen.
8. **Onboarding tone rewrite.** Welcome → Profile → Permissions copy currently
   reads as technical. For Aviate, rewrite as identity-building: "What should we
   call you on your Passport?", "We'll watch your bank notifications so you
   don't have to."

### Trade-offs to acknowledge
- Wrapped/Passport need ~3 months of data to feel real. Need a graceful "early
  era" state ("Your Passport is still gathering stamps — check back in 30
  days").
- Pastel-only palette makes accessibility contrast harder. Audit at AA
  level — onPrimaryContainer pairs are currently borderline.
- Narrative copy can age badly if not tuned per category. Plan a quarterly
  copy review.

---

## Path B — vwfndr

> "Operate your money. Sign every move."

### What it does to the wallet
- **Black ground, exposed grid, one electric lime.** No tints, no gradients, no
  warmth. Lime marks **state** (active, current, recording), nothing else.
- **Type is mono.** Every number reads like an EXIF strip: `₹38,420.00`,
  `S 1/17 · ISO 8960 · EV 0`. Headlines are grotesk all-caps, tracked open.
- **The transaction is a *signed receipt*.** Every transaction shows
  CONTENT CREDENTIALS / SIGNED / device fingerprint / source app / hash. The
  C2PA-like framing maps onto bank notifications: "this came from HDFC
  notification ID X at TS Y, signed by parser version Z." Provenance is the
  product moat.
- **Aggressive copy.** `DESTROY ALL DATA`, `KEEP TRACKING ↑`, `SIGNED`. The
  copy is industrial — verbs and nouns, all caps, no friendliness.
- **The control strip.** Bottom of the LIVE screen has a 5-cell strip:
  `MODE / IN / SPEND / BAL / SIGN` (analogous to vwfndr's
  `PRESET / EV / SPEED / ISO`). Each cell is a readout the user can poke.
- **No rounded corners.** The grid hairlines are visible. RectangleShape across
  the entire Material 3 Shape system.

### Screens shipped
1. **LIVE (Home)** — corner brackets, "KEEP TRACKING ↑" marker, big mono
   balance, control strip at the bottom with mode/spend/in/bal cells, lime
   shutter "+" affordance.
2. **LEDGER (Transactions)** — list of receipt-style cards. Each row has
   rotated metadata strips (`TODAY 02:07 · BENGALURU + KARNATAKA · S 1/17 ·
   ISO 8960 · EV 0`), the merchant code, the amount in mono.
3. **Detail / Receipt** — opens from any ledger row. Full "content credentials"
   view: CONTENT CREDENTIALS (SIGNED), copyright (`PARSED BY RUPEE LISTENER`),
   device (`PIXEL 8 · A015`), format (`PARSER · ICICI v2`), photo (the
   transaction itself rendered as the "frame"), `DESTROY` action at the corner.
4. **SYS (Settings)** — capability matrix. All caps, switch toggles styled as
   `ON / OFF`. UI COLOUR row with the lime palette dots (white / lime / amber /
   red / pink / blue / violet). `DESTROY ALL DATA` row.

### What needs to change behind the screen for this design to work fully

1. **Signed transactions / content credentials.** This is the *strategic* one.
   Sign every confirmed transaction with a per-device key (Android Keystore
   `EC_P256`) and store the signature + parser fingerprint on the
   `CanonicalTransactionEntity`. Schema add: `signatureBlob: ByteArray?`,
   `signedAt: Instant?`, `signerDeviceId: String?`, `parserVersion: String?`,
   `sourceCaptureId: String` (FK to `RawCaptureEventEntity`). The receipt
   screen reads these and shows the SIGNED chip. *This is the only feature in
   either flavor that's also a real product moat — for a wallet that ingests
   from bank notifications, provable provenance is a defensible story.*
2. **Source-of-record disclosure.** Every transaction should be tappable down to
   the raw notification: title, body, source app package, capture timestamp,
   hash. We already store this in `RawCaptureEventEntity` — wire it to a
   "VIEW SOURCE" affordance in the receipt. Today it's only surfaced in the
   debug screen.
3. **Mono-readable currency formatter.** Mono fonts render `₹` poorly at small
   sizes. Either bundle a custom mono with hand-tuned currency glyphs (e.g. JB
   Mono, IBM Plex Mono) or pre-format `INR 38,420.00` with three-letter codes
   for top-line readouts. Don't mix — pick a convention.
4. **No-emoji, no-marketing-copy guardrail.** A lint rule on category names and
   merchant labels: must be ASCII, ≤16 chars, all-caps display-mapping. Mock
   category names like "Food & Dining" need to compress to `FOOD / DINE` or
   `F&D` for the viewfinder. Add a `displayCode` column on `CategoryEntity`.
5. **Strip the inbox.** The notion of "review these unparsed transactions"
   doesn't fit vwfndr. Either the parser confirms a transaction or the raw
   signal is shown as a `PARSE FAILED` receipt in the ledger with a lime
   `RETRY` action. No human-curation step shown to the user.
6. **Destructive copy & confirms.** `DESTROY ALL DATA` is real — it should
   actually destroy the local Room DB and Keystore key. Implement a confirmed
   irreversible wipe in `LocalFinanceRepository.destroyAll()`. Two-step confirm
   with a typed phrase (vwfndr would make you literally type `DESTROY`).
7. **Always-dark.** No light theme, no system-toggle. Strip
   `isSystemInDarkTheme()` checks from any flavor-specific code.
8. **Sound + haptic design.** The viewfinder vibe needs a *shutter* sound on
   transaction confirm and a firm haptic pulse. Both are 1-line Android APIs
   (`VibratorManager.vibrate(...)`, `SoundPool` for the shutter click) but
   they're load-bearing for the vibe. Same way an instrument *clicks*.

### Trade-offs to acknowledge
- Mono everywhere is fatiguing for long reading. Acceptable for a wallet
  (mostly numbers + short merchant names) but it would break for any
  long-form content (e.g. monthly recap narrative). Plan that recap stays in
  grotesk.
- Aggressive copy can read as cold to non-target users. The audience is
  self-custody-curious, privacy-coded, "I prefer the terminal" — a deliberately
  narrow positioning. Marketing should not try to broaden it.
- The viewfinder metaphor strains for finance UX in some places. There's no
  natural EV-compensation analogue for money. Don't force it — keep the
  metaphor where it works (signed capture, readouts, instrument density) and
  drop it where it doesn't (don't ship a fake "ISO" knob).

---

## What both paths share

- The data model (entities, DAOs, repository) doesn't need to change to *try*
  either design — both paths can read from the existing `CanonicalTransactionEntity`
  + `IngestionHealth` surfaces. The deeper recommendations above are about
  finishing each direction once one wins.
- Neither path keeps the current bottom-tab structure exactly. Aviate collapses
  CALENDAR → into HOME's monthly view. Vwfndr collapses CALENDAR + INBOX → into
  LEDGER (everything is one stream).
- Both paths reduce the prominence of the *ingestion mechanics*. The current
  Inbox tab and Debug ingestion-health card are appropriate for v0.14 because
  parsing is still maturing, but they're a v0 affordance — neither shipping
  design language would surface them as top-level destinations.

## Decision question

The right path depends on positioning, not aesthetics:

- **If Rupee is for "feeling good about your money over time"** → aviate.
  Emotional retention, shareable artifacts, identity. Compete with Cred /
  Jupiter / Fi on warmth and polish.
- **If Rupee is for "proving your financial activity is yours and yours alone"**
  → vwfndr. Trust through visible provenance. Compete with self-custody crypto
  wallets and privacy-coded finance tools on rigor.

The wallet's actual technical strength right now (parsing notifications into a
local Room DB, no cloud, no account) maps more naturally to **vwfndr's
provenance frame** than to Aviate's social/identity frame. That's not a
recommendation — both are buildable — but it's the path with less *invented*
product story.
