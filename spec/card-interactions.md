# Card interaction increment

## Metadata

- Status: Completed
- Priority: High
- Priority rationale: Deliver the second playtest increment without duplicating card permissions or commands.
- Depends on: [Frequent table interactions](table-interactions.md), [Inspector composition](inspector-composition.md)

## Summary

Deliver R07, R08, R05, R10, R03, R21 and R22 from the [playtest backlog](../docs/playtest-backlog.md) for Standard and Commander.

## Goals

- Offer a contextual non-modal quick-action popover and a larger modal details dialog instead of the fixed inspector sidebar.
- Make attachment targets and detaching explicit and show the current relationship.
- Select controlled battlefield permanents and tap or untap the selection atomically.
- Enlarge an opponent battlefield without losing the live session, grouping, identity or permissions.
- Announce the start of the viewer's turn visibly and accessibly once per turn, including across reconnects.

## Non-goals

- R19 double-click from hand, R20 giving back turns, R23 hover artwork previews, casting/resolution automation, mulligans, replay, sideboards or deployment.
- Automated card legality, equipment costs, targeting or triggers.
- Global state libraries or changing existing private-card visibility.

## Background

Existing Inspector action groups, server ATTACH validation and battlefield grouping remain authoritative. The first increment added double-click tap and drag; new gestures must preserve those interactions. The pre-design audit identified the need for atomic batch validation and reuse of existing action components; both are approved scope.

## Requirements

### Functional requirements

1. Right-clicking a card or activating its visible Actions button opens its contextual menu in a non-modal popover anchored beside the trigger and constrained to the viewport. The rest of the board remains interactive. Outside clicks dismiss the popover without blocking their target. Keyboard users can activate Actions, navigate menu items with arrows/Home/End, and dismiss with Escape. Escape restores trigger focus when it still exists. Menu actions follow current permissions and pending state; Details remains available for read-only cards.
2. Inspection opens a modal details dialog with larger artwork and the existing detailed controls. Escape and Close dismiss it; focus returns to the triggering control when it still exists. Form state survives ordinary snapshots and card selection changes as established by the Inspector specification. Closing and reopening starts a new form lifetime.
3. Menus cancel deferred pointer inspection. Double-click tapping, ordinary inspection and native dragging retain the first increment's behavior.
4. Attach controls identify the source, current target and available battlefield targets by permitted name and controller. Provide explicit Attach and Detach actions. Show the current target in card details; no hidden definition may be inferred. Reuse server cycle and cleanup validation.
5. Editable battlefield cards have explicit selection controls independent of inspection, tapping and dragging. Show a selected count and Tap selected, Untap selected and Clear selection controls. Both tap actions set a state rather than toggling. Disable submission when empty, disconnected, pending, finished or when any selection is no longer eligible; invalid selections can be cleared.
6. Focus battlefield opens a large modal for an opponent, showing the live identity/life and grouped controlled permanents. Preserve read/edit permissions, card gestures and the existing session. Close returns to the normal board. Do not fetch private data to populate the view. Opening an existing zone viewer from the focused board dismisses the focused modal so the destination remains accessible.
7. Turn notifications use a visible polite live region. Announce the viewer's active turn once per room/seat/turn; phase updates, repeated snapshots, reconnects and page reloads in the same browser session do not duplicate it. Clear the displayed notification when the turn or game ends. Do not request operating-system notification permissions.

8. Highlight the active player’s battlefield panel with a distinct background/border colour and an Active turn label. Apply the same cue to the viewer and opponents, including focused views, using the current server active seat. Update after turn changes/reconnects and clear the cue outside play. Preserve contrast, artwork and other card states. This follow-up scope was explicitly requested by the user during implementation.

### Non-functional requirements

- Modal focus is contained and restored; visible keyboard focus and accessible names remain available.
- No new long-lived dependencies; reuse server-filtered snapshots and existing action payloads.

## Contracts and data

Add `TAP_SELECTED` with `{cardIds: string[], tapped: boolean}`. Require 1–500 unique nonblank identifiers of at most 80 characters and an explicit boolean. Validate the complete payload, every card's edit permission and battlefield zone before changing any card. Invalid input leaves cards, revision and log unchanged. Success sets every selected tapped state, advances revision once and writes one public generic log entry without private card identities. Existing RoomStore revision, persistence and command-id deduplication apply unchanged. Existing TAP, UNTAP_ALL and ATTACH remain compatible.

Selection and focused-view state are local presentation state. Snapshot updates always supply current cards and permissions. No persisted snapshot fields are added. Notification deduplication uses browser-session storage scoped by room and viewer seat, with an in-memory fallback if storage is unavailable.

## Technical constraints

