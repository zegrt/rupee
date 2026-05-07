# Rupee Settings & Debug

Date: May 8, 2026
Status: Reflects v0.5.3

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

## 5. Deferred — design captured for later

These were considered for this round but not built:

- **"Always trust this merchant" rule** — needs a new `MerchantTrustRule` entity (`{ userId, merchantPattern, autoCategoryId, createdAt }`) and a check in `NotificationDecisionEngine` that elevates matched signals to `AUTO_CREATED` + `CONFIRMED`. Confirm flow on Inbox should add a checkbox "Always trust [merchant]" that creates the rule.
- **Merge with existing transaction** — when an Inbox item duplicates a transaction the dedupe layer missed, the user should be able to pick the target canonical row (a search-then-select UI) and merge. Repository contract: `mergeInboxIntoTransaction(inboxItemId, targetTransactionId)` — sets the target's `dedupeFingerprint` to include the source candidate, links the candidate via `linkedCanonicalTransactionId`, and resolves the inbox item with `decisionState = MERGED`.
- **Recategorize on Transactions tab** — the `TransactionDetailSheet` Edit form has Merchant + Notes today; add a `CategoryDropdown` row mirroring the Inbox review row.
- **PhonePe / Paytm parsers** — their notifications currently fall into `GenericUpiNotificationParser` with `providerHint = "upi"`. Dedicated parsers would give better merchant extraction and a branded provider hint.
- **Upcoming dues strip on Home** — needs cards/EMI plumbing from Milestone 8.
- **Custom bucket progress cards on Home** — needs per-bucket budgets which are not seeded yet, plus a `transaction_bucket_assignments` DAO.
