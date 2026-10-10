# SPEC-008: Accessibility and localization readiness

**Status:** Draft. English is the first-release locale. D-08 removes screen-reader requirements from the MVP; the audit device/API set and maximum text scale still require owner approval. This specification supplements feature behavior; it does not approve open rules in other drafts.

## User outcome

Complete critical BubbleSpend journeys with touch and keyboard actions, large and bold text, high contrast, and reduced motion, including empty, invalid, and recoverable failure states. Understand every spending visualization from exact visible text and reach its matching transactions without interpreting color, size, or animation.

## In scope

First-run setup; expense entry and category/payment management; History; Dashboard; Analytics; overlay settings and in-app fallback; export/restore; visible status and errors; keyboard and touch operation; text scaling, contrast, motion, and localization readiness.

## Out of scope

Approval of onboarding defaults, reporting timezone, overlay lifecycle/lock-screen policy, analytics thresholds, backup format, or restore record scope. Screen-reader behavior is outside MVP scope under D-08. Owning feature specifications and decisions define the other behaviors.

## User journeys

1. Complete first-run currency confirmation and expense entry with labeled fields, validation, save, delete/undo, and recoverable storage failure. Manage categories and payment methods, including empty active lists and archive conflicts.
2. Search, filter, sort, and inspect History; navigate from Dashboard or Analytics to the exact matching transactions and back with context intact.
3. Read Dashboard totals, comparisons, and category bubbles and Analytics trends, bars, and insights as exact text/list information, then open the same transaction drill-down available from the visual item.
4. Enable or decline the floating shortcut through an understandable opt-in flow; recover from permission denial/revocation using in-app entry. If the overlay is available, its controls are operable without relying on drag alone.
5. Export, validate, preview, confirm/cancel, and recover from restore failure with visible explanations of the effect on data under the approved SPEC-007 policy.

## Functional requirements

- Every actionable control has a visible label or adjacent text explaining its action and state, and supports touch/keyboard activation. Logical keyboard traversal follows task order and preserves paths to Back/Cancel and recovery. Touch targets meet the Android platform minimum and remain separately operable.
- On validation failure, show the error beside the affected field or in a visible summary that identifies it. Preserve input. Loading, success, empty, failure, retry, permission denial/revocation, delete/undo, and restore outcomes show visible status without false success or repeated messages from recomposition.
- Every visually shown amount, count, percentage, period, comparison baseline, category, bucket, and insight claim has an exact text/list equivalent. It uses the same filters and underlying values as the graphic; display rounding never becomes source data. Each actionable chart/bubble/list value opens matching History transactions. Empty and insufficient-data visuals have textual explanations and relevant actions.
- No control or data meaning depends solely on color, icon, spatial position, sound, haptic feedback, or motion. Text labels identify categories and methods even if decorative assets are unavailable. Error, warning, selection, and success are distinguishable in high contrast.
- At the approved maximum font/display scale with bold text, critical labels, values, confirmations, navigation, and recovery actions remain readable and operable. Layout may reflow or scroll; it must not clip required actions, overlap controls, or trap focus. Landscape and display-size changes retain the active task where the platform permits.
- Respect the system reduced-motion setting. Essential state changes remain understandable without animation. The same exact information and actions are available before/after optional animation; no timed motion is required to finish a critical task.
- Store user-facing copy in Android string resources. English is the first-release locale. Format money, date/time, and plural counts for English without changing stored minor units, UTC instants, or filtering boundaries. Keep resources ready for later localization; check text expansion and right-to-left layout when a later approved locale requires them.

## Business rules

- Feature specifications own data and error semantics. This specification owns the accessible route through their input, visual, and recovery states.
- A text/list equivalent is a first-class path to exact data and matching transactions, not a rounded summary or differently filtered list.
- Do not show private expense data in an overlay on the lock screen. D-06 and platform proof define exact presentation; denial/revocation never blocks in-app entry.
- Audit evidence requires observation on the approved device/API set and maximum text scale. Automated checks alone do not prove clipping, contrast, or reduced-motion behavior.

