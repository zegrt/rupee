# Rupee Context

Date: May 7, 2026
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

## Immediate Next Work

1. round out the dashboard: upcoming dues strip (cards + EMIs + recurring), custom bucket progress cards, "Add transaction" wired to a manual-entry sheet
2. deepen the Inbox review flow beyond confirm and dismiss (edit-before-confirm, merge-with-existing, recategorize, "always trust this merchant" rule)
3. surface the SUGGESTED → CONFIRMED promotion in the Inbox (today only `INBOX_PENDING` candidates appear; `SUGGESTED` canonical rows from auto-creation also need a review path)
4. refine canonical transaction editing — category, split, attach to EMI, link back to source notification
5. parser refinement using real notification samples (depends on collecting real fixtures)
6. richer dedupe rules for fuzzy multi-source collisions (depends on observing real collisions)
7. wire `EmiPlanEntity` (currently registered in `RupeeDatabase` with no DAO)
8. add the missing schema entities once their UI surfaces are scoped: `canonical_transaction_source_links`, `dedupe_groups` / `dedupe_group_members`, `recurring_patterns`, `alert_rules` / `alert_events`, `monthly_recaps`

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

## Known Gaps vs Schema and Architecture

These are intentional or unintentional omissions surfaced by a deep review. They are not bugs in current behavior; they are work that has not happened yet.

- `EmiPlanEntity` is registered in `RupeeDatabase` but has no DAO and no repository methods.
- Schema entities not yet implemented in code: `canonical_transaction_source_links`, `dedupe_groups`, `dedupe_group_members`, `recurring_patterns`, `alert_rules`, `alert_events`, `monthly_recaps`, `emi_transaction_links`, `budget_category_assignments`.
- `CanonicalTransactionEntity` carries a flat `dedupeFingerprint` field; the schema models duplicate clusters via dedupe_groups join tables. The current flat field is a pragmatic shortcut, not the long-term shape.
- Architecture spec calls for a five-tab bottom nav (Home, Inbox, Transactions, Calendar, Settings); the app currently has Home/Inbox/Transactions only, rendered as chip tabs inside `MainActivity` rather than as separate routes.
- No NavHost / navigation-compose in use yet. Onboarding → Home transitions are driven by an `OnboardingStep` enum in `MainActivity`.
- `HomeViewModel` is monolithic — owns Home summary, Inbox review, and Transaction edit state. Should split when surfaces grow.
- `LocalFinanceRepository.completeInitialSetup` hardcodes seed IDs (`account-bank-1`, `card-1`, `account-cash`) and a single 2026-03 budget period; needs a proper seeding service before MVP.
- No alerting/notification-posting from the app yet. `POST_NOTIFICATIONS` permission is intentionally absent until budget/due alerts are implemented.
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
