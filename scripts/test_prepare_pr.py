"""Read-only handoff preparation in isolated repositories with real evidence."""

import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import unittest

import test_verify as runner_tests

SCRIPTS = Path(__file__).resolve().parent
TEMPLATE = SCRIPTS.parent / '.github/pull_request_template.md'


class PreparePullRequestTest(unittest.TestCase):
    def setUp(self):
        self.fixture = runner_tests.LocalVerificationTest()
        self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups)
        self.root = self.fixture.root
        self.directory = self.fixture.directory
        for name in ('prepare-pr.py', 'change_handoff.py'):
            shutil.copy2(SCRIPTS / name, self.root / 'scripts' / name)
        (self.root / '.github').mkdir()
        shutil.copy2(TEMPLATE, self.root / '.github/pull_request_template.md')
        (self.root / 'spec').mkdir()
        (self.root / 'docs/adr').mkdir(parents=True)
        (self.root / 'spec/task.md').write_text('# Task\n')
        (self.root / 'docs/adr/0001-example.md').write_text('# Decision\n')
        self.git('add', '.')
        self.git('commit', '-qm', 'test: add handoff fixtures')
        self.git('branch', '-M', 'main')
        self.git('update-ref', 'refs/remotes/origin/main', 'HEAD')
        self.git('remote', 'add', 'origin', 'https://github.com/example/project.git')
        self.git('checkout', '-qb', 'feat/handoff')
        (self.root / 'source.txt').write_text('planned change')
        self.summary = self.directory / 'summary.md'
        self.summary.write_text('Record the problem and resulting behavior.\n\nPreserve this paragraph.\n')
        self.review = self.directory / 'review.md'
        self.review.write_text('Review cleanup and exact scope. No data migration.\n')
        self.command = [sys.executable, '-B', str(self.root / 'scripts/prepare-pr.py')]

    def git(self, *args):
        return self.fixture.git(*args)

    def evidence(self):
        result, _, path = self.fixture.run_profile()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        return path

    def args(self, output, paths=None):
        values = ['--branch', 'feat/handoff', '--title', 'feat(handoff): prepare a pull request',
                  '--summary-file', str(self.summary), '--review-file', str(self.review),
                  '--spec', 'spec/task.md', '--adr', 'docs/adr/0001-example.md',
                  '--output', str(output)]
        for path in paths if paths is not None else ['source.txt']:
            values += ['--path', path]
        return values

    def cli(self, args):
        return subprocess.run([*self.command, *args], cwd=self.directory,
                              env=self.fixture.env, capture_output=True, text=True, timeout=15)

    def prepare(self, paths=None, extra=(), evidence='current'):
        path = self.evidence() if evidence == 'current' else evidence
        output = self.directory / f'handoff-{time.time_ns()}'
        args = self.args(output, paths) + list(extra)
        if path is not None:
            args += ['--evidence', str(path)]
        result = self.cli(args)
        report = output / 'handoff.json'
        self.assertTrue(report.is_file(), result.stdout + result.stderr)
        return result, json.loads(report.read_text()), output

    def codes(self, report):
        return {item['code'] for item in report['findings']}

    def test_current_evidence_precise_scope_and_template_sections(self):
        result, data, output = self.prepare()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertTrue(data['local_checks_passed'])
        self.assertEqual(data['stage_paths'], ['source.txt'])
        self.assertEqual(data['scope'], ['source.txt'])
        self.assertEqual(data['evidence'][0]['state'], 'current')
        self.assertEqual(data['evidence'][0]['profile'], 'frontend')
        self.assertEqual(data['repository'], 'example/project')
        draft = (output / 'pr.md').read_text()
        for heading in ('Change', 'Specification and decisions', 'Verification', 'Review and release considerations'):
            self.assertIn('## ' + heading, draft)
        self.assertIn(self.summary.read_text(), draft)
        self.assertIn(self.review.read_text(), draft)
        self.assertIn('https://github.com/example/project/blob/feat/handoff/spec/task.md', draft)
        self.assertIn('before staging', draft)
        self.assertIn('not verification of the resulting commit', draft)
        self.assertIn('does not deploy', draft)
        self.assertTrue({'github-history', 'base-freshness', 'ci', 'diff-review', 'authorization'}
                        <= {item['id'] for item in data['pending']})

    def test_unrelated_changes_excluded_and_index_refs_and_evidence_untouched(self):
        (self.root / 'feedback.txt').write_text('user work')
        self.git('status', '--porcelain')
        evidence = self.evidence()
        before_index = (self.root / '.git/index').read_bytes()
        before_status = self.git('status', '--porcelain=v1', '-z')
        before_evidence = evidence.read_bytes()
        before_head = self.git('rev-parse', 'HEAD')
        result, data, output = self.prepare(evidence=evidence)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(data['excluded_paths'], ['feedback.txt'])
        self.assertNotIn('feedback.txt', data['stage_paths'])
        self.assertEqual((self.root / '.git/index').read_bytes(), before_index)
        self.assertEqual(self.git('status', '--porcelain=v1', '-z'), before_status)
        self.assertEqual(self.git('rev-parse', 'HEAD'), before_head)
        self.assertEqual(evidence.read_bytes(), before_evidence)
        self.assertEqual((output / 'handoff.json').stat().st_mode & 0o777, 0o600)
        self.assertEqual((output / 'pr.md').stat().st_mode & 0o777, 0o600)
        self.assertEqual(output.stat().st_mode & 0o777, 0o700)

    def test_staged_outside_scope_blocks_without_unstaging(self):
        (self.root / 'unrelated.txt').write_text('queued by user')
        self.git('add', 'unrelated.txt')
        result, data, _ = self.prepare()
        self.assertEqual(result.returncode, 1)
        self.assertIn('staged-outside-scope', self.codes(data))
        self.assertIn('unrelated.txt', self.git('diff', '--cached', '--name-only'))

    def test_committed_pr_changes_must_be_in_scope(self):
        (self.root / 'unrelated.txt').write_text('already committed')
        self.git('add', 'source.txt', 'unrelated.txt')
        self.git('commit', '-qm', 'feat: add both changes')
        result, data, _ = self.prepare()
        self.assertEqual(result.returncode, 1)
        self.assertIn('committed-outside-scope', self.codes(data))
        result, data, _ = self.prepare(paths=['source.txt', 'unrelated.txt'])
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(data['stage_paths'], [])

    def test_staged_and_unstaged_parts_of_same_file(self):
        self.git('add', 'source.txt')
        (self.root / 'source.txt').write_text('latest working contents')
        result, data, _ = self.prepare()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn('source.txt', data['staged_paths'])
        self.assertIn('source.txt', data['unstaged_paths'])
        self.assertEqual(data['stage_paths'], ['source.txt'])

    def test_rename_endpoints_and_already_staged_deletions_have_safe_candidates(self):
        (self.root / 'source.txt').write_text('original')
        (self.root / 'source.txt').rename(self.root / 'renamed.txt')
        self.git('add', 'source.txt', 'renamed.txt')
        result, data, _ = self.prepare(paths=['renamed.txt'])
        self.assertEqual(result.returncode, 1)
        self.assertIn('rename-scope', self.codes(data))
        result, data, _ = self.prepare(paths=['source.txt', 'renamed.txt'])
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(data['stage_paths'], [])
        self.git('commit', '-qm', 'refactor: rename source')
        (self.root / 'renamed.txt').unlink()
        result, data, _ = self.prepare(paths=['source.txt', 'renamed.txt'])
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(data['stage_paths'], ['renamed.txt'])

    def test_literal_names_are_not_expanded_or_evaluated(self):
        names = ['literal [x] $(touch nope).txt', ':(glob)*.txt', 'line\nbreak`name.txt']
        for name in names:
            (self.root / name).write_text('new file')
        result, data, output = self.prepare(paths=['source.txt', *names])
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(data['stage_paths'], sorted(['source.txt', *names]))
        self.assertFalse((self.root / 'nope').exists())
        self.assertIn('line\\nbreak', (output / 'pr.md').read_text())
        self.git('--literal-pathspecs', 'add', '--', *data['stage_paths'])
        staged = self.git('diff', '--cached', '--name-only', '-z').split(chr(0))
        self.assertTrue(set(names) <= set(staged))

    def test_invalid_scope_paths_and_duplicates(self):
        for paths in (['../outside'], ['/etc/passwd'], ['.git/config'], ['scripts'],
                      ['source.txt', 'source.txt'], ['missing.txt'], ['spec/task.md']):
            with self.subTest(paths=paths):
                result, data, _ = self.prepare(paths=paths)
                self.assertEqual(result.returncode, 1)
                self.assertTrue(any(x.startswith('scope-') for x in self.codes(data)))

    def test_branch_and_base_checks_and_main_are_local_only(self):
        result, data, _ = self.prepare(extra=['--branch', 'feat/other'])
        self.assertEqual(result.returncode, 1)
        self.assertIn('branch-mismatch', self.codes(data))
        self.git('checkout', '--detach', '-q')
        result, data, _ = self.prepare()
        self.assertEqual(result.returncode, 1)
        self.assertIn('detached-head', self.codes(data))
        self.git('checkout', '-q', 'main')
        result, data, _ = self.prepare(extra=['--branch', 'main'])
        self.assertEqual(result.returncode, 1)
        self.assertIn('main-branch', self.codes(data))
        self.git('checkout', '-q', 'feat/handoff')
        self.git('update-ref', '-d', 'refs/remotes/origin/main')
        result, data, _ = self.prepare()
        self.assertEqual(result.returncode, 1)
        self.assertIn('base-missing', self.codes(data))

    def test_diverged_base_blocks_preparation(self):
        self.git('add', 'source.txt')
        self.git('commit', '-qm', 'feat: add topic work')
        self.git('checkout', '-q', 'main')
        (self.root / 'base.txt').write_text('main advanced')
        self.git('add', 'base.txt')
        self.git('commit', '-qm', 'docs: advance main')
        self.git('update-ref', 'refs/remotes/origin/main', 'HEAD')
        self.git('checkout', '-q', 'feat/handoff')
        result, data, _ = self.prepare()
        self.assertEqual(result.returncode, 1)
        self.assertIn('base-not-integrated', self.codes(data))

    def test_title_rules_and_valid_custom_types_breaking_scope(self):
        for title in ('missing prefix', 'feat: Capitalized', 'feat: add HTTP',
                      'feat: ', 'feat: one\ntwo', 'feat: add\x01control'):
            with self.subTest(title=title):
                result, data, _ = self.prepare(extra=['--title', title])
                self.assertEqual(result.returncode, 1)
                self.assertIn('title', self.codes(data))
        result, data, _ = self.prepare(extra=['--title', 'custom(api)!: change the contract'])
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertTrue(data['breaking_change'])

    def test_reference_constraints_and_unpublished_documentation(self):
        for args in (['--spec', 'spec/missing.md'], ['--spec', 'source.txt'],
                     ['--adr', 'source.txt'], ['--spec', '../outside.md']):
            with self.subTest(args=args):
                result, data, _ = self.prepare(extra=args)
                self.assertEqual(result.returncode, 1)
                self.assertTrue({'references', 'reference-unpublished'} & self.codes(data))
        (self.root / 'spec/new.md').write_text('# New\n')
        result, data, _ = self.prepare(extra=['--spec', 'spec/new.md'])
        self.assertEqual(result.returncode, 1)
        self.assertIn('reference-unpublished', self.codes(data))
        result, _, _ = self.prepare(paths=['source.txt', 'spec/new.md'], extra=['--spec', 'spec/new.md'])
        self.assertEqual(result.returncode, 0)

    def test_rejected_and_missing_evidence_and_manual_notes_are_honest(self):
        evidence = self.evidence()
        (self.root / 'source.txt').write_text('changed since verification')
        original = evidence.read_bytes()
        result, data, output = self.prepare(evidence=evidence)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(data['evidence'][0]['state'], 'rejected')
        self.assertIn('verification', {x['id'] for x in data['pending']})
        self.assertNotIn('frontend-lint: passed', (output / 'pr.md').read_text())
        self.assertEqual(evidence.read_bytes(), original)
        result, data, output = self.prepare(evidence=None, extra=['--verification-note', 'Reviewed manual checks.'])
        self.assertEqual(result.returncode, 0)
        self.assertEqual(data['evidence'], [])
        self.assertIn('Author-provided', (output / 'pr.md').read_text())
        self.assertIn('manual-verification-review', {x['id'] for x in data['pending']})
        evidence.write_text('invalid json')
        _, data, _ = self.prepare(evidence=evidence)
        self.assertEqual(data['evidence'][0]['state'], 'rejected')

    def test_credentials_are_not_exposed_and_repository_mismatch_blocks(self):
        result, data, _ = self.prepare(extra=['--repository', 'other/project'])
        self.assertEqual(result.returncode, 1)
        self.assertIn('repository-mismatch', self.codes(data))
        self.git('remote', 'set-url', 'origin', 'https://user:never-print-this@github.com/example/project.git')
        result, data, output = self.prepare()
        self.assertEqual(result.returncode, 0)
        self.assertIsNone(data['repository'])
        self.assertNotIn('never-print-this', json.dumps(data) + (output / 'pr.md').read_text() + result.stderr)
        self.git('remote', 'set-url', 'origin', 'git@github.com:example/project.git')
        _, data, _ = self.prepare()
        self.assertEqual(data['repository'], 'example/project')

    def test_template_and_input_failures_are_actionable_without_traceback(self):
        self.summary.write_text('   ')
        result, _, _ = self.prepare()
        self.assertEqual(result.returncode, 1)
        self.summary.write_text('Summary')
        (self.root / '.github/pull_request_template.md').write_text('## Different\n')
        result, data, _ = self.prepare()
        self.assertEqual(result.returncode, 1)
        self.assertIn('template', self.codes(data))
        self.assertNotIn('Traceback', result.stderr)

    def test_unsafe_outputs_and_invalid_cli_do_not_write_to_repository(self):
        output = self.directory / 'existing'
        output.mkdir()
        keep = output / 'keep'
        keep.write_text('unchanged')
        alias = self.directory / 'alias'
        alias.symlink_to(output, target_is_directory=True)
        for target in (output, alias / 'new', self.root / 'report', self.directory / 'missing/new'):
            with self.subTest(target=target):
                result = self.cli(self.args(target))
                self.assertEqual(result.returncode, 1)
                self.assertNotIn('Traceback', result.stderr)
        self.assertEqual(keep.read_text(), 'unchanged')
        self.assertFalse((self.root / 'report').exists())
        self.assertEqual(self.cli([]).returncode, 2)
        self.assertEqual(self.cli(['--help']).returncode, 0)
        self.assertEqual(self.cli([*self.args(self.directory / 'new'), '--unknown']).returncode, 2)

    def test_damaged_logs_are_rejected_without_copying_successes(self):
        evidence = self.evidence()
        original = evidence.read_bytes()
        metadata = json.loads(original)
        log = next(check['log'] for check in metadata['checks'] if check['status'] == 'passed')
        (evidence.parent / log).write_text('damaged log')
        result, data, output = self.prepare(evidence=evidence)
        self.assertEqual(result.returncode, 0)
        self.assertEqual(data['evidence'][0]['state'], 'rejected')
        self.assertNotIn('checks', data['evidence'][0])
        self.assertNotIn('Recorded local profile', (output / 'pr.md').read_text())
        self.assertEqual(evidence.read_bytes(), original)

    def test_no_changes_and_invalid_utf8_inputs_produce_findings(self):
        (self.root / 'source.txt').write_text('original')
        result, data, _ = self.prepare()
        self.assertEqual(result.returncode, 1)
        self.assertIn('scope-empty', self.codes(data))
        (self.root / 'source.txt').write_text('planned change')
        self.summary.write_bytes(b'\xff')
        result, data, _ = self.prepare()
        self.assertEqual(result.returncode, 1)
        self.assertIn('inputs', self.codes(data))
        self.assertNotIn('Traceback', result.stderr)

    def test_source_change_during_preparation_is_reported(self):
        tool = self.root / 'bin/git'
        real_git = shutil.which('git')
        tool.unlink()
        tool.write_text(f'#!{sys.executable}\nimport os, pathlib, sys\n'
                        f'if "config" in sys.argv:\n    pathlib.Path({str(self.root / "source.txt")!r}).write_text("concurrent edit")\n'
                        f'os.execv({real_git!r}, [{real_git!r}, *sys.argv[1:]])\n')
        tool.chmod(0o755)
        result, data, _ = self.prepare(evidence=None)
        self.assertEqual(result.returncode, 1)
        self.assertIn('source-changed', self.codes(data))

    def test_squash_merged_branch_still_requires_live_history_review(self):
        self.git('add', 'source.txt')
        self.git('commit', '-qm', 'feat: complete original change')
        self.git('checkout', '-q', 'main')
        self.git('merge', '--squash', 'feat/handoff')
        self.git('commit', '-qm', 'feat: squash original change')
        self.git('update-ref', 'refs/remotes/origin/main', 'HEAD')
        self.git('checkout', '-q', 'feat/handoff')
        self.git('merge', '--no-ff', 'main', '-m', 'chore: integrate main')
        (self.root / 'source.txt').write_text('new task on completed branch')
        result, data, _ = self.prepare()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn('github-history', {item['id'] for item in data['pending']})
        # Passing local checks explicitly leave the completed-branch decision unresolved.
        self.assertNotIn('ready_to_publish', data)

    def test_reference_links_encode_paths_and_escape_markdown_labels(self):
        name = 'spec/a [draft].md'
        (self.root / name).write_text('# Reference\n')
        result, _, output = self.prepare(paths=['source.txt', name], extra=['--spec', name])
        self.assertEqual(result.returncode, 0)
        draft = (output / 'pr.md').read_text()
        self.assertIn('/spec/a%20%5Bdraft%5D.md', draft)
        self.assertIn(r'[spec/a \[draft\].md](', draft)

    def test_note_and_evidence_inputs_require_regular_files(self):
        _, data, _ = self.prepare(extra=['--summary-file', '/dev/null'], evidence=Path('/dev/null'))
        self.assertTrue(any(item['code'] == 'inputs' and 'regular' in item['message']
                            for item in data['findings']))
        self.assertEqual(data['evidence'][0]['state'], 'rejected')
        self.assertIn('regular', data['evidence'][0]['reason'])

    def test_routine_reason_can_replace_specification_without_inventing_approval(self):
        output = self.directory / 'routine'
        args = self.args(output)
        position = args.index('--spec')
        del args[position:position + 2]
        result = self.cli(args + ['--routine-reason', 'Routine documentation correction.'])
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertTrue((output / 'handoff.json').is_file(), result.stdout + result.stderr)
        data = json.loads((output / 'handoff.json').read_text())
        self.assertIn('verification', {x['id'] for x in data['pending']})
        self.assertIn('Routine documentation correction.', (output / 'pr.md').read_text())
        args[args.index('--output') + 1] = str(self.directory / 'no-rationale')
        result = self.cli(args)
        self.assertEqual(result.returncode, 1)
        data = json.loads((self.directory / 'no-rationale/handoff.json').read_text())
        self.assertIn('references', self.codes(data))


    def test_task_evidence_with_local_settings_is_consumed_without_environment_wrappers(self):
        config = self.root / '.verification.local.json'
        # Browser paths support spaces and have no JVM argument splitting.
        config.write_text(json.dumps({'chromePath': str(self.root / 'bin/npm')}))
        self.fixture.env.pop('CHROME_PATH', None)
        result = self.fixture.run_cli('--task', 'handoff-check', 'browser')
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        path = Path(next(line.removeprefix('Manifest: ') for line in result.stdout.splitlines()
                         if line.startswith('Manifest: ')))
        self.addCleanup(shutil.rmtree, path.parent)
        result, data, _ = self.prepare(evidence=path)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertTrue(data['local_checks_passed'])
        self.assertEqual(data['evidence'][0]['state'], 'current')
        self.assertEqual(data['evidence'][0]['profile'], 'browser')
        config.write_text(json.dumps({'chromePath': str(self.root / 'bin/mvn')}))
        result, data, _ = self.prepare(evidence=path)
        self.assertEqual(result.returncode, 0)
        self.assertEqual(data['evidence'][0]['state'], 'rejected')
        self.assertNotIn('checks', data['evidence'][0])


if __name__ == '__main__':
    unittest.main()
