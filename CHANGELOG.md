# Changelog

All notable changes to Rupee are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
with pre-release tags (`-alpha.N`, `-beta.N`, `-rc.N`).

The detailed historical narrative through v0.14.5 lives in
[`CONTEXT.md`](CONTEXT.md). This file is the per-release summary; the
backlog of what's planned next is in
[`docs/rupee-backlog.md`](docs/rupee-backlog.md).

The mobile-stage progression we track against:
**pre-alpha → alpha → closed beta → open beta → release candidate → 1.0**.

---

## [0.15.0-alpha.3] — 2026-05-25

Sprint 4 safety bundle + the DIAG-CAPTURE-TOGGLE from the 2026-05-25
audit. No new features; six pure-correctness items the alpha-soak
audit surfaced. Diagnostic-capture is now a user-controllable toggle
(defaults ON during alpha builds), so testers on this signed release
can actually capture dumps when something breaks.

### Added

- **DIAG-CAPTURE-TOGGLE** — Settings → Privacy & data gains a
  "Diagnostic capture" Switch backed by `app_state`. Defaults ON for
  `-alpha` / `-beta` versionNames; OFF for `-rc` / stable. Upgrade-
  reset hook reverts to the stage default when `VERSION_CODE`
  changes so a stale ON doesn't leak across major releases. Persistent
  banner on Home + Inbox while capture is on, tappable to Settings.
  Matches the AOSP privacy guidance + Signal / Bitwarden / Proton
  / DuckDuckGo pattern. Unblocks the alpha-soak feedback loop —
  pre-alpha.3, the dumper was gated to `BuildConfig.DEBUG` so
  testers on the signed release APK couldn't capture dumps even
  when bugs surfaced.

### Changed

- **CANONICAL-AUDIT-TRAIL** — `CanonicalTransactionDao.upsertTransactions`
  swapped from `@Insert(onConflict = REPLACE)` → `@Upsert`. Stops the
  silent SET-NULL of `linkedCanonicalTransactionId` back-pointers on
  inbox + candidate rows every time a canonical transaction is
  confirmed / edited / deleted. Sibling fix to alpha.2's
  TransactionCandidateDao swap; same SQLite REPLACE gotcha, softer
  consequence (SET NULL vs CASCADE).
- **PARSED-SIGNAL-UPSERT-LATENT** — `ParsedSignalDao.upsertParsedSignal`
  swapped to `@Upsert`. Defense-in-depth — `parsed_signals` is the
  CASCADE parent of `transaction_candidates`, so a future code path
  that re-upserts a parsed_signal would silently destroy every
  downstream candidate + inbox row. Not exploitable today (every
  callsite uses a fresh UUID) but trivial to exploit accidentally.
- **MONEY-MATH-LEGACY** — `GenericNotificationParser.extractAmountMinor`
  now routes through the shared `NotificationParsingUtils.extractAmountMinor`.
  Fixes the alpha.1 Walnut SMS-bridge bug where `₹1,593.77 Credited
  from 7510773991@YESCRED` parsed as ₹159.00 (the old regex's `[.,]`
  treated dot/comma as interchangeable, mangling Indian-grouped
  decimals; the `Double * 100 → Long` rounding was also vulnerable
  to off-by-one drift). Three new unit tests pin the corpus.
- **TRUST-WRITE-RACE** — `LocalFinanceRepository.setMerchantTrust`
  read-then-conditional-write pair wrapped in `database.withTransaction`.
  Two rapid TRUST-FROM-TXN toggle taps no longer race on the unique
  pattern constraint.

### Fixed

- **DATE-PARSE-LOGGING** — Three `runCatching{ LocalDate.parse }.getOrNull()`
  sites in `HomeViewModel`, `RecurringDetectionEngine`, and
  `DuesAlertManager` used to swallow `DateTimeParseException`
  silently. A malformed timestamp in any one of those would silently
  drop a row from upcoming-dues / recurring detection / dues
  notifications with no failure trail. Each now logs `Log.w("Rupee",
  ...)` before the `getOrNull()` so tester reports like "my EMI
  isn't showing under upcoming dues" have something to bisect against.

### Released as

- `versionName = "0.15.0-alpha.3"`, `versionCode = 44`
- Git tag: `v0.15.0-alpha.3`
- Signed release APK: `rupee-0.15.0-alpha.3-release.apk`

### Behind the scenes — Sprint 4 progress

