"""Daemon-free contract checks for production deployment preparation."""

from contextlib import redirect_stderr, redirect_stdout
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/check_deployment.py"
COMPOSE = ROOT / "compose.production.yaml"
SETTINGS = {
    "PUBLIC_IPV4": "8.8.8.8",  # Configuration only; tests never connect to it.
    "APP_IMAGE": "mtgcmd:0123456",
    "ACME_EMAIL": "operator@example.com",
    "DATABASE_PASSWORD": "test-only-secret-do-not-print",
}


def environment(settings):
    # Do not inherit deployment variables or COMPOSE_FILE from the operator shell.
    return {"PATH": os.environ["PATH"], "HOME": os.environ["HOME"], **settings}


def resolve(settings, path=COMPOSE):
    return subprocess.run(
        ["docker", "compose", "--env-file", "/dev/null", "-f", str(path),
         "config", "--format", "json"],
        cwd=ROOT, env=environment(settings), capture_output=True, text=True, timeout=15,
    )


class DeploymentConfigurationTest(unittest.TestCase):
    def config(self, settings=None):
        result = resolve(SETTINGS if settings is None else settings)
        self.assertEqual(result.returncode, 0, result.stderr)
        return json.loads(result.stdout)

    def test_requires_all_deployment_settings(self):
        for key in SETTINGS:
            with self.subTest(key=key):
                for value in (None, ""):
                    settings = dict(SETTINGS)
                    if value is None:
                        del settings[key]
                    else:
                        settings[key] = value
                    self.assertNotEqual(resolve(settings).returncode, 0)

    def test_only_proxy_publishes_ports(self):
        services = self.config()["services"]
        self.assertEqual(set(services), {"app", "db", "https"})
        for service in ("app", "db"):
            self.assertFalse(services[service].get("ports"))
        self.assertEqual(
            {(port["published"], port["target"], port["protocol"])
             for port in services["https"]["ports"]},
            {("80", 80, "tcp"), ("443", 443, "tcp")},
        )

    def test_origin_and_cookie_ignore_local_defaults(self):
        services = self.config({**SETTINGS, "PUBLIC_ORIGIN": "http://localhost:5173",
                                "SECURE_COOKIE": "false"})["services"]
        app = services["app"]["environment"]
        self.assertEqual(app["PUBLIC_ORIGIN"], "https://8.8.8.8")
        self.assertEqual(app["SECURE_COOKIE"], "true")
        self.assertEqual(services["https"]["environment"]["PUBLIC_IPV4"], "8.8.8.8")
        self.assertEqual(app["DATABASE_PASSWORD"], SETTINGS["DATABASE_PASSWORD"])

    def test_storage_limits_and_lifecycle(self):
        config = self.config()
        self.assertEqual(config["name"], "mtgcmd-production")
        services = config["services"]
        for service in services.values():
            self.assertEqual(service["restart"], "unless-stopped")
            self.assertGreater(int(service["mem_limit"]), 0)
            self.assertEqual(service["logging"]["options"]["max-file"], "3")
        self.assertEqual(services["app"]["stop_grace_period"], "45s")
        self.assertEqual(services["app"]["environment"]["SERVER_SHUTDOWN"], "graceful")
        self.assertEqual(services["app"]["depends_on"]["db"]["condition"], "service_healthy")
        for name, target in (("db", "/var/lib/postgresql/data"), ("https", "/data")):
            self.assertTrue(any(v["type"] == "volume" and v["target"] == target
                                for v in services[name]["volumes"]))
        self.assertEqual(services["https"]["image"], "caddy:2.11.7-alpine")

    def test_local_configuration_stays_local(self):
        result = resolve({"DATABASE_PASSWORD": "fixture"}, ROOT / "compose.yaml")
        self.assertEqual(result.returncode, 0, result.stderr)
        services = json.loads(result.stdout)["services"]
        self.assertEqual(services["app"]["ports"][0]["host_ip"], "127.0.0.1")
        self.assertEqual(services["db"]["ports"][0]["host_ip"], "127.0.0.1")
        self.assertEqual(services["app"]["environment"]["SECURE_COOKIE"], "false")

    def test_resource_overrides(self):
        services = self.config({**SETTINGS, "APP_MEMORY": "2g", "DB_MEMORY": "1g",
                                "CADDY_MEMORY": "256m"})["services"]
        self.assertEqual(int(services["app"]["mem_limit"]), 2 * 1024**3)
        self.assertEqual(int(services["db"]["mem_limit"]), 1024**3)
        self.assertEqual(int(services["https"]["mem_limit"]), 256 * 1024**2)

    def test_invalid_memory_configuration_rejected(self):
        self.assertNotEqual(resolve({**SETTINGS, "APP_MEMORY": "invalid"}).returncode, 0)

    def test_proxy_mount_and_ca(self):
        proxy = self.config()["services"]["https"]
        mount = next(v for v in proxy["volumes"] if v["target"] == "/etc/caddy/Caddyfile")
        self.assertTrue(mount["read_only"])
        self.assertEqual(Path(mount["source"]), ROOT / "deploy/Caddyfile.ip")
        self.assertEqual(proxy["environment"]["ACME_DIRECTORY"],
                         "https://acme-v02.api.letsencrypt.org/directory")


