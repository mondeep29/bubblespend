# BT-009 implementation plan: Expense entry and management

**Status:** Planning only. No implementation or acceptance-test files have been changed.  
**Risk:** Elevated under `docs/security-gates.md` because the feature parses financial input and invokes persistent Room mutations. Independent security preflight is required before Tester, Builder, emulator, or device work. No hard deletion or destructive migration is planned.

## Inputs and scope

BT-007 is done. Approved SPEC-001 and SPEC-002, ADR-001, ADR-002, ADR-003, ADR-005, D-03, the approved currency portion of D-05, and C-01 through C-05 apply.

Deliver a usable in-app route for first currency confirmation, expense creation, editing, duplication, soft deletion and undo, plus category and payment method management. A simple active-expense list supplies access to lifecycle actions and is part of expense management. Dashboard, reporting history, analytics, general onboarding, week-start settings, later currency changes, and system overlay remain outside BT-009.

The current app contains only a Home route. BT-007 supplies validation, repository contracts, and Room adapters, but the app has no persistent confirmed-currency preference or production composition of those adapters. Both are required to satisfy SPEC-001 without assuming a currency.

## Ownership

- **Coordinator/integration owner:** task queue, `workflow/evidence/BT-009*`, transitions, criterion mapping, and scope clarification.
- **Tester:** new tests under `core/application/src/test/**` and `app/src/androidTest/**`; acceptance assertions stay separate from Builder ownership.
- **Builder:** scoped production files under `core/application/src/main/**`, `app/src/main/**`, `app/src/main/res/**`, and only necessary pinned dependency entries in `app/build.gradle.kts` and `gradle/libs.versions.toml`.
- **Independent Reviewer:** elevated-risk preflight before red tests and feature review after verification; read-only.

No implementation begins until the plan is persisted, preflight is approved, BT-009 is claimed, `check-start` passes, and Tester records a genuine failing acceptance test.

## Architecture and implementation sequence

### 1. Local currency confirmation

Add a small application boundary for reading and confirming one base `CurrencyCode`. Back it with Android Preferences DataStore using the already pinned version. On first entry, suggest the locale currency when available and require explicit confirmation before Save is enabled. Persist confirmation across restart. If the locale has no usable currency, require explicit manual selection. Later currency migration remains outside BT-009. The expense use case receives the confirmed currency explicitly and rejects a mismatched amount.

### 2. Composition root and screen state

Extend the explicit app composition root to open `RoomExpenseStore` off the main thread, call idempotent `seedDefaults`, create the preference adapter and application controllers/use cases, and expose loading, ready, and recoverable storage-error states. Use ViewModel and StateFlow with lifecycle-aware collection. Inject `Clock`, `UuidGenerator`, locale, and dispatcher. Navigation passes stable record IDs and reloads durable records from Room. Composables render state and emit events; they do not perform repository mutations.

### 3. Expense management surface

Extend Home with an in-app entry action and a simple active-expense list. Each record exposes edit, duplicate, and delete. If the existing bounded observation API cannot represent this list safely, add a narrow `observeAllActiveExpenses()` contract and Room query after its test is red. Resolve category and payment labels by stable ID, including archived records. The list excludes tombstones and updates after create, edit, delete, undo, rename, and app reopen.

### 4. Entry form and save controller

Keep raw amount text in the draft until `ExpenseEntryValidator` parses it with the confirmed currency and explicit locale. Provide active category and payment selectors, occurred date/time, merchant, and note. Render user-entered values as plain text. Map `ExpenseField` errors to visible and announced errors and move focus to the first invalid field.

Guard Save against simultaneous taps and use one UUID submission token per logical submission. Preserve draft fields and the token across supported activity/process recreation. After an uncertain response, retry with the same token and reconcile stored state before announcing success. A successful edit retains ID and creation time; duplicate starts a new draft and token. Storage and stale-version failures preserve a recoverable draft.

### 5. Delete and undo

Delete with ID, expected version, and a mutation token, and show success only after persistence confirms it. Offer Undo during one visible snackbar only. Snackbar dismissal or app interruption invalidates the UI permit while the tombstone remains. Undo uses its own operation token and expected deleted version. Reconcile persisted state after uncertain delete or undo responses and never announce unconfirmed success.

### 6. Category and payment management

Show active records with accessible create, rename, archive, and default actions. Reuse BT-007 validation and repository rules: trimmed 1–40 character display names, normalized uniqueness, supported icon/color keys, stable IDs, stale-version checks, and atomic default switching. Category text remains readable without icon or color. Archiving a selected category or method clears the draft selection and requires a fresh active choice. Archiving the default clears it without automatically selecting another. Management failures preserve editable input and offer retry.

### 7. Discard and accessibility

