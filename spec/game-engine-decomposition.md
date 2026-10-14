# Game-engine component decomposition

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: Localize command, privacy, transition, and projection responsibilities while preserving gameplay.
- Depends on: [Standard tables](standard-tables.md)

## Summary

Retain GameEngine as a facade. Extract shared access policy, player-view mapping,
zone transitions, command-input/lookup helpers, and cohesive action groups behind
functional command dispatch. The user selected comprehensive coverage and approved
ADR-0005.

## Goals

- Separate independently changing gameplay and view responsibilities.
- Reduce GodClass, CognitiveComplexity, CyclomaticComplexity, and NPathComplexity findings.
- Preserve every command, privacy rule, wire field, and transaction invariant.

## Non-goals

- Card-rules automation, event sourcing, undo, new persistence, or new wire schemas.
- A class per command, State/Strategy framework, or unrelated PMD style cleanup.

## Background

The approved audit found apply combines most actions (cognitive complexity 87,
cyclomatic complexity 48, NPath 348), while move combines transition effects
(cognitive complexity 19, cyclomatic complexity 15, NPath 513). Views and permission
rules share the same class. The follow-up audit confirmed admission/error order,
transition order, and partial in-memory mutations on rejected actions are meaningful
compatibility details. RoomStore rolls back persisted state; the facade must not
introduce a new snapshot/transaction model.

## Requirements

### Functional requirements

1. Keep public constructors, join/load/apply/view/clean, ZONES, PHASES, and wire
   strings compatible. GameEngine owns common validation, successful revision
   advancement, log descriptions, and the bounded 100-entry action log.
2. Validate envelope, revision, actor, admission, then dispatch. READY/START bypass
   playing/concession admission as before; FINISH permits a conceded host.
   Unknown commands retain the same error precedence in lobby/playing/conceded states.
3. GameAccess owns private-zone permission, visibility, definition inspection,
   editability, and editable-card validation; handlers and projections share it.
4. GameViewMapper constructs the exact existing seat-filtered map without mutating
   the game. Preserve field names, omission versus null, zone counts, private
   warnings, commander metadata, and face-down/reveal/grant/search behavior.
5. CardZoneTransitions owns move/shuffle/rekey and coherent private helpers for
   cleanup, reset, ownership, token removal, and placement. Preserve operation
   order, same-zone behavior, hidden identifiers, dependent references, and
   library top/bottom ordering. Shuffle invalidates relevant searches/reveals.
6. Group commands into lifecycle, library/access, permanent, object/stack, and
   counter actions. Use one functional handler contract and an explicit immutable
   registry. Each handler returns the existing action description and leaves
   revision/log advancement to GameEngine.
7. Shared input/lookup helpers preserve strict type/range checks, defaults,
   whitespace handling, and exact messages. Shared zone constants retain string
   representation; lifecycle owns the existing phase sequence.
8. Preserve both formats' seat/deck/start/commander behavior and single SecureRandom
   use for shuffle/dice. Preserve UUID generation and all object/counter limits.
9. RoomStore remains responsible for row locking, revisions/idempotency, persistence,
   and transactional rollback; component extraction does not change its contract.

### Non-functional requirements

- No schema, protocol, dependency, or client changes.
- No private-card information in action descriptions or unauthorized views.
- Review PMD findings on each extracted component to avoid simply relocating complexity.

## Contracts and data

Command and snapshot models remain unchanged. Package-private handlers receive
Game, seat, and command data and return a description. Public facade operations
retain validation precedence and bookkeeping. Internal utilities and components
are implementation boundaries, not new API services.

## Technical constraints

Reuse existing deck rules, format profiles, clocks, randomness, and persistence.
Retain mutable snapshot semantics and current partial in-memory mutation behavior
on failures; only persisted atomicity is guaranteed by RoomStore.

## Invariants

- A successful command advances one revision and records one existing description.
- Rejected commands do not advance revision or append logs.
- Hidden identities/definitions remain filtered by the same access policy.
- Transitions preserve ordering, reference cleanup, and token lifecycle semantics.
- Failed persisted commands and duplicate retries retain existing transaction behavior.

## Edge cases and failures

Cover malformed/null envelopes, stale revisions, unknown seats/commands, concession,
invalid input types/ranges, grants/search/reveal changes, attachment cycles, token
removal, library placement, same-zone resets, phase rollover, and both formats.

## Migration and compatibility

No migration or caller changes. Existing facade tests and database/browser flows
continue to exercise the composed engine.

## Acceptance criteria

- [x] Facade and components have the approved responsibilities.
- [x] Every existing command remains registered with compatible results and descriptions.
- [x] Shared policy and views preserve privacy and wire shape.
- [x] Transition, admission, revision/log, and rollback behavior remains compatible.
- [x] Targeted engine/transition PMD complexity findings are removed.
- [x] Full backend, formatting, packaging, and browser verification pass.

## Verification

Confirmed target: comprehensive coverage. Add characterization tests for all
commands in both formats, descriptions, one-revision advancement, validation
precedence, conceded-host finish, and log bounds. Add access/view and transition
contracts/tests before implementing their behavior; fail on explicit unimplemented
stubs while the original facade tests remain green. Exercise the full transition
matrix, token destinations, library placement, shuffle invalidation, view wire
fields/nonmutation, face-down and private visibility versus editing.

Run existing gameplay/Standard, import, database rollback/idempotency/concurrency,
HTTP, and diagnostics tests. Verify focused checks after extraction, then full
Spotless/backend verify and PMD reports. Run the existing five browser flows with
isolated embedded PostgreSQL/Java/Vite servers and Chrome. Record blocked checks
explicitly instead of claiming success.

### Executed verification

- Characterization baseline: 87 tests passed against the original engine.
- Red component contracts: 60 explicit unimplemented-contract failures; the original 87 tests passed.
- Composed engine: all 147 focused tests passed after extraction.
- Full `spotless:apply spotless:check verify`: 348 tests passed, no failures or skips; packaging and PMD reporting passed.
- All five existing browser regression flows passed.
- PMD reports no GodClass, CognitiveComplexity, CyclomaticComplexity, or NPathComplexity findings in the refactored production components. The command-test data fixture has a cyclomatic-complexity advisory; unrelated findings remain outside scope.

## Architecture decisions

- [ADR-0005](../docs/adr/0005-game-engine-component-boundaries.md): facade, access,
  views, transitions, grouped command handlers, and retained transaction ownership.
- [ADR-0001](../docs/adr/0001-authoritative-game-state.md): authoritative snapshots,
  persistence, and privacy.
- [ADR-0002](../docs/adr/0002-room-format-profiles.md): server-owned format profiles.

## Open questions

None.
