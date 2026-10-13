---
name: codebase-design-audit
description: Audit a codebase for evidence-backed maintainability problems, code smells, unnecessary complexity, weak boundaries, and domain-modeling issues using Clean Code, Domain-Driven Design, and Ousterhout-inspired design principles. Use for requested code-quality or design reviews and pre-specification audits of affected existing code; do not activate for ordinary implementation work.
---

# Codebase Design Audit

Review the requested codebase or scope without modifying it unless the user
explicitly asks for fixes. Produce a prioritized audit grounded in observable
code and expected change costs, not a catalogue of stylistic preferences.

Before reviewing, read [references/review-framework.md](references/review-framework.md).

## Scope

Determine the review boundary from the request and repository context. State
important exclusions and assumptions. If the requested scope is large, inspect
architecture and dependency boundaries first, then prioritize representative
or high-risk paths rather than implying exhaustive coverage.

Respect generated code, third-party code, migrations, framework conventions,
and intentionally transitional code. Do not judge them as ordinary application
code without accounting for their constraints.

## Audit workflow

1. Discover repository instructions, structure, languages, frameworks, test
   strategy, and architectural conventions.
2. Trace important behavior and dependencies across boundaries before judging
   individual methods or classes.
3. Look for concrete maintenance, correctness, coupling, testability, domain,
   and complexity costs using the review framework.
4. Test each candidate finding against local context and plausible
   alternatives. Reject findings supported only by preference or dogma.
5. Report a small, prioritized set of well-supported findings. Include exact
   file and line references where possible.
6. Separate confirmed findings from lower-confidence risks or questions.

Use read-only inspection and verification appropriate to the codebase. Do not
create commits, refactor code, install dependencies, or change external state
as part of an audit unless separately authorized.

## Output

Lead with the most consequential findings, ordered by priority rating and then
severity. For every finding, use the schema in the review framework and show
the rating calculation.

After the findings, include:

- Review scope and exclusions.
- Cross-cutting themes, only when supported by multiple findings.
- Positive design characteristics worth preserving.
- Suggested remediation order and dependencies between fixes.
- Verification performed and limitations of the audit.

If no meaningful issues are found, say so directly and describe the inspected
scope and remaining uncertainty. Do not manufacture findings to fill a report.
