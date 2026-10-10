# First-run setup and settings verification

SPEC-009 implementation uses the existing currency DataStore. Completion writes the currency, week start, and completion marker in one atomic update. Existing confirmed currencies remain fixed; installations without a completion marker resume setup to choose a week start. Unfinished choices are stored separately and do not confirm currency.

The application reads stored settings before enabling expense saves. Settings failures preserve input and expose Retry. Setup can be left for browsing or expense drafting; returning from setup preserves the current expense draft. WeeklyReportingPeriod reads the persisted weekday for each query and accepts the current device timezone. Production dashboard/reporting UI remains a separate unfinished feature.

## Acceptance mapping

| Criterion | Recorded verification coverage |
| --- | --- |
| AC-01: suggestion, explicit confirmation, save gate | SetupSettingsJourneyTest.freshInstallSuggestionsBrowseDraftAndExplicitCompletion; localeWithoutCurrencyRequiresExplicitChoice; SetupPersistentJourneyTest.firstLaunchUnfinishedSetupReopenCompletionExpenseAndSettingsSurviveStorageReopen; SetupCurrencyDurabilityAcceptanceTest.confirmationReportsSuccessOnlyAfterDurableCurrencyCanBeRead; ExpenseRecoveryTest.committedCreateWithLostResponseRetryPreservesOneLogicalExpense |
| AC-02: all weekdays, restart, timezone | SetupSettingsAcceptanceTest.weeklyQueriesReadAllPersistedWeekdaysInTheSuppliedCurrentTimezone; SetupSettingsStorageTest.legacyCurrencyIsPreservedAndAllWeekdaysSurviveReopenAndDriveQueries |
| AC-03: fixed currency and durable choices | SetupSettingsStorageTest.draftAndCompletedSettingsSurviveStoreCloseAndReopen; lostCompletionResponseReconcilesAtomicallyAndRetryCannotResetLaterWeek; SetupSettingsJourneyTest.unfinishedChoicesResumeInANewViewModelAndLocaleCannotReplaceConfirmedCurrency |
| AC-04: validation and recovery | SetupSettingsAcceptanceTest.invalidCurrencyOrWeekCannotCommitOrDiscardChoices; acknowledgedButUnreadableCompletionCannotReportSuccess; SetupSettingsStorageTest.failedWritesKeepConfirmationAbsentAndPreviousWeekEffectiveUntilRetry; SetupSettingsJourneyTest.invalidCurrencyKeepsInputWithoutConfirmingAndCanBeCorrected; completionFailureKeepsChoicesAndRetryCompletes; settingsFailureRetainsAttemptedDayAndPreviousEffectiveDayUntilRetry; readFailureAllowsBrowsingAndDraftingButBlocksSaveUntilRetry; failedDraftWriteKeepsEnteredChoiceForRetryAndReopen |
| AC-05: browsing and interruption | SetupPersistentJourneyTest.firstLaunchUnfinishedSetupReopenCompletionExpenseAndSettingsSurviveStorageReopen; SetupSettingsStorageTest.lostCompletionResponseReconcilesAtomicallyAndRetryCannotResetLaterWeek; SetupSettingsJourneyTest.unfinishedChoicesResumeInANewViewModelAndLocaleCannotReplaceConfirmedCurrency |
| AC-06: visible labels, errors, recovery and display settings | SetupSettingsJourneyTest.largeTextKeepsSetupValidationRecoveryAndSettingsControlsReachable; physical display-setting verification and screenshot inspection |

## Results

Verified 2026-10-10 on a physical Pixel 7 (panther), Android 17 / API 37.

- Required build checks passed: spotlessCheck, lint, testDebugUnitTest, assembleDebug, connectedDebugAndroidTest, :core:application:test, and :core:domain:test.
- Local suites passed 28 application, 11 domain, and 3 app tests (42 total), with no failures, errors, or skips.
- The full connected suite passed **44/44**, including the existing expense journeys and the lost-response create Retry regression. [Full device results](setup-settings/normal-results.xml).
- The focused setup suite passed **9/9** with font scale 2.0, font weight adjustment 300, high text contrast enabled, and window/transition/animator scales at zero. [Display results](setup-settings/display-results.xml).
- Screenshots were visually inspected: setup, currency validation, saved choices after a write failure with Retry, and Settings retain readable labels, errors, and controls. Controls remained reachable by touch/scrolling. [Setup](setup-settings/images/setup-large.png), [validation](setup-settings/images/setup-validation-large.png), [Retry](setup-settings/images/setup-retry-large.png), [Settings](setup-settings/images/settings-large.png).
- The initial cleanup restored preference rows but left Android’s cached bold weight active. On 2026-10-10, follow-up correction explicitly restored weight 0 and verified active configuration at font scale 1.0 and weight 0. The display runner now snapshots and verifies active font configuration and resets the weight explicitly before removing an originally absent preference. Screen-reader checks are excluded under D-08.
- Storage interruption is verified by closing/reopening real DataStore and Room files, rebuilding the ViewModel, and injecting a lost completion response after an atomic write. No claim is made about a hardware power-loss test.

## Review APK

Debug APK: app/build/outputs/apk/debug/app-debug.apk. Built from revision 07eedbfe6ee0a665d36e0198ca59d23221bd61e0 plus the modified working tree. Successfully installed and exercised by the connected suite. SHA-256: 80c183a3b1e86ab6e61d517e5a1ea8305859997072eb9c6d82338889cfadfdea.

[Verification metadata and source hashes](setup-settings/verification.json) identify the verified implementation. [Display runner](../../scripts/verify-setup-display.ps1) reproduces the focused display checks and collects screenshots through Gradle’s additional test-output directory.
