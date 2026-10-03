import json
import re
import subprocess
import sys
import tomllib
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ABSORPTIONS = {"BT-008": "BT-007", "BT-013": "BT-011", "BT-018": "BT-017"}

def packet(task_id):
    return (ROOT / "docs/agent-tasks" / f"{task_id}.md").read_text()

class ConsolidationTests(unittest.TestCase):
    def setUp(self):
        queue = json.loads((ROOT / "workflow/tasks.json").read_text())
        self.tasks = {task["id"]: task for task in queue["tasks"]}

    def test_active_queue_has_unique_ids_and_no_absorbed_or_dangling_dependencies(self):
        ids = list(self.tasks)
        self.assertEqual(len(ids), len(set(ids)))
        self.assertTrue(set(ABSORPTIONS).isdisjoint(ids))
        for task in self.tasks.values():
            with self.subTest(task=task["id"]):
                self.assertTrue(set(task["dependsOn"]).issubset(ids))
                self.assertTrue(set(ABSORPTIONS).isdisjoint(task["dependsOn"]))

    def test_dependencies_are_redirected_exactly(self):
        expected = {
            "BT-007": ["BT-004", "BT-006"],
            "BT-009": ["BT-007"],
            "BT-011": ["BT-003", "BT-009", "BT-010", "BT-012"],
            "BT-016": ["BT-007", "BT-015"],
            "BT-017": ["BT-011", "BT-014", "BT-016"],
        }
        for task_id, dependencies in expected.items():
            self.assertEqual(dependencies, self.tasks[task_id]["dependsOn"])

    def test_only_pending_tasks_are_archived_and_active_history_is_preserved(self):
        self.assertEqual("blocked", self.tasks["BT-003"]["state"])
        self.assertEqual("claimed", self.tasks["BT-006"]["state"])
        archive_path = ROOT / "workflow/archived-tasks.json"
        self.assertTrue(archive_path.is_file(), "absorbed records need a non-executable archive")
        archive = json.loads(archive_path.read_text())
        archived = {item["id"]: item for item in archive["tasks"]}
        self.assertEqual(set(ABSORPTIONS), set(archived))
        for task_id, successor in ABSORPTIONS.items():
            self.assertEqual("pending", archived[task_id]["state"])
            self.assertEqual(successor, archived[task_id]["absorbedInto"])
            self.assertNotEqual("done", archived[task_id]["state"])

    def test_packets_link_archives_and_survivors_define_ordered_slices(self):
        for absorbed, successor in ABSORPTIONS.items():
            self.assertRegex(packet(absorbed),
                             rf"(?i)absorbed\s+into[^\n]*{re.escape(successor)}")
            survivor = packet(successor)
            self.assertRegex(survivor, r"(?i)ordered\s+(implementation\s+)?slices?")
            self.assertIn(absorbed, survivor)

    def test_room_slice_transfers_all_bt008_obligations_to_bt007(self):
        text = packet("BT-007").lower()
        for value in ("room", "migration", "reopen", "idempotent", "rollback",
                      "corruption", "upgrade", "no destructive migration fallback",
                      "connecteddebugandroidtest", "assembledebug",
                      "spec-001", "spec-002", "adr-002"):
            self.assertIn(value, text)

    def test_analytics_slice_transfers_all_bt013_obligations_to_bt011(self):
        text = packet("BT-011").lower()
        for value in ("spec-003", "spec-004", "spec-006", "chart proof",
                      "zero-filled buckets", "share rounding", "insight eligibility",
                      "insufficient data", "drill-down", "chart gestures",
                      "accessible", "connecteddebugandroidtest"):
            self.assertIn(value, text)

    def test_sync_readiness_is_the_final_bt017_slice(self):
        text = packet("BT-017").lower()
        for value in ("adr-005", "implemented repositories", "migration history",
                      "uuid", "versions", "timestamps", "tombstones",
                      "dependency scan", "without cloud dependency"):
            self.assertIn(value, text)
        self.assertGreater(text.rfind("bt-018"), text.find("required tests"))

    def test_gate_enforces_merged_required_specs_only_on_active_ids(self):
        text = (ROOT / "workflow/gate.py").read_text()
        self.assertRegex(text, r'"BT-007"\s*:\s*\[[^\]]*SPEC-001[^\]]*SPEC-002')
        self.assertRegex(text, r'"BT-011"\s*:\s*\[[^\]]*SPEC-003[^\]]*SPEC-004[^\]]*SPEC-006')
        for absorbed in ABSORPTIONS:
            self.assertNotRegex(text, rf'"{absorbed}"\s*:')

    def test_next_task_never_emits_an_archived_id(self):
        result = subprocess.run(
            [sys.executable, str(ROOT / "workflow/next_task.py")],
            cwd=ROOT, text=True, capture_output=True, check=False)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertTrue(set(ABSORPTIONS).isdisjoint(result.stdout.split()))

class WorkflowContractTests(unittest.TestCase):
    def test_context_is_concise_and_all_role_contracts_load_it(self):
        context_path = ROOT / "workflow/CONTEXT.md"
        self.assertTrue(context_path.is_file())
        context = context_path.read_text()
        self.assertLessEqual(len(context.splitlines()), 180)
        self.assertRegex(context, r"(?i)approved specifications?.*authoritative")
        self.assertRegex(context, r"(?i)red.*before.*implementation")
        for role_path in (ROOT / ".codex/agents").glob("*.toml"):
            tomllib.loads(role_path.read_text())
            self.assertIn("workflow/CONTEXT.md", role_path.read_text())

    def test_planner_and_reviewer_contracts_are_tiered_cache_first_and_scoped(self):
        planner = (ROOT / ".codex/agents/planner.toml").read_text()
        reviewer = (ROOT / ".codex/agents/reviewer.toml").read_text()
        self.assertRegex(planner, r"(?i)risk.*before.*work")
        self.assertRegex(planner, r"(?i)low.risk.*concise")
        self.assertRegex(planner, r"(?i)research.*cache.*first")
        self.assertRegex(planner, r"(?i)30.day")
        self.assertRegex(planner, r"(?i)external research.*(current|unknown)")
        self.assertRegex(reviewer, r"(?i)diff.*evidence")
        self.assertRegex(reviewer, r"(?i)risk.relevant")
        self.assertRegex(reviewer, r"(?i)independent.*(mandatory|required)")

    def test_model_contracts_are_explicit_without_conflating_api_prices(self):
        agents = {path.name: tomllib.loads(path.read_text())
                  for path in (ROOT / ".codex/agents").glob("*.toml")}
        self.assertEqual("gpt-6-sol", agents["planner.toml"].get("model"))
        self.assertEqual("gpt-6-luna", agents["builder.toml"].get("model"))
        combined = "\n".join((ROOT / ".codex/agents" / name).read_text()
                             for name in agents)
        self.assertRegex(combined, r"(?i)reasoning.*runner.*support|runner.*support.*reasoning")
        self.assertRegex(combined, r"(?i)api.*prices?.*not.*codex agent costs?")

    def test_parallel_policy_allows_safe_overlap_without_bypassing_dependencies(self):
        guidance = "\n".join((ROOT / path).read_text() for path in
                             ("AGENTS.md", "workflow/README.md", "workflow/CONTEXT.md")
                             if (ROOT / path).is_file())
        self.assertRegex(guidance, r"(?i)n\+1.*planning.*n.*review")
        self.assertRegex(guidance, r"(?i)disjoint.*file.*integration owner")
        self.assertRegex(guidance, r"(?i)dependen.*(chain|dependencies).*done|dependencies.*done")

if __name__ == "__main__":
    unittest.main()
