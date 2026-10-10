# BubbleSpend product brief

## Purpose

BubbleSpend helps one person record expenses quickly and understand where money goes, fully offline. A floating Android shortcut is the fast entry path; the full app provides entry, history, dashboard, analytics, settings, and backup/restore.

## MVP outcomes

1. First-run setup selects one base currency, local defaults, and week start.
2. Create, edit, duplicate, soft-delete, and undo an expense. Manage categories and payment methods.
3. Enter an expense from the in-app action or, after explicit permission, an overlay above another app. Open the main app from the overlay.
4. View week, month, year, and custom totals; category bubbles; recent transactions; trends; explainable insights; and matching transactions behind every figure.
5. Search, filter, sort, export, validate, and restore locally stored data.
6. Use critical journeys with visible labels and touch actions, large/bold text, high contrast, and reduced motion. Screen-reader support is outside the MVP under approved D-08.

## MVP journey and state inventory

Specifications have mixed approval status. This inventory records product coverage and gaps; draft details remain unapproved. See [completion status](../project-status.md) for actual delivery and verification.

| Outcome | Journey and states | Edge and recovery coverage | Privacy and accessibility coverage |
| --- | --- | --- | --- |
| First-run setup | Confirm one base currency, local defaults, and week start, then enter the app. SPEC-009 is approved, implemented, and verified; see [setup/settings proof](../proofs/setup-settings.md). | D-05 and SPEC-009 define validation, interrupted setup, persistent choices, Retry, browsing before completion, and later week-start changes. | Settings stay on device. Visible labels and recovery controls must remain usable with large/bold text, contrast, and reduced motion. |
| Expense and category/payment management | Expense draft: editing → validation → saving → saved or error; delete → undo available → tombstoned. Categories and methods can be created, renamed, selected, or archived. (SPEC-001/002, Draft.) | SPEC-001 covers invalid fields, unavailable selections, storage failure, interrupted saves, and duplicate submissions. SPEC-002 still needs management error and fallback states. | Never log expense fields. Entry errors are announced and focus moves to the invalid field. Category and method management needs complete accessible criteria. |
| In-app and floating entry | In-app entry works without special access. Overlay path: disabled → collapsed → expanded → saving → success/error, with dismiss and restore states. SPEC-001 is approved; SPEC-005 remains draft. | Denial or revocation leaves in-app entry available; failed saves remain recoverable. D-01/D-06 approve restart restoration and neutral locked presentation; platform proof remains partial. | Hide expense details while locked and keep transaction contents out of logs. Provide visible labels, touch controls, large-text support, and reduced motion. |
| Dashboard and analytics | Choose a reporting period, inspect totals, comparisons, bubbles/trends, and open matching transactions. (SPEC-003/004, Draft.) | Empty, loading, and error behavior is partly specified. Timezone, custom-period comparison, filter persistence, bucket rules, and insight thresholds remain open. | Figures use the same transaction filtering semantics; every visualization needs exact text/list values and transaction drill-down. No remote telemetry. |
| History, export, and restore | Search/filter/sort history; export through a user-selected document; validate and preview a restore before applying it. (SPEC-006/007, Draft.) | Search semantics and tie-breakers are open. Restore must validate before mutation and roll back on failure; D-04 restore policy and file-format decisions remain open. Accessible recovery states need explicit criteria. | Exclude backup contents from logs. Export uses Android document APIs; whether remote-backed document providers are allowed is unresolved under D-07. Provide accessible filters, previews, errors, and confirmations. |
| Accessibility across critical journeys | Complete entry, history, dashboard, analytics, overlay settings, and restore with visible labels, touch actions, large/bold text, high contrast, and reduced motion. (SPEC-008, Draft; D-08 excludes screen readers.) | Show errors and recovery outcomes; preserve usable empty and failure states. The specs do not yet map these consistently. | Do not rely on color or motion alone. Provide equivalent lists/text for charts and bubbles. Apply privacy requirements: keep data on device, exclude sensitive contents from logs, and protect lock-screen overlays. English is the first-release locale; audit devices and text scale remain to be selected. |

Approved and unresolved owner decisions are listed in [the decision log](../decisions.md). Feature specifications must resolve their own open questions before approval and implementation.

## Boundaries

- Single user, no account, offline function, no cloud service in the MVP.
- One base currency; no conversion.
- Money is stored as integer minor units and an ISO 4217 code.
- SQLite remains the source of truth if sync is added later.
- Backup is user-initiated; permission requests occur only when needed.

## Release success

- **REL-01:** Every approved acceptance criterion maps to a passing verification result; unrun checks are not reported as passed.
- **REL-02:** Dashboard, analytics, and history totals reconcile for the same filters and reporting interval.
- **REL-03:** Expense data survives app restart, and export/restore preserves the supported records in a round trip.
- **REL-04:** Overlay permission denial and revocation leave normal in-app entry usable; lock-screen privacy and lifecycle checks pass on the required devices.
- **REL-05:** All six MVP outcomes, including first-run setup and category/payment management, pass accessibility checks for success, empty, validation, and recovery states, as defined by approved specifications. Checks include visible labels, touch actions, large/bold text, high contrast, and reduced motion; screen-reader checks are excluded under D-08.
- **REL-06:** Release, privacy, performance, and recovery checks pass with no known critical or data-loss defects.
