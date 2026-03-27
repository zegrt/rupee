# Rupee Schema Spec

Date: March 27, 2026
Status: Draft v1
Author: Codex

## 1. Purpose

This document defines the v1 data schema for Rupee.

It translates the architecture into concrete storage entities for:

- Android local database
- future backend sync model
- user-facing finance records
- raw evidence and parser outputs

This schema is designed for:

- offline-first Android usage
- source-evidence retention for trust and debugging
- deduplication across multiple signals
- support for budgets, credit cards, EMIs, recurring items, alerts, and reports

Related docs:

- [PRD](/Users/cyril/Personal/webdev/wallet/docs/rupee-prd.md)
- [Architecture](/Users/cyril/Personal/webdev/wallet/docs/rupee-architecture.md)

## 2. Schema Design Principles

- separate raw evidence from canonical finance data
- prefer append-and-link over destructive overwrites for ingestion history
- keep user-facing queries fast on-device
- model cards and accounts separately
- allow inferred data to be corrected manually
- keep local IDs and sync IDs compatible

## 3. Storage Strategy

### Local database

Recommended:

- SQLite via Room

### Backend database

Recommended:

- Postgres with matching logical entities

### ID strategy

Use UUID-style string IDs or ULIDs consistently across local and server models.

Suggested fields:

- `id`
- `server_id` nullable if local-first sync is used
- `sync_status`
- `updated_at`
- `created_at`

## 4. Enum Definitions

### 4.1 source_type

- `notification`
- `sms`
- `manual`
- `csv`

### 4.2 transaction_kind

Used in parsed signals.

- `spend`
- `bill_due`
- `statement`
- `payment`
- `emi`
- `recurring_candidate`
- `unknown`

### 4.3 candidate_type

Used in normalized candidates.

- `spend`
- `cash_withdrawal`
- `card_due`
- `emi_due`
- `transfer`
- `unknown`

### 4.4 canonical_transaction_type

- `expense`
- `income`
- `transfer`
- `cash_adjustment`

### 4.5 canonical_transaction_status

- `confirmed`
- `suggested`
- `ignored`

### 4.6 confidence_tier

- `high`
- `medium`
- `low`

### 4.7 entity_type

- `bank_account`
- `credit_card`
- `cash`
- `unknown`

### 4.8 mode

- `upi`
- `credit_card`
- `debit_card`
- `bank_transfer`
- `cash`
- `atm`
- `other`

### 4.9 budget_type

- `monthly_total`
- `category`
- `bucket`

### 4.10 inbox_reason_code

- `medium_confidence`
- `possible_duplicate_conflict`
- `missing_account_mapping`
- `missing_category`
- `ambiguous_merchant`
- `ambiguous_kind`

### 4.11 inbox_decision_state

- `pending`
- `confirmed`
- `edited`
- `dismissed`
- `merged`

### 4.12 alert_rule_type

- `budget_near_limit`
- `recurring_due_soon`
- `credit_card_due`

### 4.13 sync_status

- `local_only`
- `pending_upload`
- `synced`
- `pending_delete`
- `sync_error`

## 5. Core Tables

## 5.1 users

Single-user app in practice for v1, but keep the schema user-scoped.

Columns:

- `id` text primary key
- `email` text nullable
- `display_name` text nullable
- `default_currency_code` text not null
- `country_code` text not null
- `timezone` text not null
- `week_start_day` integer not null
- `month_start_day` integer not null default 1
- `created_at` text not null
- `updated_at` text not null
- `sync_status` text not null

Indexes:

- unique index on `email` where not null

## 5.2 raw_capture_events

Stores untouched captured signals from Android sources.

Columns:

- `id` text primary key
- `user_id` text not null
- `source_type` text not null
- `source_app_package` text nullable
- `sender_address` text nullable
- `title` text nullable
- `body` text not null
- `received_at` text not null
- `device_event_time` text nullable
- `hash_fingerprint` text not null
- `ingestion_status` text not null
- `metadata_json` text nullable
- `created_at` text not null
- `updated_at` text not null
- `sync_status` text not null

Foreign keys:

- `user_id -> users.id`

Indexes:

- index on `user_id`
- index on `source_type`
- index on `source_app_package`
- index on `sender_address`
- index on `received_at`
- unique index on `hash_fingerprint`

