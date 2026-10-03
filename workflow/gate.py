#!/usr/bin/env python3
"""Validate BubbleSpend task handoffs. This never grants command approval."""
import argparse
from datetime import datetime, timezone
import json
import os
import re
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RISK = {"low": 0, "elevated": 1, "destructive": 2}
FLOORS = {
    "BT-003": "elevated", "BT-005": "elevated", "BT-006": "elevated",
    "BT-007": "elevated", "BT-011": "elevated", "BT-014": "elevated",
    "BT-016": "elevated", "BT-017": "elevated", "BT-019": "elevated",
    "BT-021": "elevated",
}
REQUIRED_SPECS = {
    "BT-007": ["docs/specs/SPEC-001-expense-entry.md", "docs/specs/SPEC-002-categories-payment-methods.md"],
    "BT-011": ["docs/specs/SPEC-003-dashboard.md", "docs/specs/SPEC-004-analytics.md", "docs/specs/SPEC-006-history-search.md"],
    "BT-014": ["docs/specs/SPEC-005-floating-shortcut.md"],
    "BT-016": ["docs/specs/SPEC-007-export-restore.md"],
}
KINDS = {"behavior", "documentation", "proof", "foundation"}
GATES = frozenset({"check-start", "check-build", "check-review", "check-done"})
TASK_ID_PATTERN = re.compile(r"^BT-[0-9]{3}$")


def nonempty(value):
    return isinstance(value, str) and bool(value.strip())


def _utc_now():
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def _validated_task_id(task_id):
    if not isinstance(task_id, str) or not TASK_ID_PATTERN.fullmatch(task_id):
        raise ValueError("Invalid task ID")
    return task_id


def _append_gate_record(task_id, stage, root, record):
    """Append one metadata-only gate record beneath this project's evidence folder."""
    task_id = _validated_task_id(task_id)
    if stage not in GATES:
        raise ValueError("Invalid gate name")
    root_path = Path(root).resolve()
    workflow_dir = root_path / "workflow"
    evidence_dir = workflow_dir / "evidence"
    if workflow_dir.is_symlink() or evidence_dir.is_symlink():
        raise OSError("Workflow evidence path must not contain symlinks")
    if not evidence_dir.is_dir():
        raise OSError("Workflow evidence directory is missing")
    log_path = evidence_dir / f"{task_id}-gates.jsonl"
    if log_path.is_symlink() or log_path.resolve().parent != evidence_dir.resolve():
        raise OSError("Gate log path must remain inside workflow evidence")
    flags = os.O_WRONLY | os.O_APPEND | os.O_CREAT
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    descriptor = os.open(log_path, flags, 0o600)
    try:
        payload = (json.dumps(record, separators=(",", ":"), ensure_ascii=True) + "\n").encode("utf-8")
        offset = 0
        while offset < len(payload):
            written = os.write(descriptor, payload[offset:])
            if written <= 0:
                raise OSError("Unable to append complete gate record")
            offset += written
    finally:
        os.close(descriptor)


