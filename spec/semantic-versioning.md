# Semantic versioning convention

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: Give future releases explicit compatibility meaning and consistent identity.
- Depends on: [GitHub Flow](github-flow.md)

## Summary

Adopt shared application semantic versioning and document release-time review.

## Goals

- Define the compatibility contract, bump rules, and version synchronization.
- Make future release preparation concrete without publishing a release now.

## Non-goals

- Version bumps, historical retagging, publication, deployment, or release automation.
- New CI/build behavior or dependency changes.

## Background

The Java project, frontend package, and lockfile agree at `0.1.0`. Existing GitHub
Flow defines immutable version tags and manual deployment, but not bump selection.
The pre-design review found no prerequisite code change; the missing policy is
within the approved scope. User confirmed shared versions and the `0.x` policy.

## Requirements

1. Follow Semantic Versioning 2.0.0, with one version for the packaged application.
2. During `0.x`, features, deprecations, and breaks increment MINOR; compatible fixes
   and maintenance-only releases increment PATCH. Always identify breaking changes.
3. From `1.0.0`, use standard MAJOR for breaks, MINOR for compatible features or
   public API deprecations, and PATCH for compatible fixes/maintenance releases.
4. Define compatibility across documented HTTP/WebSocket contracts, supported
   workflows/privacy/permissions, retained saved rooms, and operator interfaces.
   Distinguish internal details, preserving migrations, and client-version direction.
5. Choose the highest required bump for the full release scope since the previous
   published version. Require appropriate notes for breaking changes and upgrades.
6. In release-preparation PRs, synchronize the Java project version, frontend package
   version, lockfile top-level version, and lockfile root-package version. Preserve
   parent/dependency versions. Ordinary PRs do not update the application version.
7. Document optional prereleases, numeric reset rules, tag prefixes, and immutable
   releases. Keep image version tags aligned and record source/image identity.
8. Preserve existing explicit authorization and verified-main/manual-deployment
   requirements. Be explicit that version equality and bump classification are
   manual review checks, not newly enforced CI checks.
9. Add policy links to the README and AGENTS.md, ask for release impact in the PR
   template, and maintain the spec index/graph.

## Contracts and data

Version strings are shared release metadata, without `v` in manifests and with `v`
in annotated Git tags. Optional prerelease suffixes are identical across declarations.
The current `0.1.0` declarations and public/runtime contracts are unchanged by this task.

## Technical constraints

Documentation-only. Reuse existing commands and release workflow; add no scripts,
dependencies, tags, commits, publishing, or deployment as part of implementation.

## Invariants

- A merge remains distinct from a release or deployment.
- Published releases/tags are immutable.
- A commit label never overrides actual compatibility impact.

## Edge cases and failures

Mixed feature/fix/break releases, security fixes with contract changes, maintenance
without a release, duplicate version tags, pre-1.0 breaks, prereleases, mismatched
manifest versions, and preserving versus destructive migrations require explicit rules.

## Migration and compatibility

Retain `0.1.0` and existing history. Inspect remote published history during actual
release preparation; this policy does not declare an existing deployment released
under a new tag or retroactively classify prior changes.

## Acceptance criteria

- [x] Instructions agree on shared SemVer and release-time bumps.
- [x] Compatibility, pre/post-1.0 rules, manifest synchronization, and authorization are explicit.
- [x] Specification, ADR, and policy links validate; current versions remain identical.

## Verification

User selected focused documentation verification: check policy examples against
SemVer 2.0.0, inspect
existing build/release references and all four version declarations, run Markdown
formatting on changed documentation, specification/index validation, and whitespace
checks. No unit tests are added because no executable behavior changes. Do not claim
CI enforcement, a version bump, a tag, or a deployment was performed.

### Executed checks

- Reviewed bump/reset/prerelease examples against the official SemVer 2.0.0 text.
- Read all four version values (Java project, frontend package, and both lockfile
  roots): identical at 0.1.0. Those manifests and the CI workflow are unchanged.
- Changed-document Markdown formatting, specification/index/link validation, and
  whitespace checks passed.
- No runtime, unit, browser, or build checks were rerun for this documentation-only
  change. No executable behavior, release, or deployment changed.

## Architecture decisions

[ADR-0017](../docs/adr/0017-semantic-versioning.md) records the approved shared-version
and manual release-preparation convention, extending
[ADR-0012](../docs/adr/0012-github-flow.md)'s tagged manual releases.

## Open questions

None.
