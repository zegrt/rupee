# Rupee — Deferred Work & Sprint Backlog

**Purpose:** durable, in-repo backlog. Anything Claude promised to do "next sprint" or "later" lives here, not just in conversation context. This file is the single source of truth for what's deferred — if it's not here, it doesn't exist.

**Last updated:** 2026-05-23 (alpha rename + deep-audit follow-up).
Reframed the 1.0 target to a much more honest **alpha** milestone — see
the new "Path to alpha" section below for the mobile-stage progression
(pre-alpha → alpha → closed beta → open beta → RC → 1.0). Added six
Watching items surfaced by the 2026-05-23 audit pass, slotted
NOTIF-CHANNELS post-alpha, and folded BABYPROOF-INPUTS into Sprint 3.

---

## How to read this

- **In flight** — currently being shipped or actively designed.
- **Sprint N** — concrete sprint with a defined item set; *every* gap we
  know about has a sprint home. Order isn't a hard contract — sprints
  later in the list can move forward if a tester report demands it — but
  the placement reflects natural dependencies and a reasonable cadence.

The 2026-05-23 doc pass dissolved the older **Slotted** / **Watching**
sections. Items that lived there were either (a) shipped and removed,
(b) moved into the appropriate post-alpha sprint below.

Each item has: *what*, *why it matters*, *touchpoints*, *blocked by*.

---

## In flight

