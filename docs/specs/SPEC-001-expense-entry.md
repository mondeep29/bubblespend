# SPEC-001: Expense entry and lifecycle

**Status:** Approved

## User outcome

Record an expense in seconds, edit or duplicate it, and undo an accidental deletion.

## In scope

In-app quick entry; amount, category, payment method, occurred time, optional merchant/note; create, edit, duplicate, soft-delete, undo; interruption and double-submit handling.

## Out of scope

Recurring expenses, receipt capture, automatic categorization, currency conversion.

## User journeys

Open in-app entry, save, and find the expense after app restart. Edit retains the original ID; duplicate begins an editable new draft. Delete offers an undo action through the owner-approved workflow. Validation and storage errors leave input available for correction or retry.

## Functional requirements

- Amount > 0 in integer minor units and selected base currency. Reject overflow and excess decimal digits for the currency.
- Category and payment method must be active at save time. Future occurred times are rejected until a later approved specification allows them.
- Trim merchant and note before persistence; reject merchant text longer than 100 characters or note text longer than 500 characters after trimming. Render plain text only.
- Successful save happens exactly once per submission token. Edit increments version and updates `updatedAt`; delete sets `deletedAt`; undo clears it through a controlled workflow.
- Closing an unsaved form requires explicit discard confirmation. Keeping the draft returns to editing; confirming discard closes without saving. No silent duplicate after process interruption.
- A stale edit or delete version must not overwrite a newer record; show a recoverable conflict state. A failed or interrupted edit, delete, or undo must resolve against persisted state before showing success or retrying the same logical mutation.

## Business rules

Use the one explicitly confirmed base currency without conversion. First run suggests the locale currency; a later change requires a separately approved migration (D-05). UUIDs, positive versions, UTC instants, tombstones, and isolated local sync status follow ADR-002 and ADR-005. Active history and totals exclude tombstoned expenses; undo restores the original UUID to matching queries. Domain and application contracts do not import Android, Compose, Room, DataStore, chart, or overlay types.

## State model

Closed → Editing → Validating → Saving → Saved/Closed. A validation error, stale conflict, inactive selection, or storage error returns to Editing with a recoverable draft. Closing a modified draft opens discard confirmation; Keep returns to Editing and Discard closes without saving. Duplicate opens Editing with a new submission token. Active → Delete requested → Tombstoned → Undo available during one visible snackbar; successful undo during that window clears the tombstone and restores Active. Closing the snackbar or interrupting the app ends undo availability while the persisted tombstone remains (D-03).

## Error and empty states

Field-specific validation covers missing required fields, future occurred time, invalid amounts, and text over approved limits. Inactive selection blocks saving and offers an active choice or management path. Storage failure preserves input with retry; an interrupted create or edit resolves persisted state before retry. Failed delete or undo does not show success until the stored tombstone state is known; the original record remains recoverable. No active categories or methods shows an accessible management path and blocks Save. Stale mutation reports a conflict without overwriting newer data.

## Accessibility requirements

TalkBack announces field labels, errors, save state, and delete/undo actions; focus moves to the first invalid input. Large/bold text, keyboard navigation, high contrast, and reduced motion keep editing and recovery usable. Labels and states do not rely on color or motion alone.

## Privacy and security

Expense contents stay in the local store. Do not log amounts, merchant, note, or other personal transaction content in any build type. No private content appears on an overlay lock-screen preview. Entry adds no network, account, or telemetry dependency.

## Analytics/telemetry events

No remote events. Local UI state may record save outcome without content.

## Acceptance criteria

- `SPEC-001-AC-01`: Given valid fields, when Save is tapped, then one expense with exact minor units is persisted and visible after app restart.
- `SPEC-001-AC-02`: Given zero, negative, overflow, or too-precise amount, when Save is tapped, then no expense is persisted and the amount error is announced.
- `SPEC-001-AC-03`: Given an inactive category or payment method, when Save is tapped, then no expense is persisted and selection can be corrected.
- `SPEC-001-AC-04`: Given rapid repeated Save taps or an interrupted save retry, when completion occurs, then at most one expense exists for the submission.
- `SPEC-001-AC-05`: Given an existing expense, when edited, then fields update, version increases once, and createdAt remains unchanged.
- `SPEC-001-AC-06`: Given an existing expense, when duplicated, then a new UUID is used and source remains unchanged.
- `SPEC-001-AC-07`: Given an existing expense, when deleted and then undone within the supported workflow, then the original UUID is visible again and totals reconcile.
- `SPEC-001-AC-08`: Given a storage failure, when Save fails, then the form remains recoverable and no success confirmation is shown.
- `SPEC-001-AC-09`: Given TalkBack or large text, when entering an expense, then all fields, errors, and actions remain perceivable and operable.
- `SPEC-001-AC-10`: Given missing required fields, a future occurred time, or text beyond approved limits, when Save is tapped, then no mutation occurs, relevant errors are announced, and entered values remain available.
- `SPEC-001-AC-11`: Given no active categories or payment methods, when entry opens, then an accessible management path is available and Save cannot succeed until an active selection exists.
- `SPEC-001-AC-12`: Given a newer stored version than an edit or delete request, when the stale request runs, then it does not overwrite the newer record and offers a recoverable conflict state.
- `SPEC-001-AC-13`: Given an unsaved modified draft, when the user closes entry, then discard confirmation appears; Keep returns to the draft, while Discard closes without saving.
- `SPEC-001-AC-14`: Given a deleted expense, when its one visible undo snackbar closes or the app is interrupted, then the undo action is unavailable and the original UUID remains tombstoned.
- `SPEC-001-AC-15`: Given an expense deleted without undo, when the app restarts, then its original UUID remains tombstoned and the expense is absent from normal history and totals.
- `SPEC-001-AC-16`: Given a storage failure or interrupted response during edit, delete, or undo, when the operation resolves, then the UI reflects persisted state, gives a recoverable action for a failed operation, and never reports an unconfirmed success or applies the same logical mutation twice.

