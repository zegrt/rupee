# Notification ingestion deep dive — Rupee

**Date:** 2026-05-13 · last refreshed 2026-05-19 for v0.14.0
**Status:** Research brief. Original framing was "reference before writing
the v0.13 listener fix" — the v0.13 / v0.14 work has all shipped. Kept
in-repo as background for the ingestion pipeline's design; the v0.14
audit work added inline callouts where the spec has moved on.
**Context:** Pre-v0.13, `RupeeNotificationListenerService` read
`NotificationCompat.EXTRA_TEXT` only, found it blank for almost every
real-world bank notification, logged *"Skipped: empty body"*, and never
reached the parsers. This document mapped the entire surface; the
listener-side and parser-side fixes are now live.

> **What's changed since the original write-up (v0.13.4 → v0.14.0)**:
> - `TransactionalGate` pre-filter — rejects promo pushes / OTPs /
>   payment-requests before any parser runs. Vocabulary grew in v0.13.6,
>   v0.13.8, and v0.14.0 (M2 — reversed/chargeback/SIP/NACH/ECS).
> - `ParsedTransactionKind.INCOME` and the shared
>   `NotificationParsingUtils.classifyDirection` (v0.13.6) — parsers
>   route INCOME vs SPEND off direction rather than hardcoding SPEND.
> - REFUND → INCOME canonical routing (v0.14.0, H1) for PhonePe/Paytm.
> - UPI confidence tiering (v0.14.0, M5) — amount + merchant + masked
>   digits → 0.85 HIGH (auto-create) instead of locking at 0.7 MEDIUM.
> - `AtmNotificationParser` registered FIRST in the registry (v0.14.0,
>   M3a) — ATM withdrawals route as `CASH_WITHDRAWAL`.
> - Fuel-brand normalisation in `MerchantNameUtils.clean` (v0.14.0, M3b).
> - Foreign-key cascades + 90-day pruning of `raw_capture_events`
>   (v0.14.0, H3/H4) — see [docs/rupee-schema.md §9](rupee-schema.md).
> - Notification dump format v2: ingest-time `outcome{}` block per line
>   (v0.13.x) plus sibling `outcomes.jsonl` snapshot at share time
>   (v0.13.x phase 2a). Full schema:
>   [docs/dump-enrichment-followups.md](dump-enrichment-followups.md).

---

## 1. Notification Bundle anatomy — what's actually in there

A `StatusBarNotification.notification.extras` is an Android `Bundle` populated by `Notification.Builder` / `NotificationCompat.Builder`. The fields relevant to transaction extraction:

| Key (`Notification.EXTRA_*` / `NotificationCompat.EXTRA_*`) | Type | When set |
|---|---|---|
| `EXTRA_TITLE` | `CharSequence` | Every notification — `setContentTitle()`. Banks like Kotak put the amount here (`"₹10.00 sent via UPI"`). |
| `EXTRA_TITLE_BIG` | `CharSequence` | Set by `BigTextStyle.setBigContentTitle()`. Often duplicates title; sometimes carries longer phrasing when expanded. |
| `EXTRA_TEXT` | `CharSequence` | The collapsed body — `setContentText()`. Frequently truncated or *omitted entirely* when the builder uses `BigTextStyle` without also calling `setContentText`. **This is the only field we read today.** |
| `EXTRA_BIG_TEXT` | `CharSequence` | `BigTextStyle.bigText()` — the full body shown when expanded. Almost every Indian bank uses this for the real transaction string. |
| `EXTRA_SUB_TEXT` | `CharSequence` | `setSubText()` — small grey caption (e.g., "Credit Card", masked digits, sometimes account nickname). |
| `EXTRA_INFO_TEXT` | `CharSequence` | `setContentInfo()` — small right-aligned info; some banks put balance here. Deprecated in API 24+ but still appears. |
| `EXTRA_SUMMARY_TEXT` | `CharSequence` | `BigTextStyle.setSummaryText()` — footer of expanded view. Common location for "*Available limit ₹xx,xxx*". |
| `EXTRA_TEXT_LINES` | `CharSequence[]` | `InboxStyle.addLine()` — array of lines; some aggregator notifications (Slice statements, CRED rewards digest) use this. |
| `EXTRA_MESSAGES` | `Parcelable[]` (bundles) | `MessagingStyle` — array of message bundles each containing `text`, `sender`, `time`. A few banks experiment with conversational layouts (rare today). Walk with `MessagingStyle.extractMessagingStyleFromNotification(notification)` then iterate `messages`. |
| `EXTRA_PEOPLE_LIST` | `ArrayList<Person>` | Names attached to MessagingStyle. Mostly irrelevant for txn extraction. |
| `EXTRA_CONVERSATION_TITLE` | `CharSequence` | MessagingStyle group title. |
| `EXTRA_PICTURE`, `EXTRA_LARGE_ICON_BIG` | `Bitmap` | Image styles. Not useful unless we OCR (out of scope). |
| `EXTRA_TEMPLATE` | `String` | The fully-qualified class name of the style — `"android.app.Notification$BigTextStyle"` etc. Use this to dispatch by style. |

