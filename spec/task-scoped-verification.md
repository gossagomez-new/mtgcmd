# Task-scoped verification

## Metadata

- Status: Completed
- Priority: High
- Priority rationale: Reduce repeated test execution approvals with a bounded task command.
- Depends on: [Local verification](local-verification.md)

## Summary

Extend the existing verification runner with a task-first interface, fixed scopes,
internal logging and optional local path defaults so repeated runs share one
reviewable command prefix.

## Goals

- Reuse a task-specific approval prefix across frontend, backend, browser and full checks.
- Preserve sequential execution, failures, signals and source-bound evidence.
- Keep environment setup and changing log paths out of invocation text.

## Non-goals

- Editing Codex rules, auto-approving actions, expiring permissions, or promising no prompts.
- Git publication, deployment, formatting writes, dependency installation or arbitrary commands.
- Arbitrary test selectors, JVM option strings or environment assignments from task arguments.
- Replacing CI, the build wrapper, or existing verification profiles.

## Background

The pre-design audit found that external environment assignments change approval
commands, and browser checks currently require another profile. These are required
integration changes for the requested task command. Fixed argv definitions,
sequential execution and evidence validation remain owned by the current runner.
The user confirmed comprehensive coverage and ADR-0023 on 2026-10-19.

## Requirements

### Functional requirements

1. Accept `./scripts/verify.sh --task TASK PROFILE`, optionally followed by
   `--browser` for existing profiles. Require the literal `--task TASK` pair first;
   reject reordered/equal-sign task syntax, duplicate flags, unknown arguments,
   shell command passthrough and output overrides before executing any check.
2. Task names contain lowercase ASCII letters/digits separated by single hyphens,
   start/end alphanumeric, and are 1–64 characters. Reject path traversal,
   separators, whitespace and shell expressions. They are labels, not authorization.
3. Add a browser-only profile using the existing fixed browser command exactly once.
   Reject redundant `browser --browser`. Existing frontend/backend/full commands,
   output overrides and read-only evidence checks continue to work without task mode.
4. All task runs use fresh private output directories outside the checkout, labelled
   with task and a unique suffix. Repeated invocations never overwrite evidence.
   Include the task in manifests and console scope reporting; retain schema v1
   compatibility with legacy manifests lacking task metadata. Validate task metadata.
5. Read optional `.verification.local.json` only for task execution/evidence checks.
   Accept only `mavenRepository`, `javaTemporaryDirectory`, and `chromePath`, each
   an existing absolute path of the required type; Chrome must be executable.
   Reject malformed/non-object JSON, duplicate/unknown keys, symlink config files,
   invalid types, missing paths, and JVM paths containing whitespace or quotes.
   Do not create configured directories or execute settings as code.
6. Local paths supply defaults for MAVEN_OPTS (maven.repo.local), JAVA_TOOL_OPTIONS
   (java.io.tmpdir), and CHROME_PATH. Existing variables, including empty values,
   always take precedence. Legacy invocations ignore local settings. Keep effective
   settings hashed/redacted in evidence and detect changed effective settings on
   read-only validation, including validation from handoff tooling.
7. Never change approval policy or include arbitrary command/configuration loading.
   Document an example prefix `['./scripts/verify.sh', '--task', 'TASK']`, its fixed
   allowed scopes and its limitations. Actual approval is controlled by Codex;
   saved rules may persist and do not pin script contents or expire when a task ends.

### Non-functional requirements

- Python standard library and existing Bash entry point; no new dependency.
- Fail before running checks on invalid setup; preserve child failure exit status.
- Preserve signal cleanup, log privacy, stale-evidence detection and command ordering.
- No parallel verification invocations in the same checkout; existing shared outputs remain.

## Contracts and data

Task-first CLI is additive. Optional task metadata is validated even in externally
consumed evidence. Local JSON is ignored by Git; a tracked example documents paths.
Approval prefixes are suggested for human review, never installed by this runner.

## Technical constraints

Reuse verification_profiles, verify.py and verification_evidence. Centralize path
configuration and task validation without mutating the parent process environment.
No shell evaluation of user inputs. Existing CI and check argv remain unchanged.

## Invariants

- Task names cannot select executables or widen checks beyond fixed profiles.
- Logs/manifest never claim skipped checks passed or record raw JVM option strings.
- Evidence remains scoped to source, selected checks and effective settings.

## Edge cases and failures

Repeated task runs, missing config, inherited empty values, spaces in executable
paths, invalid JVM path quoting, duplicate JSON keys, malformed task metadata,
read-only validation after settings changes, interruptions and command failures.

## Migration and compatibility

Existing commands remain supported. Task mode opts into optional local defaults.
Old complete evidence remains structurally supported; source/command-plan checks
still invalidate stale reports. No app protocol or deployment changes.

## Acceptance criteria

- [x] Task commands share a task-specific prefix with predefined profile selection.
- [x] Invalid arguments/settings run no tools and do not overwrite reports.
- [x] Defaults, overrides, task evidence and read-only validation work correctly.
- [x] Existing runner and handoff tests remain passing.
- [x] Real representative scopes execute without command-line environment/log wrappers.

## Verification

User confirmed **comprehensive coverage** on 2026-10-19. Test task/profile selection,
invalid names and CLI forms, browser-only scope, repeated/private output, local JSON
validation, defaults and inherited settings, redaction, stale task evidence, legacy
compatibility, command failures and signal cleanup. Run tooling fixtures first red,
then green; retain existing verification/handoff suites. Exercise real task-scoped
backend, frontend and browser checks, validate documentation/specification links,
and run whitespace checks. No performance assertions or app behavior changes.

## Architecture decisions

[ADR-0023](../docs/adr/0023-task-scoped-verification.md) records task prefixes and
local path defaults; [ADR-0015](../docs/adr/0015-local-verification-evidence.md)
retains sequential checks and source-bound evidence.

## Open questions

None.

### Execution results

- TDD red: the new task command tests failed because task/profile support was
  absent; all 18 original runner tests passed before production changes.
- Final Python tooling suite: 99 passed, including 27 runner tests and 24 handoff
  tests. Coverage includes invalid inputs, defaults/overrides, stale task evidence,
  exit preservation and child termination.
- Real `--task task-verification frontend`: passed lint, formatting, 225 unit
  tests, 41 analyzer tests, analysis and production build. Analysis retained seven
  advisory ESLint warnings and zero Knip issues.
- Real backend profile under the same approved prefix: passed Spotless and Maven
  verification with 675 Java tests, packaging, PMD and JaCoCo reports.
- Real browser-only profile under that prefix: 117 browser tests passed.
- Each run wrote a fresh private task-labelled manifest; all three read-only
  evidence checks passed before this final documentation update. Evidence files
  remain original pre-staging records and are not rebound after edits or commits.
- Local machine paths were configured in ignored JSON; no Codex policy file was
  edited. Task-prefix approval was requested once and reused across all scopes.
- Full profile selection/order is covered by fixture tests; real execution used
  the three individual scopes above rather than claiming a real full-profile run.
