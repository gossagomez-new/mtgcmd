# Lifecycle action behavioral verification

## Metadata

- Status: Completed
- Priority: High
- Priority rationale: Protect turn progression, concession privacy cleanup, and game startup invariants.
- Depends on: [Backend mutation testing](backend-mutation-testing.md), [Game-engine decomposition](game-engine-decomposition.md)

## Summary

Strengthen LifecycleActions tests while preserving production behavior and boundaries.

## Goals

- Assert outcomes and failures for READY, START, PHASE, CONCEDE and FINISH in both formats.
- Compare a focused PIT run with the preserved full-run baseline.

## Non-goals

- New game policies, production refactoring, score gates, suppression or other component remediation.

## Background

The approved review found 15 survivors, one uncovered mutation and one timeout
among 54 LifecycleActions mutations. Existing command tests protect dispatch and
bookkeeping but do not establish all cleanup and boundary outcomes. The audit
rates this high priority (high severity 45 + high confidence 15 + medium effort
14 = 74): regression risk is demonstrated by mutations; stronger tests cost
maintenance but preserve useful component boundaries. A deterministic random
source can enter through the existing package-private constructor for a startup
behavior test; public facade tests retain real randomness and avoid order assertions.
No new production dependency or injection contract is necessary.

## Requirements

1. Exercise every lifecycle command through GameEngine.apply in both formats,
   checking exactly one revision/log entry on success and unchanged bookkeeping
   with existing errors on rejection.
2. Cover readiness flags, warning acceptance, empty decks, validation precedence,
   and started-game rejection. Preserve current unready behavior.
3. Cover host/full-table/readiness/start-status checks, initial hand sizes,
   commander placement, controller/owner fields and Standard first-player selection.
4. Test actual startup shuffling deterministically through existing constructors,
   without probabilistic order assertions or production changes.
5. Check every phase boundary, authorization alternatives, turn increments,
   wraparound, skipped conceded seats and combat cleanup.
6. Check concession grant/search cleanup, unchanged unrelated access, active and
   nonactive concessions, remaining/last-player cases and turn/combat outcomes.
7. Check finish permissions including a conceded host, and preserve public
   admission/validation ordering and mutable in-memory snapshot semantics.
8. Preserve the original PIT XML outside target before clean; compare equivalent
   target mutations, report timeouts separately and inspect remaining findings.

## Contracts and data

No wire, persistence or runtime changes. Tests assert existing strings, flags,
phase ordering and seat-filtered private-zone access. They do not impose automatic
untap/draw/finish behavior or rollback beyond existing RoomStore transactions.

## Technical constraints

Use existing JUnit/AssertJ and explicit Java types. Most commands use GameEngine;
one deterministic handler integration test uses the existing transition/random
constructor seam. Do not add production injection merely to control test randomness.
Do not introduce in-process preemptive timeouts that leave looping mutant threads.

## Invariants

- Production code, build policy, dependencies and analysis scope remain unchanged.
- Focused results do not represent a fresh full-backend score or establish a gate.

## Edge cases and failures

Missing or malformed flags, warning rejection, host versus active authorization,
incomplete tables, restarting games, skipped/wrapped conceded seats, all players
conceded, and preserved unrelated grants/searches require explicit assertions.

## Migration and compatibility

Test-only maintenance on a new branch from updated main; independent of the open
PermanentActions test PR. No app-version bump, release or deployment.

## Acceptance criteria

- [x] Behavioral tests pass on unchanged production code in both formats.
- [x] Focused PIT is compared with the preserved original class baseline.
- [x] Backend verification, formatting, PMD review and specification checks complete.

## Verification

User confirmed comprehensive coverage: every lifecycle command in both formats,
validation precedence, phase/turn boundaries, concession and privacy cleanup,
last-player cases, startup shuffle behavior, and a focused PIT comparison. First
establish green characterization tests, then use PIT to demonstrate failures on
incorrect mutations. Run targeted tests, clean Spotless/verify, inspect new PMD
advisories, validate specifications and formatting, then run clean PIT targeting
LifecycleActions with all dev.mtgcmd.* tests available. Record actual executed
checks and remaining survivors/timeouts without weakening tests or mutators.
Frontend/browser/Docker and a full-backend mutation rerun are outside this scope.

### Executed verification

