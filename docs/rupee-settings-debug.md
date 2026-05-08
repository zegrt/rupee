# Rupee Settings & Debug

Date: May 8, 2026
Status: Reflects v0.7.0

## 1. Purpose

Two surfaces that sit alongside Home / Inbox / Transactions:

- **Settings** — long-lived user-facing controls. Survives indefinitely.
- **Debug** — engineering-only tools. Should be removed (or feature-flagged) before public release.

## 2. Settings (v1 scope)

### Sections

- **Profile**
  - Display name (editable, persisted to `users.displayName`)
  - Currency (read-only, `INR`)
- **Monthly budget**
  - Current limit (rendered from the `MONTHLY_TOTAL` budget covering today)
  - Editable rupees-only input
  - Save button writes through `LocalFinanceRepository.setMonthlyBudgetLimit(limitMinor)`, which also creates the current month's row if missing
- **Notifications**
  - Status line ("granted" / "required for auto-tracking")
  - Open system notification-listener settings
- **Cards & EMIs** — a tappable row that opens a fullscreen `CardsEmisScreen` (Compose `Dialog`). Lists active credit cards (display name, provider/network/masked id, outstanding, limit, statement due) and the user's EMI plans. EMI plans support a manual add (name, monthly amount, optional months remaining, optional next due date, notes) and per-row Remove. Backed by `CardsEmisViewModel` + `LocalFinanceRepository.observeEmiPlans` / `addEmiPlan` / `removeEmiPlan`. Auto-detection of cards/EMIs from notifications is M8 follow-on
- **Trusted merchants** — a tappable row showing the current rule count. Tapping opens a fullscreen `TrustRulesScreen` (Compose `Dialog`) listing each saved `MerchantTrustRule` as its own card with merchant pattern, auto-category label (when set), and a Remove action that calls `LocalFinanceRepository.removeMerchantTrustRule(id)`. Empty state nudges the user to use the Inbox-confirm "Always trust" toggle to add one
- **Categories** — read-only list of seeded categories
- **Buckets** — read-only list of seeded buckets
- **About**
  - App version, read from `BuildConfig.VERSION_NAME` (with `-debug` appended when `BuildConfig.DEBUG`)
  - Currency
  - Open Debug button (opens the same fullscreen Debug surface that the floating pill does)

### Out of scope for v1

- Editing categories or buckets
- Theme selection
- Export / backup
- Account-level (cloud) settings — gated on Milestone 10

## 3. Debug (v1 scope)

### Sections

- **Send sample notification (synthetic)**
  - Three preset payloads (GPay, CRED, ICICI) hardcoded in `DebugSamples`
  - Each tap calls `LocalFinanceRepository.debugIngestNotification(packageName, title, body)` which goes through the *application-side* ingestion path: `RawCaptureWriter.storeNotificationEvent` → `NotificationSignalNormalizer.normalize`. Bypasses the actual `NotificationListenerService`, so this is the right tool when you want to test parser/normalizer/dedupe changes without involving the OS.
- **Post real system notification**
  - Editable Title and Body fields, plus three "Load from sample" buttons that prefill the GPay/CRED/ICICI presets
  - Posts an actual `NotificationManager.notify()` on the `rupee_debug` channel with a `RupeeApplication.DEBUG_MOCK_EXTRA` bool extra
  - The listener service (`RupeeNotificationListenerService`) explicitly bypasses its self-package filter when that extra is set, so the *real* listener path runs end-to-end. This is the right tool when you want to verify the listener is bound and that a particular body actually flows through the Android side
  - Logcat: `adb logcat -s RupeeNotifListener` shows `Listener connected`, per-event `onPosted pkg=… mock=… body=…`, skip reasons, and `Ingested raw event …` when normalization runs
- **Parser playground**
  - Free-form title + body inputs
  - Runs `NotificationParserRegistry.default().parse(rawEvent)` on a synthetic `RawCaptureEventEntity` *without* persisting anything
  - Renders parser output (key, provider hint, kind, amount, merchant, mode, masked digits, confidence) in a code-style block
- **Danger zone**
  - "Reset app data" → `LocalFinanceRepository.resetAllData()` which calls `database.clearAllTables()` on the IO dispatcher and then `ensureBaseData()` to re-seed user/categories/buckets/current-month budget
  - Confirm dialog before destructive action

### Why this exists

Hardware notifications are awkward to fixture — providers may A/B body templates, the listener service is hard to invoke from tests, and dev devices accumulate noise. The Debug page lets a developer:

