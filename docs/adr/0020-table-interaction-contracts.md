# ADR-0020: Server-owned turn advancement and shared card gestures

- Status: Accepted
- Date: 2026-10-19
- Related specification: [Frequent table interactions](../../spec/table-interactions.md)

## Context

Playtest feedback requested accessible turn controls, faster taps and battlefield
movement. The user explicitly chose active-player-only advancement without a host
override, manual untap/draw, comprehensive verification and this ADR. Current PHASE
allows the host, and a client phase loop would require several acknowledged mutations
and could stop halfway through a turn. Card gestures share filtered snapshots and
existing permission rules.

## Decision

Enforce active-player-only PHASE and introduce one atomic END_TURN command in the
existing lifecycle handler/facade/RoomStore transaction path. Use the same next-turn
transition for Cleanup. Keep untap/draw and stack resolution manual. Reuse TAP,
UNTAP_ALL and MOVE for gestures, with explicit props, live client action gating,
and current-snapshot drop validation. Keep the server authoritative and the inspector
available; do not introduce new global state or a client rules engine.

## Alternatives considered

### Client-issued phase loops

- Benefits: No new command type.
- Drawbacks: Several revisions, race/conflict opportunities and partial advancement.
- Reason not selected: End turn is one user intent requiring one committed action.

### Host override or shared advancement authority

- Benefits: Host can advance for another player.
- Drawbacks: Allows unrequested control of another player's turn.
- Reason not selected: User explicitly chose active-player-only authority.

### Separate commands for every input gesture

- Benefits: Gesture-specific server naming.
- Drawbacks: Duplicates existing permissions and physical movement behavior.
- Reason not selected: Existing commands already express tap, untap and move intent.

## Rationale

An atomic command preserves revision/idempotency and transaction guarantees while
client controls make the approved authority discoverable. Shared gesture validation
adds convenience without extending hidden information or bypassing server rules.

## Consequences

### Benefits

- Predictable single-action turn advancement and consistent client/server permissions.
- Existing physical action semantics and privacy remain reusable.

### Drawbacks and risks

- Old clients may display an enabled host button that the updated server rejects.
- Double-click and drag must coexist with inspection and pending updates.

### Operational impact

No SQL migration or dependency changes. Deploy matching client/backend versions;
publication and deployment still require separate authorization. Mark the narrowed
turn authority explicitly in PR/release review.

## Follow-up actions

- [x] Verify both formats, persistence/idempotency and browser interaction boundaries.

## References

- [Playtest backlog](../playtest-backlog.md)
- [Game engine boundaries](0005-game-engine-component-boundaries.md)
- [App composition](0013-app-composition-boundaries.md)
