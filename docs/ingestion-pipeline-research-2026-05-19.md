# Ingestion Pipeline Research — v0.14.0 Nothing-A015 Dump (2026-05-19)

Source dumps:
- `dumps/rupee-notif-dumps-0.14.0-Nothing-A015-20260519-202444.jsonl` (916 raw lines)
- `dumps/rupee-notif-outcomes-0.14.0-Nothing-A015-20260519-202444.jsonl` (738 outcome snapshots)

Outcome distribution for the day:

| outcome                              | count |
|--------------------------------------|------:|
| `gate_rejected / NO_TRANSACTIONAL_VERB` | 721 |
| `filtered / GROUP_SUMMARY`           | 169   |
| `gate_rejected / PROMO_KEYWORD`      |  11   |
| `filtered / INGEST_FAILED`           |   7   |
| `ingested / IGNORED`                 |   6   |
| `filtered / RAW_DUPLICATE`           |   2   |

Zero transactions reached the user — every "ingested" row landed in `IGNORED`, every signal-bearing notification that *would* have routed to Inbox crashed before reaching the candidate write, and the half-dozen Truecaller-mirrored bodies that survived parsing all evaded dedupe and re-fired as new IGNORED candidates.

---

## 1. Executive Summary

The pipeline regressed yesterday, on the same calendar day this dump was captured. Commit `1dff869` ("H4: declare foreign keys + cascade deletes on the ingestion chain", May 19 00:07) added `ForeignKey(... onDelete = CASCADE)` from `inbox_items.transactionCandidateId → transaction_candidates.id`. The normalizer's write order, however, is **inbox_item first, candidate second** (`NotificationSignalNormalizer.kt:188-239`). Every notification routed to `INBOX_PENDING` now hits `SQLiteConstraintException` on the inbox insert (the parent candidate row doesn't exist yet), which `runCatching` in the listener (`RupeeNotificationListenerService.kt:93-104`) swallows and labels `INGEST_FAILED`. **All 7 INGEST_FAILED notifications in this dump are real money signals lost to a write-order bug.**

The 6 rows that *did* survive ingestion landed in `IGNORED` for two reasons: (a) Truecaller-mirrored Kotak debits with title-only bodies fall through to `GenericNotificationParser` at confidence 0.55 (LOW → IGNORED), and (b) ICICI statement-mailed-to alerts parsed as UNKNOWN/MISSING_AMOUNT. None of these survived for *dedupe* either: `TransactionCandidateDao.getLatestUsableByFingerprint` filters `decisionState != 'IGNORED'`, so identical IGNORED candidates never see each other and each new mirror writes a fresh row.

Layered on top: title-carries-data shapes from Nothing/OneUI banks expose **gate vocab drift** ("sent from" missing from `POSITIVE_VERBS`, killing real Kotak811 debits at the gate) and silent regex non-matches in parsers that assume body has structured text.

The fix order is unambiguous: **(1)** reverse the inbox/candidate write order or defer the FK, **(2)** broaden dedupe to include IGNORED rows in lookup, **(3)** add `"sent from"` (and the title-only debit family) to `POSITIVE_VERBS`, **(4)** stop swallowing exception types in the listener so future regressions don't require dump-spelunking to find.

---

## 2. Topic A — `INGEST_FAILED` Root Cause

### 2.1 The crash site

`NotificationSignalNormalizer.normalizeLocked` writes the inbox row before the candidate:

```kotlin
// NotificationSignalNormalizer.kt:188-198
val inboxItemId = if (decision.decisionState == CandidateDecisionState.INBOX_PENDING) {
    createInboxItem(
        userId = rawEvent.userId,
        transactionCandidateId = candidateId,   // <— FK pointer
        reasonCode = toInboxReasonCode(decision.decisionReason),
        now = now,
    )
} else null
...
// NotificationSignalNormalizer.kt:239
database.transactionCandidateDao().upsertTransactionCandidate(candidate)   // <— parent inserted AFTER
```

`InboxItemEntity` (lines 41-54) declares:

```kotlin
ForeignKey(
    entity = TransactionCandidateEntity::class,
    parentColumns = ["id"],
    childColumns = ["transactionCandidateId"],
    onDelete = ForeignKey.CASCADE,
),
```

