"""Source identity, private report storage, and read-only evidence validation."""

from datetime import datetime, timezone
import hashlib
import json
import math
import os
from pathlib import Path
import platform
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import uuid

from verification_profiles import PROFILES, plan
from verification_environment import environment, task_name


SCHEMA_VERSION = 1
TOOL_NAMES = ('bash', 'git', 'java', 'mvn', 'node', 'npm', 'python3')
OPTION_NAMES = ('MAVEN_OPTS', 'NODE_OPTIONS', 'JAVA_TOOL_OPTIONS', 'JAVA_OPTS')


def utc_now():
    return datetime.now(timezone.utc).isoformat()


def digest(data):
    return hashlib.sha256(data).hexdigest()


def file_digest(path):
    result = hashlib.sha256()
    with path.open('rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            result.update(chunk)
    return result.hexdigest()


def git(root, *args, detached_ok=False):
    result = subprocess.run(['git', '--no-optional-locks', '-c', 'core.fsmonitor=false',
                             '-C', str(root), *args], capture_output=True, cwd=root)
    if result.returncode and not (detached_ok and result.returncode == 1):
        # Do not repeat arbitrary Git hooks/configuration/environment in metadata.
        raise ValueError(f'cannot inspect Git source state ({args[0]}, exit {result.returncode})')
    return result.stdout


def source_once(root):
    top = git(root, 'rev-parse', '--show-toplevel').decode().strip()
    if Path(top).resolve() != root:
        raise ValueError('runner must reside at the Git checkout root')
    status = git(root, 'status', '--porcelain=v1', '-z', '--untracked-files=all')
    files = sorted(set(git(root, 'ls-files', '-z', '--cached', '--others',
                           '--exclude-standard').split(b'\0')) - {b''})
    contents = hashlib.sha256()
    for name in files:
        path = root / os.fsdecode(name)
        contents.update(len(name).to_bytes(8, 'big') + name)
        if path.exists() or path.is_symlink():
            contents.update(stat.S_IMODE(path.lstat().st_mode).to_bytes(4, 'big'))
        if path.is_symlink():
            contents.update(b'symlink\0' + os.fsencode(os.readlink(path)))
        elif not path.exists():
            contents.update(b'deleted\0')
        elif path.is_file():
            contents.update(b'file\0' + file_digest(path).encode())
        else:
            raise ValueError(f'unsupported source entry (for example, a submodule): {os.fsdecode(name)}')
    source = {
        'commit': git(root, 'rev-parse', 'HEAD').decode().strip(),
        'branch': git(root, 'symbolic-ref', '--quiet', '--short', 'HEAD', detached_ok=True).decode().strip() or None,
        'dirty': bool(status), 'status_sha256': digest(status),
        'diff_sha256': digest(git(root, 'diff', '--no-ext-diff', '--no-textconv', '--binary', 'HEAD', '--')),
        'staged_diff_sha256': digest(git(root, 'diff', '--cached', '--no-ext-diff', '--no-textconv', '--binary', '--')),
        'files_sha256': contents.hexdigest(), 'file_count': len(files),
    }
    source['state_sha256'] = digest(json.dumps(source, sort_keys=True).encode())
    return source


def source_snapshot(root):
    first = source_once(root)
    if source_once(root) != first:
        raise ValueError('source changed while its identity was being captured; retry when stable')
    return first


def settings(values=None):
    values = os.environ if values is None else values
    result = {key: values.get(key) for key in ('CHROME_PATH', 'JAVA_HOME')}
    for key in OPTION_NAMES:
        value = values.get(key)
        result[key] = {'present': value is not None,
                       'sha256': digest(value.encode()) if value is not None else None}
    for key in ('CI', 'API_PORT'):
        value = values.get(key)
        if value is None or value in ('true', 'false', '0', '1') or (key == 'API_PORT' and value.isdecimal()):
            result[key] = value
        else:
            result[key] = {'present': True, 'sha256': digest(value.encode())}
    return result


def execution(root):
    search_path = os.pathsep.join(str(root / item) if not Path(item).is_absolute() else item
                                  for item in os.environ.get('PATH', os.defpath).split(os.pathsep))
    return {'python_version': platform.python_version(), 'interpreter': sys.executable,
            'platform': platform.platform(),
            'tools': {tool: shutil.which(tool, path=search_path) for tool in TOOL_NAMES}}


def no_symlinks(path):
    for candidate in (path, *path.parents):
        if candidate.is_symlink():
            raise ValueError(f'evidence paths must not use symlinks: {candidate}')


class Report:
    def __init__(self, root, requested, task=None):
        if requested is not None:
            directory = Path(os.path.abspath(requested))
            no_symlinks(directory)
            if directory.is_relative_to(root):
                raise ValueError('evidence output must be outside the checkout')
            if not directory.parent.is_dir():
                raise ValueError('evidence output requires an existing directory parent')
            directory.mkdir(mode=0o700)
        else:
            parent = Path(tempfile.gettempdir()).absolute()
            no_symlinks(parent)
            if parent.resolve().is_relative_to(root):
                raise ValueError('temporary evidence directory must be outside the checkout')
            prefix = f'mtgcmd-verify-{task_name(task)}-' if task is not None else 'mtgcmd-verify-'
            directory = Path(tempfile.mkdtemp(prefix=prefix, dir=parent))
        self.directory = directory
        self.path = directory / 'manifest.json'

    def save(self, data):
        temporary = None
        try:
            with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', dir=self.directory,
                                             prefix='.manifest-', delete=False) as output:
                temporary = Path(output.name)
                json.dump(data, output, indent=2, sort_keys=True)
                output.write('\n')
                output.flush()
                os.fsync(output.fileno())
            os.replace(temporary, self.path)
        finally:
            if temporary is not None:
                temporary.unlink(missing_ok=True)

    def open_log(self, name):
        descriptor = os.open(self.directory / name,
                             os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        return os.fdopen(descriptor, 'wb')


def timestamp(record, key):
    value = record.get(key)
    if not isinstance(value, str):
        raise ValueError(f'missing or invalid evidence timestamp: {key}')
    parsed = datetime.fromisoformat(value)
    if parsed.tzinfo is None:
        raise ValueError(f'evidence timestamp requires a timezone: {key}')
    return parsed


def successful(record):
    return record.get('status') == 'passed' and type(record.get('exit_code')) is int and record['exit_code'] == 0


def validate_evidence(root, manifest):
    path = Path(os.path.abspath(manifest))
    no_symlinks(path)
    data = json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(data, dict) or type(data.get('schema_version')) is not int or data['schema_version'] != SCHEMA_VERSION:
        raise ValueError('unsupported or missing evidence schema version')
    if not successful(data) or data.get('source_unchanged') is not True:
        raise ValueError('evidence is incomplete or did not pass')
    if not isinstance(data.get('run_id'), str):
        raise ValueError('missing evidence run identity')
    uuid.UUID(data['run_id'])
    run_start, run_end = timestamp(data, 'started_at'), timestamp(data, 'finished_at')
    if run_end < run_start:
        raise ValueError('invalid evidence timestamp order')
    if data.get('root') != str(root):
        raise ValueError('evidence describes another checkout')
    if data.get('profile') not in PROFILES or type(data.get('browser')) is not bool:
        raise ValueError('invalid evidence profile/browser selection')
    if 'task' in data:
        task_name(data['task'])
    if data['profile'] == 'browser' and data['browser']:
        raise ValueError('redundant browser evidence selection')
    expected = plan(data['profile'], data['browser'])
    checks = data.get('checks')
    if not isinstance(checks, list) or len(checks) != len(expected):
        raise ValueError('evidence does not match the current command plan')
    for number, (record, check) in enumerate(zip(checks, expected), 1):
        if (not isinstance(record, dict) or type(record.get('selected')) is not bool
                or any(record.get(key) != value for key, value in check.items())):
            raise ValueError('evidence does not match the current command plan')
        if record.get('cwd') != str(root):
            raise ValueError('invalid command working directory')
        if check['selected']:
            if not successful(record):
                raise ValueError('selected check is incomplete or did not pass')
            start, end = timestamp(record, 'started_at'), timestamp(record, 'finished_at')
            if not run_start <= start <= end <= run_end:
                raise ValueError('invalid check timestamp order')
            duration = record.get('duration_seconds')
            if type(duration) not in (int, float) or not math.isfinite(duration) or duration < 0:
                raise ValueError('invalid check duration')
            name = f'{number:02d}-{check["id"]}.log'
            if record.get('log') != name or not re.fullmatch(r'[0-9a-f]{64}', str(record.get('sha256'))):
                raise ValueError('invalid check log identity')
            log = path.parent / name
            no_symlinks(log)
            if file_digest(log) != record['sha256']:
                raise ValueError(f'check log is missing or changed: {name}')
        elif record.get('status') != 'skipped' or not record.get('reason'):
            raise ValueError('unselected check must be explicitly skipped')
    current = source_snapshot(root)
    if data.get('source_before') != current or data.get('source_after') != current:
        raise ValueError('evidence is stale: source state does not match this checkout')
    if data.get('settings') != settings(environment(root, data.get('task'))) or data.get('execution') != execution(root):
        raise ValueError('evidence settings or tool paths do not match this execution environment')
    return data
