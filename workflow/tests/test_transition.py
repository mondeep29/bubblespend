import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


WORKFLOW_DIR = Path(__file__).resolve().parents[1]
PROJECT_ROOT = WORKFLOW_DIR.parent
sys.path.insert(0, str(WORKFLOW_DIR))
import transition


class TransitionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        (self.root / "workflow/evidence").mkdir(parents=True)
        (self.root / "docs/agent-tasks").mkdir(parents=True)
        (self.root / "docs/agent-tasks/BT-100.md").write_text("# packet")
        (self.root / "plan.md").write_text("# plan")
        self.queue = {
            "tasks": [
                {
                    "id": "BT-100",
                    "packet": "docs/agent-tasks/BT-100.md",
                    "dependsOn": [],
                    "state": "pending",
                }
            ]
        }
        self.evidence = {
            "taskId": "BT-100",
            "kind": "behavior",
            "workflow": {
                "phase": "pending",
                "correctionCycles": 0,
                "maxCorrectionCycles": 2,
                "activeFailure": None,
                "blockedReason": "",
            },
            "planner": {
                "owner": "planner",
                "planPath": "plan.md",
                "risk": "low",
                "operations": ["Implement the approved plan"],
                "ownedFiles": ["src/main"],
                "rollback": "Revert the scoped implementation",
                "acceptanceIds": ["BT-100-AC-01"],
                "approvedSpecs": [],
            },
            "securityPreflight": {
                "reviewer": "",
                "decision": "pending",
                "scope": "",
                "findings": [],
            },
            "tester": {
                "owner": "tester",
                "red": {
                    "command": "python3 -m unittest",
                    "observedFailure": "Expected workflow behavior is absent",
                    "acceptanceIds": ["BT-100-AC-01"],
                },
                "verification": [
                    {"command": "python3 -m unittest", "result": "passed"}
                ],
                "status": "passed",
            },
            "builder": {
                "owner": "builder",
                "status": "complete",
                "revision": 1,
                "changedFiles": ["src/main/Feature.kt"],
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
        (self.root / "workflow/evidence/BT-100.json").write_text(
            json.dumps(self.evidence)
        )

    def reload(self):
        self.queue = json.loads((self.root / "workflow/tasks.json").read_text())
        self.evidence = json.loads(
            (self.root / "workflow/evidence/BT-100.json").read_text()
        )

    def set_phase(self, phase):
        self.queue["tasks"][0]["state"] = phase
        self.evidence["workflow"]["phase"] = phase
        self.evidence["workflow"]["phaseTimings"] = [
            {"phase": phase, "startedAt": None, "finishedAt": None, "elapsedMs": None}
        ]
        self.save()

    def assert_phase(self, expected):
        self.reload()
        self.assertEqual(expected, self.queue["tasks"][0]["state"])
        self.assertEqual(expected, self.evidence["workflow"]["phase"])

    def phase_visits(self):
        self.reload()
        return self.evidence["workflow"].get("phaseTimings", [])

    def gate_records(self):
        path = self.root / "workflow/evidence/BT-100-gates.jsonl"
        if not path.exists():
            return []
        return [json.loads(line) for line in path.read_text().splitlines()]

    def load_actual_bt006_legacy_fixture(self):
        """Copy the checked-in legacy active evidence shape into this temp project."""
        source_evidence = PROJECT_ROOT / "workflow/evidence/BT-006.json"
        source_plan = PROJECT_ROOT / "workflow/evidence/BT-006-plan.md"
        source_packet = PROJECT_ROOT / "docs/agent-tasks/BT-006.md"
        evidence = json.loads(source_evidence.read_text())
        self.assertNotIn("workflow", evidence)

        (self.root / "docs/agent-tasks/BT-006.md").write_bytes(
            source_packet.read_bytes()
        )
        (self.root / "workflow/evidence/BT-006-plan.md").write_bytes(
            source_plan.read_bytes()
        )
        (self.root / "workflow/evidence/BT-006.json").write_text(
            json.dumps(evidence)
        )
        queue = {
            "tasks": [
                {
                    "id": "BT-005",
                    "packet": "docs/agent-tasks/BT-005.md",
                    "dependsOn": [],
                    "state": "done",
                },
                {
                    "id": "BT-006",
                    "packet": "docs/agent-tasks/BT-006.md",
                    "dependsOn": ["BT-005"],
                    "state": "claimed",
                },
            ]
        }
        (self.root / "workflow/tasks.json").write_text(json.dumps(queue))
        return evidence

    def assert_sanitized_gate_records(self, records):
        allowed = {"taskId", "gate", "startedAt", "finishedAt", "elapsedMs", "outcome"}
        for record in records:
            self.assertEqual(allowed, set(record))
            self.assertEqual("BT-100", record["taskId"])
            self.assertGreaterEqual(record["elapsedMs"], 0)
            self.assertTrue(record["startedAt"].endswith("Z"))
            self.assertTrue(record["finishedAt"].endswith("Z"))

    def set_failure(self, owner, classification, key):
        self.evidence["workflow"]["activeFailure"] = {
            "owner": owner,
            "classification": classification,
            "key": key,
            "status": "open",
        }
        self.save()

    def test_behavior_task_follows_complete_happy_path(self):
        expected_steps = (
            ("claim", "claimed"),
            ("red", "red"),
            ("implement", "implementing"),
            ("verify", "verifying"),
            ("review", "review"),
            ("done", "done"),
        )
        for action, expected in expected_steps:
            with self.subTest(action=action):
                self.assertEqual(
                    expected, transition.transition("BT-100", action, self.root)
                )
                self.assert_phase(expected)

    def test_happy_path_records_ordered_phase_visits_and_all_transition_gate_calls(self):
        for action in ("claim", "red", "implement", "verify", "review", "done"):
            transition.transition("BT-100", action, self.root)

        visits = self.phase_visits()
        self.assertEqual(
            ["pending", "claimed", "red", "implementing", "verifying", "review", "done"],
            [visit["phase"] for visit in visits],
        )
        self.assertIsNone(visits[0]["startedAt"])
        self.assertIsNotNone(visits[0]["finishedAt"])
        self.assertIsNotNone(visits[1]["startedAt"])
        self.assertIsNotNone(visits[-2]["finishedAt"])
        self.assertIsNone(visits[-1]["finishedAt"])
        self.assertTrue(all(visit["elapsedMs"] is None or visit["elapsedMs"] >= 0 for visit in visits))

        records = self.gate_records()
        self.assertEqual(
            ["check-start", "check-build", "check-review", "check-done"],
            [record["gate"] for record in records],
        )
        self.assertEqual(["pass"] * 4, [record["outcome"] for record in records])
        self.assert_sanitized_gate_records(records)

    def test_documentation_shortcut_records_only_phases_it_visits(self):
        self.evidence["kind"] = "documentation"
        self.save()
        transition.transition("BT-100", "claim", self.root)
        transition.transition("BT-100", "verify", self.root)

        visits = self.phase_visits()
        self.assertEqual(["pending", "claimed", "verifying"], [v["phase"] for v in visits])
        self.assertEqual(["check-start"], [row["gate"] for row in self.gate_records()])

    def test_rework_appends_a_new_phase_visit(self):
        for action in ("claim", "red", "implement", "verify"):
            transition.transition("BT-100", action, self.root)
        self.reload()
        self.set_failure("builder", "implementation", "verify:unit:stable-test")
        transition.transition("BT-100", "rework", self.root)

        visits = self.phase_visits()
        self.assertEqual(
            ["pending", "claimed", "red", "implementing", "verifying", "implementing"],
            [visit["phase"] for visit in visits],
        )
        self.assertIsNotNone(visits[-2]["finishedAt"])
        self.assertIsNotNone(visits[-1]["startedAt"])

    def test_failed_gate_keeps_phase_history_unchanged_but_records_block_attempt(self):
        for action in ("claim", "red"):
            transition.transition("BT-100", action, self.root)
        before = self.phase_visits()
        self.evidence["tester"]["red"]["observedFailure"] = ""
        self.save()

        with self.assertRaises(ValueError):
            transition.transition("BT-100", "implement", self.root)

        self.assertEqual(before, self.phase_visits())
        records = self.gate_records()
        self.assertTrue(records, "a rejected production gate evaluation must be logged")
        self.assertEqual("block", records[-1]["outcome"])
        self.assertEqual("check-build", records[-1]["gate"])
        self.assert_sanitized_gate_records(records)

    def test_failed_transition_gate_exception_records_error_without_phase_visit(self):
        with patch.object(
            transition, "check_candidate", side_effect=RuntimeError("private gate details")
        ):
            with self.assertRaisesRegex(RuntimeError, "private gate details"):
                transition.transition("BT-100", "claim", self.root)
        self.assert_phase("pending")
        self.assertEqual([], self.phase_visits())
        records = self.gate_records()
        self.assertTrue(records, "an errored production gate evaluation must be logged")
        self.assertEqual("error", records[-1]["outcome"])
        self.assertEqual("check-start", records[-1]["gate"])
        self.assert_sanitized_gate_records(records)
        self.assertNotIn("private gate details", json.dumps(records))

    def test_forbidden_skips_and_done_before_review_leave_state_unchanged(self):
        cases = (
            ("claimed", "implement"),
            ("claimed", "verify"),
            ("red", "review"),
            ("implementing", "review"),
            ("claimed", "done"),
            ("verifying", "done"),
        )
        for phase, action in cases:
            with self.subTest(phase=phase, action=action):
                self.set_phase(phase)
                with self.assertRaises(ValueError):
                    transition.transition("BT-100", action, self.root)
                self.assert_phase(phase)

    def test_failed_build_gate_leaves_queue_and_evidence_state_unchanged(self):
        self.set_phase("red")
        self.evidence["tester"]["red"]["observedFailure"] = ""
        self.save()
        with self.assertRaises(ValueError):
            transition.transition("BT-100", "implement", self.root)
        self.assert_phase("red")

    def test_second_state_file_replace_failure_rolls_back_both_files(self):
        self.set_phase("claimed")
        queue_before = (self.root / "workflow/tasks.json").read_text()
        evidence_path = self.root / "workflow/evidence/BT-100.json"
        evidence_before = evidence_path.read_text()
        real_replace = Path.replace

        def fail_queue_replace(path, target):
            if path.name == "tasks.json.tmp":
                raise OSError("simulated queue replacement failure")
            return real_replace(path, target)

        with patch.object(Path, "replace", new=fail_queue_replace):
            with self.assertRaisesRegex(OSError, "simulated queue replacement failure"):
                transition.transition("BT-100", "red", self.root)

        self.assertEqual(queue_before, (self.root / "workflow/tasks.json").read_text())
        self.assertEqual(evidence_before, evidence_path.read_text())

    def test_failed_second_replace_preserves_existing_phase_history(self):
        # First create one committed visit, then fail the queue replacement for
        # the next transition after the new evidence file has been replaced.
        self.set_phase("claimed")
        self.evidence["workflow"]["phaseTimings"] = [
            {"phase": "pending", "startedAt": None, "finishedAt": None, "elapsedMs": None},
            {"phase": "claimed", "startedAt": "2026-09-29T00:00:00Z", "finishedAt": None, "elapsedMs": None},
        ]
        self.save()
        evidence_path = self.root / "workflow/evidence/BT-100.json"
        history_before = json.loads(evidence_path.read_text())["workflow"]["phaseTimings"]
        real_replace = Path.replace

        def fail_queue_replace(path, target):
            if path.name == "tasks.json.tmp":
                raise OSError("simulated second state replacement failure")
            return real_replace(path, target)

        with patch.object(Path, "replace", new=fail_queue_replace):
            with self.assertRaisesRegex(OSError, "second state replacement failure"):
                transition.transition("BT-100", "red", self.root)

        persisted = json.loads(evidence_path.read_text())
        self.assertEqual(history_before, persisted["workflow"]["phaseTimings"])
        self.assertEqual("claimed", persisted["workflow"]["phase"])

    def test_only_documentation_and_proof_can_skip_red_and_implementation(self):
        for kind in ("documentation", "proof"):
            with self.subTest(kind=kind):
                self.set_phase("claimed")
                self.evidence["kind"] = kind
                self.save()
                self.assertEqual(
                    "verifying",
                    transition.transition("BT-100", "verify", self.root),
                )
                self.assert_phase("verifying")

        for kind in ("behavior", "foundation"):
            with self.subTest(kind=kind):
                self.set_phase("claimed")
                self.evidence["kind"] = kind
                self.save()
                with self.assertRaises(ValueError):
                    transition.transition("BT-100", "verify", self.root)
                self.assert_phase("claimed")

    def test_rework_routes_by_failure_owner_and_classification(self):
        cases = (
            ("builder", "implementation", "implementing"),
            ("tester", "test", "red"),
            ("coordinator", "specification", "blocked"),
            ("coordinator", "environment", "blocked"),
        )
        for owner, classification, expected in cases:
            with self.subTest(owner=owner, classification=classification):
                self.set_phase("verifying")
                self.set_failure(
                    owner,
                    classification,
                    f"verify:{classification}:stable-identity",
                )
                self.assertEqual(
                    expected, transition.transition("BT-100", "rework", self.root)
                )
                self.assert_phase(expected)

    def test_first_repair_is_allowed_but_second_identical_unresolved_failure_blocks(self):
        stable_key = "verify:unit:FeatureTest.test_visible_behavior"
        self.set_phase("verifying")
        self.set_failure("builder", "implementation", stable_key)
        self.assertEqual(
            "implementing", transition.transition("BT-100", "rework", self.root)
        )
        self.assert_phase("implementing")

        self.set_phase("verifying")
        self.set_failure("builder", "implementation", stable_key)
        self.assertEqual(
            "blocked", transition.transition("BT-100", "rework", self.root)
        )
        self.assert_phase("blocked")

    def test_different_failure_keys_do_not_share_the_retry_budget(self):
        self.set_phase("verifying")
        self.set_failure("builder", "implementation", "verify:unit:first")
        self.assertEqual(
            "implementing", transition.transition("BT-100", "rework", self.root)
        )

        self.set_phase("verifying")
        self.set_failure("builder", "implementation", "verify:unit:second")
        self.assertEqual(
            "implementing", transition.transition("BT-100", "rework", self.root)
        )
        self.assert_phase("implementing")

    def test_same_failure_key_recurring_after_passed_verify_and_review_is_repairable(self):
        stable_key = "review:FeatureTest.test_visible_behavior"
        self.set_phase("verifying")
        self.set_failure("builder", "implementation", stable_key)
        self.assertEqual(
            "implementing", transition.transition("BT-100", "rework", self.root)
        )

        self.assertEqual("verifying", transition.transition("BT-100", "verify", self.root))
        self.assertEqual("review", transition.transition("BT-100", "review", self.root))

        self.reload()
        self.set_failure("builder", "implementation", stable_key)
        self.assertEqual(
            "implementing", transition.transition("BT-100", "rework", self.root)
        )
        self.assert_phase("implementing")

    def test_rework_requires_complete_open_failure_classification(self):
        incomplete_failures = (
            None,
            {"owner": "builder", "classification": "implementation", "status": "open"},
            {"owner": "builder", "key": "verify:unit:x", "status": "open"},
            {
                "classification": "implementation",
                "key": "verify:unit:x",
                "status": "open",
            },
            {
                "owner": "builder",
                "classification": "implementation",
                "key": "verify:unit:x",
                "status": "resolved",
            },
        )
        for failure in incomplete_failures:
            with self.subTest(failure=failure):
                self.set_phase("verifying")
                self.evidence["workflow"]["activeFailure"] = failure
                self.save()
                with self.assertRaises(ValueError):
                    transition.transition("BT-100", "rework", self.root)
                self.assert_phase("verifying")

    def test_claim_transition_checks_dependencies(self):
        self.queue["tasks"][0]["dependsOn"] = ["BT-001"]
        self.queue["tasks"].append(
            {
                "id": "BT-001",
                "packet": "docs/agent-tasks/BT-100.md",
                "dependsOn": [],
                "state": "pending",
            }
        )
        self.save()
        with self.assertRaisesRegex(ValueError, "Unfinished dependencies"):
            transition.transition("BT-100", "claim", self.root)
        self.assert_phase("pending")

    def test_actual_bt006_legacy_active_evidence_passes_check_start_without_workflow(self):
        evidence = self.load_actual_bt006_legacy_fixture()

        self.assertEqual([], transition.check("BT-006", "check-start", self.root))
        self.assertNotIn("workflow", evidence)
        persisted = json.loads(
            (self.root / "workflow/evidence/BT-006.json").read_text()
        )
        self.assertNotIn("workflow", persisted)

    def test_actual_bt006_legacy_transition_keeps_unknown_start_time_null(self):
        evidence = self.load_actual_bt006_legacy_fixture()

        self.assertEqual("red", transition.transition("BT-006", "red", self.root))

        persisted = json.loads(
            (self.root / "workflow/evidence/BT-006.json").read_text()
        )
        visits = persisted["workflow"]["phaseTimings"]
        self.assertEqual("claimed", visits[0]["phase"])
        self.assertIsNone(visits[0]["startedAt"])
        self.assertIsNone(visits[0]["elapsedMs"])
        self.assertIsNotNone(visits[0]["finishedAt"])
        self.assertEqual("red", visits[1]["phase"])
        self.assertIsNotNone(visits[1]["startedAt"])
        self.assertIsNone(visits[1]["finishedAt"])
        self.assertNotIn("workflow", evidence)


if __name__ == "__main__":
    unittest.main()
