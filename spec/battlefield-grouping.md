# Automatic battlefield grouping

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: Make battlefield types easy to scan and place lands below other permanents.
- Depends on: [Commander tabletop](commander-tabletop.md)

## Summary

Derive automatic type groups from visible cards for every player's battlefield.
Preserve order within each group and render lands in a separate lower section.

## Goals

- New permanents appear beside others of their primary type.
- Owner and opponent views share grouping rules.
- Automatic layout accommodates tapping without group overlap.

## Non-goals

- Persistent manual ordering, arbitrary positioning, backend sorting, or rules interpretation.
- Changes to protocol, schema, saved games, permissions, or card actions.

## Background

The pre-design audit identified separate owner/opponent renderers and absolute
position overrides in the owner's renderer. Shared grouping/rendering addresses
both within the approved scope. The user chose primary-type classification,
automatic placement instead of free positioning, and risk-focused verification.

## Requirements

### Functional requirements

1. Use primary-type precedence Land, Creature, Planeswalker, Battle, Artifact,
   Enchantment, Other. A land-containing permanent belongs below; an artifact
   creature belongs with creatures.
2. Use the active face's nonempty type line, falling back to the definition's
   type line. Match whole card-type words before the subtype separator.
3. Face-down cards and cards without visible types belong to Other, even if the
   client knows a private definition.
4. Display nonland groups in fixed precedence order, then a labeled Lands section.
   Label groups and omit empty groups/sections.
5. Preserve snapshot order within groups. Recompute from snapshots after arrivals,
   departures, transformation, control changes, and visibility updates.
6. Share the layout between player views while retaining existing card sizes.
7. Ignore saved coordinates, remove free positioning and its drag affordance,
   and retain dragging playable cards into the battlefield through MOVE.
8. Preserve inspection, tapping, counters, attachments, and existing empty states.

### Non-functional requirements

- No sorting or mutation of the server's card arrays.
- No hidden definition lookup, additional request, or persistent grouping data.
- Reserve enough card space for both standard and 90-degree tapped orientations;
  groups and the land section wrap without overlapping each other.

## Contracts and data

A pure frontend helper takes readonly visible battlefield cards and returns
nonempty groups containing the original card references. A shared renderer uses
those groups in both owner and opponent fields. Backend card ordering, x/y data,
and POSITION support remain unchanged; the automatic frontend ignores coordinates.

## Technical constraints

Use existing React, TypeScript, CSS, Vitest, and Playwright dependencies. Avoid
new backend endpoints, layout libraries, or protocol changes.

## Invariants

- Each input card appears in exactly one group.
- Lands are always below nonlands, including mixed land types.
- Group order is deterministic and within-group order remains stable.
- Face-down grouping never exposes a private type.

## Edge cases and failures

- Missing/unknown type information uses Other; empty battlefields retain their hint.
- Subtypes and partial words must not accidentally classify a permanent.
- Saved manual coordinates cannot bypass grouping.
- Re-dropping an existing battlefield card does not issue POSITION or MOVE.
- Disabled/read-only battlefields do not accept drops.

## Migration and compatibility

No migration. Existing coordinates are retained on the server but ignored in this
UI. Update README playing guidance to describe automatic placement. Existing
users intentionally lose free positioning, as explicitly selected for this feature.

## Acceptance criteria

- [x] Creature, artifact, creature arrivals render creature, creature, artifact.
- [x] Lands occupy the lower section in owner and opponent views.
- [x] Active faces, mixed types, and hidden types use the stated classification.
- [x] Saved coordinates do not bypass grouping; within-group order remains stable.
- [x] Tapping and drag-to-play continue to work without arbitrary positioning.

## Verification

The user selected **risk-focused coverage**. Unit tests cover primary-type
precedence, active faces, unknown/face-down cards, exact type words, stable order,
empty groups, coordinates, and snapshot immutability. Browser tests cover the
reported insertion example, both player views, lands below nonlands, saved
coordinates, drag-to-play, and tap/hover/untap. Existing private-library and
four-player scenarios remain in the browser suite.

Explain test cases before editing tests. Demonstrate the wrong arrival order
before implementing grouping, then run frontend tests, type checks, lint,
formatting checks, browser tests, and production build.

Results:

- Red phase: the browser regression showed creature, artifact, creature instead
  of creature, creature, artifact. Fixture Origin headers were corrected before
  treating the ordering assertion as the behavior failure.
- Frontend unit suite: eight cases passed, including six new grouping cases.
- Full browser suite: all four scenarios passed, including insertion order in
  both player views, lower land sections, saved coordinates, drag-to-play, and
  tap/hover/untap. Existing privacy and library-search checks also passed.
- Type checking, ESLint, Prettier checks, and production frontend build passed.
- Rendered owner layout was inspected using the browser screenshot.
- `git diff --check` passed.
- Browser execution required running outside the sandbox for local servers and
  Chrome. Backend tests were not rerun because backend code and contracts did
  not change; the browser suite exercises the existing backend commands.

## Architecture decisions

No new ADR: the existing client/server state and privacy boundaries remain.
Related architecture: [ADR-0001](../docs/adr/0001-authoritative-game-state.md).

## Open questions

None.
