# ADR-0017: Shared application semantic versioning

- Status: Accepted
- Date: 2026-10-17
- Related specification: [Semantic versioning](../../spec/semantic-versioning.md)

## Context

The backend project, frontend package, and lockfile currently declare `0.1.0`.
The frontend is embedded in the backend deployment image. GitHub Flow already
requires immutable release tags and separately authorized manual deployment, but
it does not define bump selection or a compatibility contract. The user approved
Semantic Versioning, a shared application version, the initial-development bump
policy, and this ADR.

## Decision

Adopt Semantic Versioning 2.0.0 with a shared application release version. Keep
manifest values synchronized through reviewed release-preparation PRs. Ordinary
work does not change version declarations. Publish immutable tags only through
the existing authorized release workflow; no automatic release tooling is added.

Use MINOR for features and breaking changes during `0.x`, and PATCH for compatible
fixes or maintenance-only releases. Identify breaking changes in every phase.
Adopt `1.0.0` through an explicit stable-contract decision, then use SemVer's normal
MAJOR/MINOR/PATCH compatibility rules. The contribution guide defines the contract,
bump examples, manifest locations, and release preparation requirements.

## Alternatives considered

### Independent frontend/backend versions

- Benefits: Supports independent release schedules.
- Drawbacks: Adds coordination and compatibility tracking for components currently
  shipped as one image.
- Reason not selected: Deployment and release ownership are unified today.

### Automatic releases from commit types

- Benefits: Reduces mechanical version updates and tagging effort.
- Drawbacks: Commit types cannot establish compatibility by themselves; publishing
  would require new automation, permissions, and authorization arrangements.
- Reason not selected: Retain reviewed, explicitly authorized releases and the
  existing build process. Automation can be proposed separately when warranted.

### Adopt 1.0.0 immediately

- Benefits: Establishes stable major-version compatibility signaling immediately.
- Drawbacks: Commits the evolving API and upgrade behavior to stable guarantees
  without a dedicated release review.
- Reason not selected: The user selected the explicit initial-development policy.

## Rationale

One version matches the deployed application boundary. Release-time bumps make
versions describe reviewed releases instead of individual implementation commits.
Explicit compatibility review covers protocol, persisted rooms, documented player
workflows, and operator interfaces, beyond changes inferred from commit labels.

## Consequences

### Benefits

- Clear release identity and impact classification.
- Consistent frontend/backend versions and explicit breaking-change notes.
- No additional dependency or automatic external effects.

### Drawbacks and risks

- Manual review must catch version mismatches and incorrect bump classification.
- Before 1.0.0, a minor bump alone cannot distinguish a feature from a breaking
  change; release notes remain essential.

### Operational impact

No immediate version bump, tag, image publication, or deployment. Existing `0.1.0`
values remain unchanged. Published history is not rewritten. Future release PRs
must inspect actual published history before choosing a new version.

## Follow-up actions

- [x] Document compatibility and release review in the contribution guide.
- [x] Link the policy from repository instructions and the README.

## References

- [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html)
- [GitHub Flow decision](0012-github-flow.md)
- [Contribution guide](../../CONTRIBUTING.md#semantic-versioning)
