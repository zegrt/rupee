# Rupee Android Screen Spec

Date: March 27, 2026
Status: Draft v1
Author: Codex

## 1. Purpose

This document defines the Android-first screen structure and product flows for Rupee v1.

It translates the PRD, architecture, and schema into a concrete app experience.

Related docs:

- [PRD](rupee-prd.md)
- [Architecture](rupee-architecture.md)
- [Schema](rupee-schema.md)

## 2. Design Direction

Rupee should feel:

- minimal
- bold
- premium
- dense but calm

Visual and interaction references:

- Bump by Amo
- recent Apple UI sensibilities

Interaction qualities:

- soft premium motion
- card-led surfaces
- strong hierarchy
- low clutter
- high information density where useful

## 3. Product Navigation

Primary navigation for v1 — five top-level surfaces:

- Home
- Inbox
- Transactions ("Txns")
- Calendar
- Settings

Currently rendered as a chip-row tab switcher inside `MainActivity` (v0.8.0+). Migration to a Material3 `BottomNavigationBar` is a tracked follow-up; the structural shape (5 tabs, same labels) will not change.

Secondary entry points (currently reached from Settings; in v1 several should also surface from Home):

- Budgets (monthly total + per-category, on one page; bucket budgets pending)
- Cards & EMIs
- Trusted merchants
- Reports / Monthly recap (not yet implemented)

Reason:

- top nav prioritizes daily-use surfaces
- budgets and cards are important, but not as frequently switched as Home/Inbox/Transactions

## 4. Global UX Rules

- Home should answer key questions within 5 seconds
- User should never need more than 2 taps to review a suspicious transaction
- Manual entry should always exist, but never dominate the UI
- Every inferred transaction should be explainable
- Permission requests should be contextual, not dumped all at once
- Empty states should teach the next useful action

## 5. Screen Inventory

v1 screens:

1. Splash / Launch
2. Welcome / Intro
3. Permission Setup
4. Initial Account & Card Setup
5. Home Dashboard
6. Inbox
7. Inbox Detail / Review Sheet
8. Transactions Feed
9. Transaction Detail / Edit
10. Manual Transaction Entry
11. Budgets Overview
12. Budget Detail
13. Cards & EMIs Overview
14. Credit Card Detail
15. EMI Detail
16. Calendar Heatmap
17. Reports / Monthly Recap
18. Settings

## 6. Onboarding Flow

## 6.1 Splash / Launch

Purpose:

- fast app load
- show product identity
- route to onboarding or main app

Elements:

- Rupee wordmark
- subtle animated currency / motion accent
- loading state only if required

Exit conditions:

- new user -> Welcome
- returning user -> Home

## 6.2 Welcome / Intro

Purpose:

- explain value clearly before permissions

Primary message:

- Rupee tracks spending automatically from your phone's transaction signals

Supporting points:

- budgets that stay visible
- card dues and EMI tracking
- privacy-aware local capture

Actions:

- `Set up Rupee`
- optional `See how it works`

## 6.3 Permission Setup

Purpose:

- request permissions gradually and with context

Permission order for current preview build:

1. notification access

Rules:

- notification access is primary
- SMS access is deferred from the current preview build
- do not request SMS until the notification-first experience is proven

UI structure:

- one card per permission
- what Rupee reads
- why it helps
- what happens if skipped
- CTA to enable

States:

- not started
- granted
- skipped
- denied

If skipped:

- allow progression
- explain that accuracy and coverage may be lower

## 6.4 Initial Account & Card Setup

Purpose:

- map future parsed signals into known entities

Inputs:

- add bank account
- add credit card
- optionally add cash on hand

For each account/card:

- display name
- provider name
- masked digits or nickname
- opening/current balance if known

Actions:

- `Add bank account`
- `Add credit card`
- `Add cash`
- `Finish setup`

Notes:

- this screen must feel lightweight
- user should be able to add one account/card and continue

## 7. Home Dashboard

## 7.1 Purpose

This is the app's main daily screen.

It should surface:

- budget remaining
- this week spend
- upcoming dues
- recent activity

## 7.2 Structure

Top to bottom:

1. greeting / month label
2. hero summary card
3. weekly spend module
4. upcoming dues strip
5. custom bucket progress cards
6. recent transactions
7. quick actions

## 7.3 Hero Summary Card

