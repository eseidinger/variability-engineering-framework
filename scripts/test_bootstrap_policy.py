"""Verify initial policy provisioning and preservation of existing access rules."""

from contextlib import redirect_stdout
import importlib.util
import io
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import Mock, patch

from platform_bootstrap import BootstrapError


SPEC = importlib.util.spec_from_file_location("bootstrap_policy", Path(__file__).with_name("bootstrap-policy.py"))
bootstrap = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(bootstrap)


class BootstrapPolicyTest(unittest.TestCase):
    def invoke(self, cli, environment, *options):
        output = io.StringIO()
        argv = ["bootstrap-policy.py", "--environment", environment, "--profile", "lab", *options]
        with patch.object(sys, "argv", argv), patch.object(bootstrap, "Devplat", return_value=cli), redirect_stdout(output):
            bootstrap.main()
        return json.loads(output.getvalue())

    def test_creates_group_and_policy_for_each_environment(self):
        for environment, project in [("production", "vareng"), ("test", "vareng-test"),
                                     ("test-local", "vareng-test")]:
            with self.subTest(environment=environment):
                cli = Mock()
                policy = {"id": "pol_example", "state": "active"}
                cli.run.side_effect = [None, {"items": []}, {"id": "grp_users"}, policy]
                result = self.invoke(cli, environment)
                self.assertEqual(result, {"project": project, "application_policy": policy})
                create, apply = cli.run.call_args_list[2:]
                self.assertEqual(create.args[:3], ("access", "groups", "create"))
                self.assertIn("application-users", create.args)
                self.assertEqual(apply.args, ("access", "policy", "apply", "--project", project,
                                             "--application", project, "--wait"))
                body = apply.kwargs["body"]
                self.assertEqual(body["defaultDecision"], "deny")
                self.assertEqual(body["component"], "web")
                for route in body["routes"]:
                    self.assertEqual(route["authentication"], {"mode": "browser-session"})
                    self.assertEqual(route["authorization"], {"anyOfGroups": ["grp_users"]})
                self.assertEqual(len(body["routes"]), 5)

    def test_reuses_named_group(self):
        cli = Mock()
        cli.run.side_effect = [None, {"items": [{"id": "grp_existing", "name": "members"}]}, {"state": "active"}]
        self.invoke(cli, "test", "--group", "members")
        self.assertEqual(cli.run.call_count, 3)
        body = cli.run.call_args.kwargs["body"]
        self.assertEqual(body["routes"][0]["authorization"]["anyOfGroups"], ["grp_existing"])

    def test_preserves_existing_policy(self):
        cli = Mock()
        policy = {"state": "active", "spec": {"routes": ["existing rules"]}}
        cli.run.return_value = policy
        self.assertEqual(self.invoke(cli, "production")["application_policy"], policy)
        self.assertEqual(cli.run.call_count, 1)

    def test_pending_policy_stops_without_mutation(self):
        cli = Mock()
        cli.run.return_value = {"state": "provisioning"}
        with self.assertRaisesRegex(BootstrapError, "not active"):
            self.invoke(cli, "test")
        self.assertEqual(cli.run.call_count, 1)


if __name__ == "__main__":
    unittest.main()
