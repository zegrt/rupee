# Gravedigging — 2026-05-18

Findings from the audit done the day we shipped the dump-enrichment branch and
diagnosed the Inbox husk bug. Organised by **severity** (High → Medium → Low),
with technical detail per item: root cause, user-visible symptom, code
locations, and proposed fix.

> **Audience note.** Glosses are written for a designer reader — programming
> jargon is defined inline. Code citations are clickable for navigation.

> **Status (as of 2026-05-19):** **Audit closed.** Phase 1, phase 2, phase
> 3a, and phase 3b have all shipped. Every High and Medium tier item is
> done; the L-series tail (L1, L4, L5) is deferred until either the affected
> features actually ship or release prep starts. See the
> [Severity index](#severity-index) below for per-item status and the
> [Suggested remediation roadmap](#suggested-remediation-roadmap) for the
> closing-out picture.

## Cross-cutting theme

The data-layer write paths are **healthy**: notifications get parsed, candidates
get persisted, transactions can be confirmed, dumps capture both raw input and
ingest-time decisions. The correctness gaps cluster on the **read/UI side**:

- **In-memory joins over windowed Room flows** (the Inbox husk bug; see H2/M-Inbox)
- **Parser/gate vocabulary holes** (the P2P bug; see H1, M2)
- **No retention / pruning** of ingest tables (H3)
- **No DB-enforced parent/child integrity** (H4)

These four show up under different surface symptoms (husks, wrong sign on
income, growing storage, occasional orphans) but they have the same root:
**convention enforced at write time, broken at read time.** The "Suggested
remediations" section at the end groups them into one structural pass.

---

## Severity index

| ID  | Title                                                              | Severity | Status     | Shipped in |
|-----|--------------------------------------------------------------------|----------|------------|------------|
| H1  | Refunds recorded as EXPENSE instead of INCOME                      | High     | ✅ shipped | PR #41     |
| H2  | Husk-row pattern lurks beyond Inbox (recent transactions list)     | High     | ✅ shipped | PR #41     |
| H3  | No pruning on ingestion tables — DB grows forever                  | High     | ✅ shipped | PR #41     |
| H4  | No foreign keys / cascade deletes declared                         | High     | ✅ shipped | PR #41     |
| M1  | Load-bearing `"txn-<candidateId>"` magic string                    | Medium   | ✅ shipped | PR #42     |
| M2  | Transactional gate vocabulary gaps                                 | Medium   | ✅ shipped | PR #42     |
| M3  | ATM / fuel transactions have no dedicated parser                   | Medium   | ✅ shipped | PR #44     |
| M4  | `lastRecurringRefreshMs` not persisted across cold starts          | Medium   | ✅ shipped | PR #42     |
| M5  | Generic UPI parser locks confidence at 0.7                         | Medium   | ✅ shipped | PR #42     |
| M6  | Silent error swallowing on share / snapshot paths                  | Medium   | ✅ shipped | PR #42     |
| M7  | `findMatchingCard` falls back to the only active card              | Medium   | ✅ shipped | PR #42     |
| L1  | "Coming soon" half-features still wired into UI                    | Low      | ⏸ deferred | when shipped |
| L2  | Sparse tests on `NotificationSignalNormalizer` (ledger-type mapping) | Low    | ✅ partial | PR #41     |
| L3  | `CrashReporter` returns empty string indistinguishably from no-log | Low      | ✅ shipped | PR #42     |
| L4  | `fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4)` data-loss risk | Low   | ⏸ deferred | release-prep |
| L5  | `MainActivity.kt` is 2279 lines — single-file UI                   | Low      | ⏸ deferred | when blocking |

**12 of 16 audit items shipped, plus the originally-reported Inbox husk bug
fix.** Every High and Medium tier item is on `main` across three PRs (#41,
#42, #44). The remaining four are all in the Low tier and explicitly
deferred — L1 (placeholder comments), L4 (destructive-migration fallback;
needs release prep), L5 (`MainActivity.kt` refactor; not blocking work
today). Two trivial migrations and one table-rebuild migration landed
cleanly across the High and Medium tiers.

---

## H1. Refunds recorded as EXPENSE instead of INCOME

**Severity:** High. Real money in your account doesn't show as income; monthly
spend totals are inflated by the refunded amount.

**Where:**
- [PhonePeNotificationParser.kt:26-37](../android/app/src/main/java/com/zegrt/rupee/ingestion/PhonePeNotificationParser.kt#L26-L37)
- [PaytmNotificationParser.kt:26-30](../android/app/src/main/java/com/zegrt/rupee/ingestion/PaytmNotificationParser.kt#L26-L30)
- [NotificationSignalNormalizer.kt:431-433](../android/app/src/main/java/com/zegrt/rupee/ingestion/NotificationSignalNormalizer.kt#L431-L433)

**Root cause.** The parser layer recognises three kinds of "money in" events:
`INCOME`, `REFUND`, and (less obviously) `PAYMENT` for card bill pays. The
`when` block that maps `transactionKind → candidateType` has explicit
branches for `INCOME` and `SPEND` but **no branch for `REFUND`** — it falls
into the `else → UNKNOWN` arm. Then in `NotificationSignalNormalizer.create
CanonicalTransaction`:

```kotlin
val canonicalType = if (parseResult.transactionKind == ParsedTransactionKind.INCOME)
    CanonicalTransactionType.INCOME
else CanonicalTransactionType.EXPENSE
```

Anything not explicitly `INCOME` becomes `EXPENSE`. Refunds, despite being
parsed correctly upstream, are written as expenses.

**Repro.** Spend ₹500 via PhonePe at a merchant who later refunds it. Two
notifications arrive — the spend (correctly EXPENSE), and the refund. The
refund body says "Refund of ₹500 credited" or similar. App records that as
a second ₹500 expense. Net displayed: −₹1000. Net actual: ₹0.

**Same shape as the P2P fix.** The P2P bug was "credit verbs missing →
direction classifier said UNKNOWN". This is "kind = REFUND → candidateType
switch has no branch". Both bugs slip through because the parser layer
gets a *recognised* state but a downstream `when`/`if` chain doesn't have
a branch for it.

**Proposed fix.**
1. Add `REFUND → TransactionCandidateType.INCOME` to the parser switches in
   PhonePe and Paytm parsers (and any other parser that emits REFUND).
2. Promote the `INCOME` check in `createCanonicalTransaction` to handle
   `REFUND` too:
   ```kotlin
   val canonicalType = when (parseResult.transactionKind) {
       ParsedTransactionKind.INCOME, ParsedTransactionKind.REFUND -> CanonicalTransactionType.INCOME
       else -> CanonicalTransactionType.EXPENSE
   }
   ```
3. Add `ParsedTransactionKind.REFUND` test fixtures to
   `NotificationParserParseTest` and a regression assert that they route
   to `CanonicalTransactionType.INCOME` end-to-end.
4. Audit the other six parsers for the same gap (CRED, ICICI, Kotak,
   GenericUpi, Generic, GPay) — at minimum ensure their `when` blocks
   handle REFUND or explicitly document why they don't emit it.

---

## H2. Husk-row pattern lurks beyond Inbox

**Severity:** High. Recent manual entries or back-dated notifications can
silently drop off the Home screen.

**Where:**
- [HomeViewModel.kt:258-295](../android/app/src/main/java/com/zegrt/rupee/home/HomeViewModel.kt#L258-L295) — fan-in of three observers
- [LocalFinanceRepository.kt:233-247](../android/app/src/main/java/com/zegrt/rupee/data/repository/LocalFinanceRepository.kt#L233-L247) — `LIMIT 20` calls
- [TransactionCandidateDao.kt:12-13](../android/app/src/main/java/com/zegrt/rupee/data/local/dao/TransactionCandidateDao.kt#L12-L13)

**Root cause.** Same shape as the Inbox husk bug (which the user reported
verbally; not yet patched at audit time). The Home screen combines three
**windowed observers** (each capped at the 20 most recent rows ordered by
`createdAt` / `occurredAt`) and **joins them in Kotlin code** instead of in
one SQL query. Glossary:

- **Windowed query** = `SELECT ... LIMIT 20` — only the 20 most recent rows
  reach the UI; everything else is invisible.
- **In-memory join** = the UI fetches two lists separately and pairs them
  up by id in Kotlin. If one list windows past a referenced row, the join
  returns `null` and the UI renders a husk.

Specifically:
- `observeRecentTransactions(limit = 20)` — last 20 canonical txns by `occurredAt`
- `observeRecentTransactionCandidates(limit = 20)` — last 20 candidates by `createdAt`
- `observePendingInboxItems(limit = 20)` — last 20 PENDING inbox rows

**Symptoms.**
- **Inbox husk** (the original bug): a pending inbox row whose candidate has
  rotated past the 20-row window renders with empty merchant and `—` amount.
- **Hidden transactions**: a back-dated manual entry (or any txn with an
  `occurredAt` earlier than the 20th-most-recent) is silently absent from the
  Home transactions list.
- **Compounding with H3**: the more spam your phone gets, the faster the
  candidate window rotates → the more Inbox husks appear.

**Repro (Inbox husk).** With 20+ marketing/OTP/promo notifications arriving in
quick succession after a real transactional notification, the real candidate
falls off the recents window and the Inbox row goes husk.

**Repro (back-dated transactions).** Manually enter a transaction with date
= last week. If 20 newer transactions exist, last week's entry won't appear
on Home (still in the DB; visible only in screens that don't window).

**Proposed fix.**
1. **Inbox path** — replace the parallel-flow join with a Room `@Relation`
   query. New POJO `InboxItemWithCandidate` with `@Embedded inbox` +
   `@Relation candidate`. The DB does the join; husks become impossible.
2. **Recent transactions** — switch from `LIMIT 20 ORDER BY occurredAt DESC`
   to `WHERE occurredAt >= :sevenDaysAgo` or paged query. A time-windowed
   query has a defined semantic ("last 7 days"); a row-windowed query
   silently lies.
3. **Audit other `LIMIT N` observers** for the same hazard:
   - `observeSuggestedTransactions(limit = 50)` — same shape; less acute
     because suggested-status txns are bounded.
   - `observeRecentRawEvents(limit = 50)` / `observeRecentParsedSignals(limit = 50)`
     — debug-screen only, lower stakes.

**Why this matters structurally.** Every windowed observer that the UI
joins to is a future husk bug. The hardest version of this fix is to adopt
a discipline: **observers feed the UI a single shape**, not a join target.

---

## H3. No pruning on ingestion tables — DB grows forever

**Severity:** High. Slow rot: not visible week 1, painful by month 3.

**Where:**
- [NotificationSignalNormalizer.kt:293-354](../android/app/src/main/java/com/zegrt/rupee/ingestion/NotificationSignalNormalizer.kt#L293-L354) — `writeGateRejectedSignal` writes to both `parsed_signals` AND `transaction_candidates` for **every gate-rejected** notification
- [LocalFinanceRepository.kt:801-807](../android/app/src/main/java/com/zegrt/rupee/data/repository/LocalFinanceRepository.kt#L801-L807) — only the debug nuke (`wipeRawCaptureData`) deletes from `raw_capture_events`

**Root cause.** Every notification that reaches the listener writes:
- 1 row in `raw_capture_events` (raw text storage)
- 1 row in `parsed_signals` (what the parser extracted — written even for gate-rejected)
- 1 row in `transaction_candidates` (decision state — written even for gate-rejected)

There is **no time-based pruning, no row-count cap, no archival job**. The
only DELETE queries in the codebase are:
- `DELETE FROM raw_capture_events` (debug, manual)
- `recurring_patterns` by id
- `merchant_trust_rules` by id
- `emi_plans` by id

On a notification-heavy phone (banking + payments + shopping + delivery),
ingestion fires hundreds of times per week — most of it gate-rejected
marketing — and every event leaves 3 rows behind permanently.

**Symptoms.**
- **DB file size** grows linearly with notification volume; SQLite remains
  fine into the tens of MB but the dump-file rotation (4 MB cap) loses
  context faster than the DB does.
- **Inbox husk frequency** increases (H2 compounds): more candidates →
  faster window rotation past inbox-referenced rows.
- **Cold-start scans get slower**: M4's recurring-pattern recompute reads
  120 days of `canonical_transactions`; not affected directly, but the
  general read patterns on `transaction_candidates` will eventually slow.

**Proposed fix (phased).**
1. **Stop the bleeding on gate-rejected rows.** Move `writeGateRejectedSignal`
   to a much narrower telemetry table (or just a counter) — these rows are
   never read by the UI, only by debug dump-replay. A 7-day rolling table
   with `OnConflictStrategy.REPLACE` keyed on (`packageName`, `body_hash`)
   would keep the per-app rejection rate observable without unbounded storage.
2. **Prune raw + parsed older than N days.** Add a periodic job (run on
   ingestion or via WorkManager) that deletes raw_capture_events and
   parsed_signals older than 90 days. Keep `transaction_candidates` longer
   only if they're referenced by an inbox_item or canonical_transaction
   (this requires H4's foreign keys to express cleanly).
3. **Schema migration to add `createdAt` indices** where missing — the
   pruning DELETE will need them.

**Estimated effort.** Phase 1 is small (~30 LOC + a migration to create
the telemetry table). Phase 2 is medium (~80 LOC + WorkManager wiring).
Combined: ½ day to a day of focused work.

---

## H4. No foreign keys / cascade deletes declared

**Severity:** High structurally; symptomless until a migration or partial
wipe goes wrong, at which point: orphan rows everywhere.

**Where.** Search confirms zero `@ForeignKey` declarations across
[android/app/src/main/java/com/zegrt/rupee/data/local/entity/](../android/app/src/main/java/com/zegrt/rupee/data/local/entity/).

**Root cause.** The schema has clearly-shaped parent/child relationships:

```
raw_capture_events
    ↓ rawCaptureEventId
parsed_signals
    ↓ parsedSignalId
transaction_candidates
    ↓ transactionCandidateId
inbox_items
    ↓ linkedCanonicalTransactionId
canonical_transactions
```

But these are **convention-only** — the parent column is referenced by the
child column, and the foreign-key constraint exists in the developer's
mental model, but the database has no idea. Glossary:

- **Foreign key** = a database rule that says "this column in child table
  must reference an existing id in parent table — otherwise reject the
  write."
- **Cascade delete** = a database rule that says "if a parent row is
  deleted, automatically delete its children." Without this, deleting a
  parent leaves orphan children — children that point at a parent that no
  longer exists.

**Symptoms (latent today, will surface with any of these triggers):**
- H3's pruning job, when added, will leave orphan inbox_items and
  transaction_candidates pointing at deleted parsed_signals.
- The debug "Reset all data" / "Wipe raw capture" flows currently rely on
  ordered deletes; any reordering or partial failure leaks orphans.
- Future migrations that drop or split a table.

**Proposed fix.**
1. **Declare foreign keys** with `ON DELETE CASCADE` (or `RESTRICT` where
   appropriate) on each child entity:
   ```kotlin
   @Entity(
       foreignKeys = [
           ForeignKey(
               entity = ParsedSignalEntity::class,
               parentColumns = ["id"],
               childColumns = ["parsedSignalId"],
               onDelete = ForeignKey.CASCADE,
           ),
       ],
       indices = [Index("parsedSignalId"), ...],
   )
   ```
2. **One migration** that adds the FK constraints in SQLite's roundabout
   way (CREATE TABLE new; INSERT INTO new SELECT * FROM old; DROP old;
   RENAME new). Room's `MIGRATION_N_M` can do this but it's the largest
   migration the app has shipped — needs a deliberate test pass.
3. **Test discipline**: after the migration, the in-memory Room test
   fixture (deferred from PR #37) becomes mandatory — without it,
   regressions on the FK behaviour are silent.

**Why this matters even without immediate symptoms.** Once H3's pruning
ships, H4 becomes load-bearing. Doing them in the wrong order will
introduce orphans. Best to land H4 first.

---

## M1. Load-bearing `"txn-<candidateId>"` magic string

**Severity:** Medium. We've added cross-link comments (PR #37 polish commit),
but the underlying coupling remains.

**Where:**
- [LocalFinanceRepository.kt:498-502](../android/app/src/main/java/com/zegrt/rupee/data/repository/LocalFinanceRepository.kt#L498-L502) — write site
- [DumpOutcomeDao.kt:51-55, 93-98](../android/app/src/main/java/com/zegrt/rupee/data/local/dao/DumpOutcomeDao.kt) — read site (SQL CASE expression)

**Root cause.** When the user confirms an inbox row, the synthesised
canonical transaction id is `"txn-${candidate.id}"`. The dump-outcome
DAO's `mergedIntoExistingTxnId` column derives "this was a merge"
heuristically by comparing `inbox.linkedCanonicalTransactionId` against
the literal `'txn-' || tc.id` form. KSP catches column-name typos but
**not** SQL semantics: if anyone changes the id format (UUID, snowflake,
hex), the CASE expression silently returns wrong values.

**Proposed fix.** Add a real column to `inbox_items`:
`mergedFromExistingCanonicalId: String?`. `confirmInboxItemMergedWith`
sets it; `confirmInboxItem` leaves it null. The DAO selects the column
directly instead of reverse-engineering it. Requires a small migration
(add nullable column) and a one-line backfill from inspection of
existing rows is optional.

**Estimated effort.** ~30 LOC + small migration. Half-day with tests.

---

## M2. Transactional gate vocabulary gaps

**Severity:** Medium. Real bodies in the wild get rejected as spam.

**Where:**
- [TransactionalGate.kt:64-79](../android/app/src/main/java/com/zegrt/rupee/ingestion/TransactionalGate.kt#L64-L79) — `POSITIVE_VERBS` list

**Root cause.** The gate's positive-verb list controls which notifications
are deemed "potentially transactional" and forwarded to the parsers.
Missing entries:

| Phrase | Why it matters |
|---|---|
| `reversed` / `reversal` | Failed-autodebit reversals (real money back) |
| `chargeback` | Disputed charge resolutions |
| `refund of` | Variant of `refunded` not currently caught |
| `SIP installment` | Mutual fund debits with no anchor verb |
| `NACH mandate` | Standing instructions on Indian banks |
| `ECS debit` | Electronic clearing service debits |
| `standing instruction executed` | Recurring bill auto-pays |

Same shape as the P2P fix. The fix that just shipped added
`STRONG_CREDIT_PHRASES` for receiver-side P2P phrasing. This is the
analogous "negative-side missing vocabulary" pattern.

**Proposed fix.**
1. Add the missing phrases to `POSITIVE_VERBS` (or split into a dedicated
   `STRONG_TRANSACTIONAL_PHRASES` if any overlap with the spam side).
2. **Add tests** for each — `TransactionalGateTest` has a real-dump
   corpus pattern that's easy to extend; drop one fixture per phrase.
3. Make a habit of dump-replay: every release, pull the latest production
   dumps, filter for `outcome.kind = gate_rejected` lines, eyeball the
   reasons. Anything that *should* have been transactional becomes a
   test fixture.

**Estimated effort.** ~30 LOC of vocab + ~80 LOC of tests. Half-day.

---

## M3. ATM withdrawals and fuel transactions have no dedicated parser

**Severity:** Medium. Workflow-friction, not data-correctness.

**Where:**
- [GenericNotificationParser.kt:79](../android/app/src/main/java/com/zegrt/rupee/ingestion/GenericNotificationParser.kt#L79) — `atm` only used for Mode classification
- No `AtmNotificationParser.kt`, no fuel-station parser

**Root cause.** ATM bodies (HDFC, Axis, ICICI ATM withdrawals) typically
say "ATM Cash Wdl Rs.5000 from A/c XX1234" — they lack a merchant name
in the conventional sense. The generic parser produces a candidate with
`toEntityName = null`, which surfaces in the UI as "Unnamed" and routes
to Inbox forever. Same for fuel pumps (HPCL/IOCL/BPCL) which usually
include the location ("HPCL DELHI") but no clean merchant clean-up rule.

**Proposed fix.**
1. **ATM parser**: dedicated rule that recognises ATM withdrawal bodies,
   sets `mode = ATM`, sets `toEntityName = "ATM Cash"` (or extracts the
   ATM location if present), routes to auto-create at high confidence.
2. **Fuel parser**: pattern recognition for "Fuel" / "PETRO" / location
   tokens; set category to fuel where confident.
3. Both should slot into the existing `NotificationParserRegistry` like
   the other parsers.

**Estimated effort.** ~150 LOC + tests per parser. 1-2 days.

---

## M4. `lastRecurringRefreshMs` not persisted across cold starts

**Severity:** Medium. Wasted battery, not correctness.

**Where:**
- [LocalFinanceRepository.kt:54-55](../android/app/src/main/java/com/zegrt/rupee/data/repository/LocalFinanceRepository.kt#L54-L55) — `AtomicLong` instance field
- [LocalFinanceRepository.kt:118+](../android/app/src/main/java/com/zegrt/rupee/data/repository/LocalFinanceRepository.kt#L118) — `refreshRecurringPatterns`

**Root cause.** The recurring-pattern detection scans 120 days of
`canonical_transactions`. To avoid running this on every foreground
tick, there's a 30-minute debounce backed by an in-memory `AtomicLong`.
On cold start, the field resets to 0 → the next foreground entry runs
the full scan again, wastefully.

**Proposed fix.** Persist the last-refresh timestamp:
- Add a key to a small `app_state` table or to `DataStore` preferences
  (the project doesn't currently use DataStore — consider whether to
  add it or keep state in Room with a single-row settings table).
- Hydrate the `AtomicLong` on `LocalFinanceRepository` initialisation.
- Update it both in memory and in persistence after each successful
  refresh.

**Estimated effort.** ~20 LOC, one small migration. Half-day with
testing.

---

## M5. Generic UPI parser locks confidence at 0.7

**Severity:** Medium. Manual-review fatigue.

**Where:**
- [GenericUpiNotificationParser.kt:47-49](../android/app/src/main/java/com/zegrt/rupee/ingestion/GenericUpiNotificationParser.kt#L47-L49) — "keep at 0.7 for now" comment

**Root cause.** A confidence of 0.7 routes the candidate to Inbox per
`NotificationDecisionEngine`'s thresholds, even when the parser has
extracted merchant + amount + masked digits + mode. The "for now"
comment suggests the value was a placeholder pending tuning that never
happened.

**Symptoms.** Routine UPI debits the app *could* auto-confirm (HIGH
confidence path) sit in Inbox waiting on the user. Review fatigue
trains the user to mass-confirm without reading, defeating the Inbox.

**Proposed fix.** Tier the confidence dynamically based on how much
was extracted:
- amount + merchant + maskedDigits + mode → 0.85 (HIGH)
- amount + merchant + mode → 0.78 (MEDIUM)
- amount only → 0.55 (LOW)

Mirror what `GpayNotificationParser.confidenceFor` already does for
its provider. After the change, replay the latest production dumps to
confirm the distribution of confidence values matches intent.

**Estimated effort.** ~10 LOC + dump-replay verification. Half-day.

---

## M6. Silent error swallowing on share / snapshot paths

**Severity:** Medium. Bad UX when something fails; bad debuggability
for us.

**Where:**
- [DebugViewModel.kt](../android/app/src/main/java/com/zegrt/rupee/debug/DebugViewModel.kt) — `runCatching { … }.isSuccess` / `.getOrNull()` / `.onFailure {}` around the dump-share flow

**Root cause.** Defensive `runCatching` wrappers around DB reads, file
writes, FileProvider URI construction. On failure, the only signal is
`message = "Couldn't prepare dump for sharing."` — no exception detail
reaches CrashReporter or logcat, so we have no diagnostic trail when a
user reports "share doesn't work."

**Proposed fix.**
1. **Surface the throwable**: in each `runCatching { … }.onFailure { t -> }`
   block, log `t` to logcat with a stable tag and append a short reason
   string to the user message: `"Couldn't prepare dump (${t.javaClass.simpleName}: ${t.message?.take(80)})"`.
2. **Funnel to CrashReporter** for non-trivial failures so they're
   visible from the debug screen later.
3. Listener service exception swallowing
   ([RupeeNotificationListenerService.kt:93-104](../android/app/src/main/java/com/zegrt/rupee/ingestion/RupeeNotificationListenerService.kt#L93-L104))
   is acceptable as-is — a crash there kills the service and stops
   ingestion silently, which is worse — but logging the exception detail
   would help debug parser regressions.

**Estimated effort.** ~30 LOC. Couple of hours.

---

## M7. `findMatchingCard` falls back to the only active card

**Severity:** Medium. Edge case, can cause silent data overwrite.

**Where:**
- [NotificationSignalNormalizer.kt:397-399](../android/app/src/main/java/com/zegrt/rupee/ingestion/NotificationSignalNormalizer.kt#L397-L399)

**Root cause.** When a BILL_DUE notification arrives and no card match is
found by masked digits or provider hint, the code falls back to the
single active card if exactly one exists. The intent is helpful: most
users start with one card, the bill-due should apply to it. But the
fallback fires even when **the body had a digit-match attempt that
failed** — e.g. the parser captured `xx5678` but the user's only card
ends in `xx1234`. The mismatch is ignored; the alert applies to the
wrong card.

**Proposed fix.** Require at least one positive signal:
- If parser captured `maskedDigits` and they don't match, return null
  (don't fall back).
- If parser captured a provider hint that doesn't match the only card's
  provider, return null.
- Only fall back to the only active card when **no identifying signal
  was extracted at all** (very weak parser output).

```kotlin
// Before falling back to the single card, require that nothing
// identifying was extracted — a digit or provider mismatch should
// fail closed, not silently overwrite the wrong card.
if (parseResult.maskedDigits != null || parseResult.sourceCardHint != null) {
    return null
}
return cards.singleOrNull()
```

**Estimated effort.** 5 LOC + a test. Hour or two.

---

## L1. "Coming soon" half-features

- [CardsEmisScreen.kt:61](../android/app/src/main/java/com/zegrt/rupee/cards/CardsEmisScreen.kt#L61) — "auto-detection from notifications is coming"
- [ui/theme/Theme.kt:51](../android/app/src/main/java/com/zegrt/rupee/ui/theme/Theme.kt#L51) — "no manual toggle for now"

Track each as a real ticket or remove the placeholder. Comment debt
accumulates fast in personal projects.

## L2. Sparse tests on critical ingestion components

> **Correction (post-audit).** The audit agent's grep missed
> `TransactionalGateTest.kt`. That file actually has 20+ accept/
> reject cases drawn from real production dumps and the v0.13.4
> Kotak marketing-push regression — coverage there is fine. The
> remaining gaps are below.

- `NotificationSignalNormalizer` — historically zero direct
  unit/integration tests; the whole ingest pipeline was exercised
  only end-to-end via `NotificationParserParseTest`. The phase-1
  test-net commit on this branch carves the canonical-type slice
  out into a pure `canonicalTypeFor` helper and pins it with
  `CanonicalTypeMappingTest` (one case per `ParsedTransactionKind`
  enum value). Other decision branches inside `normalizeLocked`
  (dedupe / trustRule / baseDecision merging) remain untested at
  the unit level and need an in-memory Room fixture to cover.
- DAO tests — none. PR #37's followup (in-memory Room fixture) blocks
  on this.

**Why this is L not H**: not a bug itself, but the cause of every
"silent regression" in the high tier. Coverage on these three would
have caught H1 and M2 at PR time.

## L3. `CrashReporter` returns empty string on success and on read-failure

[CrashReporter.kt:40-44](../android/app/src/main/java/com/zegrt/rupee/diagnostics/CrashReporter.kt#L40-L44) — `readLog` returns `""` whether the log file is empty OR
the read failed. Debug screen can't distinguish "no crashes" from
"couldn't read the log file." Tiny fix; tiny impact.

## L4. `fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4)`

[RupeeApplication.kt:25](../android/app/src/main/java/com/zegrt/rupee/RupeeApplication.kt#L25) — debug-build users still on v1-v4 will lose all data on
upgrade. Fine pre-1.0; remove the fallback before any real release.

## L5. `MainActivity.kt` is 2279 lines

Not a bug — but every list-rendering issue (potential future H2-class
bug) is harder to spot. The file does double duty as navigation host
and screen-level composition. A pass that extracts each top-level
screen into its own file would help future audits land cleaner. The
absence of `LazyColumn` everywhere (everything is hand-rolled
`verticalScroll` over `.take(20)`) is fine today because of H3's
`LIMIT 20` semantics, but the moment those limits get lifted (e.g. for
H2's time-based queries), every screen needs to become Lazy.

---

## Suggested remediation roadmap

Phase 1, phase 2, phase 3a, and phase 3b landed across four PRs over ~3
days. All audit items in the High and Medium tier are shipped. Roadmap
captured in hindsight below — useful as a paper trail and to inform how
future audits get sequenced.

### Phase 1 — kill the bugs *(PR #41, merged)*
1. **Inbox husk fix** — new `InboxItemWithCandidate` POJO with
   `@Embedded` + `@Relation`. Joined at the DB so husks become
   structurally impossible.
2. **H1 — refund classification** — RefUND now routes to INCOME at the
   normalizer + parser layers.
3. **L2 partial — canonical-type test net** — extracted `canonicalTypeFor`
   into a pure function pinned by `CanonicalTypeMappingTest` (10 cases).

### Phase 2 — structural integrity *(PR #41, merged in the same branch)*
4. **H4 — foreign keys + cascade deletes**. Largest migration the app
   has shipped; rebuilt three tables with FK clauses, pre-cleaned
   dangling references, `PRAGMA foreign_keys = OFF` around the swap.
5. **H3 phase 1 — gate-rejected candidate writes dropped** (saves
   ~18k rows/year on a notification-heavy phone).
6. **H3 phase 2 — 90-day time-based prune** via FK cascade. Debounced
   24h, fire-and-forget from `RupeeApplication.onCreate`.
7. **H2 — date-windowed recent-transactions query**. Replaces
   `LIMIT 20 ORDER BY occurredAt DESC` with a 30-day window.

### Phase 3a — correctness wins *(PR #42, merged)*
8.  **M2** — gate vocabulary: `reversed`/`reversal`/`chargeback`/SIP/NACH/ECS.
9.  **M7** — `findMatchingCard` defensive guard.
10. **M5** — dynamic UPI confidence tiering (HIGH = 0.85 when fully extracted).
11. **M1** — `mergedFromExistingCanonicalId` column kills the magic-string CASE.
12. **M6** — exception logging on the share path.
13. **M4** — `app_state` K/V table persists debounce timestamps across cold starts.
14. **L3** — `CrashReporter.readLog` returns `Result<String>`.

### Phase 3b — workflow friction *(PR #44, merged)*
15. **M3a — ATM withdrawal parser**. New `AtmNotificationParser`,
    registered FIRST so HDFC/ICICI/SBI/Axis ATM bodies route to
    `CASH_WITHDRAWAL` (flattens to `CASH_ADJUSTMENT`, excluded from spend
    totals) instead of being misclassified as SPEND by bank-specific
    parsers. Routes to Inbox for user confirmation rather than auto-
    create — cash withdrawals deserve a visible review step before they
    leave the spend ledger.
16. **M3b — fuel-brand normalisation**. Added to `MerchantNameUtils.clean`
    rather than a dedicated parser, so every existing bank/UPI parser
    benefits without losing maskedDigits + provider hints. IOC/HPCL/BPCL/
    Shell/Nayara/Essar tokens normalise to the bare brand; padded
    whole-word matcher avoids the BIOCON / IOC collision (regression
    pinned).

### Deferred indefinitely (release-prep / refactor — no user-visible payoff today)
- **L1** — "coming soon" half-features. Surface when those features ship.
- **L4** — `fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4)`. Pre-MVP
  app per the rest of the docs; keep until release prep starts.
- **L5** — `MainActivity.kt` decomposition. 2279 lines but not blocking
  audit work; tackle when the file actively gets in the way.

## Discipline takeaways

Every High-tier bug here ships with one of these "smells" — useful to
internalise so future PRs can be self-screened:

1. **A `LIMIT N` on an observed flow that the UI joins to.** If the
   joined source can grow, a windowed query is a husk-bug-in-waiting.
   Use `WHERE timestamp >= :cutoff` queries OR DB-level joins via
   `@Relation`.
2. **A `when` block over an enum without an explicit branch for every
   case.** Kotlin's compiler will warn on non-exhaustive `when` for
   `sealed`/`enum` results — heed those warnings. The refund bug is
   exactly this.
3. **Convention enforced only at write time.** If the database schema
   doesn't encode the constraint, future code WILL violate it. Foreign
   keys, `NOT NULL`, `CHECK` constraints — let SQLite do the work.
4. **A write path with no corresponding cleanup path.** Every INSERT
   needs to answer "when does this row die?" — even if the answer is
   "never, by design."
5. **`runCatching { }.getOrNull()` without logging.** If you don't
   want to crash on failure, at least log what failed. Silent
   `getOrNull` is debugging debt.

---

*Audit performed on commit `2a47bc2` (post-PR #37 merge). Closing-out
state captured 2026-05-19 after PR #44 (the final audit-item merge):
12 of 16 items shipped across PRs #41, #42, and #44, plus the
originally-reported Inbox husk bug fix. The remaining four (L1, L4,
L5) are deferred indefinitely per the roadmap above. **The audit is
closed.** A re-audit after another month of real usage is the next
sensible touch point — new vocabulary gaps and new edge cases tend to
surface on the cadence of dump-replay sessions.*
