# Rupee Context

Date: May 8, 2026
Status: Working memory

## Product Snapshot

- Product codename: `Rupee`
- Type: personal finance app
- Scope: single-user only
- Primary market: India
- Primary platform: Android first
- Future platforms: full web companion, iOS later if needed

## Core Product Direction

Rupee is an expense tracker and budget helper with near-zero manual entry.

The core differentiator is local ingestion of:

- Android notifications
- SMS messages later

The app should infer transactions from real-world payment alerts instead of making the user manually log everything.

## Main User Goals

- track spending automatically
- see budget remaining
- understand weekly spending
- track credit card balances and dues
- track EMIs
- see recurring obligations
- keep some support for cash on hand and manual cash expenses

## Explicit Product Decisions

- Android-only for v1
- India-first for v1
- no bank account linking in v1
- notification ingestion is the active primary capture method
- SMS ingestion is intentionally deferred to a later build to reduce install friction and sensitive-permission prompts
- auto-create transactions only when confidence is high
- medium-confidence events should go to an Inbox
- duplicates across multiple sources must be merged
- one combined dashboard with deeper detail views
- support bank accounts, credit cards, and cash
- recurring detection should ask for confirmation
- EMI tracking should exist as its own module
- budgets should support:
  - monthly overall
  - per-category
  - custom buckets

## Important Data Concepts

- raw capture events
- parsed signals
- normalized transaction candidates
- canonical transactions
- source links for explainability
- accounts
- credit cards
- budgets
- custom buckets
- recurring patterns
- EMI plans

## Priority Sources For Early Parsing

- GPay
- CRED
- Kotak
- SBI
- ICICI credit card alerts

## UX Direction

- minimal
- bold
- premium
- soft premium motion
- card-heavy surfaces
- dense but beautiful typography

References:

- Bump by Amo
- recent Apple UI sensibilities

## Dashboard Priorities

- budget remaining
- this week's spend
- upcoming dues
- recent activity

## Alerts To Support

- budget nearing limit
- recurring due soon
- credit card bill due

## Reporting Direction

- monthly summarization
- activity recap in a fun lightweight format
- keep email reports and export workflows in mind

## Current Docs

- [PRD](docs/rupee-prd.md)
- [Architecture](docs/rupee-architecture.md)
- [Schema](docs/rupee-schema.md)
- [Android Screen Spec](docs/rupee-android-screens.md)
- [Engineering Roadmap](docs/rupee-roadmap.md)

## Immediate Next Work (post-MVP / user-testing follow-ups)

1. **Onboarding revisit** — fresh-install Welcome → Permissions → Setup → Home flow hasn't been walked end-to-end on a clean device; eyeball each screen and fix anything that looks broken
2. **Gateway-merchant stripper** — bodies routed through Razorpay/PayU/BillDesk produce merchant strings like `"PAYTM-12345-RZRPAY-MERCH-XYZ"`. UI clamps with ellipsis now (v0.11.0); the data-level fix is a small alias/lookup layer that recognizes known gateway prefixes and either flags as "Unknown merchant" or maps to the underlying merchant
3. **Expanded parser coverage** — SBI, HDFC (plain, non-CRED), Axis, Kotak, Yes, Amazon Pay, Slice, Jupiter, Fi, Niyo, BHIM standalone, Mobikwik, ride-share receipts. Generic UPI / Generic fallback catches some today but with low confidence
4. **Cloud backup / restore** — none today. Tester data is gone on uninstall. Surfaced upfront in Settings → Privacy & data
5. **Empty-state review (12)** — visually check Home / Calendar / Inbox / Recap on a first-run device with zero data
6. **Hide the Debug pill behind `BuildConfig.DEBUG`** before any external test build (intentionally still visible per current dev preference; Reset is type-WIPE gated as of v0.11.0)
7. **Bottom-nav migration** — chip row works but Material3 `NavigationBar` is the spec; cosmetic
8. **Inbox merge guardrails extras** — picker filter by "same-merchant suggestion" + "amount within 20%"; an undo affordance
9. **Bucket-level budgeting** — needs transaction-to-bucket tagging first (probably category → bucket mapping + a per-tx override). Currently surfaced as "coming soon" in Budgets page
10. **Richer dedupe** — replace the flat `dedupeFingerprint` with `dedupe_groups` / `dedupe_group_members` once we observe fuzzy multi-source collisions
11. **Schema gap (low priority)** — `canonical_transaction_source_links`, `alert_rules` / `alert_events`, `monthly_recaps`, `emi_transaction_links`, `budget_category_assignments` (recurring_patterns shipped in v0.9.1)
12. **Auto-detection of EMIs and credit-card statement events from notifications** — partially covered by `EmiNotificationParser`; card-statement-detection parser is still missing

