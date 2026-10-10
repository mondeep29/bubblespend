# SPEC-006: Transaction history and search

**Status:** Approved

## User outcome

Find, inspect, and retrace stored expenses reliably, whether opening History
directly or arriving from a dashboard/analytics figure.

## In scope

History list and detail view; period/category/payment filters; free-text search;
deterministic sorting; dashboard/analytics drill-down; loading, empty, error,
retry, and archived-label states; and responsiveness at 50,000 expenses.

## Out of scope

Editing/deleting behavior, export/restore, account sync, remote search,
analytics calculations, and overlay entry.

## User journeys

1. History opens with active, non-deleted expenses in newest-first order.
2. The user applies period, category, payment, search, and sort controls in any
   combination; results update without blocking the UI.
3. The user opens a row to inspect exact fields. Back returns to the same query,
   filters, sort, and source context.
4. Dashboard or analytics drill-down opens History with the source period and
   category/payment filters applied, an empty search, and default sort. A
   recent-expense source may also include the selected expense's stable UUID as
   an initial constraint. The user can then change or clear those controls.
5. Empty, loading, storage-error, and invalid-range states are clearly shown and
   provide an applicable recovery action. A missing historical label uses its
   visible archived-label fallback; label absence alone needs no recovery.

## Functional requirements

- Normal history excludes expenses with non-null `deletedAt`. Period filters
  use the same configured reporting timezone, week-start value, and
  inclusive-start/exclusive-end interval as Dashboard.
- Category and payment filters compare stable IDs, not display labels. Renamed
  or archived records remain readable for historical expenses.
- Search trims leading/trailing whitespace and replaces each run of whitespace
  with one ASCII space, applies Unicode NFKD normalization, removes all Unicode
  combining marks, and compares case-insensitively using locale-independent
  Unicode case conversion. Searchable fields are merchant, note, current
  category label, and current payment label; matching is substring matching
  anywhere. Empty/whitespace-only search means no restriction.
- Amounts, UUIDs, sync status, and raw database fields are not searchable.
- Default sort is occurred time descending, then creation time descending,
  then UUID ascending. Merchant sort keys use the same whitespace trim/collapse,
  Unicode NFKD normalization, combining-mark removal, and locale-independent
  case conversion as search. Compare normalized keys lexicographically by
  Unicode code point. Additional sorts and exact tie rules are:
  oldest first (occurred ascending, creation ascending, UUID ascending);
  amount high-to-low or low-to-high (minor units in the chosen direction,
  occurred descending, creation descending, UUID ascending); merchant A-Z or
  Z-A (normalized merchant in the chosen direction, occurred descending,
  creation descending, UUID ascending). Blank merchant sorts after named
  merchants in either direction; equal normalized names use the fixed ties.
- Search/filter/sort do not block the main UI and are functionally correct for
  50,000 records. The repeatable manual benchmark procedure is
  `SPEC-006-BP-01` below; it records a named physical device/API, deterministic
  dataset seed and generator/version, each operation, and its observed duration.
  There is no fixed latency threshold.
- Query, filters, sort, and source context persist across detail navigation and
  back for the current navigation session. A new source drill-down replaces any
  earlier History query with its period/category/payment filters, empty search,
  and default sort; a recent-expense source additionally constrains the initial
  results to its stable UUID. Clearing that UUID constraint removes only it and
  preserves the current filters, search, and sort. Later changes within History
  do not change the source. Fresh app start uses default sort, unset filters,
  and empty search.

## Business rules

- A newer query selection cannot be replaced by a slower older result.
- Missing labels use visible fallback text (`Archived category` or `Archived payment method`). IDs and expense values remain unchanged. A missing label alone does not require a recovery action.
- Amounts display exact stored minor units and currency; display rounding never
  changes filtering or sorting.
- Search displays original text while matching normalized text. Query and result
  contents are never logged.
- The normalization operation trims and collapses whitespace, applies Unicode
  NFKD, removes all combining marks, then applies locale-independent Unicode
  case conversion. Search matches substrings; merchant ordering compares
  normalized keys lexicographically by Unicode code point.
