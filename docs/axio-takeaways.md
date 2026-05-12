# Rupee × Axio — What to Steal, In Priority Order

**Date:** 2026-05-13
**Audience:** the single dev shipping Rupee
**Companion:** [`axio-competitor-analysis.md`](./axio-competitor-analysis.md), [`notification-ingestion-deep-dive.md`](./notification-ingestion-deep-dive.md)

## 1. The shortlist (TL;DR)

In priority order:

1. **JSON-driven, per-package rule engine** (Axio §3.3, §3.7) — replaces the eight hardcoded `*NotificationParser.kt` files. Ships *with* the `NotificationExtractor` refactor, because both presuppose a single `combinedBody` string.
2. **Chaining-style dedupe with `parent_selection`** (§3.6) — strict superset of our current `DedupeFingerprint`; keep the SHA-256 fingerprint as a fast first pass, run chaining as second pass.
3. **`networkReferenceId` + `networkReferenceType` columns on `parsed_signals` and `canonical_transactions`** (§3.4) — cheap, immediately enables cross-stream collision (HDFC bank ↔ CRED mirror), unlocks the chaining engine.
4. **`pattern_UID` + `obsolete` + `parserVersion` discipline** (§3.7) — so we can ship rule updates without breaking historical parses, and so Crashlytics-style failures point at a stable identifier.
5. **Refund linking with the 5 Axio strategies** (§3.8) — we have *zero* today, this is the most visible user-facing PFM correctness win after notification breadth.

Items 6–12 are real but defer-able. Items 10–11 are blocked on having a backend at all.

---

## 2. Per-item proposals

### Item 1 — JSON-driven rule engine

- **What Axio does (§3.3, §3.7):** `assets/rules.json` (2.4 MB, version 126) holds 2,284 patterns across 199 entities. Each pattern is `{ pattern_UID, sort_UID, regex, sms_type, account_type, data_fields, obsolete? }`. Dispatch is `sender → patterns[] sorted by sort_UID → first match wins`. OTA via `getLatestRules(app_version, rules_version)`.
- **What we have today:** hardcoded Kotlin classes — `CredNotificationParser.kt`, `IciciNotificationParser.kt`, `GPayNotificationParser.kt`, `PhonePeNotificationParser.kt`, `PaytmNotificationParser.kt`, `EmiNotificationParser.kt`, `GenericUpiNotificationParser.kt`, `GenericNotificationParser.kt`, dispatched by `NotificationParserRegistry.default()` first-match-wins. Each parser carries its own ad-hoc regexes. Adding a sender = shipping a Kotlin class and an APK.
- **What we'd change:**
  - New `assets/rupee-rules.json` keyed by `packageName` (our equivalent of sender). Shape: `{ version, blacklistRegex, packages: [{ packageName, displayName, patterns: [{ pattern_UID, sort_UID, regex, kind: SPEND|REFUND|BILL_DUE|EMI|STATEMENT|UNKNOWN, mode: UPI|CC|DC|…, dataFields: { amount: {groupId}, maskedDigits: {groupId}, merchant: {groupId|"after_at"}, networkRef: {groupId}, eventOccurredAt: {groupId, formats}, dueDate: {groupId, formats} }, posTypeRules?, transactionTypeRule?, chainingRule?, obsolete? }] }] }`.
  - New `ingestion/rules/` package: `RuleSet`, `Rule`, `DataFields`, `RuleLoader` (assets-first, OTA-overlay-second from `filesDir/rules.json`).
  - Replace `NotificationParserRegistry` with `RuleDrivenParser(ruleSet)` that produces `NotificationParseResult`. Keep `NotificationParser` interface as a thin shim during migration so the listener doesn't change shape.
  - Bad-regex behaviour: catch `PatternSyntaxException` per-rule at load time, mark rule as `disabled`, log once, continue. Never block the registry boot on a single bad rule.
  - Rules live in `assets/` for v1; add `RuleUpdater` interface scoped for a future backend but don't implement.
