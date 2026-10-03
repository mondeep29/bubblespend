"""BT-021-AC-03/07 supplemental delivery and approval contract tests."""

import importlib.util
import json
import re
import tempfile
import tomllib
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OVERLAY_SPEC = "docs/specs/SPEC-005-floating-shortcut.md"
module_spec = importlib.util.spec_from_file_location(
    "feature_delivery_gate", ROOT / "workflow/gate.py"
)
gate = importlib.util.module_from_spec(module_spec)
module_spec.loader.exec_module(gate)


def packet(task_id):
    return (ROOT / "docs/agent-tasks" / f"{task_id}.md").read_text()


class FeatureDeliveryTests(unittest.TestCase):
    def test_expense_spec_approval_can_proceed_without_unblocking_device_proof(self):
        """AC-07: only the expense/category approval dependency moves."""
        tasks = {
            task["id"]: task
            for task in json.loads((ROOT / "workflow/tasks.json").read_text())["tasks"]
        }
        self.assertEqual(["BT-002"], tasks["BT-004"]["dependsOn"])
        self.assertEqual("blocked", tasks["BT-003"]["state"])
        self.assertEqual("claimed", tasks["BT-006"]["state"])
        self.assertEqual(
            ["BT-003", "BT-004", "BT-009"], tasks["BT-014"]["dependsOn"]
        )

    def test_bt004_approves_only_expense_and_category_specs(self):
        """AC-07: overlay approval is delegated, not prematurely approved."""
        text = packet("BT-004")
        objective = re.search(r"\*\*Objective:\*\*([^\n]+)", text)
        self.assertIsNotNone(objective)
        self.assertIn("SPEC-001", objective.group(1))
        self.assertIn("SPEC-002", objective.group(1))
        self.assertNotIn("SPEC-005", objective.group(1))
        scope = re.search(r"\*\*Allowed scope:\*\*([^\n]+)", text)
        self.assertIsNotNone(scope)
        self.assertNotIn("SPEC-005", scope.group(1))
        self.assertRegex(text, r"(?is)SPEC-005.{0,200}BT-003|BT-003.{0,200}SPEC-005")

    def test_bt003_final_spec_phase_retains_entire_overlay_approval_obligation(self):
        """AC-07: retain the former BT-004 requirements in the final slice."""
        text = packet("BT-003")
        heading = re.search(
            r"(?im)^#{2,6}[^\n]*final[^\n]*(?:documentation|spec)[^\n]*$", text
        )
        self.assertIsNotNone(heading, "BT-003 needs an explicit final spec approval phase")
        final_phase = text[heading.end():]
        for obligation in (
            "SPEC-005", "D-01", "D-06", "template", "Given/When/Then",
            "error", "interruption", "privacy", "accessibility", "test plan",
            "open question", "Approved", "reviewer evidence", "verification",
        ):
            with self.subTest(obligation=obligation):
                self.assertIn(obligation.lower(), final_phase.lower())

    def test_overlay_spec_requires_independent_approval_after_all_proofs_before_done(self):
        """AC-07: the packet must explicitly order proof, approval, completion."""
        text = packet("BT-003")
        self.assertRegex(
            text,
            r"(?is)(?:after|only once|only when).{0,400}emulator.{0,200}real.device"
            r".{0,200}chart.{0,400}independent.{0,120}(?:review|approv)",
        )
        self.assertRegex(
            text, r"(?is)independent.{0,200}approv.{0,200}before.{0,100}(?:done|completion)"
        )

    def test_bt014_still_requires_approved_spec005_at_start(self):
        """AC-07: dependency simplification cannot bypass the executable spec gate."""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "docs/agent-tasks").mkdir(parents=True)
            (root / "docs/specs").mkdir(parents=True)
            (root / "docs/agent-tasks/BT-014.md").write_text("# Overlay packet\n")
            (root / "plan.md").write_text("# Persisted overlay plan\n")
            spec_path = root / OVERLAY_SPEC
            queue = {"tasks": [{
                "id": "BT-014", "state": "claimed", "dependsOn": [],
                "packet": "docs/agent-tasks/BT-014.md",
            }]}
            evidence = {
                "taskId": "BT-014", "kind": "behavior",
                "planner": {
                    "owner": "planner", "planPath": "plan.md", "risk": "elevated",
                    "operations": ["Implement overlay"], "ownedFiles": ["app/src/main"],
                    "rollback": "Restore owned adapter files",
                    "acceptanceIds": ["SPEC-005-AC-01"], "approvedSpecs": [OVERLAY_SPEC],
                },
                "securityPreflight": {
                    "reviewer": "independent-reviewer", "decision": "approved",
                    "scope": "Overlay adapter",
                },
            }

            def failures():
                return gate.check("BT-014", "check-start", root=root,
                                  queue_data=queue, evidence_override=evidence)

            spec_path.write_text("# Overlay\n\n**Status:** Approved\n")
            self.assertEqual([], failures(), "Control fixture must pass the start gate")
            evidence["planner"]["approvedSpecs"] = []
            self.assertIn(
                f"Required approved spec missing from handoff: {OVERLAY_SPEC}", failures()
            )
            evidence["planner"]["approvedSpecs"] = [OVERLAY_SPEC]
            spec_path.write_text("# Overlay\n\n**Status:** Draft\n")
            self.assertIn(f"Spec not approved: {OVERLAY_SPEC}", failures())

    def test_builder_instructions_reference_existing_coding_standards(self):
        """AC-03: the Builder receives the delivery contract."""
        builder = tomllib.loads((ROOT / ".codex/agents/builder.toml").read_text())
        self.assertIn("docs/coding-standards.md", builder["developer_instructions"])
        self.assertTrue((ROOT / "docs/coding-standards.md").is_file())


if __name__ == "__main__":
    unittest.main()
