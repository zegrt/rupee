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
- a daily dashboard (hero monthly budget, this-week spend, recent activity, Inbox CTA) on Home
- a unified review queue on Inbox: pending items plus auto-created `SUGGESTED` transactions, with edit-before-confirm (merchant, amount, category dropdown)
- "Always trust this merchant" toggle on Inbox confirm — saves a `MerchantTrustRule` so future notifications from that merchant auto-create as `CONFIRMED`, skipping the inbox
- Settings → Trusted merchants page (fullscreen) to view and remove existing trust rules
- transactions surface with tap-to-view modal (Edit + Delete + recategorize)
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

- [rupee-0.6.3-debug.apk](./android/app/build/outputs/apk/debug/rupee-0.6.3-debug.apk)

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
