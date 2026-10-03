# SPEC-002: Categories and payment methods

**Status:** Approved

## User outcome

Create, rename, archive, and select categories and payment methods while keeping historical expenses readable.

## In scope

Idempotent starter records; active selection; category icon and color tokens; one optional default payment method; create, rename, and archive; empty, validation, failure, and recovery states.

## Out of scope

Hard deletion of referenced records, account-linked methods, automatic categorization, and remote synchronization.

## User journeys

1. On first setup, receive six predefined categories and two predefined payment methods exactly once, with Cash as the default method.
2. Create an additional category or payment method, rename an editable record, correct a name error, and select an active record for expense entry.
3. Archive a record, then continue entry using the owner-approved selection fallback.
4. Set or archive a default payment method without leaving an invalid default.

## Functional requirements

- Names are required and contain 1–40 characters after trimming. Uniqueness among active records of the same type compares trimmed names case insensitively after collapsing repeated spaces. Validation leaves the draft editable.
- Category icon and color use the supported keys below and have readable text labels. Color and icon alone never convey the category name. The UI maps color keys to theme swatches that meet the applicable contrast target; stored records keep keys rather than raw colors or drawable IDs.
- Unsupported icon or color tokens are rejected before persistence; a missing decorative asset cannot hide the category's text label.
- Only active categories and methods are offered for new expense selection. An archived record remains resolvable by ID for historical expense labels.
- First use creates predefined categories Food, Transport, Housing, Utilities, Health, and Other, plus predefined payment methods Cash and Card. Cash is the initial default. Users can create additional categories and payment methods. Predefined records are editable and archivable under the same rules as user-created records. Predefined records use stable IDs or another deterministic key so initialization and app restart cannot create duplicates or reset a user's edits or archives.
- At most one active payment method is default. Changing the default is atomic with clearing the old default. Archiving the default clears its status; no other method becomes default until the user chooses one.
- Rename and archive operations report storage errors and stale-version conflicts without silently overwriting newer records. Compound changes use a database transaction.

Supported icon keys are `food`, `transport`, `housing`, `utilities`, `health`, and `other`. Supported color keys are `red`, `orange`, `yellow`, `green`, `blue`, `purple`, and `neutral`. The six predefined categories use matching icon keys in the order listed above and color keys `red`, `orange`, `yellow`, `green`, `blue`, and `purple` respectively. New user-defined categories initially use `other` and `neutral`; the user may choose any supported pair. The theme owns the rendered icon assets and color values.

## Business rules

Category and payment IDs remain stable across rename and archive. Historical expenses retain their original references, and displayed labels reflect the referenced record without losing readability. Referenced records are archived rather than physically deleted. Repository contracts follow ADR-002; stable IDs, positive versions, timestamps, and local sync status follow ADR-005.

## State model

`List → Create or Edit → Validate → Save → List`; invalid input or storage failure returns to the editable form. `Active → Archive requested → Archived` removes a record from new-entry selection while preserving historical lookup. An empty active list offers creation. Default-method changes are one atomic transition. If an archived record was selected in an unsaved expense draft, that draft requires a new active selection before Save. Archiving the default method clears the default.

## Error and empty states

Missing, duplicate, or over-limit names show a field error and preserve input. Storage failures preserve the management draft and offer retry. Stale changes do not overwrite newer records. With no active category or method, management offers an accessible create action, and expense entry cannot save until an active selection exists. Archiving a selected record requires a fresh active selection in the unsaved expense draft.

## Accessibility requirements

Management lists, create and edit forms, archive actions, default status, errors, and empty states have clear TalkBack labels and logical focus order. Errors are announced and focus moves to the affected field. Large and bold text, keyboard navigation, high contrast, and reduced motion keep every action usable. Category colors and icons have text equivalents.

## Privacy and security

Names and historical labels remain on device. Do not log category or payment names, expense contents, or credentials. No network or telemetry dependency is added.

## Analytics/telemetry events

No remote events. Local UI state may represent loading, success, and failure without recording user-entered content.

## Acceptance criteria

- `SPEC-002-AC-01`: Given first setup, when predefined records are seeded, then Food, Transport, Housing, Utilities, Health, Other, Cash, and Card each exist exactly once and are active, with Cash as the sole default method; given repeated initialization after a user edit or archive, when setup runs again, then no record is duplicated or reset.
- `SPEC-002-AC-02`: Given a missing, over-limit, or normalized duplicate name among active records of the same type, when create or rename is attempted, then no mutation occurs, the field error is announced, and the draft remains editable.
- `SPEC-002-AC-03`: Given a record referenced by an expense, when it is archived, then historical expense ID and amount remain unchanged, its label remains readable, and the archived record is unavailable for new expense selection.
- `SPEC-002-AC-04`: Given a default payment method, when a new default is chosen or the current default is archived, then at most one active method is default; archiving clears the default without selecting another until the user chooses one.
- `SPEC-002-AC-05`: Given TalkBack, large text, keyboard input, or no active records, when managing categories or methods, then create, rename, archive, default, error, and empty-state actions are perceivable and operable.
- `SPEC-002-AC-06`: Given a category or method selected in an unsaved expense draft, when that record is archived, then saving cannot use the archived selection and the draft requires a fresh active selection.
- `SPEC-002-AC-07`: Given a storage failure or stale version during management, when the operation fails, then no false success is shown, the user's draft is recoverable, and newer stored data is not overwritten.
- `SPEC-002-AC-08`: Given a valid new user-defined category or payment method, or a valid rename of a predefined or user-defined record, when Save completes, then its stable ID and positive version are persisted, the active record is selectable for new expenses, and any historical expense reference remains readable after rename.
- `SPEC-002-AC-09`: Given an unsupported category icon or color token, when Save is attempted, then the invalid token is not persisted; given a supported token or unavailable decorative asset, the category's text label remains readable without color or icon recognition.

