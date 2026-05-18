# Gravedigging — 2026-05-18

Findings from the audit done the day we shipped the dump-enrichment branch and
diagnosed the Inbox husk bug. Organised by **severity** (High → Medium → Low),
with technical detail per item: root cause, user-visible symptom, code
locations, and proposed fix.

> **Audience note.** Glosses are written for a designer reader — programming
> jargon is defined inline. Code citations are clickable for navigation.

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

| ID  | Title                                                          | Severity | Scope        |
|-----|----------------------------------------------------------------|----------|--------------|
| H1  | Refunds recorded as EXPENSE instead of INCOME                  | High     | ~30 LOC      |
| H2  | Husk-row pattern lurks beyond Inbox (recent transactions list) | High     | ~100 LOC     |
| H3  | No pruning on ingestion tables — DB grows forever              | High     | Migration + DAO |
| H4  | No foreign keys / cascade deletes declared                     | High     | Migration    |
| M1  | Load-bearing `"txn-<candidateId>"` magic string                | Medium   | ~20 LOC + migration |
| M2  | Transactional gate vocabulary gaps                             | Medium   | ~30 LOC + tests |
| M3  | ATM / fuel transactions have no dedicated parser               | Medium   | ~150 LOC     |
| M4  | `lastRecurringRefreshMs` not persisted across cold starts      | Medium   | ~10 LOC      |
| M5  | Generic UPI parser locks confidence at 0.7                     | Medium   | ~10 LOC      |
| M6  | Silent error swallowing on share / snapshot paths              | Medium   | ~20 LOC      |
| M7  | `findMatchingCard` falls back to the only active card          | Medium   | ~5 LOC       |
| L1  | "Coming soon" half-features still wired into UI                | Low      | N/A          |
| L2  | No tests on `TransactionalGate` or `NotificationSignalNormalizer` | Low   | ~200 LOC     |
| L3  | `CrashReporter` returns empty string indistinguishably from no-log | Low | ~5 LOC      |
| L4  | `fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4)` data-loss risk | Low | N/A      |
| L5  | `MainActivity.kt` is 2279 lines — single-file UI                | Low      | Refactor     |

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
2. **Add tests** for each (currently zero coverage on this file — see L2).
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

## L2. Missing tests on critical ingestion components

- `TransactionalGate` — zero test coverage. H1 (refund routing) and M2
  (gate vocab) would have been caught with even smoke tests.
- `NotificationSignalNormalizer` — zero unit/integration tests. The
  whole ingest pipeline is exercised only end-to-end via
  `NotificationParserParseTest`, which doesn't cover the normalizer's
  decision branches.
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

A structural pass that lands these in dependency order. Each step is
its own PR; merge before the next starts. Estimated cumulative effort:
~1 week of focused work; longer with thorough testing.

### Week 1 — kill the bugs
1. **Inbox husk fix** (the bug user verbally reported; diagnosis is in
   chat history). New `InboxItemWithCandidate` POJO with `@Embedded`
   + `@Relation`. Single-PR fix.
2. **H1 — refund classification** (~30 LOC + tests). Highest user-visible
   correctness win.
3. **L2 — minimum viable tests** for `TransactionalGate` and
   `NotificationSignalNormalizer`. Without these, every subsequent
   change is risky.

### Week 2 — structural integrity
4. **H4 — foreign keys + cascade deletes**. Migration is the largest
   the app has shipped; needs a deliberate test pass. Land before H3.
5. **H3 — pruning**. Phase 1 (gate-rejected telemetry table) first,
   Phase 2 (time-based pruning) once H4's cascades are validated.
6. **M2 — gate vocabulary gaps**. Cheap to add, big payoff in
   notification capture rate.

### Week 3+ — quality of life
7. **M1 — `mergedFromExistingCanonicalId` column** (removes magic string)
8. **M5 — dynamic UPI confidence tiering** (less Inbox fatigue)
9. **M7 — `findMatchingCard` defensive guard** (correctness on edge case)
10. **M6 — error surfacing on share path** (debuggability)
11. **M4 — persist `lastRecurringRefreshMs`** (battery)
12. **M3 — ATM and fuel parsers** (workflow friction)
13. **L1, L3, L4, L5** — minor polish; opportunistic.

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

*Audit performed on commit `2a47bc2` (post-PR #37 merge). Findings are a
snapshot — re-audit after the Week 1 fixes land to confirm the structural
recommendations remain valid.*
