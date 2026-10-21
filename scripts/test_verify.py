"""Isolated process, source-identity, and evidence contracts for local verification."""

import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import unittest

SCRIPTS = Path(__file__).resolve().parent
TOOL = '''#!EXECUTABLE
import json, os, pathlib, signal, sys, time
name = pathlib.Path(sys.argv[0]).name
args = sys.argv[1:]
root = pathlib.Path(os.environ['FIXTURE_ROOT'])
with open(os.environ['EVENTS'], 'a') as events:
    events.write(json.dumps({'name': name, 'args': args, 'cwd': os.getcwd(),
                             'maven_opts': os.environ.get('MAVEN_OPTS'),
                             'secret': os.environ.get('FIXTURE_SECRET'),
                             'java_options': os.environ.get('JAVA_TOOL_OPTIONS'),
                             'chrome': os.environ.get('CHROME_PATH')}) + '\\n')
print(name + ' stdout', flush=True)
print(name + ' stderr', file=sys.stderr, flush=True)
if os.environ.get('BLOCK') == '1':
    if os.environ.get('IGNORE_SIGNALS') == '1':
        signal.signal(signal.SIGTERM, signal.SIG_IGN)
        signal.signal(signal.SIGINT, signal.SIG_IGN)
    pathlib.Path(os.environ['CHILD_PID']).write_text(str(os.getpid()))
    print('READY', flush=True)
    time.sleep(60)
if os.environ.get('CHANGE_SOURCE') == '1':
    (root / 'source.txt').write_text('changed by check')
if os.environ.get('REMOVE_LOG') == '1':
    (pathlib.Path(os.environ['RUN_OUTPUT']) / '01-frontend-lint.log').unlink()
if os.environ.get('LOCK_OUTPUT') == '1':
    os.chmod(os.environ['RUN_OUTPUT'], 0o500)
if os.environ.get('FAIL_MATCH') in args:
    sys.exit(int(os.environ.get('FAIL_CODE', '37')))
if name == 'npm' and 'analyze' in args:
    target = root / 'frontend/reports/static-analysis/summary.json'
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text('{}')
if name == 'npm' and 'build' in args:
    target = root / 'frontend/dist/index.html'
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text('<div>fixture</div>')
if name == 'mvn' and ('verify' in args or 'package' in args):
    target = root / 'backend/target'
    if 'clean' in args and target.exists():
        import shutil
        shutil.rmtree(target)
    target.mkdir(parents=True, exist_ok=True)
    (target / 'fixture.jar').write_text('jar')
    (target / 'pmd.txt').write_text('')
'''.replace('EXECUTABLE', sys.executable)

EXPECTED = {
    'frontend': ['frontend-lint', 'frontend-format', 'frontend-unit', 'analyzer-tests',
                 'frontend-analysis', 'frontend-build'],
    'backend': ['backend-verify'],
    'full': ['frontend-lint', 'frontend-format', 'frontend-unit', 'analyzer-tests',
             'wrapper-tests', 'python-tests', 'specifications', 'backend-format', 'package'],
}


class LocalVerificationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='verify fixtures ')
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.root = self.directory / 'repo with spaces'
        self.root.mkdir()
        for folder in ('scripts', 'frontend', 'backend', 'bin'):
            (self.root / folder).mkdir()
        for name in ('verify.sh', 'verify.py', 'verification_profiles.py', 'verification_evidence.py', 'verification_environment.py', 'build.sh'):
            shutil.copy2(SCRIPTS / name, self.root / 'scripts' / name)
        self.events = self.directory / 'events.jsonl'
        self.child_pid = self.directory / 'child.pid'
        for tool in ('npm', 'mvn', 'node', 'python3'):
            (self.root / 'bin' / tool).write_text(TOOL)
            (self.root / 'bin' / tool).chmod(0o755)
        for tool in ('git', 'bash'):
            (self.root / 'bin' / tool).symlink_to(shutil.which(tool))
        (self.root / '.gitignore').write_text(
            'frontend/dist/\nfrontend/reports/\nbackend/target/\n__pycache__/\n.verification.local.json\n')
        (self.root / 'source.txt').write_text('original')
        self.git('init', '-q')
        self.git('config', 'user.name', 'fixture')
        self.git('config', 'user.email', 'fixture@example.invalid')
        self.git('add', '.')
        self.git('commit', '-qm', 'test: initialize fixture')
        self.env = {**os.environ, 'PATH': str(self.root / 'bin') + os.pathsep + os.environ['PATH'],
                    'FIXTURE_ROOT': str(self.root), 'EVENTS': str(self.events),
                    'CHILD_PID': str(self.child_pid), 'CHROME_PATH': '/fixture/chrome',
                    'MAVEN_OPTS': '-Dmaven.repo.local=/fixture/cache -Dpassword=do-not-copy',
                    'FIXTURE_SECRET': 'private-environment-value'}
        # Invoke the actual interpreter, while child commands see stub tools.
        self.command = [sys.executable, '-B', str(self.root / 'scripts/verify.py')]

    def git(self, *args):
        return subprocess.run(['git', '-C', str(self.root), *args], check=True,
                              capture_output=True, text=True).stdout.strip()

    def run_cli(self, *args, env=None):
        return subprocess.run([*self.command, *args], cwd=self.directory,
                              env={**self.env, **(env or {})}, text=True,
                              capture_output=True, timeout=25)

    def run_profile(self, profile='frontend', browser=False, env=None, output=None):
        output = output or self.directory / ('run-' + str(time.time_ns()))
        args = [profile, '--output', str(output)] + (['--browser'] if browser else [])
        result = self.run_cli(*args, env=env)
        manifest_path = output / 'manifest.json'
        self.assertTrue(manifest_path.is_file(), result.stdout + result.stderr)
        return result, json.loads(manifest_path.read_text()), manifest_path

    def captured(self):
        return [json.loads(x) for x in self.events.read_text().splitlines()] if self.events.exists() else []

    def test_profiles_exact_scope_order_and_browser_is_last(self):
        for profile in EXPECTED:
            for browser in (False, True):
                with self.subTest(profile=profile, browser=browser):
                    result, data, _ = self.run_profile(profile, browser)
                    self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                    selected = [s['id'] for s in data['checks'] if s['status'] == 'passed']
                    self.assertEqual(selected, EXPECTED[profile] + (['browser'] if browser else []))
                    self.assertEqual(data['status'], 'passed')
                    self.assertTrue(data['source_unchanged'])
                    self.assertTrue(all(s['reason'] for s in data['checks'] if s['status'] == 'skipped'))
                    if browser:
                        self.assertEqual(self.captured()[-1]['args'],
                                         ['--prefix', 'frontend', 'run', 'test:e2e'])

    def test_full_delegates_packaging_without_duplicate_frontend_compilation(self):
        result, data, _ = self.run_profile('full')
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        events = self.captured()
        self.assertEqual(sum('build' in e['args'] for e in events if e['name'] == 'npm'), 1)
        self.assertEqual(sum('analyze' in e['args'] for e in events if e['name'] == 'npm'), 1)
        self.assertEqual(events[-1]['args'], ['-f', str(self.root / 'backend/pom.xml'), 'clean', 'verify'])
        self.assertEqual(next(s['argv'] for s in data['checks'] if s['id'] == 'package'),
                         ['bash', 'scripts/build.sh'])
        self.assertFalse(any('typecheck' in e['args'] or '--skip-tests' in e['args'] for e in events))

    def test_first_failure_is_not_masked_and_remaining_checks_are_skipped(self):
        result, data, path = self.run_profile('full', True, env={'FAIL_MATCH': 'lint', 'FAIL_CODE': '37'})
        self.assertEqual(result.returncode, 37, result.stdout + result.stderr)
        self.assertEqual(data['exit_code'], 37)
        self.assertEqual(data['status'], 'failed')
        self.assertEqual(data['checks'][0]['status'], 'failed')
        self.assertEqual(len(self.captured()), 1)
        self.assertTrue(all(s['status'] == 'skipped' for s in data['checks'][1:]))
        self.assertIn('npm stderr', (path.parent / data['checks'][0]['log']).read_text())
        self.assertNotIn('Verification passed', result.stdout)
        self.assertEqual(self.run_cli('--check', str(path)).returncode, 1)

    def test_logs_timestamps_private_permissions_and_environment_redaction(self):
        result, data, path = self.run_profile()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        serialized = path.read_text()
        self.assertNotIn('do-not-copy', serialized)
        self.assertNotIn('private-environment-value', serialized)
        self.assertNotIn('MAVEN_OPTS', result.stdout)
        self.assertEqual(data['schema_version'], 1)
        self.assertEqual(path.stat().st_mode & 0o777, 0o600)
        self.assertEqual(path.parent.stat().st_mode & 0o777, 0o700)
        self.assertIn('sha256', data['settings']['MAVEN_OPTS'])
        self.assertEqual(data['settings']['CHROME_PATH'], '/fixture/chrome')
        for item in data['checks']:
            if item['status'] == 'passed':
                self.assertLessEqual(item['started_at'], item['finished_at'])
                self.assertGreaterEqual(item['duration_seconds'], 0)
                log = path.parent / item['log']
                self.assertEqual(log.stat().st_mode & 0o777, 0o600)
                self.assertIn('stdout', log.read_text())
                self.assertIn('stderr', log.read_text())
                self.assertIn('sha256', item)
        self.assertTrue(all(e['cwd'] == str(self.root) and
                            e['maven_opts'] == self.env['MAVEN_OPTS'] and
                            e['secret'] == self.env['FIXTURE_SECRET'] for e in self.captured()))

    def test_source_changes_during_checks_invalidate_success(self):
        result, data, path = self.run_profile(env={'CHANGE_SOURCE': '1'})
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertFalse(data['source_unchanged'])
        self.assertEqual(data['status'], 'failed')
        self.assertIn('source', data['failure'].lower())
        self.assertEqual(self.run_cli('--check', str(path)).returncode, 1)

    def test_evidence_recheck_is_read_only_and_rejects_each_source_change(self):
        result, data, path = self.run_profile()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        manifest_bytes = path.read_bytes()
        count = len(self.captured())
        self.assertEqual(self.run_cli('--check', str(path)).returncode, 0)
        self.assertEqual(len(self.captured()), count)
        self.assertEqual(path.read_bytes(), manifest_bytes)
        original = (self.root / 'source.txt').read_bytes()
        for variant in ('unstaged', 'staged', 'untracked', 'deleted', 'symlink'):
            with self.subTest(variant=variant):
                source = self.root / 'source.txt'
                extra = self.root / 'extra'
                if variant in ('unstaged', 'staged'):
                    source.write_text('different')
                    if variant == 'staged':
                        self.git('add', 'source.txt')
                        source.write_bytes(original)
                elif variant == 'untracked':
                    extra.write_text('new')
                elif variant == 'deleted':
                    source.unlink()
                else:
                    extra.symlink_to(self.directory / 'unrelated')
                self.assertEqual(self.run_cli('--check', str(path)).returncode, 1)
                source.write_bytes(original)
                if extra.exists() or extra.is_symlink():
                    extra.unlink()
                self.git('reset', '-q', 'HEAD', '--', 'source.txt')
        self.assertEqual(self.run_cli('--check', str(path)).returncode, 0)
        (self.root / 'frontend/dist/ignored.txt').write_text('outside attestation')
        self.assertEqual(self.run_cli('--check', str(path)).returncode, 0)

    def test_detached_head_and_environment_or_log_changes(self):
        self.git('checkout', '--detach', '-q')
        result, data, path = self.run_profile()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIsNone(data['source_before']['branch'])
        self.assertEqual(self.run_cli('--check', str(path)).returncode, 0)
        self.assertEqual(self.run_cli('--check', str(path), env={'MAVEN_OPTS': '-Dother=true'}).returncode, 1)
        log = path.parent / next(s['log'] for s in data['checks'] if s['status'] == 'passed')
        log.write_text('tampered')
        self.assertEqual(self.run_cli('--check', str(path)).returncode, 1)

    def test_output_safeguards_and_cli_errors_run_no_commands(self):
        existing = self.directory / 'existing'
        existing.mkdir()
        marker = existing / 'keep'
        marker.write_text('preserve')
        alias = self.directory / 'alias'
        alias.symlink_to(existing, target_is_directory=True)
        for args in ([], ['unknown'], ['frontend', '--unknown'], ['frontend', '--browser', '--browser'],
                     ['frontend', '--check', 'x'], ['--output', 'x'], ['--browser', '--check', 'x']):
            with self.subTest(args=args):
                self.assertEqual(self.run_cli(*args).returncode, 2)
        for output in (existing, alias, alias / 'new', self.root / 'reports',
                       self.directory / 'absent-parent/new'):
            with self.subTest(output=output):
                self.assertEqual(self.run_cli('frontend', '--output', str(output)).returncode, 1)
        self.assertEqual(marker.read_text(), 'preserve')
        self.assertFalse(self.events.exists())
        self.assertEqual(self.run_cli('--help').returncode, 0)

    def test_default_output_and_shell_entry_point_from_other_directory(self):
        # Keep the real Python interpreter for the entry point's initial exec.
        (self.root / 'bin/python3').unlink()
        result = subprocess.run(['bash', str(self.root / 'scripts/verify.sh'), 'frontend'],
                                env=self.env, cwd=self.directory, text=True,
                                capture_output=True, timeout=25)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        lines = [x for x in result.stdout.splitlines() if x.startswith('Manifest: ')]
        self.assertTrue(lines, result.stdout + result.stderr)
        line = lines[0]
        path = Path(line.removeprefix('Manifest: '))
        self.addCleanup(shutil.rmtree, path.parent)
        self.assertTrue(path.is_file())
        self.assertFalse(path.is_relative_to(self.root))

    def test_missing_non_executable_tools_and_failed_bookkeeping(self):
        tool = self.root / 'bin/npm'
        tool.chmod(0o644)
        result, data, _ = self.run_profile(env={'PATH': str(self.root / 'bin')})
        self.assertEqual(result.returncode, 126, result.stdout + result.stderr)
        self.assertEqual(data['exit_code'], 126)
        tool.chmod(0o755)
        # A selected executable that resolves but has a missing interpreter.
        tool.write_text('#!/no/such/interpreter\n')
        result, _, _ = self.run_profile(env={'PATH': str(self.root / 'bin')})
        self.assertEqual(result.returncode, 127, result.stdout + result.stderr)
        tool.write_text(TOOL)
        output = self.directory / 'unwritable-run'
        try:
            result, data, _ = self.run_profile(env={'FAIL_MATCH': 'lint', 'FAIL_CODE': '37',
                                                    'LOCK_OUTPUT': '1', 'RUN_OUTPUT': str(output)},
                                               output=output)
            self.assertEqual(result.returncode, 37, result.stdout + result.stderr)
            self.assertNotEqual(data['status'], 'passed')
            self.assertIn('evidence', result.stderr.lower())
        finally:
            output.chmod(0o700)

    def test_interrupts_stop_child_and_finalize_evidence(self):
        for signum, ignore in ((signal.SIGINT, '0'), (signal.SIGTERM, '1')):
            with self.subTest(signum=signum):
                output = self.directory / f'signal-{signum}'
                process = subprocess.Popen([*self.command, 'frontend', '--output', str(output)],
                                           env={**self.env, 'BLOCK': '1', 'IGNORE_SIGNALS': ignore},
                                           cwd=self.directory, stdout=subprocess.PIPE,
                                           stderr=subprocess.PIPE, text=True)
                try:
                    deadline = time.monotonic() + 10
                    while not self.child_pid.exists() and time.monotonic() < deadline:
                        if process.poll() is not None:
                            break
                        time.sleep(0.02)
                    if not self.child_pid.exists():
                        self.fail(str(process.communicate(timeout=1)))
                    child = int(self.child_pid.read_text())
                    process.send_signal(signum)
                    stdout, stderr = process.communicate(timeout=10)
                    self.assertEqual(process.returncode, 128 + signum, stdout + stderr)
                    data = json.loads((output / 'manifest.json').read_text())
                    self.assertEqual(data['status'], 'interrupted')
                    self.assertEqual(data['exit_code'], 128 + signum)
                    self.assertTrue(all(s['status'] == 'skipped' for s in data['checks'][1:]))
                    with self.assertRaises(ProcessLookupError):
                        os.kill(child, 0)
                finally:
                    if process.poll() is None:
                        process.kill()
                        process.communicate()
                    self.child_pid.unlink(missing_ok=True)

    def test_malformed_incomplete_and_unsupported_evidence_is_rejected(self):
        _, original, path = self.run_profile()
        for content in ('not json', '[]', '{}', json.dumps({**original, 'schema_version': 99}),
                        json.dumps({**original, 'status': 'running'}),
                        json.dumps({**original, 'checks': []})):
            with self.subTest(content=content[:45]):
                path.write_text(content)
                result = self.run_cli('--check', str(path))
                self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
                self.assertNotIn('Traceback', result.stderr)
        self.assertEqual(self.run_cli('--check', str(self.directory / 'missing')).returncode, 1)

    def test_untracked_content_and_executable_mode_are_part_of_source_identity(self):
        source = self.root / 'untracked-script'
        source.write_text('one')
        source.chmod(0o644)
        result, _, path = self.run_profile()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        source.write_text('two')  # Same name, size, and Git status.
        self.assertEqual(self.run_cli('--check', str(path)).returncode, 1)
        source.write_text('one')
        self.assertEqual(self.run_cli('--check', str(path)).returncode, 0)
        source.chmod(0o755)
        self.assertEqual(self.run_cli('--check', str(path)).returncode, 1)

    def test_complete_evidence_requires_valid_identity_times_and_numeric_fields(self):
        _, original, path = self.run_profile()
        variants = []
        for key in ('run_id', 'started_at'):
            variant = dict(original)
            del variant[key]
            variants.append(variant)
        variants.append({**original, 'exit_code': False})
        variants.append({**original, 'finished_at': 'not a timestamp'})
        for key, value in (('selected', 1), ('exit_code', False),
                           ('duration_seconds', float('nan')),
                           ('finished_at', '1900-01-01T00:00:00+00:00')):
            variant = json.loads(json.dumps(original))
            variant['checks'][0][key] = value
            variants.append(variant)
        for variant in variants:
            with self.subTest(variant=variant.get('finished_at')):
                path.write_text(json.dumps(variant))
                result = self.run_cli('--check', str(path))
                self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
                self.assertNotIn('Traceback', result.stderr)

    def test_successful_command_with_evidence_failure_cannot_report_success(self):
        output = self.directory / 'unwritable-success'
        try:
            result, data, _ = self.run_profile(env={'LOCK_OUTPUT': '1', 'RUN_OUTPUT': str(output)},
                                               output=output)
            self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
            self.assertNotEqual(data['status'], 'passed')
            self.assertNotIn('Verification passed', result.stdout)
        finally:
            output.chmod(0o700)

    def test_executed_command_with_missing_log_is_failed_not_skipped(self):
        output = self.directory / 'missing-log'
        result, data, _ = self.run_profile(env={'REMOVE_LOG': '1', 'RUN_OUTPUT': str(output)},
                                           output=output)
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertEqual(data['checks'][0]['status'], 'failed')
        self.assertEqual(data['checks'][0]['exit_code'], 1)
        self.assertEqual(len(self.captured()), 1)
        self.assertTrue(all(c['status'] == 'skipped' for c in data['checks'][1:]))

    def test_relative_path_tool_identity_uses_command_working_directory(self):
        options = {'PATH': 'bin' + os.pathsep + os.environ['PATH']}
        result, data, path = self.run_profile(env=options)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(data['execution']['tools']['npm'], str(self.root / 'bin/npm'))
        result = self.run_cli('--check', str(path), env=options)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_current_ci_and_build_commands_are_reused(self):
        _, data, _ = self.run_profile('full', True)
        workflow = (SCRIPTS.parent / '.github/workflows/verify.yml').read_text()
        wrapper = (SCRIPTS / 'build.sh').read_text()
        for item in data['checks']:
            if item['id'] in ('frontend-analysis', 'frontend-build', 'backend-verify', 'backend-format'):
                continue  # Wrapper-owned commands or explicit local formatting/BE scope.
            if item['id'] == 'package':
                self.assertIn('./scripts/build.sh', workflow)
                self.assertIn('clean verify', wrapper)
            else:
                command = ' '.join(item['argv'])
                self.assertIn(command, workflow.replace("'", ''))


if __name__ == '__main__':
    unittest.main()
