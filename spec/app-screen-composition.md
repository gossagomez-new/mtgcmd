# App screen composition refinement

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: Separate remaining screen routing and action policy from root state ownership.
- Depends on: [App composition](app-composition.md), [Shared browser fixtures](shared-browser-fixtures.md)

## Summary

Extract a stateless screen selector, notification rendering, and pure action-availability
policy while retaining App-owned forms, overlay identifiers, and session lifetime.

## Goals

- Remove remaining App complexity and length warnings through cohesive extraction.
- Preserve screen, permission, notification, and state-lifetime behavior.

## Non-goals

- New state ownership, context, dependencies, session or protocol changes.
- Redesign overlay coordination or alter available commands.

## Background

App currently has complexity 15 (limit 10) and length 111 (limit 80). The pre-design
review identifies existing form/overlay ownership and stable navigation callbacks as
required invariants, with no additional remediation scope.

## Requirements

1. Select welcome without a view, lobby only for lobby status, and PlayingTable
   for every other status including finished and unknown non-lobby values.
2. Keep action disabled when disconnected, pending, busy, or finished; gameplay
   additionally requires that the current player has not conceded. Preserve the
   existing nullable-view and absent-current-player results.
3. Keep conceded-host finish availability separate from gameplay availability.
4. Keep error notices after the header and before the selected screen. Dismissal
   calls the existing fail callback with an empty message. Finished notices remain
   after the screen and before overlays, with unchanged text/classes/accessibility.
5. Retain form values, selected card ID, viewer selection, and the single session
   hook in App. Screen selector must have stable module-level component identity.
6. Keep callbacks, screen prop values, DOM wrappers, and library SEARCH-before-open
   and CLOSE_SEARCH-before-clear ordering unchanged.
7. Preserve zone-viewer room/seat/zone keys, selected-card lookup from latest view,
   navigation cleanup, and connection stability across local edits and snapshots.

## Contracts and data

Existing component props, protocol types, and action payloads remain unchanged.
The pure availability helper is internal frontend code; no new external API.

## Technical constraints

Use explicit screen data/callback props; no entire session object passed to screen
components, large controller hook, or new global state. Keep notification and screen
helpers module-level. Preserve existing disabled/canPlay distinction.

## Invariants

- No local UI edit reconnects the socket or bootstraps a new guest session.
- App remains the owner of forms and overlay selection.
- Server-filtered privacy and authorization remain unchanged.

## Edge cases and failures

No view, absent current player, conceded host, finished and other non-lobby statuses,
concurrent error and finish notices, pending/busy/disconnected combinations, missing
selected card, navigation away and back, and malformed server updates.

## Migration and compatibility

No migration, persistence, or deployment change.

## Acceptance criteria

- [x] Characterization tests pass before and after extraction.
- [x] Exhaustive availability tests preserve existing boolean results.
- [x] No App or extracted-helper warnings and no new analysis findings.

## Verification

User confirmed comprehensive coverage: screen selection, action-availability matrix,
banner behavior, form/navigation persistence, current-card and library-search lifetimes,
and connection stability. Add browser characterizations against unchanged App and
confirm green; add pure-policy unit tests before implementing the extracted policy.
Run existing composition/protocol/deck-import/inspector/tabletop browser flows plus
frontend unit tests, typecheck, lint, formatting, production build, static analysis,
and specification validation. Record actual results and limitations.

### Executed checks

- Nine browser characterization/lifecycle tests passed against unchanged App after
  correcting a fixture that attempted to click a banner behind an open modal.
- New policy tests demonstrated expected failures against the stub (139 failed;
  all original 66 tests and six coincidentally matching new cases remained green).
  After copying the existing policy, all 211 unit tests passed, including 145 policy
  matrix/edge cases. They passed again after App extraction.
- Full browser suite after extraction: all 43 tests passed, including both formats,
  notifications, overlay/search lifetimes, navigation and form persistence, connection
  stability, canceled requests, protocol failures, import flows, and real-server gameplay.
- Typecheck, lint, frontend formatting, production build, specification validation,
  and diff whitespace checks passed.
- Current frontend analysis: four existing production warnings, no test warnings,
  zero errors, zero Knip issues. Both App warnings disappeared and no helper is
  flagged. This main-based branch still includes the CardButton warning addressed
  separately by open PR #11; its changes are not duplicated here.
- Backend-only tests were not rerun; real-server browser scenarios started the
  existing backend test server. No dependencies or analysis policies changed.

## Architecture decisions

[ADR-0013](../docs/adr/0013-app-composition-boundaries.md) already defines root state,
explicit props, and session ownership. This refinement adds no new ownership convention.

## Open questions

None.
