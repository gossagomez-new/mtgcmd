# Frequent table interactions

## Metadata

- Status: Completed
- Priority: High
- Priority rationale: Remove frequent interaction friction observed in a real Standard playtest.
- Depends on: [Game-engine decomposition](game-engine-decomposition.md), [App screen composition](app-screen-composition.md), [Card button presentation](card-button-presentation.md), [Shared browser fixtures](shared-browser-fixtures.md)

## Summary

Deliver backlog R01, R02, R04, R06 and R09 in both formats. See the
[playtest backlog](../docs/playtest-backlog.md) for confirmed product decisions.

## Goals

- Keep turn controls and Untap all accessible while scrolling.
- Provide reliable double-click tapping and battlefield-to-zone dragging.
- Place opponent life beside their name.

## Non-goals

- Quick-action menus/dialog redesign, batch tapping, turn notifications, casting,
  mulligans, sideboards, automatic priority, untapping, drawing or deployment.

## Background

The pre-design audit found duplicated host/active turn permissions and no atomic
end-turn command. Both must change together for R02. CardButton excludes battlefield
sources and has no connection/pending gate; existing battlefield drops accept raw
IDs. Shared drop validation and live action gating are required for R01/R06.
Existing explicit props and server-filtered snapshots remain the boundaries.
No unrelated component decomposition is included. User confirmed comprehensive
coverage and ADR-0020. The branch retains the already committed backlog.

## Requirements

1. PHASE and new END_TURN accept only the active, non-conceded player in a playing
   game. The host has no override. Existing envelope/revision/seat/admission order
   remains. Unauthorized actions leave persisted state/revision/log unchanged.
2. END_TURN has empty data. Atomically advance to the next non-conceded seat,
   wrapping and allowing the sole remaining player to keep the marker; increment
   turn, set phase Untap and clear combat markers. Do not tap/untap/draw, resolve
   stack items or change other card/counter state. One success creates one revision
   and log entry; existing RoomStore idempotency and transactions apply.
3. PHASE at Cleanup uses the same transition. Next phase and End turn are shown
   identically for all seats and disabled for nonactive/disconnected/pending/busy/
   conceded/finished clients. They remain outside the desktop board scroll region.
4. Place Untap all with the persistent controls, available to any eligible player
   independently of whose turn it is. Keep existing controller-based behavior.
5. Double-click an editable battlefield card to send exactly one TAP. Single-click
   inspection and keyboard inspection remain. Pointer inspection may be briefly
   deferred to distinguish a double click; cancel deferred inspection on a tap,
   drag, invalidating snapshot, permission loss or unmount. No hidden identity
   may be inferred. Live action gating applies to taps and drag sources.
6. Allow editable battlefield cards to be dragged to the player's hand, library
   bottom, graveyard, exile, stack or designated-commander command shortcut. Preserve
   hand-to-battlefield dragging. Use existing application/card ID transport and MOVE.
   Private/discard destinations follow card ownership under existing server rules;
   destination hints make this clear. Library top and other exceptions remain in
   the inspector. Do not add opponent-zone drop targets or change control transfer.
7. Drop handlers validate the current visible card, editable flag, live canPlay,
   allowed zone and commander metadata. Ignore foreign/empty/unknown/read-only IDs,
   disabled drops and same-zone drops. Highlight candidate targets for card drags;
   failed validation sends no command. No browser-local game-state mutation.
8. Put opponent name and life in one adjacent identity group, retaining connection
   status and snapshot updates, including zero/negative values and concealed cards.
9. Preserve 90-degree tapped orientation, grouping, lands below other permanents,
   private-card filtering, inspector actions and supported desktop layouts.

## Contracts and data

New WebSocket command type END_TURN with data {}. PHASE's authority is deliberately
narrowed to the active player. No snapshot/SQL fields change. Existing TAP,
UNTAP_ALL and MOVE payloads remain compatible. Error: Only the active player can
advance turns. END_TURN description: ended the turn. Reconnect/resend continues to
use existing command IDs and expected revisions; no mutation retry loops.

## Technical constraints

Use existing components/hooks and explicit callbacks, without a state library or
new runtime dependencies. Server owns turn transitions. Keep side effects out of
render, clean up deferred click callbacks and validate drops against fresh props.

## Invariants

- No automatic card-effect, priority, draw or untap rules.
- Hidden definitions never become client-visible through drag feedback or controls.
- Only accepted committed actions advance revision/log and broadcast success.

## Edge cases and failures

Nonactive host, conceded seats, lone survivor, Cleanup and early End turn,
nonempty stack, stale revisions, duplicate command IDs, stolen permanents,
face-down cards, token removal, stale IDs, canceled drags, pointer/keyboard
inspection, disabled snapshots and board scrolling in both formats.

## Migration and compatibility

