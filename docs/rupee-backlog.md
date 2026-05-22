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
- **Next** — agreed direction, concrete enough to start.
- **Slotted** — captured intent, design pending.
- **Watching** — known gap, not prioritised yet.

Each item should have: *what*, *why it matters*, *touchpoints*, *blocked by*.

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

## Slotted — captured intent, design pending

*Everything in this section is **post-alpha**. Most will happen during
the closed-beta / open-beta arcs. The "post-1.0" markers that earlier
revisions used were aspirational; rewritten as "post-alpha" here
because the realistic phasing puts most of these items years before a
true 1.0 cut.*

### NOTIF-CHANNELS — auto-confirmed silent notif + interactive review notif *(post-alpha)*
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

### S2 — JSON-driven rule engine
**What:** Walnut-style per-package rule table (regex sets indexed by `packageName`) loaded from JSON. `pattern_UID`, `sort_UID`, `obsolete` versioning. OTA-tunable.
**Why:** parser logic currently in Kotlin classes — every rule tweak ships an APK. Rule table lets us update parsers without a release.
**Touchpoints:** new `rule_patterns` table, replaces or sits alongside parser registry.
**Reference:** `docs/axio-takeaways.md` Item 2.

### S2.1 — Chain dedupe via network reference
**What:** Use the v0.13.0 `networkReferenceId` column to chain duplicates across providers (e.g. HDFC bank notif + CRED mirror of the same swipe).
**Why:** today dedupe is fingerprint+5-min-bucket; cross-package duplicates with different merchant cleaning slip through.
**Blocked by:** S2 (rules table needs to emit network refs reliably across providers first).

### S3 — Refund linking
**What:** 5-strategy refund detection (same merchant + amount in 30d, network ref match, etc.) → link refund to original spend.
**Why:** refunds today are either ignored or appear as separate negative entries.
**Reference:** `docs/axio-takeaways.md` Item 5.

### S6 — Dedupe enrichment (additive instead of subtractive)
**What:** Today's dedupe is *subtractive* — finds a duplicate, marks it `DUPLICATE_IGNORED`, throws the data away. Change to *additive* — treat the dup as a second source of evidence that fills gaps on the existing canonical transaction:
- If the original canonical has `merchantName=null` but the dup has `merchantName="Swiggy"` → fill merchant
- If the original has no `maskedDigits` but the dup has `"1234"` → fill digits
- If the original has no `networkReferenceId` but the dup has one → fill ref id
**Why:** the 2026-05-19 dump showed PhonePe pushes (rich merchant, no account digits) and Truecaller-mirrored bank SMS (rich account digits, weak merchant) arriving for the same transaction. Each has data the other doesn't. Today we keep whichever fired first and discard the second. Enrichment captures the best of both.

**Truecaller and Walnut specifically — primary enrichment sources, not dupes to discard.** The 2026-05-22 Nothing-A015 dump showed the textbook case: a single ₹3 Kotak debit fired three notifications — the native Kotak811 push (`₹3.00 sent from XX4129` → has package attribution, timestamp, masked digits, but no payee), three Truecaller SMS mirrors (`Sent Rs.3.00 from Kotak Bank AC X4129` → has explicit bank name and is the body that carries the `UPI Ref XX YYYY` token), and a Walnut SMS-bridge push (`₹3.00 at 8943068824@YESCRED` → has the recipient UPI handle that no other source carries). Today: Kotak becomes one Inbox row, Walnut becomes a second Inbox row, all three Truecaller mirrors are `DUPLICATE_IGNORED`. Under S6 these merge into a single canonical row carrying the union of the data — Kotak's package/digits + Truecaller's UPI ref + Walnut's recipient handle. Two specific notes worth pinning before implementation:
- Truecaller's `subText` field carries the issuer name in plain text (`SMS from Kotak Mahindra Bank` in this dump) — useful for cross-validating the brand attribution we got from the native push's package name.
- Walnut posts the merchant/handle that the native bank push never includes. Treat Walnut and Truecaller as complementary, not redundant: Truecaller mirrors the bank's SMS verbatim; Walnut parses it locally and emits a different shape.

**Three structural decisions before this ships:**