Primary content:

- remaining monthly budget
- total spent this month
- top budget pressure indicator

Optional secondary content:

- subtle trend vs last week / last month

Visual behavior:

- bold number hierarchy
- soft progress ring or bar
- warm caution state near budget limits

## 7.4 Weekly Spend Module

Shows:

- total this week
- short trend visualization
- biggest category this week

Tap action:

- opens filtered Transactions view for current week

## 7.5 Upcoming Dues Strip

Shows:

- next credit card due
- next EMI due
- next recurring due

Tap action:

- opens Cards & EMIs overview or recurring details depending on item

## 7.6 Custom Bucket Progress

Shows a horizontal or stacked card list for:

- Wants
- Essentials
- Food Out
- EMIs
- Subscriptions

Each item shows:

- spent amount
- limit
- remaining

Tap action:

- opens Budget Detail filtered to that bucket

## 7.7 Recent Activity

Shows last few canonical transactions.

Each row:

- merchant/payee
- amount
- category
- source/account hint
- confidence or auto/manual badge if useful

Tap action:

- opens Transaction Detail

## 7.8 Quick Actions

Actions:

- `Add transaction`
- `Review Inbox`
- `Add EMI`
- `Edit budgets`

## 7.9 Empty State

If few/no transactions exist:

- explain that Rupee starts filling in once notification-based transaction signals arrive
- show permission completion status
- show option to add a manual transaction

## 8. Inbox

## 8.1 Purpose

Inbox is where medium-confidence or ambiguous items are reviewed.

This screen is critical for trust.

## 8.2 Structure

Top sections:

- pending review count
- grouped review cards
- resolved recent items optional

Grouping options:

- possible duplicates
- missing account mapping
- unclear merchant
- recurring confirmation
- EMI confirmation

## 8.3 Item Card

Each item should show:

- amount
- guessed merchant/payee
- source(s)
- time
- reason Rupee is unsure

Primary actions:

- `Confirm`
- `Edit`
- `Dismiss`

Secondary action:

- `Merge` when duplicate conflict exists

## 8.4 Behavior

Fast review is the goal.

The user should be able to:

- confirm with one tap when suggestion is correct
- open detail when unsure

## 9. Inbox Detail / Review Sheet

Purpose:

- resolve one candidate with full context

Content:

- parsed transaction preview
- source evidence summary
- duplicate candidate matches if any
- guessed account/card
- guessed category
- similar-history reference

Actions:

- confirm as-is
- edit before confirm
- merge into existing transaction
- dismiss

Explainability copy:

- short reason such as `Amount found, but account is unclear`

## 10. Transactions Feed

## 10.1 Purpose

Provide the complete browsing and filtering surface for all canonical transactions.

## 10.2 Structure

Top controls:

- search
- date range filter
- account/card filter
- category filter
- mode filter

Body:

- grouped list by day

Each transaction row:

- merchant/payee
- amount
- category
- account/card source
- mode
- note marker if present

Floating action:

- `Add transaction`

## 10.3 Empty / Sparse States

- no results for filters
- no transactions yet

Both should offer fast reset and next action.

## 11. Transaction Detail / Edit

Purpose:

- inspect and correct a canonical transaction

Fields visible:

- amount
- date/time
- category
- bucket assignments
- from account/card/cash
- to merchant/payee
- mode
- notes
- linked EMI if any
- recurring status if any
- source summary

Actions:

- edit
- reassign category
- adjust bucket membership
- link/unlink EMI
- mark recurring
- delete

## 12. Manual Transaction Entry

Purpose:

- fallback for missed events or cash spending

Fields:

- amount
- transaction type
- source account/card/cash
- merchant/payee
- category
- bucket
- date/time
- mode
- notes

Design rule:

- compact and fast
- optimized for 20-second entry

## 13. Budgets Overview

## 13.1 Purpose

Show budget health across monthly total, categories, and (eventually) custom buckets, all from a single surface — opened from Settings → Budgets in the current build. Implemented as `BudgetsScreen` + `BudgetsViewModel` (v0.9.0).

## 13.2 Structure

Sections in order:

