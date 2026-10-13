# Tapped cards and private library filtering

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: Correct tapped-card orientation and make private library searches usable.
- Depends on: [Commander tabletop](commander-tabletop.md)

## Summary

Rotate tapped cards 90 degrees relative to their standard orientation. Add a
local name filter to the private library viewer while retaining original
positions, card actions, and server-managed visibility.

## Goals

- Standard 90-degree tap orientation in owner and opponent views, including hover.
- Accessible name search and clear control in library viewers.
- Filtering that preserves ordering, positions, and privacy.

## Non-goals

- Backend search, new visibility permissions, advanced query syntax, or searching
  card rules/type text.
- Changes to catalog import work already in the working tree.

## Background

The focused design audit found a 12-degree tapped transform in both ordinary
and hover CSS rules. The zone modal currently renders all visible cards without
filtering. Both issues are local to the frontend; no architectural prerequisite
or unrelated cleanup is required. Existing server-filtered snapshots and SEARCH /
CLOSE_SEARCH commands remain the visibility boundary.

## Requirements

### Functional requirements

1. A tapped card rotates clockwise 90 degrees; hover retains that angle, and
   untapping restores its standard angle. Apply consistently to shared card buttons.
2. Library viewers include a labeled search box, clear button, and result count.
3. Match case-insensitive substrings of canonical card names or individual face
   names. Trim surrounding query whitespace; an empty query shows every visible card.
4. Preserve each card's original position number in the current library view
   rather than renumbering filtered matches.
5. Render distinct empty-zone and no-match messages; retain inspection and moves.
6. Reset the query on close/reopen, changing zones, or switching rooms/players.
7. Recompute results from current visible snapshot cards after moves, shuffles,
   or permission changes. Do not retain a separate private-card cache.

### Non-functional requirements

- Filtering does not send commands, fetch cards, mutate arrays, or advance revision.
- Only server-visible definitions participate in matching; hidden cards have
  only their existing face-down display name.
- Use existing React, TypeScript, and browser-test dependencies.

## Contracts and data

No public API or protocol changes. The query belongs to the viewer session.
Display positions are derived from the unfiltered list for the selected seat
and zone. All existing zone-opening and closing commands remain unchanged.

## Technical constraints

Keep presentation changes in the frontend. Isolate the zone viewer's local query
state without expanding the App component's state management. Preserve existing
card actions, CSS transitions, server permissions, and library order.

## Invariants

- Filtering reveals no cards outside the current authorized snapshot.
- Matching results retain snapshot order and original library position numbers.
- Hover does not override tap orientation.

## Edge cases and failures

- Blank query restores all currently visible cards.
- Nonmatching query shows a no-match message without implying an empty library.
- Loss of visibility removes matching cards even while a query is active.
- Other zones retain their existing viewer behavior without a search control.

## Migration and compatibility

No migrations, new dependencies, or backend contracts. Existing catalog changes
remain separate. No deployment or application restart is performed automatically.

## Acceptance criteria

- [x] Tap/hover/untap renders the expected angle for owner and opponent.
- [x] Library filtering and clearing work with case/whitespace normalization.
- [x] Positions and actions stay associated with the original cards.
- [x] Query resets and no-results/empty-zone states are clear.
- [x] Filtering preserves revision and privacy permissions.

## Verification

The user selected **risk-focused coverage**. Before implementation, add browser
regressions for tap/hover/untap and private library filtering. Confirm the old
angle and missing search box cause the expected failures; unrelated fallback
behavior continues to pass. Cover filtering, clearing, position numbers,
inspection, query reset, snapshot updates, and privacy without adding exhaustive
format combinations. Run frontend tests, type checks, lint, formatting checks,
build, and the browser suite.

Results:

- Red phase: computed rotation was 12 degrees instead of 90; the library
  regression could not find its search control. Test setup and selector errors
  were corrected separately before accepting behavior failures.
- Browser suite: all three scenarios passed, including owner/opponent tap and
  hover, library filtering/clearing, original positions, inspection, query reset,
  unchanged revision/card order, and visibility removal after permission revocation.
- Frontend unit suite: both existing protocol tests passed.
- Type checking, ESLint, Prettier checks, and the frontend production build passed.
- Browser tests required execution outside the sandbox for local servers and
  Chrome. Backend tests were not rerun because backend code was not changed in
  this task; existing catalog changes were verified in their own specification.
- `git diff --check` passed.

## Architecture decisions

No new ADR: local frontend behavior with existing communication and visibility
boundaries. Related architecture:
[ADR-0001](../docs/adr/0001-authoritative-game-state.md).

## Open questions

None.
