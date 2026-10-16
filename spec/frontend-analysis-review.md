# Frontend analysis review automation

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: Automate repeated ESLint/Knip review and baseline comparisons without changing the advisory policy.
- Depends on: [Frontend static analysis](frontend-static-analysis.md)

## Summary

Extend existing analysis with deterministic scope/rule/file summaries, saved comparisons, provenance checks, and a contextual review skill.

## Goals

- Reuse existing ESLint/Knip execution and produce reviewable counts and findings.
- Provide an offline review CLI with explicit baseline and new output paths.
- Guide source-aware recommendations using existing audit/refactor skills.

## Non-goals

- Change thresholds, suppressions, advisory gates, source code, or dependencies.
- Automatically advance baselines, fix findings, publish, or deploy.
- Infer exact metrics where native messages do not supply them.

## Background

The pre-design audit identified missing provenance and aggregate-only frontend summaries. It also found that the current Knip count omits its native top-level unused-files array. Complete counts and source/report identity are prerequisites for the approved reporting work. Existing PMD comparison guidance and design-audit/refactor-explanation skills remain reusable; the Java XML parser is not reused for JSON.

## Requirements

### Functional requirements

1. Preserve analyzer execution and existing completion/failure semantics. Add source/report provenance and deterministic review summaries without removing existing summary fields.
2. Count active ESLint errors/warnings and Knip issues, including unused files. Classify tests before production: tests directory and test/spec filenames are tests, other src files production, remaining paths other.
3. Normalize relative, POSIX absolute, and Windows paths to frontend-relative paths. Reject traversal/outside-root paths rather than assigning misleading source scopes. Support different checkout roots explicitly.
4. Summarize findings by scope, tool/rule, file, and available native metrics. Sort output deterministically. Do not deduplicate genuine duplicate occurrences.
5. Compare an explicit saved review JSON baseline as a multiset of tool/file/rule/severity/message identities. Ignore line/column changes. File renames or changed messages appear as resolved/new, with the limitation stated. Comparisons do not prove semantic identity or quality improvement.
6. Report provenance as current, stale, or unverified. Hash analysis source/config inputs before and after generation; mark/report mutation during execution as failure. Bind provenance to native report hashes. Legacy completed reports may be read with unverified freshness; mismatched provenance or counts must not appear current.
7. Reject malformed/incomplete reports and invalid baselines. Missing completion markers or failure markers produce an incomplete-analysis exit status. Do not replace missing tools/data with zero findings.
8. CLI defaults work from any directory. Support reports/root/baseline and optional JSON/Markdown output paths. Print Markdown by default. Refuse overwrites and symlinked output parents; preserve existing inputs and baselines. Validate all destinations before writing and clean up only newly reserved output files on failure.
9. The skill reads reports by default, regenerates only within the authorized task, inspects source/tests, and reuses existing audit/refactor guidance. It reports limitations, counts, deltas, hotspots, and a reasoned next target.

### Non-functional requirements

- No new dependencies or network requests in parsing/comparison.
- Keep generated artifacts ignored and out of application assets.
- Use only native diagnostic metrics; no performance assertions.

## Contracts and data

Native summary schema retains version 1 and existing status/eslint/knip fields, with additive provenance/review data. Saved review JSON has an explicit kind/schema version, normalized findings, totals, source scopes, per-rule/file counts, and provenance. Exit 0 means successful review regardless of advisory findings; exit 2 incomplete analysis; exit 1 invalid input/output or CLI usage. Old reports without provenance remain readable but unverified. Saved baselines must be completed compatible review artifacts and are never updated implicitly.

## Technical constraints

Source fingerprinting covers frontend JS/TS/JSON/HTML/CSS inputs, including package lock/configuration and scripts, excluding generated/dependency/report directories and build info. It does not attest backend state, external plugins, environment equivalence, or correctness; record its scope. Tool/runtime metadata and source changes are comparison caveats. Helpers must be copied into the existing real-analyzer fixtures and new tests included in the established test:analysis/CI entrypoint.

## Invariants

- Findings remain advisory; analyzer/validation failures remain failures.
- Baseline writes are explicit and cannot overwrite prior artifacts.
- Source locations can move without changing occurrence identity; renames cannot silently be treated as the same file.
- Failed/stale/unverified provenance cannot be described as current.

## Edge cases and failures

Empty reports, duplicate occurrences, moved lines, changed rules/messages/files, other-source findings, cross-checkout paths, parser diagnostics, failed generators, changed inputs/reports, malformed JSON/counts, existing outputs, symlinks, and multiple-output reservation failures receive explicit handling.

## Migration and compatibility

Additive summary fields preserve build consumers. The unused-file count correction can increase reported Knip totals; it does not introduce a new build gate. Legacy reports require no migration for unverified reading. Save baselines outside the analyzer-owned regenerated directory.

## Acceptance criteria

- [x] New fixtures demonstrate missing reporting/comparison behavior before implementation.
- [x] Existing analyzer integration and build checks remain passing.
- [x] Actual current report produces scope/rule/file counts and can be compared with an explicitly saved temporary baseline.
- [x] Skill validates and follows existing authorization boundaries.

## Verification

User selected comprehensive coverage and approved skill creation. Cover summaries, all scopes and native metrics, Knip unused files/categories, empty/invalid/incomplete reports, count disagreements, duplicate/moved/renamed findings, cross-checkout paths, stale/unverified/tampered provenance, CLI defaults/errors, and output safeguards. Retain real analyzer fixtures and test failure cleanup. Run analyzer tests, frontend unit/lint/type/format/build checks, build-wrapper/PMD integration tests, full packaging verification, and skill validation. Browser flows are not required: application behavior is unchanged.

### Executed checks

- New report fixtures demonstrated missing summaries, comparison, provenance, and CLI output before implementation. A real analyzer fixture reproduced the omitted unused-file count (0 instead of 1). Additional tests reproduced JSON-key-order sensitivity, incorrect missing-artifact status, and acceptance of a completion marker despite enforced errors before fixes.
- Combined analyzer/review suite: 41 passed, including 16 real-analyzer integration fixtures and 25 report/CLI fixtures.
- Existing frontend unit suite: 66 passed. Enforced lint, type checking, formatting, and git whitespace checks passed.
- Build-wrapper and PMD integration suite: 26 passed.
- Full analyzed build passed, including 571 backend tests. Generated analysis reports and review scripts are excluded from the packaged application.
- Skill structural validation and local documentation links passed.
- Live CLI smoke checks from /tmp saved a new baseline and compared it to the same report: 0 new, 0 resolved, 17 unchanged occurrences. Freshness was current; counts were 6 production warnings, 11 test warnings, zero other warnings, and zero Knip issues.
- Browser flows were not rerun because application behavior is unchanged. Analyzer/CLI subprocess fixtures needed execution outside the sandbox; sandbox process-launch errors were treated as environment failures rather than analyzer results.

## Architecture decisions

[ADR-0010](../docs/adr/0010-frontend-static-analysis.md) already establishes existing analyzers, ignored reports, and advisory/failure policy. This work extends reporting under that convention without new technology or system ownership changes; no new ADR is required.

## Open questions

None.