1. Monthly overall budget hero — current label, spent / remaining, progress bar (red when over, tertiary when near limit), inline edit field with Save
2. Per-category list — one card per category with spent label, optional limit label, progress bar, and inline edit field. Entering 0 deactivates the row.
3. Bucket budget list — **not yet implemented**; needs `transaction_bucket_assignments` DAO and bucket-spend queries before this section can render.

For each row:

- spent
- limit (when set)
- remaining (monthly hero only)
- progress bar
- over-limit / near-limit accent

Primary actions:

- inline `Save` per row
- enter 0 to clear a category limit (deactivates the active row)

## 14. Budget Detail

Purpose:

- deep dive into one category or bucket budget

Content:

- limit
- current spend
- remaining
- spend trend over month
- recent transactions contributing to this budget

Actions:

- edit budget
- edit mapped categories if bucket

## 15. Cards & EMIs Overview

## 15.1 Purpose

This screen combines liabilities and obligations without splitting them into disconnected product areas.

Top sections:

- cards summary
- EMI summary
- upcoming due timeline

## 15.2 Cards Summary

Each card tile shows:

- card name
- current outstanding
- due amount
- due date
- available limit

Tap:

- opens Credit Card Detail

## 15.3 EMI Summary

Each EMI tile shows:

- name
- monthly amount
- next due
- remaining tenure
- total outstanding if known

Tap:

- opens EMI Detail

## 16. Credit Card Detail

Purpose:

- one clear view per card

Content:

- hero summary with due amount and due date
- current outstanding
- available limit
- spend timeline
- recent transactions on this card

Actions:

- edit card details
- update outstanding
- link transactions

Optional later:

- payment history

## 17. EMI Detail

Purpose:

- show the lifecycle and burden of one EMI

Content:

- monthly amount
- next due
- tenure remaining
- total outstanding
- linked card/account
- matched transactions

Actions:

- confirm or edit inferred EMI
- manually add/link transaction
- edit plan details

## 18. Calendar Heatmap

## 18.1 Purpose

Provide a visual spending map of the month.

## 18.2 Core Layout

Top:

- month selector
- total spend summary

Middle:

- daily heatmap

Top overlay / pinned row:

- recurring dues
- card due dates
- EMI due dates

Bottom:

- selected-day transaction list

## 18.3 Interactions

- tap day -> show transactions for that day
- swipe month -> next/previous month
- tap due item -> open relevant detail screen

## 19. Reports / Monthly Recap

## 19.1 Purpose

Turn a month of activity into something useful and a little fun.

## 19.2 Content

The recap should combine:

- story-like highlights
- visual report blocks
- lightweight insight cards

Examples:

- biggest spend category
- most expensive day
- how much was left vs budget
- what changed from last month
- fixed obligations vs discretionary spending

## 19.3 Tone

- not childish
- not accountant-like
- concise and rewarding

## 20. Settings

Sections:

- profile
- currency and region
- notification access status
- SMS access status later
- accounts and cards management
- categories and buckets
- alert preferences
- export and reports
- privacy and data retention

Actions:

- manage permissions
- manage accounts/cards
- manage categories/buckets
- export CSV
- delete local data or sign out later

## 21. Key States and Edge Cases

## 21.1 Permission denied

- show reduced-mode explanation
- keep app useful
- provide re-enable shortcut

## 21.2 No parsers matched

- keep event out of main feed
- optionally log for diagnostics

## 21.3 Too many Inbox items

- batch actions may be needed later
- v1 should at least group items by reason

## 21.4 Duplicate conflict

- show explicit merge UI
- never silently show two likely identical spends without surfacing conflict logic

## 21.5 No budget set

- Home should still work
- budget card becomes setup prompt

## 21.6 No cards or EMIs

- Cards & EMIs screen should collapse into clean onboarding prompts

## 22. Suggested Component Set

Shared components:

- hero summary card
- metric chip
- transaction row
- review card
- due item chip
- progress bar / ring
- heatmap cell
- bucket pill
- sheet-based editor

## 23. Recommended Android Build Order

1. Welcome and onboarding
2. Permissions flow
3. Initial account/card setup
4. Home shell
5. Transactions feed
6. Inbox
7. Transaction detail and manual entry
8. Budgets
9. Cards & EMIs
10. Calendar heatmap
11. Reports / recap

## 24. Immediate Next Artifact

After this screen spec, the next document should be the engineering roadmap:

- milestones
- build order
- delivery cut line for MVP
- technical dependency map
