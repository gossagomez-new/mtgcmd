#!/usr/bin/env python3
"""Summarize native PMD XML without modifying reports or invoking the build."""
import argparse
from collections import Counter
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

REPOSITORY = Path(__file__).resolve().parent.parent
SCHEMA_VERSION = 1


class ReportTreeBuilder(ET.TreeBuilder):
    """Reject DTDs through XML parsing, independent of document encoding."""

    def doctype(self, name, pubid, system):
        raise ValueError('XML DTD declarations are not supported')


def tag(element):
    return element.tag.rsplit('}', 1)[-1]


def normalized_path(name, root):
    """Keep repository-relative paths stable across checkout locations."""
    name = name.replace('\\', '/')
    marker = 'backend/src/'
    index = name.find(marker)
    if index >= 0:
        return name[index:]
    path = Path(name)
    if path.is_absolute():
        try:
            return path.resolve().relative_to(root.resolve()).as_posix()
        except ValueError:
            pass
    return name


def scope(path):
    segments = '/' + path
    if '/src/main/' in segments:
        return 'production'
    if '/src/test/' in segments:
        return 'tests'
    return 'other'


def parse_report(path, root):
    content = path.read_bytes()
    report = ET.fromstring(content, parser=ET.XMLParser(target=ReportTreeBuilder()))
    if tag(report) != 'pmd':
        raise ValueError('Expected a native PMD XML report')
    rules, files, priorities = Counter(), Counter(), Counter()
    scopes = Counter({'production': 0, 'tests': 0, 'other': 0})
    diagnostics, suppressions = [], []
    for child in report:
        kind = tag(child)
        if kind == 'file':
            name = child.get('name')
            if not name:
                raise ValueError('PMD file record has no name')
            name = normalized_path(name, root)
            for violation in child:
                if tag(violation) != 'violation':
                    raise ValueError('Unexpected record inside PMD file')
                rule = violation.get('rule')
                priority = violation.get('priority', '')
                if not rule or priority not in {'1', '2', '3', '4', '5'}:
                    raise ValueError('PMD violation requires a rule and priority from 1 to 5')
                rules[rule] += 1
                files[name] += 1
                priorities[priority] += 1
                scopes[scope(name)] += 1
        elif kind in {'error', 'configerror', 'suppressedviolation'}:
            record = {'kind': kind, **child.attrib}
            if 'filename' in record:
                record['filename'] = normalized_path(record['filename'], root)
            record['detail'] = ' '.join(''.join(child.itertext()).split())
            (suppressions if kind == 'suppressedviolation' else diagnostics).append(record)
        else:
            raise ValueError(f'Unsupported PMD record: {kind}')
    return {
        'schema_version': SCHEMA_VERSION,
        'report': {'path': str(path.resolve()), 'pmd_version': report.get('version'),
                   'timestamp': report.get('timestamp')},
        'complete': not diagnostics,
        'total': sum(rules.values()),
        'scopes': dict(sorted(scopes.items())),
        'rules': dict(sorted(rules.items())),
        'files': dict(sorted(files.items())),
        'priorities': dict(sorted(priorities.items())),
        'diagnostics': diagnostics,
        'suppressions': suppressions,
    }


def load_baseline(path):
    data = json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(data, dict) or data.get('schema_version') != SCHEMA_VERSION:
        raise ValueError('Unsupported baseline schema')
    total = data.get('total')
    if type(total) is not int or total < 0:
        raise ValueError('Invalid baseline total')
    for key in ['scopes', 'rules', 'files', 'priorities']:
        counts = data.get(key)
        if (not isinstance(counts, dict)
                or any(not isinstance(k, str) or type(v) is not int or v < 0
                       for k, v in counts.items())
                or sum(counts.values()) != total):
            raise ValueError(f'Invalid baseline {key} counts')
    if set(data['scopes']) != {'production', 'tests', 'other'}:
        raise ValueError('Invalid baseline scopes')
    if not isinstance(data.get('report'), dict) or type(data.get('complete')) is not bool:
        raise ValueError('Invalid baseline provenance')
    return data