`Notification.tickerText` is the legacy short-text shown on the status bar in pre-Lollipop devices and to accessibility services. Banks rarely set it now; when set it's usually a one-liner like `"HDFC: Rs.500 debited"`. Cheap to read as a fallback.

`Notification.actions[]` are buttons (`Action.title` is the visible label). For txn alerts these can carry the verb ("Dispute", "Mark as paid", "Report fraud") — useful as *evidence the notification is transactional*, not a place to find the amount.

`contentIntent.getIntent()` extras would let us read into the bank app's deep-link payload, but `PendingIntent` is opaque without `getIntentSender()` reflection — Android does not expose the wrapped `Intent`'s extras to listeners. Treat as unreadable.

`RemoteViews` (`bigContentView`, `contentView`, `headsUpContentView`) are present when the builder used `setCustomBigContentView()` etc. The text lives inside the `RemoteViews` action list rather than the Bundle. There is a documented reflection workaround that walks `mActions` and extracts `setTextViewText` calls. It's fragile (the field name `mActions` is hidden API, restricted on API 28+, and on API 30+ requires `@RequiresApi` reflection probes that may break). For Indian banks the good news: virtually all major banks (HDFC, ICICI, SBI YONO, Axis, Kotak, Yes, Federal, IDFC First, Amex) use the standard `BigTextStyle` — they want WearOS/Android Auto compatibility and the consistent system look. The apps that custom-view today are mostly food delivery, music players, and a handful of fintechs with branded headers (PhonePe occasionally on rewards, never on transactions). **Verdict: skip RemoteViews extraction in v1; revisit only if a specific issuer is discovered to use it.**

Docs: <https://developer.android.com/reference/android/app/Notification#extras>, <https://developer.android.com/reference/androidx/core/app/NotificationCompat>.

---

## 2. Notification styles and how text is distributed

Detect via `extras.getString(EXTRA_TEMPLATE)` — it contains the style class name.

- **`BigTextStyle`** — `Notification$BigTextStyle`. Layout:
  - title → `EXTRA_TITLE` (and often duplicated in `EXTRA_TITLE_BIG`)
  - collapsed snippet → `EXTRA_TEXT` (may be empty)
  - full body → `EXTRA_BIG_TEXT`
  - footer → `EXTRA_SUMMARY_TEXT`
  This covers ~85% of bank notifications.
- **`InboxStyle`** — `Notification$InboxStyle`. Lines in `EXTRA_TEXT_LINES` (`CharSequence[]`). Used by Gmail-style digests, some CRED weekly summaries.
- **`MessagingStyle`** — `Notification$MessagingStyle`. Walk via `NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)` then `style.messages.map { it.text }`. Rare in banking, common in WhatsApp Pay-style flows.
- **`BigPictureStyle`** — image-first. `EXTRA_PICTURE` is a Bitmap; the caption is in `EXTRA_TEXT`. Card-statement preview images sometimes. Not worth a parser branch.
- **`DecoratedCustomViewStyle`**, **`MediaStyle`** — irrelevant for txn extraction; skip.

