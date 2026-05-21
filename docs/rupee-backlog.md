# Rupee — Deferred Work & Sprint Backlog

**Purpose:** durable, in-repo backlog. Anything Claude promised to do "next sprint" or "later" lives here, not just in conversation context. This file is the single source of truth for what's deferred — if it's not here, it doesn't exist.

**Last updated:** 2026-05-21 (after the design-exploration branch was parked
and a docs-wide post-MVP scan surfaced items not yet captured)

---

## How to read this

- **In flight** — currently being shipped or actively designed.
- **Next** — agreed direction, concrete enough to start.
- **Slotted** — captured intent, design pending.
- **Watching** — known gap, not prioritised yet.

Each item should have: *what*, *why it matters*, *touchpoints*, *blocked by*.

---

## In flight

- **Design exploration — parked on branch** *(2026-05-21)*. Branch
  `design/aviate-vs-vwfndr` pushed to origin, **not merged**. Two product
  flavors (`aviate` = calm/Wrapped, `vwfndr` = instrument/signed-receipt)
  ship as side-by-side installable APKs from one repo via Gradle product
  flavors. Real wallet logic (Room / ViewModels / ingestion) is untouched
  and dormant during the evaluation. Decision is pending. Picking a
  direction blocks **POLISH-1** and the **RECAP-PERSIST** / **INBOX-WHY**
  items below. See `DESIGN_NOTES.md` (root) + `design-research/DESIGN_NOTES.md`
  for what each direction does, what would change behind the screen, and
  trade-offs.

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

## Next — pick one of these to start

### EXT-COMBINED — Notification extractor `combinedBody` refactor
**What:** Today `NotificationExtractor` reads `Notification.extras.getString(EXTRA_TEXT)` and ignores `EXTRA_TITLE`, `EXTRA_SUB_TEXT`, `EXTRA_BIG_TEXT`, and the various inbox-style extras. Some senders (Kotak811, certain SBI mirrors, ICICI cross-app forwards) place the amount or merchant in the *title* or *subText* and leave `text` empty or generic. The notification arrives, the gate sees no body, and the transaction is silently lost. Fix: extract every present Bundle extra into a single canonical `combinedBody` string before any parser/gate logic runs.
**Why:** the deep-dive estimates **~60% more notification coverage** with this one change. It is the single biggest "we are quietly losing transactions" item in the repo today. Bigger user impact than EMI-AUTO.
**Touchpoints:** `ingestion/NotificationExtractor.kt`, `ingestion/ExtractedNotification.kt` (add `combinedBody` field), all 9 parsers (`canParse` + `parse` switch from `body` to `combinedBody`), `NotificationDecisionEngine` (gate runs on combined), tests under `ingestion/NotificationExtractorTest.kt` + every parser test.
**Reference:** [notification-ingestion-deep-dive.md §5–§9](notification-ingestion-deep-dive.md).
**Blocked by:** nothing. ~3–4 days. **T1/T2** are already shipped, so regression coverage is automatic — every dump in `dumps/*.jsonl` replays through the new path on every PR.

