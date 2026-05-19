# Rupee — Deferred Work & Sprint Backlog

**Purpose:** durable, in-repo backlog. Anything Claude promised to do "next sprint" or "later" lives here, not just in conversation context. This file is the single source of truth for what's deferred — if it's not here, it doesn't exist.

**Last updated:** 2026-05-19 (after the v0.14.1 hotfix research; promoting
test/observability infra items out of "implied" and into the formal backlog)

---

## How to read this

- **In flight** — currently being shipped or actively designed.
- **Next** — agreed direction, concrete enough to start.
- **Slotted** — captured intent, design pending.
- **Watching** — known gap, not prioritised yet.

Each item should have: *what*, *why it matters*, *touchpoints*, *blocked by*.

---

## In flight

- **v0.14.1 hotfix** (PR #48) — recovers the v0.14.0 ingestion regression
  surfaced by the 2026-05-19 Nothing-A015 dump: 0/916 notifications
  reached Inbox or Transactions because H4's foreign key collided with
  the existing write order in `normalizeLocked`. Fix reverses the order
  (candidate → inbox → backfill), splits the dedupe DAO (stop filtering
  IGNORED in the lookup), adds `"sent from"` etc. to gate vocab, surfaces
  exception class+message into the dump's `outcome` block, and loosens
  the masked-digit regex for single-X SMS forms. Full investigation:
  [ingestion-pipeline-research-2026-05-19.md](ingestion-pipeline-research-2026-05-19.md).

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

### T1 — In-memory Room test fixture
**What:** Add `androidx.room:room-testing` to `androidTestImplementation`, set up an in-memory `RupeeDatabase` builder, and seed a base fixture (one user, one account, one card, one budget). Use it to write the first instrumented DB test — proposed coverage:
- The H4 FK ordering: ingest one INBOX_PENDING-bound notification, assert that `inbox_items` and `transaction_candidates` both have a row (regression for yesterday's bug)
- `DumpOutcomeDao` joined query: insert a synthetic raw → parsed → candidate → inbox → canonical chain, call the DAO, assert the snapshot has the expected fields populated
- Migration `MIGRATION_8_9` (the only table-rebuild migration we've shipped): seed v8 data, run the migration, assert FKs were declared correctly
**Why:** every "structural" fix the gravedigging audit deferred ([gravedigging-2026-05-18.md §L2](gravedigging-2026-05-18.md)) blocks on this fixture. Yesterday's regression would have been caught at PR time with a single test. Until we have this, every schema change is a roulette spin.
**Touchpoints:** new `androidTest/.../IngestionPipelineTest.kt`, `DumpOutcomeDaoTest.kt`, `MigrationTest.kt`; tiny gradle change in `app/build.gradle.kts`.
**Blocked by:** nothing. ~1 day of setup + first three tests.

### T2 — Automated dump-replay in CI
**What:** Each committed dump file in `dumps/*.jsonl` becomes a regression corpus. New gradle task `:android:app:replayDumps` loads each dump, runs every raw body through `NotificationSignalNormalizer.ingestNotification` against an in-memory DB, and asserts that the ingested-vs-rejected distribution doesn't regress more than ±5pp vs a checked-in baseline. PR CI runs the task.
**Why:** three of the last four production bugs were "the gate dropped a class of body it used to accept" or "a parser regressed on a body shape." The dump files ARE our regression corpus — we just don't read them. The user pushes a dump → CI catches the next regression at PR time → we patch without losing a day of transactions.
**Touchpoints:** new `app/src/test/java/.../DumpReplayHarness.kt`, gradle task, baseline snapshot file. Depends on T1.
**Blocked by:** T1 (needs the in-memory Room fixture to run the normalizer end-to-end).

### T3 — Ingestion health surface in the Debug screen
**What:** New Debug-tab card showing rolling 7-day ingestion metrics — total notifications received, % accepted by gate, % ingested with `amountMinor != null`, count of `INGEST_FAILED` with the most common `errorClass` (now that v0.14.1 captures it). Loud red if `INGEST_FAILED` count > 0.
**Why:** v0.14.0's regression hid for 24 hours because nothing in-app reflected the silent failure rate. The data is already captured (it lives in `parsed_signals` for gate decisions and in `raw_capture_events.ingestionStatus`); we just don't surface it. Without this, you notice missing transactions days later instead of minutes.
**Touchpoints:** new `IngestionHealthCard` composable on `DebugScreen`, queries against `parsed_signals` + `raw_capture_events` (7-day window). Optional notification when failure rate spikes.
**Blocked by:** nothing. Half a day.

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
**Blocked by:** nothing — needs a polish brief, not a tech blocker. ~½ to 2 days depending on scope.

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

### S4-5 — Adaptive confidence from user behaviour (Item 13)
**What:** `ingestion_signal_stats` table keyed `(package, parserKey, cleanedMerchant)`. Confirm counts up, dismiss counts down. Bootstrap mode for first 14 days lowers MEDIUM threshold from 0.6 → 0.5 to be more liberal at onboarding. Auto-promote to `MerchantTrustRule` after 3 confirms in a 7+ day window.
**Why:** the app learns the user's actual transaction patterns instead of relying on hardcoded confidence floors. User asked for this explicitly.
**Touchpoints:** new table + DAO, decision engine reads stats, UI shows "auto-trusted (3 confirms)" badge.
**Reference:** `docs/axio-takeaways.md` Item 13.

---

## Watching — known gaps, no sprint yet

### From `docs/notification-ingestion-deep-dive.md` §9
- **§9.3 `extractedJson TEXT NULL` on `RawCaptureEventEntity`.** Persist structured Bundle fields so we can re-parse old events when parser v2 ships. ~1-2 KB/notif storage cost. Not urgent — current `body` field has the combinedBody which is enough for re-parsing 95% of cases.
- ~~**§9.9 Post-ship parse-rate counter.**~~ Promoted to **T3 — Ingestion health surface in the Debug screen** in the **Next** section above (2026-05-19).

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
