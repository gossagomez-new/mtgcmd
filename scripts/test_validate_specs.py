"""Behavioral fixtures for offline specification/index validation."""

from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('validate-specs.py')


class SpecificationValidationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='spec validation ')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'spec').mkdir()
        (self.root / 'docs/adr').mkdir(parents=True)
        (self.root / 'docs/adr/0001-example.md').write_text('# Example decision\n')
        self.spec('a', 'Alpha')
        self.spec('b', 'Beta', '[Alpha](a.md)')
        self.index()

    def spec(self, key, title, deps='None', status='Completed', adr='None.'):
        (self.root / f'spec/{key}.md').write_text(
            f'# {title}\n\n## Metadata\n\n- Status: {status}\n- Priority: Medium\n'
            f'- Priority rationale: Repeatable checks.\n- Depends on: {deps}\n\n'
            '## Verification\n\nUser selected comprehensive coverage.\n\n'
            f'## Architecture decisions\n\n{adr}\n\n## Open questions\n\nNone.\n')

    def index(self, extra='', graph=None):
        if graph is None:
            graph = 'a["Alpha (independent)"]\nb["Beta"]\nb -->|depends on| a'
        (self.root / 'spec/INDEX.md').write_text(
            '# Specification index\n\n'
            '| Specification | Status | Priority | Purpose | Depends on | Related ADRs |\n'
            '| --- | --- | --- | --- | --- | --- |\n'
            '| [Alpha](a.md) | Completed | Medium | Base | None | None |\n\n'
            '| [Beta](b.md) | Completed | Medium | Child | [Alpha](a.md) | None |\n'
            f'{extra}\n## Dependency graph\n\n```mermaid\nflowchart TD\n{graph}\n```\n')

    def edit(self, path, before, after):
        target = self.root / path
        target.write_text(target.read_text().replace(before, after))

    def run_cli(self, *args):
        return subprocess.run([sys.executable, '-B', str(SCRIPT), '--root',
                               str(self.root), *args], cwd=self.root,
                              text=True, capture_output=True)

    def valid(self):
        result = self.run_cli()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn('validated', result.stdout.lower())

    def invalid(self, *terms):
        result = self.run_cli()
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertNotIn('Traceback', result.stderr)
        for term in terms:
            self.assertIn(term.lower(), result.stdout.lower() + result.stderr.lower())
        return result

    def test_valid_multiple_tables_shared_dependencies_independent_nodes(self):
        self.spec('c', 'Gamma', '[Alpha](a.md), [Beta](b.md)')
        self.index(extra='| Specification | Status | Priority | Purpose | Depends on | Related ADRs |\n'
                         '| --- | --- | --- | --- | --- | --- |\n'
                         '| [Gamma](c.md) | Completed | Medium | Shared | [Beta](b.md), [Alpha](a.md) | None |',
                   graph='a["Alpha (independent)"]\nb["Beta"]\nc["Gamma"]\n'
                         'b -->|depends on| a\nc -->|depends on| a\nc -->|depends on| b')
        self.valid()

    def test_templates_and_fenced_inline_code_are_not_documents_or_links(self):
        (self.root / 'spec/TEMPLATE.md').write_text('<Placeholder> TODO [bad](missing)')
        (self.root / 'spec/INDEX-TEMPLATE.md').write_text('TBD')
        with (self.root / 'spec/a.md').open('a') as out:
            out.write('\n```md\nTODO [bad](missing)\n```\n'
                      '\n~~~text\nTBD\n~~~\n`[bad](missing)`\n')
        self.valid()

    def test_missing_invalid_and_duplicate_metadata(self):
        for before, after, term in [
            ('- Status: Completed', '- Status: Finished', 'status'),
            ('- Priority: Medium', '- Priority: Urgent', 'priority'),
            ('- Priority rationale: Repeatable checks.', '- Priority rationale:', 'rationale'),
            ('- Depends on: None', '', 'depends on'),
            ('- Status: Completed', '- Status: Completed\n- Status: Draft', 'status'),
        ]:
            with self.subTest(term=term):
                self.spec('a', 'Alpha')
                self.edit('spec/a.md', before, after)
                self.invalid('spec/a.md:', term)

    def test_all_allowed_statuses_priorities(self):
        for status in ('Draft', 'Approved', 'In progress', 'Completed', 'Superseded'):
            for priority in ('Critical', 'High', 'Medium', 'Low'):
                with self.subTest(status=status, priority=priority):
                    self.spec('a', 'Alpha', status=status)
                    self.index()
                    self.edit('spec/a.md', 'Priority: Medium', f'Priority: {priority}')
                    self.edit('spec/INDEX.md', '[Alpha](a.md) | Completed | Medium',
                              f'[Alpha](a.md) | {status} | {priority}')
                    self.valid()

    def test_required_sections_and_title(self):
        for before in ('# Alpha', '## Metadata', '## Verification', '## Architecture decisions'):
            with self.subTest(before=before):
                self.spec('a', 'Alpha')
                self.edit('spec/a.md', before, 'Unstructured text')
                self.invalid('spec/a.md:', 'required')

    def test_coverage_record_presence_and_compatible_wording(self):
        for wording in ('Confirmed target: risk-focused coverage.',
                        'User chose **comprehensive verification**.',
                        'User confirmed **comprehensive coverage**.',
                        'The user selected **risk-focused coverage**.'):
            self.spec('a', 'Alpha')
            self.edit('spec/a.md', 'User selected comprehensive coverage.', wording)
            self.valid()
        self.edit('spec/a.md', wording, 'Run tests.')
        self.invalid('coverage')

    def test_explicit_placeholders_rejected_even_in_drafts(self):
        for marker in ('TODO: finish', 'TBD', 'FIXME', '<Reason for priority>', '<spec-name>'):
            with self.subTest(marker=marker):
                self.spec('a', 'Alpha')
                with (self.root / 'spec/a.md').open('a') as out:
                    out.write('\n' + marker + '\n')
                self.invalid('placeholder')
        self.spec('a', 'Alpha', status='Draft')
        self.edit('spec/a.md', 'Repeatable checks.', 'TBD')
        self.invalid('placeholder')

    def test_local_inline_reference_images_anchors_and_external_links(self):
        (self.root / 'docs/guide space.md').write_text(
            '# Guide\n## A heading!\n## A heading!\n<a id="custom"></a>\n')
        with (self.root / 'spec/a.md').open('a') as out:
            out.write('\n[one](../docs/guide%20space.md#a-heading)\n'
                      '[two](<../docs/guide space.md#a-heading-1>)\n'
                      '[custom][guide]\n[guide]: <../docs/guide space.md#custom>\n'
                      '![image](../docs/guide%20space.md)\n'
                      '[web](https://invalid.example/no-network) [mail](mailto:a@example.com)\n'
                      '[relative](//invalid.example/path)\n'
                      '[local](#alpha)\n')
        self.valid()
        self.assertIn('3 external', self.run_cli().stdout)
        self.edit('spec/a.md', '#a-heading-1', '#missing')
        self.invalid('anchor', 'missing')

    def test_broken_and_undefined_links(self):
        for link, term in [('[bad](missing.md)', 'missing.md'),
                           ('[bad][absent]', 'reference'),
                           ('[bad](../docs/guide%20space.md)', 'guide space.md'),
                           ('[bad](a.md#absent)', 'anchor')]:
            with self.subTest(link=link):
                self.spec('a', 'Alpha')
                with (self.root / 'spec/a.md').open('a') as out:
                    out.write('\n' + link + '\n')
                self.invalid(term)

    def test_repository_escape_and_symlink_escape_rejected(self):
        for target in ('../../outside.md', '/etc/passwd'):
            self.spec('a', 'Alpha')
            with (self.root / 'spec/a.md').open('a') as out:
                out.write(f'\n[escape]({target})\n')
            self.invalid('outside repository')
        (self.root / 'spec/escape.md').symlink_to('/etc/passwd')
        self.spec('a', 'Alpha')
        with (self.root / 'spec/a.md').open('a') as out:
            out.write('\n[escape](escape.md)\n')
        self.invalid('outside repository')

    def test_dependency_contract_and_cycles(self):
        for deps, term in [('[Self](a.md)', 'self'),
                           ('[Missing](missing.md)', 'dependency'),
                           ('[ADR](../docs/adr/0001-example.md)', 'specification'),
                           ('None, [Beta](b.md)', 'depends on'),
                           ('[Beta](b.md), [Beta](b.md)', 'duplicate'),
                           ('[Web](https://example.com)', 'dependency')]:
            with self.subTest(deps=deps):
                self.spec('a', 'Alpha', deps)
                self.invalid(term)
        self.spec('a', 'Alpha', '[Beta](b.md)')
        self.invalid('cycle', 'a.md', 'b.md')

    def test_index_missing_extra_duplicate_mismatch_and_purpose(self):
        for before, after, term in [
            ('| [Alpha](a.md) | Completed', '| [Alpha](a.md) | Approved', 'status'),
            ('| Completed | Medium | Base', '| Completed | Low | Base', 'priority'),
            ('| Base | None', '| Base | [Beta](b.md)', 'dependencies'),
            ('| Base |', '|  |', 'purpose'),
            ('| [Alpha](a.md)', '| [Ghost](missing.md)', 'index'),
        ]:
            with self.subTest(term=term):
                self.index()
                self.edit('spec/INDEX.md', before, after)
                self.invalid('spec/INDEX.md:', term)
        self.index(extra='| [Alpha](a.md) | Completed | Medium | Again | None | None |')
        self.invalid('duplicate')
        self.index()
        lines = (self.root / 'spec/INDEX.md').read_text().splitlines()
        (self.root / 'spec/INDEX.md').write_text('\n'.join(x for x in lines if '| [Beta]' not in x))
        self.invalid('missing index')

    def test_adr_set_comparison_description_and_location(self):
        adr = '[ADR-0001](../docs/adr/0001-example.md): existing boundary'
        self.spec('a', 'Alpha', adr=adr)
        self.invalid('adr')
        self.edit('spec/INDEX.md', '| Base | None | None |', f'| Base | None | {adr} |')
        self.valid()
        self.edit('spec/INDEX.md', ': existing boundary', '')
        self.invalid('description')
        self.spec('a', 'Alpha', adr='[Wrong](a.md)')
        self.invalid('adr')

    def test_graph_missing_spurious_duplicate_unknown_unsupported(self):
        cases = [
            ('a["Alpha"]\nb["Beta"]', 'missing dependency edge'),
            ('a["Alpha"]\nb["Beta"]\na -->|depends on| b\nb -->|depends on| a', 'unexpected dependency edge'),
            ('a["Alpha"]\nb["Beta"]\nb -->|depends on| a\nb -->|depends on| a', 'duplicate edge'),
            ('a["Alpha"]\na["Alpha"]\nb["Beta"]\nb -->|depends on| a', 'duplicate node'),
            ('a["Alpha"]\nb["Beta"]\nb -->|depends on| unknown', 'unknown node'),
            ('a["Alpha"]\nb["Beta"]\nb --> a', 'unsupported'),
            ('a["Alpha"]\nb["Unknown"]', 'unknown'),
            ('a["Alpha"]', 'missing graph node'),
        ]
        for graph, term in cases:
            with self.subTest(term=term):
                self.index(graph=graph)
                self.invalid(term)
        self.index()
        self.edit('spec/INDEX.md', '```mermaid', '```text')
        self.invalid('mermaid')
        self.index()
        with (self.root / 'spec/INDEX.md').open('a') as out:
            out.write('\n```mermaid\nflowchart TD\n```\n')
        self.invalid('one mermaid')

    def test_graph_comments_semicolons_and_duplicate_labels(self):
        self.index(graph='%% example\na["Alpha"];\nb["Beta"];\nb -->|depends on| a;')
        self.valid()
        self.spec('b', 'Alpha', '[Alpha](a.md)')
        self.invalid('ambiguous')

    def test_missing_root_index_bad_utf8_and_empty_repository(self):
        (self.root / 'spec/INDEX.md').unlink()
        self.invalid('INDEX.md')
        self.index()
        (self.root / 'spec/a.md').write_bytes(b'\xff')
        self.invalid('read')
        result = self.run_cli('--root', str(self.root / 'missing'))
        self.assertEqual(result.returncode, 1)
        (self.root / 'spec/a.md').unlink()
        (self.root / 'spec/b.md').unlink()
        self.invalid('no specifications')

    def test_heading_code_markup_and_duplicate_heading_suffixes(self):
        (self.root / 'docs/headings.md').write_text(
            '# A `code` heading\n## **Bold** & _emphasis_!\n'
            '## Repeat\n## Repeat-1\n## Repeat\n')
        with (self.root / 'spec/a.md').open('a') as out:
            out.write('\n[code](../docs/headings.md#a-code-heading)\n'
                      '[markup](../docs/headings.md#bold--_emphasis_)\n'
                      '[duplicate](../docs/headings.md#repeat-2)\n')
        self.valid()

    def test_reference_definitions_are_document_scoped(self):
        self.spec('a', 'Alpha', adr='[ADR-0001][decision]')
        with (self.root / 'spec/a.md').open('a') as out:
            out.write('\n[decision]: ../docs/adr/0001-example.md\n')
        self.edit('spec/INDEX.md', '| Base | None | None |',
                  '| Base | None | [ADR](../docs/adr/0001-example.md): boundary |')
        self.valid()

    def test_html_autolinks_and_local_angle_destinations_are_not_placeholders(self):
        with (self.root / 'spec/a.md').open('a') as out:
            out.write('\n<a id="custom"></a><details><summary>More</summary></details>\n'
                      '<https://invalid.example> <person@example.com>\n'
                      '[local](<b.md>)\nValue is < 10 and > 0.\n')
        self.valid()
        self.assertIn('2 external', self.run_cli().stdout)

    def test_duplicate_references_malformed_links_and_graph_spec_aliases(self):
        for addition, term in [('[x](a.md', 'malformed'),
                               ('[x]: a.md\n[x]: b.md', 'duplicate link reference')]:
            with self.subTest(term=term):
                self.spec('a', 'Alpha')
                with (self.root / 'spec/a.md').open('a') as out:
                    out.write('\n' + addition + '\n')
                self.invalid(term)
        self.spec('a', 'Alpha')
        self.index(graph='a["Alpha"]\na2["Alpha"]\nb["Beta"]\nb -->|depends on| a')
        self.invalid('duplicate graph nodes')

    def test_cli_default_root_uses_script_location_not_working_directory(self):
        scripts = self.root / 'scripts'
        scripts.mkdir()
        copied = scripts / SCRIPT.name
        copied.write_bytes(SCRIPT.read_bytes())
        result = subprocess.run([sys.executable, '-B', str(copied)],
                                cwd='/tmp', text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn('2 specifications validated', result.stdout)

    def test_deterministic_diagnostics_read_only_and_cli_errors(self):
        before = {str(p.relative_to(self.root)): p.read_bytes()
                  for p in self.root.rglob('*') if p.is_file()}
        self.valid()
        after = {str(p.relative_to(self.root)): p.read_bytes()
                 for p in self.root.rglob('*') if p.is_file()}
        self.assertEqual(before, after)
        self.edit('spec/a.md', 'Priority: Medium', 'Priority: Bad')
        first = self.invalid('priority')
        self.assertEqual(first.stdout, self.run_cli().stdout)
        self.assertEqual(self.run_cli('--unknown').returncode, 2)
        self.assertEqual(self.run_cli('--help').returncode, 0)


if __name__ == '__main__':
    unittest.main()
