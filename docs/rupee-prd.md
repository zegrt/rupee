# Rupee PRD

Date: March 27, 2026
Status: Draft v1
Author: Codex

## 1. Product Summary

Rupee is an Android-first personal finance app for India that tracks spending with minimal manual entry.

The product's current core value is automatic transaction capture from Android app notifications, with smart deduplication across sources like GPay, CRED, and bank alerts. SMS ingestion remains a planned later feature once the notification-first flow is stable enough to justify a more sensitive permission.

Rupee is not an accounting tool. It should feel calm, bold, and premium, while staying dense enough to be useful every day.

## 2. Product Vision

Make personal finance tracking effortless enough that a user can stay on top of money without manually logging every payment.

Rupee should answer these questions immediately:

- How much can I still spend this month?
- What did I spend this week?
- What is due soon?
- Which credit card and EMI obligations are coming up?
- Where is my money actually going?

## 3. Target User

### Primary user

- Single-user, personal use only
- India-based
- Uses Android
- Wants an expense tracker with budget help
- Uses multiple financial sources such as bank accounts, credit cards, UPI apps, and cash
- Wants near-zero manual entry

### Initial personal context

The initial usage assumption is based on:

- GPay
- CRED
- Kotak Bank
- SBI Bank
- ICICI credit cards

This should guide early parser priorities and sample data design, but not hardcode the app to only those providers.

## 4. Problem Statement

The user does not want to manually enter transactions but still wants a clean and reliable view of spending, budgets, card dues, and EMI obligations.

Existing apps often fail this user because they:

- depend on bank integrations that are hard to support in India
- are too generic or too manual
- do not handle Indian payment patterns well
- do not merge duplicate alerts from multiple sources
- do not make budgets and dues visible in a simple daily workflow

## 5. Product Principles

- Auto-capture first
- Manual fallback always available
- India-native transaction understanding
- Combined overview, deeper drill-down
- Budget guidance over finance complexity
- Privacy-aware by default
- Premium feel without visual clutter

## 6. Platform Strategy

### v1 platform

- Android only

Reason:

- Android can support notification access and later SMS-based ingestion
- This is the only realistic path for low-friction auto-entry in v1
- iPhone parity is not required initially

### Future platform direction

- Web app as a full companion app
- iOS considered later, but not a blocker for first release

## 7. Research-Based Constraints

### Notification and message ingestion

- Android supports reading system notifications through notification listener access.
- iOS does not provide the same cross-app notification-reading model for third-party apps.

This means Rupee must be designed around Android-first ingestion if auto-entry is the core differentiator.

### Bank-linking constraint

Direct bank sync should not be required for v1.

Reasons:

- India support is fragmented
- transaction ingestion from alerts is more aligned with the user's workflow
- v1 should prove usefulness before adding heavy integrations

## 8. Core Product Direction

Rupee is primarily:

- an expense tracker
- a budget helper
- a credit card and EMI organizer

It is not primarily:

- a bank aggregation product
- an investment app
- a tax tool
- a shared family finance app

## 9. Core User Flow

1. User installs Rupee on Android
2. User grants notification access
3. User manually sets up known accounts and cards
4. Rupee detects transactions from messages and notifications
5. Rupee deduplicates and classifies them
6. High-confidence transactions are auto-created
7. Medium-confidence transactions land in an Inbox for confirmation
8. User checks dashboard for budget remaining, weekly spend, and upcoming dues
9. User reviews detailed timelines, calendars, cards, and EMI views when needed

## 10. Confidence Model

### Purpose

The app should avoid making the user manually clean up every transaction while also avoiding obvious mistakes.

### Confidence tiers

- High confidence:
  - auto-create transaction
- Medium confidence:
  - send to Inbox for confirmation
- Low confidence:
  - ignore

### Confidence inputs

- parser match quality
- sender trust
- account/card mapping certainty
- transaction type clarity
- duplicate-match certainty

### Examples

High confidence:

- clear amount
- clear merchant/payee
- clear source account/card or masked digits
- clear payment mode such as UPI or card

Medium confidence:

- amount found but merchant unclear
- likely spend transaction but account mapping ambiguous

Low confidence:

- promotional or marketing messages
- statement reminders with no spend event
- generic notifications that do not represent a transaction

## 11. Deduplication Model

Deduplication is mandatory.

The same spend may be reported by:

- multiple notification sources and, later, SMS + notification
- bank app + UPI app
- card app + CRED

### Deduplication approach

Create a merge candidate when records are close on:

- amount
- timestamp window
- merchant/payee similarity
- source account/card similarity
- transaction mode

The system should prefer one canonical transaction record with multiple source references, not multiple visible transactions for the same spend.

## 12. MVP Scope

### 12.1 Ingestion

- Notification ingestion
- SMS ingestion later
- Source tagging per transaction
- Parser framework for Indian transaction formats
- Initial support focused on:
  - GPay
  - CRED
  - Kotak
  - SBI
  - ICICI credit card alerts

### 12.2 Transaction Inbox

- Review queue for medium-confidence transactions
- Confirm, edit, merge, dismiss actions
- Show why the app is unsure when possible

### 12.3 Accounts

- Manual setup for bank accounts
- Manual setup for credit cards
- Optional cash-on-hand account
- Balance fields where relevant
- Account details page

### 12.4 Transactions

- Auto-created transactions
- Manual add/edit/delete
- Fields:
  - amount
  - category
  - source
  - from account/card
  - to merchant/payee
  - mode
  - notes
  - similar-history reference
- Search and filters
- Combined feed view
- Detail view per transaction

### 12.5 Categories