- Drill-down source filters, including a selected recent expense UUID, are
  visible and removable; removing the UUID constraint broadens only History and
  preserves its other current controls. Removing other source filters broadens
  only History and does not silently change the source dashboard/analytics
  state.

## State model

`Uninitialized -> Loading -> Results | Empty | Error`; detail is a child state
of Results and returns to the exact prior list context. Changing any query
control enters Loading while retaining recoverable input.

## Error and empty states

- Loading shows that History is updating.
- No records explains that no expenses have been recorded and offers add
  expense. No matches offers clear search/filters while retaining query input.
- Invalid/over-limit custom period identifies the correction and keeps the last
  valid query.
- Storage failure preserves query/filter/sort and offers retry; partial data is
  not presented as complete without labeling.
- Missing historical labels show the approved visible fallback; this condition alone does not require a retry or other recovery action.

## Accessibility requirements

Search has a visible label and clear touch action. Period, category, payment,
sort, result count, rows, detail fields, source context, empty states, and retry
actions have meaningful visible labels. Rows expose exact amount, local
date/time, category, payment, merchant, and note as text. Support large/bold
text, high contrast, and reduced motion. Per D-08, this specification makes no
TalkBack or other screen-reader acceptance claim; acceptance here is limited to
visible information and touch actions.

## Privacy and security

History stays on device. Never log search text, amounts, merchants, notes,
labels, IDs, or backup contents. No account, network, or remote telemetry
dependency is introduced.

## Analytics/telemetry events

No remote events. Local UI state may record a search/filter/sort action without
recording its values or transaction content.

## Acceptance criteria

- `SPEC-006-AC-01`: Given active expenses and period/category/payment filters, when History loads, then rows and count use the same interval and stable-ID predicates as Dashboard, exclude tombstones, and reconcile with the source total.
- `SPEC-006-AC-02`: Given a dashboard or analytics total drill-down, when History opens, then it applies source period/category/payment filters, empty search, and default sort so initial IDs match the total; given a category drill-down, when History opens, then it applies the selected group's stable category ID and applicable period/payment filters to return exactly that group; given a recent-expense drill-down, when History opens, then those source filters plus a stable UUID constraint return only the selected expense; clearing the UUID constraint removes only it and preserves current filters, search, and sort; History context persists across detail/back for the navigation session and does not change the source.
- `SPEC-006-AC-03`: Given equal primary sort keys, when results are displayed under any supported sort, then fixed secondary keys and final UUID ascending tie-break produce the same order across reloads and pages.
- `SPEC-006-AC-04`: Given case, accents, whitespace, and text in searchable fields, when search runs, then trimming/whitespace collapse, Unicode NFKD normalization, combining-mark removal, and locale-independent case-insensitive substring matching return exactly matching active expenses and exclude amounts, UUIDs, sync status, and raw fields.
- `SPEC-006-AC-05`: Given loading, no records, no matches, invalid range, or storage failure, when state is rendered, then it has a visible explanation and applicable recovery action, preserves input, and never presents partial or failed data as a complete success.
- `SPEC-006-AC-06`: Given the deterministic seeded 50,000-expense dataset, when the named physical-device procedure `SPEC-006-BP-01` runs its filter/reconciliation, normalized-search, sort, and detail open/back operations, then the dataset seed/generator, Android API level, each operation, its observed duration, and whether the main UI remains interactive are recorded; no latency threshold is required.
- `SPEC-006-AC-07`: Given an expense whose category or payment label is unavailable, when its History row or detail is shown, then the corresponding visible archived-label fallback appears while its stable ID and expense values remain unchanged; the missing label alone does not require recovery.

## Test mapping

