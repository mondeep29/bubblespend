# SPEC-005: Android floating shortcut

**Status:** Draft

## User outcome

Open fast expense entry above another app after opting in, or use the same entry flow inside BubbleSpend without special permission.

## In scope

In-app shortcut; Android overlay permission education and settings; collapsed, expanded, saving, success, error, dismissal, drag/snap, restore, open dashboard; lifecycle and lock-screen privacy.

## Out of scope

Automatic permission bypass, background data sync, notification-triggered expense suggestions.

## User journeys

The user enables the shortcut from an in-app setting, reads why Android special access is needed, and chooses whether to grant it in Android Settings. A denial returns to usable in-app expense entry. After a grant, one collapsed shortcut appears above other apps. A tap opens quick entry, a drag moves the shortcut to a reachable edge, and a long press opens BubbleSpend. Close or an outside tap dismisses an unsaved panel without creating an expense. Save confirms only after the shared expense use case persists one record, then returns to the collapsed shortcut.

The user can disable the shortcut at any time through the in-app setting. Permission revocation removes the overlay and leaves in-app entry available. With the shortcut enabled and permission still granted, process recreation and device restart restore one collapsed shortcut automatically. When the device locks, entry contents are replaced by a neutral button; entry becomes available only after unlock.

## State model

Disabled → Collapsed → Expanded → Saving → Success → Collapsed. Expanded may collapse on outside tap/close or enter Error on save failure. Dismissed hides the shortcut until restored through a visible settings path. Permission revoked returns to Disabled safely.

## Functional requirements

- Explain special access and request it only after an explicit enable action. A disable switch removes the window promptly.
- Tap opens quick entry; drag clamps within safe bounds and release near an edge snaps to it; long press opens dashboard; outside tap/close collapses without saving.
- Saving delegates to the same idempotent expense use case as in-app entry. Confirmation occurs only after persistence.
- Position adapts to rotation and display changes. While the device is locked, only a neutral button appears; expense details are unavailable.
- Lifecycle after process death and device restart follows D-01 and tested platform behavior. Preserve the explicit opt-in across restart only while overlay permission remains granted; a single collapsed shortcut reappears automatically.

## Business rules

- Overlay access and shortcut enablement are separate states. A granted Android permission alone must not create a shortcut; an explicit in-app enable action is required. Persist the user's enable choice locally, and clear it on explicit disable. Recheck permission whenever the app or service restores the window.
- The expanded panel delegates validation and persistence to the same application use case as in-app entry. A submission token survives repeated taps and interrupted completion, so one logical submission cannot create multiple expenses. Do not report success before the repository confirms persistence.
- The adapter owns only Android window, permission, and lifecycle operations. Domain and application code stay independent of Android and Compose. Overlay presentation contains no different financial business rules from in-app entry.
- A locked device may show the neutral shortcut but no expense amount, category, merchant, note, draft, or transaction detail. Tapping or long pressing it while locked cannot open quick entry or expose financial data. Unlock returns to the collapsed state; unsaved expanded input must not be exposed on the lock screen.

## Error and empty states

Permission denied or revoked leaves a clear disabled state and normal in-app entry available. A service/window failure leaves the enable setting recoverable without claiming that the shortcut is visible. Save validation and storage failures preserve the editable draft and a correction or retry path; no success confirmation appears until persistence is known. If no active category or payment method exists, Save cannot succeed and the user can open management through the main app. If launching the app fails, the shortcut remains usable and a visible recovery path returns to the app. An interrupted or uncertain save resolves the persisted result by submission token before reporting success or offering a retry.

## Accessibility and privacy

Large and bold text must not clip or hide entry controls. Contrast and reduced-motion settings must leave controls and states visible and usable; state cannot rely on color or motion alone. Do not expose transaction details on the lock screen or log entered values. The app shows no financial content in system overlay permission explanations. Screen-reader support is not an MVP requirement under D-08; visible text/list values and touch paths remain required where specified.

## Analytics/telemetry events

No remote events. Local debugging may record state transitions without financial content.

## Acceptance criteria

- `SPEC-005-AC-01`: Given permission is absent, when a user enables the overlay, then a plain-language explanation precedes system settings; denial leaves the app usable.
- `SPEC-005-AC-02`: Given the user explicitly enabled the shortcut and overlay permission remains granted, when the setting is enabled or the device restarts, then one collapsed shortcut appears and can open entry; permission denial leaves in-app entry available.
- `SPEC-005-AC-03`: Given the shortcut is dragged or display rotates, when released, then it remains inside safe bounds and snaps to a reachable edge.
- `SPEC-005-AC-04`: Given expanded entry, when the user closes or taps outside, then no expense is saved.
- `SPEC-005-AC-05`: Given valid entry and repeated Save taps, when persistence succeeds, then one expense is created, confirmation appears, and the shortcut collapses.
- `SPEC-005-AC-06`: Given permission revocation or disabling in settings, when the state is observed, then the window is removed and normal app entry remains available.
- `SPEC-005-AC-07`: Given the device is locked, when the overlay is present, then only a neutral button is shown and private expense details are not displayed; tapping while locked does not open entry until the device is unlocked.
- `SPEC-005-AC-08`: Given a long press, when the app is launchable, then the dashboard opens.

## Test mapping

The [physical Pixel 7 proof](../proofs/android-overlay.md) records partial permission and platform feasibility. Production acceptance tests must repeat the lifecycle checks against the actual adapter on a named physical device. Verification command: `./gradlew spotlessCheck lint testDebugUnitTest connectedDebugAndroidTest assembleDebug`; instrumented execution is on the physical device only. Screen-reader testing is excluded under D-08.

| Criterion | Required verification |
| --- | --- |
| AC-01 | Physical permission explanation, denial, and in-app entry; production permission UI test |
| AC-02 | Physical enable, process recreation, and actual device restart with permission still granted; production single-window lifecycle test |
| AC-03 | Physical drag/snap and rotation with visible bounds; geometry unit test |
| AC-04 | Physical close and outside tap with unchanged stored count; production dismissal test |
| AC-05 | Physical rapid Save with one persisted synthetic record; production shared-use-case idempotency test |
| AC-06 | Physical disable and permission revocation with window removal and usable in-app entry; production permission-state test |
| AC-07 | Physical lock/unlock from both collapsed and expanded states; production privacy-state test |
| AC-08 | Physical long press with the main app foreground; production app-launch test |

## Open questions

The ordinary-service boot restoration path is still awaiting the physical-device result. If Android blocks it, SPEC-005 remains Draft until a policy-compliant mechanism is proposed, proved, and independently reviewed. D-01, D-06, and D-08 are resolved in the decision log.
