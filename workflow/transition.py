#!/usr/bin/env python3
"""Move a task through the finite, gated workflow state machine."""
import argparse
import json
from datetime import datetime, timezone
from pathlib import Path

from gate import check, check_candidate, run_gate

ROOT = Path(__file__).resolve().parent.parent
QUEUE = ROOT / "workflow/tasks.json"

PHASES = {"pending", "claimed", "red", "implementing", "verifying", "review", "done", "blocked"}
FAILURE_ROUTES = {
    ("builder", "implementation"): "implementing",
    ("tester", "test"): "red",
    ("coordinator", "specification"): "blocked",
    ("coordinator", "environment"): "blocked",
}


def _write_json(path, value):
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, indent=2) + "\n")
    return temporary


def _restore_bytes(path, contents):
    temporary = path.with_name(f".{path.name}.rollback.tmp")
    temporary.write_bytes(contents)
    temporary.replace(path)


def _phase(evidence, queue_state):
    workflow = evidence.get("workflow")
    if workflow is None:
        workflow = {}
        evidence["workflow"] = workflow
    if not isinstance(workflow, dict):
        raise ValueError("Workflow evidence must be an object")
    workflow.setdefault("phase", queue_state)
    workflow.setdefault("correctionCycles", 0)
    workflow.setdefault("maxCorrectionCycles", 2)
    workflow.setdefault("activeFailure", None)
    workflow.setdefault("blockedReason", "")
    workflow.setdefault("failureHistory", {})
    return workflow


def _require_phase(queue_task, workflow):
    if queue_task.get("state") != workflow.get("phase"):
        raise ValueError(
            f"Queue state {queue_task.get('state')} does not match evidence phase "
            f"{workflow.get('phase')}"
        )


def _utc_now():
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def _phase_elapsed_ms(started_at, finished_at):
    if started_at is None:
        return None
    started = datetime.fromisoformat(started_at.replace("Z", "+00:00"))
    finished = datetime.fromisoformat(finished_at.replace("Z", "+00:00"))
    return max(0, int((finished - started).total_seconds() * 1000))


def _record_phase_visit(workflow, current, target, timestamp):
    visits = workflow.get("phaseTimings")
    if visits is None:
        visits = []
    if not isinstance(visits, list):
        raise ValueError("workflow.phaseTimings must be a list")
    if not visits:
        visits.append(
            {"phase": current, "startedAt": None, "finishedAt": None, "elapsedMs": None}
        )
    active = visits[-1]
    if not isinstance(active, dict) or active.get("phase") != current or active.get("finishedAt") is not None:
        raise ValueError("workflow.phaseTimings must end with the current open phase")
    started_at = active.get("startedAt")
    active["finishedAt"] = timestamp
    active["elapsedMs"] = _phase_elapsed_ms(started_at, timestamp)
    visits.append(
        {"phase": target, "startedAt": timestamp, "finishedAt": None, "elapsedMs": None}
    )
    workflow["phaseTimings"] = visits


def _handoff_gate(task_id, action, root, queue, evidence, state):
    """Validate the destination phase before either source file is replaced."""
    task_copy = next(item for item in queue["tasks"] if item["id"] == task_id)
    task_copy["state"] = state
    evidence_copy = dict(evidence)
    evidence_copy["workflow"] = dict(evidence.get("workflow") or {})
    evidence_copy["workflow"]["phase"] = state
    if action == "claim":
        stage = "check-start"
        failures = run_gate(
            task_id,
            stage,
            root,
            lambda: check_candidate(task_id, stage, root, queue, evidence_copy),
        )
    elif action == "done":
        stage = "check-done"
        failures = run_gate(task_id, stage, root, lambda: check(task_id, stage, root))
    elif action in {"implement", "review"}:
        stage = "check-build" if action == "implement" else "check-review"
        failures = run_gate(task_id, stage, root, lambda: check(task_id, stage, root))
    else:
        failures = []
    if failures:
        raise ValueError("Transition gate failed:\n" + "\n".join(failures))