| Criterion | Exact verification to add or run | Command |
| --- | --- | --- |
| AC-01 | `HistoryFilterAcceptanceTest.historyAndDashboardUseTheSamePeriodAndStableIdPredicates` | `./gradlew :core:application:test :app:testDebugUnitTest` |
| AC-02 | `DashboardHistoryJourneyTest.drillDownPreservesFiltersAndReturnsToDashboardContext`; new case `DashboardHistoryJourneyTest.recentExpenseDrillDownConstrainsHistoryToStableExpenseIdAndClearRemovesOnlyThatConstraint`; `HistoryDetailTest.backRestoresQuerySortAndContext` | `./gradlew :app:connectedDebugAndroidTest` |
| AC-03 | `HistorySortAcceptanceTest.allSortsUseDeterministicSecondaryKeysAndUuidTieBreak` | `./gradlew :core:application:test` |
| AC-04 | `HistorySearchAcceptanceTest.searchesNormalizedTextFieldsAndExcludesAmountsAndIds` | `./gradlew :core:application:test` |
| AC-05 | `HistoryStateTest.loadingEmptyErrorInvalidRangeAndArchivedLabelsAreAccessible`; manual visible-state, touch-recovery, large/bold-text, contrast, and reduced-motion review | `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest` |
| AC-06 | `SPEC-006-BP-01`: repeatable named-device benchmark procedure below | Run `./gradlew :core:application:test :app:connectedDebugAndroidTest`, then execute the procedure on the named physical Android device; no fixed latency threshold |
| AC-07 | `HistoryStateTest.loadingEmptyErrorInvalidRangeAndArchivedLabelsAreAccessible`; manual missing-label visible fallback review | `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest` |

## Seeded reference transactions

Use the SPEC-003 T0-T6 fixtures, with these additional fields:

| ID | Merchant | Note | Payment |
| --- | --- | --- | --- |
| T0 | Café | breakfast | Cash |
| T1 | Market | morning  coffee | Card |
| T2 | Café | brunch | Cash |
| T3 | Bus | commute | Card |
| T4 | Rent | March rent | Card |
| T5 | Clinic | checkup | Cash |
| T6 | Café | excluded | Cash |

With the local week Mar 4 through Mar 11, History returns T1-T4 in default
order T4, T3, T2, T1. A query for `CAFE` matches T2's `Café` after
normalization; `morning coffee` matches T1 after whitespace collapse. T5 is
excluded at the exclusive end, and T6 is excluded by its tombstone.

Within the same week, `food` matches T1 and T2 through the current category
label, while `CARD` matches T1, T3, and T4 through the current payment label.
These examples use the same whitespace, Unicode, and case-independent
normalization.

## Repeatable benchmark procedure

`SPEC-006-BP-01`:

1. Generate exactly 50,000 expenses using a deterministic generator. Record the
   generator/version and seed so the dataset can be regenerated; include a
   `Café` merchant, `Food` category, and `Card` payment label so the fixed
   search queries below have known matches. Use the same generated dataset for
   every operation in a run.
2. Record the physical Android device model and Android API level. Install and
   launch the build under evaluation with the generated dataset in the local
   store.
3. In order, execute and record the observed elapsed duration for: (a) a
   period/category/payment filter query, recording its predicates and checking
   the returned count and total against independently computed expected
   results; (b) normalized searches `CAFE`, `food`, and `CARD`; (c) each
   supported sort (newest, oldest, amount descending, amount ascending,
   merchant A-Z, merchant Z-A); and (d) opening a result row and returning to
   the retained list context.
4. Measure each query from submitting it until its complete result is visible;
   measure detail navigation from tap to rendered detail, then back to restored
   list. During each operation, observe whether the main UI remains
   interactive, and record that observation with the operation name and
   observed duration alongside the device/API and dataset seed/generator/
   version. Report observations as measured; this procedure sets no latency
   threshold.

## Open questions

None for SPEC-006. Reporting timezone, week-start default, search fields and
normalization, supported sorts and tie-breaks, navigation-session persistence,
and the named-device 50,000-expense benchmark contract were approved by the
product owner on 2026-10-07. The benchmark requires recording observed results
without a fixed latency threshold.
