# Configurable mulligans

## Metadata

- Status: Completed
- Priority: High
- Priority rationale: R16 enables supported opening-hand choices before normal play in both formats.
- Depends on: [Game-engine decomposition](game-engine-decomposition.md), [Validated frontend protocol](validated-frontend-protocol.md)

## Summary

Deliver [R16](../docs/playtest-backlog.md): visible host-configured house rules,
persisted opening-hand progress and private atomic redraw/keep workflows.

## Goals

- London mode defaults to zero free redraws for Standard and one for Commander.
- Support unlimited seven-card redraws without bottoming.
- Start normal play automatically when every player has kept independently.

## Non-goals

- Replay, sideboarding, automatic payment/timing enforcement or turn-order opening-hand decisions.
- Undoing a kept hand, automatically keeping for disconnected players, or normal gameplay during opening selection.

## Background

START currently deals seven and immediately enables play. Command admission and
screen selection need an explicit opening state. Existing zone transitions own
shuffle, rekeying and private-zone cleanup and will be reused. These are required
R16 changes, not a broader lifecycle refactor. Existing playing/finished snapshots
must not retroactively enter opening selection.

## Requirements

1. Only the host configures settings in the lobby. Everyone sees settings before readiness. Actual changes reset every player's ready and warning-acceptance flags; unchanged settings do not reset readiness.
2. Modes are LONDON and UNLIMITED. Free count is an integer from 0 to 100, used only by London mode. Settings lock after start.
3. START deals up to seven available cards per player, retains commanders outside the library, and enters `opening`. Existing host, readiness and first-player checks remain.
4. Players independently redraw or keep only their own hands. A redraw returns the whole hand, shuffles and rekeys the library, then deals up to seven. London bottom debt is redraw count minus free count, bounded by seven and available hand size. Stop paid London redraws once debt reaches the available opening-hand size; unlimited mode has no paid limit.
5. KEEP_HAND atomically moves exactly the required unique hand IDs to the library bottom in submitted order (first ID nearer the top). Each redraw still presents seven where available; bottoming occurs only when keeping. Empty libraries permit keeping an empty hand, not redrawing it.
6. Kept players wait; they cannot redraw or keep again. When all keep, status becomes playing with the selected active player, turn and phase unchanged. No automatic draw/untap occurs.
7. Opening allows only redraw/keep and host FINISH cancellation. Normal game actions, imports, joining and setting changes are rejected. Disconnected players resume from persisted state; the host may finish an abandoned opening table.
8. Own-hand identities are private during opening, regardless of any legacy grants/search metadata. Other players see progress and counts only. Logs contain no drawn or bottomed identities/order. Private-zone transitions rekey cards.
9. UI provides visible settings, pending/disconnected guards, progress, accessible cards and ordered bottom selection with keyboard-operable controls. Selection resets after redraw/card identity changes; authoritative progress survives reload/reconnect.
10. Preserve RoomStore revision, retry and transaction guarantees for all new commands. Malformed/stale requests cause no partial state, history or broadcast changes.

## Contracts and data

- CONFIGURE_MULLIGANS: `{mode, freeMulligans}`.
- MULLIGAN: no target; actor-owned hand only.
- KEEP_HAND: `{bottom: string[]}`; omitted bottom means empty, explicit null is invalid.
- Persist one nullable/defaultable mulligan state containing mode/free settings and per-seat redraw/kept progress. Views expose settings/progress/bottom counts, never selected private IDs.
- Add `opening` lifecycle status. Old snapshots missing mulligan fields use format defaults and empty progress; existing playing games continue normally. Frontend schemas accept missing fields on legacy non-opening snapshots and require opening metadata for opening snapshots.

## Technical constraints

No new dependency. Reuse transitions, server command admission, seat filtering,
Zod validation, existing components and RoomStore transactions. Do not add mutable
client-owned game authority or a second random provider.

## Invariants

- No normal gameplay until everyone keeps; no opponent-hand disclosure.
- Registered decks, commander identity, life totals and selected starting player survive redraws.
- A rejected command leaves state/revision/log unchanged; duplicate successful requests do not redraw twice.

## Edge cases and failures

Cover short/empty libraries, zero-card kept hands, free/paid boundary, malformed
settings/IDs, duplicates/foreign IDs, pending gestures, disconnected peers, old
snapshots, stale revisions and persistence rollback. Card IDs selected before a
redraw must not be reusable. No opening-stage concede policy is introduced.

## Migration and compatibility

No SQL migration or release bump. Deploy bundled frontend/backend together.
START intentionally changes from immediate play to opening-hand selection; old
clients must reload to use the new stage. Persisted playing games are unchanged.
Older binaries cannot resume new opening-stage games; downgrades require an
explicit snapshot migration or restoration plan.

## Acceptance criteria

- [x] Both modes/formats complete opening selection and allow play only after all keep.
- [x] Settings authority/readiness, privacy, ordered bottoming and rejection atomicity hold.
- [x] Reconnect/recovery, duplicate requests and rollback preserve progress and card order.
- [x] Relevant backend/frontend/browser suites, formatting/build and focused mutation checks pass; limitations are recorded.

## Verification

User confirmed comprehensive coverage on 2026-10-22: both formats/modes,
settings/readiness, free/paid boundaries, ordered bottoming, short/empty libraries,
privacy/rekeying, gameplay gating, reconnects, legacy snapshots, transaction rollback,
retries, keyboard/browser flows and focused mutation checks. Follow red-green-refactor:
new behavior must fail on original production for the intended reason before implementation.

### Executed verification (2026-10-22)

- Confirmed behavioral red tests for the missing opening stage/commands and frontend
  metadata validation before implementation; subsequent focused suites passed.
- `./scripts/verify.sh --task configurable-mulligans full --browser` passed:
  759 backend tests, 275 frontend unit cases, 161 browser cases, 41 analyzer
  fixtures, 38 build/analysis integration fixtures and 99 Python tooling tests.
  Formatting, lint, TypeScript/production builds and all 46 specification checks
  passed. Browser coverage includes real four-player Commander and two-player
  Standard games with redraw, reload, keeping and subsequent gameplay.
- Focused PIT for `dev.mtgcmd.game.MulliganActions` using `dev.mtgcmd.game.Mulligan*`
  tests killed all 65 mutants, with no surviving, uncovered or timed-out mutants.
  This is a focused result, not a whole-backend mutation score.
- Evidence manifest: `/tmp/mtgcmd-verify-configurable-mulligans-26w66k4y/manifest.json`.
  PIT reports were preserved before the clean build at
  `/tmp/mtgcmd-mulligan-evidence-u3wbax40/pit-reports/`. The manifest describes
  executable source before these final documentation completion updates.
- Advisory analysis remains enabled: nine frontend warnings (including the new
  89-line `HandChoice` component), zero Knip issues, and PMD findings including
  existing mutable-game access conventions. No rules or score gates were weakened.
- No hosted deployment or full-backend PIT run was performed for this increment.

## Architecture decisions

[ADR-0025](../docs/adr/0025-configurable-mulligans.md), authorized 2026-10-22.

## Open questions

None.
