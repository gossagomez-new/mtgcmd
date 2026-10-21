# ADR-0022: Turn return checkpoints

- Status: Accepted
- Date: 2026-10-19
- Related specification: [Give back turn](../../spec/give-back-turn.md)

## Context

R20 corrects accidental turn handoffs without undoing draws, moves, counters or
other gameplay. The user chose backward history traversal and restoration of the
previous phase and displayed turn number. Concessions make seat arithmetic unreliable;
reconnecting clients need the same authoritative history. Displayed turn numbers
can now decrease, while notification deduplication needs a stable handoff identity.

## Decision

Persist up to 100 server-owned turn checkpoints containing only outgoing seat,
phase and displayed turn number. Store this history and a monotonic visit counter
in a cohesive turnTracking snapshot object. Forward handoffs capture checkpoints;
GIVE_BACK_TURN consumes the latest eligible checkpoint without recording the abandoned
future. Refuse a conceded predecessor instead of skipping it.

Expose only eligibility and the visit identifier to clients. Keep the existing
RoomStore transaction/idempotency boundary. Deduplicate announcements by visit in
a separate namespace, retaining fallback behavior for older views.

## Alternatives considered

### Client-derived previous seat

- Benefits: Little new persisted data.
- Drawbacks: Cannot recover actual handoffs, phases, concessions or reconnect history.
- Reason not selected: Does not meet authoritative correction requirements.

### Full-game snapshots or generic undo/redo

- Benefits: Can reverse a wider range of actions.
- Drawbacks: Reverses gameplay the user explicitly wants preserved, retains much
  more data, and introduces privacy and branching-history complexity.
- Reason not selected: Unnecessary scope and incompatible behavior.

### Only the last checkpoint or unlimited history

- Benefits: A single checkpoint is simpler; unlimited history permits arbitrary rewind.
- Drawbacks: One checkpoint prevents repeated correction; unlimited retention grows
  throughout long-running rooms.
- Reason not selected: A bounded stack supports repeated returns with predictable storage.

## Rationale

Small checkpoints capture exactly the state the user wants corrected. Server
ownership preserves consistency across clients and persistence. An independent
visit identifier separates announcement identity from the user-visible turn label.

## Consequences

### Benefits

- Actual handoffs, phase restoration and retries behave consistently in both formats.
- Cards, counters and private access remain independent of turn correction.
- Existing rooms gain support without SQL migration or history reconstruction.

### Drawbacks and risks

- Rewind is limited to retained checkpoints and cannot bypass a conceded predecessor.
- All handoff sources must update tracking; notification storage has two compatibility paths.
- The additive view/persistence contract needs regression coverage and backend-first rollout.

### Operational impact

Bounded snapshot growth; no new service, dependency or transaction owner. Missing
old metadata means no eligible history until a new handoff occurs. Merging does not deploy.

## Follow-up actions

- [x] Verify handoff coverage, persistence rollback and notification deduplication.

## References

- [ADR-0001](0001-authoritative-game-state.md)
- [ADR-0020](0020-table-interaction-contracts.md)
