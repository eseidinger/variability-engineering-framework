"""Verify bootstrap retries, permission boundaries and private credential capture."""

import importlib.util
from contextlib import redirect_stdout
import io
import json
from pathlib import Path
import stat
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

from platform_bootstrap import BootstrapError, Devplat, wait_active


def load(name):
    spec = importlib.util.spec_from_file_location(name.replace("-", "_"), Path(__file__).with_name(name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


bootstrap = load("bootstrap-application")
access = load("bootstrap-access")
credential = load("create-deployment-credential")


class PlatformFixture:
    def __init__(self):
        self.projects = []
        self.stores = []
        self.bindings = []
        self.services = []
        self.mutations = []

    def run(self, *args, body=None):
        if body is not None or args[:2] == ("projects", "create") or args[:3] == ("access", "services", "request"):
            self.mutations.append((args, body))
        if args[:2] == ("projects", "list"):
            return self.projects
        if args[:2] == ("projects", "create"):
            self.projects.append({"name": args[3], "status": "empty"})
            return self.projects[-1]
        if args[:2] == ("stores", "list"):
            return {"resources": self.stores}
        if args[:2] == ("stores", "create"):
            self.stores.append({**body, "resource_id": "resource-1", "state": "active", "observed_state": "active"})
            return self.stores[-1]
        if args[:2] == ("stores", "get"):
            return self.stores[0]
        if args[:3] == ("stores", "bindings", "list"):
            return {"bindings": self.bindings}
        if args[:3] == ("stores", "bindings", "request"):
            self.bindings.append({**body, "grant_id": "11111111-1111-4111-8111-111111111111",
                                  "state": "active", "observed_state": "active"})
            return self.bindings[-1]
        if args[:2] == ("deploy", "apply"):
            return {"state": "succeeded"}
        if args[:3] == ("access", "services", "list"):
            return {"items": self.services}
        if args[:3] == ("access", "services", "request"):
            self.services.append({
                "id": "svc_aabbccddeeff00112233445566778899", "project": args[4],
                "name": args[6], "state": "pending",
                "spec": {"providerProject": args[8], "application": args[10]},
            })
            return self.services[-1]
        raise AssertionError(args)


class BootstrapTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        for name in ["application.json", "application-test.json"]:
            (self.root / name).write_text((bootstrap.ROOT / name).read_text())
        patcher = patch.object(bootstrap, "ROOT", self.root)
        patcher.start()
        self.addCleanup(patcher.stop)
        access_patcher = patch.object(access, "ROOT", self.root)
        access_patcher.start()
        self.addCleanup(access_patcher.stop)

    def test_missing_profile_explains_setup_without_echoing_stdout(self):
        result = Mock(returncode=2, stdout="sensitive-output", stderr="The selected profile does not exist or is invalid.")
        with patch("platform_bootstrap.subprocess.run", return_value=result):
            with self.assertRaises(BootstrapError) as raised:
                Devplat("lab").run("projects", "list")
        message = str(raised.exception)
        self.assertIn("devplat profiles show lab", message)
        self.assertIn("devplat profiles create lab", message)
        self.assertIn("devplat --profile lab auth login", message)
        self.assertNotIn("sensitive-output", message)

    def test_first_run_provisions_and_second_run_reuses(self):
        cli = PlatformFixture()
        first = bootstrap.provision(cli, "vareng-test", "primary", 1073741824, 1, 0.1)
        second = bootstrap.provision(cli, "vareng-test", "primary", 2147483648, 1, 0.1)
        self.assertEqual(first, second)
        self.assertEqual(len(cli.mutations), 3)
        self.assertEqual(cli.stores[0]["quota_bytes"], 1073741824)
        self.assertEqual(cli.bindings[0]["mode"], "read-write")

    def test_retired_project_is_not_reactivated(self):
        cli = PlatformFixture()
        cli.projects = [{"name": "vareng", "status": "retired"}]
        with self.assertRaisesRegex(BootstrapError, "retired"):
            bootstrap.provision(cli, "vareng", "primary", 1073741824, 1, 0.1)
        self.assertEqual(cli.mutations, [])

    def test_read_only_binding_is_not_upgraded(self):
        cli = PlatformFixture()
        bootstrap.provision(cli, "vareng", "primary", 1073741824, 1, 0.1)
        cli.bindings[0]["mode"] = "read-only"
        cli.mutations.clear()
        with self.assertRaisesRegex(BootstrapError, "read-only"):
            bootstrap.provision(cli, "vareng", "primary", 1073741824, 1, 0.1)
        self.assertEqual(cli.mutations, [])

    def test_failed_provisioning_and_timeout_stop_bootstrap(self):
        with self.assertRaisesRegex(BootstrapError, "not usable"):
            wait_active(lambda: {"state": "active", "error_code": "Unavailable"}, "store", 1, 0.1)
        with patch("platform_bootstrap.time.monotonic", side_effect=[0, 2]):
            with self.assertRaisesRegex(BootstrapError, "Timed out"):
                wait_active(lambda: {"state": "active", "observed_state": "pending"}, "store", 1, 0.1)

    def test_bootstrap_updates_only_storage_in_place_and_deploys_latest(self):
        for environment, project in [("production", "vareng"), ("test", "vareng-test")]:
            with self.subTest(environment=environment):
                cli = PlatformFixture()
                output = io.StringIO()
                path = self.root / ("application.json" if environment == "production" else "application-test.json")
                before = json.loads(path.read_text())
                argv = ["bootstrap-application.py", "--environment", environment, "--profile", "lab"]
                with patch.object(sys, "argv", argv), patch.object(bootstrap, "Devplat", return_value=cli), redirect_stdout(output):
                    bootstrap.main()
                binding_id = output.getvalue().strip()
                self.assertEqual(binding_id, "11111111-1111-4111-8111-111111111111")
                expected = before
                bootstrap.storage_binding(expected)["bindingId"] = binding_id
                self.assertEqual(json.loads(path.read_text()), expected)
                self.assertEqual(cli.services, [])
                self.assertEqual(cli.projects[0]["name"], project)
                self.assertEqual(len(cli.mutations), 4)
                command, declaration = cli.mutations[-1]
                self.assertEqual(command[:2], ("deploy", "apply"))
                self.assertIn("--wait", command)
                self.assertEqual(declaration["metadata"]["project"], project)
                component = declaration["spec"]["components"][0]
                self.assertTrue(component["runtime"]["image"].endswith(":latest"))
                self.assertEqual(component["bindings"][0]["bindingId"], binding_id)
                self.assertEqual(declaration["spec"]["configuration"], expected["spec"]["configuration"])

    def test_deployment_failure_does_not_print_binding_id(self):
        cli = PlatformFixture()
        run = cli.run

        def fail_deployment(*args, **kwargs):
            if args[:2] == ("deploy", "apply"):
                raise BootstrapError("deployment failed")
            return run(*args, **kwargs)

        output = io.StringIO()
        with patch.object(sys, "argv", ["bootstrap-application.py", "--environment", "test"]), \
                patch.object(bootstrap, "Devplat", return_value=cli), \
                patch.object(cli, "run", side_effect=fail_deployment), redirect_stdout(output):
            with self.assertRaisesRegex(BootstrapError, "deployment failed"):
                bootstrap.main()
        self.assertEqual(output.getvalue(), "")

    def test_existing_browser_policy_is_preserved(self):
        cli = Mock()
        cli.run.return_value = {"state": "active", "spec": {"routes": ["existing"]}}
        self.assertEqual(access.setup_browser(cli, "vareng", "users"), cli.run.return_value)
        self.assertEqual(cli.run.call_count, 1)

    def test_active_odip_request_returns_platform_prefix(self):
        cli = Mock()
        cli.run.return_value = {"items": [{
            "id": "svc_existing", "project": "vareng-test", "name": "odip-test-reader", "state": "active",
            "spec": {"providerProject": "odip-test", "application": "odip-test", "secretPrefix": "ACCESS_123456789ABC"},
        }]}
        result = access.request_odip(cli, "vareng-test", "odip-test", "odip-test")
        self.assertEqual(result["binding_prefix"], "ACCESS_123456789ABC")
        self.assertEqual(result["state"], "active")
        self.assertEqual(cli.run.call_count, 1)

    def test_access_bootstrap_updates_only_odip_prefix_in_place(self):
        for environment, provider, filename in [
            ("production", "odip", "application.json"),
            ("test", "odip-test", "application-test.json"),
        ]:
            with self.subTest(environment=environment):
                cli = PlatformFixture()
                path = self.root / filename
                before = json.loads(path.read_text())
                argv = ["bootstrap-access.py", "--environment", environment, "--profile", "lab"]
                output = io.StringIO()
                with patch.object(sys, "argv", argv), patch.object(access, "Devplat", return_value=cli), redirect_stdout(output):
                    access.main()
                result = json.loads(output.getvalue())
                before["spec"]["configuration"]["values"]["ENERGY_ODIP_BINDING_PREFIX"] = result["odip"]["binding_prefix"]
                self.assertEqual(json.loads(path.read_text()), before)
                self.assertEqual(result["odip"]["binding_prefix"], "ACCESS_445566778899")
                self.assertEqual(cli.services[0]["spec"], {"providerProject": provider, "application": provider})
                self.assertEqual(len(cli.mutations), 1)

    def test_failed_access_request_preserves_descriptor(self):
        path = self.root / "application-test.json"
        original = path.read_text()
        with patch.object(sys, "argv", ["bootstrap-access.py", "--environment", "test", "--odip"]), \
                patch.object(access, "Devplat"), \
                patch.object(access, "request_odip", side_effect=BootstrapError("request failed")):
            with self.assertRaisesRegex(BootstrapError, "request failed"):
                access.main()
        self.assertEqual(path.read_text(), original)

    def test_odip_rerun_does_not_create_another_request(self):
        cli = PlatformFixture()
        first = access.request_odip(cli, "vareng-test", "odip-test", "odip-test")
        second = access.request_odip(cli, "vareng-test", "odip-test", "odip-test")
        self.assertEqual(first, second)
        self.assertEqual(len(cli.services), 1)
        self.assertEqual(len(cli.mutations), 1)

    def test_existing_odip_request_is_reused_without_approval(self):
        cli = Mock()
        cli.run.return_value = {"items": [{
            "id": "svc_1", "project": "vareng-test", "name": "odip-reader", "state": "pending",
            "spec": {"providerProject": "odip", "application": "odip"},
        }]}
        result = access.request_odip(cli, "vareng-test", "odip", "odip")
        self.assertEqual(result["state"], "pending")
        self.assertEqual(cli.run.call_count, 1)

    def test_credential_saved_privately_and_never_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "credential.json"
            cli = Mock()
            cli.run.return_value = {"client_id": "ci-id", "client_secret": "one-time-value"}
            argv = ["create-deployment-credential.py", "--environment", "test", "--output-file", str(path)]
            with patch.object(sys, "argv", argv), patch.object(credential, "Devplat", return_value=cli):
                credential.main()
                self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600)
                self.assertEqual(json.loads(path.read_text()), cli.run.return_value)
                with self.assertRaises(FileExistsError):
                    credential.main()
                self.assertEqual(cli.run.call_count, 1)


if __name__ == "__main__":
    unittest.main()
