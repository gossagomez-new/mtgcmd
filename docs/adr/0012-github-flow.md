# ADR-0012: GitHub Flow with tagged manual deployments

- Status: Accepted
- Date: 2026-10-16
- Related specification: [GitHub Flow](../../spec/github-flow.md)

## Context

The project has a small active team, an Oracle VM deployment, and several topic
and experiment branches. A long-lived deployment branch has accumulated unrelated
completed work. The user selected GitHub Flow after comparing it with Gitflow.

## Decision

Use main as the only permanent integration branch, with focused branches and PRs.
Squash merge by default using Conventional Commit titles, preserving multiple
commits through reviewed exceptions. Require the stable verify CI check and
protected-main PR integration when hosting is configured. Do not require another
person's approval while there is one active maintainer.

Use immutable version tags on verified main commits to identify releases. Deploy
manually with recorded source/image identity. Merging does not deploy. Preserve
AGENTS.md authorization requirements for commits, publication, deletion, and deployment.

## Alternatives considered

### Gitflow

- Benefits: Explicit parallel release stabilization and production hotfix paths.
- Drawbacks: Permanent develop branch and repeated propagation/merge overhead.
- Reason not selected: Current incremental work does not need parallel release trains.

### Direct development on main

- Benefits: Minimal branch management.
- Drawbacks: No isolated PR review and easier accumulation of unverified changes.
- Reason not selected: Deployable main and reviewable outcomes are explicit goals.

### Automatic deployment after every merge

- Benefits: Short release cycle.
- Drawbacks: Introduces deployment credentials, operational effects, and automated
  release/rollback policy before those are established.
- Reason not selected: Tagged manual VM deployment is the approved recommendation.

## Rationale

Focused branches and one integration line reduce coordination while retaining a
review/check gate. Tags provide release traceability without environment branches.
Separate release authorization matches the existing operating workflow.

## Consequences

### Benefits

- Clear change scope, stable CI gate, and traceable production versions.
- No permanent develop branch or mandatory second reviewer bottleneck.

### Drawbacks and risks

- Unfinished features must remain isolated or use intentionally designed flags.
- Local files do not enable remote protections; the first hosted run and settings
  need explicit verification. Older-release fixes need deliberate backport planning.

### Operational impact

Review and integrate existing deployment work, preserve benchmark work, and stop
reusing completed topic branches. Configure the GitHub repository and required
check after publication is authorized. This ADR does not perform those actions.

## Follow-up actions

- [ ] Verify the first hosted CI run and activate main protection.
- [ ] Complete the documented transition from the existing deployment branch.

## References

- [Contribution guide](../../CONTRIBUTING.md)
- [GitHub Flow](https://docs.github.com/en/get-started/using-github/github-flow)