This release closes **6 of 8** Sprint 4 items (DIAG-CAPTURE-TOGGLE
shipped in alpha.2's interim PR; the five above ship here). Remaining:
**MIGRATION-SKIP-TEST** (~1 day, test-infra + chain test),
**CAST-SAFETY + REPO-VM-TEST-COVERAGE** (~2-3 days, the refactor pair).

---

## [0.15.0-alpha.2] — 2026-05-23

**Hotfix for the alpha-blocker discovered in ALPHA-STAGE-ROLL.** The very
first day of real-device soak surfaced that every notification routing to
`INBOX_PENDING` was being silently dropped — the user saw an empty Inbox
even though `ingestNotification` was reporting `Ingested` outcomes.

### The bug
`TransactionCandidateDao.upsertTransactionCandidate` was annotated with
`@Insert(onConflict = REPLACE)`, which Room renders as
`INSERT OR REPLACE INTO transaction_candidates ...`. SQLite's REPLACE
conflict resolution **deletes the conflicting row first**, then inserts.
Because `inbox_items.transactionCandidateId` has
`onDelete = ForeignKey.CASCADE`, every REPLACE on a candidate row
cascade-deleted any inbox row pointing at it.

`NotificationSignalNormalizer.normalizeLocked` does three writes in a
single transaction:
1. Insert candidate
2. Insert inbox_item (FK to candidate)
3. Re-upsert candidate to backfill `linkedInboxItemId`

Step 3's REPLACE cascade-deleted the inbox row from step 2. The
`IngestionResult.Ingested` returned still carried the now-orphaned
`inboxItemId`, which is why the dump line said "ingested with inbox id
X" but the actual DB row was gone.

Bug lived in production since v0.14.1 (when the H4 write-order fix
introduced the third upsert). v0.14.0 had a different bug (FK violation
on first write) that masked this one — once that was fixed, this one
took over. Three production dumps (v0.14.2, v0.14.5, v0.15.0-alpha.1)
all showed the signature: candidates in INBOX_PENDING with
`inboxItemId = NULL` in the snapshot.

### The fix
Switch `TransactionCandidateDao.upsertTransactionCandidate` from
`@Insert(onConflict = REPLACE)` to `@Upsert`. Room's `@Upsert`
(introduced in Room 2.5, we're on 2.7.1) generates
`INSERT ... ON CONFLICT(id) DO UPDATE SET ...` — an in-place update
that doesn't trigger conflict-resolution deletion and doesn't cascade.

Reference: https://dexterslog.com/posts/insert-on-conflict-replace-with-on-delete-cascade-in-sqlite/

### Test coverage
- `IngestionPipelineTest.ingestNotification_inboxPendingPath_writesBothInboxAndCandidate`
  now uses a Kotak debit body (the only parser-shape that still lands
  MEDIUM tier after Sprint 2's S1.3 promotions) and asserts the inbox
  row count is exactly 1 post-ingest. Reproduced the bug on a real
  device (Nothing-A015 / Android 16) before the fix.
- The same test caught the pre-existing tier assertion drift — its
  prior body landed AUTO_CREATED post-Sprint 2 instead of INBOX_PENDING,
  which is why the existing test infra hadn't caught the cascade bug
  on its own.

### Three pre-existing test failures filed to Sprint 4
Discovered during this hotfix work but unrelated to the cascade bug:
- 3× `MigrationTest` fail with "Cannot find the schema file in the
  assets folder" — a build.gradle.kts source-set fix.
- 2× `DumpOutcomeDaoTest` fail with `SQLiteConstraintException: FOREIGN
  KEY constraint failed` — the test fixture pre-dates H4's FK
  enforcement and constructs invalid parent-child orderings.
- Sibling concern: `CanonicalTransactionDao.upsertTransactions` still
  uses `@Insert(REPLACE)`, which silently SET NULLs the audit-trail
  back-pointers from inbox/candidate every time. Filed as
  `CANONICAL-AUDIT-TRAIL`. Softer bug than the cascade-delete (no data
  loss, just audit-trail loss).

All three filed in `docs/rupee-backlog.md` under Sprint 4 (closed-beta
stability hardening).

### What this hotfix does NOT do
- **Recover the inbox rows already lost.** Every inbox row written
  between v0.14.1 and v0.15.0-alpha.1 was cascade-deleted at write
  time. Those candidates still exist (in `INBOX_PENDING` state) with
  orphaned `linkedInboxItemId` pointers. They cannot be recovered.
  After upgrading to alpha.2, all *new* notifications routing to
  INBOX_PENDING will properly create durable inbox rows.

### Released as
- `versionName = "0.15.0-alpha.2"`, `versionCode = 43`
- Git tag: `v0.15.0-alpha.2`
- Signed release APK: `rupee-0.15.0-alpha.2-release.apk`

---

## [0.15.0-alpha.1] — 2026-05-23

**The alpha milestone.** First Rupee build judged stable enough for a
small closed-tester group. Sprint 3 closed every blocker the audit
surfaced; the app's core ingestion + Inbox + budget + calendar surfaces
are feature-complete for the alpha scope.

### Added (this release)

- **BABYPROOF-INPUTS** (PR #75) — three reusable input primitives in
  `ui/Inputs.kt`:
  - `CurrencyInputField` — ₹ prefix, digit-only filter (optional one
    decimal + 2 paise), Indian-grouping `visualTransformation` so
    `100000` reads as `1,00,000` while typing.
  - `DateField` — read-only OutlinedTextField that opens a Material 3
    `DatePickerDialog`. Replaces every "type yyyy-MM-dd" site.
  - `ProviderDropdown` — typeable dropdown over the bank/card
    providers we have parsers for (SBI / ICICI / Axis / HDFC / Kotak /
    Yes Bank / Federal / Jupiter / Fi / Niyo + "Other").
  Sweep replaced every offending free-text input across Cards & EMIs,
  Budgets, Onboarding, Inbox review, and Manual entry.

### Changed (this release)

- **DUMP-REPLAY-REBASELINE** (PR #76) — tightened the regression
  harness's tolerance from 5 → 1 and added a separate `autoCreated`
  baseline so the S1.3 tier-promotion work has its own regression
  alarm. Numbers locked: 32 accepted / 6 AUTO_CREATED for v0.13.3 dump;
  14 accepted / 5 AUTO_CREATED for v0.14.0 dump.

### Released as

- `versionName = "0.15.0-alpha.1"`, `versionCode = 42`
- Git tag: `v0.15.0-alpha.1`
- Signed release APK: `rupee-0.15.0-alpha.1-release.apk`

### Known limitations (alpha is alpha, not 1.0)

- No SMS ingestion — only notifications. Sprint 11 (post-alpha).
- No new-phone restore — export buys backup, import is Sprint 13.
- Parser corpus is biased toward the pilot banks (ICICI / CRED / Kotak
  / PhonePe / Paytm / GPay). SBI YONO / Federal / Yes / Jupiter / Fi /
  Niyo bodies fall to the generic fallback. Sprint 6.
- The HIGH-confidence threshold may be too strict for some real-world
  bodies — Sprint 3's ALPHA-STAGE-ROLL will surface this.
- No "we caught this" notification yet. Sprint 5 NOTIF-CHANNELS.
- Six known code-quality items deferred to Sprint 4 (CAST-SAFETY,
  TRUST-WRITE-RACE, MONEY-MATH-LEGACY, DATE-PARSE-LOGGING,
  MIGRATION-SKIP-TEST, REPO-VM-TEST-COVERAGE). None are blockers for
  closed-tester usage; all have specific sprint homes.

See [`docs/rupee-backlog.md`](docs/rupee-backlog.md) for the full
post-alpha plan (Sprints 4 → 15, organised into four phases).

---

## [0.14.5] — 2026-05-22 — Sprint 2: polish on the existing theme

Closed across six PRs (#65 backlog rescope, #66 INBOX-WHY-COPY,
#67 CAL-INTER, #68 COLDSTART, #69 S1.3-rest, #70 MANUAL-FAST,
#71 POLISH-1). All flavor-agnostic — the Aviate-vs-vwfndr design call
moved to post-alpha as `REVAMP` in the backlog.

- **INBOX-WHY-COPY** — every Inbox row's reason chip now reads as
  user-facing copy (`AMOUNT ONLY`, `NO MERCHANT`, `DUPLICATE SUSPECTED`,
  `MERCHANT UNCLEAR`, etc.) instead of the enum echo. Pure helper at
  `home/InboxReasonCopy.kt` with 9 pin tests.
- **CAL-INTER** — horizontal-drag gesture on the Calendar's weekday
  header + grid fires `onPrev`/`onNext` past a 60dp threshold. Chevrons
  + bottom-sheet drag-dismiss both still work.
- **COLDSTART** — theme-native skeleton primitives at `ui/Skeletons.kt`
  (surfaceVariant pills + 1.8s alpha pulse) replace the empty-state
  copy that flashed during the brief `isSeeding == true` window on
  Home/Inbox/Transactions.
- **S1.3 (rest)** — remaining 8 parsers (Atm/Cred/Emi/GPay/Generic/
  Icici/Kotak/Paytm/PhonePe) migrated off hardcoded confidence ladders
  onto `EvidenceTally`. Per-parser weights tuned to preserve tier
  landings across the dump-replay corpus with three deliberate
  within-tier promotions.
- **MANUAL-FAST** — AssistChip row on the manual-entry sheet shows the
  5 most recent unique-merchant EXPENSE transactions; tap fills
  merchant + category + mode in one gesture.
- **POLISH-1** — content-card corner radius standardised at 24.dp
  across Home + Settings. WeeklySpend's static "Spent" pill becomes a
  primary-tinted income pill when monthly income > 0.

`versionCode = 41`, `versionName = "0.14.5"`.

---

## [0.14.4] — 2026-05-22 — Sprint 0: release blockers

Six items closed across six PRs (#58–#63). Pure correctness; no
features. All were release blockers the gravedigging audit had
deferred.

- **SEED-SERVICE** — replaced hardcoded seed IDs (`account-bank-1`,
  `card-1`, `account-cash`) and the March-2026-only budget with UUID
  generation + current-month budget. Fresh installs in any month now
  work.
- **CRED-ICICI-AUDIT** — reordered CRED parser's kind ladder so
  refund / reversal / chargeback bodies route to INCOME instead of
  SPEND. Added `refunded` / `reversed` / `reversal` / `chargeback` to
  `CREDIT_VERBS`.
- **L4** — removed `fallbackToDestructiveMigrationFrom(true, 1, 2, 3,
  4)` from `RupeeDatabase`. Anyone on v1–v4 is past the upgrade
  window; this was a data-loss risk in production.
- **KOTAK-VERB-DRIFT** — dropped the redundant per-parser
  `TRANSACTIONAL_VERBS` list from `KotakNotificationParser` (the
  central gate already filters verb-less bodies). The local list had
  drifted from the central one.
- **GATE-AUTOPAY-VOCAB** — added `autopay`, `payment to `, `payment
  for ` to `TransactionalGate.POSITIVE_VERBS` so GPay Autopay
  notifications (Spotify, Netflix, recurring SIP) clear the gate.
- **CRED-PROMO-BODY-GATE** — added cash-advance / credit-line negative
  keywords + widened the amount regex to handle Indian comma-grouping
  (`2,80,000`, `1,00,00,000`). The CRED Cash promo no longer ingests
  as a ₹2.00 SPEND. **Categorical fix** — every amount ≥ ₹1,00,000 is
  now parsed correctly across every parser.

`versionCode = 40`, `versionName = "0.14.4"`.

---

## [0.14.3] — 2026-05-21 — Sprint 1: utility wins

Four items across four PRs (#53–#56).

- **TRUST-FROM-TXN** — "Always trust this merchant" toggle added to
  the Transactions tab edit sheet. Users can now create trust rules
  after the fact, not just during Inbox confirm.
- **EXCL-FROM-SPEND** — Settings → Accounts screen with per-account
  hide-from-expense + hide-from-income toggles. The DB columns
  existed; UI was the gap.
- **S1.3 (pilot)** — Migrated `GenericUpiNotificationParser` to the
  new shared `EvidenceTally` helper as a pilot validation of the
  additive-tally pattern. Pattern validated; rest migrated in Sprint
  2's S1.3-rest.
- **EXPORT-UI** — Settings → Export entry that produces CSV or JSON
  of every transaction (joined with merchant/category/account names)
  and hands it to the share sheet.

`versionCode = 39`, `versionName = "0.14.3"`.

---

## Earlier history

For v0.14.0 (gravedigging audit), v0.14.1 (Inbox-husk hotfix),
v0.14.2 (ingestion health card), and everything before, see
[`CONTEXT.md`](CONTEXT.md) §"Current Implementation State". Those
entries were written before this CHANGELOG existed.

[0.15.0-alpha.1]: https://github.com/zegrt/rupee/releases/tag/v0.15.0-alpha.1
[0.14.5]: https://github.com/zegrt/rupee/releases/tag/v0.14.5
[0.14.4]: https://github.com/zegrt/rupee/releases/tag/v0.14.4
[0.14.3]: https://github.com/zegrt/rupee/releases/tag/v0.14.3
