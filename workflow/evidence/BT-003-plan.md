# BT-003 Planner handoff

**Planner:** `bt003_planner_sol` (`gpt-6-sol`, read-only)
**Planning date:** 2026-09-27 UTC
**Objective:** Prove floating-shortcut permission/lifecycle/input/privacy behavior on a representative emulator and a real device, and separately prove accessible chart/list/drill-down behavior in a disposable spike.
**Risk:** Elevated. Overlay permission, service lifecycle, and lock-screen behavior are involved. An independent Reviewer security preflight must approve the exact proof scope before device proof work; `check-start` must pass.
**Acceptance IDs:** Proof informs draft `SPEC-005-AC-01` through `SPEC-005-AC-08` and `SPEC-004-AC-06`; these are proof inputs, not approved implementation criteria.

## Ordered proof plan

1. Confirm a disposable emulator and a real device, their model/manufacturer, Android API/build, display configuration, target SDK, serial alias, and OEM restrictions. Locate Android SDK/build tools and record availability before building.
2. Create only an isolated spike under `spikes/`: explicit Enable/Disable, in-app fallback, `TYPE_APPLICATION_OVERLAY`, synthetic expanded form, and local idempotency counter/store. Log only state transitions. Evaluate whether a foreground service is needed and verify its type/permission/start path; do not select a type just to make the spike run.
3. On each device record: permission absent; explanation/settings; deny/back out; in-app fallback; grant/enable; collapsed window; tap/expand; close/outside tap without save; drag/clamp/snap; repeated synthetic Save with one record; long-press app launch; disable/remove; permission revocation; rotation/display change; force-stop/relaunch; actual device restart; lock/unlock when expanded and collapsed. Record steps, expected/observed result, permission state, screenshots/video, and redacted logs. Label every unrun case **untested**.
4. Build a separate Compose visual spike with fixed synthetic fixtures for bubbles, trend buckets, category share, and comparison bars plus exact values and an equivalent ordered list. Verify list/visual selection maps to the same synthetic IDs and period/filter interval. Check baseline labeling, TalkBack order/actions, touch and TalkBack drill-down, large/bold text, high contrast, reduced motion, empty state, and zero buckets. Capture evidence on a named device.
5. Record actual results, limitations, and separate go/no-go recommendations in `docs/proofs/android-overlay.md` and `docs/proofs/analytics-visuals.md`. Update ADR-004 with observed constraints and record findings about D-01/D-06 without marking either product-owner decision approved. Obtain independent final review.

## Product decisions and boundaries

- D-01 (restart behavior) and D-06 (lock-screen behavior) remain owner decisions. Proof can establish platform behavior and feasible choices but cannot approve either policy.
- No chart library is selected until proof supports that choice.
- Use synthetic values and transaction IDs only. Never capture financial records, merchants, notes, backups, or credentials.
- Disable the spike overlay and revoke only its special access after evidence capture. Do not alter or remove unrelated apps or user data.
- No production overlay feature is in scope.

## Android research accessed 2026-09-27 UTC

- `SYSTEM_ALERT_WINDOW` must be declared and user-granted; call `Settings.canDrawOverlays()`. The settings action may open a general screen on Android 11+, so recheck permission on return: [Settings API](https://developer.android.com/reference/android/provider/Settings).
- `TYPE_APPLICATION_OVERLAY` requires special permission. Android may alter position, size, or visibility; Android 12+ limits touches passed through untrusted overlays: [WindowManager.LayoutParams](https://developer.android.com/reference/android/view/WindowManager.LayoutParams).
- Foreground service starts are restricted; target API 35+ cannot rely on overlay permission alone without an already visible overlay for that exemption. Declarations need matching service type and permissions: [background-start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), [service declarations](https://developer.android.com/develop/background-work/services/fgs/declare).
- Android 15 tightens background activity launch behavior, so prove long-press app launch on both device types: [Android 15 behavior changes](https://developer.android.com/about/versions/15/behavior-changes-15), [activity launch security](https://developer.android.com/guide/components/activities/secure-bal).
- Compose charts must expose semantic labels/actions and traversal order; manually exercise TalkBack: [Compose semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics), [traversal order](https://developer.android.com/develop/ui/compose/accessibility/traversal), [accessibility testing](https://developer.android.com/guide/topics/ui/accessibility/testing).

## Initial environment observation

The initial shell lookup found no `adb` or `emulator` command in `PATH`. This is not a device behavior result; inspect SDK locations and available devices after preflight before deciding whether the proof can run. The repository currently contains no Android/Gradle application project.

## Verification and recovery

- `python3 workflow/gate.py check-start BT-003` after independent security preflight and before any build/proof.
- `adb devices -l`; for each connected device, collect `ro.build.version.sdk` and `ro.product.model` using its exact device serial.
- Use the spike's checked-in build/install command once its build system exists. Record package ID, exact command, and artifact hash.
- `python3 workflow/gate.py check-review BT-003`, `python3 workflow/gate.py check-done BT-003`, then `python3 workflow/transition.py done BT-003` only after both proof records and independent review pass.
- Task-start copies of proof templates, ADR-004, and decisions are stored under `/tmp/bt003-baseline/`. The workspace has no usable Git metadata. Restore only BT-003-owned files if a review rejects them; no destructive operation is planned.

## Owned files

`spikes/`, `docs/proofs/android-overlay.md`, `docs/proofs/analytics-visuals.md`, `docs/adr/ADR-004-android-floating-shortcut.md`, `docs/decisions.md`, and BT-003 evidence files. The coordinator owns evidence and task state; Reviewer is read-only.
