# ADR-0024: Bounded library inspections

- Status: Accepted
- Date: 2026-10-20
- Related specification: [Bounded library actions](../../spec/bounded-library-actions.md)

## Context

R13/R14 require private access to only the current top N cards and a confirmed
scry arrangement. Full search grants whole-library visibility and editing; using
it as a bounded workflow would expose excess information. Concurrent draws,
shuffles, moves, grants and reconnects affect which selection remains valid.
The user approved this decision and comprehensive verification.

## Decision

Persist one server-owned bounded inspection per actor, separately from full search.
The server selects the top cards; only the actor's filtered view receives the
session and inspected definitions. Bounded visibility grants no card-edit permission.
Scry applies a validated exact top/bottom partition atomically under existing
RoomStore ownership. Cancellation preserves order. Central library transitions
invalidate sessions instead of locking the library during an inspection.

Reordering shares shuffle-style identity/reveal/search cleanup while preserving
chosen order. Keep unconfirmed arrangement local; persisted inspection supports
reload without replay. Preserve existing private-access grants and manual tabletop
rules. Use additive protocol metadata with validated frontend decoding.

## Alternatives considered

### Reuse full search or accept initial card IDs from clients

- Benefits: Less new session state.
- Drawbacks: Full search exposes too much; client-selected IDs can bypass top-N bounds.
- Reason not selected: Incompatible with bounded privacy and server authority.

### Client-only inspection state

- Benefits: Less persistence code.
- Drawbacks: Cannot authoritatively resume/validate the selection across reconnects.
- Reason not selected: Weakens consistency and privacy boundaries.

### Lock the library until confirmation

- Benefits: Prevents selection changes while arranging cards.
- Drawbacks: Abandoned sessions block normal table play and require lease/timeouts.
- Reason not selected: Invalidation and retry fit the existing assisted tabletop.

## Rationale

A small persisted session captures exactly the authorized selection. Existing
zone transitions centralize invalidation, while RoomStore already provides atomic
state/revision/log/idempotency behavior. Exact partitions prevent lost/duplicated
cards without implementing a card-rules engine or undo framework.

## Consequences

### Benefits

- Private bounded visibility, safe ordered confirmation and durable reconnect.
- Shared behavior in both formats using existing authority and transaction boundaries.

### Drawbacks and risks

- Every library-changing transition must invalidate sessions.
- Reordering rekeys private library cards and closes searches/reveals consistently.
- Unconfirmed arrangement is not restored after a browser reload.

### Operational impact

Additive snapshot fields; no SQL migration, dependency or deployment. Roll out
backend before frontend. Old snapshots have no bounded sessions.

## Follow-up actions

- [x] Verify privacy, invalidation, atomicity, persistence and browser workflows.

## References

- [ADR-0001](0001-authoritative-game-state.md)
- [ADR-0011](0011-validated-frontend-protocol-boundary.md)
- [Playtest backlog](../playtest-backlog.md)
