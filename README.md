# Rupee

Rupee is an Android-first personal finance app for India.

The idea is simple:

- track spending with less manual effort
- help you stay on top of budgets
- keep credit card dues and EMIs visible
- start with notification-based tracking first

## What It Is

Rupee is being built as a personal money tracker for one user, not a big finance super-app.

The intended feel is:

- simple
- bold
- useful every day

## What It Tries To Solve

Most people do not want to manually log every expense.

Rupee is meant to make it easier to answer:

- How much did I spend?
- How much budget is left?
- What bills or dues are coming up?
- What is happening across my accounts, cards, cash, and EMIs?

## Current Direction

- Android first
- India first
- notification-first tracking
- SMS planned later, not in the current preview build
- bank linking not required for v1

## Comparison Learnings

From reviewing a focused workflow app, Rupee should preserve:

- narrow product framing around a specific recurring user job
- obvious, task-based top-level modules
- a main screen that answers the most important questions immediately
- clear separation between daily-use product surfaces and future admin or import tooling

Rupee should avoid:

- relying on a heavy client-only shell for product clarity
- shipping privileged setup or admin elevation paths in production
- letting non-core operational tooling bloat the initial user experience
- trusting client-side access checks for sensitive capabilities

## Current Status

Right now the project has:

- product and technical planning docs
- Android app scaffold (Compose, Room, Kotlin)
- onboarding flow
- local database foundation
- notification ingestion foundation
- provider-specific parsing for GPay, CRED, and ICICI notifications, plus a generic UPI fallback for PhonePe/Paytm-style bodies
- a decision layer for auto-create vs Inbox vs ignore, and a dedupe layer that suppresses repeated alerts
- a daily dashboard (hero monthly budget, this-week spend, upcoming dues, recent activity, Inbox CTA) on Home
- a unified review queue on Inbox: pending items plus auto-created `SUGGESTED` transactions, with edit-before-confirm (merchant, amount, category dropdown)
- "Always trust this merchant" toggle on Inbox confirm — saves a `MerchantTrustRule` so future notifications from that merchant auto-create as `CONFIRMED`, skipping the inbox
- Settings → Trusted merchants page (fullscreen) to view and remove existing trust rules
- Settings → Cards & EMIs page listing credit cards (outstanding, limit, dues) and manual EMI plans (add + remove)
- transactions surface with tap-to-view modal (Edit + Delete + recategorize)
- calendar tab with month-grid view, daily spend labels, and tap-day → transactions sheet
- a unified Budgets page (Settings → Budgets) covering monthly total + per-category limits with progress bars and over-limit highlighting
- recurring-spend detection that auto-finds monthly subscriptions in the last 120 days and asks the user to confirm or dismiss; confirmed ones surface as "Upcoming dues" on Home alongside cards/EMIs
- a Monthly recap surface (Settings → Monthly recap) with total spent, change vs last month, top categories, top merchants, and biggest transactions
- light/dark theme following the Android system setting
- per-account / per-card "exclude from spend totals" toggle for wallets that double-count from another tracked source
- dedicated parsers for GPay, CRED, ICICI, Kotak (incl. Kotak811), PhonePe, Paytm, EMI debits, generic UPI fallback; debug-only notification-dump tool to bootstrap real-world parser corpus
- manual transaction entry through a bottom sheet
- a Settings page (display name, monthly budget, notification permission re-check, version, category/bucket lists)
- a Debug page (preset + editable mock notifications, parser playground, DB reset) accessible via a floating pill and from Settings
- versioned APK output (`rupee-{version}-debug.apk`) and a version pill rendered on Home
- first unit tests in `src/test/java` for parser canParse routing
- a buildable debug APK

## Current Preview

The current preview build:

- uses notification access
- does not request SMS access
- can be built locally as a debug APK

APK output:

- [rupee-0.13.5-release.apk](./android/app/build/outputs/apk/release/rupee-0.13.5-release.apk) (signed; for friends/family)
- [rupee-0.13.5-debug.apk](./android/app/build/outputs/apk/debug/rupee-0.13.5-debug.apk) (for personal dogfooding — includes the notification-dump tool that bootstraps the parser corpus)

## Main Docs

- [Product PRD](./docs/rupee-prd.md)
- [Technical Architecture](./docs/rupee-architecture.md)
- [Settings & Debug surfaces](./docs/rupee-settings-debug.md)
- [Schema Spec](./docs/rupee-schema.md)
- [Android Screen Spec](./docs/rupee-android-screens.md)
- [Engineering Roadmap](./docs/rupee-roadmap.md)

## Project Context

For detailed implementation context, product decisions, current phase, and working notes, see:

- [CONTEXT.md](./CONTEXT.md)

That file is the deeper working memory for ongoing development. This `README` is meant to stay high-level and easy to scan.
