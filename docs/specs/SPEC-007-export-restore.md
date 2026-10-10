# SPEC-007: Export and restore

**Status:** Draft. Atomic replace and user-selected remote-backed document providers are product-owner decisions; the remaining open questions below block approval.

## User outcome

Create a user-initiated backup in a chosen Android document location, inspect a candidate backup, and restore supported local records without losing live data when validation or application fails.

## In scope

User export; document selection; versioned format and integrity validation; restore preview, confirmation, atomic application, cancellation, interruption, and recovery; supported record and reference preservation; large-data responsiveness and privacy.

## Out of scope

Automatic backup, cloud account or sync integration, app-owned network transfer, and import of unversioned third-party formats. A user-selected remote-backed Android document provider is permitted for both export and restore.

## User journeys

1. The user requests Export, chooses an Android document destination, and receives success only after a complete backup is written. Cancellation or a failed write leaves local records unchanged and reports no completed export.
2. The user requests Restore and selects a document. The app reads and validates the entire candidate before a preview. The preview identifies version and supported-record counts, explains that current supported records will be replaced atomically with no merge, and offers Confirm and Cancel.
3. Confirmation replaces the supported records as one recoverable operation. Success is shown only after durable commit. Validation failure, cancellation, or failure before commit leaves live data unchanged; the user can choose another file or retry safely.
4. After interruption or an uncertain response, the app resolves whether the prior operation committed before presenting success or permitting a retry. One confirmation cannot become two logical restores.

## Functional requirements

- Use Android system document APIs for user-selected import and export. A user-selected provider may be remote-backed for either operation. Request only access needed for the chosen document. No app-owned network service or account is needed.
- Export a self-identifying versioned format with integrity metadata. Format version, encoding, canonicalization, integrity method, encryption policy, and exact supported records/fields remain **unresolved**. The approved record set must preserve expense and reference values needed for a round trip: stable IDs, UTC instants, integer minor units and currency, positive versions, tombstones, and isolated local sync status where supported.
- Read a consistent export snapshot so counts and references agree. A canceled or failed write cannot be reported as a valid complete backup. An incomplete document artifact must fail later restore validation.
- Before any mutation, read the complete candidate and validate its recognized version and structure, integrity metadata, exact money/currency invariants, UTC instants, positive versions, unique IDs within each record type, and cross-record references. Reject zero-byte, partial/truncated, corrupt, tampered, unsupported-version, duplicate-ID, invalid-money, and broken-reference candidates without changing live data. No silent dropping or coercion of unsupported records is allowed.
- Generate preview counts only from a fully validated candidate. Count each supported record type and explain that existing records within the approved restore scope will be replaced, including those whose IDs also appear in the candidate. The exact record scope and treatment of an empty candidate remain **unresolved**.
- Restore requires explicit confirmation after preview. Cancel at picker or preview performs no mutation. The approved D-04 policy is atomic replace with no merge in the MVP.
- Apply the whole restore in a database transaction. Validation failure starts no mutation; a failure before commit rolls back every change. Report success only after durable commit. An uncertain response must resolve persisted state before retry.
- Expose selecting, reading, validating, preview, applying, success, canceled, rejected, and failure states. Prevent repeated confirmation while applying. A failure gives a non-sensitive reason and a safe action without silently discarding live data or the selected input.
- Run read, validation, encoding, and application work off the main thread so large backup operations leave controls responsive.

## Business rules

- Room/SQLite remains the device source of truth. A successful round trip preserves exact supported values and references, not just aggregate totals. No floating-point money conversion or currency conversion occurs.
- The product owner selected atomic replace under D-04. A replace preview must state that current supported records would be replaced before confirmation. A candidate ID matching a live ID does not trigger a merge; it participates in the replacement of the approved record scope. The exact supported record set remains open.
- An unsupported future or obsolete format version is rejected before mutation until an approved migration path exists.
- Test a 50,000-expense fixture with linked categories and payment methods. Record device/API, record counts, phase durations, and UI responsiveness. No numeric time budget is yet approved; a duration alone is not a pass/fail threshold.

## State model

`Idle -> Select export destination -> Exporting -> Export success | Export failure | Canceled`.

`Idle -> Select restore source -> Reading -> Validating -> Preview -> Applying -> Restore success | Restore failure`.

Reading or validation can enter Rejected; selection or preview can enter Canceled. Interruption enters Resolving until committed state is known. Only a fully validated candidate reaches Preview; only explicit confirmation reaches Applying.

## Error and empty states

- Zero-byte, truncated, corrupt, tampered, duplicate-ID, broken-reference, invalid-money, and unsupported-version candidates are rejected before mutation with a distinct non-sensitive explanation and recovery action.
- Provider read/write failure or permission loss explains that the operation did not complete and offers another document or retry. A provider artifact may still exist after a failed export; do not present it as complete.
- An apply failure rolls back the full restore. Keep the preview recoverable when its document can still be read; otherwise offer reselection.
- An ambiguous completion checks persisted state before reporting outcome or accepting retry.
- A backup with zero supported records shows explicit zero counts. Whether it may replace nonempty live data remains an open product decision.

