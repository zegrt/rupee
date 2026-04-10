# Rupee Context

Date: April 11, 2026
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

- [PRD](/Users/cyril/Personal/webdev/wallet/docs/rupee-prd.md)
- [Architecture](/Users/cyril/Personal/webdev/wallet/docs/rupee-architecture.md)
- [Schema](/Users/cyril/Personal/webdev/wallet/docs/rupee-schema.md)
- [Android Screen Spec](/Users/cyril/Personal/webdev/wallet/docs/rupee-android-screens.md)
- [Engineering Roadmap](/Users/cyril/Personal/webdev/wallet/docs/rupee-roadmap.md)

## Immediate Next Work

1. parser refinement using real notification samples
2. canonical transaction pipeline refinement
3. richer dedupe rules for fuzzy multi-source collisions
4. dedicated Inbox and debug views beyond the home inspection surface

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
- local toolchain is installed enough to compile and assemble a debug APK
- debug APK builds successfully
- raw notification capture events are stored locally
- raw notification events are normalized into parsed signals and transaction candidates
- parser registry is in place for notification normalization
- GPay, CRED, and ICICI parsers exist alongside the generic fallback parser
- a first decision layer now routes candidates to auto-created canonical transactions, Inbox, or ignore
- a first dedupe layer now suppresses repeated alerts using candidate fingerprints built from amount, mode, merchant/counterparty, masked digits, and a five-minute time bucket
- the home screen now doubles as a basic pipeline inspection surface for canonical transactions, recent candidates, and pending Inbox volume

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
