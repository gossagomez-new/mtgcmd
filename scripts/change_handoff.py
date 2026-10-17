"""Offline local handoff checks and PR draft generation; never mutates Git state."""

import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
from urllib.parse import quote, urlsplit

from verification_evidence import (Report, digest, git, source_snapshot,
                                   utc_now, validate_evidence)


SECTIONS = ('Change', 'Specification and decisions', 'Verification', 'Review and release considerations')
TITLE = re.compile(r'([a-z][a-z0-9-]*)(?:\([a-z0-9][a-z0-9._/-]*\))?(!)?: (.+)')
REPOSITORY = re.compile(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+')


def finding(data, code, message):
    data['findings'].append({'code': code, 'message': message})


def pending(data, key, message):
    if not any(item['id'] == key for item in data['pending']):
        data['pending'].append({'id': key, 'message': message})


def local_path(root, value):
    path = PurePosixPath(value)
    if (not value or path.is_absolute() or '..' in path.parts or '.git' in path.parts
            or not path.parts or '\0' in value):
        raise ValueError('require an exact repository-relative file path without traversal or .git internals')
    name = path.as_posix()
    absolute = root / name
    if not absolute.parent.resolve().is_relative_to(root):
        raise ValueError('path parent escapes the checkout through a symlink')
    return name


def changes(root, *args):
    tokens = git(root, 'diff', '--no-ext-diff', '--no-textconv', '--name-status',
                 '-z', '--find-renames', *args, '--').split(b'\0')
    result = []
    cursor = 0
    while cursor < len(tokens) and tokens[cursor]:
        status = tokens[cursor].decode('ascii')
        width = 2 if status.startswith(('R', 'C')) else 1
        paths = [os.fsdecode(value) for value in tokens[cursor + 1:cursor + width + 1]]
        if len(paths) != width or any(not p for p in paths):
            raise ValueError('invalid NUL-delimited Git diff result')
        result.append({'status': status, 'paths': paths})
        cursor += width + 1
    return result


def paths_in(entries):
    return {path for item in entries for path in item['paths']}


def git_probe(root, *args):
    result = subprocess.run(['git', '--no-optional-locks', '-c', 'core.fsmonitor=false',
                             '-C', str(root), *args], cwd=root, capture_output=True)
    if result.returncode not in (0, 1):
        return None
    return result


def inspect_branch(root, args, data):
    branch = data['source_before']['branch']
    if branch is None:
        finding(data, 'detached-head', 'Switch to an intended topic branch before handoff.')
    elif branch == 'main':
        finding(data, 'main-branch', 'Prepare changes on a focused topic branch.')
    if branch != args.branch:
        finding(data, 'branch-mismatch', 'The requested branch does not match the current branch.')
    try:
        base = git(root, 'rev-parse', '--verify', args.base + '^{commit}').decode().strip()
    except ValueError:
        finding(data, 'base-missing', 'The selected main ref is unavailable; review/fetch it separately.')
        return []
    data['base_commit'] = base
    merge = git_probe(root, 'merge-base', 'HEAD', base)
    if merge is None or merge.returncode:
        finding(data, 'base-not-integrated', 'The topic and selected main ref have no common base.')
        return []
    data['merge_base'] = merge.stdout.decode().strip()
    if data['merge_base'] != base:
        finding(data, 'base-not-integrated', 'Integrate the selected main ref before preparing this handoff.')
    return changes(root, data['merge_base'], 'HEAD')


def inspect_scope(root, args, data, committed):
    staged, unstaged = changes(root, '--cached'), changes(root)
    untracked = {os.fsdecode(x) for x in git(root, 'ls-files', '-z', '--others',
                                            '--exclude-standard').split(b'\0') if x}
    sets = {'committed_paths': paths_in(committed), 'staged_paths': paths_in(staged),
            'unstaged_paths': paths_in(unstaged), 'untracked_paths': untracked}
    all_changes = set().union(*sets.values())
    scope = set()
    for raw in args.paths:
        try:
            name = local_path(root, raw)
            path = root / name
            if path.is_dir() and not path.is_symlink():
                raise ValueError('scope accepts files only; enumerate exact changed paths')
            if name in scope:
                finding(data, 'scope-duplicate', f'Duplicate scoped file: {name!r}')
            scope.add(name)
            if name not in all_changes:
                finding(data, 'scope-unchanged', f'No changed file matches literal scope entry: {name!r}')
        except ValueError as exc:
            finding(data, 'scope-path', f'Invalid scope {raw!r}: {exc}')
    if not all_changes:
        finding(data, 'scope-empty', 'There are no committed or working changes to prepare.')
    for key, code in (('committed_paths', 'committed-outside-scope'), ('staged_paths', 'staged-outside-scope')):
        excluded = sorted(sets[key] - scope)
        if excluded:
            finding(data, code, f'Include or separately resolve these changes before handoff: {excluded!r}')
    for item in committed + staged:
        endpoints = set(item['paths'])
        if item['status'].startswith('R') and endpoints & scope and not endpoints <= scope:
            finding(data, 'rename-scope', f'Include both rename endpoints: {item["paths"]!r}')
    data.update({key: sorted(value) for key, value in sets.items()})
    data['scope'] = sorted(scope)
    data['stage_paths'] = sorted(scope & (sets['unstaged_paths'] | untracked))
    data['excluded_paths'] = sorted((sets['unstaged_paths'] | untracked) - scope)
    if data['excluded_paths']:
        pending(data, 'verification-applicability', 'Review whether excluded local changes influenced verification.')


def inspect_title(args, data):
    match = TITLE.fullmatch(args.title)
    if (not match or args.title != args.title.strip() or len(args.title.splitlines()) != 1
            or any(ord(c) < 32 or ord(c) == 127 for c in args.title)
            or not match[3].islower() or match[3] != match[3].strip()):
        finding(data, 'title', 'Use a single-line Conventional Commit title with a lowercase description.')
    data['breaking_change'] = bool(match and match[2])


def repository_name(value):
    return (bool(REPOSITORY.fullmatch(value))
            and all(part not in ('.', '..') for part in value.split('/')))


def infer_repository(root):
    remote = git(root, 'config', '--get', 'remote.origin.url', detached_ok=True).decode().strip()
    if remote.startswith('git@github.com:'):
        name = remote.removeprefix('git@github.com:').removesuffix('.git')
    else:
        try:
            url = urlsplit(remote)
            if (url.scheme != 'https' or url.hostname != 'github.com' or url.username is not None
                    or url.password is not None or url.port is not None or url.query or url.fragment):
                return None
            name = url.path.removeprefix('/').removesuffix('.git')
        except ValueError:
            return None
    return name if repository_name(name) else None


def inspect_references(root, args, data):
    inferred = infer_repository(root)
    explicit = args.repository
    if explicit is not None and not repository_name(explicit):
        finding(data, 'repository-mismatch', 'Repository must have owner/name syntax.')
        explicit = None
    if explicit and inferred and explicit.casefold() != inferred.casefold():
        finding(data, 'repository-mismatch', 'Explicit repository differs from the recognized origin repository.')
    data['repository'] = explicit or inferred
    if not data['repository']:
        pending(data, 'repository', 'Confirm GitHub repository identity and documentation links live.')
    tracked = {os.fsdecode(x) for x in git(root, 'ls-files', '--cached', '-z').split(b'\0') if x}
    for kind, values in (('specifications', args.spec), ('adrs', args.adr)):
        accepted = []
        for raw in values:
            try:
                name = local_path(root, raw)
                path = root / name
                if kind == 'specifications':
                    valid = (path.parent == root / 'spec' and path.suffix == '.md'
                             and path.name != 'INDEX.md' and not path.name.endswith('TEMPLATE.md'))
                else:
                    valid = path.parent == root / 'docs/adr' and re.fullmatch(r'\d{4}-.+\.md', path.name)
                if not valid or not path.is_file() or not path.resolve().is_relative_to(root):
                    raise ValueError('reference must identify an existing specification or numbered ADR in its repository directory')
                if name not in tracked and name not in data['scope']:
                    finding(data, 'reference-unpublished', f'New reference is excluded from the intended commit: {name!r}')
                if name in accepted:
                    finding(data, 'references', f'Duplicate reference: {name!r}')
                accepted.append(name)
            except ValueError as exc:
                finding(data, 'references', f'Invalid reference {raw!r}: {exc}')
        data[kind] = sorted(set(accepted))
    if not args.spec and not (args.routine_reason and args.routine_reason.strip()):
        finding(data, 'references', 'Supply a specification or explain why this is a routine change.')


def inspect_evidence(root, args, data):
    records = []
    for path in args.evidence:
        record = {'manifest': str(path.absolute())}
        try:
            if not path.is_file():
                raise ValueError('evidence input must be a regular file')
            evidence = validate_evidence(root, path)
            record.update(state='current', profile=evidence['profile'], browser=evidence['browser'],
                          checks=[check['id'] for check in evidence['checks'] if check['status'] == 'passed'],
                          source_commit=evidence['source_before']['commit'],
                          source_state_sha256=evidence['source_before']['state_sha256'])
        except (OSError, ValueError, OverflowError, RecursionError) as exc:
            record.update(state='rejected', reason=str(exc))
        records.append(record)
    data['evidence'] = records
    if not records or any(record['state'] != 'current' for record in records):
        pending(data, 'verification', 'Resolve rejected/missing evidence or review actual relevant checks; no rejected successes were copied.')
    if args.verification_note:
        pending(data, 'manual-verification-review', 'Review author-provided notes against actual executed checks.')


def read_prose(path, data, name):
    try:
        if not path.is_file():
            raise ValueError('input must be a regular UTF-8 file')
        content = path.read_bytes()
        text = content.decode('utf-8')
        if not text.strip():
            raise ValueError('input is empty')
        data['input_hashes'][name] = digest(content)
        return text
    except (OSError, ValueError) as exc:
        finding(data, 'inputs', f'Cannot read {name} input: {exc}')
        return f'{name.capitalize()} input unavailable; resolve the local preparation findings.\n'


def inspect_template(root, data):
    path = root / '.github/pull_request_template.md'
    try:
        headings = re.findall(r'^## (.+?)\s*$', path.read_text(encoding='utf-8'), re.M)
        if tuple(headings) != SECTIONS:
            raise ValueError('expected the four repository PR sections in their established order')
    except (OSError, ValueError) as exc:
        finding(data, 'template', f'Cannot use the PR template: {exc}')


def inline_code(value):
    text = json.dumps(value, ensure_ascii=False)
    ticks = '`' * (max((len(x) for x in re.findall(r'`+', text)), default=0) + 1)
    return f'{ticks}{text}{ticks}'


def reference_link(data, path):
    if not data['repository']:
        return inline_code(path)
    url = f'https://github.com/{data["repository"]}/blob/{quote(data["branch"], safe="/")}/{quote(path, safe="/")}'
    label = path.replace('\\', '\\\\').replace('[', '\\[').replace(']', '\\]')
    return f'[{label}]({url})'


def render(data, summary, review):
    references = [reference_link(data, path) for path in data['specifications'] + data['adrs']]
    decisions = '\n'.join('- ' + value for value in references)
    if data['routine_reason']:
        decisions += '\n\nRoutine-change explanation: ' + data['routine_reason']
    verification = []
    for record in data['evidence']:
        if record['state'] == 'current':
            scope = record['profile'] + (' with browser checks' if record['browser'] else '')
            verification.append(f'- Recorded local profile **{scope}** passed: ' + ', '.join(record['checks']) + '.')
            verification.append(f'- Evidence matched the working tree **before staging** (source commit `{record["source_commit"]}`, state `{record["source_state_sha256"]}`).')
        else:
            verification.append('- A supplied evidence record was rejected as current; no success claims were copied.')
    if not any(record['state'] == 'current' for record in data['evidence']):
        verification.append('- No current runner evidence has been accepted; review actual verification before publication.')
    for note in data['verification_notes']:
        verification.append('- Author-provided verification note (requires review): ' + note)
    verification.append('\nStaging/committing invalidate the exact evidence fingerprint. This is not verification of the resulting commit. Hosted CI status must be checked separately.')
    considerations = review + '\n\nPrepared scope (exact literal file paths):\n\n'
    considerations += '\n'.join('- ' + inline_code(path) for path in data['scope'])
    if data['excluded_paths']:
        considerations += '\n\nUnrelated local changes were excluded; review their effect on verification applicability.'
    considerations += '\n\nPending checks:\n\n' + '\n'.join('- ' + item['message'] for item in data['pending'])
    if data['findings']:
        considerations += '\n\nLocal preparation has unresolved findings; resolve the JSON report before handoff.'
    considerations += '\n\nMerging this PR does not deploy to the production VM.\n'
    bodies = (summary, decisions, '\n'.join(verification), considerations)
    return '\n\n'.join('## ' + heading + '\n\n' + body for heading, body in zip(SECTIONS, bodies)) + '\n'


def prepare(root, args):
    output = Report(root, args.output)
    output.path = output.directory / 'handoff.json'
    data = {'schema_version': 1, 'kind': 'change-handoff', 'created_at': utc_now(),
            'root': str(root), 'branch': args.branch, 'base': args.base,
            'base_commit': None, 'merge_base': None, 'title': args.title,
            'routine_reason': args.routine_reason, 'verification_notes': args.verification_note,
            'source_before': source_snapshot(root), 'findings': [], 'pending': [], 'input_hashes': {}}
    for key, message in (
        ('github-history', 'Check live PR history, including squash merges, before reusing the branch.'),
        ('base-freshness', 'Fetch/review current main separately; this script only inspected a local ref.'),
        ('ci', 'Review the actual GitHub verify status separately.'),
        ('diff-review', 'Review the exact diff, title meaning, and verification applicability.'),
        ('authorization', 'Use session authorization for each intended Git/GitHub action.')):
        pending(data, key, message)
    committed = inspect_branch(root, args, data)
    inspect_scope(root, args, data, committed)
    inspect_title(args, data)
    inspect_references(root, args, data)
    inspect_evidence(root, args, data)
    summary = read_prose(args.summary_file, data, 'summary')
    review = read_prose(args.review_file, data, 'review')
    inspect_template(root, data)
    data['source_after'] = source_snapshot(root)
    if data['source_before'] != data['source_after']:
        finding(data, 'source-changed', 'Source changed during preparation; rerun against a stable reviewed state.')
    data['findings'].sort(key=lambda item: (item['code'], item['message']))
    data['local_checks_passed'] = not data['findings']
    draft = render(data, summary, review).encode('utf-8')
    data['draft_sha256'] = digest(draft)
    with output.open_log('pr.md') as stream:
        stream.write(draft)
    output.save(data)
    print(f'Handoff report: {output.path}')
    print(f'PR draft: {output.directory / "pr.md"}')
    for item in data['findings']:
        print(f'{item["code"]}: {item["message"]}')
    print('Local checks passed; remote/human checks remain pending.' if data['local_checks_passed']
          else 'Local preparation needs attention; no Git or GitHub actions were performed.')
    return 0 if data['local_checks_passed'] else 1
