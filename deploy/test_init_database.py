"""실제 GCP 호출 없이 DB 초기화의 비밀값 전달 경계를 검증한다."""
import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("database_init", Path(__file__).with_name("init-database.py"))
database_init = importlib.util.module_from_spec(spec)
spec.loader.exec_module(database_init)


class DatabaseInitTest(unittest.TestCase):
    def test_only_explicit_local_mode_uses_disposable_password(self):
        with patch.dict(os.environ, {"DB_BOOTSTRAP_MODE": "local-smoke"}, clear=True):
            self.assertEqual(database_init.password(), "decision_assistant")
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaises(ValueError):
                database_init.password()

    def test_numeric_version_and_fixed_project_path_are_validated_before_network(self):
        with patch.dict(os.environ, {"DB_BOOTSTRAP_MODE": "gcp", "PROD_PROJECT_ID": "example-prod", "DB_SECRET_VERSION": "latest"}, clear=True), patch.object(database_init, "get_json") as request:
            with self.assertRaises(ValueError):
                database_init.password()
            request.assert_not_called()

    def test_secret_is_used_only_as_sql_stdin_and_quotes_are_escaped(self):
        value = "fixture'password\\value"
        with patch.object(database_init, "password", return_value=value), patch.object(database_init.subprocess, "run") as execute:
            execute.return_value.returncode = 0
            database_init.main()
            args, kwargs = execute.call_args
            self.assertNotIn(value, " ".join(args[0]))
            self.assertNotIn("env", kwargs)
            self.assertIn("fixture''password\\value", kwargs["input"])
            self.assertIn("standard_conforming_strings = on", kwargs["input"])
            self.assertTrue(kwargs["capture_output"])

    def test_invalid_configuration_does_not_print_exception_or_secrets(self):
        result = subprocess.run([sys.executable, str(Path(__file__).with_name("init-database.py"))],
                                env={**os.environ, "DB_BOOTSTRAP_MODE": "invalid", "PROD_PROJECT_ID": "fixture-private-content"},
                                capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(result.stdout, "")
        self.assertEqual(result.stderr.strip(), "DATABASE_INITIALIZATION_FAILED")


if __name__ == "__main__":
    unittest.main()
