# RoomStore response and durable-event verification

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: TD04 protects successful response contents and durable events beyond execution/revision-only checks.
- Depends on: [Persisted inspection cleanup verification](inspection-cleanup-verification.md), [Backend mutation testing](backend-mutation-testing.md)

## Summary

Strengthen [TD04](../docs/technical-debt-backlog.md) without changing RoomStore's
production behavior. Reuse the PostgreSQL fixture retained from TD03.

## Goals

- Assert meaningful caller-filtered responses and durable action entries.
- Verify rejection, rollback, recovery and current-state retry contracts.

## Non-goals

- Production refactoring, new transactions, event schema changes or new retries.
- Making create idempotent, deployment, score thresholds or unrelated survivor cleanup.

## Background

The scoped audit found create/join/load/execute return filtered views and write
durable action entries inside existing transactions. Many tests consume setup
responses or compare two calls without asserting required field contents or
creation/join action history. Stronger postconditions are the required remediation;
no additional production-boundary changes are proposed.

## Requirements

1. Verify both-format create/join responses contain the correct room, caller seat, revision, roster, status and format. Verify registered deck and readiness changes in load/execute responses.
2. Verify creation/join/load/execute action rows identify the committed revision and seat, match the saved log entry, and retain meaningful operation descriptions without private deck identities.
3. Verify existing-member joins and duplicate load/execute requests return current caller state after subsequent changes, without extra state/history mutations. Create remains a new-room operation without an idempotency key.
4. Verify command IDs remain scoped to room and seat; mismatched reuse, stale revisions, invalid inputs, nonmembers and full-room joins reject without partial snapshots, membership, commands or action history.
5. Inject action persistence failures during create/join/load and assert transaction rollback, recovery and a successful explicit retry. Preserve existing command failure and concurrent stale-revision tests.
6. Preserve both formats, filtered privacy, validation behavior, RoomStore transaction ownership and established revision/idempotency semantics.

## Contracts and data

No wire/schema changes. Inspect the existing rooms, members, commands and actions
tables for durable postconditions; avoid pinning incidental SQL execution order,
map ordering or exact prose punctuation.

## Technical constraints

Reuse the embedded PostgreSQL fixture and pinned PIT. Group related contract tests
with JUnit nested tests rather than introducing a fixture framework or changing
existing test lifetimes. Preserve baseline/final reports outside backend/target.

## Invariants

- Each successful durable mutation records its resulting revision once.
- Rejected, failed and duplicate mutations do not add partial or duplicate history.

## Edge cases and failures

Null/default format, existing membership, current-state retries after later changes,
shared IDs across callers/rooms, invalid payloads and injected SQL failures.

## Migration and compatibility

Test-only maintenance; no migration or version bump expected. A demonstrated
production defect requires explicit scope review before implementing a fix.

## Acceptance criteria

- [x] Both-format response and event postconditions pass.
- [x] Retry, rejection, rollback and recovery guarantees pass.
- [x] Relevant focused mutants fail the new assertions; remaining limitations are recorded.
- [x] Relevant backend, formatting/build and specification checks pass.

## Verification

User confirmed comprehensive coverage on 2026-10-22: create/join/load/execute
responses, durable event seat/revision semantics, current-state duplicate retries,
rejections, injected create/join/load rollback, recovery and focused PIT comparisons.
Original production must pass; the relevant incorrect mutations must fail the new
assertions. Preserve comparable baseline/final settings and classify remaining
survivors/timeouts without changing production to improve scores.

### Results (2026-10-22)

- Added 13 cases in the nested `RoomStoreTest.ResponseContracts` group, retaining
  the existing PostgreSQL fixture and rollback/concurrency tests. Both formats
  pass; production source is unchanged.
- Focused original-code execution passed. Comparable PIT runs targeted
  `dev.mtgcmd.store.RoomStore` with `dev.mtgcmd.store.*` tests, using the same
  pinned DEFAULTS mutators and single worker: **35/42 → 41/42 killed**.
  All six newly detected mutations fail the new contract assertions: omitted
  create/join events, empty join/load responses, malformed-command validation,
  and the missing-room guard. Neither run had uncovered mutants, timeouts or
  execution errors. Mutant identities were compared, not just totals.
- The remaining create null-format normalization mutant survives because
  `Game.format()` supplies the same Commander default. Response/recovery checks
  establish the effective default, not a canonical non-null stored field. This
  is a fallback-masked survivor, not evidence of a caller-visible defect;
  production and analysis policy remain unchanged.
- `./scripts/verify.sh --task room-store-assurance backend` passed: **822 tests**,
  Spotless, advisory PMD generation, packaging and JaCoCo reporting. PMD retains
  test-fixture constructor/method-count, multiple-assertion, descriptive-name
  and stored-model traversal advisories; no suppressions were added.
- `python3 -B scripts/validate-specs.py` validated all 50 specifications;
  documentation formatting and whitespace checks passed.
- Preserved local PIT HTML/XML and identity comparison:
  `/tmp/room-store-contract-pit-14i6fr62/{baseline,final-first,comparison.json}`.
  Backend evidence: `/tmp/mtgcmd-verify-room-store-assurance-59kcce20/manifest.json`
  (source unchanged during verification; completion documentation was updated
  afterward). These temporary artifacts are local evidence, not durable repository
  deliverables.
- Frontend/browser suites and full-backend PIT were not rerun: production,
  frontend and wire contracts are unchanged; PIT results apply only to RoomStore.

## Architecture decisions

No new ADR required: tests preserve the transaction/ownership boundaries in
[ADR-0007](../docs/adr/0007-api-component-boundaries.md).

## Open questions

None.
