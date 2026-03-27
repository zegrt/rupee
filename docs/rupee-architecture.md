# Rupee Architecture

Date: March 27, 2026
Status: Draft v1
Author: Codex

## 1. Purpose

This document defines the technical architecture for Rupee v1.

It is meant to answer:

- how the Android app captures financial signals
- how raw alerts become canonical transactions
- how duplicate events are merged
- how confidence scoring works
- how budgets, cards, EMIs, and recurring spend attach to transaction data
- what should stay local vs what syncs to backend

This is the implementation companion to the product PRD in [rupee-prd.md](/Users/cyril/Personal/webdev/wallet/docs/rupee-prd.md).

## 2. Architecture Goals

- Android-first
- works offline for core usage
- supports low-manual-entry transaction tracking
- privacy-aware data handling
- future-ready for web app and account sync
- stable ingestion pipeline despite multiple noisy sources
- explicit separation between raw captured data and user-facing finance records

## 3. High-Level Architecture

Rupee should use a layered architecture with a clear data pipeline:

1. Capture Layer
2. Parsing Layer
3. Normalization Layer
4. Deduplication Layer
5. Confidence + Decision Layer
6. Canonical Finance Layer
7. Product Feature Layer
8. Sync / Backend Layer

### 3.1 Capture Layer

Responsible for collecting raw financial signals from the device.

Sources:

- Android notifications
- manual entry
- future CSV import
- SMS messages later

Output:

- raw captured events

### 3.2 Parsing Layer

Responsible for extracting structured fields from raw message text.

Examples:

- amount
- source account/card
- merchant/payee
- mode such as UPI, card, ATM, transfer
- sender/provider identity
- timestamp
- masked digits

Output:

- parsed candidate records

### 3.3 Normalization Layer

Responsible for converting parser-specific records into a common internal shape.

This is where all providers map into one transaction candidate format.

Output:

- normalized transaction candidates

### 3.4 Deduplication Layer

Responsible for detecting when multiple normalized candidates represent the same real-world spend event.

Output:

- dedupe groups
- canonical candidate with linked source references

### 3.5 Confidence + Decision Layer

Responsible for deciding:

- auto-create
- send to Inbox
- ignore

Output:

- transaction action decision

### 3.6 Canonical Finance Layer

Stores durable user-facing finance objects.

Examples:

- transactions
- accounts
- cards
- EMI plans
- recurring items
- budgets
- bucket assignments

### 3.7 Product Feature Layer

Builds user experiences on top of canonical objects.

Examples:

- dashboard
- Inbox
- budgets
- card detail
- EMI module
- calendar heatmap
- monthly recap

### 3.8 Sync / Backend Layer

Responsible for:

- authentication
- encrypted sync
- backups
- future web app reads/writes
- report generation

This layer should not be required for basic offline function.

## 4. Client / Server Split

### 4.1 Android client responsibilities

- onboarding
- permission flows
- notification listener
- SMS reader access later
- local persistence
- parsing
- normalization
- dedupe
- confidence scoring
- Inbox handling
- dashboard and all user flows
- offline-first behavior

### 4.2 Backend responsibilities

- authenticated account storage
- encrypted data sync
- multi-device support later
- report generation
- export jobs
- remote configuration for parser rules in future

### 4.3 Why keep ingestion local

- permissions are device-local
- privacy expectations are better when raw messages do not need to be uploaded first
- offline use remains possible
- avoids backend dependence for core value

## 5. Core Data Model

The system should explicitly separate raw device events from user-visible financial records.

### 5.1 RawCaptureEvent

Represents a single captured source event before parsing.

Fields:

- `id`
- `source_type`
  - `notification`
  - `sms`
  - `manual`
  - `csv`
- `source_app_package` nullable
- `sender_address` nullable
- `title` nullable
- `body`
- `received_at`
- `device_event_time` nullable
- `hash_fingerprint`
- `ingestion_status`
- `metadata_json`

Purpose:

- auditability
- debugging parsers
- reprocessing when parser logic improves

### 5.2 ParsedSignal

Represents structured data extracted from one raw event.

Fields:

- `id`
- `raw_capture_event_id`
- `parser_key`
- `parser_version`
- `provider_hint`
- `transaction_kind`
  - `spend`
  - `bill_due`
  - `statement`
  - `payment`
  - `emi`
  - `recurring_candidate`
  - `unknown`
- `amount_minor`
- `currency_code`
- `merchant_raw`
- `source_account_hint`
- `source_card_hint`
- `masked_digits`
- `mode`
- `event_occurred_at`
- `parse_confidence`
- `structured_json`