Notes:

- `hash_fingerprint` should be stable enough to suppress duplicate raw ingestion of the exact same source event.

## 5.3 parsed_signals

Stores parser output for each raw capture.

Columns:

- `id` text primary key
- `user_id` text not null
- `raw_capture_event_id` text not null
- `parser_key` text not null
- `parser_version` text not null
- `provider_hint` text nullable
- `transaction_kind` text not null
- `amount_minor` integer nullable
- `currency_code` text nullable
- `merchant_raw` text nullable
- `source_account_hint` text nullable
- `source_card_hint` text nullable
- `masked_digits` text nullable
- `mode` text nullable
- `event_occurred_at` text nullable
- `parse_confidence` real not null
- `structured_json` text nullable
- `created_at` text not null
- `updated_at` text not null
- `sync_status` text not null

Foreign keys:

- `user_id -> users.id`
- `raw_capture_event_id -> raw_capture_events.id`

Indexes:

- index on `user_id`
- index on `raw_capture_event_id`
- index on `provider_hint`
- index on `transaction_kind`
- index on `event_occurred_at`

## 5.4 transaction_candidates

Stores normalized candidate transactions before final user-facing decision.

Columns:

- `id` text primary key
- `user_id` text not null
- `parsed_signal_id` text not null
- `candidate_type` text not null
- `amount_minor` integer nullable
- `currency_code` text nullable
- `from_entity_type` text not null
- `from_entity_hint` text nullable
- `to_entity_name` text nullable
- `mode` text nullable
- `occurred_at` text nullable
- `candidate_fingerprint` text nullable
- `normalization_version` text not null
- `created_at` text not null
- `updated_at` text not null
- `sync_status` text not null

Foreign keys:

- `user_id -> users.id`
- `parsed_signal_id -> parsed_signals.id`

Indexes:

- index on `user_id`
- index on `parsed_signal_id`
- index on `candidate_type`
- index on `occurred_at`
- index on `candidate_fingerprint`

## 5.5 dedupe_groups

Represents a possible or confirmed duplicate cluster.

Columns:

- `id` text primary key
- `user_id` text not null
- `duplicate_score` real not null
- `decision_state` text not null
- `canonical_candidate_id` text nullable
- `created_at` text not null
- `updated_at` text not null
- `sync_status` text not null

Foreign keys:

- `user_id -> users.id`
- `canonical_candidate_id -> transaction_candidates.id`

Indexes:

- index on `user_id`
- index on `decision_state`
- index on `canonical_candidate_id`

## 5.6 dedupe_group_members

Columns:

- `id` text primary key
- `dedupe_group_id` text not null
- `transaction_candidate_id` text not null
- `match_score` real not null
- `created_at` text not null

Foreign keys:

- `dedupe_group_id -> dedupe_groups.id`
- `transaction_candidate_id -> transaction_candidates.id`

Indexes:

- index on `dedupe_group_id`
- index on `transaction_candidate_id`
- unique index on `dedupe_group_id, transaction_candidate_id`

## 5.7 accounts

For bank accounts and cash accounts.

Columns:

- `id` text primary key
- `user_id` text not null
- `account_type` text not null
- `display_name` text not null
- `provider_name` text nullable
- `masked_identifier` text nullable
- `currency_code` text not null
- `opening_balance_minor` integer nullable
- `current_balance_minor` integer nullable
- `is_active` integer not null default 1
- `sort_order` integer not null default 0
- `created_at` text not null
- `updated_at` text not null
- `sync_status` text not null

Foreign keys:

- `user_id -> users.id`

Indexes:

- index on `user_id`
- index on `account_type`
- index on `provider_name`
- index on `is_active`

Notes:

- `account_type` should be limited to `bank` and `cash` in v1.

## 5.8 credit_cards

Columns:

- `id` text primary key
- `user_id` text not null
- `display_name` text not null
- `provider_name` text nullable
- `masked_identifier` text nullable
- `network` text nullable
- `credit_limit_minor` integer nullable
- `statement_due_amount_minor` integer nullable
- `statement_due_date` text nullable
- `current_outstanding_minor` integer nullable
- `available_limit_minor` integer nullable
- `is_active` integer not null default 1
- `sort_order` integer not null default 0
- `created_at` text not null
- `updated_at` text not null
- `sync_status` text not null

