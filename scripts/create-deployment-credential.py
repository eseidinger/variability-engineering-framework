#!/usr/bin/env python3
"""Create a CI credential and save its one-time response to a private JSON file."""

import argparse
import json
import os
from pathlib import Path
import sys

from platform_bootstrap import Devplat, PROJECTS, add_environment_arguments, run_main


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    add_environment_arguments(parser)
    parser.add_argument("--output-file", type=Path, required=True, help="New private file for the one-time credential response")
    parser.add_argument("--name", default="github-actions")
    parser.add_argument("--expires-in-days", type=int, default=90)
    args = parser.parse_args()
    if not 1 <= args.expires_in_days <= 90:
        parser.error("credential lifetime must be between 1 and 90 days")
    # Reserve the file before creating a credential; never overwrite a saved secret.
    descriptor = os.open(args.output_file, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w") as output:
        response = Devplat(args.profile).run(
            "credentials", "create", "--project", PROJECTS[args.environment],
            "--name", args.name, "--expires-in-days", str(args.expires_in_days),
        )
        json.dump(response, output, indent=2)
        output.write("\n")
        output.flush()
        os.fsync(output.fileno())
    suffix = "_TEST" if args.environment == "test" else ""
    print(f"Saved one-time credential to {args.output_file}. Set PLATFORM_CLIENT_ID{suffix} and PLATFORM_CLIENT_SECRET{suffix} from that file.", file=sys.stderr)


if __name__ == "__main__":
    run_main(main)
