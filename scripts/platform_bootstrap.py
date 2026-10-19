"""Shared CLI helpers for the application bootstrap entry points."""

import json
import shlex
import subprocess
import sys
import time


PROJECTS = {"production": "vareng", "test": "vareng-test"}
ODIP_PROJECTS = {"production": "odip", "test": "odip-test"}


class BootstrapError(RuntimeError):
    pass


class Devplat:
    def __init__(self, profile=None):
        self.profile = profile
        self.command = ["devplat"] + (["--profile", profile] if profile else [])

    def run(self, *args, body=None, missing_ok=False):
        command = [*self.command, *args, "--output", "json"]
        if body is not None:
            command += ["--file", "-"]
        result = subprocess.run(
            command, input=json.dumps(body) if body is not None else None,
            capture_output=True, text=True,
        )
        if result.returncode == 5 and missing_ok:
            return None
        if result.returncode:
            # Never relay captured stdout: credential commands return secrets there.
            message = f"devplat {' '.join(args[:2])} failed (exit {result.returncode}): {result.stderr.strip()}"
            if (result.returncode == 2 and self.profile
                    and "profile does not exist or is invalid" in result.stderr):
                profile = shlex.quote(self.profile)
                message += (
                    f"\nCheck the profile with: devplat profiles show {profile}"
                    f"\nIf it is missing, create it with your platform connection settings:"
                    f"\n  devplat profiles create {profile} --api-url PLATFORM_API_URL --issuer OIDC_ISSUER_URL"
                    f"\nThen sign in: devplat --profile {profile} auth login"
                    "\nOr rerun bootstrap with the name of an existing administrator profile."
                )
            raise BootstrapError(message)
        if result.stderr:
            print(result.stderr.strip(), file=sys.stderr)
        return json.loads(result.stdout)


def unique(rows, description):
    if len(rows) > 1:
        raise BootstrapError(f"Multiple {description}; inspect platform state before retrying.")
    return rows[0] if rows else None


def wait_active(read, description, timeout, poll_interval):
    deadline = time.monotonic() + timeout
    while True:
        row = read()
        if row is None:
            raise BootstrapError(f"{description} disappeared during provisioning.")
        if (row.get("error_code") or row.get("state") not in {"active", "pending"}
                or row.get("observed_state") in {"failed", "retired", "revoked", "rejected"}):
            raise BootstrapError(f"{description} is not usable: state={row.get('state')}, error={row.get('error_code')}.")
        if row.get("state") == "active" and row.get("observed_state") == "active":
            return row
        if time.monotonic() >= deadline:
            raise BootstrapError(f"Timed out waiting for {description}; inspect it and rerun bootstrap.")
        time.sleep(min(poll_interval, max(0, deadline - time.monotonic())))


def add_environment_arguments(parser):
    parser.add_argument("--environment", choices=PROJECTS, required=True)
    parser.add_argument("--profile", help="Authenticated devplat administrator profile")


def run_main(main):
    try:
        main()
    except (BootstrapError, OSError, ValueError) as exc:
        print(f"Bootstrap failed: {exc}", file=sys.stderr)
        sys.exit(1)

def request_odip(cli, project, provider_project, application):
    request_name = provider_project + "-reader"
    requests = cli.run("access", "services", "list", "--project", project)["items"]
    service = unique([
        r for r in requests if r["project"] == project and r["name"] == request_name
    ], "ODIP reader requests")
    if service is None:
        service = cli.run(
            "access", "services", "request", "--project", project,
            "--name", request_name, "--provider-project", provider_project,
            "--application", application, "--project-wide-credential",
            "--idempotency-key", f"{project}-bootstrap-{request_name}",
        )
    if (service["spec"]["providerProject"] != provider_project
            or service["spec"]["application"] != application):
        raise BootstrapError("Existing ODIP reader request targets a different provider.")
    if service["state"] not in {"pending", "provisioning", "active"}:
        raise BootstrapError(f"ODIP reader request is {service['state']}; inspect it before retrying.")
    return {"request_id": service["id"], "state": service["state"],
            "binding_prefix": service["spec"].get("secretPrefix") or "ACCESS_" + service["id"][-12:].upper(),
            "provider_url": service["spec"].get("providerUrl")}



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