Foreign keys:

- `user_id -> users.id`

Indexes:

- index on `user_id`
- index on `provider_name`
- index on `statement_due_date`
- index on `is_active`

## 5.9 categories

Columns:

- `id` text primary key
- `user_id` text not null
- `name` text not null
- `icon_key` text nullable
- `color_key` text nullable
- `is_default` integer not null default 0
- `is_archived` integer not null default 0
- `sort_order` integer not null default 0
- `created_at` text not null
- `updated_at` text not null
- `sync_status` text not null

Foreign keys:

- `user_id -> users.id`

Indexes:

- index on `user_id`
- index on `is_archived`
- unique index on `user_id, name`

## 5.10 buckets

Custom budget grouping above categories.

Columns:

- `id` text primary key
- `user_id` text not null
- `name` text not null
- `description` text nullable
- `color_key` text nullable
- `is_default` integer not null default 0
- `sort_order` integer not null default 0
- `created_at` text not null
- `updated_at` text not null
- `sync_status` text not null

Foreign keys:

- `user_id -> users.id`

Indexes:

- index on `user_id`
- unique index on `user_id, name`

## 5.11 bucket_category_maps

Maps one category into one or more custom buckets.

Columns:

- `id` text primary key
- `bucket_id` text not null
- `category_id` text not null
- `created_at` text not null
- `updated_at` text not null
- `sync_status` text not null

Foreign keys:

- `bucket_id -> buckets.id`
- `category_id -> categories.id`

Indexes:

- index on `bucket_id`
- index on `category_id`
- unique index on `bucket_id, category_id`

## 5.12 canonical_transactions

Main user-facing finance table.

Columns:

- `id` text primary key
- `user_id` text not null
- `type` text not null
- `status` text not null
- `amount_minor` integer not null
- `currency_code` text not null
- `account_id` text nullable
- `credit_card_id` text nullable
- `cash_account_id` text nullable
- `merchant_name` text nullable
- `category_id` text nullable
- `mode` text nullable
- `notes` text nullable
- `occurred_at` text not null
- `source_summary` text nullable
- `created_by` text not null
- `confidence_tier` text nullable
- `similar_history_key` text nullable
- `is_hidden_from_budget` integer not null default 0
- `created_at` text not null
- `updated_at` text not null
- `sync_status` text not null

Foreign keys:

- `user_id -> users.id`
- `account_id -> accounts.id`
- `credit_card_id -> credit_cards.id`
- `cash_account_id -> accounts.id`
- `category_id -> categories.id`

Indexes:

- index on `user_id`
- index on `occurred_at`
- index on `category_id`
- index on `account_id`
- index on `credit_card_id`
- index on `type`
- index on `status`
- index on `similar_history_key`

Constraints:

- exactly one of `account_id`, `credit_card_id`, `cash_account_id` should be set for expense-origin records in v1

## 5.13 canonical_transaction_source_links

Links canonical transactions to evidence and candidates.

Columns:

- `id` text primary key
- `canonical_transaction_id` text not null
- `raw_capture_event_id` text nullable
- `parsed_signal_id` text nullable
- `transaction_candidate_id` text nullable
- `link_type` text not null
- `created_at` text not null

Foreign keys:

- `canonical_transaction_id -> canonical_transactions.id`
- `raw_capture_event_id -> raw_capture_events.id`
- `parsed_signal_id -> parsed_signals.id`
- `transaction_candidate_id -> transaction_candidates.id`

Indexes:

- index on `canonical_transaction_id`
- index on `raw_capture_event_id`
- index on `parsed_signal_id`
- index on `transaction_candidate_id`

## 5.14 transaction_bucket_assignments

Allows a canonical transaction to appear in one or more buckets.

Columns:

- `id` text primary key
- `canonical_transaction_id` text not null
- `bucket_id` text not null
- `assignment_source` text not null
- `created_at` text not null
- `updated_at` text not null
- `sync_status` text not null

Foreign keys:

- `canonical_transaction_id -> canonical_transactions.id`
- `bucket_id -> buckets.id`

Indexes:

- index on `canonical_transaction_id`
- index on `bucket_id`
- unique index on `canonical_transaction_id, bucket_id`