Fallback strategy: if `EXTRA_TEMPLATE` is unknown, *still* union every standard CharSequence/CharSequence[] in the Bundle into one string. Defensive.

---

## 3. Real-world Indian bank notification anatomy

*Below is composited from public Walnut rule patterns, screenshots, and the Kotak example you observed. Items marked* (guess) *are extrapolated from typical patterns and need confirmation from real dumps.*

| Issuer | Title (`EXTRA_TITLE`) | Body location | Notes |
|---|---|---|---|
| **ICICI Bank** | `ICICI Bank` (constant) | `EXTRA_BIG_TEXT`: `"Rs 1,234.00 spent on ICICI Bank Card XX1234 on 12-May-26 at AMAZON. Avail Limit Rs 45,678."` | Amount + masked + merchant + balance all in big_text. Title is brand-only. |
| **HDFC Bank** | `HDFC Bank` (constant) | `EXTRA_BIG_TEXT`: `"Sent Rs.500.00 From HDFC Bank A/C *1234 To MERCHANT On 12/05. Ref 123456. Not You? Call ..."` | Debit alerts always BigTextStyle. Credit-card alerts: `"Spent Rs.X on HDFC Bank Card XX1234 at MERCHANT on DATE"`. |
| **Kotak / Kotak811** | `"₹10.00 sent via UPI"` (amount-in-title!) | `EXTRA_BIG_TEXT`: `"Amount debited from XX4129. Check out details."` | The amount is **only** in the title. Body is generic. Today's parsers won't find an amount. |
| **SBI YONO / SBI Quick** | `YONO SBI` / `SBI` | `EXTRA_BIG_TEXT`: `"Dear Customer, A/c XX1234 debited by 500.00 on 12May26 transfer to MERCHANT Ref 1234567"` | (guess: occasionally uses `EXTRA_TEXT` only — short bodies) |
| **Axis Bank** | `Axis Bank` | `EXTRA_BIG_TEXT`: `"INR 1,500.00 debited from A/c no. XX1234 on 12-05-26. Info: UPI/P2A/.../MERCHANT. Avl Bal..."` | Long bodies, sometimes truncated in `EXTRA_TEXT`. |
| **Yes Bank** | `Yes Bank` | `EXTRA_BIG_TEXT` | (guess) similar to Axis structure. |
| **Federal Bank** | `Federal Bank` | `EXTRA_BIG_TEXT` | (guess) `"Rs X debited from A/c XXXX on DATE"`. |
| **Amex India** | `American Express` | `EXTRA_BIG_TEXT`: `"Alert: You have spent INR 1,234 on your Amex card ending 12345 at MERCHANT on DATE."` | 5-digit masked, not 4. |
| **PhonePe** collapsed | `"₹10.00 at MOHAN…"` | `EXTRA_TEXT`: short. Expanded: `EXTRA_BIG_TEXT` with full merchant + UPI ref. | Collapsed view truncates merchant. |
| **GPay** | `Google Pay` | `EXTRA_BIG_TEXT`: `"You paid ₹500 to MERCHANT"` | UPI ref in summary. |
| **Paytm** | `Paytm` / `Paytm Payments Bank` | `EXTRA_BIG_TEXT` | (guess) `"Paid ₹500 to MERCHANT via UPI"`. |
| **CRED** | varies — `"Credit card spent"` for spend; `"Payment due"` for due | `EXTRA_BIG_TEXT`: `"You spent ₹1234 on HDFC card XX1234 at MERCHANT"` for spend; `"Your ICICI card bill of ₹X is due on DD MMM"` for due | CRED is an aggregator — it sends both spend mirrors and statement reminders. |
| **Slice / Jupiter / Niyo / Fi** | brand-only | `EXTRA_BIG_TEXT` | (guess) all BigTextStyle. Slice statement-reminders sometimes use InboxStyle. |

Key takeaways:
- **Amount lives in title** for Kotak811 (and possibly some Fi/Jupiter UPI flows). Cannot ignore title.
- **Masked digits live in big_text** for every issuer — never in title.
- **`EXTRA_TEXT` is empty or duplicates big_text** for almost every modern bank alert.
- **`EXTRA_SUB_TEXT`** sometimes holds the masked tail (`"XX1234"`) on its own; worth concatenating.

