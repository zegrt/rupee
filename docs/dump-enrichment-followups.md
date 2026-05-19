# Dump enrichment — followups

Phase 1 shipped on branch `dump-enrichment` (commit `ce86869`). Phase 2a shipped on the
same branch — the share button now emits a sibling `outcomes.jsonl` alongside the raw
`dumps.jsonl`. Phase 2b remains optional (not shipped).

## Phase 2a — DB-snapshot at export time  *(shipped)*

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

### What shipped

- `data/local/dao/DumpOutcomeDao.kt` — single `@Query` joining
  `parsed_signals → transaction_candidates → inbox_items → canonical_transactions`,
  returning `List<DumpOutcomeSnapshot>` by `rawEventId IN (:ids)`.
- `LocalFinanceRepository.getDumpOutcomeSnapshot(rawEventIds)` — chunks at 500
  ids to stay under SQLite's older 999-parameter cap.
- `NotificationDumper.parseRawEventIdsFromDump(file)` — regex over the live
  dump (`"rawEventId":"..."`), order-preserving + deduping. `Filtered` lines
  are skipped automatically since they don't emit the field.
- `NotificationDumper.writeOutcomesFile(outFile, snapshots)` — JSONL emitter
  pinned by `DumpOutcomeExporterTest` (5 cases: id parser order/dedupe,
  missing-file, null-field encoding, confirm-fresh, merge-into-existing).
- `DebugViewModel.shareNotificationDumps` — now uses `ACTION_SEND_MULTIPLE`
  with paired stamps (`rupee-notif-dumps-…` + `rupee-notif-outcomes-…`).
  Falls back to single-file `ACTION_SEND` if the snapshot build fails so a
  share never loses the dump.

### Followup: in-memory Room coverage

The joined query is validated at compile time by Room/KSP, and the JSONL
emitter has unit-test coverage. We deliberately did **not** add the in-memory
Room test infrastructure described in the original plan — adding the first
instrumented test of its kind is its own piece of work, and KSP catches column
renames at build time. When we do add it, the fixture should exercise:

- Filtered raw event → row with `rawEventId` only, everything else null
- Gate-rejected raw event → same shape (still no candidate / inbox / canonical)
- Auto-created candidate → candidate + canonical populated, no inbox row
- Inbox confirm-fresh → candidate, inbox, canonical all populated;
  `mergedIntoExistingTxnId` is null
- Inbox confirm-merged-with-existing → `mergedIntoExistingTxnId` matches
  `inboxLinkedCanonicalTxnId` and both differ from `txn-<candidateId>`
- Inbox dismissed → `inboxDecisionState = DISMISSED`, no canonical row
- Canonical edited post-confirm → `canonicalMerchantName/categoryId/notes`
  reflect the latest edit (snapshot is "current state at export time")

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

## Doc-touch followups (small) — all resolved

- `dumps/README.md` updated with the sibling `outcomes.jsonl`.
- `docs/notification-ingestion-deep-dive.md` received an inline v0.14.0
  callout pointing at this document for the dump format v2 + outcome
  block. The dump-shape source of truth lives in
  [IngestionResult.kt](../android/app/src/main/java/com/zegrt/rupee/ingestion/IngestionResult.kt)
  and [NotificationDumper.kt](../android/app/src/main/java/com/zegrt/rupee/diagnostics/NotificationDumper.kt);
  the deep-dive doc references both rather than duplicating the field
  list inline.