## State model

Each journey can expose `Loading -> Content | Empty | Validation error | Recoverable failure -> Retry/Correction -> Content`. Confirmations add `Review -> Confirm | Cancel`; permission journeys add `Disabled -> Education -> Granted | Denied | Revoked`. Visible status matches persisted/displayed state. Keyboard focus returns to a meaningful origin after a dialog closes and reaches the new heading/error after navigation or failure.

## Error and empty states

- Empty setup/management, History, Dashboard, Analytics, and restore previews explain what is absent and expose the relevant create, change-filter, choose-file, or Back action.
- Invalid fields, ranges, filters, permissions, and backup candidates identify a correction without losing recoverable input or previous valid context.
- Storage, provider, or permission failure explains the outcome and offers retry or in-app fallback where applicable. A failed operation is never shown as success.
- Loading shows progress without presenting stale values as current. Interrupted tasks return to a known state and recovery action.

## Accessibility requirements

This matrix is the required audit inventory. Each row requires success, empty, validation, and recovery checks where that state can arise. Record `N/A` with a concrete reason only for an impossible state. Owner-approved device/API, maximum font scale, English locale, and high-contrast/reduced-motion setup must be recorded before a row passes.

| Journey | Success/content | Empty | Validation/correction | Failure/recovery | Specific equivalent or fallback |
| --- | --- | --- | --- | --- | --- |
| First-run setup | Currency suggestion, explicit confirmation, week-start/settings controls where specified | No choice/default | Missing or invalid choice | Interrupted setup/retry | Visible choice and confirmation in English |
| Expense entry | Form, exact amount, Save, edit, duplicate, delete/undo | No active category/method | Amount, future time, text limits, inactive selection | Failed/interrupted save, stale edit/delete, discard dialog | Visible field errors; no private lock-screen content |
| Category/payment management | Create, rename, archive, default status | No active records | Missing/duplicate/long name, unsupported token | Storage failure or stale version | Text labels independent of icon/color |
| History | Search, filters, sort, row/detail, drill-down/back | No records and no matches | Invalid period/filter | Storage failure/retry, archived label | Exact row values and source context |
| Dashboard | Period/filters, total/comparison, bubble/list, drill-down | Zero-spend period | Invalid custom interval | Query failure/retry | Exact list value and matching transaction IDs |
| Analytics | Trend/bars/insight/list, drill-down | No data/insufficient insight data | Invalid selection/period if offered | Query failure/retry | Exact buckets, baselines, percentages, matching IDs |
| Overlay settings/entry | Education, enable/disable, shortcut action, app entry | Disabled/no permission | Permission refusal or unavailable selection | Revocation/service/save failure; in-app fallback | Touch path without drag; lock-screen privacy |
| Export/restore | File selection, progress, validated preview, consequence, confirm/cancel | Empty supported-record backup | Corrupt/unsupported/partial candidate | Provider/apply failure, rollback, interruption | Visible warning before Confirm; no private content in status |

For every row, audit touch and keyboard actions, standard and approved maximum font scale with bold text, high contrast, and reduced motion. Record device model/API, locale, display size/orientation, font scale, mode, journey/state, observed result, and issue reference. Repeat failed cells after fixes. Document unavailable feature states instead of silently omitting them.

## Privacy and security

Labels, visible status, screenshots captured for audit, and logs respect visible-UI privacy rules. Never log amounts, merchants, notes, backup contents, credentials, or document URIs. Audit artifacts use synthetic fixtures. No account, network, or analytics SDK is added.

## Analytics/telemetry events

No remote events. Local UI state may represent phase/outcome without personal content.

## Acceptance criteria

