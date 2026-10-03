# BT-001 Planner handoff

**Owner:** `bt001_planner` (read-only project Planner)
**Risk:** Low; documentation review and edits only.
**Scope:** `docs/product/product-brief.md`, `docs/product/glossary.md`, `docs/product/non-functional-requirements.md`, and `docs/decisions.md`. Coordinator owns `workflow/evidence/BT-001.*`.

## Plan

Review all six product outcomes against journey/state, edge or error recovery, privacy, and accessibility rules. Add a concise crosswalk and state inventory to the product brief, tighten release evidence where needed, and retain open owner decisions as open. Do not edit specifications or ADRs in BT-001 and do not decide proposed behavior.

## Findings to carry into the edit

- The first-run setup outcome lacks a feature specification and defined recovery states; D-05 leaves currency and setup defaults open.
- Expense behavior has draft lifecycle coverage, but category/payment management has incomplete state and error detail.
- Overlay behavior has a draft journey, with restart and lock-screen decisions D-01/D-06 open; BT-003 still requires emulator and real-device proof.
- Dashboard, analytics, history, and backup/restore have draft journey coverage with unresolved boundary, search, comparison, and restore rules.
- Accessibility NFRs exist, but accessible error/recovery behavior and audit target details need criterion-level follow-up.

## Verification and recovery

The packet allows documentation-only work without a red test. Verify using `rg -n 'Draft|Proposed|Open questions|D-0' docs/product docs/decisions.md`, then manually review the six-outcome crosswalk and source docs. Compare the focused edits against baseline copies at `/tmp/bt001-baseline/`; restore only coordinator-owned documentation files from those copies if a BT-001 edit must be rolled back.