## 5.15 inbox_items

Stores candidates needing review.

Columns:

- `id` text primary key
- `user_id` text not null
- `transaction_candidate_id` text not null
- `reason_code` text not null
- `decision_state` text not null
- `linked_canonical_transaction_id` text nullable
- `created_at` text not null
- `resolved_at` text nullable
- `updated_at` text not null
- `sync_status` text not null

Foreign keys:

- `user_id -> users.id`
- `transaction_candidate_id -> transaction_candidates.id`
- `linked_canonical_transaction_id -> canonical_transactions.id`

Indexes:

- index on `user_id`
- index on `decision_state`
- index on `transaction_candidate_id`
- index on `created_at`

## 5.16 recurring_patterns

Columns:

- `id` text primary key
- `user_id` text not null
- `name` text not null
- `merchant_name` text nullable
- `linked_account_id` text nullable
- `linked_credit_card_id` text nullable
- `expected_amount_minor` integer nullable
- `frequency` text not null
- `next_due_at` text nullable
- `is_confirmed` integer not null default 0
- `last_matched_transaction_id` text nullable
- `created_at` text not null
- `updated_at` text not null
- `sync_status` text not null

Foreign keys:

- `user_id -> users.id`
- `linked_account_id -> accounts.id`
- `linked_credit_card_id -> credit_cards.id`
- `last_matched_transaction_id -> canonical_transactions.id`

Indexes:

- index on `user_id`
- index on `next_due_at`
- index on `is_confirmed`

## 5.17 emi_plans

Columns:

- `id` text primary key
- `user_id` text not null
- `name` text not null
- `linked_account_id` text nullable
- `linked_credit_card_id` text nullable
- `monthly_amount_minor` integer not null
- `remaining_tenure_months` integer nullable
- `next_due_at` text nullable
- `total_outstanding_minor` integer nullable
- `source_type` text not null
- `is_confirmed` integer not null default 0
- `notes` text nullable
- `created_at` text not null
- `updated_at` text not null
- `sync_status` text not null

Foreign keys:

- `user_id -> users.id`
- `linked_account_id -> accounts.id`
- `linked_credit_card_id -> credit_cards.id`

Indexes:

- index on `user_id`
- index on `next_due_at`
- index on `is_confirmed`

## 5.18 emi_transaction_links

Links spend transactions to an EMI plan.

Columns:

- `id` text primary key
- `emi_plan_id` text not null
- `canonical_transaction_id` text not null
- `link_confidence` real nullable
- `created_at` text not null

Foreign keys:

- `emi_plan_id -> emi_plans.id`
- `canonical_transaction_id -> canonical_transactions.id`

Indexes:

- index on `emi_plan_id`
- index on `canonical_transaction_id`
- unique index on `emi_plan_id, canonical_transaction_id`

## 5.19 budgets

Columns:

- `id` text primary key
- `user_id` text not null
- `budget_type` text not null
- `target_ref_id` text nullable
- `limit_minor` integer not null
- `currency_code` text not null
- `period_start` text not null
- `period_end` text not null
- `alert_threshold_percent` real not null
- `is_active` integer not null default 1
- `created_at` text not null
- `updated_at` text not null
- `sync_status` text not null

Foreign keys:

- `user_id -> users.id`

Indexes:

- index on `user_id`
- index on `budget_type`
- index on `period_start, period_end`
- index on `is_active`

Notes:

- `target_ref_id` references `categories.id` for category budgets and `buckets.id` for bucket budgets
- it remains null for `monthly_total`

## 5.20 alert_rules

Columns:

- `id` text primary key
- `user_id` text not null
- `rule_type` text not null
- `is_enabled` integer not null default 1
- `config_json` text nullable
- `created_at` text not null
- `updated_at` text not null
- `sync_status` text not null

Foreign keys:

- `user_id -> users.id`

Indexes:

- index on `user_id`
- index on `rule_type`

## 5.21 alert_events

Tracks generated alert instances so the app does not spam the user.

Columns:

- `id` text primary key
- `user_id` text not null
- `rule_type` text not null
- `target_ref_id` text nullable
- `message` text not null
- `triggered_at` text not null
- `acknowledged_at` text nullable
- `created_at` text not null
- `updated_at` text not null
- `sync_status` text not null

