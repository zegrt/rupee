# Rupee Engineering Roadmap

Date: March 27, 2026
Status: Draft v1
Author: Codex

## 1. Purpose

This roadmap turns the Rupee PRD, architecture, schema, and Android screen spec into an executable engineering plan.

It defines:

- milestone order
- dependency sequencing
- MVP cut line
- suggested branch slices
- delivery risks

Related docs:

- [PRD](rupee-prd.md)
- [Architecture](rupee-architecture.md)
- [Schema](rupee-schema.md)
- [Android Screen Spec](rupee-android-screens.md)

## 2. Delivery Philosophy

Rupee should not be built screen-first or parser-first in isolation.

The right sequence is:

1. app shell and local persistence
2. ingestion foundation
3. parser and canonical transaction pipeline
4. Inbox trust loop
5. dashboard and budgeting
6. cards, EMIs, and calendar
7. recap, sync, and web expansion

This keeps the product usable early while protecting the risky core: capture, dedupe, and trust.

## 3. MVP Definition

Rupee MVP is successful if one Android user can:

- grant notification access
- set up bank account, credit card, and optional cash account
- have real spend transactions auto-captured from supported sources
- avoid duplicate transaction entries across overlapping sources
- review ambiguous transactions in Inbox
- see monthly budget remaining and this week's spending
- track card due amount and due date
- track basic EMI obligations
- view spending on a calendar heatmap

MVP does not require:

- bank linking
- multi-user support
- iOS
- web app
- highly polished monthly recap delivery
- backend sync

## 4. Post-MVP Definition

Post-MVP work includes:

- backend sync
- account auth
- web app
- more parser coverage
- better recurring detection
- richer recap/report delivery
- export workflows
- stronger tuning and diagnostics

## 5. Milestone Plan

## Milestone 0: Repo and Tooling Foundation

Goal:

- create implementation-ready project structure

Includes:

- Android project scaffold
- package/module layout
- formatting and lint setup
- basic CI for build/test checks
- environment config conventions

Suggested branch:

- `chore/android-scaffold`

Exit criteria:

- app builds and launches
- repository structure is stable enough for feature work

## Milestone 1: Local Data Foundation

Goal:

- establish the app's canonical local model

Includes:

- Room schema setup
- entity models for:
  - users
  - accounts
  - credit cards
  - categories
  - buckets
  - canonical transactions
  - budgets
  - inbox items
  - EMI plans
- DAOs and repository interfaces
- seed/default data for categories, buckets, and alerts

Suggested branches:

- `feat/local-db-foundation`
- `feat/seed-default-data`

Dependencies:

- milestone 0

Exit criteria:

- app can store and query local finance entities
- schema migrations have a clear pattern

## Milestone 2: Onboarding and Setup

Goal:

- let the user enter the app and configure minimum required state

Includes:

- welcome flow
- notification permission screen
- SMS permission screen later
- initial account setup
- initial credit card setup
- optional cash setup
- settings entry points for permission recovery

Suggested branches:

- `feat/onboarding-flow`
- `feat/permissions-flow`
- `feat/account-setup`

Dependencies:

- milestones 0 and 1

Exit criteria:

- new user can finish setup and land on Home

## Milestone 3: Capture Layer

Goal:

- ingest raw signals from Android into local storage

Includes:

- notification listener implementation
- SMS reader implementation later
- capture allowlist/filtering
- `raw_capture_events` persistence
- ingestion diagnostics/logging path

Suggested branches:

- `feat/notification-ingestion`
- `feat/sms-ingestion` later
- `feat/raw-capture-storage`

Dependencies:

- milestones 0, 1, and 2

Exit criteria:

- supported raw notifications are captured reliably
- raw evidence can be inspected in debug flows

## Milestone 4: Parsing and Normalization

Goal:

- convert raw signals into structured candidates

Includes:

- parser registry
- provider-specific parser interface
- generic fallback parser
- normalized transaction candidate pipeline
- parser version tracking

Suggested branches:

- `feat/parser-registry`
- `feat/parser-gpay`
- `feat/parser-cred`
- `feat/parser-kotak`
- `feat/parser-sbi`
- `feat/parser-icici-card`
- `feat/normalization-pipeline`

Dependencies:

- milestone 3

Exit criteria:

- supported sources generate normalized candidates with structured fields

## Milestone 5: Dedupe and Confidence Engine

Goal:

- turn candidates into trustworthy decisions

Includes:

- duplicate scoring
- dedupe groups and member linking
- confidence scoring
- thresholds for auto-create / Inbox / ignore
- canonical transaction creation path
- provenance/source linking

Suggested branches:

- `feat/dedupe-engine`
- `feat/confidence-scoring`
- `feat/canonical-transaction-pipeline`

Dependencies:

- milestone 4

Exit criteria:

- overlapping source events can merge correctly
- high-confidence events create canonical transactions
- ambiguous events route to Inbox

## Milestone 6: Inbox and Transaction Surfaces

Goal:

- give the user control over uncertain data

Includes:

- Inbox list
- Inbox detail / review sheet
- confirm, edit, dismiss, merge flows
- transactions feed
- transaction detail/edit
- manual transaction entry

Suggested branches:

- `feat/inbox-list`
- `feat/inbox-review-flow`
- `feat/transactions-feed`
- `feat/transaction-detail-edit`

Dependencies:

- milestone 5

Exit criteria:

- user can resolve uncertain items quickly
- canonical transaction feed is usable

## Milestone 7: Dashboard and Budgets

Goal:

- make Rupee useful as a daily spending app

Includes:

- Home dashboard shell
- monthly remaining budget
- this week spend
- recent activity
- budget overview
- category budgets
- bucket budgets
- monthly total budget
- alerts for budget nearing limit

Suggested branches:

- `feat/home-dashboard`
- `feat/budget-engine`
- `feat/budget-ui`
- `feat/bucket-rollups`

Dependencies:

- milestones 1 and 6

Exit criteria:

- dashboard shows the main daily value
- budget health is visible and actionable

## Milestone 8: Cards, EMIs, and Recurring

Goal:

- support obligations and liabilities properly

Includes:

- Cards & EMIs overview
- credit card detail
- EMI module
- EMI inference + confirm flow
- recurring detection prompt flow
- due alerts

Suggested branches:

- `feat/cards-overview`
- `feat/card-detail`
- `feat/emi-module`
- `feat/recurring-detection`
- `feat/due-alerts`

Dependencies:

- milestones 1, 6, and 7

Exit criteria:

- card due information and EMI obligations are visible and manageable

## Milestone 9: Calendar and Recap

Goal:

- add the visual layer that makes Rupee feel complete

Includes:

- calendar heatmap
- due overlays
- selected-day transaction drill-in
- monthly recap screen foundation

Suggested branches:

- `feat/calendar-heatmap`
- `feat/monthly-recap`

Dependencies:

- milestones 6, 7, and 8

Exit criteria:

- user can visually browse spend by day
- a basic monthly recap exists in-app

## Milestone 10: Sync and Expansion

Goal:

- prepare Rupee for web and account-backed usage

Includes:

- backend API foundation
- auth/account model
- sync protocol
- export jobs
- web app bootstrap

Suggested branches:

- `feat/backend-foundation`
- `feat/auth-sync`
- `feat/export-jobs`
- `feat/web-bootstrap`

Dependencies:

- strong local product loop already working

Exit criteria:

- canonical data can sync to backend and power future web surfaces

## 6. MVP Cut Line

Hard MVP includes:

- milestones 0 through 9, but with limited scope inside each

Important note:

Milestone 10 is not MVP.

Within milestones 8 and 9, MVP can stay lighter:

- EMI support can be basic but real
- recurring detection can be prompt-first, not automation-heavy
- monthly recap can be in-app only

## 7. Technical Dependency Map

Core dependency order:

1. scaffold
2. local schema
3. onboarding and permissions
4. raw capture
5. parsers
6. dedupe/confidence
7. Inbox + transactions
8. dashboard + budgets
9. cards/EMIs/recurring
10. calendar + recap
11. sync/web

Important rule:

- do not build dashboard logic before canonical transaction flow is real
- do not build EMI UX before core transaction editing/linking exists
- do not start backend sync before local truth is stable

## 8. Suggested Git Branch Sequence

Recommended near-term branch order:

1. `docs/roadmap`
2. `chore/android-scaffold`
3. `feat/local-db-foundation`
4. `feat/onboarding-flow`
5. `feat/permissions-flow`
6. `feat/account-setup`
7. `feat/notification-ingestion`
8. `feat/sms-ingestion` later
9. `feat/parser-registry`
10. `feat/parser-gpay`
11. `feat/parser-cred`
12. `feat/parser-kotak`
13. `feat/parser-sbi`
14. `feat/parser-icici-card`
15. `feat/dedupe-engine`
16. `feat/confidence-scoring`
17. `feat/inbox-list`
18. `feat/inbox-review-flow`
19. `feat/transactions-feed`
20. `feat/home-dashboard`
21. `feat/budget-engine`
22. `feat/cards-overview`
23. `feat/emi-module`
24. `feat/calendar-heatmap`

## 9. Delivery Risks

## 9.1 Highest risk

- parser quality
- duplicate merging accuracy
- user trust in auto-created transactions

## 9.2 Medium risk

- permissions friction
- noisy Inbox if confidence tuning is weak
- card/EMI modeling complexity

## 9.3 Lower but important risk

- overdesigning UI before core ingestion works
- starting backend too early

## 10. Testing Strategy by Milestone

### Early milestones

- unit tests for entities and repositories
- instrumentation smoke tests for onboarding and local DB

### Ingestion milestones

- parser fixture tests
- normalization tests
- dedupe scenario tests
- confidence threshold tests

### Product milestones

- UI tests for Inbox flows
- budget rollup tests
- EMI linking tests
- alert trigger tests

## 11. Definition of Done

A milestone is done when:

- code exists
- happy-path UI works
- data is persisted correctly
- core edge cases are tested
- docs are updated if scope changed
- `README.md` and `CONTEXT.md` are updated before commit when needed

## 12. Immediate Next Step

After this roadmap, the next implementation step is:

- create branch `chore/android-scaffold`
- scaffold the Android project structure
- set up modules and baseline build config