Closing a modified expense draft opens Keep/Discard confirmation. Use resource strings, ordinary Material controls, at least 48 dp interactive targets, keyboard actions, logical traversal, `FocusRequester` for the first error, and semantics for field errors and state changes. Verify large and bold text, high contrast, and reduced-motion usability. Device observation with TalkBack is required where available; semantics assertions alone do not prove spoken output.

## Test-first slices and traceability

Tester writes behavior-focused tests that fail against the current Home-only app. Record exact commands, failures, acceptance IDs, and environment before Builder begins. Each later slice receives a genuine red before its implementation.

| Slice | Acceptance coverage | Test focus |
| --- | --- | --- |
| Currency and startup | SPEC-001 AC-01, AC-04; SPEC-002 AC-01 | Explicit currency confirmation and restart persistence; seed exactly once without resetting edited/archived starters; startup failure recovery. |
| Entry and validation | SPEC-001 AC-01–04, AC-08–11, AC-13; SPEC-002 AC-06 | Exact minor units, invalid/missing/future/over-limit fields, empty or inactive selectors, rapid taps, uncertain retry, recoverable save error, focus/semantics, discard confirmation. |
| Expense lifecycle | SPEC-001 AC-05–07, AC-12, AC-14–16 | Edit identity/version, duplicate identity, stale conflict, one-snackbar undo, interruption ending undo, persisted tombstone after reopen, uncertain mutation reconciliation. |
| Management | SPEC-002 AC-02–09 | Create/rename/archive/default, invalid and duplicate names, historical labels, archived selection, stale/storage failure recovery, supported visuals, readable text, empty state, keyboard and large-text actions. |
| End-to-end reopen | SPEC-001 AC-01, AC-07, AC-15; SPEC-002 AC-01, AC-03, AC-04, AC-08 | Real Room and preference adapters in the launched app, close/relaunch, and persisted records/settings. |

Use focused `:core:application:test` for application controller/use-case tests and `:app:connectedDebugAndroidTest` for Compose and launched-app journeys. Device tests inject controllable stores, clocks, and dispatchers for failure and timing cases, plus a separate real-Room reopen journey. Verify keyboard and semantics through actual controls. Retain BT-007 repository contracts as regression coverage.

## Verification and evidence

Required packet gate:

```sh
./gradlew spotlessCheck lint testDebugUnitTest connectedDebugAndroidTest assembleDebug
```

Run with the checked-in JDK 17/SDK 35 toolchain and record device model/API. Linux currently lacks Java; the established route is a Windows-local copy with Windows JDK 17 and the API 35 emulator. Use one build process against that copy at a time and copy back only owned files. Inspect launch, currency confirmation, management, entry, validation/failure, undo, and reopen states on the emulator.

Record red/green commands, changed files, criterion-to-test mapping, preflight/final-review findings, assumptions, and limits in BT-009 evidence. Move phases only through `workflow/transition.py`. Completion requires the packet gate and independent feature approval.

## Security and recovery

Keep amounts, merchants, notes, category/payment names, preference values, and mutation receipts out of logs. Keep data on device; add no network, account, or telemetry dependency. Preflight verifies that currency confirmation cannot be bypassed, repeated saves retain one logical token, uncertain mutations reconcile with Room, archived references cannot be saved, and snackbar expiry cannot reactivate undo.

Use disposable emulator databases for destructive fixtures. Do not clear an installed user database to repair tests. If implementation fails, preserve red tests and restore only BT-009-owned production/build edits from the pre-change inventory. A Room schema change is not planned; an unexpected need requires a revised plan, migration tests, and another security preflight.

## Research checked 2026-10-02

No current exact-scope BT-009 entry existed under `workflow/research/`. Primary Android sources:

- [DataStore guidance](https://developer.android.com/topic/libraries/architecture/datastore): local preferences, asynchronous Flow reads, transactional updates, and data-layer ownership.
- [State hoisting and ViewModel collection](https://developer.android.com/develop/ui/compose/state-hoisting): ViewModel state exposed to Compose through lifecycle-aware collection.
- [Saved UI state](https://developer.android.com/develop/ui/compose/state-saving): retain small input/navigation state across recreation and reload durable records from storage.
- [Navigation Compose migration guidance](https://developer.android.com/develop/ui/compose/migrate/migration-scenarios/navigation): pass an ID and reload the record from the data layer.
- [Snackbar guidance](https://developer.android.com/develop/ui/compose/components/snackbar): action result and dismissal support the approved visible undo window.
- [Compose semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics), [focus requests](https://developer.android.com/develop/ui/compose/touch-input/focus/change-focus-behavior), and [accessibility testing](https://developer.android.com/develop/ui/compose/accessibility/testing): labels, errors, focus, and device verification.

All sources were accessed 2026-10-02.

## Scoped interpretation

The packet allows expense/category presentation and application use cases. The minimum local preference adapter and pinned lifecycle/DataStore dependencies are necessary supporting scope because approved D-05 requires persisted explicit currency confirmation before the first expense. This adds no new product behavior and does not authorize general onboarding or currency migration.
