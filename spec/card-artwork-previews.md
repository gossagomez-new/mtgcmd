# Card artwork previews

## Metadata

- Status: Completed
- Priority: High
- Priority rationale: Complete R23’s requested card inspection shortcut without disrupting table interaction.
- Depends on: [Card interaction increment](card-interactions.md)

## Summary

Deliver R23 for Commander and Standard: enlarged artwork on pointer hover and keyboard focus.

## Goals

- Preview the currently displayed, server-permitted artwork without opening details.
- Preserve all existing card gestures and privacy.

## Non-goals

- Give back turn (R20), casting, mulligans, new server commands or image discovery.
- Interactive tooltip controls or changes to the details dialog.

## Background

CardButton already selects displayed face/name/image fallbacks from the current snapshot.
Action popovers and modal details are established in [ADR-0021](../docs/adr/0021-card-interaction-contracts.md).
The pre-design audit found no out-of-scope prerequisites. Reuse the existing presentation
result and preserve gesture ownership rather than duplicate identity selection or change commands.

## Requirements

### Functional requirements

1. Hover or keyboard-focus the inspect card button for 350 ms to show a named artwork tooltip.
2. Use displayed-face image/name, falling back to the definition image/name exactly as the card does.
3. Keep the tooltip open while the pointer is over the card or tooltip, or the card retains focus.
   Allow a 200 ms pointer transit delay; Escape dismisses until a new hover/focus interaction.
4. Hide immediately on click, context menu, drag start, opening actions/details, card removal,
   or loss of permitted artwork. Cancel pending opening as well as visible previews.
5. Reflect face/artwork updates from current snapshots. Cards without permitted artwork show no tooltip.
6. Display a larger image beside the card, preferring the right then left, constrained to an
   eight-pixel viewport margin. Reposition on scroll/resize; never cover the source card
   or its Actions button.
   If neither side has enough room, use space above/below; omit preview when no non-overlapping
   enlarged image fits. Cap image dimensions at 280 by 392 pixels and preserve card proportions.

### Non-functional requirements

- Accessible tooltip name and trigger description, Escape support, no focus stealing/trapping.
- No new identity fetches, commands, dependencies or backend changes.
- Preview is not rotated with a tapped card and does not intercept dragging from the card.

## Contracts and data

Reuse CardButton’s current name/image presentation. Preview lifetime is local UI state;
no persisted data, protocol fields or retries. Native manual popover supports the existing
modal top layer without making the preview modal or replacing the action popover.

## Technical constraints

Keep preview timing/placement cleanup cohesive and separate from command dispatch.
Preserve delayed inspection, double-click tap, native drag payload and action menu focus.
Follow anchor geometry on animation frames only while a preview is visible, including
CSS tap rotation, scrolling and resizing; cancel the frame callback on dismissal/unmount.

## Invariants

- Server-filtered definitions are the sole identity source, including authorized face-down cards.
- No extra mutation commands or automatic actions.

## Edge cases and failures

Missing definitions/images, empty face image, invalid face index, snapshot permission loss,
rapid enter/leave, Escape before the delay, unmount, viewport edges and narrow screens,
modal focus, pointer travel and image load failure. A failed image hides its preview until
artwork changes or a new interaction; it does not infer alternate identities.

## Migration and compatibility

Frontend-only compatible feature; no schema migration or version bump. Depends on the
card-interaction increment (PR #19). While that PR is open, publish a dependent PR
against its branch; integrate the merged parent and retarget main before merging.

## Acceptance criteria

- [x] Both formats show current enlarged permitted artwork on hover and focus.
- [x] Hidden or unavailable artwork never appears; snapshot updates and removal are respected.
- [x] Preview is dismissible, hoverable, viewport-constrained and does not cover its source.
- [x] Existing inspection, tap, drag, menu and focused-view workflows remain correct.

## Verification

User confirmed **comprehensive coverage** on 2026-10-19. Browser tests cover both formats,
face/definition fallbacks, hidden/missing art, authorized face-down cards, live updates,
viewport edges, hover delay and cancellation, pointer transfer, keyboard/Escape, removal,
image failure, inspection/menu/drag/tap conflicts and focused-view behavior. Retain the
existing browser suite. Run frontend unit tests, lint, type checks, formatting, production
frontend build, specification validation and whitespace checks. Record actual outcomes.

### Executed checks

- Red tests confirmed the absent tooltip and placement behavior, parent-dialog Escape
  propagation, snapshot-rotation overlap and below-card Actions-button overlap before fixes.
- `npm --prefix frontend test`: **225 passed**, including four placement tests.
- A complete Chromium browser run passed **106 tests**. After the final placement
  correction to reserve the Actions button, reran the four affected browser files:
  **65 passed**, including all **20** new preview scenarios and existing card, menu,
  tap, drag and focused-view scenarios. The complete suite was not rerun after that correction.
- `npm --prefix frontend run lint`, `run format:check` and `run build`: passed;
  the production build includes TypeScript checks.
- `npm --prefix frontend run analyze`: seven existing ESLint advisories,
  zero errors and zero Knip issues; this increment adds no advisory findings.
- Specification validation, targeted Markdown formatting and `git diff --check`: passed.
- Java production code is unchanged; backend unit/PIT campaigns were not rerun.
  Browser execution used Chromium and the existing test servers.

## Architecture decisions

Existing [ADR-0021](../docs/adr/0021-card-interaction-contracts.md) and
[ADR-0013](../docs/adr/0013-app-composition-boundaries.md) apply. No new long-lived dependency,
server contract or architectural convention; no new ADR required.

## Open questions

None.