def run_gate(task_id, stage, root, evaluator):
    """Time and log one production gate evaluation, then return/rethrow its result."""
    task_id = _validated_task_id(task_id)
    if stage not in GATES:
        raise ValueError("Invalid gate name")
    started_at = _utc_now()
    started_ns = time.monotonic_ns()
    outcome = "error"
    try:
        result = evaluator()
        outcome = "block" if result else "pass"
        return result
    finally:
        finished_at = _utc_now()
        elapsed_ms = max(0, (time.monotonic_ns() - started_ns) // 1_000_000)
        _append_gate_record(
            task_id,
            stage,
            root,
            {
                "taskId": task_id,
                "gate": stage,
                "startedAt": started_at,
                "finishedAt": finished_at,
                "elapsedMs": elapsed_ms,
                "outcome": outcome,
            },
        )


PHASE_FOR_STAGE = {
    "check-start": "claimed",
    "check-build": "red",
    "check-review": "verifying",
    "check-done": "review",
}


def check(task_id, stage, root=None, queue_data=None, evidence_override=None):
    if root is None:
        root = ROOT
    failures = []
    try:
        queue = (queue_data or json.loads((root / "workflow/tasks.json").read_text()))["tasks"]
        tasks = {task["id"]: task for task in queue}
        task = tasks[task_id]
    except (OSError, ValueError, KeyError) as exc:
        return [f"Invalid task queue or ID: {exc}"]
    for dependency in task["dependsOn"]:
        if tasks.get(dependency, {}).get("state") != "done":
            failures.append(f"Dependency {dependency} is not done")
    if not (root / task["packet"]).is_file():
        failures.append("Task packet is missing")
    evidence_path = root / "workflow/evidence" / f"{task_id}.json"
    if evidence_override is not None:
        evidence = evidence_override
    else:
        try:
            evidence = json.loads(evidence_path.read_text())
        except (OSError, ValueError) as exc:
            return failures + [f"Missing or invalid handoff evidence: {exc}"]
    if evidence.get("taskId") != task_id:
        failures.append("Evidence taskId does not match")
    kind = evidence.get("kind")
    if kind not in KINDS:
        failures.append(f"kind must be one of {sorted(KINDS)}")
    workflow = evidence.get("workflow")
    legacy_completed = task.get("state") == "done" and workflow is None
    if task.get("state") != "done":
        if workflow is None:
            workflow = {
                "phase": task.get("state"),
                "correctionCycles": 0,
                "maxCorrectionCycles": 2,
                "activeFailure": None,
                "blockedReason": "",
                "failureHistory": {},
            }
        if not isinstance(workflow, dict):
            failures.append("Active task workflow evidence must be an object")
            workflow = {
                "phase": task.get("state"),
                "correctionCycles": 0,
                "maxCorrectionCycles": 2,
                "activeFailure": None,
                "blockedReason": "",
                "failureHistory": {},
            }
        else:
            workflow = dict(workflow)
            required_workflow_fields = (
                "phase", "correctionCycles", "maxCorrectionCycles", "activeFailure", "blockedReason"
            )
            if any(field not in workflow for field in required_workflow_fields):
                failures.append("Active task workflow control fields are incomplete")
            workflow.setdefault("phase", task.get("state"))
            workflow.setdefault("correctionCycles", 0)
            workflow.setdefault("maxCorrectionCycles", 2)
            workflow.setdefault("activeFailure", None)
            workflow.setdefault("blockedReason", "")
            workflow.setdefault("failureHistory", {})
        phase = workflow["phase"]
        if phase != task.get("state"):
            failures.append(
                f"Workflow phase {phase!r} does not match queue state {task.get('state')!r}"
            )
        expected_phase = PHASE_FOR_STAGE.get(stage)
        if expected_phase and phase != expected_phase:
            failures.append(f"{stage} requires workflow phase {expected_phase}, found {phase}")
        if not isinstance(workflow.get("correctionCycles"), int) or workflow["correctionCycles"] < 0:
            failures.append("Workflow correctionCycles must be a non-negative integer")
        if not isinstance(workflow.get("maxCorrectionCycles"), int) or workflow["maxCorrectionCycles"] < 1:
            failures.append("Workflow maxCorrectionCycles must be a positive integer")
    plan = evidence.get("planner") or {}
    if not nonempty(plan.get("owner")):
        failures.append("Planner owner is missing")
    plan_path = plan.get("planPath")
    if not nonempty(plan_path):
        failures.append("Planner planPath is missing")
    else:
        candidate = (root / plan_path).resolve()
        if root.resolve() not in candidate.parents or not candidate.is_file():
            failures.append("Planner planPath must be an existing file inside this project")
    if not plan.get("operations") or not isinstance(plan.get("operations"), list):
        failures.append("Planner operations are missing")
    if not plan.get("ownedFiles") or not isinstance(plan.get("ownedFiles"), list):
        failures.append("Owned files are missing")
    if not nonempty(plan.get("rollback")):
        failures.append("Rollback/recovery plan is missing")
    risk = plan.get("risk")
    if risk not in RISK:
        failures.append("Risk must be low, elevated, or destructive")
        risk_value = -1
    else:
        risk_value = RISK[risk]
        if risk_value < RISK.get(FLOORS.get(task_id, "low"), 0):
            failures.append(f"Risk below {FLOORS[task_id]} floor for {task_id}")
    approved_specs = plan.get("approvedSpecs", [])
    for required in REQUIRED_SPECS.get(task_id, []):
        if required not in approved_specs:
            failures.append(f"Required approved spec missing from handoff: {required}")
    for spec in approved_specs:
        candidate = (root / spec).resolve()
        if root.resolve() not in candidate.parents or not candidate.is_file():
            failures.append(f"Approved spec path missing/outside project: {spec}")
        elif "**Status:** Approved" not in candidate.read_text():
            failures.append(f"Spec not approved: {spec}")
    if kind == "behavior" and not plan.get("acceptanceIds"):
        failures.append("Behavior task lacks acceptance IDs")
    if risk_value >= 1:
        security = evidence.get("securityPreflight") or {}
        reviewer = security.get("reviewer")
        if not nonempty(reviewer) or reviewer == plan.get("owner"):
            failures.append("Independent security preflight reviewer is required")
        if security.get("decision") != "approved":
            failures.append("Security preflight is not approved")
        if not nonempty(security.get("scope")):
            failures.append("Security preflight scope is missing")
    if risk_value == 2:
        approval = evidence.get("destructiveApproval") or {}
        if not all(nonempty(approval.get(field)) for field in ("action", "approvalReference", "recoverySnapshot")):
            failures.append("Destructive action requires a recorded, scoped approval and recovery snapshot")
        # The record is traceability only. The active tool must still obtain live approval.
    if stage in {"check-build", "check-review", "check-done"} and kind in {"behavior", "foundation"}:
        tester = evidence.get("tester") or {}
        red = tester.get("red") or {}
        if not nonempty(tester.get("owner")):
            failures.append("Tester owner is missing")
        if not nonempty(red.get("command")) or not nonempty(red.get("observedFailure")):
            failures.append("Genuine red-test command and observed failure are required")
        require_foundation_coverage = kind != "foundation" or not legacy_completed
        if require_foundation_coverage and kind in {"behavior", "foundation"} and not set(plan.get("acceptanceIds", [])).issubset(set(red.get("acceptanceIds", []))):
            failures.append("Red tests do not cover all planned acceptance IDs")
    if stage in {"check-review", "check-done"}:
        builder = evidence.get("builder") or {}
        tester = evidence.get("tester") or {}
        if kind in {"behavior", "foundation"}:
            if not nonempty(builder.get("owner")) or builder.get("status") != "complete" or not builder.get("changedFiles"):
                failures.append("Builder handoff is incomplete")
            if nonempty(builder.get("owner")) and builder.get("owner") == tester.get("owner"):
                failures.append("Tester and Builder owners must be independent")
            if tester.get("status") != "passed" or not tester.get("verification"):
                failures.append("Tester verification is incomplete")
            elif any(item.get("result") != "passed" or not nonempty(item.get("command")) for item in tester["verification"]):
                failures.append("Tester verification contains missing or failed results")
        elif kind in {"documentation", "proof"}:
            if tester.get("status") != "passed" or not tester.get("verification"):
                failures.append("Documentation/proof verification is incomplete")
    if stage == "check-done":
        review = evidence.get("featureReview") or {}
        reviewer = review.get("reviewer")
        if not nonempty(reviewer) or reviewer in {plan.get("owner"), (evidence.get("builder") or {}).get("owner"), (evidence.get("tester") or {}).get("owner")}:
            failures.append("Independent feature reviewer is required")
        if review.get("decision") != "approved":
            failures.append("Feature review is not approved")
        if any(item.get("severity") in {"blocker", "critical", "high"} and item.get("status") != "resolved" for item in review.get("findings", [])):
            failures.append("Blocking reviewer findings remain")
    return failures


def check_candidate(task_id, stage, root, queue_data, evidence):
    """Run the normal checks against an in-memory destination state."""
    return check(task_id, stage, root, queue_data, evidence)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("stage", choices=["check-start", "check-build", "check-review", "check-done"])
    parser.add_argument("task_id")
    args = parser.parse_args()
    try:
        task_id = _validated_task_id(args.task_id)
        failures = run_gate(task_id, args.stage, ROOT, lambda: check(task_id, args.stage))
    except Exception:
        print("BLOCKED: gate evaluation or timing record failed", file=sys.stderr)
        return 1
    if failures:
        for failure in failures:
            print(f"BLOCKED: {failure}", file=sys.stderr)
        return 1
    print(f"PASS: {args.stage} {args.task_id}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