### EMI-AUTO — EMI auto-detection from notifications
**What:** Today `EmiPlanEntity` is populated only by manual entry through the Cards & EMIs screen. EMI debit notifications parse as SPEND. Extend the EMI parser to upsert into `emi_plans` directly when amount + merchant + due-date all extract confidently — same shape as the BILL_DUE → credit_cards side-effect that's already wired.
**Why:** the only "Track basic EMI obligations" gap on the MVP checklist in [rupee-roadmap.md §3](rupee-roadmap.md). Everything else on the MVP list ships today.
**Touchpoints:** `EmiNotificationParser` (already exists, extracts amount + merchantishly + dueDateIso), `NotificationSignalNormalizer.applyBillDueToCard` is the structural mirror — copy the shape for `applyEmiToPlan`. New repo method + DAO upsert.
**Blocked by:** nothing. ~1-2 days.
**Open design Q:** route confirmed EMIs to Inbox first vs auto-add to `emi_plans`? Recommend Inbox (EMIs are commitments — user should verify before they show up on Home's upcoming-dues strip).

### SEED-SERVICE — proper seeding service (replace hardcoded IDs)
**What:** `LocalFinanceRepository.completeInitialSetup` hardcodes seed IDs (`account-bank-1`, `card-1`, `account-cash`) and a single 2026-03 budget period. Works for one tester; breaks for a clean install in any other month. Replace with UUID generation + current-month budget.
**Why:** release blocker (any install outside March 2026 silently has no budget seeded for the current month).
**Touchpoints:** `LocalFinanceRepository.completeInitialSetup`, possibly `OnboardingViewModel` if budget period needs surfacing.
**Blocked by:** nothing. ~half-day.

### POLISH-1 — Home / Inbox / Settings polish pass
**What:** Designer-driven visual review pass on the three most-trafficked surfaces. Specific items emerge from walkthrough; common rough spots based on past testing:
- spacing inconsistencies (margins, padding) between Home / Inbox / Settings
- typography hierarchy on Home (greeting vs month vs budget number)
- empty states (Inbox empty, no transactions yet, no income captured)
- loading states on first cold start before flows hydrate
- snackbar / toast styling
- Recap surface visual density
**Why:** pre-1.0 product polish; the v0.13.8 onboarding pass cleaned Welcome / Permissions / Profile screens but Home / Inbox / Settings haven't had a focused visual review since.
**Touchpoints:** mostly `MainActivity.kt` composables. Could prompt an L5-flavoured decomposition pass as a side effect.
**Blocked by:** **the design-direction decision** on the parked `design/aviate-vs-vwfndr` branch. Most polish work is design-neutral (spacing, empty/loading states) but the typography hierarchy, surface tinting, motion vocabulary, and Recap visual density all flow from picking Aviate vs vwfndr. Do **TRUSTED-UI**, **EXPORT-UI**, **TRUST-FROM-TXN**, **EXCL-FROM-SPEND** first — they're pure utility and flavor-agnostic. ~½ to 2 days for the design-neutral subset; full pass needs the direction call first.

### TRUSTED-UI — Trusted Merchants management screen
**What:** `MerchantTrustRuleEntity` and `MerchantTrustRuleDao` have shipped since v0.13.x — the data model + insert path are done. What's missing is a dedicated screen to list / edit / delete trust rules. Currently the only way to manage trust rules is via the Settings deep-link modal that's stub-implemented; spec calls for a full list view with per-merchant scope, "always confirm" toggle, and last-fired timestamp.
**Why:** users have no visibility into which auto-confirm rules they've accumulated. A user adding "Always trust Swiggy" can never see or revoke it without dev tooling. Pre-1.0 trust requirement.
**Touchpoints:** `settings/TrustRulesScreen.kt` (currently a placeholder), `SettingsViewModel`, possibly a `MerchantTrustRuleDao.observeAll()` flow.
**Reference:** [rupee-android-screens.md §20](rupee-android-screens.md).
**Blocked by:** nothing. ~1–2 days. Pure UI on existing data.

### EXPORT-UI — Export to CSV / JSON
**What:** PRD §14 lists data-export as an MVP capability but no UI affordance exists today. Surface a Settings entry that lets the user save a date-windowed export of `canonical_transactions` (+ joined merchant/category) as CSV or JSON to local storage, then offer the share-sheet. JSON export should also include `raw_capture_events` for the same window so power users can debug ingestion themselves.
**Why:** MVP gap. Also unblocks "I want to see my data outside the app" trust requests and gives us a pre-baked diagnostic payload when users report bugs.
**Touchpoints:** new `diagnostics/Exporter.kt` (CSV + JSON formatters — the JSON path can lean on `DumpOutcomeExporter` plumbing that already exists for diagnostics), Settings entry, `FileProvider` share intent (already declared in manifest).
**Blocked by:** nothing. ~1–2 days.

### TRUST-FROM-TXN — "Always trust this merchant" from txn detail
**What:** Today adding a trust rule requires diving into Settings. The transaction-detail sheet on the Transactions tab has a Merchant field and a Notes field — add a "Always auto-confirm from {merchant}" toggle row that writes a `MerchantTrustRuleEntity` on enable and deletes it on disable. Same affordance on the Inbox confirmation surface.
**Why:** the friction of opening Settings → Trust Rules → Add → pick merchant is enough that users never make a trust rule even when they obviously want one. This is the "Walnut-style fast-path" referenced in [axio-takeaways.md Item 6](axio-takeaways.md).
**Touchpoints:** Transactions detail sheet in `MainActivity.kt`, Inbox review UI, `MerchantTrustRuleDao.upsert/delete`.
**Blocked by:** nothing. ~½ day. Pairs nicely with **TRUSTED-UI** in the same PR.

### EXCL-FROM-SPEND — Account "exclude from spend totals" toggle
**What:** `AccountEntity.excludeFromSpendTotals` and `CreditCardEntity` equivalents already exist as columns but the toggle is buried in the Cards & EMIs modal and absent for bank/cash accounts. Surface a per-account toggle in the same Settings pass as TRUSTED-UI.
**Why:** users with savings-account drains (loan EMIs auto-debited from a different account) want to exclude that account from "May spend" totals without losing the rows. Currently they have to manually mark each transaction.
**Touchpoints:** `settings/SettingsScreen.kt` accounts modal, `AccountDao.setExcludeFromSpend(...)`.
**Blocked by:** nothing. ~½ day.

### CRED-ICICI-AUDIT — Audit hardcoded SPEND in CRED / ICICI parsers
**What:** Per [Recently shipped — gravedigging audit](#) notes, "CRED/ICICI parsers still hardcode SPEND for some paths — fine for card alerts, audit if a real card-credit notification slips through." Concrete audit: enumerate every `parse()` return in `CredNotificationParser` and `IciciNotificationParser`, check each against a real-world card-credit dump (refund, reversal, statement credit, EMI conversion reversal), patch any that miscategorise.
**Why:** silent miscategorisation of a credit-side notification as SPEND inflates the user's spend total and confuses Insights. Hard to catch by inspection because the offending notification is rare.
**Touchpoints:** `ingestion/CredNotificationParser.kt`, `ingestion/IciciNotificationParser.kt`, regression cases in `NotificationParserParseTest`.
**Blocked by:** nothing. ~½ day audit + however long the fixes take per parser hole.

### L4 — remove `fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4)`
**What:** Drop the destructive-migration fallback in `RupeeApplication.kt:25`. Anyone on v1-v4 has long passed the upgrade window; this is just data-loss-risk for any real release.
**Why:** release blocker. Captured in [gravedigging-2026-05-18.md §L4](gravedigging-2026-05-18.md) as "deferred indefinitely until release prep" — promoting here so it doesn't get forgotten.
**Touchpoints:** one line in `RupeeApplication.kt`. ~5 minutes.
**Blocked by:** "we're committing to never supporting v1-v4 upgrades again" — true today.

### S1.3 — Evidence-stacked confidence scoring
**What:** Replace every parser's hardcoded confidence brackets (`0.62 / 0.55 / 0.15` etc.) with an additive evidence tally. Each parser contributes points per signal found (canonical verb +3, masked digits +2, UPI ref +2, named merchant +2, amount-only +1, soft anti-signals −5). Single global threshold decides MEDIUM vs HIGH.
**Why:** today two very different bodies score the same `0.62` — one with strong evidence, one with weak. Tuning is per-parser edits in nine files. With a tally, tuning is one knob.
**Touchpoints:** `NotificationParseResult.parseConfidence`, all 9 parsers in `ingestion/`, `NotificationDecisionEngineTest`.
**Blocked by:** nothing. Can do one parser at a time — start with `GenericNotificationParser` + `GenericUpiNotificationParser` (the two with the loosest current scoring).

---

## Slotted — captured intent, design pending

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

**Three structural decisions before this ships:**

1. **Field-by-field merge policy.** Per field: "first non-null wins" vs "higher-confidence parser wins" vs "newer wins." Probably different per field. `merchantName` should prefer the brand-aware parser's value. `maskedDigits` should prefer first-non-null (they don't change across sources). `networkReferenceId` is similar.
2. **Respect user edits.** If the user manually changed the merchant to "Coffee shop" after confirming the inbox row, a later mirror with a generic merchant must NOT overwrite. Two options: a per-field `userEditedAt` timestamp, or treat any `CONFIRMED` canonical's user-facing fields as locked. The second is simpler and probably right.
3. **Provenance trail.** For debugging "why did this transaction's merchant change," we'd want either a `merchantSource: parserKey` lookup field on the canonical row, or an audit log of field-level updates. Without provenance, enrichment becomes silent mutation.

**Touchpoints:** `NotificationDedupeEngine.detect` (currently returns a `DedupeResult` with `duplicateCandidate`/`duplicateCanonicalTransaction`); `NotificationSignalNormalizer.normalizeLocked` (currently routes to `DUPLICATE_IGNORED` when `isDuplicate=true`); new `CanonicalTransactionEnricher` that does the field merge; possibly a small column or audit log addition.
**Why family:** S2.1 (chain dedupe via network reference), S3 (refund linking), and S6 (enrichment) all share the underlying pattern "this notification is *related to* an existing record, not a fresh event." Worth shipping together as a "cross-stream record linking" sprint if/when prioritised together.
**Blocked by:** ideally T1 (in-memory Room tests) — enrichment is a state-mutation feature that's hard to ship safely without DB-level regression coverage.
**Source:** user-requested via 2026-05-19 conversation; not yet captured against an Axio takeaway.

### UI/UX family — design-direction-dependent

All four items below are blocked by the **design-direction decision** on
the parked `design/aviate-vs-vwfndr` branch. Each direction implies a
different answer:
- *Aviate* makes Recap a destination, leans into narrative copy, wants
  identity-coded badges.
- *vwfndr* de-emphasises Recap, exposes confidence as a numeric readout
  on every row, treats loading states as instrument warm-up.

Pick a direction before sprinting on these.

#### INBOX-WHY — "Why was this in Inbox?" badges
**What:** Every Inbox item today just says "needs review." Surface the actual reason as a small chip on the row — `LOW CONFIDENCE`, `NO MERCHANT`, `AMOUNT ONLY`, `NEW SENDER`, `MASKED DIGITS MISSING`. Read from `parsed_signals.parserKey` + the existing confidence tier + presence/absence of merchant/maskedDigits fields.
**Why:** PRD calls for "every inferred transaction should be explainable." Today the user has no signal for *why* the parser couldn't fully resolve — they just see an unreviewed row and have to guess.
**Touchpoints:** Inbox tab in `MainActivity.kt`, `InboxItemWithCandidate` already exposes the joined fields. No data change.
**Reference:** [rupee-android-screens.md §21](rupee-android-screens.md).

#### MANUAL-FAST — Manual entry speed pass (20-second goal)
**What:** Spec target for manual entry is 20 seconds. Levers: (a) category autocomplete from the user's recent 30-day picks, (b) "similar to last Swiggy / Uber" suggestion when amount + merchant match a recent pattern, (c) swipe-to-account pre-selection so card vs cash is one gesture not a dropdown.
**Why:** manual entry is the fallback when ingestion misses something — friction here is *the* failure mode for trust.
**Touchpoints:** the manual-entry sheet in `MainActivity.kt`, possibly a new `RecentPicksRepository` (small in-memory cache over `CanonicalTransactionDao`).
**Reference:** [rupee-android-screens.md §12](rupee-android-screens.md).

#### RECAP-PERSIST — Persisted monthly Recap snapshots
**What:** Recap is computed-on-read today. PRD describes a "story-like highlights" surface (biggest category, most expensive day, variance vs last month, fixed vs discretionary). Persist a `MonthlyRecap` snapshot row per closed month so the surface loads instantly and we can build "share my month" later. Also unblocks the **Aviate Wrapped-style shareable artifact** if that direction wins.
**Why:** Recap is too expensive to recompute on every open as transaction count grows; also blocks any cross-month comparison that requires a stable historical snapshot.
**Touchpoints:** new `MonthlyRecapEntity` + DAO, scheduled job on month-close (WorkManager already exists in tree), `RecapViewModel` reads from DAO with fallback to live compute.
**Reference:** PRD §14, §21; CONTEXT.md "Known Gaps" already lists `monthly_recaps` as defined-but-unimplemented.

#### COLDSTART — Cold-start hydration / loading states
**What:** First open of Home / Inbox / Transactions flashes empty before flows hydrate. Add a quick skeleton (Aviate: soft shimmer; vwfndr: viewfinder warm-up mark) and only swap in real content once the flow has emitted at least once. Pair with empty states (no income captured yet, etc.) — both surfaces need the same "we're alive but not ready" affordance.
**Why:** the empty-flash reads as "broken" to first-time users. Fast fix, high perceptual value.
**Touchpoints:** the three tab composables in `MainActivity.kt`, plus an empty-state composable that swaps in when the flow emits an empty list.

---

### S4-5 — Adaptive confidence from user behaviour (Item 13)
**What:** `ingestion_signal_stats` table keyed `(package, parserKey, cleanedMerchant)`. Confirm counts up, dismiss counts down. Bootstrap mode for first 14 days lowers MEDIUM threshold from 0.6 → 0.5 to be more liberal at onboarding. Auto-promote to `MerchantTrustRule` after 3 confirms in a 7+ day window.
**Why:** the app learns the user's actual transaction patterns instead of relying on hardcoded confidence floors. User asked for this explicitly.
**Touchpoints:** new table + DAO, decision engine reads stats, UI shows "auto-trusted (3 confirms)" badge.
**Reference:** `docs/axio-takeaways.md` Item 13.

---

## Watching — known gaps, no sprint yet

### UI/UX — post-MVP, not blocked on design direction
- **CAL-INTER — Calendar interactions.** Spec ([rupee-android-screens.md §18.2–§18.3](rupee-android-screens.md)) calls for tap-day → show txns for that day, swipe-month → next/previous, tap-due-item → detail screen. The heatmap renders; the gestures don't. Half a day.
- **PERM-REOPEN — Permission re-enable shortcut.** [rupee-prd.md §21.1](rupee-prd.md) describes an in-app shortcut for users who denied notification access and later want to re-enable. Today they have to dig through system settings. `PermissionStateChecker.kt` knows the state; surface a "Notifications denied — turn on" affordance on the Home top strip or Settings when the check returns false. ~½ day.

### Architecture — post-MVP, depends on rule engine
- **PATTERN-TELEM — Pattern telemetry / OTA-readiness.** Per [axio-competitor-analysis.md §3.7](axio-competitor-analysis.md), once **S2** ships JSON rules with `pattern_UID`, we'd want to log which patterns fire in production (counts, last-fired timestamp) so the rule corpus can be tuned from real data. Useless without S2; trivial to add once S2 exists.
- **MISSED-TXN — Missed-transaction detector via balance reconciliation.** [axio-takeaways.md Item 8](axio-takeaways.md). `ParsedSignalEntity.balanceAfterMinor` column already exists but no reconcile logic. Walnut catches the "you said balance is X but txns sum to Y" gap. Needs reliable balance extraction across providers first (multi-sprint effort tied to S2). Sprint 4+ with low priority.

### From `docs/notification-ingestion-deep-dive.md` §9
- **§9.3 `extractedJson TEXT NULL` on `RawCaptureEventEntity`.** Persist structured Bundle fields so we can re-parse old events when parser v2 ships. ~1-2 KB/notif storage cost. Not urgent — current `body` field has the combinedBody which is enough for re-parsing 95% of cases. *(2026-05-21: less urgent once **EXT-COMBINED** lands, since `combinedBody` will already cover the title/subText fields that today get dropped.)*
- ~~**§9.9 Post-ship parse-rate counter.**~~ Promoted to **T3 — Ingestion health surface in the Debug screen** — shipped in v0.14.2 (PR #51, 2026-05-21).

### From `docs/rupee-settings-debug.md` §6 (deferred-by-design)
- **Merge with existing transaction.** Repository contract: `mergeInboxIntoTransaction(inboxItemId, targetTransactionId)`. Two-step soft-confirm currently exists in UI but no full search-then-select picker. (M1's `mergedFromExistingCanonicalId` column landed v0.14.0 — surfaces the merge in analytics, but the UI picker remains TODO.)
- **Recategorize on Transactions detail sheet.** Edit form has Merchant + Notes; add CategoryDropdown row mirroring Inbox review.
- ~~**Dedicated PhonePe / Paytm parsers**~~ — shipped earlier; PhonePe and Paytm have dedicated parsers in `ingestion/`.
- **Custom bucket progress cards on Home.** Needs per-bucket budgets seeded + `transaction_bucket_assignments` DAO.

### Schema entities defined but unimplemented
From `CONTEXT.md` "Known Gaps":
- `canonical_transaction_source_links`
- `dedupe_groups`, `dedupe_group_members` (current code uses flat `dedupeFingerprint` field — pragmatic shortcut, not the long-term shape)
- `alert_rules`, `alert_events` (current `DuesAlertManager` uses SharedPrefs dedupe, not these tables)
- `monthly_recaps` (current Recap is computed on read)
- `emi_transaction_links`
- `budget_category_assignments`

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