def comparison(current, baseline):
    result = {'total': current['total'] - baseline['total'],
              'pmd_version_changed': current['report']['pmd_version'] !=
                                     baseline['report'].get('pmd_version'),
              'baseline_complete': baseline['complete']}
    for key in ['scopes', 'rules', 'files']:
        result[key] = {name: current[key].get(name, 0) - baseline[key].get(name, 0)
                       for name in sorted(current[key].keys() | baseline[key].keys())}
    return result


def cell(value):
    return str(value).replace('|', '\\|').replace('\n', ' ')


def table(title, counts, limit=None, signed=False):
    ordered = sorted(counts.items(), key=lambda item: (-abs(item[1]), item[0]))
    if limit is not None:
        ordered = ordered[:limit]
    lines = [f'## {title}', '', '| Item | Count |', '| --- | ---: |']
    lines.extend(f'| {cell(name)} | {value:+d} |' if signed else
                 f'| {cell(name)} | {value} |' for name, value in ordered)
    if not ordered:
        lines.append('| None | 0 |')
    return '\n'.join(lines)


def markdown(data):
    provenance = data['report']
    parts = ['# PMD report summary',
             f"Report: {cell(provenance['path'])}",
             f"PMD version: {cell(provenance['pmd_version'])}; report timestamp: "
             f"{cell(provenance['timestamp'])}. Freshness and source revision are unverified.",
             f"Active findings: **{data['total']}**. Analysis "
             + ('complete.' if data['complete'] else '**incomplete: diagnostics reported**.'),
             table('Scopes', data['scopes']), table('Rules', data['rules']),
             table('Priorities', data['priorities']), table('Top 15 files', data['files'], 15),
             f"Suppressed findings: {len(data['suppressions'])} (excluded from active counts)."]
    if data['diagnostics']:
        parts += ['## Analysis diagnostics', '\n'.join(
            '- ' + cell(json.dumps(item, sort_keys=True)) for item in data['diagnostics'])]
    delta = data.get('comparison')
    if delta:
        parts += ['## Baseline comparison',
                  f"Total count delta: {delta['total']:+d}. PMD version changed: "
                  f"{delta['pmd_version_changed']}. Baseline complete: {delta['baseline_complete']}.",
                  'These are aggregate count changes, not identities of introduced or resolved '
                  'findings. Ruleset and analyzed-source changes can affect comparability.',
                  table('Scope count deltas', delta['scopes'], signed=True),
                  table('Rule count deltas', delta['rules'], signed=True),
                  table('Top 15 file count deltas', delta['files'], 15, signed=True)]
    parts.append('Counts and PMD priorities are review signals, not confirmed defects. '
                 'Inspect affected code before recommending changes.')
    return '\n\n'.join(parts) + '\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('report', nargs='?', type=Path,
                        default=REPOSITORY / 'backend/target/pmd.xml')
    parser.add_argument('--root', type=Path, default=REPOSITORY)
    parser.add_argument('--baseline', type=Path, help='Previously generated JSON summary')
    parser.add_argument('--markdown', type=Path, help='New Markdown output file')
    parser.add_argument('--json', type=Path, help='New JSON summary/baseline file')
    args = parser.parse_args()
    try:
        outputs = [path for path in [args.markdown, args.json] if path is not None]
        inputs = {args.report.resolve()}
        if args.baseline:
            inputs.add(args.baseline.resolve())
        if len({path.resolve() for path in outputs}) != len(outputs):
            raise ValueError('Output paths must be distinct')
        for path in outputs:
            if path.resolve() in inputs or path.exists() or path.is_symlink():
                raise ValueError(f'Refusing to overwrite existing file or input: {path}')
            if not path.parent.is_dir():
                raise ValueError(f'Output directory does not exist: {path.parent}')
        data = parse_report(args.report, args.root)
        if args.baseline:
            data['comparison'] = comparison(data, load_baseline(args.baseline))
        rendered = markdown(data)
        for path, content in [(args.markdown, rendered),
                              (args.json, json.dumps(data, indent=2, sort_keys=True) + '\n')]:
            if path is not None:
                with path.open('x', encoding='utf-8') as output:
                    output.write(content)
        print(rendered, end='')
        return 0 if data['complete'] else 2
    except (OSError, ValueError, ET.ParseError) as error:
        print(f'PMD analysis failed: {error}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