This FK was added by commit `1dff869` ("H4: declare foreign keys + cascade deletes on the ingestion chain") on **2026-05-19 00:07** — the same day the dump was captured. Room enables `PRAGMA foreign_keys = ON` by default. Before H4 the write order was harmless ordering; after H4 it is a deferred-constraint-violation-at-statement-time.

SQLite's default behaviour with `PRAGMA foreign_keys = ON` (no `DEFERRABLE INITIALLY DEFERRED` clause, which Room does not emit) is to enforce the FK at the moment the child row is inserted. The candidate doesn't exist yet, so the inbox insert raises `SQLiteConstraintException: FOREIGN KEY constraint failed`. That exception unwinds out of `withTransaction { ... }` (rolling back both the raw event and the parsed_signal upserts) and is caught here:

```kotlin
// RupeeNotificationListenerService.kt:92-104
serviceScope.launch {
    val outcome = runCatching {
        normalizer.ingestNotification(...)
    }.getOrElse { t ->
        Log.e(TAG, "Failed to ingest notification from ${sbn.packageName}", t)
        IngestionResult.Filtered(IngestionResult.FilterReason.INGEST_FAILED)
    }
    ...
}
```

The exception type and stack trace are logged to logcat but never surface in the dump file — the dump only records `INGEST_FAILED`. That is why this regression survived the entire day undetected.

### 2.2 Why Truecaller-ICICI fails but Truecaller-Kotak doesn't

Both Truecaller bodies are title-only (the SMS mirror puts the whole bank SMS in `EXTRA_TITLE`, with `EXTRA_TEXT` empty and `subText="SMS from ICICI Bank"` or null).

