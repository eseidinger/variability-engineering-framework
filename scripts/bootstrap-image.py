#!/usr/bin/env python3
"""Build and publish the combined application image with the latest tag."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

from platform_bootstrap import BootstrapError, run_main


ROOT = Path(__file__).resolve().parent.parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--file", type=Path, default=ROOT / "application.json",
                        help="Descriptor supplying the web image repository (default: application.json)")
    args = parser.parse_args()
    descriptor = json.loads(args.file.read_text())
    components = [c for c in descriptor["spec"]["components"] if c["name"] == "web"]
    if len(components) != 1:
        raise BootstrapError("Descriptor must contain exactly one web component.")
    repository = components[0]["runtime"]["image"].split("@", 1)[0]
    if ":" in repository.rsplit("/", 1)[-1]:
        repository = repository.rsplit(":", 1)[0]
    image = repository + ":latest"
    commands = [
        ["docker", "build", "--file", str(ROOT / "apps/prototype/deployment/Dockerfile"),
         "--tag", image, str(ROOT)],
        ["docker", "push", image],
    ]
    for command in commands:
        print(f"{'Building' if command[1] == 'build' else 'Publishing'} {image}.", file=sys.stderr)
        result = subprocess.run(command, stdout=sys.stderr)
        if result.returncode:
            raise BootstrapError(f"docker {command[1]} failed (exit {result.returncode}).")
    print(image)


if __name__ == "__main__":
    run_main(main)
