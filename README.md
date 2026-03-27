# Rupee

Rupee is an Android-first personal finance app for India focused on automatic expense tracking from SMS and notification signals.

The goal is to reduce manual entry while helping a single user stay on top of:

- spending
- budgets
- credit card dues
- EMIs
- recurring obligations
- cash on hand

## Current Status

This repository currently contains product and architecture planning documents for the first version of Rupee.

## Documents

- [Product PRD](./docs/rupee-prd.md)
- [Technical Architecture](./docs/rupee-architecture.md)
- [Project Context](./CONTEXT.md)

## Product Direction

Rupee v1 is intended to be:

- Android-first
- India-first
- single-user
- offline-capable
- privacy-aware
- built around notification and SMS ingestion

Rupee v1 is not intended to depend on:

- direct bank linking
- shared household features
- investment tracking
- tax workflows

## Planned Core Features

- automatic transaction capture from notifications and SMS
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

## Repository Note

The repository was started with planning artifacts first so implementation can follow a cleaner architecture instead of ad hoc feature work.
