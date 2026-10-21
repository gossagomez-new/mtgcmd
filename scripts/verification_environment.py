"""Validated task labels and path-only local defaults for verification processes."""

import json
import os
from pathlib import Path
import re


LOCAL_SETTINGS = '.verification.local.json'
TASK_PATTERN = re.compile(r'[a-z0-9]+(?:-[a-z0-9]+)*')
DEFAULTS = {
    'mavenRepository': ('MAVEN_OPTS', '-Dmaven.repo.local='),
    'javaTemporaryDirectory': ('JAVA_TOOL_OPTIONS', '-Djava.io.tmpdir='),
    'chromePath': ('CHROME_PATH', ''),
}


def task_name(value):
    if not isinstance(value, str) or not 1 <= len(value) <= 64 or not TASK_PATTERN.fullmatch(value):
        raise ValueError('task must be 1–64 lowercase letters/digits with single separating hyphens')
    return value


def unique_settings(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f'duplicate local setting: {key}')
        result[key] = value
    return result


def local_settings(root):
    path = root / LOCAL_SETTINGS
    if path.is_symlink():
        raise ValueError('local verification settings must not be a symlink')
    if not path.exists():
        return {}
    data = json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=unique_settings)
    if not isinstance(data, dict) or any(key not in DEFAULTS for key in data):
        raise ValueError('local verification settings must be an object containing only supported path keys')
    for key, value in data.items():
        if not isinstance(value, str) or not value or any(ord(char) < 32 for char in value):
            raise ValueError(f'{key} must be an existing absolute path')
        target = Path(value)
        if not target.is_absolute():
            raise ValueError(f'{key} must be an existing absolute path')
        if key == 'chromePath':
            if not target.is_file() or not os.access(target, os.X_OK):
                raise ValueError('chromePath must be an existing executable file')
        else:
            if any(char.isspace() or char in '\'"\\*?[]' for char in value):
                raise ValueError(f'{key} cannot contain whitespace, quotes, backslashes or glob characters')
            if not target.is_dir():
                raise ValueError(f'{key} must be an existing directory')
    return data


def environment(root, task=None):
    """Return child settings without altering the parent process environment."""
    result = dict(os.environ)
    if task is not None:
        task_name(task)
        for key, value in local_settings(root).items():
            variable, prefix = DEFAULTS[key]
            result.setdefault(variable, prefix + value)
    return result
