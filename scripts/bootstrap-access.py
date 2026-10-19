#!/usr/bin/env python3
"""Set up ODIP access and save its binding prefix in the deployment descriptor."""

import argparse
import json
from pathlib import Path

from platform_bootstrap import (
    BootstrapError, Devplat, PROJECTS, add_environment_arguments, run_main, request_odip, ODIP_PROJECTS, setup_browser,
)


ROOT = Path(__file__).resolve().parent.parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    add_environment_arguments(parser)
    parser.add_argument("--browser", action="store_true", help="Create an initial browser policy after first deployment")
    parser.add_argument("--odip", action="store_true", help="Request ODIP access and update its prefix in place (default unless --browser is selected)")
    parser.add_argument("--group", default="application-users")
    parser.add_argument("--provider-project", help="Default: odip for production, odip-test for test")
    parser.add_argument("--provider-application", help="Default: provider project name")
    args = parser.parse_args()
    if not args.browser and not args.odip:
        args.odip = True
    project = PROJECTS[args.environment]
    if args.odip:
        path = ROOT / ("application.json" if args.environment == "production" else "application-test.json")
        descriptor = json.loads(path.read_text())
        if descriptor["metadata"]["name"] != project or descriptor["metadata"].get("project", project) != project:
            raise BootstrapError("Descriptor project does not match the selected environment.")
        values = descriptor["spec"]["configuration"]["values"]
        if "ENERGY_ODIP_BINDING_PREFIX" not in values:
            raise BootstrapError("Descriptor must declare ENERGY_ODIP_BINDING_PREFIX.")
    cli = Devplat(args.profile)
    result = {"project": project}
    # ODIP can be requested before this consumer's first deployment.
    if args.odip:
        provider = args.provider_project or ODIP_PROJECTS[args.environment]
        result["odip"] = request_odip(cli, project, provider, args.provider_application or provider)
        values["ENERGY_ODIP_BINDING_PREFIX"] = result["odip"]["binding_prefix"]
        path.write_text(json.dumps(descriptor, indent=2) + "\n")
    if args.browser:
        result["browser_policy"] = setup_browser(cli, project, args.group)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    run_main(main)
