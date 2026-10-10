# SPEC-003: Dashboard

**Status:** Approved

## User outcome

Understand spending for a selected week, month, year, or custom local-calendar
period, compare it with the immediately preceding equivalent period, and open
the exact transactions behind every total or category group.

## In scope

Period selection for week, month, year, and a custom interval; total amount and
count; previous-period comparison; category groups; recent expenses; optional
category/payment filters; loading, empty, error, and retry states; and
navigation to matching history with context preserved.

## Out of scope

Analytics trends and insight thresholds, budgets, recurring expenses, currency
conversion, remote telemetry, initial setup defaults, overlay, and
export/restore.

## User journeys

1. Dashboard opens on the current month. The selected period is resolved using
   the device timezone at query time (D-02). Week start is configurable; Monday
   is the initial week-start default.
2. The user changes period, custom dates, category, or payment method and all
   figures recalculate from the same active-expense predicate used by History.
3. Activating the dashboard total opens History with the exact matching result
   set. A category value opens History with the selected group's stable category
   ID and the applicable period/payment filters. Tapping a recent row opens
   History constrained to that expense's stable UUID as well as the applicable
   period and category/payment filters. Returning restores dashboard context
   for the current navigation session.
4. Loading, empty, invalid-range, and storage-error states explain the state
   and provide a visible, touch-operable recovery action.

## Functional requirements

- Store occurred instants in UTC. Resolve local boundaries independently into
  `[startInclusive, endExclusive)` UTC intervals.
- Week uses the configured week-start day. Month and year use local calendar
  boundaries. A custom period is an inclusive local start date and exclusive
  local end date, spanning 1 through 366 local calendar days. Its previous
  comparison interval is the same number of local calendar days immediately
  before the selected start.
- Totals, counts, groups, recent rows, and drill-down IDs exclude tombstones
  and use the same period/category/payment predicates as History. Sum exact
  integer minor units in the configured base currency.
- The previous Week, Month, or Year is the immediately preceding calendar unit.
  The custom comparison is the same number of local calendar days immediately
  before the selected start, with the same non-time filters.
- Category groups are ordered by amount descending, then category name
  normalized by trimming and collapsing whitespace, Unicode NFKD, removal of
  combining marks, and locale-independent Unicode case conversion. Compare the
  normalized names lexicographically by Unicode code point, then stable
  category ID ascending. Bubble area is proportional to the exact category
  amount; the visible category list
  is authoritative and contains exact amount, count, and touch drill-down
  action.
- The recent list contains the five newest matching active expenses, using the
  History default tie-break order.
- A category with a zero total has no bubble. It remains in the exact category
  list with its amount, count, and matching drill-down action.
- Period and category/payment filters persist across dashboard/history
  drill-down and back for the current navigation session. A total or category
  drill-down starts History with the source filters, empty search, and default
  newest-first sort so the initial result IDs equal the selected figure. A
  recent-row drill-down uses those same source filters and additionally
  constrains the initial result to the selected expense UUID. Clearing that
  row constraint removes only the UUID constraint and leaves the current
  period/category/payment filters, search, and sort intact. A fresh app start
  uses current month, no category/payment filters, and empty history search;
  transient filters and search are not restored after restart.

## Business rules

- Reporting uses the device timezone at query time (D-02); no reporting
  timezone is stored on an expense. A timezone change therefore affects
  subsequent local-boundary queries. Monday is the initial week-start default;
  the user-configured week-start value applies to Week.
- Comparison percentage uses exact decimal arithmetic. If the previous amount
  is zero, show the absolute difference and count comparison plus “No
  percentage baseline”; never divide by zero or display infinity.
- A zero-result period is a valid empty state with zero total/count and no
  category bubbles. Archived category labels remain readable for historical
  expenses.
- No value is actionable only through color, size, position, or motion.

## State model

`Uninitialized -> Loading -> Content | Empty | Error`.

Changing a period or filter returns to Loading. A zero-result query becomes
Empty, a nonzero query Content, and a storage failure Error. Retry preserves
the selected context; a stale older result cannot replace a newer query.

## Error and empty states

- Invalid custom range identifies the correction and keeps the last valid
  dashboard visible.
- Loading is visibly identified without presenting stale values as current.
- Empty identifies the selected period/filters and offers period/filter change.
- Storage failure keeps context, offers retry, and does not show a false total.
- A missing archived label uses a visible `Archived category` fallback.

## Accessibility requirements

Provide visible labels for period and filter controls, exact total/count and
comparison text, and a visible category list. Every bubble's value is visible,
and its touch action opens the matching transactions. Loading, empty,
validation, error, retry, and drill-down context are visible and touch
operable. Support large and bold text, high contrast, and reduced motion
without relying on color or animation. Per D-08, this specification makes no
TalkBack or other screen-reader acceptance claim.

## Privacy and security

Amounts, merchants, notes, IDs, and backup contents stay on device. Do not log
query results or filter contents. No account, network, or remote telemetry
dependency is introduced.

## Analytics/telemetry events

No remote events. Local UI state may record control or drill-down use without
transaction content or amounts.

## Acceptance criteria

