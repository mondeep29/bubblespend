# ADR-001: Native Android application architecture

**Status:** Approved

Use a single Android app with Kotlin and Jetpack Compose. Organize feature packages/modules as presentation → application → domain contracts, with infrastructure implementing domain contracts. Use ViewModel and StateFlow for screen state, Navigation Compose for routes, and coroutines for asynchronous work. Start with an explicit app composition root; add a DI framework only through a separately reviewed task if the tested scope needs it. BT-005 selects and pins exact Android, Kotlin, Compose, Room, DataStore, Gradle, and test dependency versions in checked-in build configuration; later tasks cannot upgrade them incidentally.

Domain/application code has no Android, Compose, Room, DataStore, chart, or overlay imports. Dependency checks enforce this. Presentation invokes use cases; platform adapters implement gateways. Feature boundaries: onboarding, expenses, categories, dashboard, analytics, history, floating shortcut, settings, backup/restore. Core boundaries: money, time, IDs, design system, errors, database, platform.

**Rationale:** Native platform APIs are central to the overlay and lifecycle behavior. Pure business layers make money/date rules and repository contracts deterministic and testable.

**Verification:** Compile-time boundary check, injected clock/ID/timezone tests, app startup and route smoke tests.
