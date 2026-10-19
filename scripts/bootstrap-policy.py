#!/usr/bin/env python3
"""Create the initial browser application policy and its application-users group."""

import argparse
import json

from platform_bootstrap import (
    Devplat, PROJECTS, add_environment_arguments, run_main, setup_browser,
)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    add_environment_arguments(parser)
    parser.add_argument("--group", default="application-users", help="Browser access group name")
    args = parser.parse_args()
    project = PROJECTS[args.environment]
    policy = setup_browser(Devplat(args.profile), project, args.group)
    print(json.dumps({"project": project, "application_policy": policy}, indent=2))


if __name__ == "__main__":
    run_main(main)
