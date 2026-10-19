"""Verify latest-only publishing and that failed builds are never pushed."""

from contextlib import redirect_stdout
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

from platform_bootstrap import BootstrapError


SPEC = importlib.util.spec_from_file_location("bootstrap_image", Path(__file__).with_name("bootstrap-image.py"))
bootstrap = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(bootstrap)


class BootstrapImageTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.path = Path(directory.name) / "application.json"

    def descriptor(self, image):
        self.path.write_text(json.dumps({"spec": {"components": [
            {"name": "web", "runtime": {"image": image}},
        ]}}))

    def test_builds_combined_image_then_pushes_latest_without_editing_descriptor(self):
        for source in ["example/vareng:1.2.3", "example/vareng:${VARENG_IMAGE_TAG}",
                       "registry.example:5000/vareng@sha256:" + "a" * 64]:
            with self.subTest(source=source):
                self.descriptor(source)
                original = self.path.read_text()
                output = io.StringIO()
                argv = ["bootstrap-image.py", "--file", str(self.path)]
                with patch.object(sys, "argv", argv), \
                        patch.object(bootstrap.subprocess, "run", return_value=Mock(returncode=0)) as run, redirect_stdout(output):
                    bootstrap.main()
                image = ("registry.example:5000/vareng" if source.startswith("registry.") else "example/vareng") + ":latest"
                build, push = [call.args[0] for call in run.call_args_list]
                self.assertEqual(build, ["docker", "build", "--file", str(bootstrap.ROOT / "apps/prototype/deployment/Dockerfile"), "--tag", image, str(bootstrap.ROOT)])
                self.assertEqual(push, ["docker", "push", image])
                self.assertEqual(output.getvalue(), image + "\n")
                self.assertEqual(self.path.read_text(), original)

    def test_build_or_push_failure_does_not_report_success(self):
        self.descriptor("example/vareng:latest")
        for codes in [[1], [0, 1]]:
            with self.subTest(codes=codes):
                output = io.StringIO()
                with patch.object(sys, "argv", ["bootstrap-image.py", "--file", str(self.path)]), \
                        patch.object(bootstrap.subprocess, "run", side_effect=[Mock(returncode=code) for code in codes]) as run, redirect_stdout(output):
                    with self.assertRaisesRegex(BootstrapError, "failed"):
                        bootstrap.main()
                self.assertEqual(run.call_count, len(codes))
                self.assertEqual(output.getvalue(), "")


if __name__ == "__main__":
    unittest.main()
