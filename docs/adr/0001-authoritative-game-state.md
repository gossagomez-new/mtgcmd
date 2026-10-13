# ADR-0001: Authoritative transactional game state

- Status: Accepted
- Date: 2026-10-12
- Related specification: [Commander tabletop](../../spec/commander-tabletop.md)

## Context

Four browsers must agree on state without receiving opponents' private cards.
Games must survive backend restarts. Initial operation uses one Java process.
The user approved recording this decision on 2026-10-12.

## Decision

A modular Java 25 Spring Boot monolith owns state. PostgreSQL stores full game
snapshots and separate command/action records. Per-room row locks serialize
mutations. State and idempotency records commit atomically before broadcast.
WebSockets carry server-generated, seat-filtered snapshots and revisioned
commands. React owns only transient UI state.

## Alternatives considered

### Client-authoritative replicated state

- Benefits: reduced server responsibilities.
- Drawbacks: clients could receive hidden cards and disagree on concurrent moves.
- Reason not selected: weak privacy and integrity boundaries.

### Event-sourced game state

- Benefits: reconstructable history and replay.
- Drawbacks: event compatibility and projection complexity for a prototype.
- Reason not selected: snapshot recovery meets the requirement with fewer moving parts.

### Distributed room services

- Benefits: independent scaling.
- Drawbacks: routing, distributed presence, and coordination requirements.
- Reason not selected: one-instance prototype does not justify them.

## Rationale

Central authorization and projection put hidden-information enforcement at
one boundary. Database transactions supply durable state and idempotency.
Full snapshots simplify reconnection and stale client recovery for four seats.

## Consequences

### Benefits

- Explicit privacy boundary, testable domain logic, straightforward recovery.

### Drawbacks and risks

- Full snapshots cost bandwidth; single-instance broadcasts constrain scaling.
- Snapshot schema migrations will be needed as saved state evolves.

### Operational impact

- PostgreSQL and a persistent volume are required. Backup database before upgrades.
- Reverse proxy terminates HTTPS and forwards WebSocket upgrade requests.

## Follow-up actions

- [x] Validate privacy and transaction boundaries with automated tests.

## References

- [Specification](../../spec/commander-tabletop.md)
