# ADR-0005: Game-engine component boundaries

- Status: Accepted
- Date: 2026-10-14
- Related specification: [Game-engine decomposition](../../spec/game-engine-decomposition.md)

## Context

GameEngine combines command execution, private access, player projections, and
zone transitions. Its central method and transition logic have high complexity;
changes to one action require reviewing unrelated gameplay/privacy behavior.
Persisted commands already execute in RoomStore transactions.

## Decision

Retain GameEngine as the public facade and common validation/bookkeeping boundary.
Extract GameAccess for shared policy, GameViewMapper for projections, and
CardZoneTransitions for physical movement/shuffle/rekey. Group command bodies by
lifecycle, library/access, permanents, objects/stack, and counters. Register method
references through a small CommandHandler contract returning existing descriptions.

Keep command strings, mutable snapshots, public facade entry points, phase/zone
sequences, and transaction ownership unchanged. Shared input/lookup helpers support
consistent validation; handlers do not own revision/log advancement. Components
remain internal to the game package and use existing dependencies/randomness.

## Alternatives considered

### Private-method-only extraction

- Benefits: fewer files and smaller initial edits.
- Drawbacks: retains the shared class dependency surface and a large command switch.
- Reason not selected: projection, access, transitions, and actions change independently.

### Class per command or pluggable handler framework

- Benefits: fine-grained extensibility and registration.
- Drawbacks: many small types/configuration points for a fixed command vocabulary.
- Reason not selected: grouped methods and functional dispatch are sufficient.

### State hierarchy or rich persistent aggregate redesign

- Benefits: potentially stronger lifecycle encapsulation.
- Drawbacks: expands scope into model/schema and admission semantics.
- Reason not selected: this refactor must preserve existing behavior and persistence.

## Rationale

The facade preserves caller expectations while cohesive collaborators localize
changes. Shared access policy avoids divergence between UI affordances and server
authorization. Explicit dispatch keeps existing command vocabulary inspectable.

## Consequences

### Benefits

- Independent policy/projection/transition tests and clearer command code.
- Consistent privacy policy and centralized command bookkeeping.
- Stable REST/WebSocket and saved-game contracts.

### Drawbacks and risks

- More files, imports, and composition points.
- Validation/transition ordering requires characterization coverage.
- Group sizes should be reviewed as future commands are added.

### Operational impact

No migration, new dependency, or deployment change. RoomStore retains atomic
persistence and command idempotency; extracted operations mutate supplied snapshots.

## Follow-up actions

- Review component-level complexity when adding commands.

## References

- [State ownership ADR](0001-authoritative-game-state.md)
- [Format profiles ADR](0002-room-format-profiles.md)
