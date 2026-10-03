# BT-019 Planner plan

**Planner:** `workflow_audit_plan` (`gpt-6-sol`), read-only. Prepared 2026-09-29 IST.

## Objective and acceptance

Make the established Planner → Tester red → Builder → Tester verification → Reviewer pipeline explicit and finite without weakening red-test, security-preflight, verification, or independent-review gates. Acceptance IDs are BT-019-AC-01 through BT-019-AC-04.

## Findings and risk

`workflow/README.md` documents intermediate states, but `workflow/transition.py` only implements `claim` and `done`; `done` accepts any active state. `workflow/gate.py` validates evidence contents but does not align gates with queue state. Tester red and verify phases are implicit. No failure owner, stable failure key, or repeated-failure termination rule exists. BT-005 evidence demonstrates repeated red/green/review cycles and duplicate verification.

Risk is **elevated** because this changes the development control plane. It does not touch Android production code, user data, dependencies, CI, credentials, external services, or destructive operations.

## Implementation and ownership

1. Coordinator creates and claims BT-019, persists this plan, assigns disjoint ownership, obtains independent security preflight, and runs `check-start`.
2. Tester owns `workflow/tests/**` and adds failing tests for legal phase order, forbidden skips, stage-aligned gates, role independence, rework routing, stable failure keys, repeat termination, and legacy completed evidence.
3. Builder owns `workflow/transition.py`, `workflow/gate.py`, templates, workflow documentation, `.codex/agents/*.toml`, and matching `AGENTS.md` wording.
4. Enforce `pending → claimed → red → implementing → verifying → review → done`. Documentation/proof may use `claimed → verifying`. Align start/build/review/done gates with claimed/red/verifying/review.
5. Route implementation failures to Builder, test failures to Tester, and specification/environment blockers to the coordinator. The second occurrence of the same unresolved stable failure key blocks automatic handoff. Existing completed tasks remain readable without migration.
6. Planner returns one complete plan. Builder executes it and returns either `COMPLETE` or one `PLAN_BLOCKED` handoff. Tester has explicit RED and VERIFY phases and freezes accepted red criteria. Reviewer batches blocking findings and delta-reviews repairs.
7. Tester verifies the implementation. Reviewer performs final independent review. Coordinator records the handoffs and completes through the gates.

## Verification

- `python3 -m unittest discover -s workflow/tests -p 'test_*.py' -v`
- `python3 -m py_compile workflow/gate.py workflow/transition.py workflow/next_task.py`
- JSON parsing for the queue and handoff template.
- TOML parsing for all custom agent definitions.
- `python3 workflow/next_task.py`
- BT-019 start/build/review/done gates at their matching phases.
- Confirm existing BT-005 evidence remains accepted without migration.

## Recovery and open questions

Restore only BT-019-owned files if review rejects the change. Existing task evidence and Android files remain untouched. The active BT-006 task owns only its packet, domain code/tests, and BT-006 evidence; this coordinator is integration owner for the disjoint workflow-control change. No product behavior question is open. A stable failure key is coordinator-assigned from stage and test/finding identity, never raw sensitive logs.
