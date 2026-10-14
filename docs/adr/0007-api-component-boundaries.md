# ADR-0007: API component boundaries

- Status: Accepted
- Date: 2026-10-14
- Related specification: [API controller decomposition](../../spec/api-controller-decomposition.md)

## Context

ApiController mixes endpoint groups, deck workflow, SQL/JSON persistence, broadcasts,
and HTTP exception translation. Six collaborators and 22 coupled types combine
independently changing responsibilities. The user approved decomposition and this ADR.

## Decision

Use SessionController, RoomController, and DeckController for HTTP translation.
DeckImportService coordinates deck operations; DeckDraftRepository owns draft SQL
and JSON. ApiExceptionHandler scopes advice to those controllers. Keep RoomStore
transaction/idempotency ownership, HTTP contracts, validation order, guest-scoped
reusable drafts, and the original HTTP diagnostic logger category.

## Alternatives considered

### Split controllers only

- Benefits: Smaller initial change.
- Drawbacks: Deck controller retains SQL, serialization, and workflow decisions.
- Reason not selected: Does not isolate the strongest persistence boundary.

### Broader transaction/service redesign

- Benefits: Could unify workflow transactions or draft consumption.
- Drawbacks: Changes failure timing, retry behavior, and persistence semantics.
- Reason not selected: Exceeds the behavior-preserving scope.

### Controller inheritance or class per endpoint

- Benefits: Shared methods or very small controller classes.
- Drawbacks: Hides dependencies or fragments cohesive endpoints.
- Reason not selected: Three endpoint groups and scoped advice provide enough separation.

## Rationale

HTTP, workflow, persistence, and error policies change independently. Explicit
composition makes those dependencies reviewable while avoiding a larger framework.
Scoped advice prevents accidental changes to unrelated controllers.

## Consequences

### Benefits

- Focused controllers and independently testable workflow/persistence.
- Shared error behavior with explicit scope and stable logging.

### Drawbacks and risks

- More files and bean composition.
- Advice registration and operation ordering require characterization tests.

### Operational impact

No route/schema/dependency changes. Preserve draft expiry comparisons, partial purge
behavior, RoomStore transactions, and successful mutation broadcasts.

## Follow-up actions

- Implemented and verified the linked specification.

## References

- [Authoritative state](0001-authoritative-game-state.md)
- [Format profiles](0002-room-format-profiles.md)
