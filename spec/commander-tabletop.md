# Four-player Commander tabletop

## Metadata

- Status: Completed
- Priority: High
- Priority rationale: Establish the complete first playable application.
- Depends on: None

## Summary

Private four-player desktop Commander tabletop, with React/TypeScript clients
and a Java 25 authoritative server. Players adjudicate card effects and timing.

## Goals

- Four guests can play a game, recover their seats, and resume after restart.
- Server-side filtering prevents disclosure of private card identities.
- Assisted physical actions cover complex Commander interactions.

## Non-goals

- Automated rules engine, accounts, matchmaking, spectators, mobile UX, chat,
  voice, automatic undo, and multiple backend instances.

## Background

The repository contained policy and documentation templates, with no existing
application to audit. The user approved this design and implementation. Guests
use external voice; the initial service is local with public HTTPS access.

## Requirements

### Functional requirements

1. Invite-only rooms have four guest seats; the host starts after all are ready.
2. Import pasted text or public Moxfield URLs, choose commanders, and explicitly
   accept structural/legality warnings. Unknown cards block import.
3. Provide zones, draw/shuffle/search/reorder/reveal, tap, transform, face-down,
   counters, tokens/copies, attachments, control changes, and a manual stack.
4. Track turn/phase, life (initially 40), commander damage and casts, poison,
   named counters, combat markers, dice, and concessions.
5. Correct mistakes by explicit logged actions. No automatic undo.
6. Persist room, seat credentials, decks, game snapshots, command results, and
   action logs. Disconnection reserves the seat and never concedes.
7. Conceding the active seat advances the informational marker to a remaining
   player; other game consequences are adjudicated by the players.

### Non-functional requirements

- One instance, initially one four-player room; modern desktop browsers.
- Serve frontend/API/WebSocket at one HTTPS origin; database stays private.
- Filter private identities from snapshots, logs, errors, and acknowledgments.
- Validate CSRF/origin, authenticate guests, and enforce server permissions.
- External imports have allowlisted hosts, bounded responses, and timeouts.

## Contracts and data

REST provides session bootstrap, room creation/join/list/view, deck import, and
readiness. Native WebSocket provides commands and full filtered snapshots.
Commands contain an ID, expected revision, type, and action-specific payload;
seat identity is derived from the session. Duplicate IDs apply once. Conflicts
return a current filtered view. Unknown or invalid actions do not persist.

Rooms contain four seats, host, lifecycle, decks, warnings, and readiness. Games
contain ordered card instances, players, turn/phase, stack, private-zone grants,
search sessions, and public action descriptions. Definitions describe catalog
cards/faces; instances describe owner/controller, zone, counters, attachment,
face state, commander identity, combat marker, and tabletop position.

Default library views expose counts. SEARCH explicitly exposes a permitted
library. Private-zone permission is granted/revoked by its owner. REVEAL has an
explicit seat audience. Moves revoke temporary reveals; shuffles close searches
and replace hidden identifiers. Face-down public objects conceal definitions
from observers. Deck contents are private even before play.

## Technical constraints

- Java 25, Spring Boot, Maven, PostgreSQL, Flyway migrations.
- React, TypeScript, Vite, npm; server state is never client-authoritative.
- Transactional snapshot and action log writes with per-room database locks.
- Secure HttpOnly guest cookie; no seat recovery without the original cookie.
- Scryfall catalog is cached; loaded games continue through external outages.
- Moxfield failure is actionable and permits pasted exports, without bypasses.

## Invariants

- A non-token card occupies exactly one zone; tokens cease on leaving play.
- Ownership survives control transfer. Private zones follow ownership.
- Only accepted committed commands advance revision or broadcast success.
- Client-visible identifiers do not disclose hidden library order.

## Edge cases and failures

Empty library actions fail atomically. Fifth seats are rejected. Non-host start,
unready starts, unauthorized private actions, malformed payloads, duplicates,
stale commands, unavailable card/import services, persistence failures, and
reconnection all receive explicit outcomes. Losing guest credentials cannot
reclaim an occupied seat. Concession marks a player out; friends handle game
consequences explicitly. Host may mark the game finished.

## Migration and compatibility

Initial schema only. Version commands and persisted snapshots through release
changes; retain database volumes. No public deployment or data deletion is
authorized by implementation alone.

## Acceptance criteria

- [x] Four separate browsers join, import decks, ready, and start.
- [x] Tabletop actions converge on consistent filtered game state.
- [x] Other hands, unrevealed libraries, and face-down identities stay private.
- [x] Refresh and backend restart restore occupied seats and committed state.
- [x] Duplicate, stale, malformed, and unauthorized actions cannot corrupt state.
- [x] Import failures permit a usable pasted-export fallback.

## Verification

Confirmed target: risk-focused coverage, chosen by the user. Unit tests cover
state transitions, ownership/control, privacy, and action validation.
Real-PostgreSQL integration tests cover transactions, revisions, duplicate
commands, permissions, restart recovery, and rollback. Four-browser tests cover
join/readiness, gameplay, reveals/privacy, and reconnect. Import tests cover
unknown cards, warnings, URL validation, failures, and pasted exports. Run
TypeScript checks, frontend build/lint/format, backend tests/build, and git
whitespace checks. Record actual execution outcomes in the README.

Implemented and verified on 2026-10-13: 21 backend tests (including real
PostgreSQL and HTTP fixtures), two frontend unit tests, and two browser
scenarios. Packaging and a live Scryfall deck import also passed. Public HTTPS
and Docker image execution remain unverified; no deployment was performed.

## Architecture decisions

See [ADR-0001](../docs/adr/0001-authoritative-game-state.md), authorized by the
user: state ownership, transactional recovery, and privacy projection.

## Open questions

None blocking implementation. Live Moxfield availability is an external
integration limitation with an explicitly specified fallback.
