# Tabletop API (v1)

All endpoints are same-origin. `GET /api/session` establishes or restores a
guest cookie and returns `{ "csrf": "…" }`. API responses are not cached.
Mutating requests require JSON, the configured `Origin`, `X-CSRF-Token`, the
guest cookie, and a content length at most 100,000 bytes. Browser fetch provides
the origin/cookie automatically. The frontend never receives the guest secret.

## REST

| Method | Endpoint                        | Request / result                                                         |
| ------ | ------------------------------- | ------------------------------------------------------------------------ |
| GET    | `/api/session`                  | CSRF token; HttpOnly guest cookie                                        |
| GET    | `/api/rooms`                    | Current guest's saved room IDs/statuses/formats                          |
| POST   | `/api/rooms`                    | `{name,format?}` → creator's snapshot; COMMANDER default, or STANDARD    |
| POST   | `/api/rooms/{id}/join`          | `{name}` → member's snapshot; same guest reclaims own seat               |
| GET    | `/api/rooms/{id}`               | Member's filtered snapshot                                               |
| POST   | `/api/rooms/{id}/decks/preview` | `{text,url}` → `{draftId,choices,suggested,count}`; URL takes precedence |
| POST   | `/api/rooms/{id}/deck`          | `{draftId,commanders,commandId,revision}` → snapshot                     |
| POST   | `/api/rooms/{id}/commands`      | Command envelope → snapshot                                              |

Deck previews are server-stored and guest-bound, expiring after one day.
Commander IDs refer to the returned choices. Definitions submitted by a client
are not used to construct a deck. Errors return `{message}` with 400 for invalid
actions, 403 for authorization, 409 for stale revisions, and 500 for unacknowledged
infrastructure failures. A stale REST response requires fetching a current view.

## Formats and compatibility

Every room snapshot includes `format` (COMMANDER or STANDARD) and `seatCapacity`
(4 or 2). Format is immutable. Missing/null creation format and legacy snapshot
format default to Commander; unsupported creation values return 400. Join
requests cannot change format. Existing saved life totals are preserved.

Standard starts at 20 life and requires a 60–250-card main deck. For Standard
`/deck`, omit commanders or send an empty list; nonempty selections are rejected.
Commander still requires one or two selections. Legality and copy-limit warnings
require READY `acceptWarnings=true`. Sideboards are not imported.

Definitions retain `legal` for Commander and add `legalities` (provider status by
format) and `legalityCheckedAt` (epoch milliseconds, nullable for legacy entries).
Standard preview requests attempt refresh when observations are missing/stale;
load-time warnings distinguish unavailable verification from fresh nonlegal status.
Cached definitions may be used during refresh failure, but missing uncached cards
still fail. Standard has no commander casts or command-zone cards.

## WebSocket

Connect to `/ws?room={id}` with a valid guest cookie and exact configured Origin.
Membership is checked before upgrading. Receive an initial filtered snapshot;
send command envelopes directly:

```json
{
  "id": "client-generated-uuid",
  "revision": 14,
  "type": "DRAW",
  "data": { "count": 1 }
}
```

Messages are `snapshot` (`view`), `ack` (`commandId`, `view`), `error` (`message`,
and a current `view` when available), or `presence` (`online` seat numbers).
Command IDs are scoped to seat and room. An exact retry applies once and returns
a current view; reusing an ID with different content is rejected. Snapshot
revisions are monotonic; clients ignore older updates. Disconnects do not
concede. Clients disable actions while disconnected and reconnect with backoff.
Presence is ephemeral, independent of game revisions.

## Commands

`data` is an object. Omitted booleans/defaults below are deliberate. Commands
require a playing game and an unconceded seat except READY/START in the lobby
and host FINISH. Card IDs come only from filtered snapshots.

