# Give back turn

## Metadata

- Status: Completed
- Priority: High
- Priority rationale: Complete R20 with explicit correction semantics and preserved gameplay state.
- Depends on: [Frequent table interactions](table-interactions.md), [Card interaction increment](card-interactions.md)

## Summary

Let the active player return turn tracking to the previous saved handoff in Standard and Commander.

## Goals

- Walk backward through saved turn checkpoints, restoring seat, phase and displayed turn number.
- Keep all other gameplay state intact and preserve durable authority, privacy and retries.

## Non-goals

- Undo/redo of game state, restoring cleared combat markers or automatically drawing/untapping.
- Casting, mulligans, replay, sideboarding or host overrides.

## Background

The pre-design audit traced LifecycleActions, Game, GameViewMapper, RoomStore and
turn notifications. Advancement clears combat and resets phase, so returning needs
its own transition. Notifications currently assume increasing displayed turns and
need a separate monotonically increasing handoff identity. These are required R20
integration changes; no unrelated refactor is included.

The user confirmed backward history traversal, restoration of phase and turn number,
comprehensive coverage, and creation of ADR-0022.

## Requirements

### Functional requirements

1. Add an always-visible Give back turn button to persistent turn controls. Enable
   it only when playing, connected, no action is pending, the viewer is the active
   non-conceded player, and the server reports an eligible previous checkpoint.
2. Record the outgoing active seat, displayed turn number and phase when END_TURN,
   Cleanup-to-Untap PHASE or active-player concession hands off the turn. Ordinary
   phase changes and non-active concessions do not create checkpoints.
3. Retain the latest 100 checkpoints. GIVE_BACK_TURN pops only the latest eligible
   checkpoint and restores its three turn fields. Repeated returns walk backward;
   returning does not push the abandoned turn onto history. A subsequent forward
   handoff records the current corrected turn; there is no redo branch.
4. Reject before any mutation if the actor is not active, the game is not playing,
   the actor conceded, history is empty/invalid, or the immediately previous player
   conceded. Do not skip a conceded checkpoint to select an older player.
5. Do not change cards, abilities, counters, combat, grants, searches, decks or
   readiness when returning. A lone survivor can restore their own prior checkpoint.
6. Increment a separate turn-visit identifier on each forward or return handoff.
   Return notices use that identifier rather than the displayed turn number, so a
   restored turn is announced once and snapshots/reloads do not repeat it.
7. Log an explicit public return with the recipient and restored turn/phase. Keep
   normal revision, idempotency and transaction ownership. Rejected and duplicate
   requests cannot consume additional history or emit duplicate logs.

### Non-functional requirements

- Server-owned, bounded history; no hidden identities in checkpoints or views.
- JSON persistence and restart continuity; old snapshots start with empty history.
- Accessible button and useful rejection errors in both formats.

## Contracts and data

- New command: GIVE_BACK_TURN, empty data object; client supplies no recipient,
  phase or turn override. Extra client fields cannot override server checkpoints.
- Persist a turnTracking object with visit (initially zero) and history entries
  containing active, turn and phase. Initialization applies to missing old fields.
- Views expose turnVisit (nonnegative safe integer) and canGiveBackTurn (viewer-specific
  boolean), not the stored history. Counter exhaustion rejects before mutation.
- Frontend decoding accepts omitted fields for older servers; availability defaults
  operationally to false. Legacy notification behavior remains for views lacking
  turnVisit; new visit notices use a separate storage namespace.

## Technical constraints

RoomStore retains locking, command deduplication and transaction boundaries. Lifecycle
handling owns turn transitions; view mapping exposes eligibility without mutating Game.

## Invariants

- Only the active player can return a turn; the host has no override.
- Returning changes turn tracking, revision and the public log only.
- No private access changes or automatic replay of a gesture/request on reconnect.

## Edge cases and failures

First turn, history retention exhaustion, conceded predecessors, active concession,
Cleanup rollover, same-seat handoffs, stale/duplicate/conflicting commands, invalid
saved metadata, safe-integer exhaustion, legacy snapshots, reload/restart and blocked
session storage. Persistence failure rolls back state and command records.

## Migration and compatibility

Additive persisted/view fields and command. Old snapshots initialize history lazily
with no reconstruction from logs. Give back stays unavailable until a new handoff.
Deploy backend support before frontend; older views keep the button disabled. No
SQL migration or routine version bump. Existing forward-turn behavior is preserved.

## Acceptance criteria

- [x] Both formats return to the actual previous saved seat/phase/turn.
- [x] Repeated returns consume history without changing unrelated gameplay state.
- [x] Permissions, concession rules, retention, persistence and retries are correct.
- [x] Returned-turn notices announce once and UI availability follows server state.
- [x] Existing phase/end-turn, privacy and browser workflows remain correct.

## Verification

User confirmed **comprehensive coverage** on 2026-10-19. Before implementation,
explain tests and confirm meaningful red failures. Cover both formats, every handoff
source, history traversal/bounds, restored fields, gameplay preservation, validation
order/atomic rejection, concessions, legacy JSON, safe-integer boundaries, notification
identity, idempotency, rollback/restart and browser guards/recovery/reload. Retain
existing suites; run backend verification, frontend units, lint/types/format/build,
analysis, browser tests, specification validation and whitespace checks. Record results.

## Architecture decisions

[ADR-0022](../docs/adr/0022-turn-return-checkpoints.md) records server-owned checkpoints
and a monotonic visit identifier versus client-derived seats or full-game undo.
Existing [ADR-0001](../docs/adr/0001-authoritative-game-state.md) retains authority,
privacy and transactional persistence; [ADR-0020](../docs/adr/0020-table-interaction-contracts.md)
retains active-player-only turn control.

## Open questions

None.

### Execution evidence

- Meaningful red runs demonstrated missing return dispatch/view metadata, notification
  identity on rewound turns, missing UI controls, and invalid-checkpoint/counter guards.
- Targeted backend tests: 60 passed, including both formats, history bounds, JSON
  compatibility, restart recovery, duplicate/conflicting requests and rollback.
- Frontend: 225 unit tests and 98 browser tests passed. Browser coverage includes
  both formats, pending/rejection behavior, legacy views, blocked storage, reload
  deduplication and a real two-player backend-restart/return flow.
- Frontend lint, TypeScript and formatting checks passed. Analysis completed with
  seven advisory ESLint warnings and zero Knip issues. Java Spotless check passed.
- The first full backend run found an old exact-field assertion missing the two
  approved view fields (704 passed, one failed). The assertion was updated while
  retaining privacy and no-mutation checks; final verification is recorded below.
- Final full `./scripts/build.sh`: passed, with 705 Java tests, frontend analysis
  and compilation, packaged application, PMD log and JaCoCo reports generated.
- Specification validation: 41 specifications passed offline validation; targeted
  documentation formatting and `git diff --check` passed. No deployment performed.