## Test mapping

BT-007 creates failing domain and repository acceptance tests before implementation; BT-009 creates failing Compose and device journey tests before entry UI work. Proposed test mapping:

BT-007's first red tests are in `core/application/src/test/kotlin/com/bubblespend/core/application/ExpenseRepositoryContractTest.kt` and `core/application/src/test/kotlin/com/bubblespend/core/application/ExpenseEntryValidationTest.kt`. Room contract tests are in `app/src/androidTest/java/com/bubblespend/app/expenses/RoomExpenseRepositoryContractTest.kt`; BT-009's `ExpenseEntryScreenTest`, `ExpenseEntryAccessibilityTest`, and `ExpenseDeletionJourneyTest` are planned in matching UI test files. The methods below name actual BT-007 tests where present and planned later UI tests otherwise.

BT-007's Room red tests are in `app/src/androidTest/java/com/bubblespend/app/expenses/RoomExpenseRepositoryContractTest.kt`: `sharedExpenseContractRunsAgainstFakeAndRoom` covers the repository portions of AC-01, AC-03, AC-04, AC-05, AC-07, and AC-12; `freshSchemaAndReopenPreserveExpenseTombstoneAndReceipt` covers persistence portions of AC-01, AC-04, AC-15, and AC-16. Device execution follows Room implementation. UI announcement and recovery portions remain planned for BT-009.

| Criterion | Proposed test | Verification command |
| --- | --- | --- |
| AC-01 | `RoomExpenseRepositoryContractTest.sharedExpenseContractRunsAgainstFakeAndRoom`; `RoomExpenseRepositoryContractTest.freshSchemaAndReopenPreserveExpenseTombstoneAndReceipt` | `./gradlew testDebugUnitTest connectedDebugAndroidTest` |
| AC-02 | `ExpenseEntryValidationTest.rejectsInvalidExactAmounts`; `ExpenseEntryScreenTest.invalidAmountIsAnnounced` | `./gradlew testDebugUnitTest connectedDebugAndroidTest` |
| AC-03 | `ExpenseRepositoryContractTest.rejectsArchivedSelectionsAtSave`; `ExpenseEntryScreenTest.archivedSelectionOffersCorrection` | `./gradlew testDebugUnitTest connectedDebugAndroidTest` |
| AC-04 | `ExpenseRepositoryContractTest.repeatedSubmissionTokenCreatesOnce` | `./gradlew testDebugUnitTest connectedDebugAndroidTest` |
| AC-05 | `ExpenseRepositoryContractTest.editDeleteAndUndoPreserveIdentityAndGuardVersions` | `./gradlew testDebugUnitTest connectedDebugAndroidTest` |
| AC-06 | `ExpenseRepositoryContractTest.duplicateCreatesNewIdentityAndHalfOpenQueryFiltersTombstones` | `./gradlew testDebugUnitTest connectedDebugAndroidTest` |
| AC-07 | `ExpenseRepositoryContractTest.editDeleteAndUndoPreserveIdentityAndGuardVersions` | `./gradlew testDebugUnitTest connectedDebugAndroidTest` |
| AC-08 | `ExpenseEntryScreenTest.failedSavePreservesDraftAndOffersRetry` | `./gradlew connectedDebugAndroidTest` |
| AC-09 | `ExpenseEntryAccessibilityTest.entryValidationAndRecoveryAreOperable` | `./gradlew connectedDebugAndroidTest` |
| AC-10 | `ExpenseEntryValidationTest.rejectsMissingFutureAndOverLimitFields`; `ExpenseEntryScreenTest.invalidFieldsAreAnnouncedAndPreserved` | `./gradlew testDebugUnitTest connectedDebugAndroidTest` |
| AC-11 | `ExpenseEntryScreenTest.emptySelectionsOpenManagement` | `./gradlew connectedDebugAndroidTest` |
| AC-12 | `ExpenseRepositoryContractTest.editDeleteAndUndoPreserveIdentityAndGuardVersions` | `./gradlew testDebugUnitTest connectedDebugAndroidTest` |
| AC-13 | `ExpenseEntryScreenTest.closeUnsavedDraftFollowsDiscardPolicy` | `./gradlew connectedDebugAndroidTest` |
| AC-14 | `ExpenseDeletionJourneyTest.undoWindowAndInterruptionFollowDecision` | `./gradlew connectedDebugAndroidTest` |
| AC-15 | `RoomExpenseRepositoryContractTest.freshSchemaAndReopenPreserveExpenseTombstoneAndReceipt` | `./gradlew connectedDebugAndroidTest` |
| AC-16 | `ExpenseRepositoryContractTest.expenseMutationTokenRejectsDifferentOperationWithoutFalseSuccess`; `RoomExpenseRepositoryContractTest.sharedMutationTokenRetryAndMisuseContractRunsAgainstFakeAndRoom`; `ExpenseDeletionJourneyTest.failedOrInterruptedMutationResolvesStoredState` | `./gradlew connectedDebugAndroidTest` |

## Open questions

None for SPEC-001. Reporting timezone and other onboarding settings remain in D-02/D-05 and outside this entry specification.
