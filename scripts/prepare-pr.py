#!/usr/bin/env python3
"""Prepare a scoped local handoff and PR draft without changing Git or GitHub."""

import argparse
import sys

from change_handoff import prepare
from pathlib import Path


def arguments():
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    parser.add_argument('--branch', required=True)
    parser.add_argument('--base', choices=('origin/main', 'main'), default='origin/main')
    parser.add_argument('--path', action='append', required=True, dest='paths')
    parser.add_argument('--title', required=True)
    parser.add_argument('--summary-file', type=Path, required=True)
    parser.add_argument('--review-file', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--spec', action='append', default=[])
    parser.add_argument('--adr', action='append', default=[])
    parser.add_argument('--routine-reason')
    parser.add_argument('--repository')
    parser.add_argument('--evidence', type=Path, action='append', default=[])
    parser.add_argument('--verification-note', action='append', default=[])
    return parser.parse_args()


def main():
    args = arguments()
    try:
        return prepare(Path(__file__).resolve().parents[1], args)
    except (OSError, ValueError, OverflowError, RecursionError) as exc:
        print(f'Handoff preparation error: {exc}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
