# Rupee

Rupee is an Android-first personal finance app for India focused on automatic expense tracking from notification signals first, with SMS ingestion planned for a later build.

The goal is to reduce manual entry while helping a single user stay on top of:

- spending
- budgets
- credit card dues
- EMIs
- recurring obligations
- cash on hand

## Current Status

This repository now contains the initial Android scaffold plus the core planning documents for the first version of Rupee.

## Documents

- [Product PRD](./docs/rupee-prd.md)
- [Technical Architecture](./docs/rupee-architecture.md)
- [Schema Spec](./docs/rupee-schema.md)
- [Android Screen Spec](./docs/rupee-android-screens.md)
- [Engineering Roadmap](./docs/rupee-roadmap.md)
- [Project Context](./CONTEXT.md)

## Android Scaffold

The repository now includes a minimal Android app scaffold:

- root Gradle configuration
- `android/app` application module
- basic Jetpack Compose entry screen
- initial theme placeholders
- Room-based local data foundation
- app bootstrap via `Application`
- core entities, DAOs, and repository shell
- seeded local sample data displayed through a minimal home view model
- onboarding flow with notification access
- local toolchain and Gradle wrapper for reproducible builds
- raw notification capture storage
- first-pass parsed signal and transaction candidate pipeline

This is only the implementation shell. The app does not yet include:

- ingestion pipeline
- parser logic
- budgets/cards/EMI features
- SMS reading in the shipped preview build

More precisely, the app now has the start of an ingestion pipeline, but it is still generic:

- raw notification capture exists
- normalization into parsed signals and transaction candidates exists
- provider-specific parsing does not exist yet
- deduplication does not exist yet
- Inbox and canonical transaction decisions do not exist yet

## Product Direction

Rupee v1 is intended to be:

- Android-first
- India-first
- single-user
- offline-capable
- privacy-aware
- built around notification ingestion first, with SMS ingestion deferred to a later build

Rupee v1 is not intended to depend on:

- direct bank linking
- shared household features
- investment tracking
- tax workflows

## Planned Core Features

- automatic transaction capture from notifications and SMS
- automatic transaction capture from notifications first
- SMS capture later once the install and trust flow is stronger
- deduplication across multiple sources
- confidence-based auto-entry with Inbox fallback
- account and credit card tracking
- EMI tracking
- recurring payment detection
- category, bucket, and monthly budgets
- dashboard and calendar heatmap views
- reports and export-ready data modeling

## Suggested Build Order

1. schema spec
2. Android screen spec
3. engineering roadmap
4. project scaffolding
5. Android app implementation
6. backend sync and web companion

## Current Phase

The project has finished the planning stack, Android scaffold, local data foundation, onboarding, and the first notification ingestion foundation. The next build step is provider-specific parsing and the decision layer.

## Working Rule

Before each commit, update `CONTEXT.md` and `README.md` if the project's scope, artifact list, or current phase changed.

## Repository Note

The repository was started with planning artifacts first so implementation can follow a cleaner architecture instead of ad hoc feature work.
