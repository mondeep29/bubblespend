import importlib.util
import contextlib
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


MODULE_PATH = Path(__file__).resolve().parents[1] / "gate.py"
spec = importlib.util.spec_from_file_location("bubble_gate", MODULE_PATH)
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class GateTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        (self.root / "workflow/evidence").mkdir(parents=True)
        (self.root / "docs/agent-tasks").mkdir(parents=True)
        (self.root / "docs/specs").mkdir(parents=True)
        (self.root / "docs/agent-tasks/BT-014.md").write_text("# packet")
        (self.root / "docs/specs/SPEC-005-floating-shortcut.md").write_text(
            "# Spec\n\n**Status:** Approved\n"
        )
        (self.root / "plan.md").write_text("# plan")
        self.queue = {
            "tasks": [
                {
                    "id": "BT-014",
                    "packet": "docs/agent-tasks/BT-014.md",
                    "dependsOn": [],
                    "state": "claimed",
                }
            ]
        }
        self.evidence = {
            "taskId": "BT-014",
            "kind": "behavior",
            "workflow": {
                "phase": "claimed",
                "correctionCycles": 0,
                "maxCorrectionCycles": 2,
                "activeFailure": None,
                "blockedReason": "",
            },
            "planner": {
                "owner": "planner",
                "planPath": "plan.md",
                "risk": "elevated",
                "operations": ["Write overlay adapter"],
                "ownedFiles": ["app/src/main"],
                "rollback": "Disable shortcut and revert adapter",
                "acceptanceIds": ["SPEC-005-AC-01"],
                "approvedSpecs": ["docs/specs/SPEC-005-floating-shortcut.md"],
            },
            "securityPreflight": {
                "reviewer": "reviewer",
                "decision": "approved",
                "scope": "overlay adapter",
                "findings": [],
            },
            "tester": {
                "owner": "tester",
                "red": {
                    "command": "./gradlew test",
                    "observedFailure": "Expected shortcut state",
                    "acceptanceIds": ["SPEC-005-AC-01"],
                },
                "verification": [{"command": "./gradlew test", "result": "passed"}],
                "status": "passed",
            },
            "builder": {
                "owner": "builder",
                "status": "complete",
                "revision": 1,
                "changedFiles": ["app/src/main/Shortcut.kt"],
            },
            "featureReview": {
                "reviewer": "reviewer",
                "decision": "approved",
                "findings": [],
            },
        }
        self.save()

    def tearDown(self):
        self.temp.cleanup()

    def save(self):
        (self.root / "workflow/tasks.json").write_text(json.dumps(self.queue))
        (self.root / "workflow/evidence/BT-014.json").write_text(
            json.dumps(self.evidence)
        )

    def set_phase(self, phase):
        self.queue["tasks"][0]["state"] = phase
        self.evidence["workflow"]["phase"] = phase
        self.evidence["workflow"]["phaseTimings"] = [
            {"phase": phase, "startedAt": None, "finishedAt": None, "elapsedMs": None}
        ]
        self.save()

    def test_complete_handoff_passes_all_phase_aligned_stages(self):
        stages = {
            "check-start": "claimed",
            "check-build": "red",
            "check-review": "verifying",
            "check-done": "review",
        }
        for stage, phase in stages.items():
            with self.subTest(stage=stage):
                self.set_phase(phase)
                self.assertEqual([], gate.check("BT-014", stage, self.root))

    def test_gates_reject_out_of_order_active_phases(self):
        wrong_phases = {
            "check-start": "red",
            "check-build": "claimed",
            "check-review": "implementing",
            "check-done": "verifying",
        }
        for stage, phase in wrong_phases.items():
            with self.subTest(stage=stage, phase=phase):
                self.set_phase(phase)
                problems = gate.check("BT-014", stage, self.root)
                self.assertTrue(
                    any("phase" in problem.lower() for problem in problems), problems
                )

    def test_gate_rejects_queue_and_evidence_phase_mismatch(self):
        self.queue["tasks"][0]["state"] = "red"
        self.evidence["workflow"]["phase"] = "claimed"
        self.save()
        problems = gate.check("BT-014", "check-build", self.root)
        self.assertTrue(any("phase" in problem.lower() for problem in problems), problems)

    def test_active_tasks_require_workflow_control_fields(self):
        required_fields = (
            "phase",
            "correctionCycles",
            "maxCorrectionCycles",
            "activeFailure",
            "blockedReason",
        )
        original = dict(self.evidence["workflow"])
        for field in required_fields:
            with self.subTest(field=field):
                self.evidence["workflow"] = dict(original)
                self.evidence["workflow"].pop(field)
                self.save()
                problems = gate.check("BT-014", "check-start", self.root)
                self.assertTrue(
                    any("workflow" in problem.lower() for problem in problems), problems
                )

    def test_already_done_legacy_evidence_remains_readable(self):
        self.queue["tasks"][0]["state"] = "done"
        self.evidence.pop("workflow")
        self.save()
        self.assertEqual([], gate.check("BT-014", "check-done", self.root))

    def test_actual_bt005_legacy_completed_evidence_passes_check_done(self):
        project_root = MODULE_PATH.parent.parent
        self.assertEqual([], gate.check("BT-005", "check-done", project_root))

    def test_elevated_work_requires_independent_security_preflight(self):
        self.evidence["securityPreflight"]["reviewer"] = "planner"
        self.save()
        self.assertTrue(
            any(
                "Independent security" in problem
                for problem in gate.check("BT-014", "check-start", self.root)
            )
        )

    def test_bt021_has_an_elevated_risk_floor(self):
        self.queue["tasks"][0].update({"id": "BT-021", "packet": "docs/agent-tasks/BT-021.md"})
        (self.root / "docs/agent-tasks/BT-021.md").write_text("# packet")
        self.evidence["taskId"] = "BT-021"
        self.evidence["planner"]["risk"] = "low"
        self.save()
        (self.root / "workflow/evidence/BT-014.json").unlink()
        (self.root / "workflow/evidence/BT-021.json").write_text(json.dumps(self.evidence))
        problems = gate.check("BT-021", "check-start", self.root)
        self.assertTrue(any("Risk below elevated" in item for item in problems), problems)

    def test_builder_waits_for_red_test(self):
        self.set_phase("red")
        self.evidence["tester"]["red"]["observedFailure"] = ""
        self.save()
        self.assertTrue(
            any(
                "red-test" in problem
                for problem in gate.check("BT-014", "check-build", self.root)
            )
        )

    def test_foundation_red_covers_all_planned_acceptance_ids(self):
        self.set_phase("red")
        self.evidence["kind"] = "foundation"
        self.evidence["planner"]["acceptanceIds"] = [
            "BT-019-AC-01",
            "BT-019-AC-02",
        ]
        self.evidence["tester"]["red"]["acceptanceIds"] = ["BT-019-AC-01"]
        self.save()
        problems = gate.check("BT-014", "check-build", self.root)
        self.assertTrue(
            any("acceptance" in problem.lower() for problem in problems), problems
        )

    def test_review_waits_for_test_pass(self):
        self.set_phase("verifying")
        self.evidence["tester"]["verification"][0]["result"] = "failed"
        self.save()
        self.assertTrue(
            any(
                "Tester verification" in problem
                for problem in gate.check("BT-014", "check-review", self.root)
            )
        )

    def test_tester_and_builder_must_be_independent(self):
        self.set_phase("verifying")
        self.evidence["tester"]["owner"] = self.evidence["builder"]["owner"]
        self.save()
        problems = gate.check("BT-014", "check-review", self.root)
        self.assertTrue(
            any(
                "tester" in problem.lower() and "builder" in problem.lower()
                for problem in problems
            ),
            problems,
        )

    def test_done_requires_independent_feature_review(self):
        self.set_phase("review")
        self.evidence["featureReview"]["reviewer"] = "builder"
        self.save()
        self.assertTrue(
            any(
                "Independent feature" in problem
                for problem in gate.check("BT-014", "check-done", self.root)
            )
        )

    def test_floor_and_approved_spec_are_enforced(self):
        self.evidence["planner"]["risk"] = "low"
        self.evidence["planner"]["approvedSpecs"] = []
        self.save()
        problems = gate.check("BT-014", "check-start", self.root)
        self.assertTrue(any("Risk below elevated" in problem for problem in problems))
        self.assertTrue(any("Required approved spec" in problem for problem in problems))

    def test_destructive_action_requires_approval_record(self):
        self.evidence["planner"]["risk"] = "destructive"
        self.save()
        self.assertTrue(
            any(
                "recorded, scoped approval" in problem
                for problem in gate.check("BT-014", "check-start", self.root)
            )
        )

    def test_pure_check_does_not_create_timing_records_or_mutate_evidence(self):
        evidence_before = (self.root / "workflow/evidence/BT-014.json").read_bytes()
        self.assertEqual([], gate.check("BT-014", "check-start", self.root))
        self.assertFalse(
            (self.root / "workflow/evidence/BT-014-gates.jsonl").exists()
        )
        self.assertEqual(
            evidence_before, (self.root / "workflow/evidence/BT-014.json").read_bytes()
        )

    def test_cli_records_pass_block_and_error_with_sanitized_gate_metadata(self):
        gate_log = self.root / "workflow/evidence/BT-014-gates.jsonl"
        original_check = gate.check

        def invoke_cli():
            with patch.object(gate, "ROOT", self.root), patch.object(
                gate, "check", side_effect=lambda task_id, stage: original_check(
                    task_id, stage, self.root
                )
            ), patch.object(sys, "argv", ["gate.py", "check-start", "BT-014"]):
                return gate.main()

        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(0, invoke_cli())

        self.set_phase("red")
        with contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(1, invoke_cli())

        # An unexpected evaluator exception is represented by its fixed outcome,
        # never by the exception message or traceback.
        with contextlib.redirect_stderr(io.StringIO()), patch.object(
            gate, "ROOT", self.root
        ), patch.object(
            gate,
            "check",
            side_effect=RuntimeError("private arbitrary sentinel"),
        ), patch.object(sys, "argv", ["gate.py", "check-start", "BT-014"]):
            try:
                status = gate.main()
            except RuntimeError as error:
                self.fail(f"gate CLI propagated evaluator failure: {error}")
            self.assertEqual(1, status)

        records = [json.loads(line) for line in gate_log.read_text().splitlines()]
        self.assertEqual(["pass", "block", "error"], [row["outcome"] for row in records])
        allowed = {"taskId", "gate", "startedAt", "finishedAt", "elapsedMs", "outcome"}
        for row in records:
            self.assertEqual(allowed, set(row))
            self.assertEqual("BT-014", row["taskId"])
            self.assertEqual("check-start", row["gate"])
            self.assertGreaterEqual(row["elapsedMs"], 0)
            self.assertTrue(row["startedAt"].endswith("Z"))
            self.assertTrue(row["finishedAt"].endswith("Z"))
        self.assertNotIn("private arbitrary sentinel", gate_log.read_text())
        self.assertNotIn("Expected", gate_log.read_text())

    def test_actual_bt005_legacy_done_evidence_gets_no_fabricated_phase_history(self):
        project_root = MODULE_PATH.parent.parent
        evidence_path = project_root / "workflow/evidence/BT-005.json"
        before = evidence_path.read_bytes()
        self.assertEqual([], gate.check("BT-005", "check-done", project_root))
        after = json.loads(evidence_path.read_text())
        self.assertNotIn("phaseTimings", after.get("workflow", {}))
        self.assertNotIn("phaseTimings", after)
        self.assertEqual(before, evidence_path.read_bytes())

    def test_bt006_shaped_active_evidence_keeps_unknown_history_absent(self):
        project_root = MODULE_PATH.parent.parent
        evidence = json.loads(
            (project_root / "workflow/evidence/BT-006.json").read_text()
        )
        evidence["workflow"] = {
            "phase": "claimed",
            "correctionCycles": 0,
            "maxCorrectionCycles": 2,
            "activeFailure": None,
            "blockedReason": "",
            "failureHistory": {},
        }
        queue = json.loads((project_root / "workflow/tasks.json").read_text())
        task = next(item for item in queue["tasks"] if item["id"] == "BT-006")
        self.assertEqual("claimed", task["state"])
        self.assertNotIn("phaseTimings", evidence.get("workflow", {}))
        self.assertEqual(
            [],
            gate.check(
                "BT-006", "check-start", project_root,
                queue_data=queue, evidence_override=evidence,
            ),
        )
        self.assertNotIn("phaseTimings", evidence.get("workflow", {}))


if __name__ == "__main__":
    unittest.main()
