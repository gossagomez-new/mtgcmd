# Zone viewer keyboard lifecycle

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: TD07 fixes keyboard focus and dismissal in the existing zone-viewer modal.
- Depends on: [App composition](app-composition.md), [Card and library UX](tabletop-card-and-library-ux.md)

## Summary

Deliver TD07 from the [technical debt backlog](../docs/technical-debt-backlog.md)
by reusing the shared native Modal for ZoneViewer.

## Goals

- Provide initial focus, containment, Escape/Close dismissal and focus restoration.
- Preserve private search, filtering, card interactions and nested detail behavior.

## Non-goals

- Changing focused battlefields or card-action menus from their non-modal popovers.
- Backend commands, search authority or privacy changes; generic overlay redesign.

## Background

The pre-design audit confirmed ZoneViewer uses a styled section with dialog role
but no native modal or keyboard lifecycle. Modal already supplies showModal,
cancel handling and trigger restoration, and Inspector uses it. Reuse is within
TD07's approved scope. Nested dialogs/popovers and CSS precedence need verification.

## Requirements

### Functional requirements

1. Open ZoneViewer as a native modal dialog with its existing accessible name.
2. Initially focus the visible Close zone viewer control. Tab/Shift+Tab cannot reach background controls, which remain inert; native browser-chrome traversal is permitted.
3. Escape and visible Close use the same existing close callback. Library dismissal submits CLOSE_SEARCH once; other zones do not submit it. Preserve pending/disconnected submission rules without queueing or replaying commands.
4. Capture the opening control before SEARCH can disable it. Restore focus if it remains connected and available; defer restoration while CLOSE_SEARCH keeps it disabled, and do not steal focus if the user moves elsewhere. Nested card details close back into the viewer without closing it or losing its filter. Escape dismisses the topmost card menu/artwork/details before the underlying viewer.
5. Preserve filtering, original positions, hidden definitions, snapshot updates, empty results, editable/disabled rules, library order and connection lifetime.
6. Keep the viewer within narrow viewports with scrollable content. Backdrop clicks do not introduce a new dismissal action.

### Non-functional requirements

- Preserve both formats, server authority, revisions, retry and private-card protections.
- Avoid custom focus-trap machinery when the existing native Modal is sufficient.

## Contracts and data

No wire or persisted-state changes. App retains a local opening-control reference for focus recovery. ZoneViewer keeps its props and keyed lifetime.
Shared Modal retains Inspector's current lifecycle.

## Technical constraints

Reuse Modal, React state, existing browser fixtures and server commands.
FocusedBattlefieldPopover and CardActionPopover remain non-modal popovers.

## Invariants

- Local filtering does not change library order or reveal new identities.
- Nested inspection does not close an active private search.

## Edge cases and failures

Empty zones/results, removed triggers/cards, snapshot updates, pending/disconnected
commands, nested dialogs, menus and artwork previews, and small viewports.

## Migration and compatibility

No data migration or version bump. Native dialog replaces the visual-only wrapper;
update structural test selectors while retaining observable behavior assertions.

## Acceptance criteria

- [x] Modal focus and dismissal work in both formats.
- [x] Nested interactions, search state, privacy and action guards remain correct.
- [x] Focused battlefields and action menus remain non-modal popovers.
- [x] Relevant frontend/browser and documentation checks pass.

## Verification

User confirmed comprehensive coverage on 2026-10-22: both formats, focus/Tab,
Escape/Close/restoration, nested details/actions/artwork, search closure once,
filters/positions, snapshots and pending/disconnected behavior. Add failing browser
behavior tests before production edits; run frontend and full browser profiles.

### Executed verification (2026-10-22)

- Behavioral red: the original viewer failed initial-focus assertions. After native
  modal reuse, the library test exposed failed restoration while CLOSE_SEARCH disabled
  its trigger; preserving the opener and deferring restoration resolved that failure.
- `./scripts/verify.sh --task zone-viewer-keyboard frontend` passed 240 unit cases,
  41 analyzer fixtures, lint, formatting, analysis and TypeScript/production build.
  Eight advisory warnings and zero Knip issues remain; no rules were weakened.
- `./scripts/verify.sh --task zone-viewer-keyboard browser` passed all 182 cases,
  including 12 new zone-viewer cases, existing focused-battlefield popover tests,
  real Commander/Standard multiplayer flows and private search/filter coverage.
- New cases cover modal focus and background inertness, Escape/Close/restoration,
  nested details/menu/artwork, snapshot-preserved filtering and positions, hidden
  definitions, narrow viewports, pending/disconnected commands, and deferred focus
  without stealing later user focus. Pending search dismissal preserves the existing
  no-queue behavior rather than inventing a CLOSE_SEARCH retry.
- Evidence manifests: `/tmp/mtgcmd-verify-zone-viewer-keyboard-3x7f740p/manifest.json`
  and `/tmp/mtgcmd-verify-zone-viewer-keyboard-e3fte80w/manifest.json`. They describe
  executable source before these final documentation completion edits.
- Specification/graph, Markdown formatting and whitespace checks passed. No separate
  backend unit/PIT run or deployment was performed for unchanged backend code;
  browser verification started the real backend.

## Architecture decisions

No new ADR required: reuse of the established shared modal is a reversible local
change within [ADR-0013](../docs/adr/0013-app-composition-boundaries.md).
The user explicitly confirmed focused battlefields remain popovers.

## Open questions

None.
