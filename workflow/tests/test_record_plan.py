import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

MODULE_PATH = Path(__file__).resolve().parents[1] / "record_plan.py"

def load_module():
    if not MODULE_PATH.is_file():
        raise AssertionError("workflow/record_plan.py must provide the coordinator command")
    spec = importlib.util.spec_from_file_location("bubble_record_plan", MODULE_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

class RecordPlanTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        (self.root / "workflow/evidence").mkdir(parents=True)
        self.queue_path = self.root / "workflow/tasks.json"
        self.evidence_path = self.root / "workflow/evidence/BT-123.json"
        self.plan_path = self.root / "workflow/evidence/BT-123-plan.md"
        self.queue_path.write_text(json.dumps({"tasks": [{
            "id": "BT-123", "packet": "docs/agent-tasks/BT-123.md",
            "dependsOn": [], "state": "claimed"
        }]}))
        self.evidence = {
            "taskId": "BT-123", "kind": "foundation",
            "planner": {"owner": "planner", "planPath": "old/location.md",
                        "risk": "low", "operations": ["preserve me"]},
            "tester": {"status": "pending"},
        }
        self.evidence_path.write_text(json.dumps(self.evidence, indent=2) + "\n")
        self.plan_path.write_text("old plan\n")

    def tearDown(self):
        self.temp.cleanup()

    def snapshot(self):
        return self.evidence_path.read_bytes(), self.plan_path.read_bytes()

    def test_valid_plan_uses_deterministic_path_and_only_updates_plan_path(self):
        module = load_module()
        before = json.loads(self.evidence_path.read_text())
        module.record_plan("BT-123", "# New executable plan\n", self.root)
        after = json.loads(self.evidence_path.read_text())
        before["planner"]["planPath"] = "workflow/evidence/BT-123-plan.md"
        self.assertEqual(before, after)
        self.assertEqual("# New executable plan\n", self.plan_path.read_text())

    def test_invalid_inputs_preserve_existing_bytes(self):
        module = load_module()
        for task_id, plan in (
            ("BT-12", "replacement"),
            ("BT-123/../../escape", "replacement"),
            ("bt-123", "replacement"),
            ("BT-999", "replacement"),
            ("BT-123", "   \n"),
            ("BT-123", "x" * (module.MAX_PLAN_BYTES + 1)),
        ):
            with self.subTest(task_id=task_id, size=len(plan)):
                before = self.snapshot()
                with self.assertRaises((ValueError, OSError, SystemExit)):
                    module.record_plan(task_id, plan, self.root)
                self.assertEqual(before, self.snapshot())

    def test_missing_evidence_preserves_existing_plan(self):
        module = load_module()
        self.evidence_path.unlink()
        before = self.plan_path.read_bytes()
        with self.assertRaises((ValueError, OSError)):
            module.record_plan("BT-123", "replacement", self.root)
        self.assertEqual(before, self.plan_path.read_bytes())

    def test_symlinked_plan_target_is_rejected_without_touching_target(self):
        module = load_module()
        outside = self.root / "outside.md"
        outside.write_text("outside bytes\n")
        self.plan_path.unlink()
        self.plan_path.symlink_to(outside)
        evidence_before = self.evidence_path.read_bytes()
        with self.assertRaises((ValueError, OSError)):
            module.record_plan("BT-123", "replacement", self.root)
        self.assertEqual(b"outside bytes\n", outside.read_bytes())
        self.assertEqual(evidence_before, self.evidence_path.read_bytes())

    def test_second_replace_failure_rolls_back_both_files_byte_for_byte(self):
        module = load_module()
        before = self.snapshot()
        real_replace = Path.replace
        def fail_evidence_replace(path, target):
            if Path(target) == self.evidence_path and path != self.evidence_path:
                raise OSError("simulated evidence replacement failure")
            return real_replace(path, target)
        with patch.object(Path, "replace", new=fail_evidence_replace):
            with self.assertRaisesRegex(OSError, "replacement failure"):
                module.record_plan("BT-123", "new plan", self.root)
        self.assertEqual(before, self.snapshot())

if __name__ == "__main__":
    unittest.main()
