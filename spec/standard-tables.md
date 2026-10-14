# Two-player Standard tables

## Metadata

- Status: Completed
- Priority: High
- Priority rationale: Support a second format without compromising Commander compatibility or privacy.
- Depends on: [Commander tabletop](commander-tabletop.md)

## Summary

Add an immutable Standard room format with two seats, 20 starting life, 60–250
main-deck cards, no commanders, and assisted legality/copy-limit warnings.

## Goals

- Create, import, ready, play, save, and reconnect to two-player Standard games.
- Preserve four-player Commander requests, saved games, and existing mechanics.
- Warn transparently when Standard legality cannot be verified.

## Non-goals

- Sideboards, match scoring, automatic mulligans/draw rules, or automatic wins.
- Generic rules plugins, new services, or interpreting every card-specific exception.

## Background

The pre-design audit identified hardcoded four-seat capacity/rotation, 40 life,
mandatory commander selection, and Commander-only legality data. Required
remediation is server-owned format profiles and separate deck validation.
The user selected a 60-card minimum, single games, advisory warnings, host-chosen
starting player, and comprehensive verification. ADR creation was authorized.

## Requirements

### Functional requirements

1. Room creation selects COMMANDER or STANDARD; absent/null format defaults to
   Commander. Unsupported formats are rejected. Format cannot change afterward.
2. Standard has exactly two seats, 20 life, and requires two ready players to start.
3. Standard decks contain 60–250 cards and no commander selections. Omitted or
   empty commander lists are accepted; nonempty lists are rejected.
4. Standard warns for nonlegal cards and more than four copies of a nonbasic
   identity, with basic lands and recognized any-number exceptions exempt.
   Other card-specific deck exceptions remain player-reviewed warnings.
5. Warnings require explicit acceptance before readiness. No Commander singleton,
   color identity, or eligibility checks apply to Standard.
6. Host chooses firstPlayer 0 or 1 on Standard START, defaulting to host. Commander
   START behavior remains unchanged. Seven opening cards are drawn for each player.
7. Turn rotation and target validation use actual player count. Privacy, grants,
   search, commands, revision/idempotency, and manual FINISH remain intact.
8. Standard preview refreshes legality when missing or at least 24 hours old.
   Cached cards survive refresh failure with verification warnings; uncached cards
   still require successful resolution. Unknown names block import.
9. UI selects format on creation, displays server capacity and format, shows one
   opponent for Standard, and hides commander selections/zones/tax/damage tools.
10. Keep import syntax and sideboard exclusion; existing battlefield grouping,
    library filters, and manual card actions remain shared.

### Non-functional requirements

- Use existing bounded/paced collection transport; no live providers in tests.
- Inject a clock for deterministic legality freshness tests.
- No new dependencies, database schema, or deployment actions.

## Contracts and data

POST /api/rooms accepts optional format. Room snapshots include format and
seatCapacity, and saved-room summaries include format. Standard START accepts
optional firstPlayer. Deck-loading payload shape remains unchanged; commanders
are interpreted by the authoritative room format.

Persist room format in the JSON snapshot. Missing/null legacy room format reads
as Commander, preserving counters and in-progress state rather than reinitializing.

Definitions retain the existing boolean legal (Commander), plus optional
legalities mapping format names to provider status and legalityCheckedAt epoch
milliseconds. Missing metadata denotes unverified legality. Keep existing
constructors and Commander catalog/importer entrypoints compatible; add
format-aware resolution/preview for Standard rooms.

Fresh metadata must contain a known Standard status and an observation in the
past less than 24 hours old. Missing, stale, or future observations require a
verification warning. Fresh banned/not_legal statuses require legality warnings.
A partial provider response cannot make an unknown uncached card valid.

## Technical constraints

Server-owned profiles define capacity, starting life, and deck bounds. Separate
format deck validation from physical actions. Standard resolution performs lazy
cache enrichment; Commander resolution retains its current behavior.

## Invariants

- Clients cannot override a room's format via deck or command payloads.
- Unknown/stale legality is never presented as verified.
- Standard never starts with an absent or unready opponent or a third seat.
- Legacy room loading preserves life, zones, and revisions.
- Hidden opponent cards remain absent from unauthorized snapshots.

## Edge cases and failures

- Reject 59/251-card Standard decks, invalid firstPlayer, nonhost START, invalid
  targets, and commander selections. Accept 60/61/250-card decks.
- Cache refresh failures may fall back only to existing definitions; unknown or
  missing uncached identities still fail clearly.
- Multi-face aliases and printings count by canonical definition identity.
- Missing format defaults; unsupported persisted format fails instead of silently
  treating a Standard room as Commander.

## Migration and compatibility

No SQL migration. Existing saved definitions may lack legality metadata; lazy
refresh enriches the catalog and warns if unavailable. Saved game definitions
and life totals remain stable. New Standard games require this version; older
servers are not safe rollback readers of new format snapshots.

## Acceptance criteria

- [x] Standard creation, two-seat join, import without commanders, warnings, and start work.
- [x] Both players start at 20 life with seven cards and alternate turns.
- [x] Stale/unavailable legality produces actionable advisory warnings.
- [x] Saved Standard games recover while old rooms remain Commander.
- [x] Standard UI removes Commander-only controls and retains privacy/card actions.
- [x] Full Commander regression checks pass.

## Verification

The user selected **comprehensive coverage**: backend parameterized deck-size,
copy-limit, readiness, target, turn, first-player, cache freshness, partial/provider
failure, multi-face, legacy serialization, store/reconnect, malformed format, and
API compatibility tests. Browser scenarios cover two isolated guests importing,
accepting warnings, starting, playing, and reconnecting; retain all Commander
browser scenarios. Use deterministic fake provider responses and clocks.

Follow red-green-refactor with expected behavior failures before implementation.
Run backend/frontend suites, browser tests, formatting, lint, type checks, and
frontend-first packaged build.

Results:

- Initial red phase: Standard creation lacked format metadata; the other 116
  backend cases passed. Engine regressions then demonstrated Commander-only
  life/deck/seat behavior. Six catalog regressions demonstrated missing Standard
  refresh/observation behavior, and a format-shape regression demonstrated 500
  instead of 400 for object-valued formats.
- Full frontend-first build: `./scripts/build.sh` passed, including 164 backend
  tests and executable jar packaging with embedded frontend assets.
- Frontend unit suite: all eight cases passed. Type checks, ESLint, Prettier,
  Java `spotless:check`, and working-tree whitespace checks passed.
- Full browser suite: all five scenarios passed, including a Standard duel,
  third-seat rejection, 59-card rejection, sideboard exclusion, warnings/readiness,
  host-selected first player, privacy, card actions, and reload/server reconnect.
  Every existing Commander browser scenario remained green.
- PostgreSQL/HTTP/browser checks required execution outside the sandbox for local
  port binding and Chrome. No live card service was used in automated tests.
- Build artifact: `backend/target/tabletop-0.1.0.jar`.
- No user services were restarted, deployed, or published; only test servers ran.

## Architecture decisions

- [ADR-0002](../docs/adr/0002-room-format-profiles.md): immutable server-owned formats and legacy defaults.
- [ADR-0001](../docs/adr/0001-authoritative-game-state.md): unchanged authority and privacy.

## Open questions

None.