Purpose:

- preserve parser output separately from final transaction decisions

### 5.3 TransactionCandidate

Represents a normalized candidate event that may become a canonical transaction.

Fields:

- `id`
- `parsed_signal_id`
- `candidate_type`
  - `spend`
  - `cash_withdrawal`
  - `card_due`
  - `emi_due`
  - `transfer`
  - `unknown`
- `amount_minor`
- `currency_code`
- `from_entity_type`
  - `bank_account`
  - `credit_card`
  - `cash`
  - `unknown`
- `from_entity_hint`
- `to_entity_name`
- `mode`
- `occurred_at`
- `candidate_fingerprint`
- `normalization_version`

Purpose:

- common shape used by dedupe and decision engine

### 5.4 CanonicalTransaction

Represents a user-visible financial transaction.

Fields:

- `id`
- `user_id`
- `type`
  - `expense`
  - `income`
  - `transfer`
  - `cash_adjustment`
- `status`
  - `confirmed`
  - `suggested`
  - `ignored`
- `amount_minor`
- `currency_code`
- `account_id` nullable
- `credit_card_id` nullable
- `cash_account_id` nullable
- `merchant_name`
- `category_id` nullable
- `bucket_ids`
- `mode`
- `notes`
- `occurred_at`
- `source_summary`
- `created_by`
  - `auto`
  - `user`
  - `import`
- `confidence_tier`
- `similar_history_key` nullable

Purpose:

- source of truth for all spend reporting and budgeting

### 5.5 CanonicalTransactionSourceLink

Links a canonical transaction to all underlying evidence.

Fields:

- `id`
- `canonical_transaction_id`
- `raw_capture_event_id`
- `parsed_signal_id`
- `transaction_candidate_id`
- `link_type`
  - `primary`
  - `merged_duplicate`
  - `supporting`

Purpose:

- explainability
- debugging duplicate merges
- future trust UI

### 5.6 InboxItem

Represents a candidate that needs user review.

Fields:

- `id`
- `user_id`
- `transaction_candidate_id`
- `reason_code`
- `decision_state`
  - `pending`
  - `confirmed`
  - `edited`
  - `dismissed`
  - `merged`
- `created_at`
- `resolved_at` nullable

### 5.7 Account

Fields:

- `id`
- `user_id`
- `account_type`
  - `bank`
  - `cash`
- `display_name`
- `provider_name`
- `masked_identifier`
- `currency_code`
- `opening_balance_minor` nullable
- `current_balance_minor` nullable
- `is_active`

### 5.8 CreditCard

Fields:

- `id`
- `user_id`
- `display_name`
- `provider_name`
- `masked_identifier`
- `network` nullable
- `credit_limit_minor` nullable
- `statement_due_amount_minor` nullable
- `statement_due_date` nullable
- `current_outstanding_minor` nullable
- `available_limit_minor` nullable
- `is_active`

### 5.9 Category

Fields:

- `id`
- `user_id`
- `name`
- `icon_key` nullable
- `color_key` nullable
- `is_default`
- `is_archived`

### 5.10 Budget

Fields:

- `id`
- `user_id`
- `budget_type`
  - `monthly_total`
  - `category`
  - `bucket`
- `target_ref_id` nullable
- `limit_minor`
- `currency_code`
- `period_start`
- `period_end`
- `alert_threshold_percent`

### 5.11 Bucket

Represents a cross-category spending group.

Fields:

- `id`
- `user_id`
- `name`
- `description` nullable
- `color_key` nullable
- `is_default`

### 5.12 BucketCategoryMap

Maps categories into custom buckets.

Fields:

- `id`
- `bucket_id`
- `category_id`

### 5.13 RecurringPattern

Represents a recurring payment or due pattern.

Fields:

- `id`
- `user_id`
- `name`
- `merchant_name`
- `linked_account_id` nullable
- `linked_credit_card_id` nullable
- `expected_amount_minor` nullable
- `frequency`
- `next_due_at`
- `is_confirmed`

### 5.14 EMIPlan

Represents an EMI obligation.

Fields:

- `id`
- `user_id`
- `name`
- `linked_account_id` nullable
- `linked_credit_card_id` nullable
- `monthly_amount_minor`
- `remaining_tenure_months`
- `next_due_at`
- `total_outstanding_minor` nullable
- `source_type`
  - `inferred`
  - `manual`
- `is_confirmed`

