# ADR-0025: Server-owned configurable mulligans

- Status: Accepted
- Date: 2026-10-22
- Related specification: [Configurable mulligans](../../spec/configurable-mulligans.md)

## Context

Opening hands require private repeated redraws, house-rule agreement and durable
progress across multiple clients. START currently admits normal play immediately.

## Decision

Persist server-owned settings and per-player progress. Introduce an explicit
opening stage and atomic actor-owned redraw/keep commands. Host setting changes
in the lobby reset readiness; players choose independently. Reuse zone transitions
and retain RoomStore transaction/revision/idempotency ownership. The last keep
enters normal play; it does not draw or untap automatically.

## Alternatives considered

### Client-only progress

- Benefits: fewer server changes.
- Drawbacks: cannot authoritatively gate play, recover progress or validate bottoming.
- Reason not selected: weak consistency and reconnect guarantees.

### Mulligans during normal play

- Benefits: preserves the existing startup status.
- Drawbacks: permits premature moves/draws and mixes opening obligations with gameplay.
- Reason not selected: obscures command authority and lifecycle invariants.

### Turn-order opening decisions

- Benefits: closer to formal sequential procedures.
- Drawbacks: serializes decisions and adds waiting/coordinating states.
- Reason not selected: user chose independent choices for the assisted tabletop.

## Rationale

One authoritative persisted stage makes permissions and recovery explicit while
preserving existing transaction and privacy boundaries.

## Consequences

### Benefits

- Atomic bottoming, reconnect continuity and a clear gate for normal gameplay.
- Shared settings with readiness renewed after changes.

### Drawbacks and risks

- START's behavior changes and existing startup fixtures require explicit keeps.
- Every player must keep; disconnected players can stall opening until reconnect or host cancellation.

### Operational impact

Default missing persisted fields without restarting existing playing games.
Deploy frontend/backend together; no SQL migration. Avoid logs containing card identities.

## Follow-up actions

- [x] Verify opening-stage persistence, privacy, rollback and browser recovery.
- [ ] Replay R17 must reset progress and retain agreed settings.

## References

- [ADR-0005](0005-game-engine-component-boundaries.md)
- [ADR-0024](0024-bounded-library-inspections.md)
