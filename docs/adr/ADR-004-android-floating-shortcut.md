# ADR-004: Android floating shortcut strategy

**Status:** Approved for the opt-in permission and platform-adapter boundary only; production overlay remains incomplete, with partial physical-device proof.

Use an in-app Compose floating action first. The system overlay is opt-in through Android's special overlay permission and an explicit settings switch. A native service/window adapter owns creation, touch/drag/snap, teardown, permission revocation, lifecycle behavior, and opening the app. The adapter sends commands through `FloatingShortcutGateway`; expense persistence stays in application use cases and must be idempotent.

The spike must demonstrate permission request/denial/revocation, overlay creation/removal, drag/expand, opening the app, exactly-once save boundary, rotation, process death, restart behavior, and lock-screen privacy on a named physical Android device. Emulator runs do not satisfy or supplement this requirement and must not be launched for it. Record Android version, device, observed restrictions, failures, and a go/no-go recommendation in `docs/proofs/android-overlay.md`. Do not infer device restart behavior from code alone.

Permission denial cannot block normal app use. A visible disable control must remove the overlay immediately. Platform policy and notification/service requirements must be checked before choosing service type or restart mechanism.

## Physical-device findings (2026-10-07)

On the Pixel 7 running Android 17 / API 37, the isolated BT-003 revision spike displayed the permission explanation, kept in-app entry available after denial, and created a collapsed application overlay after the user granted access in Android Settings. With the device locked, the service showed a neutral button without the synthetic entry panel. These observations establish only the recorded scenarios in `docs/proofs/android-overlay.md`.

The product owner approved automatic reappearance after restart (D-01) and a neutral button while locked (D-06). The ordinary-service boot restoration path, interaction, rotation, revocation, and process recovery still require device results. Android may restrict the boot service start; a failed ordinary start is a no-go for this proposed adapter and must not be hidden by an unapproved foreground-service workaround. Screen-reader behavior is outside MVP scope under D-08; visible values, touch drill-down, large/bold text, contrast, and reduced motion remain in scope.
