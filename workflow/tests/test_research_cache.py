import importlib.util
import tempfile
import unittest
from datetime import date
from pathlib import Path

MODULE_PATH = Path(__file__).resolve().parents[1] / "research" / "cache.py"

def load_module():
    if not MODULE_PATH.is_file():
        raise AssertionError("workflow/research/cache.py must validate cached research")
    spec = importlib.util.spec_from_file_location("bubble_research_cache", MODULE_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

class ResearchCacheTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name) / "android-room.md"

    def tearDown(self):
        self.temp.cleanup()

    def write_entry(self, topic="android-room", version="2.8.0",
                    accessed="2026-09-01",
                    sources=("https://developer.android.com/training/data-storage/room",)):
        source_lines = "\n".join(f"- {source}" for source in sources)
        self.path.write_text(
            "---\n"
            f"topic: {topic}\nversion: {version}\naccessed: {accessed}\n"
            f"sources:\n{source_lines}\n---\n\n# Findings\n\nCached facts.\n"
        )

    def current(self, topic="android-room", version="2.8.0",
                today=date(2026, 10, 1)):
        module = load_module()
        return module.is_current(self.path, topic=topic, version=version, today=today)

    def test_entry_is_current_through_day_30_and_stale_on_day_31(self):
        self.write_entry()
        self.assertTrue(self.current(today=date(2026, 10, 1)))
        self.assertFalse(self.current(today=date(2026, 10, 2)))

    def test_future_invalid_and_missing_access_dates_are_not_current(self):
        for accessed in ("2026-10-02", "not-a-date", ""):
            with self.subTest(accessed=accessed):
                self.write_entry(accessed=accessed)
                self.assertFalse(self.current(today=date(2026, 10, 1)))

    def test_missing_or_non_http_source_urls_are_not_current(self):
        for sources in ((), ("developer.android.com/room",)):
            with self.subTest(sources=sources):
                self.write_entry(sources=sources)
                self.assertFalse(self.current())

    def test_topic_or_exact_version_mismatch_is_not_current(self):
        self.write_entry()
        self.assertFalse(self.current(topic="kotlin-coroutines"))
        self.assertFalse(self.current(version="2.8.1"))
        self.assertTrue(self.current(version="2.8.0"))

    def test_missing_entry_is_not_current(self):
        self.assertFalse(self.current())

if __name__ == "__main__":
    unittest.main()
