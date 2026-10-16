# ADR-0014: Specification validation

- Status: Accepted
- Date: 2026-10-16
- Related specification: [Specification validation](../../spec/specification-validation.md)

## Context

Specifications, their index, dependency graphs, and ADR references are edited on
most refactors. Manual cross-checks are repetitive and adjacent additions can
conflict. Existing Markdown documents and Python tooling supply a stable local
validation surface, but validation cannot establish approval or test execution.

## Decision

Use a read-only, offline Python standard-library validator in the existing
verify job. Specification metadata remains authoritative; index rows and graph
edges must agree. Validate local links and coverage-record presence without
inferring decisions, rewriting documents, or probing external services. Support
a documented Markdown/Mermaid subset and fail explicitly on unsupported graph
syntax. Preserve existing multiple-table and prose formats.

## Alternatives considered

### Advisory validation only

- Benefits: No new blocking check for documentation.
- Drawbacks: Known dependency/link drift can merge unnoticed.
- Reason not selected: Consistency is deterministic and inexpensive to check.

### General Markdown/Mermaid parsing dependencies

- Benefits: Broader syntax support and less custom parsing.
- Drawbacks: More dependencies and tooling integration for a narrow format.
- Reason not selected: A bounded, tested syntax meets current repository needs.

## Rationale

A deterministic checker complements human architectural review and existing
policy. Running it in verify catches drift before merge while retaining the
stable required-check name. Fixtures make the supported syntax explicit.

## Consequences

### Benefits

- Fast local feedback and repeatable CI checks without network access.
- Shared dependencies and independent specs remain represented accurately.

### Drawbacks and risks

- Markdown syntax changes may require parser updates.
- Record presence cannot prove the truth or authorization of a claim.

### Operational impact

No application, deployment, or data migration. Contributors run the documented
command before PR handoff; CI additionally enforces it.

## Follow-up actions

- [x] Implement and verify the validator and CI step.

## References

- [AGENTS.md](../../AGENTS.md)
- [GitHub Flow](0012-github-flow.md)
