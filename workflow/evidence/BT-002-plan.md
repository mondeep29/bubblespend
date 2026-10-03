# BT-002 Planner handoff

**Planner:** `bt002_planner_sol` (`gpt-6-sol`, read-only)
**Planning date:** 2026-09-27 UTC
**Objective:** Approve foundation architecture in ADR-001 through ADR-005 while preserving all unresolved product behavior and device-proof requirements.
**Risk:** Low. Scope is architecture documentation plus the user-directed project-agent model workflow. No dependencies, CI, production code, database, or user data are changed. The packet permits documentation-only work without a red test.
**Acceptance IDs:** None; BT-002 acceptance is that foundation ADR decisions are approved and blocking choices remain visible.

## Executable plan

1. Review module direction, Room repository contracts, DataStore split, money/time invariants, overlay boundary, and future sync metadata.
2. Approve ADR-001, ADR-002, and ADR-005 for their stated foundation decisions.
3. Scope ADR-003 approval to money storage, UTC instants, and `[start, end)` interval invariants; keep D-02 reporting timezone behavior open.
4. Scope ADR-004 approval to the opt-in permission and adapter boundary only; leave production overlay behavior blocked by BT-003 device proof and D-01/D-06.
5. Keep D-01 through D-07 open. Do not treat proposed defaults as owner approval.
6. Delegate exact Android, Kotlin, Compose, Room, DataStore, Gradle, and test dependency versions to BT-005 and lock them in checked-in build configuration there.
7. Retain the user-directed model sequence: Planner uses `gpt-6-sol`, Builder uses `gpt-6-luna`; Builder reads the persisted plan before editing. The same rule is now in `AGENTS.md`, `workflow/README.md`, and `workflow/agent-prompt.md`.
8. Run the required ADR search and manual review, then complete independent Tester and Reviewer handoffs.

## Product and device blockers

- D-02 remains open and blocks final reporting-timezone behavior/date-range acceptance criteria; the UTC interval contract can be approved without choosing it.
- D-01 and D-06 remain open. BT-003 must verify overlay lifecycle, permission, and lock-screen behavior on a representative emulator and a real device before production overlay implementation.
- No product-owner decision blocks the architecture boundaries in BT-002.
- No emulator or real-device evidence is claimed by this task.

## Research checked 2026-09-27 UTC

No dependency versions were selected in this task; they are assigned to BT-005. The following primary Android documentation supports the current ADR boundaries:

- Android recommends DataStore for small preference datasets and Room for relational/complex data, supporting the ADR-002 split: [DataStore guidance](https://developer.android.com/topic/libraries/architecture/datastore).
- Room destructive migration fallback can permanently remove table data without a migration path, supporting the no-destructive-fallback rule: [Room migration guidance](https://developer.android.com/training/data-storage/room/migrating-db-versions).
- `TYPE_APPLICATION_OVERLAY` requires `SYSTEM_ALERT_WINDOW`; Android can change overlay position or visibility. Keep permission/lifecycle details in the BT-003 platform proof: [WindowManager.LayoutParams](https://developer.android.com/reference/android/view/WindowManager.LayoutParams), [Settings](https://developer.android.com/reference/android/provider/Settings).
- Foreground service type and background-start rules vary with target API. Research and prove the selected approach in BT-003 before production overlay implementation: [Foreground service declarations](https://developer.android.com/develop/background-work/services/fgs/declare), [background-start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).

The project Firecrawl developer-index CLI was unavailable; the references above are direct official Android documentation. No device proof was run.

## Verification

- `python3 workflow/gate.py check-start BT-002`
- `rg -n 'Status|Room|minor units|UTC|overlay' docs/adr`
- Manual review of all five ADRs and the decision log.
- `python3 workflow/gate.py check-review BT-002` after independent verification.
- `python3 workflow/gate.py check-done BT-002` after independent review, then `python3 workflow/transition.py done BT-002`.

## Owned files

`docs/adr/ADR-001-application-architecture.md`, `docs/adr/ADR-002-local-database.md`, `docs/adr/ADR-003-money-and-time.md`, `docs/adr/ADR-004-android-floating-shortcut.md`, `docs/adr/ADR-005-future-sync-boundary.md`, `docs/decisions.md`, `.codex/agents/planner.toml`, `.codex/agents/builder.toml`, `AGENTS.md`, `workflow/README.md`, `workflow/agent-prompt.md`, and BT-002 evidence files.

## Recovery

There is no usable Git metadata. Current task-start copies of ADRs, decisions, agent configs, and workflow instructions are preserved under `/tmp/bt002-baseline/`. Review the focused changes and restore only BT-002-owned edits from these copies if needed. No destructive operation is planned.
