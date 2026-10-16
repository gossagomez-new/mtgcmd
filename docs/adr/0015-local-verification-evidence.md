# ADR-0015: Local verification profiles and source-bound evidence

- Status: Accepted
- Date: 2026-10-16
- Related specification: [Local verification](../../spec/local-verification.md)

## Context

Repeated refactors require choosing and sequencing local checks and reporting
accurate results. CI and scripts/build.sh already define the underlying checks
and packaging order. Browser test servers share backend output directories with
clean builds, and informal logs are easy to mistake for current-source evidence.

## Decision

Add explicit local frontend, backend, and full profiles with opt-in browser checks.
Keep profile/command definitions centralized and run sequentially. Delegate full
packaging to scripts/build.sh; leave CI's explicit sequence and stable verify job
intact. Use a Bash entry point and Python standard-library process/evidence runner.

Write a versioned JSON manifest and per-command logs to fresh private directories
outside the checkout. Bind results to source fingerprints and selected settings,
preserve failed exits and skipped checks, and provide a read-only evidence check.
Hash option strings rather than copying inherited environment into metadata.
Evidence is local verification history, not an approval or signed attestation.

## Alternatives considered

### A second full build/CI pipeline

- Benefits: A standalone command could own all orchestration.
- Drawbacks: Duplicates packaging and risks drift from tested build behavior.
- Reason not selected: Reuse existing command ownership and test parity with CI.

### Informal console logs only

- Benefits: Minimal implementation and no evidence schema to maintain.
- Drawbacks: Scope, failures, and source identity are easy to misreport.
- Reason not selected: Accurate repeated handoffs need structured evidence.

### Parallel local execution

- Benefits: Shorter runs for independent checks.
- Drawbacks: Shared backend outputs and browser lifecycle complicate failure handling.
- Reason not selected: Predictable sequential execution is adequate at this scale.

## Rationale

Explicit scopes avoid overstating frontend-only success as full verification.
Structured source-bound records support later handoff tooling without changing
application/build ownership. Python handles process status and JSON directly;
Bash preserves the familiar project command entry point.

## Consequences

### Benefits

- Repeatable local commands and reviewable passed/failed/skipped evidence.
- Failed checks and stale results remain distinguishable from current success.

### Drawbacks and risks

- Profile definitions and evidence schema require maintenance.
- Start/end fingerprints miss transient edits restored during execution.
- Ignored dependencies/environment behavior and log authenticity are not attested.
- Child logs are verbatim and require review before publication.

### Operational impact

No deployment or production changes. Reports consume temporary local disk;
cleanup is an explicit user action. Existing CI/check commands remain usable.

## Follow-up actions

- [x] Implement profiles, evidence checks, fixtures, and real verification.

## References

- [Build automation](../../spec/build-automation.md)
- [GitHub Flow](0012-github-flow.md)
- [Specification validation](0014-specification-validation.md)
