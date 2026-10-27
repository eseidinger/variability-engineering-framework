"""Verify the pinned release and run its frozen, repository-neutral test suite."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tarfile
import tempfile
import urllib.request
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
LOCK_PATH = REPOSITORY_ROOT / "contracts/energy/contract-kit.lock.json"
DEFAULT_CACHE = REPOSITORY_ROOT / ".cache/energy-contract-kit"


def verify_archive(archive: Path, expected_digest: str) -> None:
    with archive.open("rb") as source:
        actual_digest = hashlib.file_digest(source, "sha256").hexdigest()
    if actual_digest != expected_digest:
        raise ValueError(
            f"Archive checksum mismatch for {archive}: "
            f"expected {expected_digest}, got {actual_digest}"
        )


def obtain_archive(lock: dict, cache_dir: Path, supplied_archive: Path | None) -> Path:
    if supplied_archive is not None:
        verify_archive(supplied_archive, lock["archiveSha256"])
        return supplied_archive

    archive_dir = cache_dir / lock["archiveSha256"]
    archive_dir.mkdir(parents=True, exist_ok=True)
    archive = archive_dir / f"contract-kit-v{lock['version']}.tar.gz"
    if not archive.exists():
        # Publish to the cache only after the complete download is verified.
        with tempfile.TemporaryDirectory(dir=archive_dir) as temporary:
            download = Path(temporary) / "download.tar.gz"
            with urllib.request.urlopen(lock["archiveUrl"], timeout=60) as response:
                with download.open("wb") as destination:
                    shutil.copyfileobj(response, destination)
            verify_archive(download, lock["archiveSha256"])
            download.replace(archive)

    # A cache hit must never bypass integrity verification.
    verify_archive(archive, lock["archiveSha256"])
    return archive


def extract_kit(archive: Path, lock: dict, output_dir: Path) -> Path:
    # Verify again immediately before extraction, including offline archives.
    verify_archive(archive, lock["archiveSha256"])
    if output_dir.exists():
        raise ValueError(f"Output directory already exists: {output_dir}")
    output_dir.parent.mkdir(parents=True, exist_ok=True)
    release_name = f"contract-kit-v{lock['version']}"
    with tempfile.TemporaryDirectory(dir=output_dir.parent) as temporary:
        staging = Path(temporary) / "release"
        staging.mkdir()
        with tarfile.open(archive, "r:gz") as package:
            package.extractall(staging, filter="data")
        release = staging / release_name
        kit = release / "contract-kit"
        if (kit / "VERSION").read_text(encoding="utf-8").strip() != lock["version"]:
            raise ValueError("Extracted kit version does not match the lock")
        for relative in ("LICENSE", "NOTICE", "LICENSES/Apache-2.0.txt", "LICENSES/CC-BY-4.0.txt"):
            if not (release / relative).is_file():
                raise ValueError(f"Release is missing attribution: {relative}")
        staging.replace(output_dir)
    return output_dir / release_name / "contract-kit"


def validate_kit(kit: Path, cache_dir: Path) -> None:
    environment = os.environ.copy()
    environment.setdefault("UV_CACHE_DIR", str(cache_dir / "uv"))
    environment["UV_PROJECT_ENVIRONMENT"] = str(kit / ".venv")
    subprocess.run(
        ["uv", "run", "--frozen", "--python", sys.executable, "pytest", "-q"],
        cwd=kit,
        env=environment,
        check=True,
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path, help="Use an offline archive; its checksum is still verified")
    parser.add_argument("--cache-dir", type=Path, default=DEFAULT_CACHE)
    parser.add_argument("--output-dir", type=Path, help="Keep the extracted release in a new directory")
    parser.add_argument("--prepare-only", action="store_true", help="Prepare fixtures without running the upstream suite")
    args = parser.parse_args()
    if sys.version_info < (3, 12):
        parser.error("Python 3.12 or newer is required")
    if args.prepare_only and args.output_dir is None:
        parser.error("--prepare-only requires --output-dir")
    if not args.prepare_only and shutil.which("uv") is None:
        parser.error("uv is required to run the frozen contract-kit test suite")

    lock = json.loads(LOCK_PATH.read_text(encoding="utf-8"))
    cache_dir = args.cache_dir.resolve()
    archive = obtain_archive(lock, cache_dir, args.archive)
    print(f"Verified contract kit {lock['version']}: {lock['archiveSha256']}", flush=True)

    def prepare_and_validate(output_dir: Path) -> None:
        kit = extract_kit(archive, lock, output_dir)
        runtime = REPOSITORY_ROOT / "apps/prototype/api/src/main/resources/energy-contract"
        expected = sorted(path.name for path in (kit / "schemas").glob("*.schema.json"))
        if json.loads((runtime / "index.json").read_text()) != expected:
            raise ValueError("Packaged schema index differs from the pinned kit")
        if sorted(path.name for path in runtime.glob("*.schema.json")) != expected:
            raise ValueError("Packaged schema files differ from the pinned kit")
        for name in expected:
            if (runtime / name).read_bytes() != (kit / "schemas" / name).read_bytes():
                raise ValueError(f"Packaged schema differs from the pinned kit: {name}")
        print(f"ENERGY_CONTRACT_KIT_DIRECTORY={kit}", flush=True)
        if not args.prepare_only:
            validate_kit(kit, cache_dir)

    if args.output_dir is not None:
        prepare_and_validate(args.output_dir.resolve())
    else:
        with tempfile.TemporaryDirectory(prefix="vef-energy-contract-") as temporary:
            prepare_and_validate(Path(temporary) / "release")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, tarfile.TarError, subprocess.CalledProcessError) as error:
        print(f"Contract-kit validation failed: {error}", file=sys.stderr)
        sys.exit(1)