### 5.15 AlertRule

Fields:

- `id`
- `user_id`
- `rule_type`
  - `budget_near_limit`
  - `recurring_due_soon`
  - `credit_card_due`
- `is_enabled`
- `config_json`

## 6. Data Relationships

Core relationships:

- one `RawCaptureEvent` may produce one or more `ParsedSignal`
- one `ParsedSignal` produces one `TransactionCandidate`
- many `TransactionCandidate` records may merge into one `CanonicalTransaction`
- one `CanonicalTransaction` belongs to one account or one card or one cash account
- one `CanonicalTransaction` may map to one category and multiple buckets
- one `EMIPlan` may be linked to many transactions over time
- one `RecurringPattern` may be linked to many transactions over time

## 7. Ingestion Pipeline

### 7.1 Notification ingestion flow

1. Notification listener receives posted notification
2. app checks source package allowlist / parser coverage
3. create `RawCaptureEvent`
4. run parser selection
5. create `ParsedSignal`
6. normalize into `TransactionCandidate`
7. attempt dedupe
8. calculate confidence
9. take action:
   - auto-create canonical transaction
   - Inbox
   - ignore

### 7.2 SMS ingestion flow

Deferred from the current preview build.

1. SMS reader receives or fetches message
2. validate sender and format
3. create `RawCaptureEvent`
4. same downstream flow as notifications

### 7.3 Manual entry flow

Manual entry should still produce canonical transactions, but without raw capture dependencies.

Manual entries may optionally create a supporting provenance record for consistency.

## 8. Parser Strategy

### 8.1 Parser design

Use provider-specific parsers behind a common interface.

Interface expectation:

- `canParse(rawEvent): boolean`
- `parse(rawEvent): ParsedSignalResult`

### 8.2 Parser priority

Initial parser coverage should focus on:

- GPay notifications
- CRED notifications
- Kotak notifications first, SMS later
- SBI notifications first, SMS later
- ICICI credit card alerts

### 8.3 Parser versioning

Every parsed result should store:

- parser key
- parser version

This allows reprocessing and regression analysis when formats change.

### 8.4 Parsing fallback

When provider-specific parsing fails:

- attempt generic UPI / bank alert pattern extraction
- if still unclear, mark low confidence or unknown

## 9. Normalization Rules

Normalization should standardize:

- currency to ISO code
- amount to minor units
- mode naming
- merchant/payee naming
- account/card hints
- event timestamp
- transaction kind

Example normalized modes:

- `upi`
- `credit_card`
- `debit_card`
- `bank_transfer`
- `cash`
- `atm`

## 10. Deduplication Design

### 10.1 Why this matters

Trust in Rupee will depend heavily on not showing the same spend multiple times.

### 10.2 Deduplication inputs

- amount match
- time proximity window
- merchant similarity
- mode similarity
- source account/card similarity
- known provider combinations

### 10.3 Proposed scoring approach

Compute a duplicate score from weighted signals.

High duplicate score:

- merge automatically

Medium duplicate score:

- show merge suggestion in Inbox

Low duplicate score:

- keep separate

### 10.4 Example duplicate pairs

- GPay success notification + SBI debit SMS once SMS support is added
- ICICI card spend alert + CRED card alert

### 10.5 Canonical merge policy

When merging duplicates:

- keep one canonical transaction
- preserve all source links
- preserve best available merchant/account metadata
- do not lose the original raw evidence

## 11. Confidence Scoring Design

Confidence score should be a numeric internal score mapped into tiers.

### 11.1 Suggested factors

- extraction completeness
- provider trust level
- mapped account/card certainty
- merchant clarity
- mode clarity
- duplicate certainty
- transaction kind certainty

### 11.2 Tier mapping

- `high`
  - auto-create
- `medium`
  - Inbox review
- `low`
  - ignore

### 11.3 Decision rule

Use configurable thresholds rather than hardcoded constants so tuning is possible later.

## 12. Categorization Strategy

### 12.1 v1 approach

Use a template category set that the user can modify.

Examples:

- Food
- Groceries
- Shopping
- Travel
- Bills
- Family
- Entertainment
- Health
- Cash
- EMIs
- Subscriptions

### 12.2 Assignment approach

Category assignment in v1 should use:

- parser hints
- merchant rules
- recent similar transaction history
- manual correction

### 12.3 Similar-history support

Store a derived `similar_history_key` based on merchant + mode + source pattern.

If a similar past transaction was categorized by the user, reuse that category as a suggestion or auto-fill signal.

