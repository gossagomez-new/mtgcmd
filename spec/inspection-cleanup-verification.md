# Persisted inspection cleanup verification

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: TD03 protects persisted cleanup guarantees that filtered views alone cannot establish.
- Depends on: [Bounded library actions](bounded-library-actions.md), [Backend mutation testing](backend-mutation-testing.md)

## Summary

Strengthen [TD03](../docs/technical-debt-backlog.md) through assertions on stored
inspection sessions, preserving existing behavior and privacy checks.

## Goals

- Assert required stored removal and preservation, including recovery and rollback.
- Detect focused cleanup mutations that remain hidden by view filtering.

## Non-goals

- Replay, new commands/policies, production decomposition, score gates or clearing every metadata-validation survivor.
- Frontend changes and integration of the independent TD06/TD07 PR.

## Background

The scoped design audit confirmed GameAccess can hide an inspection after revoked
access, concession, finish or changed library order even if the stored entry remains.
Existing BoundedLibraryTest assertions emphasize filtered views. Required remediation
is stronger storage/postcondition coverage within current component boundaries.
The fresh focused LibraryInspections baseline kills 25/37 mutants, with 12 survivors;
seven survivors affect clear/invalidate/revoke, while five concern metadata or legacy
lookup guards. These are test findings, not proof of a current disclosure defect.

## Requirements

1. Verify stored cleanup on revocation, concession, finish, draw, explicit library moves, shuffle, exile and successful scry in both formats.
2. Distinguish the inspection actor from its library target. Remove only affected sessions; preserve unrelated actor/target entries where a format's available seats permit them.
3. Verify explicit close/full-search replacement, legacy missing/null storage, malformed null entries during invalidation, and repeated safe cleanup.
4. Rejected unauthorized/stale/invalid operations preserve sessions, card order, revision and logs. Unrelated successful commands preserve stored sessions.
5. Round-trip stored state and use real PostgreSQL recovery to prove cleanup is durable. Persistence failure must roll back cleanup, card/state updates, command records and action history; retry succeeds once.
6. Preserve existing filtered/raw-view privacy checks and retain RoomStore ownership of transactions, synchronization and idempotency.

## Contracts and data

No production API/schema changes. Game.libraryInspections remains actor-keyed,
with each LibraryInspection identifying a target and ordered bounded card IDs.
Tests may inspect persisted snapshots directly because durable removal is the contract.

## Technical constraints

Reuse current game/embedded PostgreSQL fixtures and pinned PIT. Preserve independent
mutation reports outside backend/target before any subsequent run or clean build.
Prefer assertions against public command outcomes and stored state; do not restructure
production solely to kill equivalent or out-of-scope mutants.

## Invariants

- Cleanup does not alter unrelated sessions or leak private card identities.
- Rejected or rolled-back operations do not partially mutate state or durable history.

## Edge cases and failures

Cross-library delegation, actor concession versus target concession, unrelated
sessions, empty/missing maps, invalid partitions/permissions, stale revisions,
duplicate retries, recovered snapshots and injected persistence failures.

## Migration and compatibility

Test-only work; no migration, dependency, wire change or version bump expected.
A demonstrated production defect would require explicit scope review before a fix.

## Acceptance criteria

- [x] Both-format stored cleanup and unrelated-session preservation are asserted.
- [x] Rejection, serialization, database recovery/rollback and retry guarantees pass.
- [x] Focused mutation comparisons identify newly detected cleanup regressions and classify remaining limits.
- [x] Relevant backend, formatting/build and specification checks pass.

## Verification

User confirmed comprehensive coverage on 2026-10-22: cleanup on revoke/concede/finish,
movement/shuffle/scry in both formats; actor/target distinctions; unrelated sessions;
rejection atomicity; serialization and database recovery/rollback; focused PIT.
For test-strength work, original production must pass and the relevant incorrect
mutation must fail the new assertion. Preserve comparable baseline/final reports
and identify the killing tests; do not treat timeouts as assertion kills.

### Executed verification (2026-10-22)

- Added 16 both-format domain cases and four parameterized PostgreSQL cases.
  The latter exercise 11 operations in both formats for successful cleanup/recovery
  and injected persistence failure/retry (44 operation scenarios).
- Original production passes the new assertions. All 16 newly killed mutants are
  attributed by PIT to `InspectionCleanupTest`, demonstrating the intended incorrect
  behavior fails the new tests. No production source or mutation policy changed.
- `./scripts/verify.sh --task inspection-cleanup backend` passed all 809 tests,
  Spotless, PMD reporting, packaging and JaCoCo report generation. The frontend was
  rebuilt with `npm --prefix frontend run build` so packaging uses this branch's assets.
- After final test-only convention cleanup, reran
  `mvn -f backend/pom.xml spotless:apply spotless:check -Dtest=InspectionCleanupTest,RoomStoreTest test pmd:pmd -q`:
  all 40 affected cases passed, including database recovery/rollback. New literal,
  comparison-order and broad helper-exception findings were addressed; fixture
  traversal and multi-assert warnings remain advisory without suppression changes.
- All 49 specification checks, Markdown formatting and whitespace checks passed.
  Browser suites and a whole-backend PIT campaign were not rerun for unchanged
  production code. This branch remains independent of PR #27.

Focused PIT used the same DEFAULTS mutators, one worker and
`-Dpit.targetTests=dev.mtgcmd.game.*` before/after. Each target below was supplied
separately through `-Dpit.targetClasses=dev.mtgcmd.game.<class>` with
`mvn -f backend/pom.xml -Pmutation test-compile pitest:mutationCoverage -q`.
Comparison included method descriptors, instruction indexes and blocks; all mutation
identities matched without collisions.

| Target class        | Baseline assertion kills | Final assertion kills | Remaining survivors | Timeouts before/after |
| ------------------- | ------------------------ | --------------------- | ------------------- | --------------------- |
| LibraryInspections  | 25/37                    | 33/37                 | 4                   | 0/0                   |
| CardZoneTransitions | 31/38                    | 34/38                 | 4                   | 0/0                   |
| LifecycleActions    | 64/70                    | 67/70                 | 1                   | 2/2                   |
| LibraryActions      | 15/22                    | 17/22                 | 5                   | 0/0                   |

All targeted clear/invalidate/revoke omissions, affected lifecycle cleanup calls
and library mutation/search cleanup omissions are detected. Additional gains include
legacy missing-map lookup and unauthorized shuffle rejection. The four remaining
LibraryInspections survivors concern metadata bounds; transition survivors concern
controller assignment, insertion bounds and reference cleanup. Remaining library
survivors concern target-validation bounds and full-search revocation classification,
not bounded-inspection removal. The lifecycle survivor is a turn-loop boundary.
The two existing lifecycle concession timeouts remain separately tracked by TD10;
none is counted as an assertion kill. No equivalence or production-defect conclusion
is inferred for the remaining out-of-scope findings.

Evidence: `/tmp/inspection-cleanup-pit-a7cs2z89/` preserves all four baseline/final
HTML/XML reports, `comparison.json` with killing-test attribution, final affected
Surefire XML and PMD XML. Full backend evidence is
`/tmp/mtgcmd-verify-inspection-cleanup-bj2zi6b1/manifest.json`, recorded before final
test-convention and documentation completion edits. Mutation results likewise precede
the final convention-only edits; behavioral assertions and production remained unchanged.

## Architecture decisions

No new ADR required: test-strength work preserves the persisted-state and transaction
contracts in [ADR-0024](../docs/adr/0024-bounded-library-inspections.md).

## Open questions

None.
