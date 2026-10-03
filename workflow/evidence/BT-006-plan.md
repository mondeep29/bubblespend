# BT-006 plan — 2026-09-29 UTC

## Objective and source status

Implement pure Kotlin signed-`Long` minor-unit money, exact localized numeric input conversion, UTC half-open calendar intervals, and deterministic time/ID context. Scope is `:core:domain` and its JVM tests only. ADR-001 and ADR-003 approve the architecture, storage, UTC, and `[start,end)` invariants. `SPEC-003` is Draft and explicitly leaves dashboard previous-period/custom comparison semantics and D-02 open. D-05 is also open. The BT-006 task acceptance IDs below derive from the approved ADR and packet; they do not approve `SPEC-003` behavior.

## Acceptance IDs and exact test contract

The new pre-implementation red tests cover `BT-006-AC-01` through
`BT-006-AC-06`. `BT-006-AC-07` is an existing architecture-boundary test and
will be verified in the post-implementation regression run; it does not need a
new failing test because the baseline already passes it. This distinction
matches the workflow gate's red-coverage field to the criteria requiring a new
implementation while retaining AC-07 as a required completion criterion.

- `BT-006-AC-01`: Money carries an explicit supported ISO 4217 code and signed 64-bit minor units; zero and negatives round-trip unchanged. Positive-expense validation is a later application rule. No `Double`/`Float` public input or arithmetic.
- `BT-006-AC-02`: Add/subtract use checked arithmetic; `Long.MAX_VALUE + 1`, `Long.MIN_VALUE - 1`, and negating `Long.MIN_VALUE` if negate exists fail rather than wrap. Different currency codes fail before arithmetic. Equal codes retain that code.
- `BT-006-AC-03`: A pure numeric input parser takes explicit `Locale` and currency. en-US `1,234.56 USD` and de-DE `1.234,56 EUR` produce exactly 123456 minor units; zero/negative inputs preserve sign; `1.234` in en-US/USD rejects excess nonzero precision, JPY `1.5` rejects fractional units, and values beyond `Long` reject. The whole input must be consumed, including rejection of trailing junk and malformed separators. No implicit locale currency, currency symbol requirement, or rounding. Numeric strings only; symbol/whitespace UX remains for the entry specification. `1.230` may be accepted for USD because no information is lost.
- `BT-006-AC-04`: A date/explicit zone converts to UTC `[start,end)`; exactly start is included and exactly end excluded. Tests exercise midnight and month/year turnover, 2024-02-29, and New York 2024-03-10 spring (23-hour day) / 2024-11-03 fall (25-hour day). Each endpoint comes from its own local date via `LocalDate.atStartOfDay(zone).toInstant()`, never by adding 24 hours to a start instant.
- `BT-006-AC-05`: For explicitly requested day/week/month/year units, the prior calendar unit is immediately adjacent and uses the same supplied zone and week-start input: March 2024 follows February 2024 (29 days), January 2025 follows December 2024, and a Monday-start week across a year boundary follows the preceding Monday-start week. Tests compare interval adjacency, not equal elapsed hours or month lengths. These are calendar primitives; this task does not select a dashboard default prior period or implement custom-period comparison while `SPEC-003` is Draft.
- `BT-006-AC-06`: The caller supplies a fixed `Clock`, an ID generator, `Locale`, and `ZoneId` or provider. Tests prove repeated calls deterministic and a zone change alters only explicitly re-evaluated boundaries. Domain operations under test use no hidden `Clock.systemDefaultZone`, `ZoneId.systemDefault`, `Locale.getDefault`, or random UUID.
- `BT-006-AC-07`: The existing app architecture test continues to pass: core remains free of Android/Compose/Room/DataStore and no app networking/telemetry is added. No database or UI changes.

## Small public shape

Use `Money(minorUnits: Long, currency: CurrencyCode)` with checked `plus`/`minus`, adding negate only if useful. `CurrencyCode` validates through `java.util.Currency.getInstance`, rejects pseudo currencies whose `defaultFractionDigits == -1` for parsing, and exposes the ISO code and fraction digits. Money construction stays independent from text parsing.

Place `LocalizedMoneyParser.parse(text, currencyCode, locale)` in a pure JVM input-boundary package under `:core:domain`. Use JDK `DecimalFormat` configured for `BigDecimal`, verify full consumption and grouping grammar, then use `setScale(fractionDigits, UNNECESSARY)` and `longValueExact()`. Explicit locale and currency parameters prevent D-05 defaults from being inferred. This separates locale syntax from the Money value type and permits a later presentation adapter to move or call it without changing arithmetic.