- `SPEC-003-AC-01`: Given active expenses and a selected standard or valid custom period, when Dashboard loads, then the displayed sum/count includes every matching non-tombstoned expense in `[start, end)`, uses exact minor units, and reconciles with matching History.
- `SPEC-003-AC-02`: Given a selected period, when comparison loads, then the previous interval is the immediately preceding equivalent calendar period with identical non-time filters; a custom period of 1–366 local calendar days uses the same number of local days immediately before its start, and a zero previous total has no divide-by-zero percentage.
- `SPEC-003-AC-03`: Given category totals, when the visible category list is reviewed or a bubble is tapped, then exact amount, count, label, and matching transaction IDs equal the grouped query; order is amount descending, then trimmed/collapsed whitespace, Unicode NFKD with combining marks removed, locale-independent Unicode case conversion, normalized-name Unicode code-point order, then stable category ID ascending; zero-total categories remain in the exact list and have no bubble; visual size/color is not required to understand the value.
- `SPEC-003-AC-04`: Given a dashboard total, when it is opened, then History starts with the current period/category/payment filters, empty search, and default sort, and its IDs equal the total's result set; given a category value, when it is opened, then History applies the current period/payment filters and the selected group's stable category ID and returns exactly that group; given a recent row, when it is tapped, then History starts with the applicable period/category/payment filters plus a stable UUID constraint matching only that expense; clearing the UUID constraint removes only it and preserves the current History filters, search, and sort; returning restores dashboard context.
- `SPEC-003-AC-05`: Given loading, zero-result, invalid custom range, or storage failure, when the state is rendered, then it is visibly identified, operable, recoverable, and never presents a failed or stale query as a successful current result.
- `SPEC-003-AC-06`: Given the device timezone at query time and the configured week-start value (Monday initially), when a boundary occurs at local midnight or a DST transition, then both boundaries are independently resolved to UTC and the result matches the same History filter.

## Test mapping

| Criterion | Exact verification to add or run | Command |
| --- | --- | --- |
| AC-01 | `DashboardPeriodAcceptanceTest.periodTotalsReconcileWithHistoryForWeekMonthYearAndCustom` | `./gradlew :core:application:test :app:testDebugUnitTest` |
| AC-02 | `DashboardPeriodAcceptanceTest.previousEquivalentPeriodUsesAdjacentCalendarRulesAndZeroBaseline`; `CalendarPeriodsAcceptanceTest.prior day week month and year are adjacent calendar intervals` | `./gradlew :core:domain:test :core:application:test` |
| AC-03 | New `DashboardPeriodAcceptanceTest.categoryGroupsUseNormalizedNameTiesThenStableId`; `DashboardAccessibilityTest.categoryListMatchesBubbleValuesAndOpensMatchingHistory` | `./gradlew :core:application:test :app:testDebugUnitTest :app:connectedDebugAndroidTest` |
| AC-04 | `DashboardHistoryJourneyTest.drillDownPreservesFiltersAndReturnsToDashboardContext`; new case `DashboardHistoryJourneyTest.recentExpenseDrillDownConstrainsHistoryToStableExpenseIdAndClearRemovesOnlyThatConstraint` | `./gradlew :app:connectedDebugAndroidTest` |
| AC-05 | `DashboardStateTest.loadingEmptyInvalidRangeAndStorageErrorAreRecoverable`; manual visible-state, large/bold-text, contrast, reduced-motion, and touch review | `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest` |
| AC-06 | `CalendarPeriodsAcceptanceTest.day intervals resolve both local boundaries across daylight saving changes`; `DashboardPeriodAcceptanceTest.timezoneBoundaryMatchesHistory` | `./gradlew :core:domain:test :core:application:test :app:connectedDebugAndroidTest` |

## Seeded reference transactions

Use USD, `America/New_York`, Monday week start, and these illustrative fixtures:

| ID | Occurred UTC | Local time | Category | Amount | State |
| --- | --- | --- | --- | ---: | --- |
| T0 | 2024-02-26T05:00:00Z | Feb 26 00:00 EST | Food | $5.00 | Active |
| T1 | 2024-03-04T05:00:00Z | Mar 4 00:00 EST | Food | $10.00 | Active |
| T2 | 2024-03-10T06:59:00Z | Mar 10 01:59 EST | Food | $20.00 | Active |
| T3 | 2024-03-10T07:00:00Z | Mar 10 03:00 EDT | Transport | $30.00 | Active |
| T4 | 2024-03-11T03:59:00Z | Mar 10 23:59 EDT | Housing | $40.00 | Active |
| T5 | 2024-03-11T04:00:00Z | Mar 11 00:00 EDT | Health | $50.00 | Active |
| T6 | 2024-03-10T06:59:00Z | Mar 10 01:59 EST | Food | $7.00 | Tombstoned |

The local week Mar 4 through Mar 11 includes T1-T4 for $100.00/count 4; its
previous week, Feb 26 through Mar 4, includes T0 for $5.00. T5 is excluded at
the exclusive end and T6 by its tombstone. The one-day custom local interval Mar 10 through Mar 11
includes T2-T4 for $90.00. Under the equal-local-day comparison, its
previous interval is Mar 9 through Mar 10 and includes none of these fixtures:
$0.00/count 0, with no percentage baseline. The Mar 10 local day is 23 hours,
proving both boundaries must be independently resolved.

For March 2024, the local month Mar 1 through Apr 1 includes T1-T5 for
$150.00/count 5; February 2024 includes T0 for $5.00/count 1. For calendar
year 2024, Jan 1 2024 through Jan 1 2025 includes T0-T5 for $155.00/count 6;
the immediately preceding calendar year contains none of these fixtures. In
the Mar 4 week, category groups are Housing $40.00/count 1 (T4), Food
$30.00/count 2 (T1/T2), and Transport $30.00/count 1 (T3), in that order.
History opened from the Food group returns T2 then T1 with the same week and
category filters. All results exclude tombstoned T6.

## Open questions

None for SPEC-003. D-02, D-05's initial week-start default, D-09 category-name
ordering, and the reporting, custom-period, comparison, navigation, recent-list,
and zero-total bubble contracts above were approved by the product owner on
2026-10-07. Implementation verification remains subject to the mapped tests
and device/API checks.
