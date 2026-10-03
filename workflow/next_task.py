#!/usr/bin/env python3
"""Print all currently unblocked BubbleSpend tasks; never mutates the queue."""
import json
from pathlib import Path

root = Path(__file__).resolve().parent.parent
data = json.loads((root / "workflow/tasks.json").read_text())
tasks = data["tasks"]
by_id = {task["id"]: task for task in tasks}
if len(by_id) != len(tasks):
    raise SystemExit("Duplicate task IDs")
for task in tasks:
    missing = [dep for dep in task["dependsOn"] if dep not in by_id]
    if missing:
        raise SystemExit(f"{task['id']}: unknown dependencies {missing}")
    if not (root / task["packet"]).is_file():
        raise SystemExit(f"{task['id']}: missing packet {task['packet']}")
    if task["state"] == "pending" and all(by_id[dep]["state"] == "done" for dep in task["dependsOn"]):
        print(f"{task['id']}  {task['title']}  [{task['packet']}]")
