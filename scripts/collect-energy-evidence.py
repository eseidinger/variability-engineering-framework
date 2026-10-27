"""Collect sanitized local checks and a reproducible model-source archive, never a release approval."""

import argparse
from datetime import datetime, timezone
import gzip
import hashlib
import io
import json
from pathlib import Path
import subprocess
import tarfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def digest(data):
    return hashlib.sha256(data).hexdigest()


def test_summary(folder):
    reports = sorted(folder.glob("TEST-*.xml"))
    if not reports:
        raise ValueError("No API test reports found")
    suites = []
    for path in reports:
        root = ET.parse(path).getroot()
        counts = {key: int(root.get(key, "0")) for key in ("tests", "failures", "errors", "skipped")}
        if any(value < 0 for value in counts.values()) or sum(counts[key] for key in ("failures", "errors", "skipped")) > counts["tests"]:
            raise ValueError("Invalid test totals")
        suites.append({"name": root.attrib["name"], **counts})
    return {"suites": suites, **{key: sum(s[key] for s in suites) for key in ("tests", "failures", "errors", "skipped")}}


def source_archive(files):
    """Fixed metadata produces identical bytes for identical named source files."""
    raw = io.BytesIO()
    with tarfile.open(fileobj=raw, mode="w") as archive:
        for name, content in sorted(files.items()):
            if Path(name).is_absolute() or ".." in Path(name).parts:
                raise ValueError("Unsafe source path")
            entry = tarfile.TarInfo(name)
            entry.size = len(content)
            entry.mode = 0o644
            archive.addfile(entry, io.BytesIO(content))
    return gzip.compress(raw.getvalue(), mtime=0)


def sanitize_api_reports(folder, output):
    summary = test_summary(folder)
    output.mkdir(parents=True, exist_ok=False)
    for index, suite in enumerate(summary["suites"]):
        node = ET.Element("testsuite", {key: str(value) for key, value in suite.items()})
        ET.ElementTree(node).write(output / f"TEST-{index}.xml", encoding="utf-8", xml_declaration=True)
    return summary


def collect(output, api_reports, browser_report, runtime_report=None):
    if output.exists():
        raise ValueError("Evidence destination already exists; use a new directory")
    api = test_summary(api_reports)
    browser = json.loads(browser_report.read_text())
    if browser.get("environment") != "local-production-bundle-with-mocked-http" or not browser.get("checks") or browser.get("completed") is not True:
        raise ValueError("Expected the local browser verification report")
    checks = []
    for item in browser["checks"]:
        if item.get("result") not in ("PASS", "FAIL") or item.get("width") not in (390, 1280):
            raise ValueError("Invalid browser check")
        checks.append({"name": item["name"], "width": item["width"], "result": item["result"]})
    paths = subprocess.check_output(["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"], cwd=ROOT).decode().split("\0")
    selected = {}
    for name in paths:
        if name.startswith("apps/prototype/api/src/main/") or name in (
            "apps/prototype/api/pom.xml", "apps/prototype/api/checkstyle.xml",
            "contracts/energy/contract-kit.lock.json", "LICENSE", "NOTICE",
        ):
            path = ROOT / name
            if path.is_symlink():
                raise ValueError("Source snapshots must not follow symlinks")
            selected[name] = path.read_bytes()
    archive = source_archive(selected)
    lock = json.loads((ROOT / "contracts/energy/contract-kit.lock.json").read_text())
    manifest = {
        "schemaVersion": 1,
        "collectedAt": datetime.now(timezone.utc).isoformat(),
        "scope": "local-automated-checks",
        "releaseAccepted": False,
        "humanReviewRequired": True,
        "sourceRevision": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
        "workingTreeDirty": bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT)),
        "sourceArchiveSha256": digest(archive),
        "sourceFiles": {name: digest(content) for name, content in sorted(selected.items())},
        "contractKitVersion": lock["version"],
        "contractKitArchiveSha256": lock["archiveSha256"],
        "api": api,
        "browser": {"environment": browser["environment"], "checks": checks},
        "inputReportSha256": {
            **{path.name: digest(path.read_bytes()) for path in sorted(api_reports.glob("TEST-*.xml"))},
            "energy-browser.json": digest(browser_report.read_bytes()),
        },
        "deploymentEvidence": "not collected",
        "result": "FAIL" if api["failures"] or api["errors"] or any(c["result"] != "PASS" for c in checks) else "PASS",
    }
    if runtime_report is not None:
        runtime = json.loads(runtime_report.read_text())
        if runtime.get("scope") != "local-pure-optimizer-synthetic-catalog" or runtime.get("releaseAccepted") is not False:
            raise ValueError("Expected a local synthetic optimizer measurement")
        manifest["runtime"] = runtime
        manifest["inputReportSha256"]["energy-runtime-benchmark.json"] = digest(runtime_report.read_bytes())
    output.mkdir(parents=True)
    (output / "model-source.tar.gz").write_bytes(archive)
    (output / "evidence.json").write_text(json.dumps(manifest, indent=2) + "\n")
    (output / "SHA256SUMS").write_text("".join(
        digest((output / name).read_bytes()) + "  " + name + "\n"
        for name in ("model-source.tar.gz", "evidence.json")
    ))
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--api-reports", type=Path, default=ROOT / "apps/prototype/api/target/surefire-reports")
    parser.add_argument("--browser-report", type=Path, default=ROOT / "apps/prototype/ui/test-results/energy-browser.json")
    parser.add_argument("--runtime-report", type=Path)
    parser.add_argument("--api-summary-only", action="store_true",
                        help="Write count-only XML for transfer between CI jobs; exclude logs and system properties")
    args = parser.parse_args()
    if args.api_summary_only:
        summary = sanitize_api_reports(args.api_reports, args.output)
        return 1 if summary["failures"] or summary["errors"] else 0
    result = collect(args.output, args.api_reports, args.browser_report, args.runtime_report)
    print(f"Collected {result['api']['tests']} API tests and {len(result['browser']['checks'])} browser checks: {result['result']}")
    print("Local evidence only; retain this directory in controlled release storage after review.")
    return 0 if result["result"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
