# Focused battlefield popover

## Metadata

- Status: Completed
- Priority: High
- Priority rationale: Explicit user request to keep the table interactive while enlarging an opponent battlefield.
- Depends on: [Card interaction increment](card-interactions.md), [Card artwork previews](card-artwork-previews.md)

## Summary

Replace the focused opponent battlefield modal with a non-modal native popover in both formats.

## Goals

- Enlarge live opponent cards without making the rest of the table inert.
- Preserve grouping, artwork previews, actions, privacy and the existing session.

## Non-goals

- Changing card details or zone viewers from modals.
- Backend commands, dependencies, game rules or a shared overlay framework.

## Background

The pre-design audit traced OpponentBoards, PlayerArea, Modal, card-action popovers and
artwork previews. No out-of-scope prerequisite refactor was found. Preserve shared
PlayerArea rendering and snapshot ownership; nested dismissal needs browser verification.
The user explicitly requested this replacement and confirmed comprehensive coverage.

## Requirements

### Functional requirements

1. Focus battlefield opens a labelled, native non-modal popover containing the same
   enlarged PlayerArea. The trigger reports expanded state and identifies its surface.
2. Keep the background interactive. An outside click dismisses focus while still
   activating its original target once; do not cancel the outside action or steal its focus.
3. Provide Close and Escape, initial focus on Close, no focus trap and appropriate
   return to the opening trigger on explicit dismissal. Outside dismissal preserves
   the new focus. Opening a different opponent replaces the focused view.
4. Keep nested card menus and artwork previews functional. Escape dismisses the
   innermost open surface first, leaving focus open until a subsequent Escape.
5. Close focus before opening modal card details or a zone viewer. Those destinations
   remain accessible; closing them restores a connected appropriate trigger.
6. Keep the surface within viewport margins, position it near its trigger and
   reposition on viewport changes. Cap its height and scroll its contents on small screens.
7. Render current snapshots, names/life, grouping/lands, active-player indication,
   face-down privacy and edit permissions with no additional session or data fetch.

### Non-functional requirements

- No new dependencies, backend/protocol changes or identity discovery.
- Remove listeners and close native popovers on unmount; support keyboard access.

## Contracts and data

Local focused seat and trigger reference only. Reuse existing card commands and
server-filtered snapshots. Non-modal dialog semantics describe the interactive
popover, with no aria-modal=true or showModal invocation for this view.

## Technical constraints

Use the established native popover API and shared PlayerArea. Keep card-specific
menu keyboard navigation separate from the battlefield container.

## Invariants

- No mutation from merely focusing, closing or resizing a battlefield.
- No connection churn, extra private access or permission overrides.

## Edge cases and failures

Nested Escape, outside clicks on commands, rapid opponent switching, missing or
removed trigger/player, narrow and short viewports, pending actions, finished games,
live hidden-card updates and modal navigation from within nested card actions.

## Migration and compatibility

Compatible frontend refinement. No version or schema bump. Integration into main
must include the prerequisite artwork-preview and card-interaction implementations.

## Acceptance criteria

- [x] Both formats open a non-modal live enlarged battlefield.
- [x] Background actions and dismissal/focus work without duplicate commands.
- [x] Nested menus, previews and modal navigation remain accessible.
- [x] Privacy, live updates, viewport bounds and session identity are preserved.

## Verification

User confirmed **comprehensive coverage** on 2026-10-19. Add behavior-first browser
tests for non-modality, background commands, dismissal and focus, nested controls,
viewport changes, opponent switching/removal and existing live/privacy flows in both
formats. Confirm meaningful red failures before production changes. Run the complete
browser suite, frontend units, lint, formatting, type/build checks, analysis, spec
validation and whitespace checks. Record actual results before completion.

### Executed checks

- The red browser test reached the table and failed because focus lacked native
  popover behavior. Subsequent tests exposed lost focus after modal details;
  restoring the persistent trigger before navigation fixed it.
- The final complete Chromium browser suite passed **117 tests**, including nine
  new focused-battlefield cases and existing nested artwork/menu, privacy,
  Commander/Standard gameplay, reconnect and zone-navigation flows.
- Frontend unit suite: **225 passed**.
- Frontend lint, formatting and production build: passed; the build includes
  TypeScript checking. Targeted Markdown formatting and whitespace checks passed.
- Frontend analysis: seven existing advisory findings, zero errors and zero Knip
  issues. Native Escape behavior avoids a redundant container keyboard handler;
  no rule configuration or suppressions changed.
- Specification validator: **42 specifications validated**.
- Visually inspected the enlarged focused battlefield with active-player styling,
  hidden-card fallback, life/name and unobscured Actions controls.
- Backend code is unchanged; Java unit/PIT campaigns were not rerun. Browser
  verification used Chromium with the existing local test servers.

## Architecture decisions

[ADR-0021](../docs/adr/0021-card-interaction-contracts.md) and
[ADR-0013](../docs/adr/0013-app-composition-boundaries.md) still govern shared rendering
and local presentation state. This user-requested container replacement supersedes
ADR-0021’s focused-modal presentation choice without changing architectural boundaries,
data ownership, dependencies or public contracts; no new ADR is required.

## Open questions

None.