No schema migration. Active-player-only PHASE is an intentional behavior change;
old host controls may receive a server rejection when the host is not active.
END_TURN requires the updated backend. No version bump until release preparation.
Existing completed specs describe historical host/drag behavior; this specification
supersedes those clauses for the new interaction contract only.

## Acceptance criteria

- [x] Both formats reject nonactive advancement and execute END_TURN exactly once.
- [x] Turn controls and Untap all remain visible while scrolling at supported desktop sizes.
- [x] Double-click taps once while inspection, drag, privacy and disabled states remain correct.
- [x] Battlefield drops send permitted existing MOVE payloads and reject invalid sources.
- [x] Opponent life is adjacent to the name and updates with snapshots.

## Verification

User confirmed comprehensive coverage: both formats; active-only phase/end-turn;
skipped conceded seats; revision/idempotency; tap versus inspection/drag; permitted
drops; disabled/hidden-card behavior; persistent controls; and browser flows.
Add tests first and confirm expected behavior failures before production changes.
Run targeted Java and browser red/green cases, frontend unit/lint/type/format,
analysis, full browser suite and analyzed packaging/backend verify. Inspect new
advisories without blanket suppressions. Validate specs and whitespace. Record
executed checks and any environment limitations; do not claim hosted CI.

### Executed checks

On 2026-10-19, on feat/table-interactions (updated main plus the previously
committed backlog):

- Backend red tests demonstrated missing END_TURN and the old host permission;
  unrelated selected tests passed. Targeted green Java tests passed 108 cases,
  including both formats, all phases, concession skipping, stale/admission order,
  and RoomStore duplicate-command/recovery behavior.
- Browser red cases demonstrated missing End turn, double-click tap, battlefield
  dragging and adjacent life placement. One drag/life attempt failed during test
  server startup and is not counted as valid red evidence; its subsequent run
  demonstrated the expected behavior failures.
- Eight drop-intent unit cases cover supported destinations, designated commanders,
  same-zone/unknown/read-only IDs, and permitted cards without definitions. The
  complete frontend unit suite passed 218 tests. Lint, typecheck and formatting pass.
- Focused browser checks first passed 19 cases. Full regression exposed deferred
  inspection cancellation on benign snapshots and old fixture assumptions about
  immediate selection. A dedicated red test reproduced cancellation before its
  fix; snapshots now preserve pending inspection with fresh data unless identity,
  zone, permissions or live availability invalidate it. Inspector fixtures wait
  for observed selection. The combined inspector/interaction run passed 23 cases.
- The full browser suite passed all 64 tests, including real Commander/Standard
  gameplay, 90-degree rotation, grouping, privacy, reconnect and Standard End turn.
  An intermediate 63/64 run failed only the old assertion that battlefield cards
  cannot drag; the updated test asserts controller dragging and opponent read-only
  behavior. The earlier run with the inspection regression was stopped after its
  demonstrated failure; it is not reported as passing.
- Final event-boundary review added a red check showing that a disabled bubbling
  dragstart still wrote an ID. The handler now cancels disabled/read-only starts
  before writing payload data. After this final guard, all 21 affected interaction/
  shared-card browser cases passed in both formats. The full 64-test run preceded
  this narrowly scoped guard; affected tests were rerun afterward.
- The canonical analyzed package build passed on final executable source, producing
  the embedded client jar and passing all 664 Java tests, PMD reporting and JaCoCo
  reporting. Separate Spotless check passed. Final frontend analysis completed
  with three known function-length advisories (PlayerArea, PlayerCounters,
  ZoneViewer), no errors and zero Knip issues. The new GameStack length advisory
  was removed through a small render-prop grouping; no thresholds/suppressions changed.
- PMD findings on changed Java/tests were reviewed. Direct mutable snapshot access,
  coherent multi-assertion tests and bounded seat traversal remain intentional;
  no broader PMD cleanup was included.
- Specification/index/link validation, Markdown formatting and whitespace checks
  pass. API documentation records END_TURN and active-only PHASE authority.

Pointer inspection on tappable battlefield cards is delayed 450 ms to distinguish
it from double-click tapping; keyboard inspection stays immediate. Drop feedback
indicates candidate destinations, with current-snapshot validation before MOVE.
Library drops use existing bottom-placement defaults and owner-based zone rules.
No new runtime dependencies, schema changes, app version bump, hosted CI run,
mutation campaign, deployment or publication were performed. Logs and browser
traces from intermediate failures remain temporary local verification artifacts.

## Architecture decisions

[ADR-0020](../docs/adr/0020-table-interaction-contracts.md) records the approved
command/interaction decision. Existing [ADR-0005](../docs/adr/0005-game-engine-component-boundaries.md)
and [ADR-0013](../docs/adr/0013-app-composition-boundaries.md) retain component ownership.

## Open questions

None.
