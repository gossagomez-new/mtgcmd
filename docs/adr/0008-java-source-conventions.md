# ADR-0008: Java source conventions

- Status: Accepted
- Date: 2026-10-14
- Related specification: [Java source conventions](../../spec/java-source-conventions.md)

## Context

Broad advisory PMD analysis reports numerous style differences. The user selected
final bindings where possible, explicit types, meaningful names, braces, private
constants for repeated test literals, and literal-first comparisons. Blanket
comment requirements add noise alongside useful contract documentation.

## Decision

Follow these Java conventions in production and tests. Use final for bindings
that are not reassigned; domain objects remain mutable where required. Prefer
explicit types; discuss any proposed var exception with the user. Name local
variables and parameters clearly without renaming wire fields. Use braces and
literal-first String equality, preserving explicit non-null invariants in
privacy, authorization, state, and validation paths. Use meaningful private
static final constants for repeated immutable literals; never share mutable test
state merely to remove repetition.

Keep documentation for contracts, ownership, responsibilities, invariants, and
surprising behavior. Exclude PMD CommentRequired and
CommentDefaultAccessModifier, retain CommentSize and other existing analysis.
Match CommentSize's line limit to the formatter's 100-column width. Exclude
annotations from duplicate-literal extraction so compiler and analysis tools can
recognize their tokens. Retain short persisted field names with a documented
Game-model-local ShortVariable suppression instead of changing snapshot or wire
contracts. PMD remains advisory under ADR-0003.

## Alternatives considered

### Follow all broad PMD documentation rules

Uniform declaration comments, but many merely repeat names and visibility.
Rejected because noise and maintenance outweigh useful explanation.

### Disable convention analysis entirely

Less report noise, but no feedback on the conventions the user wants to follow.
Rejected in favor of selective rule alignment.

## Rationale

The selected conventions reflect the user's readability preferences while
preserving domain invariants and keeping documentation focused on meaning.

## Consequences

### Benefits

Consistent source and a more actionable advisory report.

### Drawbacks and risks

Large source-only diffs require careful review. Final does not imply immutable
objects. Literal-first equality must not mask invalid domain state.

### Operational impact

No deployment, dependency, schema, or runtime architecture changes.

## Follow-up actions

- Apply and verify the agreed conventions under the linked specification.

## References

- [ADR-0003: Advisory Java static analysis](0003-advisory-java-static-analysis.md)