- **Why now:** the `NotificationExtractor` refactor (deep-dive §5) gives us `combinedBody`, which is what regex-based dispatch wants. Doing rule-engine *after* the extractor means two ingestion rewrites in a quarter; doing it *with* the extractor is one cohesive change.
- **Size:** **medium-large**. ~1,500 LoC delta. Rule-loader + dispatcher (~500), JSON schema + data classes (~200), migration of 8 parsers to JSON rules (~600), tests (~400). The Kotlin parsers go from ~1,200 LoC to ~50 LoC of glue.
- **Risk:** regression risk on existing covered providers (GPay/CRED/ICICI). Mitigate by porting one parser at a time behind a feature flag, running the existing `NotificationParserParseTest` corpus against both implementations, switching providers green-by-green. Don't delete the Kotlin parsers until the rule engine is at parity.

### Item 2 — Chaining model for dedupe

- **What Axio does (§3.6):** declarative `chaining_rule { parent_match, parent_selection: [{parent_field, child_field, match_type: exact|delta|none|contains, match_value}] }`. On chain, parent is enriched (unset `incomplete`/`deleted`), child marked deleted. Pattern UIDs cannot chain to siblings of the same pattern (so a re-arrival of the same SMS doesn't enrich itself).
- **What we have today:** `ingestion/NotificationDedupeEngine.kt` + `DedupeFingerprint.compute()` produces a SHA-256 of `(candidateType, amountMinor, currency, mode, maskedDigits, normalizedCounterparty, 5-min bucket)`. Checks the current and previous 5-min bucket. Fingerprint is a single equality key — strictly weaker than chaining because we cannot express "amount exact + masked exact + time within ±60 min" or "merchant fuzzy match".
- **What we'd change:**
  - Keep `DedupeFingerprint` as a fast first-pass hit (cheap O(1) index lookup, already covers most cases).
  - Add a second pass: `ChainingResolver` runs against pending `transaction_candidates` + recent `canonical_transactions` (last 24h) using rules declared in `rupee-rules.json` per package or as a global default. Rule shape mirrors Axio's `parent_selection`: list of `{parentField, childField, matchType, matchValue}`.
  - Schema add (small): `parent_event_id TEXT NULL` on `parsed_signals` and `canonical_transactions`. Set when a chain fires. Lets the inbox UI render "merged from 2 alerts" later.
  - The candidate flow becomes: parse → fingerprint match? (if yes: existing path) → chaining match? (if yes: enrich the parent, mark this signal `IGNORED` with `parent_event_id`, no new canonical txn) → otherwise create normally.
- **Why now (depends on):** depends on item 3 (`networkReferenceId`) being on canonical txns to be useful at full strength — chaining HDFC and CRED reliably needs the UPI ref. Without item 3, chaining still works via amount+masked+time delta but with more false negatives.
- **Size:** **medium**. ~400 LoC for the resolver + schema migration + 3 fields on the two entities. Tests are the bulk of the work.
- **Risk:** false-positive chains corrupt the canonical layer. Mitigate with: (a) only chain when ≥2 fields are `exact`, (b) only chain to candidates younger than 24h, (c) audit log every chain into `parent_event_id` so we can reverse them. Don't auto-delete the child; mark as IGNORED with provenance.

### Item 3 — `networkReferenceId` + `networkReferenceType`

- **What Axio does (§3.4):** First-class `networkReferenceId` + `networkReferenceType` (UPI/IMPS/NEFT/RTGS) columns on `Transaction`. Captured as a `data_field` from the regex.
- **What we have today:** Nothing. We throw away the UPI ref that every real notification contains (`UPI Ref 123456789012`).
- **What we'd change:**
  - Add `networkReferenceId TEXT NULL`, `networkReferenceType TEXT NULL` (enum: UPI, IMPS, NEFT, RTGS, CARD_AUTH) on `ParsedSignalEntity.kt` and `CanonicalTransactionEntity.kt`. Indexed on both.
  - Add capture to every existing parser (or, if item 1 is shipping in the same sprint, to the rule schema's `dataFields.networkRef`). Regex: `\b(UPI Ref(?:erence)?(?: No\.?)?|Ref(?:erence)? No\.?)\s*:?\s*(\d{8,16})\b` covers ~80% of bank phrasings; CRED, ICICI add `RRN \d{12}`.
  - Surface in transaction-detail bottom sheet as a small grey caption.
- **Why now:** trivially small, unblocks chaining (item 2), and every real notification already carries it — we are leaving evidence on the table. Bundle into the same PR as the extractor refactor.
- **Size:** **small**. ~150 LoC + Room migration + 8 parser regex additions (one line each).
- **Risk:** none material. Migration is additive nullable columns.

### Item 4 — `pattern_UID` + `obsolete` versioning

- **What Axio does (§3.7):** every pattern has a globally stable `pattern_UID` (Long). Retired patterns get `obsolete: true` but stay in the rulebook so historical SMS still resolve. `POST rules_hit` telemetry pings UIDs that fire.
- **What we have today:** `parserKey: String` + `parserVersion: String` on `ParsedSignalEntity` (per-parser, not per-rule). We can tell "GPay parser v1 fired" but not *which regex inside it* matched.
- **What we'd change:**
  - When rules move to JSON (item 1), each rule gets a `pattern_uid: Long` (~10000-99999 range, leave gaps). Write it onto `ParsedSignalEntity` as `patternUid: Long?` and `obsolete: Boolean` on the rule itself in JSON.
  - On rule update via OTA, never reuse UIDs; deprecate by setting `obsolete: true` and shipping the replacement with a new UID.
  - Surface in the Debug parser-playground screen so we can see exactly which UID matched a body.
- **Why now:** belongs in the same PR as item 1; introducing the column without the JSON rule engine is pointless.
- **Size:** **trivial** rolled into item 1. ~30 LoC of column add.
- **Risk:** none.

### Item 5 — Refund linking, 5 strategies

- **What Axio does (§3.8):** five named strategies — `HAS_POS+EXACT_AMOUNT`, `HAS_POS+POS_MATCH`, `NO_POS+EXACT_AMOUNT`, `USER`, `NOT_FOUND`. Exposes confirm/deny to user and reports the link back to the server (`report_missed_refund_link`, `report_refund_link_changed`).
- **What we have today:** `ParsedTransactionKind.REFUND` exists, but nothing links a refund to its originating debit. Refunds just appear as positive entries in the recent-activity list with no relationship.
- **What we'd change:**
  - Add `linkedTransactionId TEXT NULL` + `linkMethod TEXT NULL` (enum) to `CanonicalTransactionEntity.kt`. Indexed.
  - New `RefundLinker` in `ingestion/`. Runs after a candidate is parsed as `REFUND`. Looks back 90 days at non-refund transactions for the same merchant + exact amount (strategy 1), then merchant + ±5% amount (strategy 2), then no-merchant + exact amount (strategy 3). Annotates the canonical txn with the chosen `linkMethod`. If nothing found, `linkMethod = NOT_FOUND`.
  - Inbox surfaces refund rows with "Refund from {merchantName}" + link-target preview. Tap → modal to confirm/change/unlink (strategy `USER`).
  - Budget/spend queries treat linked refunds as offsets to the original txn's category, not income — fix the long-standing bug where refunds look like positive cash flow.
- **Why now:** notable user-facing improvement; doesn't depend on items 1–4 but composes naturally once `networkReferenceId` is captured (a refund with the same UPI ref as a debit is a free strategy-0).
- **Size:** **medium**. ~600 LoC: linker engine, schema migration, refund-detail sheet UI, budget query update, tests.
- **Risk:** spend totals shift retroactively when historic refunds get linked. Mitigate by running the linker only on new refund candidates; offer a one-shot "rebuild refund links" Debug button for backfill.

### Item 6 — Account "not an expense" / "not an income" flags

- **What Axio does (§5.9):** per-account toggle. Wallets/transit cards where you transfer money in (already counted at the source) shouldn't double-count as expense or income.
- **What we have today:** `AccountEntity.kt` has `isActive`, nothing else. `isHiddenFromBudget` exists on `CanonicalTransactionEntity` but as per-txn, not per-account.
- **What we'd change:** add `excludeFromExpenseTotals Boolean = false` and `excludeFromIncomeTotals Boolean = false` to `AccountEntity.kt`. Update `CanonicalTransactionDao.observeSpentInPeriod` SQL to `LEFT JOIN accounts ON accountId = accounts.id WHERE excludeFromExpenseTotals = 0`. Surface as a per-account toggle in Cards & EMIs / Accounts screen. Same for credit cards.
- **Size:** **small**. ~100 LoC.
- **Risk:** SUM queries on `spent_in_period` are the hot path; verify the join doesn't tank performance — should be fine with the existing accountId index.

### Item 7 — `incomplete` flag + missed-transaction detection

- **What Axio does (§3.9):** reconciles derived balance against transaction sum; balance jumps that don't match a transaction populate `walnutMissedTxns`. Daily alarm prompts user "you might have a missed transaction".
- **What we have today:** nothing. `AccountEntity.currentBalanceMinor` exists but is never updated from notifications.
- **What we'd change (honest assessment):** this is **partially blocked**. Step 1: build a balance-extraction parser strategy — most bank notifications carry `Avl Bal Rs X`/`Available limit Rs X`. Capture into a new `balanceAfterMinor Long?` column on `ParsedSignalEntity`. Step 2: keep a rolling `derivedBalanceMinor` on `AccountEntity` updated from the most recent balance signal. Step 3: when `derivedBalance - sum(transactions since last balance signal) ≠ latest balance`, flag the gap, prompt user. This is two sprints of work and depends on having the right regexes for balance lines (which the JSON rule engine makes easy).
- **Size:** **large** (~1000 LoC + UX). Defer behind items 1–5.
- **Risk:** false alarms erode trust faster than missed transactions do. Set the threshold high (gap > ₹100) and make the UX a soft "did we miss this?" not an alert.

### Item 8 — Event `sms_type` for non-finance reminders

- **What Axio does (§3.10):** events table holds flights, movies, taxis. Same parser pipeline, separate sink. Adds a "What's coming up" surface.
- **What we have today:** nothing. `ParsedTransactionKind` only has finance kinds.
- **What we'd change:** add `EVENT` to `ParsedTransactionKind`, plus a new `EventEntity` with `kind` (FLIGHT/MOVIE/TAXI/TRAIN), `occursAt`, `details`, `reminderMinutesBefore`. Add a few patterns to the rule engine (IRCTC, BookMyShow, Uber). Surface as a slim card on Home or a new tab.
- **Why later:** scope-creep risk; not what we're selling. Worth ~1 week if user research signals demand.
- **Size:** **medium** (~500 LoC, mostly UI).
- **Risk:** low; isolated feature, easy to remove.

### Item 9 — Kirana account concept

- **What Axio does (§3.4 taxonomy):** `KIRANA_PERSONAL=11`, `KIRANA_BUSINESS=12` — informal neighborhood-store credit ledger.
- **What we have today:** nothing. AccountType is BANK or CASH.
- **What we'd change:** add `KIRANA` to `AccountType`. Manual-entry only (no parser). UI: small section in Accounts screen, "credit owed at {store}". Settle button moves cash → kirana entry.
- **Why:** Indian-specific; differentiator. But genuinely niche.
- **Size:** **small** (~250 LoC).
- **Risk:** if no one uses it, dead code. Ship only after user signal.

### Items 10–11 — Server-driven home cards & pattern telemetry

Both require a backend we don't have and don't plan to build for v1. **Defer indefinitely.** Do *not* design the schema speculatively — every backend-less feature designed in advance has rotted in this codebase before. The day we have a backend, design these against real auth and a real CMS, not a paper schema.

### Item 12 — Two-pass classification (`pos_type_rules` / `transaction_type_rule`)

- **What Axio does (§3.3 data_fields):** captured groups in the regex are re-run through small sub-rule tables for category + type refinement. "If captured merchant matches `(?i)(salary|sal credit)` → category = salary, type = INCOME". Baked into the parser layer.
- **What we have today:** `NotificationDecisionEngine` decides confidence tier (auto-create vs inbox vs ignore) but not category. Category is null at parse time and gets assigned by user in the inbox.
- **What we'd change:** in the JSON rule schema (item 1), each rule's `dataFields` gets optional `pos_type_rules: [{ regex, category, type, mode }]`. The rule engine runs them in order against the captured `pos`/`merchantRaw` and emits a `suggestedCategoryId` on `NotificationParseResult`. Decision engine treats a suggested category as a confidence-bumper (closer to auto-create).
- **Why later:** real lift, but only meaningful once item 1 ships. Save for sprint 3.
- **Size:** **small** layered on item 1 (~150 LoC).
- **Risk:** wrong category auto-applied silently. Keep the user-override hierarchy: trust rule > user inbox edit > rule-engine suggestion.

---

## 3. What we should not lift

- **SMS permission.** Play Store policy. Already decided.
- **5-product surface (BNPL/PL/LOC/FD).** We're a tracker.
- **Two crash reporters (Crashlytics + Sentry), two databases (SQLite + Room), two UI frameworks (XML + Compose).** Tech debt born of an acquisition.
- **`READ_PHONE_STATE`, `BLUETOOTH`, `LOCAL_MAC_ADDRESS`, `ACCESS_BACKGROUND_LOCATION`, `SEND_SMS`.** All justified by lending; none by tracking.
- **Server-side report generation (`generate_report_v2`).** No backend.
- **Server-driven content webviews (`content.axio.live`).** Same.
- **Truecaller SDK, AppsFlyer, CleverTap, FCM, Firebase Auth/RTDB/Storage/Dynamic Links.** Single-user local-first product doesn't need them.
- **Backup-as-opaque-blob.** Axio's users complain on Play; we should ship CSV/JSON export instead.
- **Global blacklist regex (§6 weakness).** Move blacklist *into* the rule schema as a per-package optional pre-filter so we don't silently drop a legitimate "activation complete" type from a future bank.

---

## 4. Integration with the notification-extractor refactor

The deep-dive (§5–§6) lands `NotificationExtractor` + `combinedBody`. The Axio-ish items split into three groups by dependency:

**Same PR as the extractor (sprint 1):**
- Item 4 (`pattern_UID`/`parserVersion` columns) — column add only, no rule engine yet.
- Item 6 (`networkReferenceId`) — column add + one regex line per existing Kotlin parser.
- Item 7 (account "not an expense" flag) — independent schema add; fits without conflict.

**Immediately after (sprint 2):**
- Item 1 (JSON rule engine) — needs `combinedBody` to exist, but does *not* need to ship in the same PR. Cleaner to land extractor first, then port providers one at a time.
- Item 2 (chaining dedupe) — needs item 6 to be at full strength.

**Later (sprint 3+):**
- Item 5 (refund linking) — orthogonal to extractor; ship anytime once items 1+6 are in.
- Item 12 (two-pass classification) — only meaningful after item 1.

**Deferred:**
- Items 8 (missed-txn / balance), 9 (events), 10 (Kirana), 11/13 (server-driven).

---

## 5. Sequenced 90-day roadmap

| Sprint | Window | Items | Risk |
|---|---|---|---|
| **1** | Now → +2 wk | `NotificationExtractor` + `combinedBody` (deep-dive §9); rides along: item 4 (column add), item 6 (networkRef), item 7 (account expense flag). | Low. Mostly additive. |
| **2** | +2 → +4 wk | Item 1 (JSON rule engine) — port GPay/CRED/ICICI first behind flag, then PhonePe/Paytm/EMI/GenericUpi/Generic. Item 2 (chaining dedupe) layered in second half. | Medium. Parse regression risk; corpus tests are the safety net. |
| **3** | +4 → +6 wk | Item 5 (refund linking, full 5 strategies + UX). Item 12 (two-pass classification) — extends item 1's rule schema. | Low–medium. Refund UX needs design. |
| **4** | +6 → +8 wk | Item 8 (balance + missed-txn) — *partial*. Land balance extraction into `ParsedSignalEntity` and update `AccountEntity.derivedBalanceMinor`; skip the daily-alarm prompt for now (UX risk too high without real-user testing). | High. Honest call: balance reconciliation across 8 banks is hard and we don't have the corpus yet. |
| **5+** | +8 → +12 wk | Item 9 (events) if a user has asked, item 10 (Kirana) if a user has asked. Otherwise polish + parser-coverage chasing. | n/a. |
| **Deferred** | — | Items 11 (server cards), 13 (telemetry). Revisit only after a backend exists. | — |

This sequencing trades item 5 (high user-visible win) being later than items 1–2 (parser-correctness wins) because items 1–2 *make* item 5 easier to write. Don't invert.

---

## 6. Open questions

- **`data_fields` field types.** Axio's schema includes `date{group_id, formats[], use_sms_time}` and `pos{group_id, pos_info?}`. The `formats` array suggests a fallback list of `SimpleDateFormat` patterns. Our `eventOccurredAt` is just an ISO string today — fine for parsers that extract a date, but we'll need a formats array on the rule schema for ports of senders that use ambiguous formats (`12/05`, `12-May`, `12-May-26`). Will need a small `DateExtractor` helper.
- **Cross-package chaining.** Can an HDFC SMS chain with a CRED notification of the same charge? Axio's chaining is within a sender (the `parent_selection` operates on `walnutSms` filtered by `sender_UID`). For us the analogue is "within a package", but the high-value use case is *cross-package* (CRED mirrors a bank charge). Two options: (a) when running the second-pass chaining, ignore the package boundary for fields that are globally unique (`networkReferenceId`, `maskedDigits + amount + ±60min`); (b) introduce a `chainAcrossPackages: true` flag on individual chaining rules. Recommend (a) — simpler, and `networkReferenceId` is globally unique by design.
- **UID collisions on OTA.** Pattern UIDs need to be globally unique across the JSON file. If we ship rules in-app and OTA later, a stale build receiving an OTA whose UID was reused locally would corrupt history. Treatment: reserve `1..99999` for in-app rules and `>=1_000_000` for OTA-only rules; OTA never reuses an in-app UID. Bake into `RuleLoader` as an assertion at load time.
- **`obsolete: true` and historical signals.** Axio retains obsolete patterns so old SMS still resolve. We don't store the raw notification body forever (well — we do, in `raw_capture_events`) but we *do* store `parserKey` + `parserVersion` on `parsed_signals`. After moving to UIDs: do we ever *replay* signals against new rules? Recommend: no, by default. Old `ParsedSignalEntity` rows keep their original `patternUid`; only new captures hit new rules. Replays are a debug-only feature.
- **Where do rules live?** Assets-bundled for v1 is the only option without a backend. The day we get a backend, `RuleUpdater` polls and overlays a `filesDir/rules.json`; `RuleLoader` is already designed for an asset-base + overlay merge by ascending priority.
