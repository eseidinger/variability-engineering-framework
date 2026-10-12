"""Exercise integrity failures independently of network and the upstream runner."""

import hashlib
import importlib.util
import io
import json
import tarfile
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location(
    "validate_energy_contract_kit", Path(__file__).with_name("validate-energy-contract-kit.py")
)
validator = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(validator)


class ContractKitIntegrityTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.archive = self.root / "kit.tar.gz"

    def fixture(self, version="0.4.0"):
        files = {
            "contract-kit/VERSION": version,
            "contract-kit/schemas/example.json": "{}",
            "LICENSE": "release licensing",
            "NOTICE": "source attribution",
            "LICENSES/Apache-2.0.txt": "software license",
            "LICENSES/CC-BY-4.0.txt": "fixture license",
        }
        with tarfile.open(self.archive, "w:gz") as package:
            for relative, content in files.items():
                payload = content.encode()
                entry = tarfile.TarInfo(f"contract-kit-v0.4.0/{relative}")
                entry.size = len(payload)
                package.addfile(entry, io.BytesIO(payload))
        return {
            "version": "0.4.0",
            "archiveSha256": hashlib.sha256(self.archive.read_bytes()).hexdigest(),
            "archiveUrl": "https://example.invalid/kit.tar.gz",
        }

    def test_modified_archive_is_rejected_before_extraction(self):
        lock = self.fixture()
        self.archive.write_bytes(self.archive.read_bytes() + b"tampered")
        output = self.root / "output"
        with self.assertRaisesRegex(ValueError, "checksum mismatch"):
            validator.extract_kit(self.archive, lock, output)
        self.assertFalse(output.exists())

    def test_cached_archive_is_reverified_without_network(self):
        lock = self.fixture()
        cache = self.root / "cache"
        cached = cache / lock["archiveSha256"] / "contract-kit-v0.4.0.tar.gz"
        cached.parent.mkdir(parents=True)
        cached.write_bytes(self.archive.read_bytes())
        with patch.object(validator.urllib.request, "urlopen") as request:
            self.assertEqual(validator.obtain_archive(lock, cache, None), cached)
            cached.write_bytes(b"corrupt cache")
            with self.assertRaisesRegex(ValueError, "checksum mismatch"):
                validator.obtain_archive(lock, cache, None)
            request.assert_not_called()

    def test_failed_download_is_not_cached(self):
        lock = self.fixture()
        cache = self.root / "cache"
        with patch.object(validator.urllib.request, "urlopen", return_value=io.BytesIO(b"wrong release")):
            with self.assertRaisesRegex(ValueError, "checksum mismatch"):
                validator.obtain_archive(lock, cache, None)
        self.assertEqual(list(cache.rglob("*.tar.gz")), [])

    def test_offline_extraction_preserves_attribution_and_fixtures(self):
        lock = self.fixture()
        archive = validator.obtain_archive(lock, self.root / "cache", self.archive)
        output = self.root / "output"
        kit = validator.extract_kit(archive, lock, output)
        self.assertEqual(json.loads((kit / "schemas/example.json").read_text()), {})
        self.assertEqual((kit.parent / "NOTICE").read_text(), "source attribution")
        self.assertTrue((kit.parent / "LICENSES/CC-BY-4.0.txt").is_file())
        with self.assertRaisesRegex(ValueError, "already exists"):
            validator.extract_kit(archive, lock, output)

    def test_wrong_embedded_version_does_not_publish_extraction(self):
        lock = self.fixture(version="0.3.0")
        output = self.root / "output"
        with self.assertRaisesRegex(ValueError, "version does not match"):
            validator.extract_kit(self.archive, lock, output)
        self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
