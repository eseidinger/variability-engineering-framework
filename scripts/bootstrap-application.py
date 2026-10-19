#!/usr/bin/env python3
"""Set up PostgreSQL, update its binding in place and deploy the latest application image."""

import argparse
from copy import deepcopy
import json
from pathlib import Path
import sys

from platform_bootstrap import (
    BootstrapError, Devplat, PROJECTS, add_environment_arguments,
    run_main, unique, wait_active,
)


ROOT = Path(__file__).resolve().parent.parent


def provision(cli, project, resource_name, quota_bytes, timeout, poll_interval):
    projects = cli.run("projects", "list")
    existing = unique([p for p in projects if p["name"] == project], "projects")
    if existing and existing["status"] == "retired":
        raise BootstrapError(f"Project {project} is retired; bootstrap will not reactivate it.")
    if existing is None:
        cli.run("projects", "create", "--project", project)

    stores = cli.run("stores", "list", "--project", project)["resources"]
    store = unique([s for s in stores if s["name"] == resource_name], "matching stores")
    if store is None:
        store = cli.run("stores", "create", "--project", project, body={
            "name": resource_name, "provider": "postgresql",
            "quota_bytes": quota_bytes, "extensions": [],
        })
    if store["provider"] != "postgresql" or store["state"] != "active":
        raise BootstrapError("Existing store must be an active PostgreSQL resource.")
    resource_id = store["resource_id"]
    print(f"Waiting for PostgreSQL {resource_id} in {project}.", file=sys.stderr)
    wait_active(lambda: cli.run("stores", "get", resource_id), "PostgreSQL store", timeout, poll_interval)

    def bindings():
        return cli.run("stores", "bindings", "list", "--project", project)["bindings"]

    binding = unique([
        b for b in bindings()
        if b["resource_id"] == resource_id and b["component"] == "web"
        and b["state"] in {"active", "pending"}
    ], "matching PostgreSQL bindings")
    if binding is None:
        binding = cli.run("stores", "bindings", "request", "--project", project, body={
            "resource_id": resource_id, "component": "web", "mode": "read-write",
        })
    if binding["mode"] != "read-write":
        raise BootstrapError("Existing binding is read-only; bootstrap will not increase its permissions.")
    binding_id = binding["grant_id"]
    print(f"Waiting for web binding {binding_id}.", file=sys.stderr)
    wait_active(
        lambda: unique([b for b in bindings() if b["grant_id"] == binding_id], "binding IDs"),
        "PostgreSQL web binding", timeout, poll_interval,
    )
    return binding_id


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    add_environment_arguments(parser)
    parser.add_argument("--resource-name", default="primary")
    parser.add_argument("--quota-bytes", type=int, default=1073741824, help="Quota for a NEW store; existing quotas are preserved")
    parser.add_argument("--timeout", type=float, default=300)
    parser.add_argument("--poll-interval", type=float, default=5)
    args = parser.parse_args()
    if args.timeout <= 0 or args.poll_interval <= 0:
        parser.error("timeout and poll interval must be positive")
    if not 1048576 <= args.quota_bytes <= 1099511627776:
        parser.error("quota must be between 1 MiB and 1 TiB")
    project = PROJECTS[args.environment]
    path = ROOT / ("application.json" if args.environment == "production" else "application-test.json")
    descriptor = json.loads(path.read_text())
    if descriptor["metadata"]["name"] != project or descriptor["metadata"].get("project", project) != project:
        raise BootstrapError("Descriptor project does not match the selected environment.")
    binding = storage_binding(descriptor)
    # Validate image references before any remote mutation.
    latest_deployment(descriptor)
    cli = Devplat(args.profile)
    binding_id = provision(
        cli, project, args.resource_name, args.quota_bytes, args.timeout, args.poll_interval,
    )
    binding["bindingId"] = binding_id
    path.write_text(json.dumps(descriptor, indent=2) + "\n")
    deployment = latest_deployment(descriptor)
    print(f"Deploying {project} and waiting for readiness.", file=sys.stderr)
    cli.run("deploy", "apply", "--project", project,
            "--wait", "--timeout", str(args.timeout),
            "--poll-interval", str(args.poll_interval), body=deployment)
    print(binding_id)


def storage_binding(descriptor):
    web = unique([c for c in descriptor["spec"]["components"] if c["name"] == "web"], "web components")
    bindings = [b for b in web.get("bindings", []) if b.get("envPrefix", "") == ""] if web else []
    if len(bindings) != 1:
        raise BootstrapError("web must declare exactly one unprefixed PostgreSQL binding.")
    return bindings[0]


def latest_deployment(descriptor):
    deployment = deepcopy(descriptor)
    for component in deployment["spec"]["components"]:
        runtime = component["runtime"]
        repository = runtime["image"].split("@", 1)[0]
        if ":" in repository.rsplit("/", 1)[-1]:
            repository = repository.rsplit(":", 1)[0]
        runtime["image"] = repository + ":latest"
    return deployment


if __name__ == "__main__":
    run_main(main)