- Keep GameEngine dispatch/bookkeeping and RoomStore transaction ownership.
- Reuse Inspector action groups and PlayerArea/battlefield grouping; do not duplicate permission logic in an alternative view.

## Invariants

- Opponent private card names and definitions remain absent unless the server grants visibility.
- No host override for turn controls, automatic untap/draw, or automatic stack resolution.
- Preserve the session, commander grouping, land section, tap orientation and drag permissions.

## Edge cases and failures

- Batch lists may be empty, duplicated, oversized, malformed or contain stale/foreign/non-battlefield cards; reject atomically.
- Selection can become stale after control or zone changes; never silently apply a subset.
- Open overlays must follow live snapshots and lose mutation controls when permissions change; removed cards close their controls.
- Face-down attachments use generic labels. Self/cyclic attachments remain rejected by the server.
- Focused views and turn announcements must not reconnect the socket or repeat commands.

## Migration and compatibility

Additive WebSocket command; deploy backend support before using batch actions in the new frontend. No SQL or snapshot migration. Existing clients remain supported. No application version bump in this ordinary feature change.

## Acceptance criteria

- [x] Both formats expose accessible contextual actions and modal details without breaking existing gestures.
- [x] Attach and Detach show current relationships and preserve server validation/privacy.
- [x] Tap/untap selection executes atomically, once, with all rejection paths unchanged.
- [x] Focused opponent views track live state and preserve permissions/session identity.
- [x] Active-player colour and label track the server seat in normal and focused views in both formats.
- [x] Turn-start notifications are visible, accessible and deduplicated across ordinary updates and reconnects.

## Verification

User confirmed **comprehensive coverage** on 2026-10-19. Before production changes, run new tests and confirm behavioral failures. Cover both formats; batch payload/permission/zone boundaries, stale revisions and unchanged state on rejection; explicit tap/untap and bookkeeping; menu keyboard/focus behavior; inspection/tap/drag conflicts; details lifetime; attachment target display, detach and server cleanup/cycles; focused-view live state/privacy; selection invalidation; notification deduplication and storage failure. Retain existing unit, backend and browser suites. Run frontend lint/types/format, backend formatting, specification validation and canonical production build. Record actual outcomes and remaining limits below before completion.

### Executed verification

Completed on 2026-10-19:

- Canonical `scripts/build.sh`: passed, including frontend analysis/build, Maven clean verify, **675 Java tests** with zero failures/errors/skips, PMD logging, JaCoCo HTML/XML/CSV reports and executable packaging with the final frontend.
- Frontend unit suite: **221 passed**. Lint, type checks and formatting checks passed.
- Full browser suite: **88 passed**, including real Commander/Standard gameplay, reconnect/restart privacy checks, non-modal popover focus/light dismissal and viewport resizing, focused-view zone navigation and all new interaction cases.
- Backend Spotless apply/check passed. Specification validation passed for **40 specifications**; documentation formatting and `git diff --check` passed.
- Visually reviewed the rendered focused battlefield, enlarged cards, active-player colour/label and hidden-card placeholder. Browser geometry checks verify controls remain outside card artwork.

The red phase demonstrated missing batch dispatch, duplicate identifiers after normalizing whitespace, missing interaction UI, removed-card selection reopening, card-control layout, focused-card enlargement, active colour, inaccessible focused-zone navigation and modal-versus-popover behavior. The existing native-drag scenario now makes both endpoints visible before dragging, preserving actual native gesture/payload assertions; Playwright's automatic centering otherwise moved the source out of view before starting the gesture.

Initial browser startup failures from host temporary-storage exhaustion were environmental, not red-phase evidence. Java checks used an ignored writable temporary directory. An intermediate run affected by frontend hot updates was followed by stable-source regression runs. No build/security/analysis policies were weakened.

Frontend analysis completed with zero errors and no Knip issues. Seven advisory findings remain: existing PlayerArea/PlayerCounters/ZoneViewer size warnings, new presentation-size warnings in App/Inspector/YourBattlefield and PlayerArea complexity (13 versus 10). These are reviewed presentation-composition follow-ups; permissions and mutation policy remain server-owned, existing form lifetimes are retained and no rules were suppressed. The menu focusability finding was corrected and keyboard/browser behavior reverified.

No PIT campaign, hosted CI, deployment or non-Chrome browser run was performed. R19, R20 and R23 remain outside this completed increment.

## Architecture decisions

[ADR-0021](../docs/adr/0021-card-interaction-contracts.md) records atomic batch commands and shared card-action/modal boundaries; creation explicitly approved by the user. [ADR-0020](../docs/adr/0020-table-interaction-contracts.md) remains applicable.

## Open questions

None for the approved increment. R20's phase/turn-history policy remains outside scope.
