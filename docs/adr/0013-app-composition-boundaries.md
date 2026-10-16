# ADR-0013: App composition boundaries

- Status: Accepted
- Date: 2026-10-16
- Related specification: [App composition](../../spec/app-composition.md)

## Context

App combines session lifecycle, navigation, forms, overlays, and table rendering. Extraction must preserve state lifetimes and privacy behavior without introducing a second source of game state.

## Decision

Use screen and table-region components with explicit data/callback props. A cohesive session hook owns bootstrap, accepted snapshots, room loading, socket lifetime, and mutations. App retains form values and overlay identifiers. Existing card/editor components move without internal redesign.

## Alternatives considered

### One controller hook

- Benefits: Small root component.
- Drawbacks: Moves unrelated responsibilities into another large module.
- Reason not selected: Does not establish cohesive boundaries.

### Global state/context

- Benefits: Fewer explicit props.
- Drawbacks: Wider coupling and additional ownership/lifetime complexity.
- Reason not selected: Current component depth does not require it.

## Rationale

Composition separates independently changing screen regions while controlled props make permissions and callbacks visible. Root-owned UI state preserves navigation behavior; one session hook keeps transport coordination together.

## Consequences

### Benefits

- Smaller composition functions and explicit state lifetimes.
- Existing protocol and gameplay components remain reusable.

### Drawbacks and risks

- More files and callback props.
- Moving keyed components can accidentally reset state; characterization tests cover these boundaries.

### Operational impact

No deployment or persistence change.

## Follow-up actions

- [x] Verify lifecycle and gameplay regressions after extraction.

## References

- [Validated frontend protocol](0011-validated-frontend-protocol-boundary.md)
