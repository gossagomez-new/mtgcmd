# ADR-0016: Read-only change preparation and contextual handoff

- Status: Accepted
- Date: 2026-10-16
- Related specification: [Change handoff](../../spec/change-handoff.md)

## Context

Repeated project handoffs require scoped staging, branch/PR checks, accurate
verification summaries, and reviewable PR descriptions. Existing exact evidence
fingerprints change on staging/commit. Squash merges make local ancestry
insufficient to determine whether a topic branch was already completed.

## Decision

Use an offline, read-only preparation CLI for deterministic local scope/title/
reference/evidence checks and PR draft generation. Use a separate repository
skill for live GitHub checks, contextual review, and session-authorized actions.
The script records pending remote/human checks and never stages or publishes.

Preserve the runner's exact evidence contract. Current evidence is labeled as
matching the pre-staging working tree. Rejected evidence supplies no copied
success claims. After a commit, preserve the original record and its historical
meaning; matching scope alone does not attest the resulting commit or exclude
the influence of unrelated worktree changes.

## Alternatives considered

### Automatic staging and publication in the script

- Benefits: Fewer commands for routine handoffs.
- Drawbacks: Mixes deterministic validation with scope judgment and authorization.
- Reason not selected: Reviewable preparation and explicit scope are recurring needs.

### Rebind runner evidence after staging/commit

- Benefits: Easier reuse of prior check records.
- Drawbacks: Changes the evidence contract and can overstate what source was tested.
- Reason not selected: Preserve original records and report their exact meaning.

### Keep all preparation manual in a skill

- Benefits: Minimal executable tooling.
- Drawbacks: Repeats error-prone scope/metadata/draft work on each handoff.
- Reason not selected: Stable local checks benefit from deterministic fixture tests.

## Rationale

Local checks and draft generation can be tested without network or mutations.
Live PR history and verification applicability require context. This split reuses
the runner and PR template while retaining repository authorization policy.

## Consequences

### Benefits

- Consistent scoped preparation and accurate verification descriptions.
- GitHub checks remain explicit and squash-merge reuse is reviewed live.

### Drawbacks and risks

- A locally passing preparation is not permission or readiness to publish.
- Evidence can become stale after preparation; the skill must preserve its label.
- Excluded functional changes require judgment or isolated verification.

### Operational impact

No application, deployment, dependency, or runner-schema change. Output artifacts
are private local files outside the checkout; sharing them requires review.

## Follow-up actions

- [x] Implement and verify the script, skill, fixtures, and walkthroughs.

## References

- [Local verification evidence](0015-local-verification-evidence.md)
- [GitHub Flow](0012-github-flow.md)
