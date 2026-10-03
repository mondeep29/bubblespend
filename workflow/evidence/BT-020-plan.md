# BT-020 Planner plan

**Planner:** `bt020_plan` (`gpt-6-sol`), read-only. Prepared 2026-09-29 UTC.

## Objective and acceptance

Measure gate calls and task-phase duration before further pipeline optimization. Criteria are BT-020-AC-01 through BT-020-AC-04 in `docs/agent-tasks/BT-020.md`.

## Findings and risk

Risk is **elevated** because `gate.py` and `transition.py` enforce security and review handoffs. No Android APIs, product data, dependencies, CI, or external services are involved. No external research is needed: the work uses Python's standard library and the existing local workflow. No product specifications or ADRs apply.

Store gate attempts in a per-task `workflow/evidence/BT-NNN-gates.jsonl` record and phase visits under `workflow.phaseTimings`. This keeps rejected gate attempts observable without mutating task evidence and preserves the queue/evidence rollback invariant. Keep pure `gate.check()` side-effect free; instrument production CLI and transition entry points. Include UTC timestamps, monotonic elapsed milliseconds, task ID, gate name, and a fixed outcome only. Never log arbitrary error text or commands.

For an already-active task with no historical timestamps, close its first observed phase with `startedAt: null` and `elapsedMs: null`, then open the destination phase at the transition time. Rework begins a new phase visit. Terminal visits may have `finishedAt: null`.

## Ownership and sequence

1. Coordinator owns BT-020 packet, task/evidence queue metadata, and persisted plan.
2. Tester owns `workflow/tests/test_gate.py` and `workflow/tests/test_transition.py`; write red tests for gate timing, phase visit history, privacy-safe fields, rollback, and legacy compatibility.
3. Builder owns `workflow/gate.py`, `workflow/transition.py`, evidence templates, and `workflow/README.md`.
4. Coordinator records handoffs and queue state. BT-006 remains disjoint and untouched.

## Verification

- `python3 -m unittest discover -s workflow/tests -p 'test_*.py' -v`
- `python3 -m py_compile workflow/gate.py workflow/transition.py workflow/next_task.py`
- `python3 -m json.tool workflow/templates/handoff.json`
- `python3 -m json.tool workflow/tasks.json`
- `python3 workflow/next_task.py`
- Run BT-020 phase gates at the corresponding transitions and inspect pass/block timing records.

## Recovery and open questions

Restore only BT-020-owned workflow scripts, docs/templates, tests, and BT-020 evidence. A timing record may remain after a failed transition write if it accurately records that the gate passed but the state change did not commit. No open product questions.