| Type             | Data                                              | Behavior                                                                                                                                                                |
| ---------------- | ------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| READY            | `ready=true, acceptWarnings=false`                | Set readiness; a deck and warning acceptance are required                                                                                                               |
| START            | `firstPlayer?` (Standard only)                    | Host starts with the format's required ready seats; Standard defaults to host or accepts seat 0/1; shuffles and draws seven                                             |
| DRAW             | `count=1, target=self`                            | Move 1–100 top cards to hand atomically; fail if insufficient                                                                                                           |
| SHUFFLE          | `target=self`                                     | Shuffle library, replace hidden IDs, close searches, clear reveals                                                                                                      |
| SEARCH           | `target=self`                                     | Open a permitted private library view                                                                                                                                   |
| CLOSE_SEARCH     | `{}`                                              | Close actor's library search                                                                                                                                            |
| LOOK_TOP         | `count=1, target=self`                            | Privately inspect the current top 1–100 cards, clamped to the remaining library; reject an empty library                                                                |
| SCRY             | `count=1, target=self`                            | Open a private bounded selection for ordered top/bottom arrangement                                                                                                     |
| EXILE_TOP        | `count=1, target=self`                            | Atomically exile current top cards face-up, clamped to the remaining library                                                                                            |
| CLOSE_INSPECTION | `inspectionId`                                    | Close only the actor's matching bounded inspection without reordering                                                                                                   |
| APPLY_SCRY       | `inspectionId, top:[cardId,…], bottom:[cardId,…]` | Apply an exact unique partition of the current inspected cards in one transaction                                                                                       |
| GRANT            | `target, allow=true`                              | Grant/revoke another seat access to actor's private zones                                                                                                               |
| MOVE             | `cardId, zone, top=false, faceDown=false`         | Explicit zone move; top affects library ordering                                                                                                                        |
| TAP              | `cardId, tapped=toggle`                           | Tap/untap a controlled permanent                                                                                                                                        |
| TAP_SELECTED     | `cardIds, tapped`                                 | Set 1–500 unique controlled battlefield cards to an explicit boolean tapped state; validate the entire selection before mutation; one revision/log entry                |
| UNTAP_ALL        | `{}`                                              | Untap controlled permanents                                                                                                                                             |
| FACE_DOWN        | `cardId, faceDown=toggle`                         | Conceal/reveal a controlled public object                                                                                                                               |
| TRANSFORM        | `cardId`                                          | Cycle through catalog faces                                                                                                                                             |
| REVEAL           | `cardId, audience:[seat,…]`                       | Replace reveal audience; empty list ends the reveal                                                                                                                     |
| CONTROL          | `cardId, target`                                  | Transfer a permanent's control, retaining ownership                                                                                                                     |
| ATTACH           | `cardId, targetId`                                | Attach to a permanent; empty target detaches; cycles rejected                                                                                                           |
| COUNTER          | `name, delta` or `name,value`                     | Adjust actor's player counter                                                                                                                                           |
| CARD_COUNTER     | `cardId,name,delta` or `cardId,name,value`        | Adjust a permitted card counter                                                                                                                                         |
| COMMANDER_CAST   | `key,delta=1`                                     | Adjust actor's designated commander's cast count                                                                                                                        |
| TOKEN            | `name,type="",text=""`                            | Create a token on actor's battlefield                                                                                                                                   |
| EDIT_TOKEN       | `cardId,name,type="",text=""`                     | Edit token characteristics                                                                                                                                              |
| COPY             | `cardId`                                          | Create a token copy of an inspectable public card                                                                                                                       |
| POSITION         | `cardId,x,y`                                      | Set normalized 0–1 tabletop coordinates                                                                                                                                 |
| COMBAT           | `cardId,marker`                                   | Empty, `attacker`, or `blocks:{publicCardId}`                                                                                                                           |
| ABILITY          | `text`                                            | Add actor-controlled stack marker                                                                                                                                       |
| RESOLVE_ABILITY  | `abilityId`                                       | Actor removes their marker                                                                                                                                              |
| PHASE            | `{}`                                              | Only the active player advances the informational phase marker                                                                                                          |
| END_TURN         | `{}`                                              | Active player advances to the next non-conceded seat, increments turn, resets to Untap and clears combat; untap/draw and stack resolution remain manual                 |
| GIVE_BACK_TURN   | `{}`                                              | Active player restores the latest saved seat, phase and turn number; cards and all other gameplay state stay unchanged. Rejects empty history or a conceded predecessor |
| DICE             | `sides=6`                                         | Server rolls 2–1000 sided die and logs result                                                                                                                           |
| CONCEDE          | `{}`                                              | Mark actor out, revoke private grants, and advance a stranded turn marker                                                                                               |
| FINISH           | `{}`                                              | Host marks game finished                                                                                                                                                |

Zones: library, hand, battlefield, graveyard, exile, command, stack. Only
designated commanders enter command. Zone moves reset transient state and
revoke reveals; tokens cease when moved outside battlefield/stack. Library
actions by card ID require an open authorized search. Public-card mutation
requires control; private-zone mutation requires ownership or an explicit grant.

Player counters use `life`, `poison`, `casts:{commanderKey}`, and
`damage:{commanderKey}`, plus arbitrary named counters. Counter values support
negative corrections and are bounded to ±99,999. Commanders' public metadata
includes their separate damage-tracking keys. Face-down card projections must
not include identity-bearing definitions or commander keys for observers.

### Turn correction metadata

Snapshots expose `turnVisit`, a monotonic handoff identifier, and viewer-specific
`canGiveBackTurn`. The server retains up to 100 private turn checkpoints across
restarts. Returning consumes the latest checkpoint; it does not undo gameplay.
Notifications use the visit identifier even when the displayed turn decreases.
Older snapshots without these fields keep return controls disabled until support
and a new handoff are available. See [the specification](../spec/give-back-turn.md).

### Bounded library inspections

Snapshots include viewer-only `libraryInspection` metadata, either null or
`{id,target,mode,cardIds}`; mode is `look` or `scry`. Only the inspecting actor
receives this metadata and the bounded cards through the normal card projection.
Existing private-access grants permit delegated targets. Bounded visibility does
not permit card editing or expose the rest of the library. Opening an inspection
closes the actor's full search; opening full search closes their inspection.

Scry confirmation produces top group + uninspected remainder + bottom group,
with each supplied list in first-to-last order. It replaces library card IDs,
clears their explicit reveals, and closes searches and inspections for that
library. Cancellation preserves every card and its order. Library moves, draws
and shuffles invalidate affected inspections; grant revocation, concession and
finish remove affected sessions. Stale or invalid confirmation changes no state.
Public inspection/scry logs contain counts and target names, never private card
identities or arrangement. Reload resumes the original selection, not a local
unconfirmed draft, and must not replay commands. Older snapshots may omit the
field; clients disable these actions until backend support is present.
See [the specification](../spec/bounded-library-actions.md).