## Test mapping

BT-007 creates failing domain and Room contract tests before implementation. BT-009 creates failing management UI tests before implementation.

BT-007's first red tests are in `core/application/src/test/kotlin/com/bubblespend/core/application/CategoryPaymentRepositoryContractTest.kt` and `core/application/src/test/kotlin/com/bubblespend/core/application/CategoryPaymentValidationTest.kt`. Room contract tests are in `app/src/androidTest/java/com/bubblespend/app/expenses/RoomExpenseRepositoryContractTest.kt`; BT-009's `CategoryPaymentAccessibilityTest` and `ExpenseSelectionTest` are planned in matching UI test files. The methods below name actual BT-007 tests where present and planned later UI tests otherwise.

BT-007's Room red tests are in `app/src/androidTest/java/com/bubblespend/app/expenses/RoomExpenseRepositoryContractTest.kt`: `sharedCategoryAndPaymentContractRunsAgainstFakeAndRoom` covers repository portions of AC-02, AC-03, AC-04, AC-06, and AC-08; `seedRepeatsAfterReopenWithoutRestoringRenamedOrArchivedRecords` covers AC-01; `failedDefaultSwitchLeavesExistingDefaultUnchanged` covers conflict/rollback portions of AC-04 and AC-07. Device execution follows Room implementation. UI announcements remain planned for BT-009.

| Criterion | Proposed test | Verification command |
| --- | --- | --- |
| AC-01 | `CategoryPaymentRepositoryContractTest.seedingIsIdempotentAndPreservesUserChanges` | `./gradlew testDebugUnitTest connectedDebugAndroidTest` |
| AC-02 | `CategoryPaymentValidationTest.rejectsInvalidNormalizedNames`; `CategoryPaymentValidationTest.preservesTrimmedDisplayWhitespaceAndValidatesLengthBeforeUniquenessCollapse`; `CategoryPaymentAccessibilityTest.invalidNameIsAnnounced` | `./gradlew testDebugUnitTest connectedDebugAndroidTest` |
| AC-03 | `CategoryPaymentRepositoryContractTest.archivePreservesHistoricalReferenceAndBlocksNewExpense` | `./gradlew testDebugUnitTest connectedDebugAndroidTest` |
| AC-04 | `CategoryPaymentRepositoryContractTest.defaultChangeAndArchiveAreAtomic` | `./gradlew testDebugUnitTest connectedDebugAndroidTest` |
| AC-05 | `CategoryPaymentAccessibilityTest.managementAndEmptyStatesAreOperable` | `./gradlew connectedDebugAndroidTest` |
| AC-06 | `ExpenseSelectionTest.archivedSelectionRequiresApprovedFallback` | `./gradlew testDebugUnitTest connectedDebugAndroidTest` |
| AC-07 | `CategoryPaymentRepositoryContractTest.normalizedDuplicateAndStaleRenameNeverOverwrite`; `CategoryPaymentRepositoryContractTest.mutationTokenRetriesOriginalOperationAndRejectsDifferentOperationOrType`; `RoomExpenseRepositoryContractTest.customCategoryAndPaymentIdentitySurviveRenameReopenAndStaleFailure`; `RoomExpenseRepositoryContractTest.failedDefaultSwitchLeavesExistingDefaultUnchanged` | `./gradlew testDebugUnitTest connectedDebugAndroidTest` |
| AC-08 | `RoomExpenseRepositoryContractTest.sharedCategoryAndPaymentContractRunsAgainstFakeAndRoom`; `RoomExpenseRepositoryContractTest.customCategoryAndPaymentIdentitySurviveRenameReopenAndStaleFailure` | `./gradlew testDebugUnitTest connectedDebugAndroidTest` |
| AC-09 | `CategoryPaymentValidationTest.rejectsUnsupportedTokens`; `CategoryPaymentAccessibilityTest.categoryLabelIsReadableWithoutDecoration` | `./gradlew testDebugUnitTest connectedDebugAndroidTest` |

## Open questions

None for SPEC-002. If all records of a type are archived, expense entry requires the user to create another active record and select it before saving.
