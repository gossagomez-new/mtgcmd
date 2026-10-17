# Permanent action behavioral verification

## Metadata

- Status: Completed
- Priority: High
- Priority rationale: Protect gameplay validation and privacy behaviors exposed by surviving mutations.
- Depends on: [Backend mutation testing](backend-mutation-testing.md), [Game-engine decomposition](game-engine-decomposition.md)

## Summary

Strengthen backend assertions for PermanentActions without changing production behavior.

## Goals

- Verify observable state changes, rejected operations, privacy effects, and bookkeeping.
- Measure remaining mutations with the existing opt-in PIT profile.

## Non-goals

- New game rules, command contracts, production refactoring, score gates, or exclusions.
- WebSocket/lifecycle mutation remediation or a full-backend mutation rerun.

## Background

The approved review found 31 surviving and three uncovered mutations out of 52
in PermanentActions. The dispatch matrix checks revisions/descriptions but does
not establish every action's state changes or negative paths. Required within-scope
work is stronger behavioral assertions; changing component boundaries offers no
demonstrated benefit. Existing transition/access tests remain useful and are retained.

## Requirements

1. Exercise all ten permanent commands through GameEngine.apply in both formats.
2. Assert successful state changes and exactly one revision/log entry per command.
3. Verify invalid zones, permissions, targets, flags, numeric bounds, and validation
   order, with existing errors and unchanged revision/log on rejection.
4. Check face-down flags, toggles and reveal clearing through seat-filtered views;
   reveal audiences replace existing access and reject invalid entries.
5. Check attachment/detachment, valid chains, self/long cycles and battlefield rules.
6. Check mixed untap selection by controller and zone, transformation wraparound,
   commander destinations, movement stack order, control cleanup, and combat markers.
7. Preserve the original mutation report outside target before clean. Run focused
   PIT with all backend tests available, compare target-class results and explain
   surviving/uncovered/time-out statuses without inventing score requirements.

## Contracts and data

No wire or persistence changes. Tests retain current messages, defaults, ownership,
visibility, and explicit-versus-omitted input semantics.

## Technical constraints

Use established JUnit/AssertJ and explicit Java types. Assertions concern outcomes,
not implementation call counts. Mutable in-memory snapshots do not gain rollback;
RoomStore continues to own durable transaction rollback. PIT worker timeouts are
reported separately from assertion kills, including cycle-related mutations.

## Invariants

- Production code, public contracts, analysis configuration, and runtime dependencies remain unchanged.
- No minimum mutation score or app-version bump.

## Edge cases and failures

Empty/replaced/duplicate reveal audiences; invalid types and seats; self/indirect
attachment cycles; off-battlefield cards; conceded controllers/targets; private-zone
access; zero/one/two-face definitions; endpoint and non-finite coordinates.

## Migration and compatibility

Test-only maintenance. Work branches from the verified PIT commit while PR 15 is
open; no remote merge or publication is implied.

## Acceptance criteria

- [x] New behavioral tests pass on unchanged production code in both formats.
- [x] The focused PIT report is compared with the saved baseline.
- [x] Full backend verification, formatting, specification checks and relevant PMD review pass.

## Verification

User confirmed comprehensive coverage: every PermanentActions command, both formats,
validation precedence, unchanged revision/log on rejection, privacy views, attachment
cycles, mixed untap selection, numeric boundaries, and a focused PIT rerun. Establish
a green characterization suite on current production, then use PIT to demonstrate
failure on incorrect mutations. No production implementation is planned. Run focused
tests, clean Spotless/verify, specification validation and whitespace checks; review
new test-file PMD findings. Then run clean mutation analysis targeting only
dev.mtgcmd.game.PermanentActions with dev.mtgcmd.* tests. Preserve reports and record
executed checks and remaining limitations. Frontend/browser/deployment are outside scope.

### Executed verification

On 2026-10-17, based on PIT commit 01c7d15 plus this test-only working tree:

- All 52 new parameterized cases passed against unchanged production code. The
  three suites cover state actions, relations/privacy and validation, with a
  small shared fixture constructing valid playing snapshots. Commands enter
  through GameEngine.apply; setup does not duplicate deck loading/start flows.
- Clean backend Spotless/verify passed 623 tests with no failures/errors/skips,
  generated JaCoCo/PMD reports and packaged the application. After mechanical
  test-style cleanup, Spotless apply/check/verify again passed all 623 tests.
- Reviewed PMD findings on the new test files. Removed duplicate literals,
  chained assignments and a short local name. Retained advisory direct snapshot
  access, multiple assertions per coherent behavior, implicit JUnit constructors
  and two helper-assertion detection false positives. No suppressions/rules changed.
- Focused clean PIT invocation succeeded in 32.177 seconds:

```sh
mvn -f backend/pom.xml -Pmutation clean test-compile pitest:mutationCoverage \
  '-Dpit.targetClasses=dev.mtgcmd.game.PermanentActions' \
  '-Dpit.targetTests=dev.mtgcmd.*'
```

The original full-backend XML was preserved outside target before clean. Compared
production class, method/descriptor, line, mutator, description and bytecode indexes:
the focused run has the identical 52-mutant population for this unchanged class.

| Status      | Original class baseline | New focused run |
| ----------- | ----------------------- | --------------- |
| KILLED      | 18                      | 52              |
| SURVIVED    | 31                      | 0               |
| NO_COVERAGE | 3                       | 0               |
| TIMED_OUT   | 0                       | 0               |

No execution/memory/unfinished statuses occurred. All detected statuses are KILLED,
with killing-test names in XML, not timeout detections. PIT reports 81/81 covered
lines for this class and ran 125 selected test executions. This demonstrates
assertion failures on the targeted incorrect mutations without changing production.
No JaCoCo execution data was collected by the clean mutation run.

These measurements are not a new full-backend score or a promise of exhaustive
correctness; no score gate was introduced. Reports remain in target/pit-reports,
and clean can remove them. Normal verification logs and the original XML/PMD
snapshot are retained in temporary local artifacts for this review. Frontend,
browser, Docker and hosted CI were not run because this change is backend tests
and specification documentation only.

## Architecture decisions

[ADR-0005](../docs/adr/0005-game-engine-component-boundaries.md) already establishes
command/access/transition boundaries; [ADR-0019](../docs/adr/0019-backend-mutation-testing.md)
defines reporting-only mutation execution. No new architectural decision is required.

## Open questions

None.
