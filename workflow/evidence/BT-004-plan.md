# BT-004 Planner handoff

**Owner:** `bt004_planner` (read-only project Planner).  
**Risk:** Low; documentation edits only.  
**Scope and ownership:** The primary coordinator authors `docs/specs/SPEC-001-expense-entry.md`, `docs/specs/SPEC-002-categories-payment-methods.md`, and `docs/decisions.md`, and owns `workflow/evidence/BT-004*`. An independent Reviewer reads the completed specifications and evidence.

## Executable plan

1. Claim BT-004 and record ownership. Preserve SPEC-001-AC-01 through AC-09 and the five proposed SPEC-002 criterion numbers.
2. Obtain explicit product decisions for deletion undo, first-run currency, text and name limits, discard handling, starter records, and archive fallback. Record decisions with date in `docs/decisions.md`. Draft independent sections while answers are pending.
3. Complete every section in `docs/specs/TEMPLATE.md`. Specify create, edit, duplicate, delete, undo, category and payment management states, validation, interruption, storage failure, empty states, privacy, accessibility, and recovery.
4. Map each criterion to a proposed named domain, Room, Compose UI, or instrumented test and its verification command. BT-007 and BT-009 own the failing tests and implementation.
5. Obtain independent review for completeness, ADR consistency, stable IDs, testability, and resolved behavior questions. Mark both specs Approved only after that review.
6. Run the packet's `rg -n 'Open questions|Status|AC-' docs/specs/SPEC-001* docs/specs/SPEC-002*` and manually review every criterion. Record evidence and move through `claimed → verifying → review → done` with `workflow/transition.py`.

## Findings and recovery

Product choices still open at plan time: D-03 undo duration, D-05 currency policy relevant to entry, merchant/note limits, unsaved-form discard, starter records, name limits and normalization, and archive fallback. No external research is needed for these owner choices. Restore only the three owned documentation files from `/tmp/bt004-baseline/` if the edits require rollback; no app data changes.