- Default editable template set
- User can add, remove, and rename categories
- Category tagging on every spend transaction

### 12.6 Budgets

- Monthly overall budget
- Per-category budgets
- Custom bucket budgets

### 12.7 Custom Buckets

Custom buckets are cross-category tracking groups.

Example default suggestions:

- Wants
- Subscriptions
- Food Out
- Family
- EMIs
- Travel
- Essentials

Custom buckets may include multiple categories and are meant to answer higher-level questions like:

- how much went to wants vs essentials
- how much fixed burden exists this month
- how much discretionary spend is creeping up

### 12.8 Credit Card Tracking

- Current outstanding
- Statement due amount
- Due date
- Available limit
- Spend timeline

### 12.9 EMI Tracking

EMI tracking should exist as its own module, separate from generic spend.

Track:

- monthly EMI amount
- remaining tenure
- next due date
- total outstanding
- linked account/card if relevant

Preferred v1 behavior:

- infer EMI-like patterns automatically
- ask the user to confirm the first time
- allow manual creation and correction at any time

### 12.10 Recurring Spend

- Detect recurring-like patterns from history
- Ask the user to confirm if a charge is recurring
- Show upcoming recurring items in dashboard and planning views

### 12.11 Dashboard

Top priorities:

- budget remaining
- this week's spend
- upcoming dues
- recent activity

Secondary widgets:

- top categories this month
- credit card due summary
- EMI burden this month
- custom bucket progress

### 12.12 Calendar / Heatmap View

- expenditure heatmap by day
- recurring and due items surfaced at the top
- drill-in from day to transactions

### 12.13 Alerts

- budget nearing limit
- recurring due soon
- credit card bill due

### 12.14 Reports and Export

Build with reporting and export in mind from v1.

Initial support:

- monthly summary report
- activity recap in a fun lightweight format
- export-friendly data model
- CSV export
- report email capability in future-ready architecture

## 13. UX Direction

### Product feel

- minimal
- bold
- premium

### Visual references

- Bump by Amo
- recent Apple UI sensibilities

### Interaction qualities

- soft premium motion
- card-based surfaces
- dense but beautiful typography
- strong summaries with low clutter

### UX structure

- combined dashboard first
- deeper detailed views on tap
- fast, calm review flow for Inbox cleanup

## 14. Information Architecture

Primary sections:

- Home
- Inbox
- Transactions
- Budgets
- Cards & EMIs
- Calendar
- Settings

## 15. Functional Requirements

### Must-have

- Android onboarding and permissions flow
- Notification access flow
- SMS access flow deferred from current preview build
- Manual account/card setup
- Transaction parsing pipeline
- Deduplication engine
- Confidence scoring
- Auto-create + Inbox fallback logic
- Transaction CRUD
- Budget management
- Custom buckets
- Credit card tracking
- EMI module
- Calendar heatmap
- Alerts

### Important but can be staged within v1

- richer similar-history suggestions
- recurring detection improvements
- polished monthly recap generation

## 16. Non-Goals

- Bank account linking
- Shared family/couple collaboration
- Investment tracking as a first-class feature
- Payments or money transfer
- Tax workflows
- Desktop-first design

## 17. Privacy and Data Principles

- Build with privacy in mind
- Only request permissions needed for ingestion and app functionality
- Be transparent about what is read and why
- Let users correct or delete inferred data
- Support offline usage where practical
- Use backend sync architecture without making the app unusable offline

## 18. Sync and Architecture Direction

### Desired model

- Android app works offline for core capture and viewing
- backend exists for account-based sync, backups, reports, and future web app
- sync happens when online

### Why this model

- supports eventual full web app
- supports report delivery and export workflows
- avoids locking the app into device-only storage

## 19. Success Metrics

### Activation

- % of users granting notification access
- % of users setting up at least one bank account and one credit card
- % of users with first auto-created transaction within 24 hours

### Quality

- duplicate rate visible to user
- false positive transaction rate
- Inbox confirmation rate
- parser success rate by source

### Engagement

- weekly dashboard opens
- budget checks per week
- recurring / due reminders acted on
- monthly summary views

## 20. Risks

- parsing quality may vary widely by provider and message format
- duplicate handling may become the main trust issue
- notification permissions may feel invasive if value is not clear quickly, and SMS is deferred specifically to reduce that risk
- too many inferred mistakes will kill confidence in the app
- design can become cluttered if cards, budgets, EMIs, and alerts compete for attention

## 21. Open Questions for Later

- exact parser rollout order by provider
- whether SMS access is still worth adding after notification support matures
- when to add web app relative to Android launch
- whether monthly reports are in-app first, email first, or both
- whether card bill payment events should be modeled in v1 or deferred

## 22. Recommended v1 Build Order

1. Android onboarding, permissions, and manual account/card setup
2. Notification ingestion pipeline
3. notification-first ingestion pipeline
4. Parser + confidence scoring + dedupe engine
5. Inbox and transaction feed
6. Budgets and custom buckets
7. Credit card and EMI modules
8. Calendar heatmap and dashboard polish
9. Alerts and monthly recap

## 23. Sources

Research checked on March 27, 2026.

- Android notification listener docs: https://developer.android.com/reference/android/service/notification/NotificationListenerService
- Apple notification docs overview: https://developer.apple.com/documentation/usernotifications/asking-permission-to-use-notifications
- Plaid country and product support context: https://support.plaid.com/hc/en-us/articles/27895826947735-What-Plaid-products-are-supported-in-each-country-and-region
- India Account Aggregator framework context: https://www.financialservices.gov.in/beta/index.php/en/account-aggregator-framework