## Current Implementation State

- root Gradle configuration scaffolded
- Android app module scaffolded under `android/app`
- basic Compose `MainActivity` added
- initial Rupee theme placeholders added
- Room-based local database foundation added
- app-level database bootstrap added
- core local entities and DAOs added
- local repository shell added
- default local seed data added for user, categories, buckets, account, card, and sample transactions
- Home screen now reads Room-backed seeded state
- onboarding flow exists and persists completion
- notification permission flow exists with a real listener service declaration
- SMS permission has been removed from the current preview build and deferred
- local Java and Android toolchain is installed enough to compile and assemble a debug APK
- debug APK builds successfully
- raw notification capture events are stored locally
- raw notification events are normalized into parsed signals and transaction candidates
- parser registry is in place for notification normalization
- GPay, CRED, and ICICI parsers exist alongside the generic fallback parser
- a first decision layer now routes candidates to auto-created canonical transactions, Inbox, or ignore
- a first dedupe layer now suppresses repeated alerts using candidate fingerprints built from amount, mode, merchant/counterparty, masked digits, and a five-minute time bucket
- the app now has a basic Home / Inbox / Transactions shell — currently rendered as inline composables in `MainActivity` with chip-based tab switching, not a bottom-nav structure yet
- the Inbox surface supports initial confirm and dismiss review actions
- the Transactions surface supports initial merchant and notes editing for canonical transactions
- the Home surface is now a real daily dashboard: greeting + month label, hero monthly budget card with progress bar and over/near-limit accents, this-week spend module, quick actions row (Review Inbox, Add transaction placeholder), and recent activity list with a "Suggested" badge for unreviewed auto-created entries
- the seeded monthly-total budget is now scoped to the current month dynamically (previously hardcoded to March 2026), at ₹40,000 default; users can edit later
- new repository flows: `observeMonthlyTotalBudget(today)` and `observeSpentInPeriod(fromIso, untilIso)` — DAO-level SUM excludes IGNORED and `isHiddenFromBudget` rows
- auto-created canonical transactions from notifications now land in `SUGGESTED` status (not `CONFIRMED`) and are promoted to `CONFIRMED` only via the Inbox review path
- candidate decision states now include `USER_CONFIRMED` to distinguish system auto-creation from user confirmation
- DAO queries for recent transactions and inbox items now scope by `userId`
- Inbox is now a unified review queue — pending `InboxItem` candidates plus auto-created `SUGGESTED` canonical transactions both render in one list with edit-before-confirm (merchant + amount + category chips)
- Manual transaction entry is wired to the dashboard "Add transaction" button via a `ModalBottomSheet` form (merchant, amount, mode chip selector, category chips, notes)
- Settings tab exists with profile (display name), monthly budget edit, notification permission re-check, and read-only category/bucket lists; documented in `docs/rupee-settings-debug.md`
- Debug tab exists with three preset sample notifications (GPay/CRED/ICICI) that exercise the real ingestion pipeline, a parser playground that runs the parser registry against arbitrary input without persisting, and a confirm-gated reset that wipes the DB and re-seeds defaults
- Build now exposes versionName (currently `0.13.1`) via `BuildConfig`; the Home greeting renders a small `v0.13.1-debug` pill top-right and Settings → About reflects the same value. The debug APK output is renamed to `rupee-{versionName}-{buildType}.apk` so the file itself carries the version
- Recent activity, Inbox review, and Transactions tab all show cleaner merchant text via `cleanMerchant()` (trims " on / using / via" tails, prefers segment after " at " for CRED-style bodies). DB-level `merchantName` is left untouched
- Sublines now use `formatOccurredAt()` to render ISO instants as friendly local-time labels ("7 May, 11:29 PM") instead of raw timestamps
- Inbox review row uses a Material3 `DropdownMenu` for category selection; manual entry still uses chips since that form has more vertical room
- Transactions tab is now tap-to-view-modal: tap a row → `ModalBottomSheet` with formatted amount + merchant + subline + notes, plus Edit and Delete buttons. Edit toggles into the form. Delete soft-deletes via `status = IGNORED` behind an `AlertDialog` confirm. Inline editing on the list is gone
- Debug is no longer a top-level chip. It opens as a fullscreen Compose `Dialog` from a small "Debug" floating pill at the bottom-right of every tab, and from Settings → About → Open debug tools. The chip row is back to four entries (Home / Inbox / Transactions / Settings)
- `Manifest` declares `POST_NOTIFICATIONS`. `RupeeApplication.onCreate` registers a `rupee_debug` notification channel. The listener service bypasses its self-package filter when the incoming notification carries `RupeeApplication.DEBUG_MOCK_EXTRA`. A new Debug button posts an actual `NotificationManager.notify()` with editable Title/Body fields (plus three "Load from sample" buttons) so the *real* listener path can be exercised end-to-end without needing a third-party app on the device
- `RupeeNotificationListenerService` logs at INFO/DEBUG/WARN under the `RupeeNotifListener` tag. Filter logcat with `adb logcat -s RupeeNotifListener` to see whether the listener is bound, whether a posted notification reached `onNotificationPosted`, and why an event was skipped (empty body, self-pkg without mock extra, writer not initialized, duplicate fingerprint)
- New `GenericUpiNotificationParser` handles UPI-style "paid to … using UPI" bodies that are not from Google Pay (PhonePe, Paytm, BHIM, debug mocks attributed to our own package). `parserKey = "notification_upi_generic"`, `providerHint = "upi"`. Slotted in the registry between `GPayNotificationParser` (now strict) and `GenericNotificationParser`
- `CredNotificationParser.canParse` now matches a `\bCRED\b` word boundary (case-insensitive) plus a `com.dreamplug.androidapp` package check — previously `body.contains("cred")` was firing on the substring of "Credit Card", silently mis-routing every ICICI/HDFC card notification to the CRED parser
- First unit tests in the repo at `android/app/src/test/java/com/zegrt/rupee/ingestion/NotificationParserCanParseTest.kt`. Pure JVM, no Android dependencies. Run with `./gradlew :android:app:testDebugUnitTest`
- "Always trust this merchant" rule (v0.6.0): `MerchantTrustRuleEntity` + DAO with `(userId, merchantPattern)` unique index, schema bumped to v6 with destructive migration. Inbox confirm row exposes a `Switch` ("Always trust ${merchant}"); toggling it stores a rule via `LocalFinanceRepository.addMerchantTrustRule`. `NotificationSignalNormalizer` checks the rule list before applying the base decision — matched candidates skip the inbox and land directly as `CONFIRMED` canonical transactions with `createdBy = "trust_rule"` and the rule's `autoCategoryId` if set
- Merchant matching is centralized in `MerchantNameUtils` (`clean()` for display/matching, `matchesPattern()` for exact case-insensitive comparison on the cleaned form). Used by both UI surfaces and trust-rule matching so a "Swiggy" rule fires on a parsed "Swiggy using UPI"
- v0.6.1 fix: `addMerchantTrustRule` now stores the cleaned form of the pattern. Earlier the raw `candidate.toEntityName` (e.g. "Swiggy using UPI") was persisted as-is, but the matcher cleans the incoming raw merchant before comparing — so stored rules never fired. Round-trip regression covered in `MerchantNameUtilsTest`
- v0.6.2: Settings exposed a "Trusted merchants" surface to list and remove saved rules, backed by `SettingsViewModel.removeTrustRule` → `LocalFinanceRepository.removeMerchantTrustRule`. The ViewModel's entity-side `combine` arity grew from 4 to 5 to fold in `observeMerchantTrustRules()`
- v0.6.2: Transactions tab `TransactionDetailSheet` Edit form now includes a `CategoryDropdown` row, and the read-only view shows a "Category" line when the transaction has one. New `transactionCategoryDrafts` flow in `HomeViewModel`; `saveTransactionEdits` only re-applies category when the draft was touched (uses `applyCategory = true` on `repository.updateTransactionDetails`)
- v0.6.3: Promoted Trusted merchants from an inline Settings card to a tappable Settings row that opens a fullscreen `TrustRulesScreen` (Compose `Dialog`, mirroring the Debug pattern). The row shows rule count; each rule renders as its own card with a Remove action
- v0.7.0 (Milestone 8 first slice): `EmiPlanDao` is now wired (was registered in `RupeeDatabase` with no DAO). Repository exposes `observeEmiPlans`, `addEmiPlan`, `removeEmiPlan`. New `CardsEmisViewModel` + `CardsEmisScreen` rendered behind a Settings row → fullscreen Dialog. Lists existing credit cards with outstanding/limit/due labels, plus a manual EMI add flow (name, monthly amount, optional months remaining, optional next-due date, notes). EMIs surface a Remove action. Auto-detection of cards/EMIs from notifications still TODO
- v0.7.1: Home dashboard now renders an "Upcoming dues" card between weekly spend and quick actions. Sources from `observeCards()` (statementDueDate + statementDueAmountMinor) and `observeEmiPlans()` (nextDueAt + monthlyAmountMinor) within a 14-day horizon, sorted by days-away, with `Overdue Nd` / `Due today` / `Due tomorrow` / `Due in N days` / `Due d MMM` labels. Card is hidden when there are no qualifying dues. `HomeViewModel.dashboardData` outer combine grew to 3-arity to fold in the cards+emis flow
- v0.8.0 (Milestone 9 first cut): new `CalendarViewModel` + `CalendarScreen` rendered as a fifth chip ("Calendar") in the home tab row. Month grid (Mon–Sun, 6 weeks) shows compact daily-spend labels (₹k/L formatted) per day; today gets an outlined cell, selected day a primary container. Tapping a day opens a `ModalBottomSheet` listing that day's transactions. Prev/next chevrons walk months. Backed by a new `observeTransactionsInPeriod(fromIso, untilIso)` flow on the repository / `CanonicalTransactionDao`. Excludes IGNORED status
- v0.8.1: per-category budgets — `BudgetDao.observeCategoryBudgetsForDate` + `getCategoryBudgetForDate`; `CanonicalTransactionDao.observeSpentByCategoryInPeriod` returning `CategorySpend(categoryId, amountMinor)`. Repository: `observeCategoryBudgets(today)`, `observeSpentByCategory(from, until)`, `setCategoryBudgetLimit(categoryId, limitMinor, today)` (limit ≤ 0 deactivates the row instead of deleting). Initial `CategoryBudgetsViewModel` + screen lived behind a Settings entry
- v0.9.0: **Budgets Overview** unification (per audit fix). `BudgetsViewModel` + `BudgetsScreen` replace the prior split between a "Monthly budget" Settings card and a separate "Category budgets" page. Settings now exposes a single "Budgets" row → fullscreen Dialog containing the monthly hero (with progress bar and inline edit) followed by the per-category list. Removed old `CategoryBudgetsViewModel`/`CategoryBudgetsScreen` files. Monthly-budget edit fields on `SettingsViewModel` are now unused but left in place for now
- v0.9.1: **Recurring detection** (PRD §12.10 fix). Schema bumped to v7 (destructive migration); new `RecurringPatternEntity` + `RecurringPatternDao`. `RecurringDetectionEngine` runs over the last 120 days of non-IGNORED expenses, groups by cleaned merchant, and reports patterns with ≥3 occurrences, median spacing in [20,35] days, and amounts within ±20% of the median. Repository: `observeRecurringPatterns`, `confirmRecurringPattern`, `dismissRecurringPattern`, `removeRecurringPattern`, `refreshRecurringPatterns(today)` (called on app init and on resume). Auto-suggestions are rewritten on each refresh; user-confirmed and user-dismissed rows are preserved. New `RecurringViewModel` + `RecurringScreen` reachable from Settings → "Recurring" with Suggested / Confirmed sections (Confirm / Not recurring / Remove actions, plus a manual Refresh). Engine is unit-tested (`RecurringDetectionEngineTest`)
- v0.9.2: confirmed recurring patterns now appear in Home → Upcoming dues alongside cards/EMIs (`HomeDueKind.RECURRING`, "Recurring • Netflix" prefix). Plus a new **Monthly recap** surface (Settings → Monthly recap) — `RecapViewModel` + `RecapScreen` show total spent / txn count / avg-per-day / delta vs last month, top categories, top merchants, biggest transactions. Prev/next chevrons walk months. No new schema entity — computed from existing `observeTransactionsInPeriod` and `observeCategories`
- v0.10.0 (other-machine commits): real `MIGRATION_5_6` and `MIGRATION_6_7` replacing destructive migration; bottom-nav + inbox merge + bucket progress cards + EMI parser; PhonePe/Paytm parsers; `BudgetAlertManager`; `Mode.WALLET` added; expanded parser tests (`NotificationParserParseTest`)
- v0.10.1 (test fix): `MerchantNameUtils.clean` tightened — handles trailing bare " at" (returns "Unnamed"), leading tail keywords (`"using UPI"` → "Unnamed"), bare noise tokens (`"at"`/`"using"` → "Unnamed"). Tail set extended with `" successfully"`, `" was "` so payment notifications like `"Netflix successfully"` and `"Zomato was successful. UPI Ref"` clean down to the merchant. New `MerchantNameUtils.cleanForEntity(raw)` helper — returns null when raw has no usable merchant — applied to `toEntityName` in every parser so canonical merchantName is now stored cleaned (raw stays in `merchantRaw` for fingerprinting/trust matching)
- v0.10.2 (audit + bug-fix pass): (1) `SettingsViewModel.uiState` now folds `notificationGranted` into the `combine` — previously it was sampled as `.value` inside the transform, so the Settings card never re-emitted when the OS toggle flipped and stayed stuck on "required" after the user granted access. (2) Dropped the `". "` tail from `MerchantNameUtils.clean` (was clipping `"St. Patrick's Restaurant"` → `"St"`); `" was "` already covers the case it was added for, with a regression test. (3) `EmiNotificationParser.canParse` no longer fires on any "emi" substring — requires the word "emi" *and* a transaction signal (`debited`/`due`/`paid`/`charged`/`instalment`/…) so marketing copy and unrelated `"reminder"`-style bodies don't get routed into the EMI lane. Added 4 EMI canParse tests
- v0.11.0 (pre-test prep + MVP gaps): big shipment. **(a)** `DuesAlertManager` — system notifications 2 days before credit-card statement due, 2 days before EMI due, 1 day before recurring auto-debits. Dedupes per (item id, due date) in SharedPreferences; checks fire on app init and on resume. **(b)** EMI parser test coverage in `NotificationParserParseTest` (3 cases). **(c)** Bucket-budgets surface defers to a "coming soon" note in Budgets page — needs transaction-to-bucket tagging first. **(d)** Inbox merge UX already shows `→ {merchant}` + flips Confirm button to "Merge" when a target is picked (two-step soft confirm) — left as-is, additional dialog deferred. **(e)** Debug pill stays visible per user preference. **(f)** Pre-test prep additions: Settings → Supported notifications, Settings → Privacy & data, Settings → Feedback (emails `studioxero.biz@gmail.com` with device/app info), type-WIPE confirm on Reset, Debug → Wipe raw capture (clears raw_capture_events only, not derived transactions). **(g)** Lightweight crash reporting: `CrashReporter` installs `Thread.setDefaultUncaughtExceptionHandler` that appends stacktraces to a file; Debug → Crash log can view / email / clear. No Firebase / Sentry SDK. **(h)** Release signing scaffold in `build.gradle.kts` — reads keystore path/passwords from `local.properties`; keystore stays out of the repo. **(i)** UI: `maxLines = 1, overflow = Ellipsis` on merchant Text composables in Inbox + Transactions + Recent activity so gateway-style long merchant strings don't break the layout. **(j)** New `Mode.WALLET` (added in 02f70b3) preserved; `ParsedTransactionKind.REFUND` preserved
- v0.12.0 (deep-review fixes, PR #23 + 2 review-driven fixes — 7 commits, +1,525/-223): **(1)** Android 13+ `POST_NOTIFICATIONS` requested at runtime via an `ActivityResultContracts` launcher with auto-prompt on first launch and a re-prompt button in Settings → Notifications. Backup rules scope `rupee.db` + alert SharedPreferences away from both cloud backup and device-to-device transfer. Debug pill gated behind `BuildConfig.DEBUG` (was always-on); release builds reach Debug only via Settings → About → "Open debug tools". **(2)** BILL_DUE candidates from CRED/ICICI write parsed amount + `dueDateIso` into the matching `credit_card` row (matches by last-4 digits first; provider-hint fallback now requires a *single* match to avoid mis-writing across multiple ICICI cards). Manual "Set due" sheet on each Cards & EMIs row. EMI parser bumps confidence to 0.88 when amount + merchant + debit verb (`debited`/`auto-debit`/`deducted`) all present, so real EMI auto-debits auto-create as SUGGESTED. New `dueDateIso` field on `NotificationParseResult`. **(3)** Dedupe checks current AND previous 5-min bucket (catches boundary cases like 11:59:30 / 12:00:30). Raw insert + normalize + ingestionStatus update is one `withTransaction` block via `NotificationSignalNormalizer.ingestNotification`; `RawCaptureWriter` is gone. PhonePe/Paytm parsers drop "received" from refund signals (was misclassifying spends). `RecurringViewModel.refreshNow` passes `force=true` to bypass 30-min debounce. **(4)** Dead-code removal: unused Settings monthlyBudget draft state; `InboxDecisionState.EDITED` and `MERGED` enum values; `RecurringPatternDao.findByMerchantPattern`. Recurring engine filters to CONFIRMED-only (closes self-reinforcing loop where SUGGESTED auto-captures seeded their own recurring suggestions). Calendar uses `previousOrSame(MONDAY)`. Calendar + Recap VMs add `refreshOnResume` for midnight rollover; Recap tracks an `anchorMonth` invariant so the snap only fires when the user hasn't manually navigated to a non-current month. **(5)** Indexed trust-rule lookup via `MerchantTrustRuleDao.findByCleanedPattern` (cleaned form, hits the `(userId, merchantPattern)` partition; LOWER() prevents direct index use on the second column but userId-prefix still helps). `OnboardingPreferences.isCompletedAsync` moves first-launch SharedPreferences read off main thread. `CrashReporter` trims line-wise (≤4k lines) instead of byte-wise so UTF-8 codepoints can't be split; drops `exitProcess(2)` so Android's default UEH owns process termination. `RecapViewModel` sorts by raw `amountMinor` Long. Adaptive launcher icon (rupee glyph on dark navy) replaces system default. **(6)** 24 new unit tests: `DedupeFingerprintTest` (boundary bucket), `NotificationDecisionEngineTest` (full routing table), `NotificationParsingUtilsTest` (due-date extraction across three Indian phrasings). 93 total tests, 0 failures
- v0.13.1 (Sprint 1 of `docs/axio-takeaways.md`): **Notification extractor + 3 Axio-take-along items.** Fixes the core bug where real-world bank notifications never reach the parsers because we only read `EXTRA_TEXT` and most banks put the body in `EXTRA_BIG_TEXT`. **(1)** New `NotificationExtractor` walks the entire `Notification.extras` Bundle plus MessagingStyle messages, ticker, and action labels; returns `ExtractedNotification` with a deduplicated `combinedBody` rollup. Listener passes `combinedBody` as the body to parsers — captures content regardless of which field a bank chose. Group-summary notifications (`FLAG_GROUP_SUMMARY`) now skipped explicitly. **(2)** Schema bumped to v8 with `MIGRATION_7_8`: `networkReferenceId` + `networkReferenceType` columns on `parsed_signals` and `canonical_transactions`; `patternUid Long?` on `parsed_signals` (reserved for the upcoming JSON rule engine, null today); `excludeFromExpenseTotals` + `excludeFromIncomeTotals` on `accounts` and `credit_cards`. **(3)** `NotificationParsingUtils.extractNetworkReference` runs in the normalizer after every parser; captures UPI/IMPS/NEFT/RTGS/RRN tokens that every real notification carries and we previously discarded. Unblocks cross-stream dedupe in Sprint 2. **(4)** Spend-period SQL joins `accounts` + `credit_cards` so per-card "Exclude from spend totals" toggles take effect; toggle exposed on each Cards & EMIs card row. **(5)** Debug build only: every incoming notification's extras get JSONL-dumped to `getExternalFilesDir("notif-dumps")/dumps.jsonl`. Debug → "Notification dumps" surfaces the dump-file size with Share / Clear actions; FileProvider wired in the manifest. Bootstraps a real-world corpus for parser v2. **(6)** `NotificationExtractorTest` (11 cases, pure-JVM via Map fixtures) + 5 new `extractNetworkReference` cases in `NotificationParsingUtilsTest`. Total 109 unit tests, 0 failures.

## Known Gaps vs Schema and Architecture

These are intentional or unintentional omissions surfaced by a deep review. They are not bugs in current behavior; they are work that has not happened yet.

- `EmiPlanEntity` now has a DAO and repository methods (v0.7.0). Auto-detection from notifications is still pending.
- Schema entities not yet implemented in code: `canonical_transaction_source_links`, `dedupe_groups`, `dedupe_group_members`, `alert_rules`, `alert_events`, `monthly_recaps`, `emi_transaction_links`, `budget_category_assignments`. (`recurring_patterns` shipped in v0.9.2.)
- `CanonicalTransactionEntity` carries a flat `dedupeFingerprint` field; the schema models duplicate clusters via dedupe_groups join tables. The current flat field is a pragmatic shortcut, not the long-term shape.
- All five chip tabs (Home, Inbox, Transactions, Calendar, Settings) now exist (v0.8.0 added Calendar). Migration to a Material3 bottom-nav is still pending.
- No NavHost / navigation-compose in use yet. Onboarding → Home transitions are driven by an `OnboardingStep` enum in `MainActivity`.
- `HomeViewModel` is monolithic — owns Home summary, Inbox review, and Transaction edit state. Should split when surfaces grow.
- `LocalFinanceRepository.completeInitialSetup` hardcodes seed IDs (`account-bank-1`, `card-1`, `account-cash`) and a single 2026-03 budget period; needs a proper seeding service before MVP.
- No real alerting (budget/due/recurring) yet. `POST_NOTIFICATIONS` is now declared in the manifest, but only the Debug surface uses it — to post a mock notification the listener round-trips. Budget/due alert logic still has to be built.
- SMS ingestion is deferred; no manifest permissions, no reader, but a `RawCaptureSourceType.SMS` enum value exists for the future.
- Hardcoded confidence thresholds (`HIGH_CONFIDENCE = 0.85`, `MEDIUM_CONFIDENCE = 0.6`) live in `NotificationDecisionEngine`; no remote config or runtime tuning.

## External Product Research Notes

- Truecaller appears to get reliability by owning the input channel or UI surface, not by depending primarily on passive notification scraping.
- Their public behavior points to:
  - default SMS app behavior for Smart SMS and message categorization
  - their own push notification flows for call alerts
  - overlay permissions for presentation, not ingestion
- Product implication for Rupee:
  - notification ingestion is a valid Android-first v1 tactic
  - treat notifications as evidence, not source of truth
  - longer-term robustness likely requires stronger owned channels or integrations beyond notification listening alone

## Comparative Product Notes

Takeaways from reviewing a focused admissions tracker:

- strong product framing matters: users should understand the workflow from the primary navigation alone
- clear, task-based modules are useful when each one maps to a real repeated job
- the main dashboard should answer the highest-frequency questions immediately
- supporting bulk, admin, or maintenance tools should stay outside the primary user journey

Boundaries to preserve in Rupee:

- do not drift into a generic finance super-app because the architecture can support more modules
- do not ship privileged setup or admin elevation paths in production clients
- do not trust client-side role checks for sensitive capabilities
- do not let non-core tooling bloat the core capture -> explain -> review -> trust loop

## Notes For Future Codex Sessions

- Do not reframe this as a generic finance super-app.
- The product should stay focused on auto-capture + budgeting + dues.
- Do not make bank sync a dependency for usefulness.
- Treat deduplication and trust as first-order product problems.
- Keep raw source evidence separate from canonical finance records.
- Keep the top-level navigation obvious and tied to user jobs, not internal implementation buckets.
- Keep any future admin, import, debug, or migration tooling out of the main end-user flow.
- Before every commit, update `CONTEXT.md` and `README.md` if project scope or artifact inventory changed.