1. **Field-by-field merge policy.** Per field: "first non-null wins" vs "higher-confidence parser wins" vs "newer wins." Probably different per field. `merchantName` should prefer the brand-aware parser's value. `maskedDigits` should prefer first-non-null (they don't change across sources). `networkReferenceId` is similar.
2. **Respect user edits.** If the user manually changed the merchant to "Coffee shop" after confirming the inbox row, a later mirror with a generic merchant must NOT overwrite. Two options: a per-field `userEditedAt` timestamp, or treat any `CONFIRMED` canonical's user-facing fields as locked. The second is simpler and probably right.
3. **Provenance trail.** For debugging "why did this transaction's merchant change," we'd want either a `merchantSource: parserKey` lookup field on the canonical row, or an audit log of field-level updates. Without provenance, enrichment becomes silent mutation.

**Touchpoints:** `NotificationDedupeEngine.detect` (currently returns a `DedupeResult` with `duplicateCandidate`/`duplicateCanonicalTransaction`); `NotificationSignalNormalizer.normalizeLocked` (currently routes to `DUPLICATE_IGNORED` when `isDuplicate=true`); new `CanonicalTransactionEnricher` that does the field merge; possibly a small column or audit log addition.
**Why family:** S2.1 (chain dedupe via network reference), S3 (refund linking), and S6 (enrichment) all share the underlying pattern "this notification is *related to* an existing record, not a fresh event." Worth shipping together as a "cross-stream record linking" sprint if/when prioritised together.
**Blocked by:** ideally T1 (in-memory Room tests) — enrichment is a state-mutation feature that's hard to ship safely without DB-level regression coverage.
**Source:** user-requested via 2026-05-19 conversation; not yet captured against an Axio takeaway.

