#!/usr/bin/env python3
"""Set up ODIP access and save its binding prefix in the deployment descriptor."""

import argparse
import json
from pathlib import Path

from platform_bootstrap import (
    BootstrapError, Devplat, PROJECTS, add_environment_arguments, run_main, unique, request_odip, ODIP_PROJECTS,
)


ROOT = Path(__file__).resolve().parent.parent


def browser_policy(group):
    def route(name, match):
        return {"name": name, "match": match,
                "authentication": {"mode": "browser-session"},
                "authorization": {"anyOfGroups": [group]}}

    return {
        "component": "web", "defaultDecision": "deny",
        "identityAttributes": {"displayName": True, "email": False},
        "routes": [
            route("read", {"pathPrefix": "/", "methods": ["GET"]}),
            route("food-analysis", {"pathPrefix": "/api/populations", "methods": ["POST"]}),
            route("energy-runs", {"path": "/api/v1/optimization-runs", "methods": ["POST"]}),
            route("ai-proposals", {"path": "/api/v1/ai/request-proposals", "methods": ["POST"]}),
            route("ai-explanations", {"pathPrefix": "/api/v1/ai/optimization-runs", "methods": ["POST"]}),
        ],
    }


def setup_browser(cli, project, group_name):
    # A policy requires a deployed public component. Never replace an existing policy.
    policy = cli.run("access", "policy", "get", "--project", project,
                     "--application", project, missing_ok=True)
    if policy is not None:
        if policy["state"] != "active":
            raise BootstrapError("Existing browser policy is not active; inspect its access operation before retrying.")
        return policy
    groups = cli.run("access", "groups", "list", "--project", project)["items"]
    group = unique([g for g in groups if g["name"] == group_name], "browser groups")
    if group is None:
        group = cli.run("access", "groups", "create", "--project", project,
                        "--name", group_name, "--display-name", "Application users",
                        "--idempotency-key", f"{project}-bootstrap-{group_name}")
    return cli.run("access", "policy", "apply", "--project", project,
                   "--application", project, "--wait", body=browser_policy(group["id"]))


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
