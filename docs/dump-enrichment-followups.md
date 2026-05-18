# Dump enrichment — followups

Phase 1 shipped on branch `dump-enrichment` (commit `ce86869`). Each dump line now
carries an `outcome{}` block describing what the ingestion pipeline did with the
notification. Two phases remain — Phase 2a is planned, Phase 2b is optional.

## Phase 2a — DB-snapshot at export time

**Goal:** when the user shares a dump, also emit a sibling `outcomes.jsonl`
file that joins each `rawEventId` (from Phase 1 dump lines) to the **current**
state of the rows it produced. Tells you: did the user confirm / merge / dismiss
that Inbox row? Did they edit the merchant or category? Does the canonical
txn still exist or did they delete it?

The Phase 1 `outcome{}` block in the dump captures **decision at ingest time**.
Phase 2a captures **current state at export time**. Different snapshots,
complementary purposes:

| Question | Answered by |
|---|---|
| Did Rupee route this notification correctly? | Phase 1 outcome |
| What did the user ultimately do with it? | Phase 2a snapshot |

### Implementation plan

1. **DAO/repo method** — new file or extension in `LocalFinanceRepository`:

   ```kotlin
   data class DumpOutcomeSnapshot(
       val rawEventId: String,
       // From parsed_signals — what we extracted; may have changed if a
       // re-parse landed (rare today, common once the JSON rule engine ships).
       val parserKey: String?,
       val transactionKind: String?,
       // From transaction_candidates — current decision state.
       val candidateId: String?,
       val candidateDecisionState: String?,        // PENDING / AUTO_CREATED / USER_CONFIRMED / IGNORED
       val candidateDecisionReason: String?,
       // From inbox_items — what the user did, if anything.
       val inboxItemId: String?,
       val inboxDecisionState: String?,            // PENDING / CONFIRMED / DISMISSED
       val inboxResolvedAt: String?,
       val inboxLinkedCanonicalTxnId: String?,     // non-null on confirm-or-merge
       // From canonical_transactions — the user-visible row's current state.
       val canonicalTxnId: String?,
       val canonicalStatus: String?,               // CONFIRMED / SUGGESTED / IGNORED
       val canonicalType: String?,                 // EXPENSE / INCOME / TRANSFER / CASH_ADJUSTMENT
       val canonicalAmountMinor: Long?,
       val canonicalMerchantName: String?,         // post-edit
       val canonicalCategoryId: String?,           // post-edit
       val canonicalNotes: String?,                // post-edit
       // Did this candidate get merged into a pre-existing txn? Non-null when
       // the user picked "merge with existing" in Inbox.
       val mergedIntoExistingTxnId: String?,
   )

   suspend fun getDumpOutcomeSnapshot(rawEventIds: List<String>): List<DumpOutcomeSnapshot>
   ```

   One method, one pass. Joins via `parsed_signals.rawCaptureEventId =
   raw_capture_events.id` → `transaction_candidates.parsedSignalId` →
   (`inbox_items.transactionCandidateId` LEFT JOIN) → (`canonical_transactions.id`
   = `transaction_candidates.linkedCanonicalTransactionId` LEFT JOIN). Room can
   express this as one `@Query` returning `List<DumpOutcomeSnapshot>` via a
   POJO with the right column aliases.

2. **Read raw event ids from the live dump** — when the share button is tapped,
   stream the dump file, parse just enough JSON per line to extract
   `outcome.rawEventId` (skipping `Filtered` outcomes which have none). Build
   the id list, pass to `getDumpOutcomeSnapshot`.

3. **Write `outcomes.jsonl`** — one line per snapshot, keyed by `rawEventId`,
   in the same `notif-dumps/` directory. Add it to the share intent as a second
   attachment (`Intent.EXTRA_STREAM` with `ArrayList<Uri>` and `ACTION_SEND_MULTIPLE`).