1. End-to-end test a parser change without rebuilding+installing+manually triggering a notification
2. Iterate on parser regexes against real-world bodies they've copied
3. Reset to a clean slate when local state gets weird

### Removal plan

Before public release the Debug tab should be:

- Hidden behind a build-type guard (`BuildConfig.DEBUG`), or
- Removed entirely (the repository methods can stay, just unwired)

## 4. Navigation

Settings is the rightmost chip in the chip-row tab switcher on the Home screen (Home / Inbox / Transactions / Settings — four chips, fits on a normal-width device).

Debug is **not** a tab. It opens as a fullscreen Compose `Dialog` from two entry points:

1. A small "Debug" floating pill at the bottom-right of every Home tab — currently visible always; before any external test build it should be wrapped in `if (BuildConfig.DEBUG)`.
2. Settings → About → "Open debug tools" button.

The architecture spec calls for a 5-tab bottom nav (Home / Inbox / Transactions / Calendar / Settings); a future task will migrate from chips to bottom nav. The Debug entry points migrate alongside.

## 5. Trust-rule loop (shipped v0.6.0, fixed v0.6.1)

When the user confirms an Inbox row, a `Switch` labelled "Always trust ${merchant}" sits next to the Confirm button. Toggling it on causes `LocalFinanceRepository.confirmInboxItem(..., addTrustRule = true)` to also persist a `MerchantTrustRuleEntity { userId, merchantPattern, autoCategoryId, createdAt, updatedAt, syncStatus }`.

On every subsequent ingestion, `NotificationSignalNormalizer` reads the user's rule list before applying the base decision. If `MerchantNameUtils.matchesPattern(parseResult.toEntityName, rule.merchantPattern)` (or the same against `merchantRaw`) returns true *and* the candidate is not a dedupe duplicate, the candidate is elevated to:

- `confidenceTier = HIGH`
- `decisionState = AUTO_CREATED`
- `decisionReason = MERCHANT_TRUSTED`

…and the canonical transaction is created with `status = CONFIRMED`, `createdBy = "trust_rule"`, and the rule's `autoCategoryId` (if set). The user never sees an inbox row for that merchant again unless the rule is removed.

Matching is centralized in `MerchantNameUtils`:

- `clean(raw)` — trims " on / using / via / through" tails and prefers the segment after the last " at " for CRED-style bodies. Returns `"Unnamed"` for null/blank.
- `matchesPattern(rawMerchant, pattern)` — case-insensitive *exact* compare of `clean(rawMerchant)` against the trimmed pattern. Substring matches are deliberately rejected so a "Big" rule does not match "Big Bazaar".

**v0.6.1 fix:** `addMerchantTrustRule` now stores the *cleaned* form of the pattern. Earlier the raw `candidate.toEntityName` (e.g. "Swiggy using UPI") was persisted as-is, but the matcher cleans the *incoming* raw merchant before comparing — so a stored rule of "Swiggy using UPI" never matched a future "Swiggy" and never fired. Round-trip regression covered in `MerchantNameUtilsTest`.

Trust rules are now manageable from Settings → Trusted merchants (v0.6.2; promoted to its own page in v0.6.3). Each rule renders as a card with the merchant pattern, the auto-category label (when set), and a Remove button. There is no edit affordance — to change a pattern, remove the rule and re-add it via Inbox confirm.

## 6. Deferred — design captured for later

These were considered but not built:

- **Merge with existing transaction** — when an Inbox item duplicates a transaction the dedupe layer missed, the user should be able to pick the target canonical row (a search-then-select UI) and merge. Repository contract: `mergeInboxIntoTransaction(inboxItemId, targetTransactionId)` — sets the target's `dedupeFingerprint` to include the source candidate, links the candidate via `linkedCanonicalTransactionId`, and resolves the inbox item with `decisionState = MERGED`.
- **Recategorize on Transactions tab** — the `TransactionDetailSheet` Edit form has Merchant + Notes today; add a `CategoryDropdown` row mirroring the Inbox review row.
- **PhonePe / Paytm parsers** — their notifications currently fall into `GenericUpiNotificationParser` with `providerHint = "upi"`. Dedicated parsers would give better merchant extraction and a branded provider hint.
- **Upcoming dues strip on Home** — needs cards/EMI plumbing from Milestone 8.
- **Custom bucket progress cards on Home** — needs per-bucket budgets which are not seeded yet, plus a `transaction_bucket_assignments` DAO.