- `SPEC-008-AC-01` Given approved audit devices, when first-run setup, expense entry, and category/payment management are completed through success, empty, validation, and recovery states, then visible labels, keyboard traversal, errors, confirmations, and correction paths are understandable and operable by touch and keyboard without false success.
- `SPEC-008-AC-02` Given any Dashboard bubble, trend bucket, category bar, insight card, total, or comparison value, when its accessible equivalent is read and activated, then exact amount/count/period/baseline text agrees with displayed data and opens matching transactions under the same filters; empty/insufficient-data states remain understandable.
- `SPEC-008-AC-03` **Blocked: maximum text scale/device set.** Given approved maximum font/display scale with bold text, when every matrix journey is completed in portrait and applicable landscape, then required copy, controls, confirmations, and recovery actions reflow or scroll without clipping, overlap, inaccessible focus, or lost input.
- `SPEC-008-AC-04` Given system reduced motion, when each journey changes state or an animated visualization/overlay action occurs, then the same content and controls remain available without essential motion, and completion is not timed to animation.
- `SPEC-008-AC-05` **Blocked: audit device/API set.** Given high contrast, touch, and keyboard input on each approved device/API, when success, empty, validation, and recovery cells are audited, then targets meet Android minimums, keyboard traversal is logical, state is not color-only, and every critical action can be reached and activated.
- `SPEC-008-AC-06` Given the first-release English locale, when money, date/time, count, error, confirmation, and navigation copy appears across the matrix, then strings are resource-backed, formatting follows the locale while stored values remain exact, and text layout does not hide meaning or actions.
- `SPEC-008-AC-07` Given History, overlay settings, and export/restore, when the user encounters an empty, validation, permission-denied/revoked, provider, or storage-failure state, then the outcome and recovery action are visible and reachable by touch and keyboard, and in-app entry remains usable after overlay denial/revocation.

## Test mapping

These are future checks and planned names; no device observation is claimed.

| Criterion | Future automated or device check | Specification check |
| --- | --- | --- |
| AC-01 | `CriticalJourneyAccessibilityTest.setupExpenseAndManagementStatesHaveLabelsAndRecovery`; manual touch/keyboard matrix rows 1–3 | Trace success, empty, invalid, and failure cells to first-run and SPEC-001/002. |
| AC-02 | `VisualizationAccessibilityTest.exactListValuesAndDrillDownMatchChartQueries`; manual visible-list Dashboard/Analytics check | Compare exact values and matching IDs for every visual type, plus empty/insufficient data. |
| AC-03 | `LargeTextJourneyTest.criticalActionsRemainReachableAtApprovedScale`; manual layout audit | Inspect all rows at approved scale and applicable orientations; record clipping/focus. |
| AC-04 | `ReducedMotionJourneyTest.stateChangesRemainOperableWithoutAnimation`; manual reduced-motion audit | Inspect overlay, chart/bubble, confirmation, and retry paths without animation. |
| AC-05 | `TouchAndKeyboardTest.targetsLabelsAndFocusAreReachable`; manual contrast/keyboard/touch audit | Check targets, high contrast, color-independent meaning, traversal, and each matrix state. |
| AC-06 | `LocalizationUiTest.releaseLocalesFormatMoneyDatesAndCounts`; manual locale/layout audit | Check English resources, number/date/plurals, and layout at approved text scale. |
| AC-07 | `RecoveryAccessibilityTest.historyOverlayRestoreFailuresOfferVisibleRecovery`; manual touch/keyboard rows 4, 7, and 8 | Trace denial/revocation, invalid backup, provider/rollback failure, and retry/fallback paths. |

Future commands: `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest`, plus recorded manual touch, keyboard, text-scale, contrast, reduced-motion, and locale audit on owner-approved devices. CI alone does not prove layout and visual readability.

## Open questions

1. Which device models/API levels, screen sizes/orientations, font and display scales are the release audit set? Define maximum text scale and how bold text, high contrast, and reduced motion are enabled/recorded.
2. First-run setup remains open under D-05; reporting timezone under D-02; chart/insight detail in SPEC-003/004; restore record scope and empty-backup/retry behavior remain open in SPEC-007. D-01 and D-06 already define overlay restart and lock-screen behavior. Their visible presentation follows approved owning behavior.
