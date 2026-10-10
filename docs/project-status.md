# BubbleSpend completion status

Updated 2026-10-10. These statuses reflect recorded verification. Setup/settings and the current expense journeys were verified in the working tree on this date; earlier results remain historical. Unfinished features are not covered by those passing results.

| Feature | Completion | Details |
| --- | --- | --- |
| Android foundation | Complete | Kotlin/Compose app, module boundaries, pinned build dependencies, and Android CI. |
| Money, dates, and storage | Complete | Integer minor-unit money, local-calendar periods, expense/category/payment contracts, Room persistence, durable reopen, and tombstones. |
| Expense management | Complete | Currency confirmation before saving; create, edit, duplicate, delete, undo; category and payment management. Includes a basic expense list, not the full reporting/history feature. |
| First-run setup and settings | Complete | Atomic explicit completion, fixed base currency, persistent unfinished choices, browsing/draft return, storage Retry, and editable week start. All seven weekdays feed the persisted weekly-period contract. Physical-device and display verification passed; see [setup/settings proof](proofs/setup-settings.md). |
| Dashboard, history, analytics, and insights | Not complete | Dashboard and history requirements approved; analytics requirements remain draft. Chart observations use synthetic spike data, not a production reporting pipeline. |
| Floating shortcut | Partial platform proof; not complete | Physical-device permission denial/grant, overlay creation, collapsed presentation, and neutral lock-screen presentation observed. Production overlay, interactions, restart, revocation, and lifecycle checks remain. |
| Backup and restore | Not complete | Draft requirements; format, validation, preview, atomic replacement, and recovery need final decisions, implementation, and verification. |
| Release quality | Not complete | Cross-feature accessibility/display, privacy, performance, migration/restore, and release checks remain. |

## Recorded verification

- Money/time and application repository tests passed during their completed work; Room contract and reopen checks were recorded passing.
- On 2026-10-06, expense-management verification passed formatting, lint, app unit tests, debug APK assembly, and `:core:application:test`.
- On 2026-10-06, the full connected expense-management suite passed **29/29**, with no failures, errors, or skipped tests, on a physical Pixel 7 (`panther`), Android 17 / API 37.
- On 2026-10-10, setup/settings verification passed formatting, lint, debug APK assembly, **42/42 local tests**, and **44/44 connected tests** on the physical Pixel 7, Android 17 / API 37, with no failures, errors, or skips. The focused setup suite also passed **9/9** with large/bold text, high contrast, and reduced motion; screenshots were visually inspected and original device settings restored. [Acceptance mapping and evidence](proofs/setup-settings.md).
- Overlay and visualization observations are partial. See [overlay proof](proofs/android-overlay.md) and [visualization proof](proofs/analytics-visuals.md) for actual scenarios and limits.
- No passing production-reporting, production-overlay, backup/restore, or release result is recorded here. Device verification uses a connected physical Android device.

Current verification commands and host requirements are in [build setup](build-setup.md). App and domain tests and Android CI remain part of the project.

## Requirements and remaining decisions

Approved specifications: expense entry (SPEC-001), category/payment management (SPEC-002), dashboard (SPEC-003), history (SPEC-006), and onboarding/settings (SPEC-009). Analytics (SPEC-004), floating shortcut (SPEC-005), backup/restore (SPEC-007), and accessibility (SPEC-008) remain draft. Approval describes agreed behavior, not completed implementation.

Resolve remaining choices in [product decisions](decisions.md) and the relevant [specifications](specs/) before implementing their visible behavior. D-05 setup choices are approved. D-08 removes screen-reader support from MVP scope; visible text/list equivalents, touch actions, large/bold text, contrast, and reduced motion remain required.

The next feature is reporting, starting with the approved dashboard and history requirements. Floating entry and backup/restore follow; release checks cover all shipped features. There is no separate task queue or agent handoff process.
