# TASK-ID evidence

**State:** claimed
**Role/owner:**
**Files owned:**
**Spec and acceptance IDs:**
**ADRs:**
**Risk level and preflight reviewer:**

Planner records risk before work. Low-risk plans may be concise; elevated plans
include sources/recovery and an independent preflight; destructive operations
also require action-specific live approval.

**Workflow phase:** pending  
**Correction cycles:** 0 / 2  
**Active failure owner / classification / stable key:** None
**Phase timing history:** `workflow.phaseTimings` in JSON evidence; legacy starts unknown (`null`).

## Inputs and assumptions

## Red test command and failure

Use `Not applicable: documentation/proof task` only when packet permits.

## Changes

## Green and verification commands/results

Include device/API level for instrumented proof.

## Traceability updates

## Independent review

Reviewer, findings, fixes, remaining blockers.

## Destructive actions

Exact target, dry-run output, recovery path, and action-specific permission result. Write `None` when no destructive action occurred; never paste credentials.

## Known limits and handoff

For rework, record the owner (`builder`, `tester`, or `coordinator`), classification (`implementation`, `test`, `specification`, or `environment`), and a stable failure key in JSON evidence before running `python3 workflow/transition.py rework TASK-ID`. The second unresolved occurrence of the same key blocks the task.

Production gate evaluations append sanitized timing metadata to `workflow/evidence/TASK-ID-gates.jsonl`. Each record contains only task ID, fixed gate name, UTC start/finish, monotonic elapsed milliseconds, and `pass`, `block`, or `error`. A gate pass records evaluation only; it does not claim a later transition write committed.
