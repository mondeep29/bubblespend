#!/usr/bin/env python3
"""Persist a coordinator-provided Planner response and its deterministic path."""
import argparse
import json
import os
import re
import sys
import tempfile
from pathlib import Path

TASK_ID = re.compile(r"^BT-[0-9]{3}$")
MAX_PLAN_BYTES = 1_000_000


def _safe_file(path, parent):
    if path.is_symlink() or path.resolve().parent != parent.resolve():
        raise ValueError("Plan/evidence path must be a regular file in its expected directory")


def _stage(path, content):
    fd, name = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as stream:
            stream.write(content)
            stream.flush()
            os.fsync(stream.fileno())
    except BaseException:
        Path(name).unlink(missing_ok=True)
        raise
    return Path(name)


def record_plan(task_id, text, root=None):
    root = Path(root or Path(__file__).resolve().parent.parent).resolve()
    if not isinstance(task_id, str) or not TASK_ID.fullmatch(task_id):
        raise ValueError("Invalid task ID")
    if not isinstance(text, str) or not text.strip():
        raise ValueError("Planner response must not be empty")
    plan_bytes = text.encode("utf-8")
    if len(plan_bytes) > MAX_PLAN_BYTES:
        raise ValueError("Planner response exceeds size limit")
    workflow = root / "workflow"
    evidence_dir = workflow / "evidence"
    queue_path = workflow / "tasks.json"
    evidence_path = evidence_dir / f"{task_id}.json"
    plan_path = evidence_dir / f"{task_id}-plan.md"
    if workflow.is_symlink() or evidence_dir.is_symlink():
        raise ValueError("Workflow paths must not be symlinks")
    queue = json.loads(queue_path.read_text(encoding="utf-8"))
    if not any(item.get("id") == task_id for item in queue.get("tasks", [])):
        raise ValueError("Task ID is not present in the active queue")
    if not evidence_path.is_file():
        raise ValueError("Task evidence is missing")
    _safe_file(evidence_path, evidence_dir)
    _safe_file(plan_path, evidence_dir)
    evidence_bytes = evidence_path.read_bytes()
    evidence = json.loads(evidence_bytes.decode("utf-8"))
    if evidence.get("taskId") != task_id or not isinstance(evidence.get("planner"), dict):
        raise ValueError("Task evidence does not contain the matching planner record")
    evidence["planner"]["planPath"] = f"workflow/evidence/{task_id}-plan.md"
    replacement = (json.dumps(evidence, indent=2, ensure_ascii=False) + "\n").encode("utf-8")
    prior_plan = plan_path.read_bytes() if plan_path.exists() else None
    staged_plan = _stage(plan_path, plan_bytes)
    staged_evidence = _stage(evidence_path, replacement)
    try:
        staged_plan.replace(plan_path)
        staged_evidence.replace(evidence_path)
    except BaseException:
        staged_plan.unlink(missing_ok=True)
        staged_evidence.unlink(missing_ok=True)
        if prior_plan is None:
            plan_path.unlink(missing_ok=True)
        else:
            plan_restore = _stage(plan_path, prior_plan)
            plan_restore.replace(plan_path)
        evidence_restore = _stage(evidence_path, evidence_bytes)
        evidence_restore.replace(evidence_path)
        raise
    return plan_path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("task_id")
    args = parser.parse_args()
    try:
        path = record_plan(args.task_id, sys.stdin.read())
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"Unable to record plan: {exc}", file=sys.stderr)
        return 1
    print(path.relative_to(Path(__file__).resolve().parent.parent))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
