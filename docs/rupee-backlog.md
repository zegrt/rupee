# Rupee — Deferred Work & Sprint Backlog

**Purpose:** durable, in-repo backlog. Anything Claude promised to do "next sprint" or "later" lives here, not just in conversation context. This file is the single source of truth for what's deferred — if it's not here, it doesn't exist.

**Last updated:** 2026-05-13 (after Sprint 1.4 partial / v0.13.6)

---

## How to read this

- **In flight** — currently being shipped or actively designed.
- **Next** — agreed direction, concrete enough to start.
- **Slotted** — captured intent, design pending.
- **Watching** — known gap, not prioritised yet.

Each item should have: *what*, *why it matters*, *touchpoints*, *blocked by*.

---

## In flight

_(nothing right now — v0.13.5 just landed)_

---

## Next — pick one of these to start

### S1.3 — Evidence-stacked confidence scoring
**What:** Replace every parser's hardcoded confidence brackets (`0.62 / 0.55 / 0.15` etc.) with an additive evidence tally. Each parser contributes points per signal found (canonical verb +3, masked digits +2, UPI ref +2, named merchant +2, amount-only +1, soft anti-signals −5). Single global threshold decides MEDIUM vs HIGH.
**Why:** today two very different bodies score the same `0.62` — one with strong evidence, one with weak. Tuning is per-parser edits in nine files. With a tally, tuning is one knob.
**Touchpoints:** `NotificationParseResult.parseConfidence`, all 9 parsers in `ingestion/`, `NotificationDecisionEngineTest`.
**Blocked by:** nothing. Can do one parser at a time — start with `GenericNotificationParser` + `GenericUpiNotificationParser` (the two with the loosest current scoring).

### S1.4 polish — remaining income work (Layer C)
**What:** the income data layer landed in v0.13.6 (kinds, parsers branch on direction, normalizer + DAO write INCOME, Recap shows "Received +₹X" + net). What still needs UI work:
- **Home dashboard income line.** `LocalFinanceRepository.observeReceivedInPeriod` exists and is unused on Home. Add a "Received this month" sub-line under the monthly-budget hero (or a small chip next to weekly spend). The wiring in HomeViewModel was sketched then reverted — flow definition can be re-added cleanly.
- **Transactions list visual diff.** Today income shows up in the list with the same red-tinted style as a spend. Show income with a `+₹X` prefix and a green/positive tint. Same in the detail sheet.
- **Manual entry income toggle.** Bottom-sheet has no Income type today — every manual entry writes EXPENSE. Add an Income/Expense segmented control. `LocalFinanceRepository.createManualTransaction` should accept a type param.
- **Per-parser INCOME coverage.** Only Kotak / Generic / GenericUpi branch on direction today. CRED/ICICI/PhonePe/Paytm/GPay parsers still hardcode SPEND — fine until a user dogfoods a credit-side notification through one of those packages. Audit each parser as it bites.
**Why:** load-bearing for credibility — user can see the data is captured in Recap but won't notice income at-a-glance until Home / Transactions / manual entry show it.
**Blocked by:** nothing. Take when next dogfood session produces an income notification we can verify against.

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

### S4-5 — Adaptive confidence from user behaviour (Item 13)
**What:** `ingestion_signal_stats` table keyed `(package, parserKey, cleanedMerchant)`. Confirm counts up, dismiss counts down. Bootstrap mode for first 14 days lowers MEDIUM threshold from 0.6 → 0.5 to be more liberal at onboarding. Auto-promote to `MerchantTrustRule` after 3 confirms in a 7+ day window.
**Why:** the app learns the user's actual transaction patterns instead of relying on hardcoded confidence floors. User asked for this explicitly.
**Touchpoints:** new table + DAO, decision engine reads stats, UI shows "auto-trusted (3 confirms)" badge.
**Reference:** `docs/axio-takeaways.md` Item 13.

---

## Watching — known gaps, no sprint yet

### From `docs/notification-ingestion-deep-dive.md` §9
- **§9.3 `extractedJson TEXT NULL` on `RawCaptureEventEntity`.** Persist structured Bundle fields so we can re-parse old events when parser v2 ships. ~1-2 KB/notif storage cost. Not urgent — current `body` field has the combinedBody which is enough for re-parsing 95% of cases.
- **§9.9 Post-ship parse-rate counter.** Surface "notifs received vs notifs with `amountMinor != null`" as a debug stat. Aim ≥70% on a typical day. Useful for catching parser regression without needing a fresh dump every time.

### From `docs/rupee-settings-debug.md` §6 (deferred-by-design)
- **Merge with existing transaction.** Repository contract: `mergeInboxIntoTransaction(inboxItemId, targetTransactionId)`. Two-step soft-confirm currently exists in UI but no full search-then-select picker.
- **Recategorize on Transactions detail sheet.** Edit form has Merchant + Notes; add CategoryDropdown row mirroring Inbox review.
- **Dedicated PhonePe / Paytm parsers** — they fall into `GenericUpi` today. Better merchant extraction + branded provider hint.
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
