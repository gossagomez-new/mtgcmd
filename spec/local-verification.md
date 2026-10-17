# Local verification profiles and evidence

## Metadata

- Status: Completed
- Priority: High
- Priority rationale: Make repeated local checks and their evidence consistent.
- Depends on: [Build automation](build-automation.md), [GitHub Flow](github-flow.md), [Specification validation](specification-validation.md)

## Summary

Provide explicit frontend, backend, and full verification profiles with optional
browser checks. Reuse existing commands and packaging, execute sequentially, and
retain source-bound JSON evidence and per-command logs outside the checkout.

## Goals

- Select coherent local checks without duplicating packaging orchestration.
- Preserve failures and distinguish passed, failed, and skipped checks.
- Make evidence auditable against the source and execution settings it describes.

## Non-goals

- Install dependencies, auto-select checks from diffs, or change CI policy.
- Deploy, commit, publish, or prove architectural/test correctness.
- Attest ignored dependency caches or authenticate evidence from untrusted parties.
- Create or modify skills, or automatically reuse old results instead of running.

## Background

The audit found frontend analysis/compilation and Java verification already owned
by scripts/build.sh. Browser tests start Java processes while packaging cleans
backend output. Profiles must preserve those boundaries and run sequentially.
The CI verify job remains the canonical full hosted sequence; local profiles reuse
its commands with documented omissions and duplicate work avoided.

## Requirements

### Functional requirements

1. Add scripts/verify.sh as an executable entry point, delegating to a Python
   standard-library runner and a shared profile/command definition. Resolve the
   checkout from script location, independent of caller directory. Require a
   Git working tree whose top-level directory is that checkout.
2. CLI: verify.sh frontend|backend|full [--browser] [--output PATH]; verify.sh
   --check MANIFEST; verify.sh --help. Reject mixed/unknown/duplicate options and
   missing profiles with exit 2 before executing checks or creating output.
3. Frontend runs lint, format:check, frontend unit tests, analyzer tests, analyze,
   and build. Build includes TypeScript checking; do not run tsc separately.
   Backend runs mvn -f backend/pom.xml spotless:check verify, including Java tests
   and PMD. This profile does not claim to refresh the bundled frontend.
4. Full runs frontend lint/format/unit/analyzer tests, build-wrapper/PMD tests,
   Python tooling tests, specification validation, Java formatting, and scripts/build.sh. The build owns frontend
   analysis/compilation and Maven clean verify. Do not use --skip-tests. Keep
   profile definitions centralized; test consistency with current CI commands.
5. Browser checks are opt-in for every profile and always run last with
   npm --prefix frontend run test:e2e. They need both toolchains and may recompile
   test-server code; this intentional overlap verifies real transport behavior.
   Run every command sequentially, with no overlapping clean/server processes.
6. Include every defined check in evidence: unselected checks are skipped with a
   reason; selected later checks are skipped after failure. Selected successful
   and failing checks retain argv, cwd, UTC start/end timestamps, duration, exit
   code, relative log path, and log SHA-256. Interim states may be pending/running.
7. Return the first failing command's exit code, mapping signal exits to 128+signal,
   missing executable to 127, and non-executable tool to 126. Stop further checks.
   Operational/evidence failures return 1 unless a command already failed, in
   which case preserve its status. Never print overall success after failure.
8. Handle SIGINT/SIGTERM by stopping the active command process group with bounded
   grace then forced termination, preserving an interrupted manifest and skipped
   later checks; return 128+signal. SIGKILL/system power loss cannot guarantee a
   finalized manifest. Never identify a running/pending manifest as successful.
9. Stream merged stdout/stderr to console and the per-check log. Child output is
   retained verbatim and may contain tool-supplied sensitive data; the runner does
   not dump its inherited environment. Record only CHROME_PATH/JAVA_HOME paths,
   allowlisted CI booleans, option presence/hashes for MAVEN_OPTS, NODE_OPTIONS,
   JAVA_TOOL_OPTIONS and JAVA_OPTS, Python/platform versions, and resolved tool
   paths (resolving PATH against the command working directory). Preserve all
   inherited settings when executing commands.
10. Default output is a unique private directory in the system temporary folder.
    Explicit output must be a fresh directory outside the checkout with an
    existing directory parent and no symlink ancestors. Refuse existing paths,
    symlinks, or in-checkout destinations before running tools. Do not delete or
    overwrite existing evidence. Use private file permissions and atomic manifest
    replacement within the owned directory. Keep logs after failures.
