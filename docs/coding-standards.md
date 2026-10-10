# Coding standards

Approved specifications and architecture decisions define behavior. Keep implementation focused on the requested feature, preserve existing passing behavior, and resolve product questions before changing visible behavior or data semantics.

## Deliver a usable feature

- Implement a usable feature across application rules, storage, and Compose presentation as needed.
- Reuse the same domain validation and repository operations for in-app and overlay entry. Preserve submission idempotency across retries.
- Complete success, empty, validation, loading, failure, and recovery states required by the approved criteria. A placeholder screen or hardcoded data does not satisfy feature delivery.
- Verify financial calculations, persistence, restore, and permissions with meaningful behavior tests, including failure and recovery cases.

## Kotlin and architecture

- Follow the checked-in formatter and existing package structure. Use descriptive names, small cohesive functions, explicit types at public boundaries, and immutable data by default.
- Keep domain and application modules independent of Android, Compose, Room, DataStore, chart libraries, and remote API types. Presentation calls application use cases; infrastructure implements domain interfaces.
- Use the existing composition root. Keep dependencies and toolchain versions pinned; avoid unrelated upgrades or unnecessary libraries.
- Inject clocks, UUID generation, locales, timezone, and dispatchers where operations depend on them. Avoid hidden global defaults and unmanaged coroutine scopes.
- Model expected validation/storage failures explicitly. Catch specific failures at adapter boundaries; preserve cancellation and do not turn a failed operation into success. Never add unconditional retries.

## Financial data and persistence

- Represent money as signed 64-bit integer minor units plus ISO currency code. Check overflow and currency equality. Use exact decimal parsing; reject excess precision instead of silently rounding. Floating point is unsuitable for financial calculations.
- Store UTC instants. Resolve local calendar boundaries independently into inclusive-start, exclusive-end intervals. History, dashboard, analytics, and drill-down use the same filters.
- Use Room/SQLite as the device source of truth. Keep UUIDs, positive versions, timestamps, tombstones, and isolated local sync status consistent with approved contracts.
- Use database transactions for compound mutations. Run shared repository contracts against fakes and Room. Check in reproducible schemas and migration fixtures; never use destructive migration fallback.
- Preserve recoverable input on failed saves and avoid duplicate mutations. Backup validation must finish before any approved restore mutation.

## Compose, accessibility, and privacy

- Use ViewModel and StateFlow for screen state and lifecycle-aware collection. Keep database work and business decisions out of composables; perform blocking work off the main thread.
- Reuse design tokens and string resources. Provide labels, semantics, focus handling, and visible errors. Respect large/bold text, contrast, and reduced motion; screen-reader support is outside MVP scope under D-08.
- Every chart and bubble has exact accessible text/list values and a matching transaction drill-down.
- Request overlay access only after opt-in. Denial/revocation leaves in-app entry usable; lifecycle and lock-screen behavior require platform proof before release.
- Keep financial data on device. Never log amounts, merchants, notes, backup contents, or credentials. Add no account, telemetry SDK, or network dependency to the MVP.

## Verification

- Run the relevant commands in [build setup](build-setup.md). Record actual results and unavailable checks; never disable a failing test to claim success.
- Update [completion status](project-status.md) when a feature is implemented and verified. Distinguish approved requirements from completed features.
- Device checks run on a connected physical Android device. Record model/API details and known limitations.
- For an APK intended for review, record artifact path, source revision, build variant, and device/install result. Publishing a release requires explicit approval.
