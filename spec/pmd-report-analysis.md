# PMD report analysis automation

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: Repeated report reviews benefit from deterministic summaries and contextual interpretation.
- Depends on: [Advisory Java static analysis](java-static-analysis.md)

## Summary

Add a standard-library Python parser and a repository companion skill for PMD
report analysis. Separate deterministic counting from evidence-based code review.

## Goals

- Summarize native XML and compare saved summaries consistently.
- Retain report diagnostics and distinguish advisory findings from analysis failures.
- Guide contextual interpretation without treating count increases as defects.

## Non-goals

- Change PMD rules, builds, application code, or automatically fix findings.
- Automatically regenerate reports or install a global skill.
- Track individual finding identities across edits.

## Background

Repeated manual reviews parse the same native report, separate main/test sources,
and inspect complexity and contextual warnings. The pre-design audit found the
existing build report adequate; no prerequisite remediation or scope expansion
is needed. Aggregate comparison avoids brittle line-based finding identities,
but cannot prove that specific findings were introduced or fixed.

## Requirements

### Functional requirements

1. Parse namespaced native PMD XML, including empty reports and duplicate findings.
2. Count findings by production/test/other scope, rule, priority, and normalized file.
3. Preserve report version/timestamp, suppressions, processing/configuration errors;
   do not count diagnostics or suppressions as active findings.
4. Normalize repository paths and recognize backend/src paths across checkouts.
   Retain unrecognized paths with other classification.
5. Accept an optional versioned JSON summary baseline; validate its counts and
   schema before comparing totals, scopes, rules, and files. Identify PMD-version
   changes and state that rules/source scope changes also affect comparability.
6. Print Markdown to stdout by default. Optional --markdown and --json paths
   create artifacts without overwriting existing files or input paths.
7. Return 0 for a complete report regardless of violations, 2 for PMD analysis
   diagnostics (still emit summaries), and 1 for invalid inputs or output failures.
8. The companion skill reads reports by default, discovers the repository command
   if regeneration is requested, inspects implicated code before recommendations,
   distinguishes style noise and contextual warnings, and records verification limits.

### Non-functional requirements

- Python standard library only, deterministic ordering, no subprocess/network calls.
- Reject malformed XML, unsupported summary schemas, and XML DTD declarations.
- No runtime or build dependency changes.

## Contracts and data

CLI: python3 scripts/analyze-pmd.py [report] [--root root] [--baseline summary.json]
[--markdown new.md] [--json new.json]. Default report/root are resolved relative
to the script's repository, independent of the working directory. JSON schema
version 1 records counts, report provenance, diagnostics, and an optional comparison.
Generated files are user-selected; recommended output locations are backend/target
or /tmp. Baselines inform review and never suppress findings.

## Technical constraints

Reuse native PMD XML. Place the skill at skills/pmd-report-analysis/SKILL.md and
parser at scripts/analyze-pmd.py. Do not alter the general audit skill.

## Invariants

- Active finding totals match scope/rule/file/priority sums.
- Duplicate violations remain counted; suppressions remain separate.
- Diagnostics never imply a complete analysis or zero issues.
- No existing output or input file is overwritten.

## Edge cases and failures

Cross-checkout paths, spaces, absent scopes, empty XML reports, errors mixed with
findings, incompatible baselines, changed PMD versions, invalid output locations.

## Migration and compatibility

No migration or build integration. This is an optional repository tool and skill.

## Acceptance criteria

- [x] Fixture tests establish missing behavior before implementation and pass afterward.
- [x] Native application report counts match the previous analysis.
- [x] JSON baseline round-trip reports zero count deltas on unchanged inputs.
- [x] Skill validates and references resolve.

## Verification

Confirmed target: comprehensive coverage. Use unittest fixtures for namespaces,
scopes, priorities, duplicates, suppressions/errors, empty/malformed reports,
cross-checkout paths, version changes, baseline validation, CLI outputs/exit codes,
and output safeguards. Run the parser against the real report and its saved summary;
validate the skill with skill-creator's validator, check Markdown formatting and
whitespace. No application tests/build needed because application behavior and
build configuration do not change.

### Executed verification

- Nine fixture tests pass. Initial CLI output cases failed because the proposed
  parser was absent. A later UTF-16 DTD case failed with exit 0 before replacing
  the byte-level check with parser-level rejection; the corrected case passes.
- Real-report summary matches 2,836 active findings: 1,620 production, 1,216 tests,
  zero other; no analysis diagnostics. No new PMD run was performed.
- Saved-summary comparison produces zero deltas on unchanged report input.
- Skill-creator validation, skill/spec relative links, Markdown formatting, and
  git whitespace checks pass. Application tests/build were not rerun because
  only optional tooling and documentation changed.

## Architecture decisions

[ADR-0003](../docs/adr/0003-advisory-java-static-analysis.md) retains advisory analysis.
No new ADR is necessary for this optional read-only tool; schema versioning is a
local compatibility detail, not an application contract or module-boundary change.

## Open questions

None.