- *(Nothing actively in flight as of 2026-05-23; Sprints 0/1/2 all merged,
  v0.14.5 cut. Sprint 3 (alpha prep) is the next runnable sprint. The
  design revamp stays post-alpha — see [REVAMP](#revamp--full-design-overhaul-aviate-vs-vwfndr-post-alpha)
  in Slotted.)*

---

## Recently shipped — post-v0.14.0 *(2026-05-19 → 2026-05-21)*

- **v0.14.1 hotfix** (PR #48, merged 2026-05-19) — recovered the v0.14.0
  ingestion regression surfaced by the Nothing-A015 dump: 0/916
  notifications reached Inbox or Transactions because H4's foreign key
  collided with the existing write order in `normalizeLocked`. Fix
  reversed the order (candidate → inbox → backfill), split the dedupe
  DAO (stopped filtering IGNORED in the lookup), added `"sent from"`
  etc. to gate vocab, surfaced exception class+message into the dump's
  `outcome` block, loosened the masked-digit regex for single-X SMS
  forms. Investigation: [ingestion-pipeline-research-2026-05-19.md](ingestion-pipeline-research-2026-05-19.md).
- **T1 — In-memory Room test fixture** (PR #50, merged 2026-05-20).
  `androidx.room:room-testing` wired; in-memory `RupeeDatabase` builder;
  base fixture seeds user/account/card/budget. First three instrumented
  tests landed: H4 FK ordering regression, `DumpOutcomeDao` joined query,
  `MIGRATION_8_9` table-rebuild migration.
- **T2 — Dump-replay harness** (PR #52, merged 2026-05-21). Committed
  dumps under `dumps/*.jsonl` are now a regression corpus runnable from
  JVM unit tests; `DumpReplayHarness` parses each dump, replays bodies
  through the normalizer against an in-memory DB, asserts ingested-vs-
  rejected distribution stays within ±5pp of a checked-in baseline. Four
  missing backlog items were captured during the implementation.
- **T3 — Ingestion-health surface** (PR #51, shipped as v0.14.2 on
  2026-05-21). New `IngestionHealthCard` on the Debug screen shows
  rolling 7-day funnel — total received, % gate-accepted, % with
  `amountMinor != null`, count of `INGEST_FAILED` with most common
  `errorClass`. Loud red when failure count > 0.
- **Audit corrections** *(2026-05-22)* — surfaced four items that
  earlier turns of this file claimed as "pending" but are already in
  code. Listed here so the next reader doesn't re-propose them:
  - **EXT-COMBINED — Bundle-extras unification.** Already wired.
    `ingestion/NotificationExtractor.kt` reads every extra (title,
    titleBig, text, bigText, subText, infoText, textLines, messages,
    ticker) into a deduplicated `combinedBody`. The listener stores
    that as `RawCaptureEventEntity.body`; every parser reads it.
    Implements `notification-ingestion-deep-dive.md §5` exactly. The
    "+60% coverage" projection lives in production.
  - **TRUSTED-UI — Trusted merchants management.**
    `settings/TrustRulesScreen.kt` is a real composable (~87 lines):
    empty-state card, list of `TrustRuleRow` cards showing
    `merchantPattern` + optional `categoryLabel`, **Remove** action.
    Reached via the Settings card. No placeholder.
  - **INBOX-WHY — Reason chips on Inbox rows.** Each row in
    `ReviewTab` renders `row.reasonLabel` inside a tinted Surface
    (`MainActivity.kt:1837`). Reasons today distinguish SUGGESTED
    vs INBOX sources; refining the per-reason copy is still possible
    but the badge slot exists.
  - **PERM-REOPEN — Notification-access re-enable shortcut.**
    `MainActivity.kt:274` defines `openNotificationSettings = ...
    ACTION_NOTIFICATION_LISTENER_SETTINGS`; the Settings → Notifications
    card surfaces it whenever `PermissionStateChecker.hasNotificationAccess`
    returns false. Works post-onboarding too.

---

## Recently shipped — gravedigging audit, v0.14.0 *(2026-05-19)*

Full ledger lives in [docs/gravedigging-2026-05-18.md](gravedigging-2026-05-18.md);
abbreviated here so the backlog reflects current backlog and history.

- **Inbox husk bug** — DB-level `@Relation` join replaces the parallel-
  flow in-memory join; husks structurally impossible.
- **H1** — REFUND now routes to canonical INCOME (PhonePe / Paytm parsers
  + normalizer).
- **H2** — Home recent-transactions list switched from `LIMIT 20` to a
  30-day windowed query.
- **H3** — gate-rejected notifs no longer write candidate rows; 90-day
  prune of `raw_capture_events` (FK cascades sweep the chain).
- **H4** — foreign keys + CASCADE / SET NULL on parsed_signals →
  transaction_candidates → inbox_items. Migration `MIGRATION_8_9`
  rebuilt three tables.
- **M1** — `inbox_items.mergedFromExistingCanonicalId` column replaces
  magic-string CASE expression in `DumpOutcomeDao`. Migration
  `MIGRATION_9_10`.
- **M2** — gate vocab additions: `reversed`, `reversal`, `chargeback`,
  `SIP installment`, `NACH mandate`, `ECS debit`, `refund of`,
  `standing instruction`.
- **M3a** — dedicated `AtmNotificationParser`, registered FIRST so ATM
  withdrawals route as `CASH_WITHDRAWAL` (→ `CASH_ADJUSTMENT` canonical,
  excluded from spend totals).
- **M3b** — fuel-brand normalisation in `MerchantNameUtils.clean`
  (IOC / HPCL / BPCL / Shell / Nayara / Essar) — trust rules now fire
  across every outlet of a brand.
- **M4** — `app_state` K/V table persists debounce timestamps across
  cold starts. Migration `MIGRATION_10_11`.
- **M5** — UPI confidence tiering: amount + merchant + maskedDigits →
  HIGH (auto-create); less → MEDIUM (Inbox).
- **M6** — share-path exceptions now logged + surfaced in toast via
  exception class.
- **M7** — `findMatchingCard` refuses single-card fallback when parser
  captured a non-matching digit or provider hint.
- **L3** — `CrashReporter.readLog` returns `Result<String>` so the
  debug screen distinguishes "no crashes" from "read failed".
- Repository gains `persistScope: CoroutineScope` parameter; production
  uses `RupeeApplication.appScope` instead of `GlobalScope`.

S1.4 fully shipped in v0.13.7 — Home income line, Transactions list +₹X
tinted rows, manual entry Expense/Income toggle, GPay/PhonePe/Paytm
INCOME branching all landed. CRED/ICICI parsers still hardcode SPEND
for some paths — fine for card alerts, audit if a real card-credit
notification slips through.

---

## Path to alpha

We were earlier calling the next milestone "1.0" — that was wrong. A real
1.0 means *public-launch quality*: tens of thousands of installs survived,
crash-free rate ≥ 99.5%, support load survivable. We're not close. We're
finishing **alpha** — the milestone where the app is feature-complete
enough to put in front of a small closed group of real testers who can
report bugs we don't anticipate.

The mobile-app stage progression we're now tracking against:

| Stage | What it means | Where we are |
|---|---|---|
| **Pre-alpha** | Building the core. Bugs everywhere. Internal only. | v0.0.x → v0.14.5 (now) |
| **Alpha** | Feature-complete for a defined scope. Closed-group testing. Known bugs OK if logged. | **Sprint 3 → v0.15.0-alpha** |
| **Closed beta** | Hand-picked external testers (~50). Stability over features. | post-alpha sprints |
| **Open beta** | Public sign-up on Play Console. Polish + performance + locale. | longer arc |
| **Release candidate** | Bug fixes only. Frozen feature set. | end of beta |
| **1.0 / GA** | Public release. Marketing surface. Support readiness. | the actual finish line |

Everything in the Sprint 3 section below is scoped to land before the
alpha cut. Everything in **Slotted** is captured intent for post-alpha
work — most of it will happen during the closed-beta / open-beta arcs.
**Watching** is "we know it's a gap but nobody's scheduled it."

**Design revamp (Aviate vs vwfndr) is intentionally post-alpha.** The
exploration branch (`design/aviate-vs-vwfndr`) stays parked. Sprint 2's
polish work targeted the current warm Clay / Sage / Paper Material 3
theme — every Sprint 0/1/2 item shipped was flavor-agnostic. The big
visual overhaul is captured as `REVAMP` in Slotted and waits behind the
alpha cut + a positioning call.

**EMI auto-detection is intentionally post-alpha.** The manual EMI
entry path in `Cards & EMIs` already covers the alpha feature
checklist; auto-detection from notifications is a quality upgrade that
needs real-user EMI corpora to tune confidence thresholds against.
See Slotted → **EMI-AUTO**.

---

## Sprint 0 — Release blockers *(shipped 2026-05-22, v0.14.4)*

Pure correctness. Had to land before the alpha cut.

*All six items below merged into main. Listed for traceability — the
work is done.*

### SEED-SERVICE — proper seeding service (replace hardcoded IDs)
**What:** `LocalFinanceRepository.completeInitialSetup` hardcodes seed IDs (`account-bank-1`, `card-1`, `account-cash`) and a single 2026-03 budget period. Works for one tester; breaks for a clean install in any other month. Replace with UUID generation + current-month budget.
**Why:** release blocker (any install outside March 2026 silently has no budget seeded for the current month).
**Touchpoints:** `LocalFinanceRepository.completeInitialSetup`, possibly `OnboardingViewModel` if budget period needs surfacing.
**Blocked by:** nothing. ~half-day.

### CRED-ICICI-AUDIT — Audit hardcoded SPEND in CRED / ICICI parsers
**What:** Per [Recently shipped — gravedigging audit](#) notes, "CRED/ICICI parsers still hardcode SPEND for some paths — fine for card alerts, audit if a real card-credit notification slips through." Concrete audit: enumerate every `parse()` return in `CredNotificationParser` and `IciciNotificationParser`, check each against a real-world card-credit dump (refund, reversal, statement credit, EMI conversion reversal), patch any that miscategorise.
**Why:** silent miscategorisation of a credit-side notification as SPEND inflates the user's spend total and confuses Insights. Hard to catch by inspection because the offending notification is rare.
**Touchpoints:** `ingestion/CredNotificationParser.kt`, `ingestion/IciciNotificationParser.kt`, regression cases in `NotificationParserParseTest`.
**Blocked by:** nothing. ~½ day audit + however long the fixes take per parser hole.

### L4 — remove `fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4)`
**What:** Drop the destructive-migration fallback in `RupeeApplication.kt:44`. Anyone on v1-v4 has long passed the upgrade window; this is just data-loss-risk for any real release.
**Why:** release blocker. Captured in [gravedigging-2026-05-18.md §L4](gravedigging-2026-05-18.md) as "deferred indefinitely until release prep" — promoting here so it doesn't get forgotten.
**Touchpoints:** one line in `RupeeApplication.kt`. ~5 minutes.
**Blocked by:** "we're committing to never supporting v1-v4 upgrades again" — true today.

### KOTAK-VERB-DRIFT — drop the per-parser verb gate in KotakNotificationParser
**What:** `KotakNotificationParser.canParse` requires a Kotak package match (load-bearing) **and** a hit against a local `TRANSACTIONAL_VERBS` list (`sent via`, `debited`, `credited`, `paid`, `received`, `deducted`, `auto-debit`, `withdrawn`, `spent`). That local list is redundant with the central `TransactionalGate.POSITIVE_VERBS` — the gate already filtered any body without a verb before the parser runs. The list has now drifted: v0.14.1 added `"sent from"` to the central gate (for Kotak811's title-only "₹3.00 sent from XX4129" shape) but the parser-level list was not updated. Result: a real Kotak ₹3 debit in the 2026-05-22 Nothing-A015 dump cleared the central gate, then failed the parser's local verb check, and fell through to `GenericNotificationParser` — providerHint became `com.kotak811...` instead of `kotak`, mode became null instead of UPI.
**Why:** every time we add a verb to the central gate we will have to remember to add it here too. The 2026-05-22 dump proves we already failed to. Cheap to delete.
**Touchpoints:** `ingestion/KotakNotificationParser.kt` (drop `TRANSACTIONAL_VERBS` and the `body` check; keep package match). Update unit tests that pin the local-gate behaviour to pin the central-gate behaviour instead.
**Caveat — read before generalising:** the audit found three *other* parsers that look superficially similar but are NOT redundant (`AtmNotificationParser`, `EmiNotificationParser`, `GenericUpiNotificationParser`). Those use verb matching as routing/disambiguation ("real ATM withdrawal" vs "Try our new ATM card", "real EMI charge" vs "EMI options available", "real UPI transaction" vs "Your UPI ID is X") — distinct from transactionality. Do not delete theirs.
**Blocked by:** nothing. ~15 minutes.

### GATE-AUTOPAY-VOCAB — add autopay verbs to TransactionalGate
**What:** `TransactionalGate.POSITIVE_VERBS` covers `auto-debit`, `auto debit`, `autodebit`, `NACH mandate`, `ECS debit`, `standing instruction`, but **not** `autopay` or noun-form `payment to ` / `payment for `. Native GPay autopay notifications use the noun form: "Payment to SPOTIFY INDIA PVT LTD was successful. Payment for Autopay of ₹X to MERCHANT was successful." Today these are rejected as `NO_TRANSACTIONAL_VERB`.
**Why:** any user on GPay UPI Autopay (NPCI's recurring-debit primitive used for Spotify / Netflix / Hotstar / Vi recharges / SIP / utilities) loses every recurring debit. Common enough that a single GPay user can have 4-8 autopay notifications a month.
**Touchpoints:** `ingestion/TransactionalGate.kt` (positive verbs list), `ingestion/TransactionalGateTest.kt` (add accept cases for autopay bodies).
**Cross-check:** the same body shape comes from CRED's UPI-autopay wrapper too ("UPI autopay of ₹139 for Spotify India Pvt Ltd has been debited successfully") — but CRED's wrapper says **debited**, so it clears the existing gate. The new vocab specifically rescues the native-GPay phrasing.
**Blocked by:** nothing. ~10 minutes.

### CRED-PROMO-BODY-GATE — stop CRED/credit-account promos from parsing as txns
**What:** The CRED Cash credit-line promo ("₹2,80,000 available for you — withdraw any amount from your CRED cash account before May 31st and start your first EMI in July") cleared the central gate because the gate's positive-verb list includes `withdraw ` (added for ATM bodies). `CredNotificationParser.canParse` is package-OR-brand-only with no verb gate, so the parser fired, the amount regex captured `₹2` (the regex doesn't handle Indian comma-grouping `2,80,000`), and the result was written to the Inbox as a ₹2.00 SPEND with no merchant. Bug has two heads:
  1. **Gate**: `withdraw ` is too broad — needs context, or needs a paired negative-keyword like `withdraw any amount from`, `withdraw from your.*account`, or just the `cred cash account` / `credit line` marker.
  2. **Amount extraction**: `extractAmountMinor` only matches Western thousands grouping (`1,000`, `1,000,000`). Indian financial copy uses lakhs/crores grouping (`2,80,000`, `1,00,00,000`). Today the regex truncates silently to `₹2`, which is worse than failing — the result looks parseable.
**Why:** the Inbox got a garbage ₹2.00 row from a promo, and the user lost trust in what's in there. Indian-numbering coverage is a categorical fix that will rescue dozens of other bodies too.
**Touchpoints:** `ingestion/TransactionalGate.kt` (negative keywords for cash-advance/credit-line promo phrasing), `ingestion/NotificationParsingUtils.extractAmountMinor` (add `\d{1,2}(?:,\d{2})*,\d{3}` Indian-grouping alternative to the existing regex; test against `₹2,80,000`, `₹1,00,000`, `₹1,00,00,000`).
**Blocked by:** nothing. ~½ day with tests.

---

## Sprint 1 — Utility wins *(shipped 2026-05-22, v0.14.3)*

Four flavor-agnostic utility wins users will feel.

*All four items below merged into main. The Aviate-vs-vwfndr design
call that earlier revisions paired with this sprint moved to post-alpha
— see [REVAMP](#revamp--full-design-overhaul-aviate-vs-vwfndr-post-alpha)
in Slotted.*

### EXPORT-UI — Export to CSV / JSON
**What:** PRD §14 lists data-export as an MVP capability but no UI affordance exists today. Surface a Settings entry that lets the user save a date-windowed export of `canonical_transactions` (+ joined merchant/category) as CSV or JSON to local storage, then offer the share-sheet. JSON export should also include `raw_capture_events` for the same window so power users can debug ingestion themselves.
**Why:** MVP gap. Also unblocks "I want to see my data outside the app" trust requests and gives us a pre-baked diagnostic payload when users report bugs.
**Touchpoints:** new `diagnostics/Exporter.kt` (CSV + JSON formatters — the JSON path can lean on `DumpOutcomeExporter` plumbing that already exists for diagnostics), Settings entry, `FileProvider` share intent (already declared in manifest).
**Blocked by:** nothing. ~1–2 days.

### TRUST-FROM-TXN — "Always trust this merchant" from the Transactions edit sheet
**What:** The Inbox review row already exposes an "Always trust {merchant}" Switch (`MainActivity.kt:1887`) that writes a `MerchantTrustRuleEntity`. The gap is on the **Transactions tab edit sheet** — when the user opens a confirmed transaction to edit merchant / category, there's no trust toggle. Mirror the Inbox row's affordance there so trust rules can be added after the fact, not only at confirm time.
**Why:** the Inbox shortcut covers "trust as I confirm." It doesn't cover "I just realised every Swiggy charge is fine, let me trust them" once those rows have already left the Inbox. Friction here pushes users to dig through Settings → Trust Rules → Add → pick merchant.
**Touchpoints:** the transaction edit sheet inside `TransactionsTab` (`MainActivity.kt`); same `MerchantTrustRuleDao.upsertRule` call the Inbox path uses.
**Blocked by:** nothing. ~½ day.

### EXCL-FROM-SPEND — Account-level "exclude from totals" toggle
**What:** `AccountEntity.excludeFromExpenseTotals` and `excludeFromIncomeTotals` columns exist (added v0.13.x); `LocalFinanceRepository.setAccountExcludeFromExpenseTotals` exists; **no UI surface** wires it. The credit-card equivalent shipped via `CardsEmisScreen.kt:170` (`onToggleExclude`). The account path is repo-only — accounts can only be edited via onboarding today.
**Why:** users with savings-account drains (loan EMIs auto-debited from a different account they don't track for budgeting) want to exclude that account from "May spend" totals without losing the rows. Today they have to manually mark each transaction.
**Touchpoints:** need a new account-management entry in Settings (currently only Cards & EMIs is exposed). Calls `repository.setAccountExcludeFromExpenseTotals(accountId, exclude)`.
**Blocked by:** nothing. ~½ day for the toggle row; ~1 day if we add a proper "Accounts" Settings screen alongside it.

### S1.3 (pilot) — Evidence-stacked confidence scoring, one parser
**What:** Pilot the additive-tally pattern on one parser to validate the design before migrating the other eight. Recommend `GenericUpiNotificationParser` (loosest current scoring → biggest tuning win). Each evidence signal contributes points (canonical verb +3, masked digits +2, UPI ref +2, named merchant +2, amount-only +1, soft anti-signals −5); single global threshold decides MEDIUM vs HIGH.
**Why:** today every parser hardcodes its own confidence values (0.76 / 0.55 / etc.) — two very different bodies score the same number. Tuning is per-parser edits in nine files. Move *one* parser to the tally; if it feels right, Sprint 2 migrates the rest.
**Touchpoints:** `NotificationParseResult.parseConfidence`, `ingestion/GenericUpiNotificationParser.kt`, new shared `EvidenceTally` helper, `NotificationDecisionEngineTest`.
**Blocked by:** nothing. Time-box to 1 day so it doesn't bleed into Sprint 2.

---

## Sprint 2 — Polish on the existing theme *(shipped 2026-05-22, v0.14.5)*

*(Previously gated on the Aviate-vs-vwfndr design call; rescoped 2026-05-22
to target the current warm Clay / Sage / Paper Material 3 theme. The big
visual revamp moved to `REVAMP` in Slotted as a post-alpha item.)*

*All six items below merged into main across PRs #66 (INBOX-WHY-COPY),
#67 (CAL-INTER), #68 (COLDSTART), #69 (S1.3 rest), #70 (MANUAL-FAST),
#71 (POLISH-1). Listed for traceability — the work is done. Next:
Sprint 3 (release prep), then v1.0.*

### POLISH-1 — Home / Inbox / Settings polish pass
**What:** Visual review pass on the three most-trafficked surfaces in the **current** theme. Common rough spots based on past testing:
- spacing inconsistencies (margins, padding) between Home / Inbox / Settings
- typography hierarchy on Home (greeting vs month vs budget number)
- empty states (Inbox empty, no transactions yet, no income captured)
- snackbar / toast styling
- Recap surface visual density
**Why:** pre-1.0 product polish; the v0.13.8 onboarding pass cleaned Welcome / Permissions / Profile screens but Home / Inbox / Settings haven't had a focused visual review since.
**Touchpoints:** mostly `MainActivity.kt` composables. Could prompt an L5-flavoured decomposition pass as a side effect.
**Blocked by:** nothing. ~2 days inside the existing palette.

### COLDSTART — Cold-start hydration / loading skeletons
**What:** First open of Home / Inbox / Transactions flashes empty before flows hydrate. Add a quick skeleton (in the current theme's idiom — soft `surfaceVariant` shimmer pills, no extra-flavor styling) and only swap in real content once the flow has emitted at least once. Pair with empty states (no income captured yet, etc.) — both surfaces need the same "we're alive but not ready" affordance.
**Why:** the empty-flash reads as "broken" to first-time users. Fast fix, high perceptual value.
**Touchpoints:** the three tab composables in `MainActivity.kt`, plus an empty-state composable that swaps in when the flow emits an empty list.
**Blocked by:** nothing. ~½ day.

### INBOX-WHY-COPY — Refine per-reason chip copy
**What:** The reason-chip slot already exists (`row.reasonLabel` renders inside a tinted Surface at `MainActivity.kt:1837`). Today the label distinguishes mainly SUGGESTED vs INBOX. Expand to read from `parsed_signals.parserKey` + confidence tier + presence/absence of merchant/maskedDigits and emit specific reasons (`LOW CONFIDENCE`, `NO MERCHANT`, `AMOUNT ONLY`, `NEW SENDER`, `MASKED DIGITS MISSING`).
**Why:** PRD calls for "every inferred transaction should be explainable." Today users see a chip but it doesn't tell them what specifically tripped the gate.
**Touchpoints:** the `reasonLabel` derivation in `HomeViewModel`. No UI rebuild needed.
**Blocked by:** nothing structurally; ride with Sprint 2 polish. ~½ day.

### MANUAL-FAST — Manual entry speed pass (20-second goal)
**What:** Spec target for manual entry is 20 seconds. Levers: (a) category autocomplete from the user's recent 30-day picks, (b) "similar to last Swiggy / Uber" suggestion when amount + merchant match a recent pattern, (c) swipe-to-account pre-selection so card vs cash is one gesture not a dropdown.
**Why:** manual entry is the fallback when ingestion misses something — friction here is *the* failure mode for trust.
**Touchpoints:** the manual-entry sheet in `MainActivity.kt`, possibly a new `RecentPicksRepository` (small in-memory cache over `CanonicalTransactionDao`).
**Reference:** [rupee-android-screens.md §12](rupee-android-screens.md).
**Blocked by:** nothing. ~1 day.

### CAL-INTER — Swipe-month navigation on Calendar
**What:** Tap-day-to-show-txns is wired (`CalendarScreen.kt:76`). Add gesture-based **month** navigation — the ViewModel has `displayMonth: MutableStateFlow<YearMonth>` driven by chevron buttons only; wrap the calendar grid in `HorizontalPager` or use `detectHorizontalDragGestures` bound to `displayMonth`.
**Why:** chevron-only feels dated next to the iOS / Android system calendars. Half a day for a meaningful UX win.
**Touchpoints:** `calendar/CalendarScreen.kt`, `CalendarViewModel.displayMonth`.

### S1.3 (rest) — Migrate remaining 8 parsers to evidence tally
**What:** If the Sprint 1 pilot validated the additive-tally pattern, migrate the other 8 parsers. Each takes ~1-2 hours including its `NotificationParserParseTest` updates. Total ~1-2 days. **Skip if the pilot felt wrong** — the cost of keeping 9 hardcoded confidence floors is bounded.
**Blocked by:** Sprint 1 pilot result.

---

## Sprint 3 — Alpha prep *(≈1 week)*

This is the sprint that earns the **alpha** tag. Four items.

### BABYPROOF-INPUTS — type-limit + picker pass across every input
**What:** Every `OutlinedTextField` / `TextField` in the app gets the
right input type, validator, and picker affordance. Four shared
composables to build, then a sweep replacing every offending site.

The four primitives:
- `CurrencyInputField` — ₹ prefix, two-decimal cap, Indian-grouping
  `visualTransformation` (`1,00,000` not `100,000`), digit-only filter,
  `KeyboardType.Decimal`. Returns minor units as `Long`. Replaces 6+
  amount inputs.
- `DateField` — clickable field that opens a Material 3
  `DatePickerDialog`, displays selected date, returns `LocalDate`.
  Replaces 2 EMI/card "type yyyy-MM-dd" sites that today silently
  accept garbage.
- `ProviderDropdown` — `ExposedDropdownMenuBox` seeded with the
  providers our parsers know about (SBI / ICICI / Axis / HDFC / Kotak
  / Yes / Federal / Jupiter / Fi / Niyo + "Other"). Replaces 2 onboarding
  text fields.
- `AccountPicker` — `ExposedDropdownMenuBox` over the user's accounts
  + cards. Used in the manual-entry sheet (currently missing entirely
  — manual entries can't pick an account today) and on the Inbox /
  Transactions edit sheet if we want to allow account re-routing.

Concrete sweep (from the 2026-05-23 input audit):
- Onboarding: bank provider, card provider, starting cash balance.
- Manual entry: amount → CurrencyInputField; **add** account picker.
- Inbox review: amount draft → CurrencyInputField.
- Budgets: monthly + per-category limits → CurrencyInputField.
- EMI draft: monthly amount, tenure months (integer 0–120), next due
  date → DateField, notes.
- Card draft: amount due, due date → DateField.

**Why:** "I typed a date and the app accepted garbage" is the kind of
trust-damaging bug early alpha testers will surface and lose faith
over. Babyproofing closes that whole class. The Material 3 toolkit
already has all the pieces — this is just wiring.
**Touchpoints:** `MainActivity.kt` (manual entry, inbox review, txn
detail), `cards/CardsEmisScreen.kt`, `budgets/BudgetsScreen.kt`,
`onboarding/*`, new `ui/Inputs.kt` for the four primitives.
**Blocked by:** nothing. ~2–3 days, mostly mechanical.

### DUMP-REPLAY-REBASELINE
**What:** Re-bake the dump-replay harness baseline against the
post-Sprint-2 state. Sprint 0/1/2 deliberately changed which bodies
get accepted (Indian-numbering fix, autopay vocab, CRED reorder, the
per-parser tier promotions from S1.3 rest). The current baselines have
loose tolerance to absorb those; tightening them now gives future
regressions a sharper alarm.
**Touchpoints:** `DumpReplayTest.kt` baselines, `DumpReplayHarness.kt`
if any tolerance constants need to drop.
**Blocked by:** BABYPROOF-INPUTS shouldn't change parser behaviour, so
these can run in either order; rebaseline last so any incidental
parser-test changes are captured.

### ALPHA-STAGE-ROLL
**What:** Side-load v0.15.0-alpha.1 onto team devices, run it through
24h of real notification traffic (your real SMS bridge, your real bank
apps), keep a written log of every weirdness. Goal isn't zero bugs —
it's *known* bugs.
**Why:** Synthetic dumps catch shape regressions. Only real-device
soak catches battery drain, alarm timing, push-arrival sequencing
issues, and the very long tail of "this notification looks fine on
paper but Compose renders it weird because of `<br/>`" edge cases.

### ALPHA-CUT — version bump + tag
**What:** Update `versionName = "0.15.0-alpha.1"`, `versionCode = 42`.
Write a real CHANGELOG.md (it doesn't exist yet — Sprint 3 is the right
moment to start one). Build signed release APK. Git-tag `v0.15.0-alpha.1`.
**Why:** Tags are what give us the ability to roll back if alpha
testing finds something catastrophic.
**Touchpoints:** `android/app/build.gradle.kts`, new `CHANGELOG.md` at
repo root, git tag.

---

## Post-alpha sprints

*Every item that lived in the old `Slotted` / `Watching` sections now has
a sprint home below. The order reflects dependencies + a natural cadence
through the closed-beta → open-beta → RC arcs. Sprints group items that
share touchpoints or schema work.*

---

## Phase A — Alpha → Closed beta *(~3 weeks of work)*

The "harden it enough for ~50 real testers" arc. Stability first, then
the two UX gaps that close out the alpha feature set, then a parser-
coverage sprint so closed-beta testers on non-pilot banks have a useful
experience.

## Sprint 4 — Stability hardening *(≈1 week)*

The six audit findings from the 2026-05-23 deep code review. Every item
is either a class of bug we want to design out before real testers see
it, or a test-coverage gap that lets future regressions sneak in.

### CAST-SAFETY — drop `Array<Any?>` + `UNCHECKED_CAST` in VM flow combinators
**What:** `HomeViewModel.dashboardData` and `viewSelection` (~lines 305–370)
and `SettingsViewModel`'s combine chain pack heterogeneous flows into
`arrayOf<Any?>()` then unpack each slot with `as` casts. A typo in the
array indices would only surface at runtime in the UI with a
`ClassCastException`. Replace with intermediate typed data classes so
the compiler enforces shape.
**Why:** the kind of bug that crashes a real tester silently mid-session.
**Touchpoints:** `HomeViewModel.kt`, `SettingsViewModel.kt`.
**Blocked by:** nothing. ~1 day.

### TRUST-WRITE-RACE — wrap `setMerchantTrust` read+write in a transaction
**What:** [LocalFinanceRepository.kt:641-651](android/app/src/main/java/com/zegrt/rupee/data/repository/LocalFinanceRepository.kt#L641-L651) reads `getRulesForUser`, searches for a match in memory, then either upserts or deletes. Two rapid taps from the new TRUST-FROM-TXN toggle could race. Fix: wrap the lookup+write pair in `database.withTransaction { ... }`, or add an idempotent upsert-by-pattern DAO method.
**Why:** the trust-from-txn surface (Sprint 1 shipped) lets users toggle this fast — race conditions become real.
**Touchpoints:** `LocalFinanceRepository.setMerchantTrust`, `MerchantTrustRuleDao`.
**Blocked by:** nothing. ~1 hour.

### MONEY-MATH-LEGACY — backport BigDecimal pattern to `GenericNotificationParser`
**What:** [GenericNotificationParser.kt:68](android/app/src/main/java/com/zegrt/rupee/ingestion/GenericNotificationParser.kt#L68) is the last parser using `Double * 100 → Long`. Every other parser routes through `NotificationParsingUtils.extractAmountMinor` which uses `BigDecimal.movePointRight(2).toLong()`. Backport.
**Why:** off-by-one rounding on edge amounts. Low frequency, but it's the kind of bug that's impossible to debug from logs.
**Touchpoints:** `GenericNotificationParser.kt`.
**Blocked by:** nothing. ~10 min.

### DATE-PARSE-LOGGING — surface silent ISO-8601 parse failures
**What:** Three `runCatching { … }.getOrNull()` sites in `HomeViewModel.formatOccurredAt`, `RecurringDetectionEngine`, and `DuesAlertManager` swallow `DateTimeParseException`. If a malformed timestamp ever lands in the DB (migration bug, third-party writer), the UI silently drops the row from upcoming-dues and recurring detection. Add `Log.w("Rupee", ...)` before the `getOrNull()`.
**Why:** silent UI dropouts are the worst kind of bug — testers report "the upcoming-dues card isn't showing my EMI" with no failure trail.
**Touchpoints:** the three sites above.
**Blocked by:** nothing. ~1 hour.

### MIGRATION-SKIP-TEST — chained v8 → v11 migration coverage + fix MigrationTest asset packaging
**What:** We have per-migration tests via `MigrationTestHelper` (Sprint 0 / T1). We don't have a chained "open a v8 fixture DB, end up at v11, verify every column exists" test. Room applies migrations sequentially so any gap in the ladder is silent.
**Sub-item (discovered 2026-05-23):** the existing per-migration tests fail with `FileNotFoundException: Cannot find the schema file in the assets folder. Missing file: com.zegrt.rupee.data.local.RupeeDatabase/{8,9,10}.json`. The schemas ARE exported to `android/app/schemas/...` but Room's `AndroidMigrationTestHelper` expects them in `androidTest` assets. Fix: add `sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")` to `build.gradle.kts` so the schema JSONs ship with the test APK.
**Why:** future schema work (post-alpha sprints add ~6 new entities) will land more migrations; need a guard against drift. *And* the existing per-migration tests need to actually run before we can rely on them.
**Touchpoints:** `build.gradle.kts` sourceSets, new chained test in the existing `androidTest` migration suite.
**Blocked by:** nothing. ~1 day.

### DUMP-OUTCOME-DAO-TEST-FK — fix `DumpOutcomeDaoTest` FK violations
**What:** `DumpOutcomeDaoTest.snapshot_confirmFreshInboxRow_populatesAllFields` and `snapshot_mergeIntoExistingRow_setsMergedFromColumn` both fail with `SQLiteConstraintException: FOREIGN KEY constraint failed`. Discovered 2026-05-23 during the v0.15.0-alpha.1 hotfix work. The tests pre-date H4's FK enforcement and the fixture data they construct violates the cascade chain. Likely fix: ensure the test fixture inserts the parent rows (raw_capture_events → parsed_signals → transaction_candidates) before referencing them in inbox/canonical inserts.
**Why:** these tests cover the dump-snapshot DAO that the share-with-debug pipeline relies on. Without them running, regressions in the snapshot DAO (the same query that surfaced the cascade-delete bug) ship silently.
**Touchpoints:** `androidTest/.../data/local/dao/DumpOutcomeDaoTest.kt`.
**Blocked by:** nothing. ~1 hour.

### CANONICAL-AUDIT-TRAIL — switch `CanonicalTransactionDao.upsertTransactions` to @Upsert
**What:** Sibling fix to v0.15.0-alpha.2's `TransactionCandidateDao` change. `CanonicalTransactionDao.upsertTransactions` is still `@Insert(onConflict = REPLACE)`. Because `transaction_candidates.linkedCanonicalTransactionId` and `inbox_items.linkedCanonicalTransactionId` are FK with `onDelete = SET NULL`, every REPLACE on a canonical row silently NULLs out the back-pointers from any inbox / candidate audit row. This is a softer bug than the cascade-delete (no data loss, just audit-trail loss), but the fix is one-line: switch the DAO to `@Upsert`.
**Why:** every `confirmSuggestedTransaction`, `deleteTransaction`, `updateTransactionDetails` call currently silently breaks the audit trail. Hard to spot in normal use but it'll bite when S6 (Sprint 8) tries to follow `mergedFromExistingCanonicalId` pointers and finds nulls.
**Touchpoints:** `CanonicalTransactionDao.kt` (one annotation swap).
**Blocked by:** nothing. ~10 min PR.

### REPO-VM-TEST-COVERAGE — unit tests for the high-stakes repo + VM paths
**What:** `LocalFinanceRepository.confirmInboxItem` / `setMerchantTrust` / `deleteTransaction` / `getLedgerExportSnapshot` are only covered by integration via `DumpReplayTest`. `HomeViewModel`'s 8-flow combine has zero direct tests. The `Array<Any?>` casts above are dangerous *specifically because* nothing tests them.
**Why:** if we want to ship to non-internal testers safely, the load-bearing repo paths need to be unit-tested first.
**Touchpoints:** new tests under `androidTest/.../data/repository/` + `test/.../home/`.
**Blocked by:** CAST-SAFETY (once VM combinators use typed data classes the VM tests get tractable). ~2-3 days.

---

## Sprint 5 — Inbox / Home UX completeness *(≈1 week)*

Three UX gaps users will hit during closed-beta soak. All
design-direction-agnostic (work on the current theme; survive REVAMP).

### NOTIF-CHANNELS — auto-confirmed silent notif + interactive review notif
**What:** Post one of two notification shapes depending on what the
parser did with an incoming transaction:

- **Auto-confirmed** (parser cleared HIGH tier → `SUGGESTED` row
  auto-created): `IMPORTANCE_LOW` channel `auto_confirmed`. Silent, no
  peek, sits in the shade. Title: `₹450 · Swiggy`. Body:
  `Auto-confirmed. Tap to edit.` One inline action: `Categorize`.
  Group all daily auto-confirms under a summary notif
  (`5 auto-confirmed spends today (₹3,420)`).
- **Needs review** (parser landed in Inbox via MEDIUM tier or
  AMBIGUOUS_*): `IMPORTANCE_DEFAULT` channel `needs_review`. Peeks
  once, plays sound once, then collapses. Title: `Review ₹450 at
  Swiggy`. Two inline actions: `Confirm spend`, `Not a transaction`.
  Body tap opens the in-app Inbox focused on the row. When the user
  taps an action from the shade, the notif updates with a 3-second
  `Undo` action before fully cancelling.

**What NOT to use:** full-screen intents (Android-14 restricted,
hostile UX for non-urgent surfaces), `IMPORTANCE_HIGH` (sound + peek
for every spend is muted-within-a-week territory), bubbles (wrong
shape), more than 3 inline actions, an "I'll deal with this later"
action (swipe-to-dismiss already does that).

**Why:** Closes the trust gap on auto-confirm — today the parser
silently writes a SUGGESTED row and the user has to open the app to
know it happened. The asymmetry (small for auto, big for inbox) is
correct: auto-confirmed has no decision to make; inbox has one.

**UX heuristic backstop** *(Nielsen, well-worn for a reason)*:
- **#1 Visibility of system status** — silent notif on auto-confirm
  proves the parser exists.
- **#2 Match between system and real world** — concrete copy
  (`₹450 at Swiggy`) beats abstract (`Was this a transaction?`).
- **#3 User control** — per-channel mute is non-negotiable; the two
  channels are what lets a power user silence auto-confirmed while
  keeping needs-review.
- **#5 Error prevention** — the 3s `Undo` window on shade-action taps
  catches accidental "Not a transaction" mis-taps.
- **Notification fatigue** (not Nielsen but real) — every push raises
  the cost of the next one; quiet hours (11pm–7am) suppress
  `auto_confirmed` and batch them as a morning summary.

**Touchpoints:** new `notifications/IngestionNotifier.kt` builds the
two `NotificationCompat.Builder` shapes. New
`notifications/NotificationActionReceiver.kt` (BroadcastReceiver) routes
shade actions into `repository.confirmInboxItem` /
`dismissInboxItem`. New `notifications/NotificationChannelManager.kt`
registers both channels at app start. Settings entry deep-linking to
the per-channel page (the standard Android long-press → channel mute
covers 90% of customisation).

**Implementation cost:** ~2 days for the happy path + a 3rd day for
grouping, summary notif, quiet-hours scheduling. Real cost is the
test matrix: Android 13+ post-notifications permission, Android
12-and-below grace, channel-mute regressions, BroadcastReceiver
lifecycle when the app is killed.

**Why post-alpha not Sprint 3:** Adds new permission surface
(`POST_NOTIFICATIONS` already requested, but channel discoverability
needs UX) and a new BroadcastReceiver that lives outside the
notification listener's process — both warrant some closed-beta
soak before we ship to a wider audience.

**Blocked by:** nothing technical. Should be among the first
post-alpha items because it's design-direction-agnostic (works on
either Aviate or vwfndr, or the current theme).

### INBOX-MERGE-PICKER — search-then-select transaction picker for inbox merge
**What:** The `mergeInboxIntoTransaction(inboxItemId, targetTransactionId)` repo method exists and the `mergedFromExistingCanonicalId` column shipped in v0.14.0. What's missing is the user-facing picker: "I see a duplicate of an existing transaction — find that one to merge into." Today the workaround is the two-step soft-confirm where you pick from recent transactions only.
**Why:** Closed-beta testers with any duplicate-prone notification setup (Truecaller + native bank, CRED + bank, etc.) will hit this fast. The right UX is a search field over the user's last-90-day transactions filtered by amount-within-20% / same-merchant-prefix.
**Touchpoints:** new `MergePickerSheet` composable, new `repository.searchTransactionsForMerge(query, hintAmount, hintMerchant)`, plumbing into the Inbox row.
**Blocked by:** nothing. ~1–2 days.

### BUCKET-PROGRESS — per-bucket progress cards on Home + budget_category_assignments
**What:** `TransactionBucketAssignmentEntity` is defined (audited 2026-05-23) but unwired — no DAO, no observer, no Home composable. Wire it: new `TransactionBucketAssignmentDao`, join into `HomeViewModel.DashboardData`, render per-bucket progress bars on Home alongside the existing category-budget bars. Also defines the new `BudgetCategoryAssignmentEntity` (currently budgets reference categories implicitly — the explicit link table makes per-category-per-budget allocations addressable).
**Why:** "Coming soon" copy in the Budgets page since v0.11; needs to actually ship before open beta.
**Touchpoints:** new DAOs, `HomeViewModel`, `BudgetsScreen`, new `BudgetCategoryAssignmentEntity`, migration adding `budget_category_assignments` table.
**Blocked by:** nothing. ~1–1.5 days.

---

## Sprint 6 — Parser corpus + EMI-AUTO *(≈1 week)*

The "alpha shipped, now broaden coverage" sprint. Closed-beta testers will be on banks our pilot corpus never touched — front-load the parser additions and the EMI auto-detect they unblock.

### Parser corpus expansion
**What:** Add dedicated parsers for the body shapes we know exist but haven't validated against a dump. Each one is shaped like our existing Kotak/PhonePe parsers: `canParse` package + verb gate, `parse` extracts amount/merchant/digits via shared utils, scoring via `EvidenceTally`.
- **SBI YONO** — guessed in `notification-ingestion-deep-dive.md` §3, never confirmed.
- **Federal Bank, Yes Bank** — same.
- **Jupiter / Fi / Niyo** — newer fintechs, possible `RemoteViews` usage we'll need to handle in the extractor.
- **Foreign-currency transactions** — many issuers SMS-only; the amount-extraction regex needs USD/EUR/AED prefix support and a currency-code field on `ParsedSignalEntity`.
**Why:** at-most 30% of closed-beta testers will be on our pilot banks (ICICI/CRED/Kotak/PhonePe/Paytm/GPay). The rest fall to the generic UPI parser or generic fallback today, both of which sit at MEDIUM tier and produce a janky Inbox.
**Touchpoints:** 4 new parser classes + registry order entries; `NotificationParserParseTest` cases for each.
**Blocked by:** ideally one real dump per provider before writing the parser. ~½ day per parser when we have the body shape.

### EMI-AUTO — EMI auto-detection from notifications (+ emi_transaction_links entity)
**What:** Today `EmiPlanEntity` is populated only by manual entry. EMI debit notifications parse as SPEND. Extend the EMI parser to upsert into `emi_plans` when amount + merchant + due-date all extract confidently — same shape as the `applyBillDueToCard` side-effect already wired in `NotificationSignalNormalizer`. Adds the `emi_transaction_links` table to associate detected debit transactions with their plan (currently no join exists between the two).
**Why:** quality upgrade — manual EMI entry covers the alpha feature checklist. Auto-detection needs real-user EMI corpora to tune confidence thresholds against, which is exactly what closed-beta surfaces.
**Touchpoints:** `EmiNotificationParser` (extends existing), new `applyEmiToPlan(...)` in `NotificationSignalNormalizer`, new repo method + `EmiPlanDao.upsertPlan`, new `EmiTransactionLinkEntity` + DAO + migration.
**Open design Q:** route confirmed EMIs to Inbox first vs auto-add to `emi_plans`? Recommend Inbox-first — EMIs are commitments and the user should verify before they show up on Home's upcoming-dues strip.
**Blocked by:** nothing technical. ~2–3 days.

---

## Phase B — Closed beta → Open beta *(~4-5 weeks of work)*

The cross-stream record linking arc. S2 unlocks rule-driven extraction; S2.1 + S6 + S3 share the underlying pattern "this notification is *related to* an existing record, not a fresh event." Worth shipping as a coherent multi-sprint family.

## Sprint 7 — S2: JSON rule engine + extractedJson + PATTERN-TELEM *(≈2 weeks)*

The foundational sprint for the S-family. Once this ships, parser tweaks no longer need an APK release.

### S2 — JSON-driven rule engine
**What:** Walnut-style per-package rule table (regex sets indexed by `packageName`) loaded from JSON at app start. `pattern_UID`, `sort_UID`, `obsolete` versioning. OTA-tunable via a remote-config style endpoint (or a bundled-with-app JSON we update via APK at first).
**Why:** parser logic currently in Kotlin classes — every rule tweak ships an APK. A rule table lets us update parser behaviour without a release. Reference: `axio-takeaways.md` Item 2.
**Touchpoints:** new `rule_patterns` table, new `RuleEngine` that sits alongside (and eventually replaces) the parser registry, JSON schema for rule files.

### §9.3 extractedJson — persist per-Bundle field breakdown
**What:** Add `extractedJson TEXT NULL` to `RawCaptureEventEntity`. Stores the structured per-field breakdown (title vs subText vs textLines vs ticker) instead of just the flat `combinedBody`. Lets S2's rule engine apply field-priority rules (which most production parsers do).
**Why:** S2 specifically needs this — without per-field separation, rules can't say "match in title only." Folds in naturally with the S2 sprint.
**Cost:** ~1-2 KB/notif storage; small migration.
**Touchpoints:** schema change + extractor change.

### PATTERN-TELEM — pattern telemetry / OTA-readiness
**What:** Once S2's rules carry `pattern_UID`, log which patterns fire in production (counts, last-fired timestamp) so the rule corpus can be tuned from real data. Per `axio-competitor-analysis.md` §3.7.
**Why:** S2 is useless without telemetry — you can't tune what you can't see fire. Ships in the same sprint as S2.
**Touchpoints:** new `pattern_telemetry` table or rolling-window counters; debug surface in the Debug screen.

---

## Sprint 8 — S2.1 + S6: cross-stream record linking *(≈2 weeks)*

S2.1 (chain dedupe via network reference) and S6 (additive enrichment) are two sides of the same coin — both replace today's subtractive `DUPLICATE_IGNORED` flow with a "this is a related record" flow. They share enough touchpoints to ship together.

### S2.1 — Chain dedupe via network reference
**What:** Use the v0.13.0 `networkReferenceId` column to chain duplicates across providers (e.g. HDFC bank notif + CRED mirror of the same swipe). Today dedupe is fingerprint + 5-minute-bucket; cross-package duplicates with different merchant cleaning slip through.
**Touchpoints:** `NotificationDedupeEngine.detect`, new `dedupe_groups` + `dedupe_group_members` tables (replacing the flat `dedupeFingerprint` field as the long-term shape).
**Blocked by:** S2 (Sprint 7) — rules need to emit network refs reliably across providers first.

### S6 — Dedupe enrichment (additive instead of subtractive)
**What:** Today's dedupe is *subtractive* — finds a duplicate, marks it `DUPLICATE_IGNORED`, throws the data away. Change to *additive* — treat the dup as a second source of evidence that fills gaps on the existing canonical transaction:
- If the original canonical has `merchantName=null` but the dup has `merchantName="Swiggy"` → fill merchant
- If the original has no `maskedDigits` but the dup has `"1234"` → fill digits
- If the original has no `networkReferenceId` but the dup has one → fill ref id
**Why:** the 2026-05-19 dump showed PhonePe pushes (rich merchant, no account digits) and Truecaller-mirrored bank SMS (rich account digits, weak merchant) arriving for the same transaction. Each has data the other doesn't. Today we keep whichever fired first and discard the second. Enrichment captures the best of both.

**Truecaller and Walnut specifically — primary enrichment sources, not dupes to discard.** The 2026-05-22 Nothing-A015 dump showed the textbook case: a single ₹3 Kotak debit fired three notifications — the native Kotak811 push (`₹3.00 sent from XX4129` → has package attribution, timestamp, masked digits, but no payee), three Truecaller SMS mirrors (`Sent Rs.3.00 from Kotak Bank AC X4129` → has explicit bank name and the `UPI Ref XX YYYY` token), and a Walnut SMS-bridge push (`₹3.00 at 8943068824@YESCRED` → has the recipient UPI handle that no other source carries). Today: Kotak becomes one Inbox row, Walnut becomes a second, all three Truecaller mirrors are `DUPLICATE_IGNORED`. Under S6 these merge into a single canonical row carrying the union of the data — Kotak's package/digits + Truecaller's UPI ref + Walnut's recipient handle.

- Truecaller's `subText` field carries the issuer name in plain text — useful for cross-validating brand attribution.
- Walnut posts the merchant/handle the native bank push never includes. Treat Walnut and Truecaller as complementary, not redundant.

**Three structural decisions to settle before this ships:**

1. **Field-by-field merge policy.** Per field: "first non-null wins" vs "higher-confidence parser wins" vs "newer wins." Probably different per field. `merchantName` should prefer the brand-aware parser's value. `maskedDigits` should prefer first-non-null (they don't change across sources). `networkReferenceId` is similar.
2. **Respect user edits.** If the user manually changed the merchant to "Coffee shop" after confirming the inbox row, a later mirror with a generic merchant must NOT overwrite. Recommended: treat any `CONFIRMED` canonical's user-facing fields as locked. Simpler than a per-field `userEditedAt`.
3. **Provenance trail.** For debugging "why did this transaction's merchant change," we want either a `merchantSource: parserKey` lookup field on the canonical row, or an audit log of field-level updates. Without provenance, enrichment becomes silent mutation. **This is where `canonical_transaction_source_links` lives.**

### MISSED-TXN — missed-transaction detector via balance reconciliation
**What:** Walnut catches the "you said balance is X but txns sum to Y" gap. We track `currentBalanceMinor` on `AccountEntity` but don't extract `balanceAfterMinor` from notifications and don't reconcile. Now that S2 (Sprint 7) gives us reliable cross-provider extraction, we can add (a) parsers emitting `balanceAfterMinor`, (b) a new column on `ParsedSignalEntity` to store it, (c) a reconcile job that diffs transactions-sum vs latest reported balance and surfaces "missed something" on the Debug screen (and eventually as a user-facing nudge).
**Why:** the highest-leverage trust signal we don't currently provide — telling the user *what we missed* is more valuable than telling them what we caught.
**Blocked by:** S2 (rule-driven balance extraction) → folds here naturally.

**Touchpoints (whole sprint):** `NotificationDedupeEngine.detect`, `NotificationSignalNormalizer.normalizeLocked`, new `CanonicalTransactionEnricher`, new `dedupe_groups` + `dedupe_group_members` + `canonical_transaction_source_links` tables, new column on `ParsedSignalEntity`, new reconcile job.

---

## Sprint 9 — S3: Refund linking *(≈1 week)*

### S3 — Refund linking
**What:** 5-strategy refund detection (same merchant + amount in 30d, network ref match, etc.) → link refund to original spend. Today refunds either get ignored or appear as a separate income row that the user has to mentally match against the original spend.
**Why:** the third side of the cross-stream linking arc (after S2.1 chains and S6 merges). Same shape: "this notification is *related to* an existing record." Reference: `axio-takeaways.md` Item 5.
**Touchpoints:** new `RefundLinker` that hangs off `NotificationSignalNormalizer`, new `refundOfTransactionId` column on `CanonicalTransactionEntity` (or use the existing `dedupe_groups` infrastructure with a `relationshipType` discriminator), UI badge on the Transactions list for linked refunds.
**Blocked by:** S2.1 (Sprint 8) — refund-by-networkRef is the highest-confidence strategy and needs the chain-dedupe plumbing.

---

## Phase C — Open-beta polish *(~4-5 weeks of work)*

The "we're nearly public" arc. Adaptive learning, broader ingestion, and the recap modernisation that was deferred from alpha.

## Sprint 10 — S4-5: Adaptive confidence + proper alert rules *(≈2 weeks)*

### S4-5 — Adaptive confidence from user behaviour
**What:** `ingestion_signal_stats` table keyed `(package, parserKey, cleanedMerchant)`. Confirm counts up, dismiss counts down. Bootstrap mode for the first 14 days lowers the MEDIUM threshold from 0.6 → 0.5 to be more liberal at onboarding. Auto-promote to `MerchantTrustRule` after 3 confirms in a 7+ day window.
**Why:** the app *learns* the user's actual transaction patterns instead of relying on hardcoded confidence floors. User asked for this explicitly. Reference: `axio-takeaways.md` Item 13.
**Touchpoints:** new `ingestion_signal_stats` table + DAO, `NotificationDecisionEngine` reads stats, UI shows an "auto-trusted (3 confirms)" badge.

### alert_rules / alert_events — modernise DuesAlertManager
**What:** Today `BudgetAlertManager` + `DuesAlertManager` use SharedPreferences to dedupe alert posts. The schema spec defines `alert_rules` + `alert_events` tables; ship them here. S4-5's adaptive learning gives us the first real use case for queryable alert history ("show me all the 'we caught this' / 'we missed this' events from last month").
**Why:** SharedPrefs dedupe was a v0.11 expedient. The proper tables unblock cross-month alert analytics and let us add new alert types (the S4-5 auto-trust promotion is a candidate) without inventing more prefs keys.
**Touchpoints:** new `AlertRuleEntity` + `AlertEventEntity` + DAOs, refactor of `BudgetAlertManager` + `DuesAlertManager` to write to the events table.

---

## Sprint 11 — SMS pipeline *(≈2 weeks)*

The biggest ingestion expansion. SMS is how non-CRED users on plain bank apps actually get transaction notifications.

**What:** Add SMS ingestion alongside the existing notification listener. `RawCaptureSourceType.SMS` enum value already exists. Need:
- SMS-receiver `BroadcastReceiver` + `READ_SMS` / `RECEIVE_SMS` permissions (real permission ask, with PRD §17 messaging).
- TRAI sender-suffix gate (`-T` transactional / `-S` service / `-P` promo / `-G` government — May 2025 rule). First-stage filter is free signal.
- 6-alpha (transactional/service) vs 6-numeric (promo) header discrimination as a second-stage filter.
- Same `TransactionalGate` + parser registry as notifications; SMS bodies are usually a subset of what banks already push to notifications, but for users without the bank app installed this is the only channel.

**Why:** every wallet competitor (Walnut/Axio, PennyWise, Truecaller) leans on SMS. Until we ship this, we're notification-only — which limits us to users who actually have their bank app installed *and* notification access granted *and* permissioned.

**Touchpoints:** new `sms/SmsListenerService.kt`, new `sms/SmsExtractor.kt` (analogous to `NotificationExtractor`), permission manifest changes, onboarding step for SMS permission. Reference: research report stashed in conversation context; PennyWise AI repo for architecture lessons.
**Blocked by:** ideally S2 (rule engine) — adding SMS as a parser source is much easier when parsers are JSON.

---

## Sprint 12 — RECAP-PERSIST + monthly_recaps *(≈1 week)*

### RECAP-PERSIST — persisted monthly Recap snapshots
**What:** Recap is computed-on-read today. PRD describes a "story-like highlights" surface (biggest category, most expensive day, variance vs last month, fixed vs discretionary). Persist a `MonthlyRecap` snapshot row per closed month so the surface loads instantly and we can build "share my month" later. Adds the `monthly_recaps` table.
**Why:** Recap is too expensive to recompute on every open as transaction count grows; also blocks any cross-month comparison that needs a stable historical snapshot. Also unblocks the Aviate Wrapped-style shareable artifact *if* that direction wins in REVAMP (Sprint 14).
**Touchpoints:** new `MonthlyRecapEntity` + DAO, scheduled `WorkManager` job on month-close, `RecapViewModel` reads from DAO with fallback to live compute. Reference: PRD §14, §21.
**Note:** the *deeper* recap design (shareable artifact, story shape) is REVAMP-sensitive — ship the persisted snapshot here; the rendering layer lands with REVAMP in Sprint 14.

---

## Phase D — Release candidate → 1.0 *(~3 weeks of work)*

## Sprint 13 — LEDGER-IMPORT *(≈1 week)*

### LEDGER-IMPORT — read CSV/JSON back into the ledger
**What:** Sprint 1's `EXPORT-UI` is one-way only. Add a Settings → Import path that reads either format and inserts rows into `canonical_transactions`. Schema validation, foreign-key remap (merchant/category/account *names* → IDs, auto-creating if missing), conflict resolution against `dedupeFingerprint`, atomic Room transaction so a malformed file doesn't half-write.
**Why:** new-phone restore is the only currently-impossible workflow. Power-user bulk-edit (Excel round-trip) is the secondary use case. Becomes a real ask during open beta when testers swap devices.
**Touchpoints:** new `diagnostics/LedgerImporter.kt` (mirror of `LedgerExporter` with `parseCsv`/`parseJson` + `Result<ImportSummary>` return shape), new `LocalFinanceRepository.importLedgerSnapshot(...)` wrapping inserts in `withTransaction`, new Settings card with a confirmation step ("This will add 412 rows. 17 look like duplicates — skip / overwrite / both?").
**Cost:** ~3–4× export. File parsing is straightforward; making "I exported, edited the merchant column, re-imported" actually merge into existing rows is the hard part.
**Open design Q's:** Conflict policy (recommend skip-on-`dedupeFingerprint`-match with an "overwrite duplicates" checkbox). Schema versioning (`schemaVersion: 1` lets the importer reject future-format files cleanly). Merchant/category creation (auto-create unknown names — friendlier but pollutes the trust corpus; recommend auto-create with a post-import dialog asking which to keep).

---

## Sprint 14 — REVAMP: design overhaul (Aviate vs vwfndr) *(≈1-2 weeks)*

### REVAMP — full design overhaul
**What:** Sprint 0/1/2 hardened the wallet on the current warm Clay/Sage/Paper Material 3 theme. **After alpha + closed-beta + open-beta + RC have all soaked**, pick one of the two design directions explored on the `design/aviate-vs-vwfndr` branch and rebuild the visual layer top-to-bottom. Style-beat-sized investment, not a polish pass — closer to "Spotify's next-version redesign" than to spacing tweaks.
**Why now:** Picking one is a *positioning* decision (emotional/shareable vs instrument/signed-receipt), and that decision is only honest after we've seen how testers actually use the existing surface for ~3 months.
**What's involved:**
- Pick the direction (decision, not code). See `DESIGN_NOTES.md` for the trade-off.
- Replace `ui/theme/Color.kt` + `Type.kt` + `Shape.kt` + `Theme.kt` with the winning flavor's set.
- Move the winning `FlavorApp.kt` content into `MainActivity.kt`, replacing the current screens. Wire it to the real `HomeViewModel` / `LocalFinanceRepository` (the parked-branch flavors are mock-only).
- Delete the loser's source set + drop the `design` Gradle dimension.
- Behind-the-screen items per `DESIGN_NOTES.md` that the chosen direction needs (era windowing + calm score for Aviate; signed receipts + per-source pipeline visibility for vwfndr) sequence as their own follow-up sprint after this lands.
**Touchpoints:** branch `design/aviate-vs-vwfndr` (parked at `2dc967e`), `DESIGN_NOTES.md`, every Composable using `MaterialTheme.colorScheme.*` on the current palette.
**Cost:** 1-2 weeks once a direction is picked. Roughly the same as building either flavor on the branch did, plus the data-wiring work that was deferred when those flavors were mocked-only.

---

## Sprint 15 — RC + 1.0 polish *(≈1 week)*

The actual public-launch sprint. By this point we're closing tickets, not opening them.

- **Crash-free rate audit** — final crash-rate sweep on the latest open-beta builds. 99.5% crash-free is the launch bar; anything outstanding gets a tracked-and-blocked entry.
- **Play Store listing assets** — screenshots, feature graphic, copy. Targeted at the chosen REVAMP direction.
- **Support readiness** — `studioxero.biz@gmail.com` feedback channel exists; need a triage / response SLA before public launch.
- **1.0 cut** — `versionName = "1.0.0"`, `versionCode = first-three-digit-number`, signed APK, git tag `v1.0.0`, Play Store track promotion from open beta to production.

---

## Process notes — how this file stays current

When a sprint closes:
1. Move the shipped item out (or delete — git history is the record).
2. Add a CONTEXT.md `vX.Y.Z` entry.
3. If new follow-ons were uncovered, add them here under the right section.

When a new dump arrives:
1. Scan for gate-acceptance + parser-extraction failures.
2. Paste representative bodies into `TransactionalGateTest.kt` and the relevant `NotificationParserParseTest`.
3. If a new parser hole shows up that needs more than a test, add to **Next** or **Slotted** here.

**Rule:** if Claude promises follow-up work in conversation, the promise lives here. Conversation context evaporates; this file doesn't.