class DeploymentPreflightTest(unittest.TestCase):
    def run_check(self, overrides=None, args=()):
        self.assertTrue(SCRIPT.is_file(), "deployment preflight is not implemented")
        with tempfile.TemporaryDirectory(prefix="mtgcmd deployment ") as directory:
            env_file = Path(directory) / "settings.env"
            settings = {**SETTINGS, **(overrides or {})}
            env_file.write_text("".join(f"{key}={value}\n" for key, value in settings.items()))
            result = subprocess.run(
                ["python3", str(SCRIPT), "--env-file", str(env_file), *args],
                cwd=directory, env=environment({}), capture_output=True, text=True, timeout=15,
            )
            self.assertNotIn(SETTINGS["DATABASE_PASSWORD"], result.stdout + result.stderr)
            return result

    def test_valid_from_another_directory_without_daemon(self):
        result = self.run_check()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("Configuration valid", result.stdout)

    def test_invalid_ip_addresses(self):
        for address in ("localhost", "127.0.0.1", "10.0.0.1", "0.0.0.0", "203.0.113.1",
                        "::1", "https://8.8.8.8", "8.8.8.8:443", "8.8.8.8/path"):
            with self.subTest(address=address):
                self.assertNotEqual(self.run_check({"PUBLIC_IPV4": address}).returncode, 0)

    def test_unversioned_images_rejected(self):
        for image in ("mtgcmd", "mtgcmd:latest", "registry.example:5000/mtgcmd"):
            with self.subTest(image=image):
                self.assertNotEqual(self.run_check({"APP_IMAGE": image}).returncode, 0)

    def test_digest_image_accepted(self):
        result = self.run_check({"APP_IMAGE": "mtgcmd@sha256:" + "a" * 64})
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_staging_requires_explicit_flag(self):
        settings = {"ACME_DIRECTORY": "https://acme-staging-v02.api.letsencrypt.org/directory"}
        self.assertNotEqual(self.run_check(settings).returncode, 0)
        result = self.run_check(settings, ("--allow-staging",))
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_arbitrary_ca_rejected(self):
        self.assertNotEqual(self.run_check({"ACME_DIRECTORY": "http://localhost/ca"}).returncode, 0)

    def test_missing_env_file(self):
        self.assertTrue(SCRIPT.is_file(), "deployment preflight is not implemented")
        result = subprocess.run(
            ["python3", str(SCRIPT), "--env-file", "/nonexistent/mtgcmd.env"],
            capture_output=True, text=True, env=environment({}), timeout=15,
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("Traceback", result.stderr)


class DeploymentPreflightFailureTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location("check_deployment", SCRIPT)
        cls.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.module)

    def test_tool_failures_are_safe_and_actionable(self):
        cases = (
            (FileNotFoundError(), "Docker CLI is missing"),
            (subprocess.TimeoutExpired("docker", 30), "timed out"),
            (subprocess.CompletedProcess([], 1, SETTINGS["DATABASE_PASSWORD"],
                                         SETTINGS["DATABASE_PASSWORD"]), "Compose rejected"),
            (subprocess.CompletedProcess([], 0, "invalid json", ""), "Unexpected Compose"),
            (subprocess.CompletedProcess([], 0, "{}", ""), "Unexpected Compose"),
        )
        with tempfile.TemporaryDirectory() as directory:
            env_file = Path(directory) / "settings.env"
            env_file.touch()
            for outcome, message in cases:
                with self.subTest(message=message):
                    output, errors = io.StringIO(), io.StringIO()
                    kwargs = {"side_effect": outcome} if isinstance(outcome, Exception) else {"return_value": outcome}
                    with patch.object(self.module.subprocess, "run", **kwargs), \
                         patch("sys.argv", [str(SCRIPT), "--env-file", str(env_file)]), \
                         redirect_stdout(output), redirect_stderr(errors):
                        with self.assertRaises(SystemExit) as error:
                            self.module.main()
                    self.assertEqual(error.exception.code, 1)
                    self.assertIn(message, errors.getvalue())
                    self.assertNotIn(SETTINGS["DATABASE_PASSWORD"], output.getvalue() + errors.getvalue())

    def test_guards_reject_configuration_drift(self):
        result = resolve(SETTINGS)
        self.assertEqual(result.returncode, 0, result.stderr)
        changes = (
            ("app", "PUBLIC_ORIGIN", "http://8.8.8.8"),
            ("app", "SECURE_COOKIE", "false"),
        )
        for service, key, value in changes:
            with self.subTest(key=key):
                config = json.loads(result.stdout)
                config["services"][service]["environment"][key] = value
                with self.assertRaises(ValueError):
                    self.module.validate(config, False)
        for service in ("app", "db"):
            with self.subTest(service=service):
                config = json.loads(result.stdout)
                config["services"][service]["ports"] = [{"target": 8080}]
                with self.assertRaises(ValueError):
                    self.module.validate(config, False)
        config = json.loads(result.stdout)
        config["services"]["https"]["ports"].append({"published": "2019", "target": 2019, "protocol": "tcp"})
        with self.assertRaises(ValueError):
            self.module.validate(config, False)


if __name__ == "__main__":
    unittest.main()
