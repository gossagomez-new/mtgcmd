# Object action behavioral verification

## Metadata

- Status: Completed
- Priority: High
- Priority rationale: Mutations demonstrate missing object postcondition, permission and capacity assertions (TD02).
- Depends on: [Backend mutation testing](backend-mutation-testing.md), [Game-engine decomposition](game-engine-decomposition.md)

## Summary

Strengthen tests for existing object commands without changing production behavior.
Tracks [TD02](../docs/technical-debt-backlog.md#td02--protect-object-command-invariants).

## Goals

- Protect TOKEN, EDIT_TOKEN, COPY, ABILITY and RESOLVE_ABILITY in both formats.
- Demonstrate detection of the existing permission/postcondition mutation survivors.

## Non-goals

- New casting/resolution rules, automatic effects, command hierarchy, production refactoring or analysis gates.

## Background

The saved full PIT run killed 11/24 ObjectActions mutations, with 12 survivors
and one uncovered mutation despite full line execution coverage. Existing facade
tests primarily check bookkeeping and a representative token/copy/ability flow.
The scoped audit found existing GameAccess and facade boundaries suitable for
test-only assurance. No prerequisite refactor or new dependency was identified.
The trade-off is additional scenario maintenance in exchange for explicit
permissions, state and boundary guarantees; do not mirror every implementation branch.

## Requirements

1. Assert token identity, owner/controller/zone owner, battlefield placement, characteristics, defaults and independent mutable state.
2. Assert token edits preserve identity, image and unrelated permanent state while changing allowed characteristics; reject non-token/unauthorized edits.
3. Assert copies preserve definition/face, create actor-owned battlefield tokens with independent mutable state, and leave sources unchanged. Cover different owners/controllers and supported public zones.
4. Distinguish hidden-definition rejection from visible-but-private-zone rejection, including grants/reveals and permitted face-down inspection.
5. Cover exact object and stack capacity boundaries and relevant overlapping failures in existing validation order.
6. Assert ability identity, controller, cleaned text, revision-derived order and removal of exactly the selected instance; reject unknown or foreign abilities.
7. Cover required/optional field boundaries, malformed inputs and facade admission. Rejections preserve the entire supplied game, including revision and log.
8. Preserve original mutation evidence and compare focused mutation identities/statuses; explain survivors or timeouts rather than imposing a score gate.

## Contracts and data

Preserve wire names, defaults, current errors/precedence, JSON shape and persistence.
Successful facade commands append one log entry and increment revision once.
Definition equality is a value contract; tests do not require shared definition
object identity. Mutable counters/reveal sets must remain independent.

## Technical constraints

Use existing JUnit, AssertJ and LifecycleFixture patterns. No production or build
changes are intended. Reuse existing dispatch coverage; do not add unrelated TD01
or general transport scenarios.

## Invariants

- Preserve assisted-tabletop rules, privacy, access policy and RoomStore transaction ownership.
- No partial game mutation on tested rejections.

## Edge cases and failures

Include final free capacity slot, reuse after resolution, duplicate ability text
with distinct IDs/controllers, non-token edits, unknown IDs, private cards visible
through grants/reveals, hidden public cards, empty/oversized/type-invalid input,
and invalid actor/conceded/not-playing admission.

## Migration and compatibility

Test-only change; no migration, public API change or version bump.

## Acceptance criteria

- [x] Original production passes the confirmed suite.
- [x] Focused PIT demonstrates new assertions catch targeted mutations.
- [x] Backend verification, formatting and specification validation pass.
- [x] Backlog and executed verification record reflect actual outcomes and limitations.

## Verification

User confirmed comprehensive coverage on 2026-10-22: all five commands in both
formats, token/copy characteristics and independent state, visibility/permissions,
separate owners/controllers, limits, validation precedence, exact ability
selection/order, rejection atomicity, facade admission and focused PIT.
Frontend/browser changes and full-backend mutation reruns are outside this scope.

### Executed verification

On 2026-10-22, from d2ba354 plus committed TD01 (288e96d) and these TD02 tests/docs:

- All 18 new parameterized cases pass on unchanged production. Initial failures were corrected test expectations for the existing `Card unavailable` message, not production defects or mutation red evidence.
- Task-scoped backend verification passed 769 tests with no failures/errors/skips, Spotless checks, PMD report generation, JaCoCo reporting and application packaging.
- Focused PIT targeted only `dev.mtgcmd.game.ObjectActions`, with all `dev.mtgcmd.*` tests available. All 24 mutations were KILLED; none survived, lacked coverage, timed out or failed execution. Mutated-class line coverage was 58/58.
- The 24 identities match the saved full-run baseline by class, method/descriptor, source line, mutator, description and bytecode indexes. All 12 previous survivors and the one uncovered mutation are killed by ObjectActionsTest. These supply the meaningful red evidence: removed permission/capacity checks, incorrect ability order and incorrect selected-ability matching fail the new assertions.
- PMD repeated fixture literals were extracted into private constants. Remaining advisories concern model traversal, multiple assertions, test helper counts/construction, local maps and deliberate creation of distinct objects in capacity fixtures. No rule changes or suppressions were introduced.
- All 47 specifications validate offline; Markdown formatting and whitespace checks pass. No frontend/browser suite, full-backend PIT, hosted CI or deployment was run for this test-only scope. The focused result is not a new whole-backend mutation score or exhaustive correctness claim.

Baseline XML is preserved at `/tmp/mtgcmd-full-pit-5k057vf8/pit-reports/mutations.xml`.
Current focused reports are in `backend/target/pit-reports`; preserve before clean.
The local TD02 branch includes TD01 because it has not been merged; the specification
has no TD01 dependency. TD02 remains uncommitted and no PR has been created.

## Architecture decisions

Existing [ADR-0005](../docs/adr/0005-game-engine-component-boundaries.md) and
[ADR-0019](../docs/adr/0019-backend-mutation-testing.md) apply; no new decision needed.

## Open questions

None.
