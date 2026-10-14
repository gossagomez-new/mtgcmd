---
name: refactor-design-explanation
description: Explain a proposed refactor of a specific method, class, or PMD hotspot with code-grounded reasoning, relevant rules, pattern trade-offs, before/after examples, and verification needs. Use for design explanations before implementation, rather than general report summaries or automatic code changes.
---

# Refactor design explanation

Produce an explanation grounded in the current repository for the requested target.
The output is a proposed design; an explanation request does not authorize source
changes, report regeneration, or creation of specifications/ADRs.

## Establish evidence

Read the target implementation, relevant callers, tests, and existing specifications
or ADRs. Trace observable behavior before proposing extraction. Use the
[audit review framework](../codebase-design-audit/references/review-framework.md)
when evaluating design issues; avoid repeating the full audit schema for every
helper unless the user requests an audit.

For PMD-driven work, use the existing
[PMD analysis skill](../pmd-report-analysis/SKILL.md) and repository parser for report
provenance and counts. Inspect the target's actual XML/text findings for method
metrics and locations; the summary is aggregate evidence. Report missing or stale
metrics as uncertainty rather than inventing them. When no PMD report applies,
explain maintainability evidence without fabricating rule violations.

Identify required compatibility details: validation and failure precedence,
return/warning order, null versus omitted values, mutation order, privacy, exception
identity/cause chains, resource ownership, locking, cancellation, and interruption
where relevant. Separate behavior-preserving extraction from any proposed policy
change. Do not silently expand the requested scope.

## Develop the explanation

Lead with the recommended boundary and why it reduces concrete review or change
cost. Scale the detail to the target; do not force every explanation into a large
report. Develop these points where relevant:

- Current responsibilities and the evidence that they should be separated.
- Proposed coordinator/helpers or components, what each owns, and how they compose.
- Specific PMD rules addressed, observed metrics when available, and how each
  change affects the finding. Flag warnings requiring contextual investigation.
- Patterns used, their practical benefit, and simpler alternatives. Extract Method,
  guard clauses, and named predicates often suffice; do not introduce Chain of
  Responsibility, Strategy, enums, or a class per rule just to attach a pattern name.
- Before/after examples from the actual code, showing the change and its purpose.
- Trade-offs, behavior constraints, verification cases, and unresolved decisions.

Label after-examples as proposed, not implemented or verified. Use realistic Java
signatures and existing types/messages for this project. If an excerpt omits setup
or helpers, say so. Keep matching rules and lifecycle behavior unchanged unless a
change is explicitly proposed; avoid subtle changes hidden in illustrative code.

Preserve the original exception instance when that is the contract, retain causes
when translating, and establish lifecycle ownership before recommending closure.
Do not narrow synchronization or change cancellation as incidental cleanup.
Validate decisions against callers and tests, not merely a smaller method metric.

Do not claim extraction removes PMD findings before rerunning analysis: complexity
can move into helpers, and class-level findings can increase. Intentional unwrapping
or framework-owned resources may warrant retaining behavior rather than rewriting
it to satisfy a warning. Explain any suppression as a separate, narrowly scoped
option requiring justification.

## Verification and handoff

Describe observable success, edge, and failure cases proportional to the proposal.
For behavior-preserving refactors, recommend a green characterization baseline
before extraction. For approved behavior changes, recommend tests demonstrating
the intended missing/incorrect behavior before production changes. Discover test
commands from repository configuration and distinguish proposed checks from checks
actually executed.

Link current source locations and relevant existing decisions. Identify when a
proposed boundary would require an ADR under repository policy; private helper
extraction alone does not establish a new architectural decision. If evidence
supports keeping the current design, explain that rather than manufacturing a
refactor. End with the expected outcome and material uncertainty, without implying
implementation or test execution has already occurred.
