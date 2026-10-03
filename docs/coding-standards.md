# Builder coding standards

Read this contract before implementing a BubbleSpend feature. The task packet defines the deliverable and file ownership; approved specifications and ADRs define behavior. The persisted plan defines the implementation order. Report a blocking ambiguity once with affected acceptance IDs and continue independent work.

## Deliver a usable feature

- Implement each merged packet in its ordered slices: domain contracts, storage adapters, application use cases, Compose presentation, and integration verification as applicable.
- Reuse the same domain validation and repository operations for in-app and overlay entry. Preserve submission idempotency across retries.
- Complete success, empty, validation, loading, failure, and recovery states required by the approved criteria. A placeholder screen or hardcoded data does not satisfy feature delivery.
- Keep prerequisite specification approval and meaningful failing acceptance tests ahead of the corresponding implementation slice. Consolidation reduces handoffs; each slice retains its verification obligations.

## Kotlin and architecture

- Follow the checked-in formatter and existing package structure. Use descriptive names, small cohesive functions, explicit types at public boundaries, and immutable data by default.
- Keep domain and application modules independent of Android, Compose, Room, DataStore, chart libraries, and remote API types. Presentation calls application use cases; infrastructure implements domain interfaces.
- Use the existing composition root. Keep dependencies and toolchain versions pinned; introduce libraries only when the packet authorizes them.
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
- Reuse design tokens and string resources. Provide labels, semantics, focus handling, and announced errors. Respect large text, reduced motion, and the approved TalkBack journeys.
- Every chart and bubble has exact accessible text/list values and a matching transaction drill-down.
- Request overlay access only after opt-in. Denial/revocation leaves in-app entry usable; lifecycle and lock-screen behavior require platform proof before release.
- Keep financial data on device. Never log amounts, merchants, notes, backup contents, or credentials. Add no account, telemetry SDK, or network dependency to the MVP.

## Verification and handoff

- Tester owns acceptance tests. Builder may request a test correction with a reproduction and affected criterion; Builder cannot edit or disable those tests.
- Run the packet's named commands. Record actual results, device/API details for instrumented checks, and any unavailable checks. Preserve existing passing behavior.
- Return one handoff with changed files, acceptance IDs satisfied, commands/results, remaining blockers, and recovery notes. Coordinator updates queue/evidence; independent Reviewer approves completion.
- For an APK intended for review, record artifact path, source provenance, build variant, device/install result, and known limitations. Publishing a release requires the exact action approval and release gate.