---

## 4. The group-summary and dedup problem

- **Group summaries**: when an app posts notifications with `setGroup("alerts")` plus a separate notification flagged `setGroupSummary(true)`, Android delivers the summary too. The summary's body is usually empty or `"3 new alerts"`. **The flag to check is `Notification.FLAG_GROUP_SUMMARY` (= `0x00000200`)** on `sbn.notification.flags`. Today our listener does *not* check this — it just sees an empty body and silently skips, which happens to be the right outcome by accident. We should still filter explicitly so we don't try to parse a summary even after we union all fields (because summary `EXTRA_TEXT_LINES` may contain the per-child snippets and would trigger false-positive parses with stale amounts).
- **Same txn from two apps**: HDFC card swipe → HDFC bank app notif + CRED notif. Today `NotificationDedupeEngine` fingerprints on `(candidateType, amountMinor, currency, mode, maskedDigits, normalizedCounterparty, 5-min bucket)`. If both notifications produce the same `(amount, last-4, merchant)` they collide in the same bucket and the second one is marked IGNORED. This works *only if both parsers succeed and extract identical fields*. The CRED parser extracts merchant via `"at <MERCHANT>"` and HDFC's bank package falls into Generic — likely diverges on merchant normalization. Worth auditing once real notifications start flowing.
- **Notification updates**: `onNotificationPosted` fires on `notify(sameId, updatedNotification)` just like fresh posts. Each update has a fresh `postTime` and identical body usually. Our SHA-256 raw fingerprint includes `postedAtMillis` — so an update with a new `postTime` is treated as a fresh raw event and re-ingested. The dedupe bucket (5-min) catches it downstream and marks the candidate IGNORED, but we still write a duplicate raw row. Acceptable but worth knowing. Mitigation: also dedupe by `(packageName, title, body)` ignoring time, or by `sbn.key` (the system-wide unique key).

---

## 5. Architectural recommendations for Rupee

**Where should "extract all text" live?** Three valid placements:

| Option | Pros | Cons |
|---|---|---|
| A. In the listener (build a single string before calling normalizer) | Simple. Normalizer signature unchanged. | Debug-inject path (which calls `ingestNotification` directly) skips it — but we control that path too. Loses field-level structure forever. |
| B. New `NotificationExtractor` JVM-pure object called from listener; result is a structured `ExtractedNotification(title, bigTitle, text, bigText, subText, infoText, summary, lines, messages, ticker, actionLabels, isGroupSummary, template)`; pass into normalizer; normalizer flattens to a single body string for storage AND keeps the structured form available. | Testable in unit tests with a mocked Bundle (Robolectric) or pure JVM via plain data. Parsers can stay body-only OR upgrade to structured input. Future-proof. | Slightly more plumbing. New entity columns or JSON blob. |
| C. Each parser walks the Bundle | Parsers see everything. | We'd have to pass the raw `Notification` deep into the parsers — couples them to Android, breaks JVM-only tests. **Reject.** |

**Recommend B.** Build `NotificationExtractor` as a pure-Kotlin object that takes a `Bundle` (or a test-friendly `Map<String, Any?>`) and returns `ExtractedNotification`. The listener calls it; the normalizer accepts either the structured form or a flattened string.

**Should we store the raw Bundle?** Yes, persist a JSON snapshot of the extracted fields (not the full Bundle — that contains Bitmaps and Parcelables; too heavy). Add `extractedJson TEXT NULL` to `RawCaptureEventEntity`. Storage cost: maybe 1–2 KB per notification, 20 KB/day of activity. Negligible vs. value of *re-parseability when we ship parser v2*. Migration: read old `body` into `extractedJson.text` and leave the rest null.

