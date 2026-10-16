# Frontend static analysis

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: Add actionable correctness, accessibility, and maintainability diagnostics to frontend builds.
- Depends on: [Build automation](build-automation.md)

## Summary

Extend existing ESLint with advisory type-aware, JSX accessibility, and complexity
rules. Add Knip for unused files, exports, and dependencies. Produce machine-readable
and human-readable reports as part of the full build, without weakening existing lint.

## Goals

- Detect additional issues without requiring a large cleanup in this change.
- Preserve existing lint enforcement and fail clearly when analyzers cannot run.
- Provide repeatable reports and document how to review them.

## Non-goals

- Refactoring the application to clear new findings, replacing Prettier/ESLint,
  introducing Opengrep, or changing browser behavior.

## Background

Existing ESLint uses non-type-aware recommended TypeScript and React Hooks rules.
TypeScript already checks strict types and unused locals/parameters. The build
wrapper runs frontend compilation and Maven, but no frontend analysis. The audit
identified preserving lint enforcement and distinguishing findings from tool errors
as integration prerequisites; broader source cleanup is excluded.

## Requirements

### Functional requirements

1. Keep `npm run lint` enforcement intact and expose a separate `npm run analyze`.
2. Add recommended type-aware ESLint rules and recommended JSX accessibility
   rules as warnings, preserving any already-enabled rule's severity.
3. Analyze production functions with ESLint complexity >10, max-depth >4, and
   max-lines-per-function >80 (excluding comments/blank lines) as warnings.
   Test functions are excluded only from those three maintainability metrics.
4. Run Knip against application, test, configuration, and tooling entry points.
   Do not suppress genuine unused-code findings with broad ignores.
5. Write ESLint and Knip JSON/text reports plus summary JSON/text under
   `frontend/reports/static-analysis/`, outside bundled assets and Git.
6. Advisory findings exit successfully. Existing ESLint errors, fatal parse errors,
   invalid analyzer configuration, missing tools, malformed output, and report
   write failures return nonzero. No blanket success fallback is allowed.
7. Refresh owned report files on each run; failures must not leave an old success
   summary. Preserve unrelated files; refuse symlinked report directories.
8. The full build runs analysis before compilation/Maven, requires its summary,
   and prints its location in both normal and skip-tests modes.

### Non-functional requirements

- Use locally installed, lockfile-resolved tooling without downloads during analysis.
- Reports identify files, locations/rules when supplied, severities, counts, and
  success/failure status. Retain raw analyzer JSON for detailed inspection.
- No autofix or application source changes merely to reduce diagnostics.

## Contracts and data

`npm --prefix frontend run analyze` returns 0 on a completed advisory run and 1
on lint/tool/report failure. `summary.json` contains schemaVersion, status,
ESLint counts, and Knip issue count; `failure.txt` describes failed execution.
ESLint's existing errors remain errors in the combined analysis report. Reports
are replaced per invocation, not maintained as a committed baseline.

## Technical constraints

Node 24, ESLint 9, existing TypeScript/React tooling. Match installed Knip's
JSON contract; use its advisory CLI flag while preserving internal failure codes.
Docker packaging is not a substitute for the tested full-build workflow.

## Invariants

- Existing lint rules and application behavior remain unchanged.
- New findings are advisory, while existing errors and analyzer failures block.
- Generated reports never become frontend assets or backend jar resources.

## Edge cases and failures

- Empty findings still produce valid reports and zero counts.
- Configuration/parse failures may leave diagnostic reports but never a success summary.
- Knip's static graph can need narrow entry configuration for Vite/test scripts.
- Report paths containing spaces work; unrelated report files are not deleted.

## Migration and compatibility

Install new dev dependencies with npm ci after checkout. Builds now run frontend
analysis before compilation. Existing source findings are recorded for later
review. `--skip-tests` still runs analysis and only skips Java tests.

## Acceptance criteria

- [x] Known-bad/clean fixtures exercise actual installed analyzers.
- [x] Existing lint errors and analyzer failures block; advisory findings do not.
- [x] Reports and build summary are complete, current, and excluded from artifacts.
- [x] Frontend checks and build-wrapper tests pass; full build is verified.

## Verification

User confirmed **comprehensive coverage**: typed/accessibility/complexity fixtures,
unused code, existing lint enforcement, report contents, analyzer failures,
stale-report handling, and both full-build modes. Use actual analyzers on temporary
projects plus targeted failure fixtures and build-wrapper stubs. Run frontend
tests/lint/typecheck/format/build and the full project build. No performance thresholds.

Executed on 2026-10-16: 14 analyzer integration tests, 23 build-wrapper tests,
8 frontend unit tests, frontend lint/typecheck/format checks, and the full build
passed. Maven verified 571 backend tests with no failures/errors/skips. Initial
tests demonstrated the absent analyzer and missing build integration; an added
failure test reproduced stale success summaries when report cleanup failed,
which was fixed by invalidating completion markers first. Analyzer subprocesses
required sandbox escalation; this is an execution-environment restriction, not
a diagnostic suppression. Initial reports contain 42 advisory ESLint warnings
and zero Knip findings. No application code changed, so browser flows were not
rerun. Report exclusion from dist and the packaged jar was checked directly.

## Architecture decisions

[ADR-0010](../docs/adr/0010-frontend-static-analysis.md) records tooling and failure policy;
creation was explicitly approved by the user.

## Open questions

None. Thresholds are initial advisory review signals and can be tuned after reviewing findings.
