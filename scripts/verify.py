#!/usr/bin/env python3
"""Run explicit local verification profiles and retain source-bound evidence."""

import argparse
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import time
import uuid

from verification_evidence import (Report, SCHEMA_VERSION, execution, file_digest,
                                   settings, source_snapshot, utc_now, validate_evidence)
from verification_profiles import PROFILES, plan


class Once(argparse.Action):
    """Reject accidental repeated options instead of silently taking the last one."""
    def __call__(self, parser, namespace, values, option_string=None):
        seen = getattr(namespace, '_seen', set())
        if self.dest in seen:
            parser.error(f'duplicate option: {option_string}')
        seen.add(self.dest)
        namespace._seen = seen
        setattr(namespace, self.dest, True if self.nargs == 0 else values)


def arguments():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('profile', nargs='?', choices=PROFILES)
    parser.add_argument('--browser', nargs=0, action=Once, default=False,
                        help='run browser checks last (requires both toolchains)')
    parser.add_argument('--output', type=Path, action=Once, help='fresh evidence directory outside the checkout')
    parser.add_argument('--check', type=Path, action=Once, help='read-only check of existing evidence; runs no checks')
    args = parser.parse_args()
    if args.check is not None:
        if args.profile or args.browser or args.output is not None:
            parser.error('--check cannot be combined with a profile, --browser, or --output')
    elif args.profile is None:
        parser.error('choose an explicit frontend, backend, or full profile')
    return args


class Processes:
    """Own the active command group, with bounded signal-driven cleanup."""
    def __init__(self):
        self.active = None
        self.interrupted = None
        self.previous = {}

    def __enter__(self):
        for signum in (signal.SIGINT, signal.SIGTERM, signal.SIGALRM):
            self.previous[signum] = signal.getsignal(signum)
            signal.signal(signum, self.force if signum == signal.SIGALRM else self.stop)
        return self

    def send(self, signum):
        if self.active is not None:
            try:
                os.killpg(self.active.pid, signum)
            except ProcessLookupError:
                pass

    def stop(self, signum, _frame):
        if self.interrupted is None:
            self.interrupted = signum
        self.send(signum)
        if self.active is not None:
            signal.setitimer(signal.ITIMER_REAL, 2)

    def force(self, _signum, _frame):
        self.send(signal.SIGKILL)

    def __exit__(self, *_args):
        signal.setitimer(signal.ITIMER_REAL, 0)
        for signum, handler in self.previous.items():
            signal.signal(signum, handler)

    def run(self, argv, root, log):
        try:
            self.active = subprocess.Popen(argv, cwd=root, stdout=subprocess.PIPE,
                                           stderr=subprocess.STDOUT, start_new_session=True)
        except FileNotFoundError:
            log.write(b'Executable or interpreter not found.\n')
            return 127
        except PermissionError:
            log.write(b'Executable permission denied.\n')
            return 126
        try:
            if self.interrupted is not None:
                self.stop(self.interrupted, None)
            with self.active.stdout as output:
                console = True
                for chunk in iter(lambda: os.read(output.fileno(), 65536), b''):
                    log.write(chunk)
                    log.flush()
                    if console:
                        try:
                            sys.stdout.buffer.write(chunk)
                            sys.stdout.buffer.flush()
                        except BrokenPipeError:
                            console = False
            code = self.active.wait()
            return 128 + self.interrupted if self.interrupted else (128 - code if code < 0 else code)
        except BaseException:
            self.send(signal.SIGKILL)
            self.active.wait()
            raise
        finally:
            self.active = None
            signal.setitimer(signal.ITIMER_REAL, 0)


def skip_pending(data, reason):
    for check in data['checks']:
        if check['status'] in ('pending', 'running'):
            check['status'] = 'skipped'
            check['reason'] = reason


def run_profile(root, args):
    # Validate all setup before any verification command executes.
    report = Report(root, args.output)
    source = source_snapshot(root)
    data = {
        'schema_version': SCHEMA_VERSION, 'run_id': str(uuid.uuid4()), 'root': str(root),
        'profile': args.profile, 'browser': args.browser, 'started_at': utc_now(),
        'finished_at': None, 'status': 'running', 'exit_code': None,
        'source_before': source, 'source_after': None, 'source_unchanged': None,
        'settings': settings(), 'execution': execution(root), 'failure': None,
        'checks': [{**check, 'cwd': str(root),
                    'status': 'pending' if check['selected'] else 'skipped',
                    'reason': None if check['selected'] else 'not selected by profile/browser options'}
                   for check in plan(args.profile, args.browser)],
    }
    report.save(data)
    print(f'Manifest: {report.path}', flush=True)
    code = 0
    with Processes() as processes:
        try:
            for number, check in enumerate(data['checks'], 1):
                if processes.interrupted:
                    code = 128 + processes.interrupted
                    break
                if not check['selected']:
                    continue
                start = time.monotonic()
                check.update(status='running', started_at=utc_now(),
                             log=f'{number:02d}-{check["id"]}.log')
                report.save(data)
                print(f'Running {check["id"]}…', flush=True)
                with report.open_log(check['log']) as output:
                    code = processes.run(check['argv'], root, output)
                check.update(status='passed' if code == 0 else 'failed', exit_code=code,
                             finished_at=utc_now(), duration_seconds=round(time.monotonic() - start, 6),
                             sha256=file_digest(report.directory / check['log']))
                if code:
                    data['failure'] = f'{check["id"]} failed (exit {code})'
                report.save(data)
                if code:
                    break
        except (OSError, ValueError) as exc:
            code = code or 1
            data['failure'] = f'evidence or execution failure: {exc}'
            for check in data['checks']:
                if check['status'] == 'running':
                    check.update(status='failed', exit_code=code, finished_at=utc_now(),
                                 duration_seconds=round(time.monotonic() - start, 6),
                                 reason=data['failure'])
            print(data['failure'], file=sys.stderr)
        if processes.interrupted:
            code = 128 + processes.interrupted
            data['failure'] = f'interrupted by {signal.Signals(processes.interrupted).name}'
        skip_pending(data, data['failure'] or 'run interrupted before check execution')
        try:
            data['source_after'] = source_snapshot(root)
            data['source_unchanged'] = source == data['source_after']
            if not data['source_unchanged']:
                code = code or 1
                data['failure'] = data['failure'] or 'source changed during verification'
        except (OSError, ValueError) as exc:
            code = code or 1
            data['failure'] = data['failure'] or f'cannot capture final source evidence: {exc}'
        data.update(finished_at=utc_now(), exit_code=code,
                    status='interrupted' if processes.interrupted else ('failed' if code else 'passed'))
        try:
            report.save(data)
        except (OSError, ValueError) as exc:
            code = code or 1
            print(f'Cannot finalize evidence: {exc}', file=sys.stderr)
    if code:
        print(f'Verification failed (exit {code}): {data["failure"]}', file=sys.stderr)
    else:
        print(f'Verification passed for {args.profile}' + (' with browser checks.' if args.browser else '.'))
    return code


def main():
    args = arguments()
    root = Path(__file__).resolve().parents[1]
    try:
        if args.check is not None:
            data = validate_evidence(root, args.check)
            print(f'Evidence matches current source and settings; recorded scope: {data["profile"]}'
                  + (' with browser checks.' if data['browser'] else '.'))
            return 0
        return run_profile(root, args)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f'Verification error: {exc}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
