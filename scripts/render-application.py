#!/usr/bin/env python3
"""Render only deployment image tags from a v-prefixed Git release tag."""

import argparse
import json
from pathlib import Path
import re
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--file", type=Path,
        default=Path(__file__).resolve().parent.parent / "application.json",
        help="Descriptor template (default: repository application.json)",
    )
    parser.add_argument("--tag", required=True, help="Git release tag, e.g. v1.2.3")
    args = parser.parse_args()

    if args.tag is not None and (not re.fullmatch(
        r"v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)"
        r"(?:-(?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)"
        r"(?:\.(?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*))*)?",
        args.tag,
    ) or len(args.tag[1:]) > 128):
        parser.error("--tag must be vMAJOR.MINOR.PATCH with an optional prerelease suffix")

    try:
        descriptor = json.loads(args.file.read_text())
        for component in descriptor["spec"]["components"]:
            runtime = component.get("runtime", {})
            image = runtime.get("image")
            if image:
                repository = image.split("@", 1)[0]
                if ":" in repository.rsplit("/", 1)[-1]:
                    repository = repository.rsplit(":", 1)[0]
                runtime["image"] = f"{repository}:{args.tag[1:]}"
    except (OSError, ValueError, KeyError, TypeError) as exc:
        parser.error(str(exc))

    json.dump(descriptor, sys.stdout, indent=2)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()