## 13. Budget Engine

Budget engine should support three budget types:

- overall monthly budget
- category budget
- bucket budget

### 13.1 Spend inclusion rules

Include:

- confirmed expense transactions

Exclude:

- ignored transactions
- transfers
- non-spend reminders

### 13.2 Budget rollups

The dashboard should compute:

- total spent this month
- remaining monthly budget
- category usage vs limit
- bucket usage vs limit
- near-limit warnings

## 14. Credit Card and EMI Model

### 14.1 Credit card view

Credit cards are distinct from bank accounts.

Track:

- current outstanding
- due amount
- due date
- limit
- available limit
- spend history

### 14.2 EMI handling

EMIs should not just be "tagged expenses."

They need separate structure so the app can show:

- future burden
- installment schedule
- upcoming due state

### 14.3 EMI creation flow

Preferred v1:

1. infer likely EMI pattern from repeated structured alerts
2. ask user to confirm
3. create `EMIPlan`
4. attach future matching transactions when confidence is sufficient

Manual fallback must always exist.

## 15. Recurring Spend Detection

Use lightweight recurring detection in v1.

Detection signals:

- same merchant
- similar amount
- repeating cadence
- same source account/card

When confidence is reasonable:

- ask user if this is recurring

Do not silently create recurring plans without user confirmation in v1.

## 16. Calendar / Heatmap Model

The calendar should be driven from canonical transactions only.

### 16.1 Heatmap inputs

- total expense amount per day
- number of transactions per day

### 16.2 Overlay inputs

- recurring due dates
- credit card due dates
- EMI due dates

## 17. Alerts Engine

v1 alert types:

- budget nearing limit
- recurring due soon
- credit card bill due

Alert computation should run locally on-device in v1 where possible.

## 18. Offline and Sync Strategy

### 18.1 Offline-first behavior

The Android app should be fully usable for:

- ingestion
- Inbox review
- budgets
- dashboard viewing
- manual edits

### 18.2 Sync model

Use local-first persistence with background sync.

Suggested sync unit:

- user-owned canonical records
- supporting metadata
- selected raw/parsed provenance depending on privacy policy

### 18.3 Conflict model

Prefer:

- last-write-wins for user profile and preferences
- field-aware merges where feasible for transactions
- server-issued revision/version ids

## 19. Recommended Storage Model

### 19.1 On-device

Use a local relational store.

Suggested fit:

- SQLite via Room on Android

Why:

- structured finance records
- joins for budgets, categories, and source links
- reliable offline storage
- mature Android tooling

### 19.2 Backend

Use a relational backend as the system grows.

Good fit for v1 planning:

- Postgres-backed API

Reason:

- finance data is relational
- reporting and web app benefit from structured queries

## 20. Suggested Technology Direction

This is a directional recommendation, not a locked stack.

### Android

- Kotlin
- Jetpack Compose
- Room
- WorkManager

### Backend

- TypeScript or Kotlin service
- Postgres
- object storage only if attachments/reports require it later

### Web

- React-based app later, powered by same backend

## 21. Security and Privacy Considerations

- minimize raw message retention if not needed long-term
- encrypt sensitive data at rest and in transit
- clearly explain notification permissions, and only add SMS later if the value is strong enough
- allow deletion of transactions and supporting source evidence where practical
- do not upload raw data unnecessarily in v1

## 22. Build Sequence

### Milestone 1

- Android app shell
- local database
- manual accounts/cards/cash

### Milestone 2

- notification ingestion
- SMS ingestion later
- raw capture storage

### Milestone 3

- parsers
- normalization
- confidence scoring
- dedupe engine

### Milestone 4

- canonical transaction feed
- Inbox
- manual correction flows

### Milestone 5

- categories
- budgets
- custom buckets

### Milestone 6

- credit card detail
- EMI plans
- recurring detection

### Milestone 7

- dashboard polish
- heatmap calendar
- alerts
- monthly recap foundation

### Milestone 8

- sync backend
- auth
- web companion

## 23. Open Technical Questions

- how much raw captured content should be retained after parsing
- whether SMS and notification ingestion should share a parser registry or separate registries once SMS is added
- whether provenance data should sync in full or remain device-local by default
- exact duplicate time windows by provider pair
- whether bill payment events should be modeled in v1 or deferred

## 24. Immediate Next Artifacts

The next useful technical docs after this one are:

1. schema spec with example tables and field types
2. ingestion event flow diagrams
3. Android screen spec
4. milestone-based engineering roadmap
