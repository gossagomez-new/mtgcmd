# Tabletop API (v1)

All endpoints are same-origin. `GET /api/session` establishes or restores a
guest cookie and returns `{ "csrf": "…" }`. API responses are not cached.
Mutating requests require JSON, the configured `Origin`, `X-CSRF-Token`, the
guest cookie, and a content length at most 100,000 bytes. Browser fetch provides
the origin/cookie automatically. The frontend never receives the guest secret.

## REST

| Method | Endpoint | Request / result |
| --- | --- | --- |
| GET | `/api/session` | CSRF token; HttpOnly guest cookie |
| GET | `/api/rooms` | Current guest's saved room IDs/statuses |
| POST | `/api/rooms` | `{name}` → creator's snapshot |
| POST | `/api/rooms/{id}/join` | `{name}` → member's snapshot; same guest reclaims own seat |
| GET | `/api/rooms/{id}` | Member's filtered snapshot |
| POST | `/api/rooms/{id}/decks/preview` | `{text,url}` → `{draftId,choices,suggested,count}`; URL takes precedence |
| POST | `/api/rooms/{id}/deck` | `{draftId,commanders,commandId,revision}` → snapshot |
| POST | `/api/rooms/{id}/commands` | Command envelope → snapshot |

Deck previews are server-stored and guest-bound, expiring after one day.
Commander IDs refer to the returned choices. Definitions submitted by a client
are not used to construct a deck. Errors return `{message}` with 400 for invalid
actions, 403 for authorization, 409 for stale revisions, and 500 for unacknowledged
infrastructure failures. A stale REST response requires fetching a current view.

## WebSocket

Connect to `/ws?room={id}` with a valid guest cookie and exact configured Origin.
Membership is checked before upgrading. Receive an initial filtered snapshot;
send command envelopes directly:

```json
{"id":"client-generated-uuid","revision":14,"type":"DRAW","data":{"count":1}}
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

| Type | Data | Behavior |
| --- | --- | --- |
| READY | `ready=true, acceptWarnings=false` | Set readiness; a deck and warning acceptance are required |
| START | `{}` | Host starts with four ready players; shuffles and draws up to seven |
| DRAW | `count=1, target=self` | Move 1–100 top cards to hand atomically; fail if insufficient |
| SHUFFLE | `target=self` | Shuffle library, replace hidden IDs, close searches, clear reveals |
| SEARCH | `target=self` | Open a permitted private library view |
| CLOSE_SEARCH | `{}` | Close actor's library search |
| GRANT | `target, allow=true` | Grant/revoke another seat access to actor's private zones |
| MOVE | `cardId, zone, top=false, faceDown=false` | Explicit zone move; top affects library ordering |
| TAP | `cardId, tapped=toggle` | Tap/untap a controlled permanent |
| UNTAP_ALL | `{}` | Untap controlled permanents |
| FACE_DOWN | `cardId, faceDown=toggle` | Conceal/reveal a controlled public object |
| TRANSFORM | `cardId` | Cycle through catalog faces |
| REVEAL | `cardId, audience:[seat,…]` | Replace reveal audience; empty list ends the reveal |
| CONTROL | `cardId, target` | Transfer a permanent's control, retaining ownership |
| ATTACH | `cardId, targetId` | Attach to a permanent; empty target detaches; cycles rejected |
| COUNTER | `name, delta` or `name,value` | Adjust actor's player counter |
| CARD_COUNTER | `cardId,name,delta` or `cardId,name,value` | Adjust a permitted card counter |
| COMMANDER_CAST | `key,delta=1` | Adjust actor's designated commander's cast count |
| TOKEN | `name,type="",text=""` | Create a token on actor's battlefield |
| EDIT_TOKEN | `cardId,name,type="",text=""` | Edit token characteristics |
| COPY | `cardId` | Create a token copy of an inspectable public card |
| POSITION | `cardId,x,y` | Set normalized 0–1 tabletop coordinates |
| COMBAT | `cardId,marker` | Empty, `attacker`, or `blocks:{publicCardId}` |
| ABILITY | `text` | Add actor-controlled stack marker |
| RESOLVE_ABILITY | `abilityId` | Actor removes their marker |
| PHASE | `{}` | Active player or host advances the informational turn/phase marker |
| DICE | `sides=6` | Server rolls 2–1000 sided die and logs result |
| CONCEDE | `{}` | Mark actor out, revoke private grants, and advance a stranded turn marker |
| FINISH | `{}` | Host marks game finished |

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
