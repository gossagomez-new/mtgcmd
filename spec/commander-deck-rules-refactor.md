# Commander deck rules refactor

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: Reduce localized validation complexity while preserving deck observations.
- Depends on: [Standard tables](standard-tables.md)

## Summary

Keep DeckRules as the validation owner. Extract commander selection/resolution,
deck-size warnings, commander warnings, combined identity, and per-card warnings
into private helpers. Use a named repeated-copy predicate.

## Goals

- Remove commander cognitive, cyclomatic, and NPath complexity findings.
- Expose the existing distinction between fatal selection errors and advisory warnings.
- Preserve observable validation behavior and warning order.

## Non-goals

- New rule frameworks, classes, enums, or dependencies.
- Rules-accuracy changes, case-insensitive matching, or Standard rule changes.
- Unrelated PMD cleanup.

## Background

The pre-design audit confirmed commander combines selection checks, commander
observations, and card observations. PMD reports cognitive complexity 16,
cyclomatic complexity 13, and NPath 1,632. This localized maintainability finding
has priority 65 (medium severity 30 + high confidence 15 + small effort 20).
Private extraction introduces a few helpers but avoids a framework. No outside
prerequisite was found. Existing PMD automation work remains intact.

## Requirements

### Functional requirements

1. Retain validate's format dispatch, deck bounds, and existing exception messages.
2. Validate commander count (one/two), distinct IDs, then membership in that order.
   Resolve selections in input order using the first matching deck definition.
3. Append deck-size warning, commander eligibility warnings in selection order,
   pairing warning, then per-card warnings in deck order.
4. Combine selected commanders' colors and retain current eligibility substring
   checks. Retain legal, identity, Basic, and any-number substring semantics and case.
5. Track every seen card ID, then warn for duplicate non-exempt cards. Preserve
   legality/identity/singleton warning order per card and stable final deduplication.
6. Do not mutate decks, commander selections, definitions, or Standard behavior.

### Non-functional requirements

- Preserve advisory warnings versus fatal errors.
- Avoid relocating complexity into another oversized helper.

## Contracts and data

No API, schema, model, or persistence changes. The existing package-private
validate method returns the same ordered distinct warnings or throws the same
validation exception. Commanders remain IDs and definitions retain first-match
lookup semantics.

## Technical constraints

Use private cohesive helpers within DeckRules, named predicates, and the existing
format profiles. No new public interface or module boundary.

## Invariants

- Invalid deck size precedes commander-selection failures.
- Invalid selection count precedes duplicate IDs, which precede missing IDs.
- Color identity uses the first matching definition for each selected ID.
- Only final duplicate warning strings are removed; order remains stable.

## Edge cases and failures

Null/empty/excessive/duplicate/missing selections; repeated IDs; overlapping
warnings; one/two commanders; deck boundaries; Basic and any-number exceptions;
case-sensitive matching; Standard validation compatibility.

## Migration and compatibility

No migration or caller changes. Refactor only implementation structure.

## Acceptance criteria

- [x] Characterization cases pass before and after extraction.
- [x] Coordinator and helpers have the approved responsibilities.
- [x] DeckRules has no cognitive, cyclomatic, or NPath complexity findings.
- [x] Focused gameplay/deck tests, formatting, backend verification, and packaging pass.

## Verification

Confirmed target: comprehensive coverage. Add direct DeckRules characterization
cases for selection validation/precedence, overlapping ordered/deduplicated
warnings, combined identity, first-match definitions, copy exceptions and
case-sensitive matching, boundaries, nonmutation, and Standard compatibility.
Establish a green baseline before extracting production code because no behavior
change is intended. Run new tests and existing gameplay/Standard tests after
extraction; run Spotless and full backend verify, inspect fresh PMD. No new
browser flow is required for private deck-validation extraction.

### Executed verification

- All 30 new DeckRules characterization cases and 28 existing gameplay/Standard
  cases passed before and after extraction. This is a behavior-preserving refactor;
  the baseline was green rather than an artificial failing behavior test.
- Full `spotless:apply spotless:check verify` passed: 460 backend tests, zero
  failures/errors/skips, successful formatting and packaging.
- Fresh PMD report has no CognitiveComplexity, CyclomaticComplexity, or
  NPathComplexity findings in DeckRules; no processing/configuration errors.
- Markdown formatting and git whitespace checks passed. Browser tests were not
  rerun because the change only extracts private deck-validation helpers.

## Architecture decisions

[ADR-0002](../docs/adr/0002-room-format-profiles.md) retains server-owned format
rules. No new ADR: private helper extraction does not change a module boundary
or public contract.

## Open questions

None.
