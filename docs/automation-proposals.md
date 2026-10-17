# Automation proposal analysis

- Updated: 2026-10-16 (original analysis: 2026-10-13).
- Status: Planning backlog and implementation tracking; proposals alone do not authorize implementation.
- Context: Repeated analysis, refactoring, verification, and GitHub Flow handoffs
  across the backend and frontend, plus the Docker/Oracle Cloud preparation.

## Purpose

Reduce repeated coordination and prevent avoidable mistakes by automating stable
execution and validation. Use scripts for deterministic work, shared test helpers
for test setup, and skills for workflows requiring contextual judgment.

Priorities now reflect actual repeated work: reviewing frontend findings,
characterizing component behavior, keeping specifications/indexes consistent,
selecting local checks, and preparing commits and PRs.

## What already exists

Reuse these implementations rather than proposing parallel replacements.

| Capability                                       | Evidence                                                                                                                         | Remaining boundary                                                                                                                                                                                              |
| ------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Frontend-first analyzed packaging                | [Build script](../scripts/build.sh), [build specification](../spec/build-automation.md)                                          | Does not run every frontend unit, formatting, lint, or browser check; `--skip-tests` explicitly skips Java tests.                                                                                               |
| Full CI verification sequence                    | [GitHub Actions workflow](../.github/workflows/verify.yml), [contribution guide](../CONTRIBUTING.md)                             | Provides the canonical hosted sequence; local profiles now reuse its commands with source-bound evidence and explicit scope. Workflow configuration does not establish that remote branch protection is active. |
| Java report generation and contextual review     | [PMD parser](../scripts/analyze-pmd.py), [PMD analysis skill](../skills/pmd-report-analysis/SKILL.md)                            | Already handles report summaries and baseline comparisons. Do not create another Java report-review skill.                                                                                                      |
| Frontend ESLint/Knip generation and reporting    | [Analyzer](../frontend/scripts/analyze.mjs), [static-analysis specification](../spec/frontend-static-analysis.md)                | Native reports, scope/rule/file summaries, baseline deltas, and provenance checks are implemented; contextual recommendations still require source inspection.                                                  |
| Evidence-backed audits and refactor explanations | [Design audit](../skills/codebase-design-audit/SKILL.md), [refactor explanation](../skills/refactor-design-explanation/SKILL.md) | Already supplies contextual review, patterns, examples, and compatibility analysis. New workflows should invoke these skills.                                                                                   |
| Specification design and repository policy       | [Specification skill](../skills/spec-design/SKILL.md), [AGENTS.md](../AGENTS.md), [specification index](../spec/INDEX.md)        | Metadata, local links, ADR sets, and index/graph consistency are checked by `scripts/validate-specs.py`; approval and execution evidence still require human review.                                            |
| Browser and database test-server lifecycle       | [Playwright configuration](../frontend/playwright.config.ts), existing test-server fixtures                                      | Server startup and shared mocked-table/request/selector helpers are implemented; scenario assertions and real-server flows remain explicit.                                                                     |
| Deployment configuration validation              | [Deployment checker](../scripts/check_deployment.py), [deployment guide](deployment-oracle.md)                                   | Checks resolved production settings; it is not a general local-environment, daemon-permission, Podman-context, or OCI reachability checker.                                                                     |

