# Java source conventions

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: Make Java source consistent while preserving behavior and useful advisory analysis.
- Depends on: [Advisory Java static analysis](java-static-analysis.md)

## Summary

Apply the agreed Java conventions to production and test sources and align
advisory PMD documentation rules with selective, useful documentation.

## Goals

- Use final parameters and local bindings when they are not reassigned.
- Use explicit types rather than var; review any necessary exception with the user.
- Use clear variable names, braces, and literal-first String comparisons.
- Replace flagged repeated literals with meaningful private constants, keeping
  mutable fixtures isolated and protocol values unchanged.
- Preserve useful documentation, wrap long comments, and remove indiscriminate
  comment requirements from PMD.

## Non-goals

No gameplay, transport, persistence, module-boundary, or frontend changes. No
blanket rule suppression or unrelated PMD remediation.

## Background

The pre-design audit found that GameZones.isPrivate feeds access decisions;
null-tolerant comparison must not classify invalid zones as public. Some values
are non-null by construction, including fixed board names and admitted command
types. PMD reports convention preferences rather than confirmed defects.

## Requirements

1. Eliminate active MethodArgumentCouldBeFinal, LocalVariableCouldBeFinal,
   UseExplicitTypes, ShortVariable, ControlStatementBraces,
   AvoidDuplicateLiterals, LiteralsFirstInComparisons, and CommentSize findings.
2. Exclude CommentRequired and CommentDefaultAccessModifier. Keep other rules,
   nonblocking build behavior, native reports, and diagnostic logging intact.
   Align CommentSize with the formatter's 100-column width and exclude annotation
   tokens from duplicate-literal extraction. Document retained short persisted
   names in a Game-model-local ShortVariable suppression.
3. Preserve non-null invariants explicitly where reversing comparisons would
   otherwise swallow invalid state. Keep evaluation order and side effects.
4. Preserve APIs, JSON/SQL names, record components, fixtures, validation order,
   exceptions and cause chains, and mutable state semantics.

## Contracts and data

No wire or snapshot schema changes. Literal extraction must retain exact values.
Final restricts reference reassignment, not mutation of domain objects.

## Technical constraints

Use compiler-resolved names/types for mechanical transformations, then manually
review exceptions. Do not introduce a new build dependency or permanent rewrite
tool. Keep constants local to the class whose meaning they express.

## Invariants

Privacy must fail closed for invalid zones. Null-invalid state must still throw;
valid requests, gameplay transitions, and existing assertions remain unchanged.

## Edge cases and failures

Preserve null comparison behavior, short-circuit conditions, deferred assignments,
anonymous-class types, generic inference, test parameter sources and resource
ownership annotations. Do not make reassigned variables final.

## Migration and compatibility

No migration or deployment needed. Source-only changes are reversible.

## Acceptance criteria

- [x] Targeted convention findings are absent, with no PMD analysis errors.
- [x] No var declarations remain and no public data names change.
- [x] Null characterization and existing backend/browser suites pass.

## Verification

Confirmed target: comprehensive coverage, selected by the user. Add null-invariant
characterization before changing production comparisons; existing behavior must
pass before and after the rewrite. This is behavior-preserving work, so no
artificial failing behavior test is required. Run complete backend verification,
Spotless, regenerated PMD, browser flows, whitespace and documentation checks.
Inspect source diffs and any introduced structural findings.

### Executed verification

- The initial 13 null characterization cases passed before production edits. All
  17 expanded cases also passed against separately compiled original HEAD sources
  and the final implementation; no artificial red test was used for style-only work.
- Final `spotless:apply spotless:check verify` passed all 571 backend tests with
  zero failures, errors, or skips, and packaged the executable jar.
- All five browser flows passed, covering private cards, URL fallback, library
  search, battlefield grouping, both formats, and durable reconnect.
- All three PMD build-script tests passed with the writable Maven cache. The
  initial sandboxed script run could not write the default cache; it was rerun
  successfully with the configured `/tmp/mtgcmd-m2` cache.
- Regenerated PMD has 777 active findings (298 production, 479 tests), down from
  3402, and no analysis errors. All targeted convention rules, including
  CommentSize, are clear; blanket comment rules are intentionally excluded.
  Annotation tokens and compatible short persisted names retain the documented
  exceptions. No var declarations remain.
- Explicit types expose one CouplingBetweenObjects advisory in JsonHttpTest
  (24 types, threshold 20). These dependencies already existed through inferred
  types; this cleanup does not add collaborators. No test-class decomposition or
  extra suppression was introduced solely to lower that metric.
- A reflection comparison against compiled original sources confirmed unchanged
  public fields and record components across all 76 production classes. Existing
  route tests and explicit PathVariable bindings preserve endpoint names.
- Source review, Markdown formatting, local documentation links, and
  `git diff --check` passed. Temporary compiler rewrite and audit utilities stayed
  outside the repository and introduce no build dependency.

## Architecture decisions

[ADR-0008](../docs/adr/0008-java-source-conventions.md) records the user-approved
source conventions and selective documentation policy.

## Open questions

None.