Expose immutable `UtcInterval(start: Instant, endExclusive: Instant)` with containment, plus a small `CalendarPeriods` factory with day/week/month/year operations taking explicit local date or year/month/year values, `ZoneId`, and `DayOfWeek` for weeks. Retain enough calendar metadata or explicit predecessor arguments for previous month/year to be calculated as a calendar unit rather than subtracting a duration. Prefer standard `Clock` and an injected `fun interface UuidGenerator` or function parameter. Avoid wrappers for `Locale`/`ZoneId` unless a concrete operation requires a provider. Add no DI framework or external library.

## Dependencies, ownership, and sequence

BT-005 is done. No external dependency is needed beyond existing catalog `libs.junit` as test wiring in `core/domain/build.gradle.kts`; JDK 17 `java.time`, `java.math`, `java.text`, and `java.util.Currency` suffice.

- Coordinator owns `docs/agent-tasks/BT-006.md` and `workflow/evidence/BT-006-plan.md`, `.md`, and `.json`.
- Tester owns `core/domain/src/test/**` and the test-only JUnit dependency line in `core/domain/build.gradle.kts`.
- Builder owns `core/domain/src/main/**`.

Sequence:

1. Coordinator records this plan, IDs, and ownership; obtains an independent elevated security preflight; then runs `python3 workflow/gate.py check-start BT-006`.
2. Tester follows `skills/spec-to-test/SKILL.md`, adds only the existing-catalog JUnit test wiring, writes contract tests for AC-01 through AC-06, and runs `:core:domain:test`. Record a genuine compilation failure from the absent production API or a focused behavioral failure, with exact output and no unrelated environment failure. Run `check-build` only after recording the genuine red.
3. Builder reads the persisted plan and red evidence, runs `check-build`, and implements the smallest production API. Tester reruns focused tests, then packet gates and existing architecture tests. Reviewer independently checks correctness, security, privacy, and test quality. Coordinator runs `check-review`, resolves findings, then runs `check-done` and transitions the task to `done`.

## Risk and recovery

Risk is **elevated** under `docs/security-gates.md`: financial arithmetic, precision, and time boundaries can silently misstate totals. Preflight must check overflow, currency validation, exact parsing, DST behavior, absence of sensitive logs, and scope. No destructive action is planned. Changes are additive and limited to the owned task, domain, test, and evidence files. On rejection, restore only BT-006 edits from the captured inventory/content, preserving unrelated workspace changes. Do not delete preexisting data. The `.git` directory has no metadata, so recovery uses the pre-task inventory and evidence rather than Git reset.

## Verification commands and environment

From the repository root with a Linux JDK 17 and SDK when available:

- Red/focused: `./gradlew :core:domain:test --no-daemon`
- Packet: `./gradlew testDebugUnitTest spotlessCheck lint --no-daemon`
- Architecture boundary: `./gradlew :app:testDebugUnitTest --no-daemon`

The current WSL environment previously had no `java`/`javac`. Windows PowerShell is available, and BT-005 passed with Windows JDK 17/SDK 35 from a Windows-local copy. Use a fresh synchronized Windows-local task copy, configure JDK 17/SDK as documented in `docs/build-setup.md`, run `gradlew.bat :core:domain:test --no-daemon`, then `gradlew.bat testDebugUnitTest spotlessCheck lint --no-daemon`, and record the exact copy path, JDK/Gradle versions, and results. Do not run Windows Gradle from the WSL UNC path. No emulator test is required for these pure JVM primitives. Record unavailable checks as unrun.

## Official research accessed 2026-09-29 UTC

- Java 17 `Currency`: ISO code validation and fraction digits, including `-1` for pseudo currencies: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Currency.html
- Checked `Math` arithmetic signals overflow: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html
- `BigDecimal.setScale(..., UNNECESSARY)` rejects rounding and `longValueExact` rejects fraction/overflow: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html
- `DecimalFormat` can return `BigDecimal`, while parsing can stop before the end and therefore requires a `ParsePosition` full-consumption check: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/DecimalFormat.html
- `LocalDate.atStartOfDay(ZoneId)` defines gap/overlap behavior and yields the earliest valid zoned time: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/LocalDate.html
- `Clock.fixed` provides deterministic time: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Clock.html
- `YearMonth` handles actual month lengths and leap years: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/YearMonth.html

## Open product questions and limits

D-02 (travel/reporting timezone), D-05 (first-run currency and currency change), dashboard custom previous-equivalent period, and first-release input UX remain owner decisions. All BT-006 APIs receive these choices explicitly, with no stored or global defaults. `SPEC-003` acceptance cannot be claimed complete by these primitive tests. If independent review concludes even standard prior calendar units need owner approval, retain generic date-to-interval primitives and test adjacency only for explicitly supplied local date bounds; defer public `priorEquivalent` naming. Android runtime locale/TZDB differences remain for later integration/device tests; JVM tests establish the pure contract only.