### EMI-AUTO — EMI auto-detection from notifications *(post-alpha)*
**What:** Today `EmiPlanEntity` is populated only by manual entry through the Cards & EMIs screen. EMI debit notifications parse as SPEND. Extend the EMI parser to upsert into `emi_plans` directly when amount + merchant + due-date all extract confidently — same shape as the `applyBillDueToCard` side-effect that's already wired in `NotificationSignalNormalizer`.
**Why:** quality upgrade — manual EMI entry already covers the alpha feature checklist. Auto-detection is a polish item that's better landed after closed-beta surfaces real-user EMI corpora to validate confidence thresholds against.
**Touchpoints:** `EmiNotificationParser` (already exists, extracts amount + merchantishly + dueDateIso), new `applyEmiToPlan(...)` in `NotificationSignalNormalizer` mirroring `applyBillDueToCard`. New repo method + `EmiPlanDao.upsertPlan`.
**Blocked by:** nothing technical. Deferred to post-alpha because EMIs are commitments — the right confidence threshold should be tuned against real-user data, not synthetic dumps.
**Open design Q:** route confirmed EMIs to Inbox first vs auto-add to `emi_plans`? Recommend Inbox (EMIs are commitments — user should verify before they show up on Home's upcoming-dues strip).

### LEDGER-IMPORT — Reverse of EXPORT-UI (read CSV/JSON back into the ledger) *(post-alpha)*
**What:** Sprint 1's `EXPORT-UI` is one-way only — CSV / JSON come out, nothing goes back in. Add a Settings → Import path that reads either format and inserts rows into `canonical_transactions`. Schema validation, foreign-key remap (merchant/category/account *names* → IDs, auto-creating if missing), conflict resolution against `dedupeFingerprint`, atomic Room transaction so a malformed file doesn't half-write.
**Why:** new-phone restore is the only currently-impossible workflow — export buys you a backup file but you can't get it back into the app. Power-user bulk-edit (Excel round-trip) is the secondary use case.
**Touchpoints:** new `diagnostics/LedgerImporter.kt` (mirror of `LedgerExporter` but with `parseCsv`/`parseJson` + a `Result<ImportSummary>` return shape), new `LocalFinanceRepository.importLedgerSnapshot(...)` that wraps the insert in `withTransaction`, new Settings card. UI needs a confirmation step ("This will add 412 rows. 17 look like duplicates of existing transactions — skip / overwrite / both?") because there's no undo from the user side.
**Cost:** ~3–4× export, almost all of it in conflict-resolution + foreign-key remap logic. The file parsing is straightforward; making "I exported, edited the merchant column, re-imported" actually merge into the existing rows is the hard part.
**Open design Q's before this ships:**
- Conflict policy: skip / overwrite / keep-both / per-row prompt. Recommend skip-on-dedupeFingerprint-match by default with an "overwrite duplicates" checkbox.
- Schema versioning: the export's `schemaVersion: 1` lets the importer reject future-format files cleanly. Need a clear error when v1 sees v2.
- Merchant/category creation: auto-create unknown names, or reject the import until the user pre-creates them? Auto-create is friendlier but pollutes the merchant trust corpus.
**Blocked by:** nothing technical. Deferred to post-alpha because export already covers the "I want my data outside the app" trust requirement, and import is meaningful only after new-phone-restore becomes a real tester request during closed beta.

### REVAMP — Full design overhaul (Aviate vs vwfndr) *(post-alpha)*
**What:** The Sprint 0 / Sprint 1 / Sprint 2 work hardens the wallet on its current warm Clay / Sage / Paper Material 3 theme. **After alpha ships and survives some closed-beta soak**, pick one of the two design directions explored on the `design/aviate-vs-vwfndr` branch and rebuild the visual layer top-to-bottom in that language. This is a style-beat-sized investment, not a polish pass — closer in shape to "Spotify's next-version redesign" than to spacing tweaks.
**Why post-alpha:** the exploration produced two fully-mocked APKs (`Rupee · Calm` aviate flavor, `RPEE™` vwfndr flavor) that read as completely different products. Picking one is a *positioning* decision (emotional / shareable vs instrument / signed-receipt), not a code task — and it shouldn't gate getting a working wallet into testers' hands. Live with the current theme through alpha and at least early closed beta, then revamp.
**What the revamp would involve:**
- Pick the direction (decision, not code). See `DESIGN_NOTES.md` for the trade-off in plain English.
- Replace `ui/theme/Color.kt` + `Type.kt` + `Shape.kt` + `Theme.kt` with the winning flavor's set.
- Move the winning `FlavorApp.kt` content into `MainActivity.kt`, replacing the current screens. Wire it to the real `HomeViewModel` / `LocalFinanceRepository` instead of the parked-branch mocks.
- Delete the loser's source set + drop the `design` Gradle dimension.
- Behind-the-screen items from `DESIGN_NOTES.md` that the chosen direction needs (era windowing + calm score for Aviate; signed receipts + per-source pipeline visibility for vwfndr) get sequenced as their own follow-up sprint.
**Touchpoints:** branch `design/aviate-vs-vwfndr` (parked at `2dc967e`), `DESIGN_NOTES.md`, every Composable that currently uses `MaterialTheme.colorScheme.*` on the current palette.
**Cost:** 1-2 weeks once a direction is picked. Roughly the same as building either flavor on the branch did, plus the data-wiring work that was deferred when those flavors were mocked-only.
**Blocked by:** alpha shipping cleanly + at least one closed-beta cycle's worth of real-tester usage. Then a positioning call.

### RECAP-PERSIST — Persisted monthly Recap snapshots *(post-alpha; deeper-recap design is REVAMP-sensitive)*
**What:** Recap is computed-on-read today. PRD describes a "story-like highlights" surface (biggest category, most expensive day, variance vs last month, fixed vs discretionary). Persist a `MonthlyRecap` snapshot row per closed month so the surface loads instantly and we can build "share my month" later. Also unblocks the **Aviate Wrapped-style shareable artifact** if that direction wins.
**Why:** Recap is too expensive to recompute on every open as transaction count grows; also blocks any cross-month comparison that requires a stable historical snapshot. Not gating alpha because the live-compute version is acceptable at current data volumes.
**Touchpoints:** new `MonthlyRecapEntity` + DAO, scheduled job on month-close (WorkManager already exists in tree), `RecapViewModel` reads from DAO with fallback to live compute.
**Reference:** PRD §14, §21; the planned `monthly_recaps` table in *Schema entities planned but not yet defined* below.

---

### S4-5 — Adaptive confidence from user behaviour (Item 13)
**What:** `ingestion_signal_stats` table keyed `(package, parserKey, cleanedMerchant)`. Confirm counts up, dismiss counts down. Bootstrap mode for first 14 days lowers MEDIUM threshold from 0.6 → 0.5 to be more liberal at onboarding. Auto-promote to `MerchantTrustRule` after 3 confirms in a 7+ day window.
**Why:** the app learns the user's actual transaction patterns instead of relying on hardcoded confidence floors. User asked for this explicitly.
**Touchpoints:** new table + DAO, decision engine reads stats, UI shows "auto-trusted (3 confirms)" badge.
**Reference:** `docs/axio-takeaways.md` Item 13.

---

## Watching — known gaps, no sprint yet

### Code-quality & reliability — surfaced in the 2026-05-23 audit
Filtered findings from a deep code audit (false positives dropped after
spot-verification against the actual lines). None are alpha blockers
on their own; collectively they're the work the closed-beta arc will
need to land before open beta.

- **CAST-SAFETY — drop `Array<Any?>` + `@Suppress("UNCHECKED_CAST")` from VM flow combinators.** `HomeViewModel.dashboardData` and `viewSelection` (~lines 305–370) and `SettingsViewModel`'s combine chain pack heterogeneous flows into `arrayOf<Any?>()` then unpack each slot with `as` casts. A typo in the array indices would only surface at runtime in the UI with a `ClassCastException`. The fix is mechanical: replace with intermediate typed data classes so the compiler enforces shape. ~1 day. **Sprint-sized once it's prioritised; in Watching for now because nothing has actually misfired.**

- **MONEY-MATH-LEGACY — `GenericNotificationParser.extractAmountMinor` still uses `Double * 100 → Long`.** [GenericNotificationParser.kt:68](android/app/src/main/java/com/zegrt/rupee/ingestion/GenericNotificationParser.kt#L68) is the last hold-out — every other parser routes through `NotificationParsingUtils.extractAmountMinor` which uses `BigDecimal.movePointRight(2).toLong()` (correct). Edge amounts like `₹19.99` can round to `1999.99` * 100 = `199999L` instead of `199999L` — usually fine, sometimes off by one. **One-off PR. ~10 min. Backport the BigDecimal pattern.**

- **TRUST-WRITE-RACE — `setMerchantTrust` reads-then-conditionally-writes.** [LocalFinanceRepository.kt:641-651](android/app/src/main/java/com/zegrt/rupee/data/repository/LocalFinanceRepository.kt#L641-L651) reads `getRulesForUser`, searches for a match in memory, then either upserts or deletes. Two rapid taps from the new TRUST-FROM-TXN toggle could race: tap 1 reads "no rule", tap 2 reads "no rule", both insert → unique-constraint violation, OR tap 1 deletes, tap 2 tries to delete the same row → no-op but lost intent. Fix: wrap the lookup+write pair in `database.withTransaction { ... }`, or change `MerchantTrustRuleDao` to expose an idempotent upsert-by-pattern. **~1 hour PR.**

- **DATE-PARSE-LOGGING — three `runCatching { … }.getOrNull()` sites swallow ISO-8601 parse failures.** [HomeViewModel.kt formatOccurredAt fallback](android/app/src/main/java/com/zegrt/rupee/home/HomeViewModel.kt), `RecurringDetectionEngine`, and `DuesAlertManager` all parse `occurredAt` / due dates with `runCatching` and fall back to `.take(10)` or null on failure. If the DB ever ends up with a malformed timestamp (migration bug, third-party writer), the UI silently drops the row from upcoming-dues and recurring detection with zero error surface. Fix: add `Log.w("Rupee", ...)` before the `getOrNull()` so the parse failure shows up in logcat / a future health surface. **~1 hour PR.**

- **MIGRATION-SKIP-TEST — no instrumented test exercises the full v8→v11 ladder in one go.** Room applies migrations sequentially, so any gap in `MIGRATION_8_9` / `9_10` / `10_11` is silent unless a test asserts the v8 fixture survives all three. We have per-migration tests via `MigrationTestHelper`; we don't have a chained "open a v8 DB, end up at v11, verify every column exists" test. **~½ day to add. Catches future migration drift.**

- **REPO-VM-TEST-COVERAGE — load-bearing repository methods + the `HomeViewModel` flow combinators have no unit tests.** `LocalFinanceRepository.confirmInboxItem` / `setMerchantTrust` / `deleteTransaction` / the export snapshot path are only covered by integration via `DumpReplayTest`. `HomeViewModel`'s 8-flow combine is only exercised by running the UI. The `Array<Any?>` casts above are dangerous *specifically because* nothing tests them. Closed-beta blocker if we want to ship to non-internal testers safely. **~2-3 days, prioritise repo confirm/merge/delete first.**

### Architecture — post-alpha, depends on rule engine
- **PATTERN-TELEM — Pattern telemetry / OTA-readiness.** Per [axio-competitor-analysis.md §3.7](axio-competitor-analysis.md), once **S2** ships JSON rules with `pattern_UID`, we'd want to log which patterns fire in production (counts, last-fired timestamp) so the rule corpus can be tuned from real data. Useless without S2; trivial to add once S2 exists.
- **MISSED-TXN — Missed-transaction detector via balance reconciliation.** [axio-takeaways.md Item 8](axio-takeaways.md). Walnut catches the "you said balance is X but txns sum to Y" gap. We currently track `currentBalanceMinor` on `AccountEntity` but **don't extract** `balanceAfterMinor` from notifications and **don't reconcile** the running sum against it. Needs (a) parsers emitting `balanceAfterMinor` reliably across providers, (b) a new column on `ParsedSignalEntity` (or `CanonicalTransactionEntity`) to store it, (c) a reconcile job that diff'ses transactions-sum vs latest reported balance and raises a "missed something" surface. Multi-sprint; tied to S2 for breadth of provider coverage. Low priority.

### From `docs/notification-ingestion-deep-dive.md` §9
- **§9.3 `extractedJson TEXT NULL` on `RawCaptureEventEntity`.** Persist structured per-Bundle-field breakdown so we can re-parse old events when a future rule-driven parser (S2) ships. The flat `combinedBody` is already stored as `body`; what's missing is the per-field separation (title vs subText vs textLines) for parsers that want to apply field-priority rules. ~1-2 KB/notif storage cost. Not urgent — `combinedBody` is enough for re-parsing in 95% of cases today.
- ~~**§9.9 Post-ship parse-rate counter.**~~ Promoted to **T3 — Ingestion health surface in the Debug screen** — shipped in v0.14.2 (PR #51, 2026-05-21).

### From `docs/rupee-settings-debug.md` §6 (deferred-by-design)
- **Merge with existing transaction (UI picker).** Repository contract `mergeInboxIntoTransaction(inboxItemId, targetTransactionId)` exists and the merge column landed in v0.14.0; what's missing is a full search-then-select picker for "find an existing transaction to merge this Inbox row into." The two-step soft-confirm in the existing Inbox is the workaround.
- ~~**Recategorize on Transactions detail sheet.**~~ Shipped — `CategoryDropdown` is in the `TransactionDetailSheet` ([MainActivity.kt:2234](android/app/src/main/java/com/zegrt/rupee/MainActivity.kt#L2234)).
- ~~**Dedicated PhonePe / Paytm parsers**~~ — shipped earlier; PhonePe and Paytm have dedicated parsers in `ingestion/`.
- **Custom bucket progress cards on Home.** `TransactionBucketAssignmentEntity` *is* defined (audited 2026-05-23) but unwired — no DAO, no observer, no Home composable. Needs the DAO + observer chain into `HomeViewModel.DashboardData`, plus per-bucket budget seeding through `BudgetsScreen`.

### Schema entities planned but not yet defined
*(Re-audited 2026-05-23. Six tables remain undefined; one previously
listed entry — `transaction_bucket_assignments` — is now defined as
`TransactionBucketAssignmentEntity` but is unwired, see Custom bucket
progress cards above.)* From `CONTEXT.md` "Known Gaps":
- `canonical_transaction_source_links` — multi-source provenance audit trail. Pairs with **S6**.
- `dedupe_groups`, `dedupe_group_members` — current code uses flat `dedupeFingerprint` field on `CanonicalTransactionEntity` (pragmatic shortcut, not the long-term shape). Pairs with **S2.1** + **S6**.
- `alert_rules`, `alert_events` — current `DuesAlertManager` uses SharedPrefs dedupe, not these tables.
- `monthly_recaps` — current Recap is computed on read (`RecapViewModel`). See **RECAP-PERSIST** in Slotted.
- `emi_transaction_links` — link auto-detected EMI debits back to their plan. Pairs with **EMI-AUTO**.
- `budget_category_assignments` — explicit per-category-per-budget link table (today budgets reference categories implicitly).

### SMS pipeline
- TRAI `-T`/`-S`/`-P`/`-G` sender-suffix as free first-stage signal (May 2025 rule).
- 6-alpha (transactional/service) vs 6-numeric (promo) headers.
- Reference: research report stashed in conversation context; PennyWise AI repo for architecture lessons.

### Parser corpus expansion
- SBI YONO real-body shapes — guessed in deep-dive §3, never confirmed against a dump.
- Federal Bank, Yes Bank — same.
- Jupiter / Fi / Niyo — newer fintechs, possible `RemoteViews` usage.
- Foreign-currency transactions — many issuers SMS-only.

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