def transition(task_id, action, root=ROOT):
    queue_path = root / "workflow/tasks.json"
    evidence_path = root / "workflow/evidence" / f"{task_id}.json"
    queue = json.loads(queue_path.read_text())
    tasks = {item["id"]: item for item in queue["tasks"]}
    if task_id not in tasks:
        raise ValueError(f"Unknown task {task_id}")
    if not evidence_path.is_file():
        raise ValueError(f"Missing handoff evidence for {task_id}")
    evidence = json.loads(evidence_path.read_text())
    task = tasks[task_id]
    workflow = _phase(evidence, task.get("state", "pending"))
    _require_phase(task, workflow)
    current = workflow["phase"]

    if action == "claim":
        if current != "pending":
            raise ValueError(f"{task_id} is {current}, expected pending")
        unfinished = [dep for dep in task["dependsOn"] if tasks[dep]["state"] != "done"]
        if unfinished:
            raise ValueError(f"Unfinished dependencies: {', '.join(unfinished)}")
        target = "claimed"
    elif action == "red":
        if current != "claimed":
            raise ValueError(f"{task_id} is {current}, expected claimed")
        target = "red"
    elif action == "implement":
        if current != "red":
            raise ValueError(f"{task_id} is {current}, expected red")
        target = "implementing"
    elif action == "verify":
        kind = evidence.get("kind")
        if current == "implementing":
            target = "verifying"
        elif current == "claimed" and kind in {"documentation", "proof"}:
            target = "verifying"
        else:
            raise ValueError(f"{task_id} cannot enter verification from {current}")
    elif action == "review":
        if current != "verifying":
            raise ValueError(f"{task_id} is {current}, expected verifying")
        target = "review"
        active_failure = workflow.get("activeFailure")
        if isinstance(active_failure, dict) and active_failure.get("status") == "open":
            active_failure["status"] = "resolved"
            workflow.setdefault("failureHistory", {}).pop(active_failure.get("key"), None)
    elif action == "done":
        if current != "review":
            raise ValueError(f"{task_id} is {current}, expected review")
        target = "done"
    elif action == "rework":
        if current not in {"implementing", "verifying", "review"}:
            raise ValueError(f"{task_id} cannot enter rework from {current}")
        failure = workflow.get("activeFailure")
        if not isinstance(failure, dict) or failure.get("status") != "open":
            raise ValueError("Rework requires an open failure classification")
        owner, classification, key = (
            failure.get("owner"), failure.get("classification"), failure.get("key")
        )
        route = FAILURE_ROUTES.get((owner, classification))
        if not route or not isinstance(key, str) or not key.strip():
            raise ValueError("Rework requires a valid owner, classification, and stable key")
        history = workflow.setdefault("failureHistory", {})
        occurrences = history.get(key, 0) + 1
        history[key] = occurrences
        workflow["correctionCycles"] = workflow.get("correctionCycles", 0) + 1
        if occurrences >= 2:
            target = "blocked"
            workflow["blockedReason"] = f"Repeated unresolved failure: {key}"
        else:
            target = route
            workflow["blockedReason"] = "" if target != "blocked" else f"{classification}: {key}"
        failure["occurrences"] = occurrences
    else:
        raise ValueError(f"Unknown action {action}")

    _handoff_gate(task_id, action, root, queue, evidence, target)
    _record_phase_visit(workflow, current, target, _utc_now())
    task["state"] = target
    workflow["phase"] = target
    queue_before = queue_path.read_bytes()
    evidence_before = evidence_path.read_bytes()
    queue_temp = _write_json(queue_path, queue)
    try:
        evidence_temp = _write_json(evidence_path, evidence)
    except OSError:
        queue_temp.unlink(missing_ok=True)
        raise
    try:
        evidence_temp.replace(evidence_path)
        queue_temp.replace(queue_path)
    except OSError:
        queue_temp.unlink(missing_ok=True)
        evidence_temp.unlink(missing_ok=True)
        try:
            _restore_bytes(queue_path, queue_before)
            _restore_bytes(evidence_path, evidence_before)
        except OSError as rollback_error:
            raise OSError(
                f"Transition failed and rollback could not restore both state files: {rollback_error}"
            ) from rollback_error
        raise
    return target


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["claim", "red", "implement", "verify", "review", "rework", "done"])
    parser.add_argument("task_id")
    args = parser.parse_args()
    try:
        state = transition(args.task_id, args.action)
    except (OSError, ValueError, KeyError) as exc:
        parser.exit(1, f"BLOCKED: {exc}\n")
    print(f"{args.task_id}: {state}")


if __name__ == "__main__":
    main()
