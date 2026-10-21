# Bounded library actions and scry

## Metadata

- Status: Completed
- Priority: High
- Priority rationale: Deliver R13 and R14 together with shared bounded privacy and safe library ordering.
- Depends on: [Card interaction increment](card-interactions.md), [Validated frontend protocol](validated-frontend-protocol.md)

## Summary

Deliver Look at top N, Exile top N and an assisted private scry workflow in Standard
and Commander, without using full-library search to reveal a bounded selection.

## Goals

- Server-select only the permitted current top cards.
- Arrange inspected cards into ordered top/bottom groups and commit together.
- Preserve authority, privacy, retries, persistence and the existing assisted-tabletop scope.

## Non-goals

- Automatic card effects, costs/timing enforcement, casting, mulligans or replay.
- Face-down top-card exile, arbitrary zone moves from bounded inspection, or whole-game undo.
- Persisting an unconfirmed local scry arrangement or locking the table/library during inspection.
- Merging unrelated open PRs or changing transaction ownership.

## Background

The pre-design audit traced LibraryActions, GameAccess, GameViewMapper,
CardZoneTransitions, YourResources and TableOverlays. Full search exposes the entire
library; a separate scope is required. Central library transitions change IDs/order
and must invalidate bounded sessions. Persisted session metadata enables reconnect
without replaying commands. These are required parts of R13/R14, not unrelated refactors.
The user approved comprehensive coverage and ADR-0024 on 2026-10-20.

## Requirements

### Functional requirements

1. Provide Look at top N, face-up Exile top N and Scry N for both formats. N is an
   integer from 1 to 100 (default 1), bounded to the remaining library size. Reject
   an empty library. The server chooses cards; no client-selected IDs determine
   the initial selection. Any non-conceded player may act during play; turn timing
   remains player-managed. Existing private-access grants authorize delegated targets.
2. Persist at most one bounded inspection per actor, with opaque ID, target, mode
   (look/scry) and ordered inspected card IDs. The actor alone receives session
   metadata and newly visible card definitions. Observers/host gain no private access.
3. Opening bounded inspection closes that actor's full search and replaces their
   previous bounded session. Opening full search closes their bounded session.
   Bounded cards are read-only unless independently authorized by full search;
   they cannot be used to bypass existing edit permissions.
4. Exile top N atomically moves the selected current cards to face-up exile in
   top-to-bottom order through existing zone transitions. Preserve identity/reference
   cleanup and manual Commander exceptions. No inspection is required first.
5. Scry begins with all inspected cards on top in their original order. The UI
   allows moving cards between top and bottom, ordering each group with accessible
   controls, then confirming once. Lists are ordered topmost-first and bottom-group
   first-to-last; result is top group + uninspected current remainder + bottom group.
6. Confirm requires the actor's current scry ID and an exact unique partition of
   inspected IDs. Reject missing, repeated, unknown IDs, malformed lists or stale
   sessions before mutation. Invalid confirmation preserves session, order, revision
   and log. Success consumes the session and advances revision/log once.
7. Cancel/close consumes only the matching inspection, never reorders cards. Escape,
   outside dismissal and a visible button use the same cancellation path. Pending,
   disconnected and finished states disable mutation controls. Reload restores the
   original inspected selection; unconfirmed arrangement is local, while ordinary
   snapshots preserve the current draft. Reconnect never repeats commands.
8. Successful moves into/out of a target library, draw, shuffle or scry invalidate
   all bounded sessions for that target. Grant revocation removes the affected
   delegate's session; concession removes sessions owned by or targeting that seat;
   finishing clears bounded sessions. Views hide invalid/legacy malformed sessions
   without mutating Game. Recheck the current top prefix before confirmation.
9. Applying scry reuses the shuffle-style hidden-identity cleanup without randomizing:
   rekey library cards, revoke their explicit reveals, close searches/inspections
   for that library, and preserve other zones and their state. No full-game copy or undo.
10. Public logs describe operation/count/target without private names, IDs or scry
    arrangement. Existing RoomStore locking, revision, idempotency and rollback
    remain authoritative. Actual exile cards are public under existing rules.

### Non-functional requirements

- Bounded server-owned metadata and no new service/dependency.
- Visible keyboard-operable ordering and private artwork without inferring identities.
- Reuse validated snapshots, shared card presentation, transitions and permissions.

## Contracts and data

New commands:

- LOOK_TOP and SCRY: `{target?: seat, count?: integer}`.
- EXILE_TOP: `{target?: seat, count?: integer}`; exile is face-up.
- CLOSE_INSPECTION: `{inspectionId: string}`.
- APPLY_SCRY: `{inspectionId: string, top: string[], bottom: string[]}`.

Persist `libraryInspections` keyed by actor with ID, target, mode and ordered
`cardIds`; missing/null old fields represent no sessions. Views add optional/nullable
`libraryInspection` metadata for the viewer only. Visible inspected cards use the
existing card projection and remain uneditable. No private session maps enter views.
The frontend accepts missing metadata from older servers and disables the new
actions when the field is omitted. Backend-first rollout;
no SQL migration or release-time version bump in this PR.

## Technical constraints

RoomStore owns persistence/transactions. GameAccess owns visibility, dedicated
bounded-action/session helpers own workflow, and CardZoneTransitions owns order and
identity cleanup. Existing full-search commands remain compatible. Prefer existing
popover and card components with explicit callbacks over global state or event buses.

## Invariants

- Only authorized, server-selected bounded cards gain new visibility.
- Cancellation does not change library order; invalid commands change no state.
- Applying scry preserves every uninspected card and other zones.
- No command replay on reconnect or private identity leakage through logs/errors.

## Edge cases and failures

N bounds/types, fewer/zero remaining cards, duplicate definitions with distinct IDs,
all-top/all-bottom/mixed order, grants/revocation, conflicting inspection IDs, moved
cards, shuffle, ordinary snapshots, stale revisions, retries, rollback, old JSON,
finished/conceded players, hidden faces, keyboard dismissal and reload.

## Migration and compatibility

Additive commands/persistence/view fields. Existing full search and manual movement
remain. Old snapshots initialize no inspection; old frontend fixtures accept omitted
metadata. New controls require backend support; merging does not deploy.

## Acceptance criteria

- [x] Both formats support bounded look, top exile and atomic ordered scry.
- [x] Bounded metadata/cards are private and cannot grant editing/full search.
- [x] Cancellation, invalidation, validation, retries and persistence are correct.
- [x] UI is keyboard-accessible and handles pending/reload/snapshot lifetimes.
- [x] Existing search, card transitions and table flows remain passing.

## Verification

User confirmed **comprehensive coverage** on 2026-10-20. Follow TDD with meaningful
red tests before production changes. Cover both formats, access/revocation and raw
view privacy, numeric/payload boundaries, library ordering and identity cleanup,
invalidation, cancellation, revision/log preservation, transaction rollback,
idempotency/restart, protocol decoding and browser keyboard/reconnect flows.
Run relevant/full backend tests, frontend units/lint/types/format/build/analysis,
browser regressions, specification validation and whitespace checks. Use task-scoped
verification with one bounded-library task prefix. PR-0023 was separately approved
for merge, verified and merged before integration into this feature branch.

## Architecture decisions

[ADR-0024](../docs/adr/0024-bounded-library-inspections.md) records separate bounded
sessions and atomic scry versus full search/client-selected IDs or library locking.
[ADR-0001](../docs/adr/0001-authoritative-game-state.md) retains server authority and
transaction ownership; [ADR-0011](../docs/adr/0011-validated-frontend-protocol-boundary.md)
retains validated wire decoding.

## Open questions

None.