## Accessibility requirements

Document selection result, progress, record-count preview, replacement warning, Confirm, Cancel, errors, and recovery actions are visible in text and operable by touch and keyboard. The preview presents the replacement consequence before Confirm; failures show the explanation and recovery action. Large/bold text, high contrast, and reduced motion keep the flow usable. No success, failure, or destructive effect relies on color or animation alone. SPEC-008 defines the audit. Screen-reader behavior is outside MVP scope under D-08.

## Privacy and security

Never log backup bytes, amounts, merchants, notes, credentials, document names/URIs, or other private record content in any build type. Do not expose backup content in notifications or errors; preview contains approved counts and consequence text. The export flow must explain the eventual approved encryption policy and that a user-selected remote-backed provider may store the document remotely. Encryption remains unresolved. No remote telemetry, app-owned network dependency, or automatic transfer is added.

## Analytics/telemetry events

No remote events. Local UI state may represent phase and outcome without financial values, file content, or URI.

## Acceptance criteria

- `SPEC-007-AC-01` **Blocked: format, supported records, encryption policy.** Given supported records with expenses, references, timestamps, versions, tombstones, and exact minor-unit amounts, when the user exports then restores under the approved policy, then every supported value and reference round trips exactly and restored active totals reconcile with History after reopen.
- `SPEC-007-AC-02` **Partly blocked: format/integrity definition.** Given zero-byte, partial/truncated, corrupt, integrity-failing, unsupported-version, duplicate-ID, invalid-money, or broken-reference input, when validation runs, then the candidate is rejected before any live-data mutation with a distinct non-sensitive explanation and recovery path.
- `SPEC-007-AC-03` Given a valid candidate and a failure injected during application before commit, when restore fails, then every live record remains as before, no partial replacement appears after reopen, and no success is shown.
- `SPEC-007-AC-04` **Blocked: supported record set and empty-backup policy.** Given a validated candidate and existing live records, when Preview opens, then supported-record counts and the exact atomic-replace consequence are presented before explicit confirmation; Cancel leaves live data unchanged.
- `SPEC-007-AC-05` **Blocked: encryption policy.** Given a 50,000-expense backup, when export, validation, and restore run on a named representative device, then the UI remains operable, phase durations and device/API are recorded, and no private content or provider URI enters logs, notifications, or unapproved transfer.
- `SPEC-007-AC-06` **Blocked: interruption/retry semantics.** Given cancellation, provider failure, process interruption, or an uncertain response, when the flow resumes, then it claims no uncommitted success, resolves committed state before retry, and does not apply one confirmed restore twice.

## Test mapping

These are future test contracts, not passing verification results. Check each scenario and keep unresolved product choices explicit.

| Criterion | Future verification | Scenario check |
| --- | --- | --- |
| AC-01 | `BackupRoundTripContractTest.supportedRecordsRoundTripExactlyAfterReopen`; `BackupRoomRoundTripTest.exportRestorePreservesReferencesAndTotals` | Trace approved record/field inventory through export, restore, and reopen. |
| AC-02 | `BackupValidationTest.rejectsCorruptUnsupportedDuplicatePartialAndInvalidReferencesBeforeMutation` | Walk zero-byte, partial, corrupt, tampered, unsupported, duplicate-within-file, invalid-money, and broken-reference rows. |
| AC-03 | `BackupRoomRollbackTest.failedApplyRollsBackAllRecordsAfterReopen` | Inject failure after some writes but before commit; compare all original records after reopen. |
| AC-04 | `RestorePreviewUiTest.validatedPreviewExplainsReplacementAndRequiresConfirmation` | Walk existing records, live ID collision, empty candidate, Confirm, and Cancel against D-04. |
| AC-05 | `BackupPerformanceDeviceTest.largeBackupKeepsUiOperable`; `BackupPrivacyTest.backupContentsAndUrisNeverReachLogs` | Inspect named device/API, timings, privacy boundary, and remote-backed provider handling. |
| AC-06 | `BackupInterruptionTest.uncertainCommitResolvesBeforeRetry`; `ExportDocumentTest.failedWriteNeverReportsComplete` | Walk canceled picker, provider error, interruption before/after commit, and repeated confirmation. |

Future commands: `./gradlew :core:application:test :app:testDebugUnitTest :app:connectedDebugAndroidTest`, plus recorded manual device performance/privacy audit. These future tests are not asserted passing.

## Open questions

1. **D-04 remaining detail:** Atomic replace with no merge is approved, including replacement rather than merging for matching live IDs. Define the exact restore scope for expenses, categories, methods, preferences, tombstones, and other records; decide whether an empty backup may replace nonempty live data and specify uncertain-commit retry behavior.
2. Approve format/version, encoding, integrity method/canonicalization, exact supported record/field set and unknown-field behavior, and required or optional encryption including missing-credential recovery.
