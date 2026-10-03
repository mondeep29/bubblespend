# BT-005 Planner plan

**Planner:** `bt005_planner` (`gpt-6-sol`), read-only. Prepared 2026-09-29 UTC.

## Objective and acceptance

Create a clean-checkout native Android Kotlin/Compose foundation, minimal accessible app shell, pure Kotlin core module boundaries, test harness, formatting/lint gates, CI, and setup instructions. The packet acceptance is foundation startup, route, theme/semantics smoke, architecture boundaries, and reproducible clean-checkout formatting, analysis, tests, and debug APK build. No approved feature specification is required for this scaffolding task; ADR-001 and ADR-002 govern architecture.

## Repository findings and risk

The task is already claimed; BT-002 is done and BT-003 is blocked independently. There is no production Android source, Gradle wrapper, or app test suite. The only Gradle project is the disposable BT-003 spike. The `.git` directory is empty and read-only, so this workspace cannot demonstrate a real Git clean checkout. The Linux shell has Python 3.14 but no Java, Gradle, or adb. Prior BT-003 evidence records a Windows JDK 17, SDK 35, and API 35 emulator. Risk is **elevated** because the work adds Gradle dependencies and CI.

## Proposed implementation and ownership

1. Coordinator records this plan and file ownership, requests independent security preflight, and runs `python3 workflow/gate.py check-start BT-005` before test/build work.
2. Builder establishes only the Gradle bootstrap required to compile the acceptance tests: wrapper, root/settings scripts, version catalog, Android `app` module, and pure Kotlin `:core:domain` / `:core:application` modules. Builder owns production and build files. Tester owns `app/src/test/**`, `app/src/androidTest/**`, and architecture test sources. Coordinate shared Gradle test wiring before edits.
3. Tester writes a launch/route/accessibility smoke test and an architecture boundary test that disallows Android/Compose/Room/DataStore imports and dependencies in pure core modules. Run the genuine failing checks before the app shell. Record the exact command and observed red output, then run `python3 workflow/gate.py check-build BT-005`.
4. Builder adds one Compose home route with accessible heading, theme semantics, explicit composition root, and module-boundary enforcement. Do not add global clock/ID/database state, expense behavior, account, network permission, analytics SDK, or networking.
5. Add least-privilege GitHub Actions (`contents: read`) for wrapper validation, `spotlessCheck`, lint, unit tests, and debug assembly; document JDK/SDK setup and `connectedDebugAndroidTest` command.
6. Tester runs the packet gates and records actual results. Reviewer performs independent feature review; coordinator runs `check-review`, `check-done`, then `transition.py done` only when all evidence is complete.

Production/build ownership: `settings.gradle.kts`, root `build.gradle.kts`, `gradle.properties`, `gradle/**`, `app/**` excluding Tester-owned test sources, `core/domain/**`, `core/application/**`, `.github/workflows/**`, `.gitignore`, and build/setup documentation. Coordinator owns `workflow/evidence/BT-005.*`.

## Version research and decisions to validate

The existing BT-003 spike pins AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, Activity Compose 1.10.0, Lifecycle 2.8.7, and SDK 35. The foundation reuses this exact known-compatible family to avoid adding a newer SDK installation or an AGP 9 migration during the initial bootstrap. Official AGP compatibility guidance confirms API 35 is supported from AGP 8.6, and AGP 8.7 requires Gradle 8.9. Official Compose setup (last updated 2026-09-16) recommends the current BOM 2026.09.00/Kotlin 2.4.10 line and notes Compose 1.12 requires API 37/AGP 9; this foundation deliberately stays on the prior Compose family and API 35 until a separately scoped toolchain upgrade is justified. Pin exact values in the catalog: **JDK 17; Gradle 8.9; AGP 8.7.3; Kotlin and Compose compiler plugin 2.0.21; compileSdk/targetSdk 35; minSdk 26; Compose BOM 2024.12.01; Activity Compose 1.10.0; Lifecycle 2.8.7; Navigation Compose 2.8.5; Room 2.6.1; Preferences DataStore 1.1.1; JUnit 4.13.2; Spotless 7.0.0; ktlint 1.5.0.** Room and DataStore are catalog-only in BT-005; instantiate neither. Verify actual plugin and dependency resolution with the pinned build. The Windows host already has SDK 35 and JDK 17.

Official source links, accessed 2026-09-29 UTC:

- Android Compose setup and compiler alignment: https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
- Android Gradle Plugin compatibility, Gradle requirements, and API levels: https://developer.android.com/build/releases/about-agp
- AGP 9 built-in Kotlin and compatibility details: https://developer.android.com/build/releases/agp-9-0-0-release-notes
- Kotlin release history: https://kotlinlang.org/docs/releases.html
- AndroidX stable release table: https://developer.android.com/jetpack/androidx/versions
- Room release history: https://developer.android.com/jetpack/androidx/releases/room
- DataStore release history: https://developer.android.com/jetpack/androidx/releases/datastore
- Spotless Gradle plugin documentation: https://github.com/diffplug/spotless/blob/main/plugin-gradle/README.md
- GitHub Actions pinned release commit references, verified from the repositories' official GitHub API commit endpoints on 2026-09-29 UTC: https://api.github.com/repos/actions/checkout/commits/v4, https://api.github.com/repos/actions/setup-java/commits/v5, https://api.github.com/repos/android-actions/setup-android/commits/v4, and https://api.github.com/repos/gradle/actions/commits/v6. CI records the resolved SHAs alongside each action reference.

## Verification and recovery

Required packet command: `./gradlew spotlessCheck lint testDebugUnitTest assembleDebug`. Device smoke: `./gradlew connectedDebugAndroidTest` on a recorded API 35 or 36 device/emulator (provision API 37 only if the chosen SDK requires it). Also run workflow `check-start`, `check-build`, `check-review`, and `check-done` at their required handoffs. Report unavailable checks as unrun; compilation is not device proof.

Before edits, capture a file inventory and preserve exact preexisting file contents that an owned edit may touch. If rejected, restore only BT-005-created files or owned-file edits to their captured baseline. Keep all existing workflow docs, ADRs, proof records, and spike content. No user data or destructive migration is involved.

## Open limits

The production application ID is not specified; choose a stable internal namespace and document it before publishing. No usable Git metadata prevents a clean-checkout proof. Linux lacks the required Java/Gradle/Android SDK tools. No scope or product behavior depends on BT-003's overlay proof.
