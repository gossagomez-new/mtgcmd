# Automation proposal analysis

- Date: 2026-10-13
- Status: Proposals recorded; implementation is not authorized by this document.
- Context: Repeated work during implementation of the
  [Commander tabletop](../spec/commander-tabletop.md).

## Purpose

Reduce repeated command coordination, catch environment problems earlier, and
make verification and documentation more consistent. Use scripts for repeatable
execution and skills for workflows that require an agent's judgment.

## Existing automation

Reuse the existing npm scripts, Java formatting configuration, Playwright server
lifecycle, embedded PostgreSQL fixtures, and packaged-jar smoke helper rather
than duplicating them. Canonical commands are documented in
[AGENTS.md](../AGENTS.md); operating instructions are in [README.md](../README.md).
The existing [specification-design skill](../skills/spec-design/SKILL.md) already
covers much of the design workflow.

## Proposals and effort estimates

Estimates assume one engineer familiar with this repository and include
implementation, relevant tests, and documentation. Proposed paths below are
future locations; the proposed artifacts have not been created.

| Proposal | Proposed location | Estimated effort | Work and expected benefit |
| --- | --- | --- | --- |
| Unified verification script | `scripts/verify.sh` | 4–6 hours | Coordinate relevant tests, lint, formatting, builds, and jar smoke checks. Stop on failure, support optional browser/live checks, and summarize passed, failed, and skipped checks accurately. Reduces manual command coordination. |
| Environment-check script | `scripts/doctor.sh` | 3–5 hours | Check Java, Maven, Node, browser availability, database configuration, and occupied ports. Explain actionable failures without exposing credentials. Catches setup problems before expensive checks. |
| Build-order script | `scripts/build.sh` | 1–2 hours | Build React before packaging Java, prevent stale frontend assets from entering the jar, and report the artifact location. Makes packaging reproducible. |
| Import-diagnostics script | `scripts/check-imports.sh` | 2–4 hours | Probe Scryfall and Moxfield with bounded requests. Distinguish provider availability and access failures, and report export/paste guidance. Keep execution optional because it requires network access. |
| Specification-validation script | `scripts/validate-specs.py` | 6–10 hours | Validate metadata, links, authoritative dependencies, dependency cycles, unresolved placeholders, and specification-index consistency. Produce actionable diagnostics. This has the largest estimate because Markdown structure and graph consistency require careful handling. |
| Full-stack verification skill | `skills/full-stack-verification/SKILL.md` | 3–5 hours | Guide agents in selecting checks, following test-first development, inspecting browser results, handling environment failures, and reporting evidence. Inputs include the change scope and confirmed verification target; outputs include check results and limitations. Reuse the scripts above and existing test helpers. |
| Extend specification-design skill | `skills/spec-design/SKILL.md` | 1–2 hours | Add explicit checks for confirmed testing coverage and dependency-index requirements, then validate the revised instructions. Extend the existing skill rather than creating a competing design workflow. |

**Combined estimate: 20–34 hours**, approximately **3–5 working days** allowing
for integration and unexpected issues. These are planning estimates, not
delivery commitments; skill scope or supported environments could change them.

## Recommended order

1. Implement verification, environment checks, and build order together:
   approximately **8–13 hours**. They address the most frequently repeated work.
2. Add the full-stack verification skill once the scripts' interfaces are
   stable, and extend the existing specification-design skill.
3. Add import diagnostics when external-provider troubleshooting recurs.
4. Add specification validation as the specification collection grows or its
   manual consistency checks become a recurring maintenance burden.

The verification script should call the build-order script rather than maintain
a second packaging sequence. The verification skill should invoke established
commands and scripts rather than embed another implementation of those checks.

## Implementation tracking

The build-order proposal was separately authorized for implementation. Its
behavior and confirmed risk-focused coverage are recorded in the
[build-automation specification](../spec/build-automation.md). Other proposals
remain unimplemented and are not authorized by this analysis.

## Boundaries and authorization

Architectural choices, scope expansion, and deployment approval remain human
decisions under [repository policy](../AGENTS.md). Scripts and skills must not
treat elapsed time or a missing reply as approval, deploy changes implicitly,
or report skipped checks as passed.

Recording this analysis does not create or modify a skill, implement a proposed
script, or authorize commits. Separate confirmation is required for skill
creation or modification under repository policy.