**Truecaller-Kotak** (`Sent Rs.14.00 from Kotak Bank AC X4129.`)
- `IciciNotificationParser.canParse` → false (no "icici" in pkg/title/body).
- `KotakNotificationParser.canParse` → false (`pkg = com.truecaller`, doesn't contain "kotak"; `IciciNotificationParser.kt:30-31`).
- `GenericUpiNotificationParser.canParse` → false (no "upi").
- Falls through to `GenericNotificationParser` (`NotificationParserRegistry.kt:9` — registry's `firstOrNull` then default).
- `amountMinor = 1400`, `maskedDigits = null` (regex `[*xX]{2,}\s*(\d{4})` needs ≥2 X's; "X4129" has one), `merchant = null` (no "to X" / "at X" anchor in body).
- Confidence: `amountMinor != null && merchant == null && maskedDigits == null` → `0.55` (`GenericNotificationParser.kt:25-30`, third branch).
- `NotificationDecisionEngine.decide`: `0.55 < MEDIUM_CONFIDENCE (0.6)` → `LOW` tier → `IGNORED` (`NotificationDecisionEngine.kt:58-62`).
- `decisionState == IGNORED` → `createInboxItem` is skipped → no FK violation → candidate write succeeds → `IngestionResult.Ingested(decisionState=IGNORED)`. **Survives.**

**Truecaller-ICICI** (`ICICI Card XX3001 debited for INR 422.00.` + subText "SMS from ICICI Bank")
- `IciciNotificationParser.canParse` → true (title contains "icici", `IciciNotificationParser.kt:14-16`).
- `amountMinor = 42200` (₹422.00 via `NotificationParsingUtils.kt:37-41`).
- `maskedDigits = "3001"` — regex `(?:xx|xx\s*card|card|a/c|account|ending)\D{0,12}(\d{4})` matches "Card XX3001" with `card` alternation + `\D{0,12}` swallowing " XX" + `(\d{4})` capturing "3001".
- `transactionKind = SPEND` (body has "debited", `IciciNotificationParser.kt:35-44`).
- `merchantRaw = null` (no "at/on/to X" anchor).
- Confidence ladder (`IciciNotificationParser.kt:79-92`): falls into the `transactionKind != UNKNOWN && amountMinor != null` branch → `0.68`.
- `NotificationDecisionEngine.decide`: `0.68 ≥ 0.6` → `MEDIUM`; `isSpendLike` true → `INBOX_PENDING` (`NotificationDecisionEngine.kt:46-57`).
- `decisionState == INBOX_PENDING` → `createInboxItem` runs → **FK violation → caught → `INGEST_FAILED`.**

That's the entire delta: the Kotak body is *just* poor enough to slip below the MEDIUM threshold and skip inbox creation; the ICICI body is good enough to trigger inbox creation, which is what trips the FK.

### 2.3 The full INGEST_FAILED roster (all 6 distinct shapes)

| Pkg | Title | Body | Parser | Path |
|---|---|---|---|---|
| `com.daamitt.walnut.app` | `₹422.00 at BISTRO` | _empty_ | Generic | amount=42200, merchant="BISTRO" → 0.62 MEDIUM → INBOX_PENDING → FK crash |
| `com.truecaller` ×3 | `ICICI Card XX3001 debited for INR 422.00.` | _empty_ | ICICI | 0.68 MEDIUM SPEND → INBOX_PENDING → FK crash |
| `com.daamitt.walnut.app` | `₹14.00 at MOHAMMEDFAHIM8824@YAPL` | _empty_ | Generic | amount=1400, merchant="MOHAMMEDFAHIM8824@YAPL" → 0.62 MEDIUM → INBOX_PENDING → FK crash |
| `com.daamitt.walnut.app` | `New Bill : ICICI credit (3001) ₹80.00` | `Due on 30'May (11d)` | ICICI | amount=8000; " credit " hits CREDIT_VERBS → kind=INCOME → 0.68 MEDIUM → INBOX_PENDING (isIncome branch in `NotificationDecisionEngine.kt:43-46`) → FK crash |
| `com.dreamplug.androidapp` | `₹861 paid to Swiggy Dineout` | `your UPI payment to Swiggy Dineout was successful...` | CRED | amount=86100; "payment successful" → PAYMENT (`CredNotificationParser.kt:39-44`); kind=PAYMENT/type=TRANSFER not isSpendLike → falls to `NON_SPEND_REVIEW` → INBOX_PENDING → FK crash |

Every shape crashes for the same structural reason: the moment any parser produces enough signal to route to `INBOX_PENDING`, the inbox-first/candidate-second order violates the FK introduced 22 hours earlier.

### 2.4 Repro recipe

In any Room-backed test or fresh DB:

```kotlin
val raw = rawCaptureEvent(body = "ICICI Card XX3001 debited for INR 422.00.\nSMS from ICICI Bank")
NotificationSignalNormalizer(db).ingestNotification(
    userId = "u", packageName = "com.truecaller",
    title = "ICICI Card XX3001 debited for INR 422.00.", body = raw.body, postedAtMillis = System.currentTimeMillis(),
)
// → IngestionResult.Filtered(INGEST_FAILED)
// Logcat: android.database.sqlite.SQLiteConstraintException: FOREIGN KEY constraint failed
```

To confirm: temporarily swap the order in `NotificationSignalNormalizer.kt` so `upsertTransactionCandidate` runs before `createInboxItem`, and the same body lands in `INBOX_PENDING` with no exception.

### 2.5 The minimal fix

Two viable options:

1. **Re-order**: write the candidate first (without `linkedInboxItemId`), then create the inbox item, then update the candidate row with `linkedInboxItemId`. Costs an extra UPDATE per medium-confidence notification but avoids FK redesign.
2. **Defer**: drop the CASCADE on `inbox_items.transactionCandidateId` (it was added "for safety" in H4 but no production code actually depends on it — inbox rows are pruned by their own lifecycle), OR mark the FK as `DEFERRABLE INITIALLY DEFERRED` (Room doesn't expose this cleanly; would need a raw `CREATE TABLE` migration).

Re-order is the right answer — keeps the FK as documented invariant, doesn't touch schema, takes ~10 lines.

---

## 3. Topic B — Dedupe Silently Failing

### 3.1 Fingerprint derivation

`DedupeFingerprint.compute` (`NotificationDedupeEngine.kt:29-35`) and `forBucket` (37-55):

```
sha256(
  candidateType.name | amountMinor | currencyCode | mode.name | maskedDigits |
  normalizedCounterparty | timeBucketIso
)
```

The time bucket is `epochMinute - (epochMinute % 5)`, both current and previous buckets are queried (so the boundary issue from v0.12 is genuinely fixed).

For Group 1 (Truecaller-Kotak ×3 within 3.1s):
- `candidateType = SPEND`
- `amountMinor = 1400`
- `currencyCode = "INR"`
- `mode = null` → `""` in fingerprint
- `maskedDigits = null` → `""`
- `normalizedCounterparty = ""` (both `toEntityName` and `merchantRaw` are null)
- `timeBucket` is identical for all three

The three SHA-256 inputs are **byte-identical**. The fingerprints *are* the same. The fingerprint is not the bug.

For Group 2 (Truecaller-ICICI-Statement ×3 within 1.1s):
- `candidateType = UNKNOWN`
- `amountMinor = null` → `""`
- `currencyCode = null` → `""`
- `mode = CREDIT_CARD`
- `maskedDigits = "3001"`
- `normalizedCounterparty = "cy"` (the email prefix `cy****p1@gmail.com` → "cy" via the merchant regex grabbing "cy" from "sent to cy****…", cleaned)
- `timeBucket` identical

Also byte-identical. Fingerprint not the bug.

### 3.2 The actual bug: the DAO lookup filters out IGNORED

`TransactionCandidateDao.getLatestUsableByFingerprint` (`TransactionCandidateDao.kt:18-31`):

```sql
SELECT * FROM transaction_candidates
WHERE userId = :userId
  AND candidateFingerprint = :fingerprint
  AND decisionState != 'IGNORED'
ORDER BY createdAt DESC
LIMIT 1
```

The intent (commented in H3 prune work) is: "don't dedupe a real candidate against a previously-ignored one; the IGNORED row is a tombstone we should ignore." That intent is right for *cross-decision* dedupe but wrong for *intra-stream* dedupe: when three identical IGNORED rows arrive in 3 seconds, each one's lookup returns null (the prior IGNORED rows are filtered out), so each one writes a fresh IGNORED candidate.

This is the dump's exact pattern. The three Kotak Truecaller rows all parsed to LOW/IGNORED. The three ICICI-Statement rows all parsed to UNKNOWN→MISSING_AMOUNT→IGNORED. Six writes, six new candidates, three duplicate pairs across the day, all unreferenced by any UI surface (`decisionState=IGNORED` is filtered everywhere) but slowly hollowing out the recent-candidates rolling window (the Inbox-husk failure mode from gravedigging).

### 3.3 Why this is the only failure mode (not fingerprint randomness)

Hypotheses to dismiss:

- *Random component in fingerprint*: ruled out — all six inputs are deterministic strings (`NotificationDedupeEngine.kt:46-53`).
- *Time-bucket boundary*: ruled out — engine queries both current AND previous bucket (`NotificationDedupeEngine.kt:72-77`).
- *Null `hashCode` quirks*: ruled out — fingerprint uses `.orEmpty()` everywhere; nulls become `""` strings, fully deterministic.
- *Dedupe doesn't run for IGNORED-bound candidates*: ruled out — `dedupeEngine.detect` runs unconditionally in `normalizeLocked` (`NotificationSignalNormalizer.kt:130`). The lookup is the filter, not the dispatch.

### 3.4 The minimal fix

Drop the `decisionState != 'IGNORED'` predicate from `getLatestUsableByFingerprint`, OR split into two DAO methods: one that returns "any candidate" (used for intra-stream dedupe — if we've seen the same fingerprint in this 5-min bucket, mark this one as a dupe regardless of the prior's decision) and one that returns "usable candidate" (used for `dedupedAgainstCanonicalTxnId` lookup against canonical txns — that one really does want to skip tombstones).

Even simpler: keep one method but add a secondary lookup against `parsed_signals` by raw fingerprint hash within the bucket — a parsed-signal exists for every notification (including IGNORED), so the existence check is monotonic.

---

## 4. Topic C — Title-vs-Body Fragility

### 4.1 Combined-body order is title-first

`NotificationExtractor.extract` (`NotificationExtractor.kt:86-115`) builds `combinedBody` from a `LinkedHashSet` whose first inserted member is always `title`, then `titleBig`, `bigText`, `text`, `subText`, etc., then `textLines`, then `messages`, then `actionLabels`, then a sweep of any other CharSequence in extras. So a title-only body becomes a combinedBody where the title is the only line. For empty-body cases the combinedBody is the title verbatim.

### 4.2 Failure cases observed

| Body | Parser behaviour | Outcome |
|---|---|---|
| `T="₹14.00 sent from XX4129"` `B="Low balance! Add funds for seamless payment"` (Kotak811) | Gate rejects: NO_TRANSACTIONAL_VERB. `"sent from"` is not in `POSITIVE_VERBS` (`TransactionalGate.kt:64-94`). The verb list has `"sent via"`, `"sent to"`, `"sent rs"`, `"sent inr"` but not `"sent from"`. | Real transaction silently dropped at gate. |
| `T="Sent Rs.14.00 from Kotak Bank AC X4129."` (Truecaller mirror of the same SMS) | Has `"sent rs"` → gate passes. But Kotak parser canParse requires `"kotak" in pkg` (`KotakNotificationParser.kt:31-32`) and Truecaller's package isn't Kotak → falls to Generic. Generic's masked-digits regex needs `[*xX]{2,}` (two+ X chars), but the SMS form is "X4129" with one X → maskedDigits=null. Generic merchant regex needs "to X" / "at X" — body has "from Kotak Bank" — no match → merchant=null. Confidence=0.55 → IGNORED. | Real transaction silently demoted to IGNORED. |
| `T="ICICI Card XX3001 debited for INR 422.00."` (Truecaller mirror) | ICICI parser picks it up; gets amount + digits + SPEND verb. But merchant regex `(?:at|on|to)\s+([A-Za-z0-9 .&'_-]{2,50})` has no "at/on/to <merchant>" anchor in this SMS shape — merchant=null. Drops it to 0.68 → INBOX_PENDING → FK crash. | INGEST_FAILED on top of merchant-shape miss. |
| `T="₹422.00 at BISTRO"` (Walnut) | Generic parser succeeds (amount + merchant via "at BISTRO"). MEDIUM → INBOX_PENDING → FK crash. | INGEST_FAILED. |

### 4.3 Structural failure modes the title-only shape exposes

1. **Masked-digit regex over-specifies**: the `[*xX]{2,}\s*(\d{4})` form expects ATM-style masking; SMS bodies commonly use a single-X form ("X4129"). Workaround: lower the requirement to `[*xX]+` (one or more) OR add a token-based regex `\bA[/.]?[Cc]\D{0,8}X*(\d{4})\b` covering "A/c X4129", "AC X4129", "A/C 4129".
2. **Merchant regex assumes prefix anchor**: parsers using `(?:to|at|on)\s+([A-Za-z0-9 .&'_-]{2,50})` (Generic, ICICI, CRED, Phonepe, Paytm, GPay, GenericUpi — every payment parser except Kotak/EMI/ATM) miss bodies where the merchant is in the title before any anchor word. For "₹14.00 at MOHAMMEDFAHIM8824@YAPL" it happens to work, but for "Sent Rs.14.00 from Kotak Bank AC X4129" → no "to/at/on" → no merchant.
3. **Bank-parser canParse over-trusts package name**: Kotak parser requires `"kotak" in pkg` even though the bank text is *unambiguous* in title ("from Kotak Bank AC"). When the SMS is mirrored through Truecaller (a common Indian-Android pattern; Truecaller asks SMS permission specifically to bridge bank notifications), the package check fails and the parser is skipped.
4. **subText carries context that pollutes parsing without enriching it**: "SMS from ICICI Bank" in subText is appended to combinedBody, which is fine for *gating* (it preserves "ICICI" mention for the canParse) but is noise for *regex extraction* — masked-digit, merchant, and direction regexes all run against the polluted string.

### 4.4 Test-net for order sensitivity

Spot-check: regexes used here use `.find()` (first match) not `.matches()` (whole-string), so order matters per-regex *only* if the body contains conflicting matches. The ICICI body "ICICI Card XX3001 debited for INR 422.00.\nSMS from ICICI Bank" has only one amount, one masked-digit, no merchant anchor — first-match-wins isn't problematic here. The ordering issue is *between* parsers, not within them: `NotificationParserRegistry.parsers.firstOrNull { it.canParse(rawEvent) }` (`NotificationParserRegistry.kt:9`) means a low-quality `canParse` win locks out a better parser. Concrete case: Truecaller-Kotak body matches GenericNotificationParser's `canParse=true` (always-true tail of registry), but a "trying every parser, picking the highest-confidence result" loop would have a Kotak-shaped Generic-fallback parser route it as bank-debit with maskedDigits via a Kotak-tuned regex.

---

## 5. Topic D — Gate Vocab Drift + Structural Fix

### 5.1 New vocab the dump surfaced

| Shape | Status |
|---|---|
| `"sent from"` (Kotak811 native debit, title-only) | **Missing from POSITIVE_VERBS** — real debit gate-rejected as NO_TRANSACTIONAL_VERB |
| `"used for"` ICICI card form already covered by "used for a transaction" (`TransactionalGate.kt:74`) — but if Nothing/OneUI shortens to "used for INR 422" without "a transaction" suffix, it would fail; not seen in this dump but a known ICICI variant per docs |
| OTP-as-footer (`" otp,"`/`" otp."`) | None observed in this dump's gate-rejects to be a real transaction; the OTP keyword family fires correctly. But the negative-keyword list runs FIRST and short-circuits — see 5.2. |

### 5.2 Negative-first short-circuit is structurally dangerous

`TransactionalGate.evaluate` (`TransactionalGate.kt:42-56`):

```kotlin
NEGATIVE_KEYWORDS.firstOrNull { it in lower }?.let { matched ->
    return Decision.Reject(RejectReason.PROMO_KEYWORD, matched)
}
val verb = POSITIVE_VERBS.firstOrNull { it in lower }
    ?: return Decision.Reject(RejectReason.NO_TRANSACTIONAL_VERB, "")
return Decision.Accept
```

Negative matches are **pre-emptive**: a single negative kills the body regardless of how many positives are present. Today's risk surface:

- `" otp."` would pre-empt a body like `"₹500 debited from A/c XX1234. Don't share OTP."` — the trailing footer is universal bank boilerplate. *Not observed in this dump* but documented in `notification-ingestion-deep-dive.md` as a known shape.
- `"valid till"` would pre-empt `"₹500 cashback credited, valid till 31-Dec-26"` — a real refund/cashback notification.
- `" p.a."` would pre-empt any FD-interest-credit notification that mentions the rate ("Rs.500 interest credited @ 6.8% p.a.").
- `"up to ₹"` already fires once in this dump — need to verify whether it pre-empted a real txn or only a marketing line.

### 5.3 Structural fix: scored, title-weighted gate with overrides

Replace the boolean gate with a confidence score:

```
score = (positive_hits in title) * W_TITLE_POS
      + (positive_hits in body)  * W_BODY_POS
      - (negative_hits in title) * W_TITLE_NEG
      - (negative_hits in body)  * W_BODY_NEG
+ override_if(title_only_amount_shape)
```

With `W_TITLE_POS > W_BODY_NEG` (positive in title outweighs negative in body) — captures the title-carries-real-signal pattern, lets footer boilerplate ("Don't share OTP") cohabit with a real debit verb in the same title. The override is the load-bearing piece: if the title alone is a money-shape (`/[₹]|rs\.?|inr/i` + numeric amount), accept regardless of negative keywords, since the title is the *authoritative* surface in OneUI-style notifications.

### 5.4 JSON rule engine (S2 backlog)

Minimal viable shape:

```json
{
  "version": 1,
  "rules": [
    {
      "id": "kotak811_title_debit",
      "match": {
        "package_glob": ["com.kotak811*"],
        "title_regex": "^₹[\\d,]+(\\.\\d+)? (sent|paid|debited)( from| to| via)?",
        "min_amount_minor": 100
      },
      "classify": {
        "kind": "SPEND",
        "candidate_type": "SPEND",
        "mode": "UPI",
        "confidence": 0.85
      },
      "extract": {
        "amount_minor": { "regex": "₹([\\d,]+(?:\\.\\d+)?)", "source": "title" },
        "masked_digits": { "regex": "X(\\d{4})\\b", "source": "title" }
      }
    }
  ]
}
```

Plus a default rule that mirrors today's `TransactionalGate` + `GenericNotificationParser` chain. The engine: (1) loads rules from `assets/` at build time and from a remote URL+SHA-pinned blob at boot (OTA-loadable), (2) iterates rules in declared order, (3) the first matching rule's `classify` + `extract` block produces a `NotificationParseResult` directly. Each rule has a `confidence` baseline; the existing decision engine consumes that without change. No more handwritten parsers per provider.

---

## 6. Topic E — The Class of Issue

Five recurring shapes, five structural fixes:

### 6.1 Swallowed exception types

**Sites:**
- `RupeeNotificationListenerService.kt:93-104` — `runCatching {} .getOrElse { Filtered(INGEST_FAILED) }` — loses exception type, message, stack
- `NotificationExtractor.kt:34-40` — `runCatching { MessagingStyle ... }.getOrDefault(emptyList())` — fine here since the fallback is information-preserving
- `NotificationDumper.kt:52-67` — `runCatching {}` around dump writing — fine, dump failure is non-fatal by design

**Structural fix**: introduce a `CrashReporter.recordIngestionFailure(rawEvent, throwable)` API (the `CrashReporter` class already exists at `diagnostics/CrashReporter.kt`). Have the listener call it inside `getOrElse`. The reporter writes the exception class + first 3 stack frames + the rawEvent fingerprint into the dump's `outcome` block (a new `errorClass` / `errorMessage` field on `IngestionResult.Filtered`). Next time a regression like H4 lands, the next dump *tells us* `SQLiteConstraintException: FOREIGN KEY constraint failed` without requiring logcat.

### 6.2 Hardcoded keyword lists that age out

**Sites:**
- `TransactionalGate.kt:64-94` (POSITIVE_VERBS — 50+ entries)
- `TransactionalGate.kt:104-147` (NEGATIVE_KEYWORDS — 60+ entries)
- `NotificationParsingUtils.kt:121-163` (CREDIT_VERBS / DEBIT_VERBS / STRONG_CREDIT_PHRASES — overlapping with TransactionalGate's positives)
- `IciciNotificationParser.kt:25-38` (`payment due / total due / minimum due ...`) — locally maintained needles
- `CredNotificationParser.kt:91-131` — three more local-needle lists (`isCardDue` / `isCardPayment` / `isCardSpend`)
- `KotakNotificationParser.kt:44-54` (TRANSACTIONAL_VERBS — third copy of the positive set)
- `AtmNotificationParser.kt:130-152` (ATM_TOKENS + MONEY_VERBS)
- `EmiNotificationParser.kt:69-75` (TRANSACTION_SIGNALS + DEBIT_VERBS)
- `MerchantNameUtils.kt:35-48` (tails, leadingTailWords, noiseTokens)

**Structural fix**: the JSON rule engine from §5.4 subsumes all of these — every parser collapses to a rule, every keyword list becomes data, OTA-updatable. Until the engine lands: extract a single `Vocabulary` object with named lists (`Vocabulary.DEBIT_VERBS`, `Vocabulary.CREDIT_VERBS`, `Vocabulary.PROMO_KEYWORDS`, etc.), so adding a new word touches one file, and write a `VocabularyTest` that exercises the dump corpus to flag entries no real notification has hit in 60 days.

### 6.3 Assume-body-populated regexes

**Sites:**
- `NotificationParsingUtils.kt:9-12` cardDigitsRegexes — `[*xX]{2,}\s*(\d{4})` excludes "X4129" single-X form
- `IciciNotificationParser.kt:96-98`, `CredNotificationParser.kt:150-153`, `PaytmNotificationParser.kt:68-71`, `PhonePeNotificationParser.kt:61-64`, `GPayNotificationParser.kt:55-59`, `GenericUpiNotificationParser.kt:107-110`, `GenericNotificationParser.kt:69` — every merchant regex anchors on `(?:to|at|on)\s+...`, missing title-only money shapes like "Sent Rs.X from Bank AC Y" where the payee isn't named.
- `EmiNotificationParser.kt:78-80` — `emi\s+of\s+...` requires both "emi" and "of" tokens; title-only "EMI debited Rs.5000" misses.

**Structural fix**: every parser's `parse` should run all its extractors against `title + "\n" + combinedBody` (today they read `rawEvent.body` which already includes title — good) AND degrade gracefully when an extractor returns null instead of using the absence as evidence. The confidence ladder should *credit* the title for carrying the signal — currently a title-only body and a body-only body produce identical scores even though the title-only shape has *less* corroborating evidence and probably deserves Inbox review rather than auto-create.

### 6.4 First-parser-wins ordering

**Site:** `NotificationParserRegistry.kt:8-11`:

```kotlin
fun parse(rawEvent: RawCaptureEventEntity): NotificationParseResult {
    val parser = parsers.firstOrNull { it.canParse(rawEvent) } ?: GenericNotificationParser()
    return parser.parse(rawEvent)
}
```

The first `canParse=true` wins regardless of the quality of its extraction. Concrete fallout in this dump: Truecaller-mirrored Kotak debit body matches `GenericNotificationParser` (always-true tail) but NOT the Kotak-specific parser (package check fails), so the body parses with Generic's regex set (missing single-X masked-digits handling), drops to LOW confidence, and is IGNORED — even though a Kotak-tuned parser would have produced a clean MEDIUM-confidence result.

**Structural fix**: replace `firstOrNull` with `maxByOrNull { it.parse(...).parseConfidence }` — let every parser whose `canParse` returns true take a shot, pick the highest-confidence result. Cost: extra parse-attempts per notification (cheap; parses are pure-regex and fast). Benefit: the package-name gate that today *excludes* a parser becomes a *signal* — Kotak parser's confidence drops 10pp when not on the Kotak package but still runs.

### 6.5 Null fields produce silent non-determinism

**Sites:**
- `NotificationDedupeEngine.kt:46-53` — fingerprint inputs use `.orEmpty()`/`.name.orEmpty()` everywhere, so nulls *do* become deterministic empty strings. Fingerprint is deterministic — confirmed in §3.1. **Not actually a bug here**, but the shape is fragile: if a future field is added to the input without an `.orEmpty()`, `null` would print as the literal string `"null"` (Kotlin's `toString` on null inside `listOf` would NPE first, but `?.toString().orEmpty()` patterns silently change behaviour).
- `TransactionCandidateDao.kt:18-31` — the `decisionState != 'IGNORED'` predicate is the *actual* dedupe leak (§3.2). Not a null-bug per se, but in the same family: a state-aware lookup that doesn't account for the fact that the state we're filtering out is the most common state of a freshly-arrived duplicate.

**Structural fix**: codify fingerprint inputs as a typed `FingerprintKey` data class with non-nullable fields (defaulting to canonical sentinels: `EMPTY_AMOUNT`, `EMPTY_MASK`, `EMPTY_COUNTERPARTY`). Drop the null/empty ambiguity at the type level. Then in `getLatestUsableByFingerprint`, split the query into two — `getLatestForDedupe` (no decisionState filter, used by `NotificationDedupeEngine`) and `getLatestUsable` (current behaviour, used wherever we mean "find me a non-tombstoned candidate").

---

## 7. Recommended Remediation Order

Ranked by user-visible leverage on the next dump, smallest blast radius first:

1. **Fix write order in `NotificationSignalNormalizer.normalizeLocked`** (Topic A). Re-order so the candidate row is upserted before `createInboxItem`, then update the candidate's `linkedInboxItemId` after. This claws back all 6 INGEST_FAILED notifications immediately — including the only ICICI Card debit, the only Walnut BISTRO transaction, the only Swiggy Dineout payment of the day. **Single-file change. 15 lines. No schema migration.**

2. **Drop `decisionState != 'IGNORED'` from `getLatestUsableByFingerprint` or split it into two methods** (Topic B). Stops triple-writing duplicate IGNORED candidates. **One DAO file. No migration.**

3. **Add `"sent from"` to `POSITIVE_VERBS`** + audit Kotak811's other native title shapes (Topic D). Reclaims the Kotak811 `₹14.00 sent from XX4129` transaction. **One-line change in `TransactionalGate.kt`.** Also add `" debit from"`, `" credit to"` as belt-and-braces.

4. **Surface exception class+message into `IngestionResult.Filtered` and the dump outcome** (Topic E.1). Next regression won't require code-archaeology. **Touches `IngestionResult.kt`, `RupeeNotificationListenerService.kt`, `NotificationDumper.kt`.**

5. **Loosen `cardDigitsRegexes` to accept single-X form** (Topic C/E.3). Specifically add `\bA(?:/|\s|\.)C\D{0,4}X?(\d{4})\b` as a third alternative. Recovers maskedDigits on every Truecaller-mirrored bank SMS, which in turn pushes Generic-parsed Kotak debits from confidence 0.55 → 0.62 → MEDIUM tier → Inbox review (right place).

6. **Replace `firstOrNull` with confidence-max in `NotificationParserRegistry.parse`** (Topic E.4). Routes Truecaller-Kotak debits through the Kotak parser despite the package check failing, since Kotak's body-shape would still produce a higher score. Single-file change; requires per-parser `canParse` calls to be made cheaper or memoised — they already are.

7. **JSON rule engine** (Topic D.4, E.2). Multi-week project. Schedule after the above six have stabilised; the dump-replay harness already exists to drive regression coverage.

Steps 1-3 alone restore the day. Steps 4-6 are defense-in-depth so the next vocab/shape surprise doesn't cost another full day of zero ingestion.
