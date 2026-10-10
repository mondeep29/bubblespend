# Android overlay technical proof

**Status:** Partial physical-device proof (2026-10-07)

## Recorded environment

- Device: Pixel 7 (`panther`), Android 17 / API 37, build `CP3A.260905.009`.
- Isolated proof package: `dev.bubblespend.bt003proof.revision`.
- Artifact SHA-256: `28B86038A3B42065D93F16D3EAD6E8F1CA8A80A14EEB3A4174C0B23DAC3C7330`.
- Data: fixed synthetic values only. No personal content was captured.

## Observed results

| Scenario | Result | Observation |
| --- | --- | --- |
| Permission denied | Pass | The visible explanation offered **Not now**; denial returned to the normal in-app synthetic entry path. |
| Permission granted | Pass | Android Settings granted `SYSTEM_ALERT_WINDOW`; `appops` reported `allow`. |
| Overlay creation | Pass | Window Manager reported a visible `TYPE_APPLICATION_OVERLAY` owned by the isolated revision package. |
| Collapsed presentation | Pass | Window Manager reported a visible 303 x 93 shortcut window. |
| Lock-screen privacy | Pass | With the Pixel locked, the service logged `neutral shortcut shown while locked`; the window remained the small neutral shortcut and did not expose the synthetic entry panel. |

## Remaining limits

Tap/expand, close without save, drag/snap, save-once, long-press app launch, rotation, process recreation, physical restart restoration, disable/removal, and permission revocation were not completed in this pass. The first tap was made while the device was locked and correctly retained the neutral presentation. It was not repeated after the user reported excessive repetition of the synthetic test.

**Recommendation:** Keep the production overlay task gated. The permission, creation, denial fallback, and locked neutral presentation are feasible on the target device. Lifecycle and interaction scenarios above still need proof against the production slice or one bounded follow-up, without replaying the chart proof.

Run this proof on a named physical Android device only. Do not launch or poll an emulator for this proof. Use an existing connection record or the user's connection confirmation; otherwise check `adb devices` once for the task. If the physical device is absent, record the proof as unavailable and wait for the user's connection confirmation without repeated checks.

Record build SHA, Android API level, physical device model, permission state, exact steps, observed behavior, failures, and screen recording or logs with private content redacted. Prove grant, deny, revoke, enable/disable, create/remove, tap, drag, snap, expand, save once, open app, rotation, process death, device restart, and lock-screen privacy. Record platform policy/service constraints and update ADR-004, SPEC-005, D-01, and D-06.

Do not mark the production overlay task ready from this partial proof.
