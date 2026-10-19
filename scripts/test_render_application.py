"""Verify release rendering changes only image tags and preserves bootstrapped settings."""

import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parent.parent
SCRIPT = ROOT / "scripts/render-application.py"


class RenderApplicationTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.template = Path(temporary.name) / "application.json"
        self.descriptor = json.loads((ROOT / "application.json").read_text())
        self.template.write_text(json.dumps(self.descriptor))

    def render(self, *args):
        return subprocess.run(
            [sys.executable, str(SCRIPT), "--file", str(self.template), *args],
            cwd="/tmp", capture_output=True, text=True,
        )

    def test_release_rendering_changes_only_image_tag(self):
        for tag in ["v1.2.3", "v2.0.0-rc.1"]:
            for image in ["example/vareng:${VARENG_IMAGE_TAG}", "example/vareng:latest",
                          "example/vareng:1.0.0", "registry.example:5000/vareng@sha256:" + "a" * 64]:
                with self.subTest(tag=tag, image=image):
                    self.descriptor["spec"]["components"][0]["runtime"]["image"] = image
                    original = json.dumps(self.descriptor)
                    self.template.write_text(original)
                    result = self.render("--tag", tag)
                    self.assertEqual(result.returncode, 0, result.stderr)
                    expected = json.loads(original)
                    repository = "registry.example:5000/vareng" if image.startswith("registry.") else "example/vareng"
                    expected["spec"]["components"][0]["runtime"]["image"] = repository + ":" + tag[1:]
                    self.assertEqual(json.loads(result.stdout), expected)
                    self.assertEqual(self.template.read_text(), original)

    def test_invalid_or_missing_tag_produces_no_descriptor(self):
        for args in [[], ["--tag", "main"], ["--tag", "v1"], ["--tag", "v01.2.3"],
                     ["--tag", "v1.2.3-01"]]:
            with self.subTest(args=args):
                result = self.render(*args)
                self.assertEqual(result.returncode, 2)
                self.assertEqual(result.stdout, "")


if __name__ == "__main__":
    unittest.main()