4. **Filename convention** — match the existing dump's stamp:
   `rupee-notif-outcomes-{version}-{device}-{stamp}.jsonl`. Same gitignore
   coverage in `dumps/.gitignore`.

5. **Privacy** — `DumpOutcomeSnapshot.canonicalMerchantName` and `.canonicalNotes`
   reveal user edits (potentially with PII the bank's body didn't have). The
   share dialog already warns about personal data; if we want to tighten, add a
   one-line "Includes user-edited merchant names and notes" beneath the share
   button.

### Files to touch

- `data/repository/LocalFinanceRepository.kt` — new method + DAO query
- `data/local/dao/CanonicalTransactionDao.kt` (or a new `DumpOutcomeDao`) — the
  joined query
- `diagnostics/NotificationDumper.kt` — new `writeOutcomesFile(context,
  snapshots): File` or move to a sibling `NotificationOutcomesExporter`
- `debug/DebugViewModel.kt` — `shareNotificationDumps` reads raw event ids
  from the dump, calls the repo method, writes the sibling file, attaches
  both via `ACTION_SEND_MULTIPLE`
- Tests for the joined query (in-memory Room) and for the dump → id-list
  parser

### Risks

- **In-memory Room test setup** — the project doesn't currently have one. The
  joined query is the first thing that genuinely needs it. Adds
  `androidx.room:room-testing` to `androidTestImplementation` OR write the
  test as a JVM unit test backed by `Room.inMemoryDatabaseBuilder` (works on
  JDK with `useLightweight` builder; Robolectric is overkill).
- **Schema drift** — if `inbox_items` or `transaction_candidates` columns
  rename, the query and POJO need updating in two places. Mitigation: a
  single test fixture that exercises the full join.
- **Dump-line parsing for raw event ids** — full JSON parse per line is
  overkill. A regex on `"rawEventId":"<uuid>"` is sufficient and ~10× faster
  on a 4 MB file.

## Phase 2b — Inline action log (OPTIONAL — can do if needed)

User confirmed this is **not required**. Capture it here so we remember the
shape if we ever decide to ship it.

**Goal:** chronological audit trail of every user action on a candidate /
canonical txn. Today Phase 2a tells us "the current state"; 2b would tell us
"the path that got there" — e.g. user confirmed at 14:01 then deleted at 14:05.

### Implementation sketch

- Every write method on `LocalFinanceRepository` that touches an ingested-by-
  notification row appends an entry to a `notif-actions.jsonl` log:
  `confirmInboxItem`, `confirmInboxItemMergedWith`, `dismissInboxItem`,
  `confirmSuggestedTransaction`, `dismissSuggestedTransaction`,
  `deleteTransaction`, `updateTransactionDetails`, `addMerchantTrustRule`.
- Entry shape: `{when, action, rawEventId?, candidateId?, inboxItemId?,
  canonicalTxnId?, changes: {...}}` — `changes` captures the delta for edits
  (old merchant → new merchant, old type → new type, etc.).
- Discipline: every new write path that touches one of these tables must
  remember to log. **Easy to forget.** Mitigation: a Room `@Update`
  interceptor that fires on every write to the four relevant tables.
  Centralised — can't drop a path by accident.
- Attached as a third file in the share intent (`ACTION_SEND_MULTIPLE`).

### Why we skipped it

For parser tuning and "did the system get this right" investigations,
Phase 2a's current-state snapshot is enough. 2b helps with timeline
forensics ("at 14:01 they confirmed, at 14:05 they deleted — was that a
mistake?"), which isn't a question we're asking yet.

## Doc-touch followups (small)

- `dumps/README.md` should be updated post-Phase-2a to mention the sibling
  `outcomes.jsonl` and how to use it. Current README only covers the raw
  dump.
- `docs/notification-ingestion-deep-dive.md` (if it covers the dump) needs
  a "what's in the outcome block" section pointing at `IngestionResult.kt`.
