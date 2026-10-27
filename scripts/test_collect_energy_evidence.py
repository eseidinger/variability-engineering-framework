import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("collector", Path(__file__).with_name("collect-energy-evidence.py"))
collector = importlib.util.module_from_spec(spec)
spec.loader.exec_module(collector)


class EvidenceTests(unittest.TestCase):
    def test_archive_is_reproducible_and_rejects_paths_outside_snapshot(self):
        self.assertEqual(collector.source_archive({"b": b"two", "a": b"one"}),
                         collector.source_archive({"a": b"one", "b": b"two"}))
        with self.assertRaises(ValueError):
            collector.source_archive({"../secret": b"no"})

    def test_reports_exclude_raw_test_output_and_preserve_failed_and_skipped_counts(self):
        with tempfile.TemporaryDirectory() as temporary:
            folder = Path(temporary)
            (folder / "TEST-one.xml").write_text(
                '<testsuite name="BoundaryTest" tests="3" failures="1" errors="0" skipped="1">'
                '<system-out>secret synthetic marker</system-out></testsuite>')
            report = collector.test_summary(folder)
            self.assertEqual((report["tests"], report["failures"], report["skipped"]), (3, 1, 1))
            self.assertNotIn("secret", str(report))
            output = folder / "sanitized"
            collector.sanitize_api_reports(folder, output)
            self.assertEqual(collector.test_summary(output), report)
            self.assertNotIn("secret", (output / "TEST-0.xml").read_text())

    def test_missing_and_inconsistent_reports_are_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            folder = Path(temporary)
            with self.assertRaises(ValueError):
                collector.test_summary(folder)
            (folder / "TEST-one.xml").write_text('<testsuite name="Bad" tests="1" failures="2"/>')
            with self.assertRaises(ValueError):
                collector.test_summary(folder)


if __name__ == "__main__":
    unittest.main()
