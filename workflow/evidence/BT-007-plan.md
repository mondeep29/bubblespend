# BT-007 executable plan

**Planner:** `bt007_planner` (read-only project Planner).  
**Risk:** Elevated: financial records, Room schema, dependencies. Independent security preflight is required before tests or implementation. No destructive operation is planned.  
**Inputs:** BT-004 and BT-006 done; SPEC-001/002 and ADR-001/002/003/005 approved. Exact-scope research cache contained no Room/KSP entry.

## Ownership

Coordinator owns `workflow/tasks.json`, `workflow/evidence/BT-007*`, transitions, and integration. Tester owns new tests/fixtures under `core/application/src/test/**` and `app/src/androidTest/**`. Builder owns production files under `core/domain/src/main/**`, `core/application/src/main/**`, `app/src/main/**`, `app/schemas/**`, and scoped Gradle configuration. These owners must coordinate any cross-file change without editing another role's files.

## Ordered slices

1. Claim, record ownership, obtain independent elevated security preflight, pass `check-start`.
2. Tester writes failing validation and shared repository contract tests for domain/fake behavior. Record a genuine failing result from `./gradlew :core:application:test` or its Windows equivalent before Builder starts.
3. Builder implements immutable domain records, validation, repository contracts, and faithful in-memory fakes in pure Kotlin, preserving BT-006 primitives. Green focused tests.
4. Tester writes/runs shared contracts against missing Room adapter plus fresh creation, reopen, idempotent seed, rollback, corruption, and initial-schema tests. Record the second genuine red before Room code.
5. Builder implements Room entities/DAOs/mappers/adapters, schema v1 export, and composition-root wiring. Green shared contracts and device tests.
6. Tester runs `./gradlew testDebugUnitTest connectedDebugAndroidTest spotlessCheck lint assembleDebug`; record device/API, criterion mapping, limitations. Independent Reviewer checks correctness, security, privacy, accessibility, and test quality before completion.

## Contracts and invariants

Use immutable `Expense`, `Category`, and `PaymentMethod` with UUID identity, positive version, UTC timestamps, archive/tombstone state, and isolated local sync status. Expense stores `Money`, occurred time, category/payment IDs, trimmed merchant/note, and created/updated/deleted times. Repositories expose suspend mutations and Flow observation. `ExpenseRepository` needs create/edit/delete/undo/get/observe with `[start, end)` filtering and idempotent tokens; category/method repositories need create/rename/archive/get/observe, and method default change is atomic. Typed outcomes cover validation, conflict, missing/inactive references, and storage failure. Repeat mutation tokens return their original logical result without repeating the mutation. UI controls snackbar undo availability; persistence uses a valid undo command without UI timing.

Validate positive exact minor units and currency, future occurred time against injected Clock, active references at commit, trimmed 100/500 character limits, supported visual token keys, and trimmed 1–40 character names. Normalize uniqueness by trimming, collapsing repeated spaces, and case-insensitive comparison with Locale.ROOT; retain a separate display name. Domain/application import no Android, Compose, Room, DataStore, chart, or overlay types.

## Room and build design

Schema v1 has expense, category, payment method, and mutation receipt tables. Store UUIDs/instants in stable representations, minor units as SQLite INTEGER, currency as ISO code; preserve historical foreign-key references. Index active expense time/category; enforce unique mutation tokens and active-name keys. Use guarded version updates and transactions for reference validation, mutation, default switching, seeding, and receipts. Seed deterministic IDs for Food, Transport, Housing, Utilities, Health, Other, Cash, and Card, with Cash default; use insert-ignore without resetting user edits or archives. No REPLACE for user records and no destructive migration fallback.

No prior supported production schema exists; v1 has no genuine upgrade fixture. Export the generated v1 schema and verify fresh creation/reopen; add migration fixtures for every later schema before release. A corrupt disposable test database yields a storage error without deleting user data.

Keep Room 2.6.1, Kotlin 2.0.21, and AGP 8.7.3 pinned. Add KSP 2.0.21-1.0.28, Room runtime/compiler/ktx/testing at 2.6.1, and pinned coroutines 1.9.0 for Flow. Do not upgrade existing dependencies. Add direct app→domain dependency if adapter imports domain types.

## Criterion scope and recovery

BT-007 covers domain/persistence portions of SPEC-001 AC-01–07, AC-10, AC-12, AC-15–16 and SPEC-002 AC-01–04, AC-06–09. UI announcements, screen recovery, and entry journey criteria belong to BT-009. Run identical repository cases against fake and Room, with Room-only reopen, rollback, schema, and corruption cases. Recovery preserves the domain slice and red evidence; revert only reviewed scoped Room/build edits if needed. Never delete an installed app database to fix a test.

## Research checked 2026-10-02

- [AndroidX Room release/dependency guidance](https://developer.android.com/jetpack/androidx/releases/room)
- [Room migration guidance](https://developer.android.com/training/data-storage/room/migrating-db-versions)
- [MigrationTestHelper](https://developer.android.com/reference/androidx/room/testing/MigrationTestHelper)
- [KSP 2.0.21-1.0.28 release](https://github.com/google/ksp/releases/tag/2.0.21-1.0.28)
- [kotlinx.coroutines 1.9.0 release](https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.9.0)
- [Room conflict strategies](https://developer.android.com/reference/androidx/room/OnConflictStrategy.html)

No behavior-changing question is open for this task. ADR-002's settings and backup repository interfaces belong to their later tasks.
