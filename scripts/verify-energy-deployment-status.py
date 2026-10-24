#!/usr/bin/env python3
"""Read the deployed energy application's readiness, image and AI egress."""

import argparse
import json
import subprocess
import sys


def verify(status: dict, expected_image: str):
    project = status["project"]
    components = project["spec"]["components"]
    if project["status"] != "applied" or len(components) != 1:
        raise ValueError("Project is not an applied single-service deployment")
    component = components[0]
    observed = status["components"]["components"][0]["status"]
    image = component["resolved_image"]
    if image != expected_image or observed["deployment_image"] != expected_image:
        raise ValueError("Running image differs from the expected digest")
    if observed["state"] != "ready" or observed["ready_replicas"] != observed["desired_replicas"]:
        raise ValueError("Deployment is not ready")
    outbound = {(item["dns"], item["port"]) for item in component["outbound"]}
    required = {
        ("api.openai.com", 443),
        ("identity.eseidinger.de", 443),
    }
    if not required.issubset(outbound):
        raise ValueError("AI provider or identity egress is absent")
    config = project["spec"]["configuration"]
    if config.get("ENERGY_AI_ENABLED") != "true":
        raise ValueError("Energy AI is not enabled")
    return {
        "revision": status["components"]["revision"],
        "image": image,
        "readyReplicas": observed["ready_replicas"],
        "aiEnabled": True,
        "model": config.get("ENERGY_AI_MODEL"),
        "outbound": sorted(f"{dns}:{port}" for dns, port in outbound),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", default="vareng-test")
    parser.add_argument("--expected-image", required=True)
    args = parser.parse_args()
    result = subprocess.run(
        ["devplat", "status", "--project", args.project, "--output", "json"],
        capture_output=True, text=True, check=True,
    )
    summary = verify(json.loads(result.stdout), args.expected_image)
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (KeyError, ValueError, json.JSONDecodeError, subprocess.CalledProcessError) as error:
        print(f"FAIL: {type(error).__name__}: {error}", file=sys.stderr)
        sys.exit(1)
