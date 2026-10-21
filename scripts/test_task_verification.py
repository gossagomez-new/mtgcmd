"""Task command boundaries exercised through the real runner with isolated tools."""

import json
import os
from pathlib import Path
import signal
import subprocess
import tempfile
import time
import unittest

import test_verify as fixtures


class TaskVerificationTest(unittest.TestCase):
    def setUp(self):
        self.fixture = fixtures.LocalVerificationTest()
        self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups)
        self.root = self.fixture.root
        self.directory = self.fixture.directory
        paths_temp = tempfile.TemporaryDirectory(prefix='task-paths-')
        self.addCleanup(paths_temp.cleanup)
        self.paths_directory = Path(paths_temp.name)
        self.config = self.root / '.verification.local.json'
        with (self.root / '.gitignore').open('a') as output:
            output.write('.verification.local.json\n')
        self.fixture.env['TMPDIR'] = str(self.directory)

    def cli(self, *args, **kwargs):
        return self.fixture.run_cli(*args, **kwargs)

    def run_task(self, profile='frontend', task='card-actions', extra=(), env=None):
        result = self.cli('--task', task, profile, *extra, env=env)
        lines = [x for x in result.stdout.splitlines() if x.startswith('Manifest: ')]
        self.assertEqual(len(lines), 1, result.stdout + result.stderr)
        path = Path(lines[0].removeprefix('Manifest: '))
        return result, json.loads(path.read_text()), path

    def local_paths(self):
        repository = self.paths_directory / 'maven-cache'
        temporary = self.paths_directory / 'java-temp'
        chrome = self.directory / 'chrome with spaces'
        repository.mkdir(exist_ok=True)
        temporary.mkdir(exist_ok=True)
        chrome.write_text('#!/bin/sh\nexit 0\n')
        chrome.chmod(0o755)
        return {'mavenRepository': str(repository), 'javaTemporaryDirectory': str(temporary),
                'chromePath': str(chrome)}

    def test_task_profiles_have_exact_scope_and_unique_private_labelled_reports(self):
        paths = []
        for profile, expected in {**fixtures.EXPECTED, 'browser': ['browser']}.items():
            result, data, path = self.run_task(profile)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertEqual(data['task'], 'card-actions')
            self.assertIn('card-actions', result.stdout)
            self.assertTrue(path.parent.name.startswith('mtgcmd-verify-card-actions-'))
            self.assertEqual([x['id'] for x in data['checks'] if x['status'] == 'passed'], expected)
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)
            self.assertEqual(path.parent.stat().st_mode & 0o777, 0o700)
            self.assertEqual(self.cli('--check', str(path)).returncode, 0)
            paths.append(path)
        result, _, repeated = self.run_task()
        self.assertEqual(result.returncode, 0)
        self.assertNotIn(repeated, paths)
        self.assertTrue(all(path.exists() for path in paths))
        result, data, _ = self.run_task('backend', extra=['--browser'])
        self.assertEqual(result.returncode, 0)
        self.assertEqual([x['id'] for x in data['checks'] if x['selected']],
                         ['backend-verify', 'browser'])

    def test_invalid_task_names_and_cli_forms_run_no_commands(self):
        for task in ('', '../escape', 'x/y', 'x\\y', '-x', 'x-', 'x--y', 'Upper',
                     'x y', 'x;echo', '$(echo)', 'x\ny', 'é', 'a' * 65):
            with self.subTest(task=task):
                result = self.cli('--task', task, 'frontend')
                self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        for args in (['frontend', '--task', 'valid'], ['--task=valid', 'frontend'],
                     ['--task', 'valid', 'frontend', '--task', 'other'],
                     ['--task', 'valid', 'frontend', '--output', str(self.directory / 'output')],
                     ['--task', 'valid', '--check', str(self.directory / 'manifest.json')],
                     ['--task', 'valid', 'frontend', '--', 'echo', 'bad'],
                     ['--task', 'valid', 'frontend', '--browser', '--browser'],
                     ['--task', 'valid', 'browser', '--browser']):
            with self.subTest(args=args):
                result = self.cli(*args)
                self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertEqual(self.fixture.captured(), [])
        self.assertFalse((self.directory / 'output').exists())

    def test_boundary_names_and_legacy_browser_profile_work(self):
        for task in ('a', '9', 'a-1', 'a' * 64):
            result, data, _ = self.run_task('browser', task=task)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertEqual(data['task'], task)
        result, data, _ = self.fixture.run_profile('browser')
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertNotIn('task', data)
        self.assertEqual([x['id'] for x in data['checks'] if x['selected']], ['browser'])

    def test_local_settings_supply_defaults_and_readonly_validation_reuses_them(self):
        paths = self.local_paths()
        self.config.write_text(json.dumps(paths))
        for name in ('MAVEN_OPTS', 'JAVA_TOOL_OPTIONS', 'CHROME_PATH'):
            self.fixture.env.pop(name, None)
        result, data, path = self.run_task('backend', extra=['--browser'])
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        for event in self.fixture.captured():
            self.assertEqual(event['maven_opts'], '-Dmaven.repo.local=' + paths['mavenRepository'])
            self.assertEqual(event['java_options'], '-Djava.io.tmpdir=' + paths['javaTemporaryDirectory'])
            self.assertEqual(event['chrome'], paths['chromePath'])
        self.assertEqual(data['settings']['CHROME_PATH'], paths['chromePath'])
        self.assertNotIn(paths['mavenRepository'], path.read_text())
        self.assertNotIn(paths['javaTemporaryDirectory'], path.read_text())
        contents = path.read_bytes()
        count = len(self.fixture.captured())
        self.assertEqual(self.cli('--check', str(path)).returncode, 0)
        self.assertEqual(len(self.fixture.captured()), count)
        self.assertEqual(path.read_bytes(), contents)
        paths['javaTemporaryDirectory'] = paths['mavenRepository']
        self.config.write_text(json.dumps(paths))
        self.assertEqual(self.cli('--check', str(path)).returncode, 1)
        self.config.unlink()
        self.assertEqual(self.cli('--check', str(path)).returncode, 1)

    def test_inherited_settings_including_empty_values_win_and_legacy_ignores_config(self):
        self.config.write_text(json.dumps(self.local_paths()))
        for value in ('', '-Dkeep=private-value'):
            result, data, path = self.run_task('browser', env={
                'MAVEN_OPTS': value, 'JAVA_TOOL_OPTIONS': value,
                'CHROME_PATH': '' if not value else '/inherited/chrome path'})
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            event = self.fixture.captured()[-1]
            self.assertEqual((event['maven_opts'], event['java_options'], event['chrome']),
                             (value, value, '' if not value else '/inherited/chrome path'))
            self.assertNotIn('private-value', path.read_text())
        self.config.write_text('invalid JSON')
        result, _, _ = self.fixture.run_profile()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_invalid_configurations_fail_before_commands_or_report_creation(self):
        paths = self.local_paths()
        bad = ['not json', '[]', 'null', '{"chromePath":"one","chromePath":"two"}',
               json.dumps({'command': 'echo bad'}), json.dumps({'chromePath': None}),
               json.dumps({'chromePath': 1}), json.dumps({'chromePath': 'relative'}),
               json.dumps({'chromePath': str(self.directory / 'absent')}),
               json.dumps({'mavenRepository': paths['chromePath']}),
               json.dumps({'javaTemporaryDirectory': str(self.directory / 'path with spaces')}),
               json.dumps({'javaTemporaryDirectory': '/tmp/quote"path'})]
        for content in bad:
            with self.subTest(content=content):
                self.config.write_text(content)
                result = self.cli('--task', 'valid', 'browser')
                self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
                self.assertNotIn('Manifest: ', result.stdout)
                self.assertNotIn('Traceback', result.stderr)
        self.config.write_text(json.dumps({'chromePath': paths['chromePath']}))
        Path(paths['chromePath']).chmod(0o644)
        self.assertEqual(self.cli('--task', 'valid', 'browser').returncode, 1)
        self.config.unlink()
        target = self.directory / 'settings.json'
        target.write_text('{}')
        self.config.symlink_to(target)
        self.assertEqual(self.cli('--task', 'valid', 'browser').returncode, 1)
        self.assertEqual(self.fixture.captured(), [])
        self.assertFalse(list(self.directory.glob('mtgcmd-verify-*')))

    def test_malformed_task_metadata_rejected_by_evidence_checker(self):
        result, original, path = self.run_task('browser')
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        for task in (None, 1, {}, '../escape', 'UPPER', 'a' * 65):
            path.write_text(json.dumps({**original, 'task': task}))
            result = self.cli('--check', str(path))
            self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
            self.assertNotIn('Traceback', result.stderr)

    def test_failure_preserves_exit_and_task_scope(self):
        result, data, _ = self.run_task('full', env={'FAIL_MATCH': 'lint', 'FAIL_CODE': '37'})
        self.assertEqual(result.returncode, 37, result.stdout + result.stderr)
        self.assertEqual(data['task'], 'card-actions')
        self.assertEqual(data['status'], 'failed')
        self.assertEqual(len(self.fixture.captured()), 1)
        self.assertTrue(all(x['status'] == 'skipped' for x in data['checks'][1:]))

    def test_task_interrupt_stops_child_and_retains_labelled_evidence(self):
        process = subprocess.Popen([*self.fixture.command, '--task', 'signal-check', 'browser'],
                                   env={**self.fixture.env, 'BLOCK': '1'}, cwd=self.directory,
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        try:
            deadline = time.monotonic() + 10
            while not self.fixture.child_pid.exists() and time.monotonic() < deadline:
                if process.poll() is not None:
                    break
                time.sleep(0.02)
            self.assertTrue(self.fixture.child_pid.exists(), 'runner did not start a child')
            child = int(self.fixture.child_pid.read_text())
            process.send_signal(signal.SIGTERM)
            stdout, stderr = process.communicate(timeout=10)
            self.assertEqual(process.returncode, 143, stdout + stderr)
            path = Path(next(x.removeprefix('Manifest: ') for x in stdout.splitlines()
                             if x.startswith('Manifest: ')))
            data = json.loads(path.read_text())
            self.assertEqual(data['task'], 'signal-check')
            self.assertEqual(data['status'], 'interrupted')
            with self.assertRaises(ProcessLookupError):
                os.kill(child, 0)
        finally:
            if process.poll() is None:
                process.kill()
                process.communicate()


if __name__ == '__main__':
    unittest.main()
