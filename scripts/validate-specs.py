#!/usr/bin/env python3
"""Validate specification metadata and derived navigation offline, without writes."""

import argparse
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
import re
from urllib.parse import unquote, urlsplit


STATUSES = {'Draft', 'Approved', 'In progress', 'Completed', 'Superseded'}
PRIORITIES = {'Critical', 'High', 'Medium', 'Low'}
INLINE_LINK = re.compile(r'!?\[([^\]\n]*)\]\(\s*(<[^>\n]+>|[^\s)]+)(?:\s+["\'][^\n]*?["\'])?\s*\)')
REFERENCE = re.compile(r'!?\[([^\]\n]+)\]\[([^\]\n]*)\]')
DEFINITION = re.compile(r'^ {0,3}\[([^\]]+)\]:\s*(<[^>]+>|\S+)(?:\s+.*)?$')
AUTOLINK = re.compile(r'<([A-Za-z][\w+.-]*:[^<>\s]+|[^<>\s@]+@[^<>\s@]+)>')
ADR_NAME = re.compile(r'\d{4}-.+\.md$')
NODE = re.compile(r'([A-Za-z_][\w-]*)\["([^"\n]+)"\];?')
EDGE = re.compile(r'([A-Za-z_][\w-]*)\s+-->\|depends on\|\s+([A-Za-z_][\w-]*);?')


def prose_lines(text, mask_inline=True):
    """Mask examples while keeping original line numbers and character offsets."""
    lines = []
    fence = None
    for raw in text.splitlines():
        match = re.match(r'^ {0,3}(`{3,}|~{3,})(.*)$', raw)
        if fence:
            if match and match[1][0] == fence[0] and len(match[1]) >= len(fence) and not match[2].strip():
                fence = None
            lines.append('')
        elif match:
            fence = match[1]
            lines.append('')
        else:
            lines.append(re.sub(r'(`+)(.+?)\1', lambda m: ' ' * len(m[0]), raw) if mask_inline else raw)
    return lines


def slug(value):
    value = re.sub(r'<[^>]+>', '', value).lower()
    value = value.replace('`', '')
    return ''.join(c for c in value if c.isalnum() or c in '_- ').replace(' ', '-')


def reference_key(value):
    return ' '.join(value.casefold().split())


@dataclass(frozen=True)
class Link:
    label: str
    target: str
    line: int
    end: int


@dataclass
class Document:
    path: Path
    lines: list
    sections: dict
    title: str
    metadata: dict
    dependencies: set
    adrs: set


