# SPEC-005: Android floating shortcut

**Status:** Draft

## User outcome

Open fast expense entry above another app after opting in, or use the same entry flow inside BubbleSpend without special permission.

## In scope

In-app shortcut; Android overlay permission education and settings; collapsed, expanded, saving, success, error, dismissal, drag/snap, restore, open dashboard; lifecycle and lock-screen privacy.

## Out of scope

Automatic permission bypass, background data sync, notification-triggered expense suggestions.

## State model

Disabled → Collapsed → Expanded → Saving → Success → Collapsed. Expanded may collapse on outside tap/close or enter Error on save failure. Dismissed hides the shortcut until restored through a visible settings path. Permission revoked returns to Disabled safely.

## Functional requirements

- Explain special access and request it only after an explicit enable action. A disable switch removes the window promptly.
- Tap opens quick entry; drag clamps within safe bounds and release near an edge snaps to it; long press opens dashboard; outside tap/close collapses without saving.
- Saving delegates to the same idempotent expense use case as in-app entry. Confirmation occurs only after persistence.
- Position adapts to rotation and display changes. Overlay details are hidden when the device is locked.
- Lifecycle after process death and device restart follows D-01 and tested platform behavior.

## Error and empty states

Permission denied/revoked, service failure, save failure, no active category/method, app unavailable while launching. Normal in-app entry remains available.

## Accessibility and privacy

TalkBack labels shortcut state/action and exposes a path to the full app. Large text must not clip entry controls. Respect reduced motion. Do not expose transaction details or log entered values.

## Analytics/telemetry events

No remote events. Local debugging may record state transitions without financial content.

## Acceptance criteria

- `SPEC-005-AC-01`: Given permission is absent, when a user enables the overlay, then a plain-language explanation precedes system settings; denial leaves the app usable.
- `SPEC-005-AC-02`: Given permission is granted, when enabled, then one collapsed shortcut appears and can open entry.
- `SPEC-005-AC-03`: Given the shortcut is dragged or display rotates, when released, then it remains inside safe bounds and snaps to a reachable edge.
- `SPEC-005-AC-04`: Given expanded entry, when the user closes or taps outside, then no expense is saved.
- `SPEC-005-AC-05`: Given valid entry and repeated Save taps, when persistence succeeds, then one expense is created, confirmation appears, and the shortcut collapses.
- `SPEC-005-AC-06`: Given permission revocation or disabling in settings, when the state is observed, then the window is removed and normal app entry remains available.
- `SPEC-005-AC-07`: Given the device is locked, when the overlay is present, then private expense details are not displayed.
- `SPEC-005-AC-08`: Given a long press, when the app is launchable, then the dashboard opens.

## Test mapping

Pending Android overlay spike and test agent. Include native unit, Compose UI, and instrumented device tests.

## Open questions

D-01 restart policy; D-06 lock-screen behavior; Android service and notification policy from technical spike.