11. Manifest schema version 1 records run ID, profile/browser selection, root,
    timestamps, overall status/exit, settings/tools, and source before/after.
    Fingerprint HEAD, branch, tracked staged/unstaged diffs, Git status, and content
    of tracked plus nonignored untracked paths (including executable mode and deletion/symlink identity).
    Store hashes and counts, never raw diffs or file contents. Ignored artifacts,
    secrets, caches, and dependencies remain outside the source claim.
12. If source differs at the end, overall evidence is failed and cannot be reused,
    even if all commands passed; return 1 unless a prior failure takes precedence.
    Start/end equivalence cannot detect transient edits restored during execution.
13. --check is read-only: require supported, complete, successful evidence with the
    current profile command plan, matching source/settings/tool paths, and intact
    logs. Reject stale, failed, incomplete, malformed, unsupported, or tampered-log
    evidence with exit 1 and a useful explanation. It performs no check commands,
    never updates evidence, and cannot establish authenticity or truth of claims.

### Non-functional requirements

- Bash and Python standard library, existing Java/Node/Maven/Git prerequisites.
- No command evaluation through a shell; execute fixed argv from profile definitions.
- Evidence describes the exact selected scope; skipped checks never count as passed.
- Retain the existing stable CI job name and build-wrapper behavior.

## Contracts and data

Centralized command definitions supply stable check IDs, argv, profile selection,
and scope descriptions. Manifest JSON is versioned; logs are relative to the owned
output directory and hashes bind their captured bytes. Source identity is distinct
from success. A successful frontend profile does not establish full-stack success.
Raw logs are local evidence, not safe-to-publish artifacts or signed attestations.

## Technical constraints

Keep CI's explicit command sequence, add runner tests to existing Python discovery,
and test plan parity against it. Full packaging delegates to the existing build
wrapper. Specification validation must be available in the checkout; full uses that
existing tool rather than adding another document checker.

## Invariants

- Failed commands cannot be masked by later successful bookkeeping.
- The runner never changes application source or reuses results automatically.
- Browser server lifecycle cannot overlap a packaging clean step in one run.
- No inherited credentials or option strings are copied into manifest metadata.

## Edge cases and failures

Paths with spaces, dirty worktrees, untracked files, deleted files, symlinks,
detached HEAD, missing tools, output I/O failures, interruptions, and source edits
must yield accurate evidence or fail before commands run. Evidence for a previous
source state is rejected without altering it.

## Migration and compatibility

Existing commands and CI remain available. New local profiles are opt-in;
removing the entry point and runner has no application/data migration impact.

## Acceptance criteria

- [x] Profiles execute exactly their documented scope in sequence.
- [x] Failure/signal fixtures preserve exit status, logs, and skipped steps.
- [x] Source-bound evidence rejects stale state and corrupted logs without writes.
- [x] Real profile and browser verification pass with accurate evidence.

## Verification

User selected comprehensive coverage. Test all profiles/CLI cases, command order,
intentional duplicate avoidance, CI parity, failure preservation/skips, missing
and non-executable tools, signals and process cleanup, safe output paths, source
changes/stale evidence (including staged, unstaged, untracked, deletion, symlink,
and detached HEAD), environment inheritance/redaction, log integrity, malformed
manifests, read-only checks, and evidence I/O failures. Demonstrate red before
implementation. Run fixture tests, existing Python/wrapper tooling tests, shell
syntax/ShellCheck, formatting/workflow/spec validation, then real frontend/backend
and full with browser profiles. Do not claim unexecuted checks passed.

### Executed verification

On 2026-10-16, the CLI scaffold failed the expected missing-profile/evidence
assertions before implementation. Subsequent red/green cases covered finite
numeric fields, complete identities/timestamps, executable modes, disappearing
logs, and relative PATH tool identity. All 18 runner test methods passed within
the complete 66-test Python tooling suite. Shell syntax/ShellCheck, workflow
Actionlint, changed documentation formatting, and the 29-specification validator
passed.

Real frontend, backend, and full-with-browser profiles passed. The full run
passed 66 frontend unit tests, 41 analyzer tests, 26 build-wrapper/PMD tests,
66 Python tooling tests, 571 Java tests, and all 35 browser scenarios. Existing
advisory findings remain advisory; no suppressions or thresholds were changed.
The full manifest passed the read-only evidence check. After the branch moved
to updated main (PR #7 had merged), the same manifest was correctly rejected as
stale without being rewritten. A fresh final run binds evidence to the completed
source/documentation state. Hosted Actions, publication, and deployment were not
performed by this task.

## Architecture decisions

[ADR-0015](../docs/adr/0015-local-verification-evidence.md): local profiles,
sequential command reuse, and source-bound versioned evidence.

## Open questions

None. Hosted CI execution of this new runner fixture suite remains pending publication.
