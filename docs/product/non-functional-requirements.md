# Non-functional requirements

## Data correctness and recovery

- Use database transactions for multi-record mutation and restore; support idempotent saves at the application boundary.
- Detect invalid or unsupported backups before changing live data. Restore failure rolls back fully.
- Every schema change has forward migration tests from all supported versions and a documented recovery strategy.
- No uncovered branches in money arithmetic, date boundaries, migrations, backup validation, or delete/restore logic.

## Performance

- Expense save should feel immediate. Use optimistic UI only with a clear rollback path.
- Dashboard and analytics remain responsive with 50,000 expenses on a representative mid-range device. Profile before moving work to background dispatchers or changing queries.
- Avoid blocking the main thread; respect the platform animation budget and reduced-motion setting.

## Privacy and security

- The MVP has no third-party analytics SDK or network dependency.
- Logs exclude transaction amounts, merchants, notes, other personal transaction content, backup payloads, and credentials in every build type.
- Overlay previews cannot reveal private transaction data on the lock screen.
- Use Android system document APIs for user-selected backup locations; request special access only when required.
- Expense and preference data remain in the app's local storage and the app adds no network dependency or automatic transfer in the MVP. User-initiated export follows an approved backup specification and Android system document APIs; whether a user may choose a remote-backed document provider remains unresolved under D-07.

## Accessibility and localization

- Aim for WCAG 2.2 AA contrast where relevant; use Android minimum touch targets and logical touch/keyboard navigation. Screen-reader support is outside MVP scope under D-08.
- Supply text/list equivalents for every visualization. Bubble size and color cannot be the only information channels.
- Support large text, bold text, high contrast, reduced motion, and localization-ready strings.

## Quality gates

- Per change: Kotlin formatting, Android Lint/static analysis, unit tests, repository contracts, Compose UI tests, generated-source consistency, and debug build.
- Main/release: integration/UI journeys, migration and backup suites, accessibility checks, dependency and secret scans, and release build.
- Domain/application line coverage target: 90%; infrastructure target: 80%. Coverage does not replace meaningful assertions.

## Evidence and unresolved targets

- Map each approved acceptance criterion to a named automated test or a reproducible manual/device check. Record command, device/API level where relevant, and result in [completion status](../project-status.md) or the relevant specification/proof.
- The 50,000-expense responsiveness requirement applies on a representative mid-range device. The task that introduces the benchmark must identify the device and measured query/render operations; do not treat the qualitative phrase “feels immediate” as a numeric latency threshold.
- Accessibility evidence covers success, empty, validation, and recovery states for each approved critical journey, not only the default screen.
