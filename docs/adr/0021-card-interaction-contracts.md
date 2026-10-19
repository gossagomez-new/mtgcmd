# ADR-0021: Card interaction contracts

- Status: Accepted
- Date: 2026-10-19
- Related specification: [Card interaction increment](../../spec/card-interactions.md)

## Context

Group tapping requires all-or-nothing mutation. The existing server owns game state and validates each command. New card menus and focused opponent views must preserve existing action behavior, privacy and form lifetimes.

## Decision

Add an explicit TAP_SELECTED command that validates the full selection before mutation, preserving facade bookkeeping and RoomStore transactions. Keep existing single-card commands. Reuse existing Inspector action groups in a modal details dialog and reuse the opponent board in its focused modal. Use a native non-modal popover for the accessible contextual menu, as explicitly requested by the user; keep card details and focused battlefields in native modal dialogs. Keep presentation state local and snapshots authoritative; introduce no global state library or dependency.

## Alternatives considered

### Send multiple TAP commands

Simple client implementation, but requests have independent revisions and can partially succeed. Rejected because it violates the selection's atomicity requirement.

### Separate focused-view and menu action implementations

Allows independent layout tuning, but duplicates permissions, card presentation and command construction. Reuse the existing components and limit the compact menu to established frequent actions.

### New general interaction framework

Could support future gestures, but adds abstraction and migration work before it is needed. Keep small components/hooks and explicit props.

### Modal card-actions menu

Native dialogs provide focus containment, but a modal menu blocks the surrounding table and does not match the user's explicit popover requirement. Reserve modal dialogs for details and focused battlefields; use an anchored, light-dismissable popover for frequent card actions.

## Rationale

A server-side batch boundary matches the requested consistency guarantee. Shared presentation and existing server-filtered snapshots avoid a second permission system, while native modal dialogs provide focus containment for detailed views. Native popovers allow light dismissal and access to the surrounding board without a new UI dependency.

## Consequences

### Benefits

- Atomic explicit tapped state and one revision/log entry per selection.
- Consistent permissions and presentation across normal and focused boards.
- Keyboard-accessible alternatives to right-click and pointer gestures.

### Drawbacks and risks

- Batch payload limits and rejection order become a public command contract.
- Menu/dialog focus and deferred inspection need browser verification.
- Existing Inspector browser tests must accommodate the modal layout.

### Operational impact

Backend must support the additive batch command before the frontend uses it. No persisted schema migration or deployment authorization is implied.

## Follow-up actions

- [x] Verify batch rejection atomicity and shared UI behavior in both formats.

## References

- [ADR-0005](0005-game-engine-component-boundaries.md)
- [ADR-0013](0013-app-composition-boundaries.md)
- [ADR-0020](0020-table-interaction-contracts.md)

## Subsequent presentation refinement

The user explicitly requested replacing the focused battlefield modal with a
non-modal popover. The [focused battlefield specification](../../spec/focused-battlefield-popover.md)
supersedes the focused-modal presentation choice above. Card details and zone
viewers remain modal; shared PlayerArea rendering, authoritative snapshots and
local presentation state remain as decided here. No new dependency, ownership
boundary or server contract is introduced by this refinement.
