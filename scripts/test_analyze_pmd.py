"""Behavior tests for the read-only PMD report analyzer."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('analyze-pmd.py')


class AnalyzerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.report = self.root / 'pmd.xml'

    def xml(self, body='', version='7.17.0'):
        self.report.write_text(
            f'<pmd xmlns="http://pmd.sourceforge.net/report/2.0.0" '
            f'version="{version}" timestamp="2026-10-14">{body}</pmd>')

    def run_cli(self, *args):
        return subprocess.run(
            [sys.executable, str(SCRIPT), str(self.report), '--root', str(self.root), *args],
            capture_output=True, text=True, cwd=self.root)

    def summary(self, *args):
        output = self.root / 'summary.json'
        result = self.run_cli('--json', str(output), *args)
        self.assertIn(result.returncode, [0, 2], result.stderr)
        self.assertTrue(output.exists(), result.stderr)
        return json.loads(output.read_text()), result

    def test_counts_scopes_duplicates_and_cross_checkout_paths(self):
        violation = '<violation rule="GodClass" priority="3" beginline="2">message</violation>'
        self.xml(f'<file name="/old checkout/backend/src/main/java/A.java">{violation * 2}</file>'
                 '<file name="backend/src/test/java/T.java"><violation rule="ShortVariable" '
                 'priority="4" beginline="1">short</violation></file>'
                 f'<file name="misc/U.java">{violation}</file>')
        data, result = self.summary()
        self.assertEqual(data['total'], 4)
        self.assertEqual(data['scopes'], {'production': 2, 'tests': 1, 'other': 1})
        self.assertEqual(data['rules'], {'GodClass': 3, 'ShortVariable': 1})
        self.assertEqual(data['priorities'], {'3': 3, '4': 1})
        self.assertEqual(data['files']['backend/src/main/java/A.java'], 2)
        self.assertEqual(sum(data['files'].values()), data['total'])
        self.assertIn('4', result.stdout)
        self.assertTrue(data['complete'])

    def test_empty_report_is_valid(self):
        self.xml()
        data, _ = self.summary()
        self.assertEqual(data['total'], 0)
        self.assertEqual(data['rules'], {})
        self.assertTrue(data['complete'])

    def test_suppressions_and_diagnostics_are_not_findings(self):
        self.xml('<suppressedviolation filename="A.java" suppressiontype="nopmd" '
                 'msg="suppressed" usermsg="reason"/>'
                 '<error filename="B.java" msg="parse failed">trace</error>'
                 '<configerror rule="Broken" msg="bad config"/>')
        data, result = self.summary()
        self.assertEqual(result.returncode, 2)
        self.assertEqual(data['total'], 0)
        self.assertFalse(data['complete'])
        self.assertEqual(len(data['diagnostics']), 2)
        self.assertEqual(len(data['suppressions']), 1)
        self.assertIn('parse failed', result.stdout)
        self.assertIn('bad config', result.stdout)

    def test_baseline_deltas_and_version_warning(self):
        self.xml('<file name="backend/src/main/java/A.java"><violation rule="GodClass" '
                 'priority="3"/></file>', '7.16.0')
        before, _ = self.summary()
        baseline = self.root / 'baseline.json'
        baseline.write_text(json.dumps(before))
        (self.root / 'summary.json').unlink()
        self.xml('<file name="/another/backend/src/main/java/A.java"><violation '
                 'rule="GodClass" priority="3"/><violation rule="ShortVariable" priority="3"/></file>')
        after, result = self.summary('--baseline', str(baseline))
        self.assertEqual(after['comparison']['total'], 1)
        self.assertEqual(after['comparison']['rules'], {'GodClass': 0, 'ShortVariable': 1})
        self.assertEqual(after['comparison']['files']['backend/src/main/java/A.java'], 1)
        self.assertTrue(after['comparison']['pmd_version_changed'])
        self.assertIn('count', result.stdout.lower())
        self.assertIn('version', result.stdout.lower())

    def test_round_trip_has_zero_deltas(self):
        self.xml()
        data, _ = self.summary()
        baseline = self.root / 'baseline.json'
        baseline.write_text(json.dumps(data))
        result = self.run_cli('--baseline', str(baseline))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('0', result.stdout)

    def test_invalid_inputs_fail_without_outputs(self):
        for text in ['not xml', '<wrong/>', '<!DOCTYPE pmd><pmd/>',
                     '<pmd><file name="A"><violation priority="x"/></file></pmd>']:
            with self.subTest(text=text):
                self.report.write_text(text)
                output = self.root / 'bad.json'
                result = self.run_cli('--json', str(output))
                self.assertEqual(result.returncode, 1, result.stderr)
                self.assertFalse(output.exists())
        self.report.unlink()
        self.assertEqual(self.run_cli().returncode, 1)

    def test_dtd_is_rejected_for_utf16_xml(self):
        self.report.write_bytes(
            '<?xml version="1.0" encoding="UTF-16"?><!DOCTYPE pmd><pmd/>'.encode('utf-16'))
        self.assertEqual(self.run_cli().returncode, 1)

    def test_invalid_baselines_are_rejected(self):
        self.xml()
        baseline = self.root / 'baseline.json'
        for data in ['not json', json.dumps({'schema_version': 99}),
                     json.dumps({'schema_version': 1, 'total': -1})]:
            with self.subTest(data=data):
                baseline.write_text(data)
                self.assertEqual(self.run_cli('--baseline', str(baseline)).returncode, 1)

    def test_outputs_are_created_without_clobbering(self):
        self.xml()
        markdown = self.root / 'analysis.md'
        data, _ = self.summary('--markdown', str(markdown))
        self.assertIn('PMD', markdown.read_text())
        original = markdown.read_text()
        self.assertEqual(self.run_cli('--markdown', str(markdown)).returncode, 1)
        self.assertEqual(markdown.read_text(), original)
        source = self.report.read_text()
        self.assertEqual(self.run_cli('--json', str(self.report)).returncode, 1)
        self.assertEqual(self.report.read_text(), source)
        self.assertEqual(self.run_cli('--markdown', str(self.root / 'same'),
                                     '--json', str(self.root / 'same')).returncode, 1)
        self.assertFalse((self.root / 'same').exists())
        self.assertEqual(self.run_cli('--json', str(self.root / 'missing' / 'a.json')).returncode, 1)


if __name__ == '__main__':
    unittest.main()
