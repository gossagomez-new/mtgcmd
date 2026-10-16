# ADR-0011: Validated frontend protocol boundary

- Status: Accepted
- Date: 2026-10-16
- Related specification: [Validated frontend protocol](../../spec/validated-frontend-protocol.md)

## Context

HTTP JSON and WebSocket data currently enter React state without runtime checks.
Separate handwritten interfaces do not validate external values and already
disagree with nullable backend fields. Large nested snapshots need reusable
validation across transports.

## Decision

Use Zod schemas as the source of frontend protocol types. Decode unknown input
at HTTP/WebSocket boundaries; expose cohesive endpoint functions and a socket
message dispatcher to React. Validate whole message variants before effects.
Retain hook lifecycle and server-owned privacy/game policy.

Use HttpError for status-bearing failures and ProtocolError for safe invalid-data
failures. Preserve network/abort errors. Do not include payloads in diagnostics
or automatically retry failed mutations.

## Alternatives considered

### Handwritten validators plus interfaces

- Benefits: No added runtime dependency and explicit control.
- Drawbacks: Repetitive nested checking and duplicated definitions prone to drift.
- Reason not selected: Snapshot schemas serve both transports and inferred types.

### Type assertions or generic response helpers

- Benefits: Minimal code and no validation overhead.
- Drawbacks: Do not check external values or prevent partial invalid state updates.
- Reason not selected: They reproduce the current unsafe boundary.

### Generate all frontend types from Java

- Benefits: Could reduce cross-language contract drift.
- Drawbacks: New build/code-generation convention; compile-time types alone still
  do not validate incoming JSON.
- Reason not selected: Larger scope than the approved frontend boundary.

## Rationale

One schema source avoids internal type/validator duplication. Endpoint adapters
hide HTTP details from App; discriminated messages make valid effects explicit
without introducing a class hierarchy or a handler framework.

## Consequences

### Benefits

- Validated data before UI effects and shared transport contract checks.
- Safe protocol failures and clearer HTTP status/recovery handling.

### Drawbacks and risks

- Runtime bundle/validation cost and dependency maintenance.
- Schemas must still be checked against real backend payloads; frontend checks
  do not replace server authorization or privacy filtering.

### Operational impact

No migration or endpoint changes. Install dependencies and verify both game formats
and reconnect behavior. Invalid payload handling becomes stricter; preserve the
last valid view and never infer whether a rejected response committed a command.

## Follow-up actions

- [ ] Review remaining UI complexity and test-only parsing findings separately.

## References

- [Zod usage](https://zod.dev/basics)
- [API contract](../API.md)
- [Authoritative state](0001-authoritative-game-state.md)
