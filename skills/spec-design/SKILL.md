---
name: spec-design
description: Design or materially update implementation-ready project specifications by discovering repository context, resolving product and technical decisions, coordinating pre-design audits, and defining verifiable acceptance criteria. Use for non-trivial features, refactors, endpoints, data models, and schema changes; do not activate for small implementation tasks that do not require a specification.
---

# Specification Design

Create a decision-complete specification that another engineer or agent can
implement without making unstated product or architectural decisions.

Follow the repository policy in [../../AGENTS.md](../../AGENTS.md) and use
[../../spec/TEMPLATE.md](../../spec/TEMPLATE.md) as the output structure. Do
not duplicate policy or architectural rationale in the specification.

## Workflow

1. Inspect repository instructions, existing specifications and ADRs, affected
   code, tests, contracts, and project configuration before asking questions.
2. Establish the goal, users, success criteria, scope, constraints, current
   behavior, and important preferences. Ask only questions whose answers cannot
   be discovered and that materially affect the specification.
3. When existing code is affected, use the `codebase-design-audit` skill on the
   relevant scope. Present only findings that could materially influence the
   proposed change, distinguishing prerequisites from optional improvements.
4. Obtain explicit confirmation before adding remediation outside the approved
   scope. Continue work on unaffected portions when possible.
5. Resolve the implementation contract: interfaces, data flow, validation,
   state transitions, failure modes, compatibility, migration, rollback, and
   verification relevant to the change. Avoid prescribing incidental details.
6. Evaluate whether the work requires an ADR under repository policy. If so,
   present the decision, alternatives, trade-offs, recommendation, title, and
   location, then wait for confirmation before creating or finalizing the ADR.
7. Complete the specification and check it for implementation-blocking
   ambiguity.

## Completion checks

Before presenting the specification as complete, verify that:

- Metadata states status, delivery priority and rationale, and authoritative
  dependencies.
- Goals, non-goals, requirements, constraints, and invariants agree with one
  another.
- Public contracts, data behavior, side effects, failures, and compatibility
  needs are explicit where relevant.
- Acceptance criteria are observable and the verification plan covers them.
- Confirmed audit findings are incorporated and optional findings are not
  silently added to scope.
- Architectural rationale links to an ADR rather than being duplicated.
- Open questions identify genuine unresolved decisions; if any blocks safe
  implementation, the specification remains a draft.
- Template placeholders and instructions have been removed.

Do not implement the specification, modify production code, or create commits
unless the user separately authorizes that work.