The App, Inspector, and DeckImport decompositions, frontend analysis review
([PR #5](https://github.com/gossagomez-new/mtgcmd/pull/5)), and shared browser
fixtures ([PR #6](https://github.com/gossagomez-new/mtgcmd/pull/6)) are on `main`
at this task's base check. Specification validation is integrated through [PR #7](https://github.com/gossagomez-new/mtgcmd/pull/7).
Local verification profiles/evidence are published in [PR #8](https://github.com/gossagomez-new/mtgcmd/pull/8);
its integration status must be checked when preparing the handoff PR. Commit/PR
preparation is implemented and locally verified on its own branch.
Publication and integration remain separate steps;
implementation status does not imply deployment.

## Priority and estimation method

- **P1 — next:** Frequent work with demonstrated coordination or correctness cost.
- **P2 — follow-up:** Useful support once the core interfaces are stable.
- **P3 — deferred:** Lower recent demand or narrower benefit.

Ordering considers repetition, potential errors prevented, reuse of existing
code, and implementation/maintenance cost. Warning counts alone do not determine
priority. Priorities are recommendations, not execution authorization.

Effort assumes one engineer familiar with the repository and includes relevant
fixture tests, documentation, and integration. Estimates are planning ranges,
not delivery commitments. Rows 1–4 retain their original planning estimates for comparison. Proposal 6
was revised to 4–6 hours for evidence-phase handling and is now implemented;
proposals 5 and 7–9 remain incremental unimplemented work. Shared infrastructure should be implemented
once.

## Prioritized backlog

The table retains original proposal numbers for traceability. Proposals 1–4 and
6 are implemented as tracked below; the remaining delivery order is described
in the sequence section. Estimates are planning ranges, not measured effort.

| Order | Priority | Proposal                                          | Proposed artifact or extension                                                                                  | Effort     | Why now                                                                                                                                                                                                 |
| ----- | -------- | ------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- | ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1     | P1       | Frontend analysis review and baseline comparison  | Extend `frontend/scripts/analyze.mjs`; add `skills/frontend-analysis-review/SKILL.md`                           | 4–6 hours  | Repeated “check the frontend analysis again” requests involve the same counting, comparison, source inspection, and next-target recommendation.                                                         |
| 2     | P1       | Shared browser characterization fixtures          | Add `frontend/tests/support/tableFixture.ts` and `frontend/tests/support/selectors.ts`; migrate repeated setup  | 5–8 hours  | App, Inspector, protocol, and DeckImport tests repeat session/room routes, WebSocket updates, request capture, deferred replies, and selectors. New tests encountered avoidable label-locator failures. |
| 3     | P1       | Specification, ADR-link, and index validation     | Add `scripts/validate-specs.py` and fixture tests; integrate into `verify`                                      | 8–12 hours | The index and dependency graph are manually updated for each component refactor. Existing metadata and links provide a stable validation contract.                                                      |
| 4     | P1       | Local verification profiles and evidence manifest | Add `scripts/verify.sh` with a shared command/profile definition and wrapper tests; reuse CI/build commands     | 6–9 hours  | Each refactor repeats check selection, background-process coordination, log inspection, and accurate reporting of passed/skipped checks.                                                                |
| 5     | P2       | Development environment doctor                    | Add `scripts/doctor.sh`; delegate production configuration checks to the existing checker                       | 3–5 hours  | Java/Node versions, browser paths, local ports, non-root PostgreSQL execution, and container contexts have caused setup friction.                                                                       |
| 6     | P1       | Commit and PR preparation workflow                | Add `skills/change-handoff/SKILL.md`; add `scripts/prepare-pr.py` for deterministic draft/check output          | 4–6 hours  | Several completed changes needed the same scoped staging, Conventional Commit, push, PR-template, and verification-summary handoff.                                                                     |
| 7     | P2       | Full-stack verification guidance                  | Add `skills/full-stack-verification/SKILL.md`; reuse the verification runner and existing audit/refactor skills | 2–3 hours  | Agent judgment is still needed to choose a profile and explain risk-specific checks; it should not maintain a second command sequence.                                                                  |
| 8     | P2       | Focused specification-skill extension             | Extend `skills/spec-design/SKILL.md`                                                                            | 1–2 hours  | Explicitly connect coverage confirmation, index validation, and recorded verification to the existing workflow without duplicating repository policy.                                                   |
| 9     | P3       | Optional external import diagnostics              | Add `scripts/check-imports.sh` or a small equivalent CLI                                                        | 2–4 hours  | Useful for renewed provider incidents, but recent work has focused on frontend structure. Existing fallback tests already cover routine UI recovery.                                                    |

## Implementation tracking

- **1. Frontend analysis review:** implemented and integrated through
  [PR #5](https://github.com/gossagomez-new/mtgcmd/pull/5).
- **2. Shared browser fixtures:** implemented and integrated through
  [PR #6](https://github.com/gossagomez-new/mtgcmd/pull/6); see
  [the specification](../spec/shared-browser-fixtures.md).
- **3. Specification, ADR-link, and index validation:** implemented and locally
  verified by [the validation specification](../spec/specification-validation.md).
  The read-only Python CLI validates metadata, links, ADR references, coverage
  records, index rows, and graph agreement. CI runs it alongside fixture tests;
  integration is complete through PR #7.
- **4. Local verification profiles and evidence:** implemented and locally verified
  by [the local-verification specification](../spec/local-verification.md).
  [The runner](../scripts/verify.sh) executes explicit frontend/backend/full
  profiles sequentially, preserves failed exits, and writes source-bound manifests
  with read-only evidence checks. Publication/integration remain separate steps.
- **6. Commit and PR preparation:** implemented and locally verified by
  [the handoff specification](../spec/change-handoff.md). The offline preparation
  CLI emits scoped JSON/Markdown without Git/network mutations; the skill handles
  live PR history, contextual review, and authorized actions. Original pre-commit
  evidence is preserved rather than rebound. Publication/integration remain separate.
- **Next unimplemented proposal:** focused specification-skill extension (row 8,
  1–2 hours), followed by verification guidance (row 7, 2–3 hours).

The scope descriptions below retain the proposal rationale; implementation
specifications and their executed verification records define delivered behavior.

## Scope and acceptance for the proposals

### 1. Frontend analysis review and comparison

Extend the existing analyzer's reporting, not the ESLint/Knip execution pipeline.
Produce production/test/other counts, counts by rule/file, and hotspot metrics
when the native report provides them. Compare an explicitly saved baseline using
repository-relative paths, distinguishing resolved, new, and unchanged findings.
Document limitations when findings move between files or line numbers change;
absence of a complexity warning does not supply an exact complexity score.

The skill should accept the report location, optional baseline, and review scope.
It should check report status and provenance, inspect the implicated source and
tests, reuse the audit/refactor-explanation skills, and return a reasoned next
recommendation. Reuse the PMD review procedure where appropriate without
pretending the Java XML parser accepts ESLint JSON.

Save baselines separately before regeneration: the existing analyzer clears its
owned reports. Do not silently advance baselines, suppress warnings, change
thresholds, or convert advisory findings into blocking checks. Test malformed,
empty, failed, renamed-path, moved-finding, and duplicate-finding reports.

### 2. Shared browser characterization fixtures

Provide typed helpers for guest/session bootstrap, initial room snapshots,
WebSocket snapshot/ack delivery, captured command bodies, and deferred HTTP
responses. Include an observable wait for snapshot acceptance; sending a frame
alone is not proof that React has applied it. Let scenarios explicitly choose
snapshot versus acknowledgment messages so pending-command behavior stays visible.

Use stable accessible-role/label selectors that work before and after textarea
values or select options change. Do not hide expected command payloads, permission
assertions, form-state lifetimes, or dialog behavior inside an opaque test DSL.
Keep real-server privacy/reconnect flows distinct from mocked transport scenarios.
Migrate a representative suite first, then the remaining duplicates while keeping
existing assertions intact. Test setup failures and release outstanding deferred
responses during cleanup.

### 3. Specification and index validation

Validate required metadata, allowed status/priority values, local links, explicit
placeholders, authoritative `Depends on` edges, dependency cycles, and agreement
with the index table and graph. Check related ADR links without inferring new
architectural dependencies. Handle the current index's multiple Markdown tables;
report intentional external links as outside local validation rather than probing
the network by default.

Verify that the verification section records a coverage choice where required.
A validator can check that the record exists; it cannot establish that a user
actually approved it or that claimed tests ran. Do not regenerate dependency
metadata or mark work completed automatically. Report paths and actionable errors,
and cover shared dependencies, independent specifications, cycles, missing files,
and incomplete drafts in fixture tests.

### 4. Verification profiles and evidence

Offer explicit frontend, backend, and full profiles, with browser checks selectable
when relevant. Derive their commands from the existing configuration and preserve
the stable CI `verify` job. The full profile should reuse `scripts/build.sh` and
coordinate required checks without maintaining another packaging sequence. Avoid
running browser servers concurrently with tasks that clean the same backend build
outputs. Avoid duplicate compilation/checks when the selected build already covers
them; document intentional duplication.

Record the source commit, working-tree state/diff identity, commands, relevant
non-secret tool/environment settings, timestamps, exit codes, log paths, and
passed/failed/skipped results in a machine-readable manifest. Stop dependent work
on failure and preserve the failing exit status. Include a deliberate-failure
fixture so trailing successful commands cannot mask a failed check. Evidence for
an older source state must not be presented as current verification; retain any
limits on reuse rather than asserting that a manifest proves correctness.

### 5. Environment doctor

Check Java 25, Node 24, Maven, browser availability/`CHROME_PATH`, local test ports,
and the non-root requirement for embedded PostgreSQL. For optional deployment
checks, distinguish Docker from Podman's Docker-compatible CLI, daemon/socket
access, rootful/rootless image stores, and privileged-port restrictions. Reuse
`scripts/check_deployment.py` for configuration checks rather than copying it.

Produce actionable diagnostics and distinct optional-check statuses. Do not
install tools, change group membership, alter firewall/sysctl settings, print
credentials, or claim external OCI reachability from a local port check. Keep
network probes explicitly requested and separate from offline checks.

### 6. Commit and PR preparation

Accept the intended branch, scoped paths, commit/PR title, specification links,
and verification evidence. Check merged-branch reuse, the PR base/diff, unrelated
working-tree files, and whether recorded checks match the proposed source state.
Generate a reviewable PR body using the existing template and identify what is
ready versus still pending.

Keep deterministic validation/draft generation in the script and contextual
review in the skill. Reuse the verification manifest and contribution guide.
Never stage everything by default: unrelated feedback files and generated caches
have repeatedly remained in the working tree during authorized commits. Commit,
push, and PR creation require authorization covering those actions; an explicit
request for all three should not trigger redundant confirmation questions. Merge,
release, branch deletion, and deployment remain separate actions.

### 7–8. Extend guidance after the executable interfaces stabilize

The verification skill should select a profile from change scope and the confirmed
coverage target, explain additional risk-specific checks, and report actual
results and limitations. It should invoke the runner rather than repeat its
command implementation.

The specification skill extension should reference AGENTS.md for policy, make
coverage/index checks explicit, and call the validator when available. It should
not duplicate the existing design-audit or refactor-explanation skills. These are
small follow-up improvements once the validator and evidence format are stable.

### 9. External import diagnostics

Use bounded, optional requests to distinguish provider access/availability issues
from local configuration or parsing failures, with export/paste guidance. Do not
make live provider availability a prerequisite for normal builds or deterministic
tests. Prioritize this only when provider troubleshooting recurs.

## Changes from the original proposals

| Original proposal                        | Updated status and treatment                                                                                                                           |
| ---------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Build-order script, 1–2 hours            | Implemented by `scripts/build.sh`; remove from remaining effort.                                                                                       |
| Unified verification script, 4–6 hours   | Partially covered by CI and the build wrapper; revise to 6–9 hours for local profiles, evidence, and integration rather than another packaging script. |
| Environment-check script, 3–5 hours      | Partially covered for deployment configuration; retain 3–5 hours for the development/permission/context gaps.                                          |
| Import diagnostics, 2–4 hours            | Still proposed; defer to P3 because recent demand is lower.                                                                                            |
| Specification validation, 6–10 hours     | Promote to P1 and revise to 8–12 hours for the larger index, graph, ADR-link, and verification-record checks.                                          |
| Full-stack verification skill, 3–5 hours | Still proposed; revise to 2–3 hours assuming the runner and existing review skills are reused.                                                         |
| Specification-skill extension, 1–2 hours | Partially covered by current guidance; retain a narrow 1–2-hour integration/consistency follow-up.                                                     |

Frontend analysis review, shared browser fixtures, and change handoff are new
proposals grounded in the subsequent repeated workflows. Java analysis, design
audits, refactor explanations, CI, and deployment validation are existing
capabilities, not new work estimates.

## Recommended delivery sequence and budget

1. Frontend analysis review, shared browser fixtures, specification validation,
   local verification profiles/evidence, and handoff preparation are implemented
   (proposals 1–4 and 6). Their combined **27–41-hour planning estimate** includes
   the revised handoff estimate; it is not measured effort or remaining work.
   Integrate published work through review when authorized.
2. Extend the existing **specification skill (1–2 hours)** next to call the validator
   and make coverage/evidence integration explicit without duplicating policy.
3. Add **verification guidance (2–3 hours)** using the stable runner. It should
   choose profiles and risk-specific checks rather than own another command list.
4. Add the **doctor (3–5 hours)** when onboarding or environment friction warrants
   offline preflight. Reuse the deployment validator. Keep **import diagnostics
   (2–4 hours)** deferred until provider troubleshooting recurs.

No unimplemented P1 proposal remains. The complete unimplemented backlog is
**8–14 hours** (P2: **6–10 hours**, P3: **2–4 hours**). The original full
nine-proposal estimate was 34–54 hours; increasing handoff from 3–5 to 4–6 hours
makes the comparable revised total 35–55 hours. These are planning ranges, not
measured effort. Revised remaining totals exclude proposals 1–4 and 6; review/
integration time is not re-estimated here. Confirm each next specification and
coverage target before implementation.

## Boundaries and authorization

This document records proposals and separately authorized implementation status.
Recording a proposal does not create or modify a skill, implement scripts or test
helpers, change CI/analysis policies, or authorize commits. New implementations
require their own scope, specification, coverage choice, and ADR evaluation under
[AGENTS.md](../AGENTS.md). Skill creation or modification requires separate
confirmation.

Approval records and architectural judgment remain human decisions. No tool
should infer approval from elapsed time, treat skipped checks as passed, claim
remote settings were configured from local files, or deploy implicitly.