On 2026-10-17, from main commit e57c993 plus this test-only working tree:

- All 30 new parameterized cases passed on unchanged production code: admission/
  startup, turns/concessions, privacy cleanup and deterministic startup shuffle.
- Clean backend Spotless/verify passed 601 tests with no failures/errors/skips,
  produced JaCoCo/PMD reports and packaged the application. After mechanical
  test cleanup, Spotless apply/check/verify again passed all 601 tests. The open
  PermanentActions PR's 52 tests are not part of this independent branch.
- PMD review removed an unused import, repeated literal and fixture method/constant
  naming collisions. Remaining advisories concern direct snapshot access, multiple
  assertions per coherent behavior, implicit test constructors, collection creation
  while building test data and a test-local nonconcurrent expected-value map.
  No production cleanup, suppressions or rule changes were introduced.
- Focused clean PIT succeeded in 52.994 seconds, targeting only LifecycleActions
  with all dev.mtgcmd.* tests available. PIT examined 116 test classes and ran 485
  selected test executions. Report XML contains the same 54 mutation identities
  as the saved original report (class, method/descriptor, line, mutator, description
  and bytecode indexes compared). Production code is unchanged.

```sh
mvn -f backend/pom.xml -Pmutation clean test-compile pitest:mutationCoverage \
  '-Dpit.targetClasses=dev.mtgcmd.game.LifecycleActions' \
  '-Dpit.targetTests=dev.mtgcmd.*'
```

| Status      | Original class baseline | New focused run |
| ----------- | ----------------------- | --------------- |
| KILLED      | 37                      | 51              |
| SURVIVED    | 15                      | 1               |
| NO_COVERAGE | 1                       | 0               |
| TIMED_OUT   | 1                       | 2               |

No execution/memory/unfinished statuses occurred. PIT reports 73/73 lines covered
and 53 detected mutations (98%), including two timeouts; only 51 have KILLED status.
No JaCoCo execution data was collected by the clean mutation run.

### Remaining mutation interpretation

- The survivor at phase line 74 changes the loop bound from less-than to
  less-than-or-equal to player count. For valid public PHASE calls, GameEngine
  requires a valid, non-conceded actor. Thus at least one player remains and the
  phase loop breaks within one complete traversal; the extra iteration cannot
  be reached. Tests cover wraparound and all other seats conceded. This is
  equivalent under those facade invariants, not an automatically classified
  PIT EQUIVALENT result. No corrupted-state scenario or production rewrite was
  introduced merely to kill it.
- The original timeout at concede line 99 remains a loop-condition mutation.
  A newly detected timeout forces lambda$concede$1 at line 96 to return true,
  entering the next-player loop even when no player remains. The last-player
  case completes normally on original code and explicitly checks turn/phase/
  status behavior. Timeouts belong to mutated worker execution and are not
  evidence of hangs in the original implementation or assertion-based kills.

Formatting, whitespace and offline validation of all 37 specifications passed.
Original XML and post-test PMD snapshots were retained outside target before clean;
logs are in temporary local artifacts. The latest HTML/XML report is focused on
LifecycleActions only. This is not a fresh full-backend score or exhaustive proof.
Frontend/browser, Docker, hosted CI and full-backend PIT were not run for this
backend-test-only change. No release or deployment was performed.

### Handoff integration verification

Before publication, integrated main ddff8d0 (including the PermanentActions test
PR) and resolved only the specification index by retaining both entries and graph
edges. No Java file required conflict resolution. Clean Spotless/verify passed
all 653 tests, and offline validation passed all 38 specifications. A fresh focused
LifecycleActions PIT run succeeded in 53.142 seconds with the same 54 mutations:
51 KILLED, two TIMED_OUT, one SURVIVED and none uncovered. The interpretation above
remains unchanged; no full-backend mutation score is claimed. Hosted verification
remains pending publication. Logs: lifecycle-integrated-verify.log and
lifecycle-integrated-pit.log in temporary local artifacts.

## Architecture decisions

[ADR-0005](../docs/adr/0005-game-engine-component-boundaries.md) already defines
lifecycle/transition boundaries and [ADR-0019](../docs/adr/0019-backend-mutation-testing.md)
defines opt-in reporting-only PIT. No new architectural decision is required.

## Open questions

None.
