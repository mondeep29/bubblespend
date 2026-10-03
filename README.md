# BubbleSpend Android

BubbleSpend is a native Android, local-first expense tracker built with Kotlin and Jetpack Compose. The app shell starts at an accessible Home route. Architecture decisions, product requirements, and feature work are tracked in `docs/` and `workflow/`.

## Build and test

See [Android build setup](docs/build-setup.md) for JDK/SDK requirements and the local verification commands, including the connected emulator test.

## Project workflow

Read [the product brief](docs/product/product-brief.md), [architecture decisions](docs/adr/), and [the autonomous workflow](workflow/README.md). Task evidence lives in `workflow/evidence/`; the queue and gates are maintained with `workflow/transition.py` and `workflow/gate.py`.

## Milestones

| Milestone | Outcome |
| --- | --- |
| M0 | Approved product rules, architecture, Android overlay proof, accessible chart proof |
| M1 | Buildable Android foundation with CI and test harness |
| M2 | Correct money, time, domain, Room persistence, migrations |
| M3 | Expense and category management |
| M4 | Dashboard and transaction history |
| M5 | Analytics and explainable insights |
| M6 | Opt-in floating shortcut |
| M7 | Backup, privacy, accessibility, performance, release |
| M8 | Review readiness for a later sync phase |

Cloud sync, accounts, bank integrations, receipt OCR, AI categorization, budgets, recurring expenses, and notifications are outside the MVP.
