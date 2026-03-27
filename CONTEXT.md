# Rupee Context

Date: March 27, 2026
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
- SMS messages

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
- notification and SMS ingestion are the primary capture methods
- SMS should be optional if notification coverage is enough
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

1. local database foundation
2. onboarding and permissions flow
3. ingestion pipeline foundation
4. parser registry and first provider parsers

## Current Implementation State

- root Gradle configuration scaffolded
- Android app module scaffolded under `android/app`
- basic Compose `MainActivity` added
- initial Rupee theme placeholders added
- no local database, navigation, or ingestion logic yet

## Notes For Future Codex Sessions

- Do not reframe this as a generic finance super-app.
- The product should stay focused on auto-capture + budgeting + dues.
- Do not make bank sync a dependency for usefulness.
- Treat deduplication and trust as first-order product problems.
- Keep raw source evidence separate from canonical finance records.
- Before every commit, update `CONTEXT.md` and `README.md` if project scope or artifact inventory changed.