class Validator:
    def __init__(self, root):
        self.root = root.resolve()
        self.errors = set()
        self.external = 0
        self.documents = {}
        self.anchors = {}
        self.sources = {}

    def error(self, path, line, message):
        try:
            name = str(path.relative_to(self.root))
        except ValueError:
            name = str(path)
        self.errors.add((name, max(1, line), message))

    def read(self, path):
        try:
            path.resolve().relative_to(self.root)
        except ValueError:
            self.error(path, 1, 'path outside repository')
            return None
        try:
            return path.read_text(encoding='utf-8')
        except (OSError, UnicodeError) as exc:
            self.error(path, 1, f'cannot read file: {exc}')
            return None

    def local_target(self, source, line, target):
        target = target.strip('<>')
        try:
            parts = urlsplit(target)
        except ValueError:
            self.error(source, line, f'invalid link destination: {target}')
            return None
        if parts.scheme or parts.netloc:
            return None
        value = unquote(parts.path)
        path = (source.parent / value).resolve() if value else source.resolve()
        try:
            path.relative_to(self.root)
        except ValueError:
            self.error(source, line, f'link outside repository: {target}')
            return None
        return path

    def links(self, path, lines, start=1):
        """Extract supported inline and explicit reference links, including images."""
        definitions = {}
        for number, line in enumerate(self.sources.get(path, lines), 1 if path in self.sources else start):
            match = DEFINITION.match(line)
            if match:
                key = reference_key(match[1])
                if key in definitions:
                    self.error(path, number, f'duplicate link reference: {match[1]}')
                definitions[key] = match[2].strip('<>')
        links = []
        for number, line in enumerate(lines, start):
            if DEFINITION.match(line):
                continue
            spans = []
            for match in INLINE_LINK.finditer(line):
                links.append(Link(match[1], match[2].strip('<>'), number, match.end()))
                spans.append(match.span())
            for match in REFERENCE.finditer(line):
                if any(a <= match.start() < b for a, b in spans):
                    continue
                key = reference_key(match[2] or match[1])
                if key not in definitions:
                    self.error(path, number, f'undefined link reference: {key}')
                else:
                    links.append(Link(match[1], definitions[key], number, match.end()))
            for match in AUTOLINK.finditer(line):
                if any(a <= match.start() < b for a, b in spans):
                    continue
                target = match[1] if ':' in match[1] else 'mailto:' + match[1]
                links.append(Link(match[1], target, number, match.end()))
            # A bracket/parenthesis start left over is a malformed inline link.
            remainder = line
            for a, b in reversed(spans):
                remainder = remainder[:a] + ' ' * (b - a) + remainder[b:]
            if re.search(r'\[[^\]]*\]\(', remainder):
                self.error(path, number, 'malformed Markdown link')
        return links

    def heading_anchors(self, path):
        if path in self.anchors:
            return self.anchors[path]
        text = self.read(path)
        anchors = set()
        counts = Counter()
        if text is not None:
            for line in prose_lines(text, mask_inline=False):
                match = re.match(r'^ {0,3}#{1,6}\s+(.+?)(?:\s+#+)?\s*$', line)
                if match:
                    base = slug(match[1])
                    candidate = base
                    while candidate in anchors:
                        counts[base] += 1
                        candidate = f'{base}-{counts[base]}'
                    anchors.add(candidate)
                anchors.update(re.findall(r'<(?:a|[A-Za-z][\w-]*)\b[^>]*?\b(?:id|name)=["\']([^"\']+)["\']', line))
        self.anchors[path] = anchors
        return anchors

    def check_links(self, path, lines):
        for link in self.links(path, lines):
            try:
                parts = urlsplit(link.target)
            except ValueError:
                self.error(path, link.line, f'invalid link destination: {link.target}')
                continue
            if parts.scheme or parts.netloc:
                self.external += 1
                continue
            target = self.local_target(path, link.line, link.target)
            if target is None:
                continue
            if not target.exists():
                self.error(path, link.line, f'missing local link target: {unquote(link.target)}')
            elif parts.fragment and target.suffix.lower() == '.md':
                if unquote(parts.fragment) not in self.heading_anchors(target):
                    self.error(path, link.line, f'missing Markdown anchor: {link.target}')

    def check_placeholders(self, path, lines):
        for number, line in enumerate(lines, 1):
            # Exclude genuine HTML anchors/elements and URI/email autolinks.
            cleaned = re.sub(r'</?(?:a|span|div|p|br|details|summary|img|em|strong|code|pre|kbd|sup|sub)(?:\s+[^<>]*)?/?>', '', line, flags=re.I)
            cleaned = re.sub(r'<(?:[a-zA-Z][\w+.-]*://|mailto:)[^>]+>', '', cleaned)
            if (re.search(r'\b(?:TODO|TBD|FIXME)\b', line)
                    or re.search(r'<[A-Za-z][\w -]+>', cleaned)):
                self.error(path, number, 'unresolved template placeholder')

    def section(self, doc, name):
        return doc.sections.get(name, (1, []))

    def parse_document(self, path):
        text = self.read(path)
        if text is None:
            return None
        lines = prose_lines(text)
        self.sources[path] = lines
        titles = [(i, line[2:].strip()) for i, line in enumerate(lines, 1) if line.startswith('# ')]
        if len(titles) != 1:
            self.error(path, 1, 'required exactly one specification title')
        sections = {}
        current = None
        for number, line in enumerate(lines, 1):
            match = re.match(r'^## (.+?)\s*$', line)
            if match:
                current = match[1].casefold()
                if current in sections:
                    self.error(path, number, f'duplicate section: {current}')
                sections[current] = (number + 1, [])
            elif current:
                sections[current][1].append(line)
        doc = Document(path, lines, sections, titles[0][1] if titles else '', {}, set(), set())
        for name in ('metadata', 'verification', 'architecture decisions'):
            if name not in sections or not any(x.strip() for x in sections[name][1]):
                self.error(path, 1, f'required nonempty section: {name}')
        start, metadata_lines = self.section(doc, 'metadata')
        for number, line in enumerate(metadata_lines, start):
            match = re.match(r'^- (Status|Priority|Priority rationale|Depends on):\s*(.*)$', line)
            if match:
                key, value = match.groups()
                if key in doc.metadata:
                    self.error(path, number, f'duplicate metadata: {key}')
                doc.metadata[key] = (number, value.strip())
        for key in ('Status', 'Priority', 'Priority rationale', 'Depends on'):
            if key not in doc.metadata or not doc.metadata[key][1]:
                self.error(path, start, f'required metadata: {key}')
        for key, allowed in (('Status', STATUSES), ('Priority', PRIORITIES)):
            number, value = doc.metadata.get(key, (start, ''))
            if value not in allowed:
                self.error(path, number, f'invalid {key}: {value!r}; expected {", ".join(sorted(allowed))}')
        verification = ' '.join(self.section(doc, 'verification')[1]).replace('*', '')
        if not (re.search(r'\b(?:user (?:selected|chose|confirmed)|confirmed target)\b', verification, re.I)
                and re.search(r'\b(?:coverage|verification)\b', verification, re.I)):
            self.error(path, self.section(doc, 'verification')[0], 'missing confirmed coverage/verification target record')
        self.check_placeholders(path, lines)
        self.check_links(path, lines)
        return doc

    def dependency_set(self, path, line, value):
        if value == 'None':
            return set()
        links = self.links(path, [value], line)
        rest = REFERENCE.sub('', INLINE_LINK.sub('', value)).strip(' ,;')
        if not links or rest:
            self.error(path, line, 'Depends on must be None or specification links only')
        dependencies = []
        for link in links:
            target = self.local_target(path, line, link.target)
            if target not in self.documents:
                self.error(path, line, f'dependency must reference a discovered specification: {link.target}')
            else:
                dependencies.append(target)
        if len(dependencies) != len(set(dependencies)):
            self.error(path, line, 'duplicate specification dependency')
        return set(dependencies)

    def adr_set(self, path, lines, start=1, described=False):
        targets = []
        for link in self.links(path, lines, start):
            target = self.local_target(path, link.line, link.target)
            if target is None or target.parent != self.root / 'docs/adr' or not ADR_NAME.fullmatch(target.name):
                self.error(path, link.line, f'related ADR must reference docs/adr/NNNN-name.md: {link.target}')
                continue
            targets.append(target)
            if described:
                # Each ADR's explanation ends before the next link in its cell.
                tail = lines[link.line - start][link.end:]
                tail = tail.split('[', 1)[0].strip(' :;,')
                if not tail:
                    self.error(path, link.line, f'ADR requires a decision description: {link.label}')
        if len(targets) != len(set(targets)):
            self.error(path, start, 'duplicate related ADR')
        return set(targets)

    def check_cycles(self):
        visited = set()
        active = []

        def visit(path):
            if path in active:
                cycle = active[active.index(path):] + [path]
                self.error(path, 1, 'dependency cycle: ' + ' -> '.join(p.name for p in cycle))
                return
            if path in visited:
                return
            active.append(path)
            for dependency in sorted(self.documents[path].dependencies):
                visit(dependency)
            active.pop()
            visited.add(path)

        for path in sorted(self.documents):
            visit(path)

    def check_index(self, path, text):
        lines = prose_lines(text)
        self.sources[path] = lines
        self.check_links(path, lines)
        self.check_placeholders(path, lines)
        rows = {}
        labels = {}
        for number, line in enumerate(lines, 1):
            if not line.strip().startswith('|'):
                continue
            cells = [x.strip() for x in re.split(r'(?<!\\)\|', line.strip().strip('|'))]
            if cells[0] == 'Specification' or all(re.fullmatch(r':?-+:?', c) for c in cells):
                continue
            if len(cells) != 6:
                self.error(path, number, 'index row requires six columns')
                continue
            links = self.links(path, [cells[0]], number)
            if len(links) != 1:
                self.error(path, number, 'index row requires one specification link')
                continue
            target = self.local_target(path, number, links[0].target)
            if target not in self.documents:
                self.error(path, number, f'index references unknown specification: {links[0].target}')
                continue
            if target in rows:
                self.error(path, number, f'duplicate index entry: {target.name}')
            rows[target] = number
            doc = self.documents[target]
            for label in (links[0].label, doc.title):
                labels.setdefault(label, set()).add(target)
            for key, cell in (('Status', cells[1]), ('Priority', cells[2])):
                expected = doc.metadata.get(key, (1, ''))[1]
                if cell != expected:
                    self.error(path, number, f'{target.name} {key} mismatch: expected {expected!r}')
            if not cells[3]:
                self.error(path, number, f'{target.name} missing purpose')
            if self.dependency_set(path, number, cells[4]) != doc.dependencies:
                self.error(path, number, f'{target.name} index dependencies differ from metadata')
            adrs = set() if cells[5] == 'None' else self.adr_set(path, [cells[5]], number, described=True)
            if cells[5] != 'None' and not adrs:
                self.error(path, number, f'{target.name} related ADRs must be None or described links')
            if adrs != doc.adrs:
                self.error(path, number, f'{target.name} related ADRs differ from Architecture decisions')
        for missing in sorted(self.documents.keys() - rows.keys()):
            self.error(path, 1, f'missing index entry: {missing.name}')
        for label, targets in labels.items():
            if len(targets) > 1:
                self.error(path, 1, f'ambiguous graph label: {label}')
        self.check_graph(path, text, labels)

    def check_graph(self, path, text, labels):
        blocks = []
        fence = None
        body = None
        for number, line in enumerate(text.splitlines(), 1):
            match = re.match(r'^ {0,3}(`{3,}|~{3,})(.*)$', line)
            if not fence and match:
                fence = match[1]
                body = [] if match[2].strip() == 'mermaid' else None
                if body is not None:
                    blocks.append(body)
            elif fence and match and match[1][0] == fence[0] and len(match[1]) >= len(fence) and not match[2].strip():
                fence = None
                body = None
            elif body is not None:
                body.append((number, line.strip()))
        if len(blocks) != 1:
            self.error(path, 1, 'required exactly one Mermaid flowchart block')
            return
        if fence:
            self.error(path, 1, 'unclosed fenced block')
        statements = [(n, line) for n, line in blocks[0] if line and not line.startswith('%%')]
        if not statements or not re.fullmatch(r'flowchart (?:TD|TB|LR|RL|BT);?', statements[0][1]):
            self.error(path, 1, 'required Mermaid flowchart direction')
            return
        nodes = {}
        edges = []
        for number, line in statements[1:]:
            node, edge = NODE.fullmatch(line), EDGE.fullmatch(line)
            if node:
                key, label = node.groups()
                if key in nodes:
                    self.error(path, number, f'duplicate node: {key}')
                label = re.sub(r' \(independent\)$', '', label)
                candidates = labels.get(label, set())
                if len(candidates) != 1:
                    self.error(path, number, f'unknown or ambiguous graph node label: {label}')
                nodes[key] = next(iter(candidates)) if len(candidates) == 1 else None
            elif edge:
                edges.append((number, *edge.groups()))
            else:
                self.error(path, number, f'unsupported Mermaid statement: {line}')
        counts = Counter(value for value in nodes.values() if value is not None)
        for target in sorted(self.documents):
            if not counts[target]:
                self.error(path, 1, f'missing graph node: {target.name}')
            elif counts[target] > 1:
                self.error(path, 1, f'duplicate graph nodes for specification: {target.name}')
        actual = set()
        raw_edges = set()
        for number, source, target in edges:
            if (source, target) in raw_edges:
                self.error(path, number, f'duplicate edge: {source} -> {target}')
            raw_edges.add((source, target))
            if source not in nodes or target not in nodes:
                self.error(path, number, f'unknown node in dependency edge: {source} -> {target}')
            elif nodes[source] and nodes[target]:
                actual.add((nodes[source], nodes[target]))
        expected = {(p, d) for p, doc in self.documents.items() for d in doc.dependencies}
        for source, target in sorted(expected - actual):
            self.error(path, 1, f'missing dependency edge: {source.name} -> {target.name}')
        for source, target in sorted(actual - expected):
            self.error(path, 1, f'unexpected dependency edge: {source.name} -> {target.name}')

    def run(self):
        spec_dir = self.root / 'spec'
        if not spec_dir.is_dir():
            self.error(spec_dir, 1, 'missing specification directory')
            return
        for path in sorted(spec_dir.glob('*.md')):
            if path.name == 'INDEX.md' or path.name.endswith('TEMPLATE.md'):
                continue
            doc = self.parse_document(path)
            if doc is not None:
                self.documents[path.resolve()] = doc
        if not self.documents:
            self.error(spec_dir, 1, 'no specifications discovered')
        for path, doc in self.documents.items():
            line, value = doc.metadata.get('Depends on', (1, ''))
            doc.dependencies = self.dependency_set(path, line, value)
            if path in doc.dependencies:
                self.error(path, line, 'self dependency is not allowed')
            start, lines = self.section(doc, 'architecture decisions')
            doc.adrs = self.adr_set(path, lines, start)
        self.check_cycles()
        index = spec_dir / 'INDEX.md'
        text = self.read(index)
        if text is not None:
            self.check_index(index, text)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1],
                        help='repository root (defaults to this script’s repository)')
    args = parser.parse_args()
    validator = Validator(args.root)
    try:
        validator.run()
    except (OSError, UnicodeError, ValueError) as exc:
        validator.error(args.root.resolve(), 1, f'cannot read validation inputs: {exc}')
    for path, line, message in sorted(validator.errors):
        print(f'{path}:{line}: error: {message}')
    print(f'{validator.external} external link occurrences unchecked (offline validation).')
    if validator.errors:
        print(f'Validation failed: {len(validator.errors)} error(s).')
        return 1
    print(f'{len(validator.documents)} specifications validated.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
