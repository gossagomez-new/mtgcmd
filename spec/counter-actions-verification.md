# Counter action behavioral verification

## Metadata

- Status: Completed
- Priority: High
- Priority rationale: Mutations demonstrate missing assertions for command updates, permissions and limits (TD01).
- Depends on: [Backend mutation testing](backend-mutation-testing.md), [Game-engine decomposition](game-engine-decomposition.md)

## Summary

Strengthen counter command assurance without changing production behavior.
Tracks [TD01](../docs/technical-debt-backlog.md#td01--protect-counter-command-behavior).

## Goals

- Protect COUNTER, CARD_COUNTER, COMMANDER_CAST and DICE outcomes and rejection contracts.
- Demonstrate new assertions detect incorrect focused mutations.

## Non-goals

- New game policies, transport retries, broad random infrastructure, other command families or score gates.

## Background

The saved full PIT baseline killed 13 of 24 CounterActions mutations. Existing
dispatch tests check revision/log bookkeeping but miss important state outcomes.
Scoped audit found existing GameAccess policies and SecureRandom constructor
injection sufficient; no production refactor is required to add deterministic
handler tests. Preserve those boundaries rather than expanding the task.

## Requirements

1. Verify explicit values, deltas, defaults, trimmed names and updates at capacity for player/card counters in both formats.
2. Reject invalid numbers, names, capacity and accumulated-value overflow atomically, including revision/log and unrelated state.
3. Verify card controller/private-zone permissions, grants and library-search prerequisites.
4. Verify commander ownership, exact cast updates, default/correction deltas, and Standard rejection with existing validation precedence.
5. Verify deterministic dice endpoints and requested/default random bounds through the existing handler constructor. Verify facade dispatch/bookkeeping using existing command tests.
6. Preserve original mutation evidence outside target and compare focused outcomes; investigate remaining survivors rather than imposing a percentage target.

## Contracts and data

Existing names, ranges, error messages, defaults, wire shape and persistence stay unchanged.
Successful facade commands increment revision and append one log entry. Rejections
leave the supplied game unchanged. Handler-only dice tests do not own bookkeeping.

## Technical constraints

Use existing JUnit, AssertJ, Mockito and fixture conventions; explicit types and
final bindings. No dependency, build policy or production API changes.

## Invariants

- Preserve authority, privacy, transaction ownership and existing mutation semantics.

## Edge cases and failures

Cover numeric endpoints, fractional/nonfinite/type errors, existing keys at capacity,
unknown/opponent commanders and overlapping invalid inputs. Do not assert probabilistic dice results.

## Migration and compatibility

Test-only change; no migration or version bump.

## Acceptance criteria

- [x] Original production passes the confirmed suite.
- [x] Focused mutations demonstrate the missing assertions and remaining findings are explained.
- [x] Backend formatting/verification and specification validation pass.

## Verification

User confirmed comprehensive coverage on 2026-10-22: all four commands, both formats,
permissions/grants, numeric/capacity boundaries, defaults/precedence, rejection
atomicity, deterministic dice endpoints and focused PIT. Retain existing facade
dispatch tests. Record executed results here; no frontend/browser changes are planned.

### Executed verification

On 2026-10-22, against main revision d2ba354 plus the TD01 test/documentation changes:

- Twelve new parameterized cases pass on unchanged production. They cover both formats and include full serialized-game equality after rejection, preserving revision/log and unrelated data.
- The task runner's backend profile passed all 751 tests, Spotless checks, PMD generation, JaCoCo reporting and application packaging. Existing facade dispatch tests remain passing.
- Focused PIT targeted `dev.mtgcmd.game.CounterActions`, with all `dev.mtgcmd.*` tests available. All 24 mutations were KILLED, with no survivors, uncovered mutations, timeouts or execution errors; mutated-class line coverage was 26/26.
- Mutation identities matched the saved full-run baseline by class, method/descriptor, line, mutator, description and bytecode indexes. All 11 previous survivors now fail new assertions; this includes removed counter updates, commander ownership checks and range/capacity guards. These failures supply the red evidence for this test-strength change; no production fix was necessary.
- Test-file PMD cleanup addresses repeated literals, literal-first comparison, numeric separators and unnecessary casts. Contextual advisories for fixture model traversal, multiple assertions, local maps and implicit test construction remain; no suppression or rule change was added.
- Offline specification validation and Markdown formatting passed. Frontend/browser suites and full-backend PIT were not run for this backend-test-only scope. The focused 100% mutation result is not a new whole-backend score or proof of exhaustive correctness.

The baseline XML remains at `/tmp/mtgcmd-full-pit-5k057vf8/pit-reports/mutations.xml`.
The current focused HTML/XML are in `backend/target/pit-reports`; preserve them
before a clean build. No commit, PR, release or deployment is part of these results.

## Architecture decisions

Existing [ADR-0005](../docs/adr/0005-game-engine-component-boundaries.md) and
[ADR-0019](../docs/adr/0019-backend-mutation-testing.md) apply. No new decision needed.

## Open questions

None.