**Title-carries-the-amount (Kotak811).** Cleanest path:
- The listener builds a `combinedBody` = `[title, bigTitle, text, bigText, subText, summary, infoText, lines.joinToString(), messages.joinToString(), ticker].filterNotBlank().joinToString("\n")`.
- Store this as `body` (replaces today's `EXTRA_TEXT`-only body).
- Parsers continue to read `body` and now see everything — title amounts get picked up.
- Optionally also pass structured `title` separately so `canParse` checks can weight title vs body.

**Routing risk from wider text.** Yes, real risk. A CRED notification mirroring an HDFC swipe says `"...on HDFC card..."` — today CRED's parser fires first (it checks package + the word "CRED"); HDFC parser doesn't exist yet, but if it did and we union all fields, an HDFC-app notification mentioning "PhonePe UPI ref" might mis-route to PhonePeNotificationParser. **Mitigation**: change parser ordering and `canParse` to be **package-name-first, body-only-second**. If `sourceAppPackage` matches a known fingerprint, that parser wins regardless of body. Only fall through to body-keyword matching for unknown packages. This is the same model Walnut uses (senders[] is the primary index; patterns are scoped within sender).

---

## 6. Walnut's approach, applied to notifications

Walnut treats an SMS as a flat string. Their `rules.json` has 2,284 regex patterns scoped per-sender, each with numbered capture groups for `amount`, `pan`, `date`, `pos`, etc. They never have to ask "which field is the amount in" — there's only one field.

**The lesson for us**: after we union all extras into one `combinedBody`, our parsers become the same shape. We can build a Walnut-style rule table indexed by `packageName` (our equivalent of sender), with per-package regex sets. We trade structure for uniformity — a *good* trade, given:

- Banks change Bundle shapes between app versions; the rule "amount comes from EXTRA_TITLE on Kotak" would rot whenever Kotak refactors. A rule "amount comes from a regex match anywhere in combinedBody" survives the refactor.
- We can OTA the rules later (load from JSON instead of hardcoded Kotlin classes), the same way Walnut does — this is the *one* place their architecture has clearly aged well.

What we lose by going flat:
- The ability to weight title-matches differently from summary-matches (some `"₹X"` appearing in the footer might be the *available limit*, not the txn amount; if we had structure we'd ignore the summary field).
- Cleaner heuristics like "if amount is in title, confidence is higher because that's the bank's headline number".

**Recommended middle path**: keep structured `ExtractedNotification` internally, but expose a single `combinedBody` string to parsers v1. Build parser v2 (rule-driven) later that takes the structured form and applies per-field rules with priority.

---

## 7. Testing strategy

Three layers, all worth doing:

1. **Pure-JVM unit tests for `NotificationExtractor`** — feed a `Bundle`-like fake (use `androidx.test`'s `Bundle` via Robolectric, or model as a `Map<String,Any?>` wrapper for true pure-JVM). Cover: BigTextStyle present, BigTextStyle with empty EXTRA_TEXT, InboxStyle with lines, MessagingStyle, group summary detection, RemoteViews-only (returns null structured fields), ticker fallback.
2. **Pure-JVM unit tests for parsers against realistic `combinedBody` strings** — corpus of ~30 real-world strings (anonymized) captured from friends/family. This is the highest-leverage test layer.
3. **Debug "notification dump" tool** — in debug builds, the listener writes the full `extras` (as `extras.keySet().associateWith { extras.get(it)?.toString() }`) to a JSON file in `/storage/emulated/0/Android/data/.../files/dumps/`. Ship a button in the dev surface to share-export the file. Use this to bootstrap the corpus.

Skip Robolectric for parser tests — slow and adds no value over plain JVM strings.

---

## 8. What we can never see, even with perfect extraction

- **Bank notifications the user disabled**. Many users mute their bank app's channel. No notification = no event.
- **Banks that send SMS only**. SBI for many users (older accounts), most regional/co-operative banks (Federal, IDFC First on basic accounts), and most PSU bank ATM withdrawals. SMS is the *only* surface.
- **Cash transactions**. Never any notification.
- **POS swipes on cards where the issuer notifies only via SMS** (older HDFC accounts, Standard Chartered, some IDFC First card variants).
- **Cheque clearings, NEFT/RTGS that arrive only as bank statement entries**.
- **Auto-debits (ECS / standing instructions)** — bank often sends SMS only, no app notification.
- **Refunds reversed to a card** — frequently SMS-only.
- **Foreign currency transactions** — many issuers send these as SMS for fraud-alert prominence.
- **EMI conversions after the fact** — usually email/SMS.
- **Statement-only events** (interest credit, fees) — usually SMS or email.

Rough Walnut-equivalent coverage estimate: notification-only ingestion captures ~60% of what SMS ingestion does, biased toward UPI and credit-card-active users. Frame this in onboarding so users with mostly-SMS banks aren't surprised.

---

## 9. Concrete next-iteration plan

1. **Add `NotificationExtractor`** (pure-Kotlin, no Android deps beyond `Bundle`; provide test seam via `Map<String, Any?>` accepting variant).
   - Reads: `EXTRA_TITLE`, `EXTRA_TITLE_BIG`, `EXTRA_TEXT`, `EXTRA_BIG_TEXT`, `EXTRA_SUB_TEXT`, `EXTRA_INFO_TEXT`, `EXTRA_SUMMARY_TEXT`, `EXTRA_TEXT_LINES`, `EXTRA_MESSAGES` (via `MessagingStyle.extractMessagingStyleFromNotification`), `Notification.tickerText`, `Notification.actions[].title`.
   - Returns `ExtractedNotification` data class with all of the above plus `template`, `isGroupSummary`, `combinedBody`.
2. **Update listener** (`RupeeNotificationListenerService.onNotificationPosted`):
   - Skip if `(flags and FLAG_GROUP_SUMMARY) != 0`.
   - Call extractor; pass `combinedBody` as the `body` to `ingestNotification`, pass `title` (and store `subText` somewhere — add column or stuff into title).
   - Skip-empty check now operates on `combinedBody` — practically never empty for a real notification.
   - Keep self-package guard.
3. **Schema migration**: add `extractedJson TEXT NULL` to `RawCaptureEventEntity`. Bump Room version. Migration is additive; no backfill needed (old rows have `body` and `null` extractedJson, which is fine).
4. **Parser fingerprint adjustment**: in `NotificationParserRegistry.default()`, prefer package-name matches first. Tighten `PhonePeNotificationParser.canParse` so body-text mention of "phonepe" only fires when there is no package-attributable parser — push generic-text parsers below the package-specific ones (they already are, but verify after we union fields).
5. **Re-ingestion of legacy events**: opt for *no* migration replay in v1. Old raw events have a body that's likely empty or short — replaying them would mostly produce nulls. Mention in code comment and move on.
6. **Tests**: ~20 realistic `combinedBody` fixtures covering Kotak (title-only amount), HDFC, ICICI, Axis, SBI, GPay, PhonePe, CRED spend, CRED due. Plus `NotificationExtractor` unit tests over Bundle fakes.
7. **Debug dump tool**: in `BuildConfig.DEBUG`, write a JSON file per notification to `getExternalFilesDir("notif-dumps")` with all extras stringified. Add a settings screen button to share-export the folder.
8. **Pre-ship checklist**:
   - Manual: turn on notification access, do a ₹1 UPI swipe on Kotak, ICICI, HDFC; confirm raw event lands AND a parsed signal with non-null `amountMinor` exists.
   - Verify dedupe still fires when CRED + bank both notify the same swipe.
   - Confirm group-summary skip works (post a grouped notification from `adb shell cmd notification post`).
9. **Post-ship monitoring**:
   - Surface a debug counter: notifications received vs. notifications with `amountMinor != null`. Aim ≥ 70% on a typical user's daily flow. If under 50% on a real user, ask for a dump.
   - Crashlytics tag the parser key on any parse exception.
   - Track `Generic` parser hit rate — if it dominates, that's a signal we need package-specific parsers for the top missing issuers.

**What stays uncertain (call out before shipping):**
- Whether any major bank in India (especially newer fintechs — Jupiter, Fi, Niyo) uses `RemoteViews` for transaction alerts. Verify with dumps before deciding to skip RemoteViews extraction permanently.
- Exact `EXTRA_*` shape for SBI YONO and Federal Bank — all guesses above need real dumps.
- Whether `MessagingStyle` is ever used by any Indian bank today (unlikely but cheap to support).
