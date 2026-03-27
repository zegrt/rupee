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

## Current Status

Right now the project has:

- product and technical planning docs
- Android app scaffold
- onboarding flow
- local database foundation
- notification ingestion foundation
- early provider-specific parsing for GPay, CRED, and ICICI notifications
- a buildable debug APK

## Current Preview

The current preview build:

- uses notification access
- does not request SMS access
- can be built locally as a debug APK

APK output:

- [app-debug.apk](./android/app/build/outputs/apk/debug/app-debug.apk)

## Main Docs

- [Product PRD](./docs/rupee-prd.md)
- [Technical Architecture](./docs/rupee-architecture.md)
- [Schema Spec](./docs/rupee-schema.md)
- [Android Screen Spec](./docs/rupee-android-screens.md)
- [Engineering Roadmap](./docs/rupee-roadmap.md)

## Project Context

For detailed implementation context, product decisions, current phase, and working notes, see:

- [CONTEXT.md](./CONTEXT.md)

That file is the deeper working memory for ongoing development. This `README` is meant to stay high-level and easy to scan.