Foreign keys:

- `user_id -> users.id`

Indexes:

- index on `user_id`
- index on `rule_type`
- index on `triggered_at`

## 5.22 monthly_recaps

Stores generated monthly summary metadata.

Columns:

- `id` text primary key
- `user_id` text not null
- `year` integer not null
- `month` integer not null
- `summary_json` text not null
- `generated_at` text not null
- `delivery_state` text nullable
- `created_at` text not null
- `updated_at` text not null
- `sync_status` text not null

Foreign keys:

- `user_id -> users.id`

Indexes:

- index on `user_id`
- unique index on `user_id, year, month`

## 6. Derived Views / Query Models

These do not need separate stored tables on day one, but the app will need optimized query paths for them.

### 6.1 dashboard_month_summary

Computed from:

- `canonical_transactions`
- `budgets`
- `credit_cards`
- `emi_plans`
- `recurring_patterns`

Outputs:

- total month spend
- remaining monthly budget
- this week spend
- upcoming due count
- active alerts

### 6.2 daily_spend_heatmap

Computed from:

- confirmed expense transactions grouped by day

Outputs:

- date
- total_amount_minor
- transaction_count

### 6.3 bucket_spend_rollup

Computed from:

- transaction bucket assignments
- canonical transactions

Outputs:

- bucket_id
- month spend
- budget limit
- remaining

## 7. Write Flows

### 7.1 Notification or SMS ingest

Writes:

1. `raw_capture_events`
2. `parsed_signals`
3. `transaction_candidates`
4. optional `dedupe_groups`
5. either:
   - `canonical_transactions` + `canonical_transaction_source_links`
   - or `inbox_items`

### 7.2 User confirms Inbox item

Writes:

1. update `inbox_items`
2. create or update `canonical_transactions`
3. create `canonical_transaction_source_links`
4. possibly update `categories`
5. possibly update `transaction_bucket_assignments`

### 7.3 User edits transaction

Writes:

- `canonical_transactions`
- possibly `transaction_bucket_assignments`
- possibly `emi_transaction_links`

### 7.4 User confirms recurring item

Writes:

- `recurring_patterns`

### 7.5 User confirms EMI

Writes:

- `emi_plans`
- `emi_transaction_links`

## 8. Sync Recommendations

### Sync first

Safe to sync:

- users
- accounts
- credit_cards
- categories
- buckets
- budgets
- canonical_transactions
- transaction_bucket_assignments
- recurring_patterns
- emi_plans
- alert_rules
- monthly_recaps

### Sync later or optionally

Potentially sensitive and heavier:

- raw_capture_events
- parsed_signals
- transaction_candidates
- dedupe_groups
- inbox_items

Recommendation:

- keep raw evidence local by default in v1 unless a clear server-side need appears

## 9. Retention Recommendations

### Raw evidence

Keep locally for a bounded retention window if storage or privacy becomes a concern.

Suggested starting point:

- retain raw captures for 90 days
- retain canonical transactions indefinitely

### Parser outputs

May be retained longer than raw text if needed for explainability and model tuning.

## 10. Seed Data Recommendations

### Default categories

- Food
- Groceries
- Shopping
- Travel
- Bills
- Family
- Health
- Entertainment
- Subscriptions
- EMIs
- Cash

### Default buckets

- Wants
- Subscriptions
- Food Out
- Family
- EMIs
- Travel
- Essentials

### Default alert rules

- budget nearing 80%
- recurring due 2 days before
- credit card bill due 3 days before

## 11. Validation Rules

- `amount_minor` must be positive for expense records
- a transaction cannot point to both `account_id` and `credit_card_id`
- bucket assignments must reference active buckets
- category names must be unique per user
- bucket names must be unique per user
- one monthly recap per user per year-month

## 12. Open Schema Questions

- whether account balance history should be modeled explicitly in v1
- whether credit card bill payment events should be first-class transactions in v1
- whether `cash_account_id` is necessary as a separate field vs treating cash as an account type only
- whether provenance tables should ever leave device storage

## 13. Immediate Next Step

With this schema defined, the next document should be the Android screen spec:

- onboarding
- permissions
- Inbox
- dashboard
- transaction detail
- budgets
- cards and EMIs
- calendar heatmap
